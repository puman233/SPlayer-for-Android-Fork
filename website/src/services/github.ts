/**
 * GitHub Release 读取。
 *
 * 这里只做一件事：拿到 latest release 的版本号、发布时间和 APK 附件。
 * 明确不读取也不展示 download_count 等下载统计字段。
 *
 * GitHub API 只是增强：任何失败（403 / 404 / 超时 / 断网 / 字段异常）都返回 null，
 * 由页面保留静态 fallback，不重试、不抛出、不阻塞渲染。
 */

export const REPO_SLUG = "puman233/SPlayer-for-Android-Fork";
export const REPO_URL = `https://github.com/${REPO_SLUG}`;
export const RELEASES_URL = `${REPO_URL}/releases`;
export const RELEASES_LATEST_URL = `${REPO_URL}/releases/latest`;

const LATEST_RELEASE_API = `https://api.github.com/repos/${REPO_SLUG}/releases/latest`;
const REQUEST_TIMEOUT_MS = 7000;
const CACHE_TTL_MS = 30 * 60 * 1000;
const CACHE_KEY = "splayer-fork-website:latest-release";

export interface ReleaseAsset {
  /** 附件文件名 */
  readonly name: string;
  /** 字节数 */
  readonly size: number;
  /** 直接下载地址 */
  readonly downloadUrl: string;
}

export interface ReleaseInfo {
  readonly tag: string;
  readonly publishedAt: string;
  readonly htmlUrl: string;
  readonly assets: readonly ReleaseAsset[];
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function asNonEmptyString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}

/** 校验 GitHub API 的原始响应；字段不完整时返回 null。 */
export function parseReleasePayload(payload: unknown): ReleaseInfo | null {
  const root = asRecord(payload);
  if (!root) return null;

  const tag = asNonEmptyString(root["tag_name"]);
  if (!tag) return null;

  const assets: ReleaseAsset[] = [];
  const rawAssets = Array.isArray(root["assets"]) ? root["assets"] : [];

  for (const item of rawAssets) {
    const asset = asRecord(item);
    if (!asset) continue;

    const name = asNonEmptyString(asset["name"]);
    const downloadUrl = asNonEmptyString(asset["browser_download_url"]);
    const size = asset["size"];

    if (!name || !downloadUrl) continue;
    if (typeof size !== "number" || !Number.isFinite(size) || size <= 0) continue;

    assets.push({ name, size, downloadUrl });
  }

  return {
    tag,
    publishedAt: asNonEmptyString(root["published_at"]) ?? "",
    htmlUrl: asNonEmptyString(root["html_url"]) ?? RELEASES_LATEST_URL,
    assets,
  };
}

/** 校验缓存里的数据，避免旧格式或损坏内容被当成有效结果。 */
function reviveRelease(value: unknown): ReleaseInfo | null {
  const root = asRecord(value);
  if (!root) return null;

  const tag = asNonEmptyString(root["tag"]);
  if (!tag) return null;

  const assets: ReleaseAsset[] = [];
  const rawAssets = Array.isArray(root["assets"]) ? root["assets"] : [];

  for (const item of rawAssets) {
    const asset = asRecord(item);
    if (!asset) continue;

    const name = asNonEmptyString(asset["name"]);
    const downloadUrl = asNonEmptyString(asset["downloadUrl"]);
    const size = asset["size"];

    if (!name || !downloadUrl) continue;
    if (typeof size !== "number" || !Number.isFinite(size) || size <= 0) continue;

    assets.push({ name, size, downloadUrl });
  }

  return {
    tag,
    publishedAt: asNonEmptyString(root["publishedAt"]) ?? "",
    htmlUrl: asNonEmptyString(root["htmlUrl"]) ?? RELEASES_LATEST_URL,
    assets,
  };
}

function readCache(): ReleaseInfo | null {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY);
    if (!raw) return null;

    const envelope = asRecord(JSON.parse(raw) as unknown);
    if (!envelope) return null;

    const savedAt = envelope["savedAt"];
    if (typeof savedAt !== "number" || Date.now() - savedAt > CACHE_TTL_MS) return null;

    return reviveRelease(envelope["release"]);
  } catch {
    return null;
  }
}

function writeCache(release: ReleaseInfo): void {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: Date.now(), release }));
  } catch {
    // 隐私模式或存储已满时忽略，缓存不是必需品。
  }
}

async function requestLatestRelease(signal: AbortSignal): Promise<ReleaseInfo | null> {
  const response = await fetch(LATEST_RELEASE_API, {
    signal,
    headers: { Accept: "application/vnd.github+json" },
    credentials: "omit",
    cache: "no-store",
    referrerPolicy: "no-referrer",
  });

  if (!response.ok) return null;

  const payload: unknown = await response.json();
  return parseReleasePayload(payload);
}

/**
 * 读取最新 Release。任何异常都折叠成 null，调用方直接沿用静态 fallback。
 */
export async function loadLatestRelease(): Promise<ReleaseInfo | null> {
  const cached = readCache();
  if (cached) return cached;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const release = await requestLatestRelease(controller.signal);
    if (release) writeCache(release);
    return release;
  } catch {
    // 超时 / 断网 / JSON 解析失败：只走 fallback，不重复请求。
    return null;
  } finally {
    clearTimeout(timer);
  }
}

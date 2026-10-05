/**
 * Release 数据读取。
 *
 * 读取顺序（前面的成功就不再往后走）：
 *   1. 本次页面会话的请求备忘（同一个页面只解析一次）
 *   2. 本站静态 metadata `latest-release.json`（构建阶段由 GitHub Actions 生成）
 *   3. 跨刷新的 sessionStorage 兜底缓存
 *   4. GitHub API releases/latest
 *   5. null —— 由页面保留静态 fallback
 *
 * 为什么本站 metadata 优先：用户浏览器不一定能稳定访问 api.github.com，
 * 而且未认证的 GitHub API 每个 IP 每小时只有 60 次。
 * 静态文件走 Pages 自己的域名，不受这两点影响。
 *
 * metadata 负责「最新版是什么」，下载线路负责「从哪条线路下载」，两者互不依赖。
 *
 * 明确不读取也不展示 download_count 等下载统计字段。
 */

export const REPO_SLUG = "puman233/SPlayer-for-Android-Fork";
export const REPO_URL = `https://github.com/${REPO_SLUG}`;
export const RELEASES_URL = `${REPO_URL}/releases`;
export const RELEASES_LATEST_URL = `${REPO_URL}/releases/latest`;

const LATEST_RELEASE_API = `https://api.github.com/repos/${REPO_SLUG}/releases/latest`;

/**
 * 本站静态 metadata 地址。
 * 必须经过 Vite 的 base，否则在 GitHub Pages 子路径下会 404。
 */
export const LOCAL_METADATA_URL = `${import.meta.env.BASE_URL}latest-release.json`;

const API_TIMEOUT_MS = 7000;
const LOCAL_TIMEOUT_MS = 5000;
/** 只作为静态 metadata 不可用时的兜底，因此保留较长的 TTL */
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
  /** 以下字段只有本站 metadata / GitHub API 提供时才存在 */
  readonly updatedAt?: string;
  readonly generatedAt?: string;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function asNonEmptyString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}

/** 归一化单个附件；本站 metadata 用 downloadUrl，GitHub API 用 browser_download_url */
function normalizeAsset(value: unknown): ReleaseAsset | null {
  const asset = asRecord(value);
  if (!asset) return null;

  const name = asNonEmptyString(asset["name"]);
  const downloadUrl =
    asNonEmptyString(asset["downloadUrl"]) ?? asNonEmptyString(asset["browser_download_url"]);
  const size = asset["size"];

  if (!name || !downloadUrl) return null;
  if (typeof size !== "number" || !Number.isFinite(size) || size <= 0) return null;

  return { name, size, downloadUrl };
}

function normalizeAssets(rawAssets: unknown): ReleaseAsset[] {
  if (!Array.isArray(rawAssets)) return [];

  const assets: ReleaseAsset[] = [];
  for (const item of rawAssets) {
    const asset = normalizeAsset(item);
    if (asset) assets.push(asset);
  }
  return assets;
}

/** 校验本站静态 metadata。 */
export function parseLocalMetadata(payload: unknown): ReleaseInfo | null {
  const root = asRecord(payload);
  if (!root) return null;

  const tag = asNonEmptyString(root["tag"]);
  if (!tag) return null;

  return {
    tag,
    publishedAt: asNonEmptyString(root["publishedAt"]) ?? "",
    htmlUrl: asNonEmptyString(root["htmlUrl"]) ?? RELEASES_LATEST_URL,
    assets: normalizeAssets(root["assets"]),
    updatedAt: asNonEmptyString(root["updatedAt"]) ?? undefined,
    generatedAt: asNonEmptyString(root["generatedAt"]) ?? undefined,
  };
}

/** 校验 GitHub API 的原始响应；字段不完整时返回 null。 */
export function parseReleasePayload(payload: unknown): ReleaseInfo | null {
  const root = asRecord(payload);
  if (!root) return null;

  const tag = asNonEmptyString(root["tag_name"]);
  if (!tag) return null;

  return {
    tag,
    publishedAt: asNonEmptyString(root["published_at"]) ?? "",
    htmlUrl: asNonEmptyString(root["html_url"]) ?? RELEASES_LATEST_URL,
    assets: normalizeAssets(root["assets"]),
  };
}

/** 校验缓存里的数据，避免旧格式或损坏内容被当成有效结果。 */
function reviveRelease(value: unknown): ReleaseInfo | null {
  const root = asRecord(value);
  if (!root) return null;

  const tag = asNonEmptyString(root["tag"]);
  if (!tag) return null;

  return {
    tag,
    publishedAt: asNonEmptyString(root["publishedAt"]) ?? "",
    htmlUrl: asNonEmptyString(root["htmlUrl"]) ?? RELEASES_LATEST_URL,
    assets: normalizeAssets(root["assets"]),
    updatedAt: asNonEmptyString(root["updatedAt"]) ?? undefined,
    generatedAt: asNonEmptyString(root["generatedAt"]) ?? undefined,
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

/** 本站静态 metadata：同源、约 1KB，因此每次都取最新的，不做前置缓存。 */
async function requestLocalMetadata(): Promise<ReleaseInfo | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), LOCAL_TIMEOUT_MS);

  try {
    const response = await fetch(LOCAL_METADATA_URL, {
      signal: controller.signal,
      headers: { Accept: "application/json" },
      credentials: "omit",
      referrerPolicy: "no-referrer",
      // 同一个 tag 下重新上传 APK 后，重新部署的 metadata 必须能立刻生效
      cache: "no-store",
    });

    if (!response.ok) return null;

    const payload: unknown = await response.json();
    return parseLocalMetadata(payload);
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function requestLatestRelease(): Promise<ReleaseInfo | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), API_TIMEOUT_MS);

  try {
    const response = await fetch(LATEST_RELEASE_API, {
      signal: controller.signal,
      headers: { Accept: "application/vnd.github+json" },
      credentials: "omit",
      cache: "no-store",
      referrerPolicy: "no-referrer",
    });

    if (!response.ok) return null;

    const payload: unknown = await response.json();
    return parseReleasePayload(payload);
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function resolveLatestRelease(): Promise<ReleaseInfo | null> {
  const local = await requestLocalMetadata();
  if (local) {
    writeCache(local);
    return local;
  }

  const cached = readCache();
  if (cached) return cached;

  const api = await requestLatestRelease();
  if (api) {
    writeCache(api);
    return api;
  }

  return null;
}

let pending: Promise<ReleaseInfo | null> | null = null;

/**
 * 读取最新 Release。任何异常都折叠成 null，调用方直接沿用静态 fallback。
 * 同一页面内只解析一次。
 */
export function loadLatestRelease(): Promise<ReleaseInfo | null> {
  pending ??= resolveLatestRelease();
  return pending;
}

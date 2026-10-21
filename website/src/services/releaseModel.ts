/** Shared by browser, generator and Node 24 tests. */
export const REPO_SLUG = "puman233/SPlayer-for-Android-Fork";
export const REPO_URL = `https://github.com/${REPO_SLUG}`;
export const RELEASES_URL = `${REPO_URL}/releases`;
export const RELEASES_LATEST_URL = `${RELEASES_URL}/latest`;
export type Channel = "stable" | "beta";
export interface ReleaseAsset {
  readonly name: string;
  readonly size: number;
  readonly downloadUrl: string;
  readonly digest?: string;
}
export interface ReleaseInfo {
  readonly tag: string;
  readonly publishedAt: string;
  readonly htmlUrl: string;
  readonly assets: readonly ReleaseAsset[];
  readonly body: string;
  readonly prerelease: boolean;
}
export interface ReleaseCatalog {
  readonly schemaVersion: 2;
  readonly generatedAt: string;
  readonly stable: ReleaseInfo | null;
  readonly beta: ReleaseInfo | null;
}
const record = (v: unknown): Record<string, unknown> | null =>
  v !== null && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
const validDate = (v: unknown): v is string =>
  typeof v === "string" && Number.isFinite(Date.parse(v));
function trustedUrl(value: unknown, prefix: string): value is string {
  if (typeof value !== "string" || !value.startsWith(prefix)) return false;
  try {
    const url = new URL(value);
    return (
      url.origin === "https://github.com" &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash &&
      !/%2f|%5c|%2e/i.test(url.pathname)
    );
  } catch {
    return false;
  }
}
/** Only published APK releases and repository-owned URLs enter the UI. */
export function parseRelease(value: unknown, local = false): ReleaseInfo | null {
  const v = record(value);
  if (!v || v.draft === true || typeof v.prerelease !== "boolean") return null;
  const tag = local ? v.tag : v.tag_name;
  const publishedAt = local ? v.publishedAt : v.published_at;
  const htmlUrl = local ? v.htmlUrl : v.html_url;
  if (typeof tag !== "string" || !/^v?\d+\.\d+\.\d+(?:-beta\.\d+)?$/.test(tag)) return null;
  if (v.prerelease !== /-beta\.\d+$/.test(tag)) return null;
  if (!validDate(publishedAt) || Date.parse(publishedAt) > Date.now() + 300000) return null;
  if (
    !trustedUrl(htmlUrl, `${RELEASES_URL}/tag/${tag}`) ||
    htmlUrl !== `${RELEASES_URL}/tag/${tag}`
  )
    return null;
  const assets: ReleaseAsset[] = [];
  for (const raw of Array.isArray(v.assets) ? v.assets : []) {
    const a = record(raw);
    if (!a) continue;
    const name = a.name,
      size = a.size,
      downloadUrl = local ? a.downloadUrl : a.browser_download_url;
    if (
      typeof name !== "string" ||
      /debug|unsigned|[/\\]/i.test(name) ||
      !/(?:^|[-.])(arm64-v8a|armeabi-v7a|x86_64|x86)(?:[-.]|$)/i.test(name) ||
      !/\.apk$/i.test(name)
    )
      continue;
    if (typeof size !== "number" || !Number.isSafeInteger(size) || size <= 0) continue;
    if (!local && a.state !== "uploaded") continue;
    const prefix = `${RELEASES_URL}/download/${tag}/`;
    if (
      !trustedUrl(downloadUrl, prefix) ||
      downloadUrl.slice(prefix.length) !== encodeURIComponent(name)
    )
      continue;
    const digest =
      typeof a.digest === "string" && /^sha256:[a-f0-9]{64}$/i.test(a.digest)
        ? a.digest
        : undefined;
    assets.push({ name, size, downloadUrl, ...(digest ? { digest } : {}) });
  }
  if (!assets.length) return null;
  return {
    tag,
    publishedAt,
    htmlUrl,
    assets,
    prerelease: v.prerelease,
    body: typeof v.body === "string" ? v.body.slice(0, 20000) : "",
  };
}
export function selectReleases(
  payload: unknown,
  generatedAt = new Date().toISOString(),
): ReleaseCatalog {
  if (!Array.isArray(payload)) throw new Error("Release list must be an array");
  const releases = payload
    .map((v) => parseRelease(v))
    .filter((v): v is ReleaseInfo => v !== null)
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
  return {
    schemaVersion: 2,
    generatedAt,
    stable: releases.find((r) => !r.prerelease) ?? null,
    beta: releases.find((r) => r.prerelease) ?? null,
  };
}
export function parseCatalog(value: unknown): ReleaseCatalog | null {
  const v = record(value);
  if (
    !v ||
    v.schemaVersion !== 2 ||
    !validDate(v.generatedAt) ||
    Date.parse(v.generatedAt) > Date.now() + 300000
  )
    return null;
  const stable = parseRelease(v.stable, true),
    beta = parseRelease(v.beta, true);
  if ((stable && stable.prerelease) || (beta && !beta.prerelease) || (!stable && !beta))
    return null;
  return { schemaVersion: 2, generatedAt: v.generatedAt, stable, beta };
}

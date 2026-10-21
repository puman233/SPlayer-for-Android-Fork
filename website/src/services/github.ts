import { parseCatalog, selectReleases, REPO_SLUG } from "./releaseModel";
import type { ReleaseCatalog } from "./releaseModel";
export * from "./releaseModel";
export const LOCAL_METADATA_URL = `${import.meta.env.BASE_URL}latest-release.json`;
const CACHE_KEY = "splayer-fork-website:catalog-v2";
export interface CatalogResult {
  readonly catalog: ReleaseCatalog;
  readonly source: "snapshot" | "api" | "cache";
}
async function request(url: string): Promise<unknown> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 7000);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      cache: "no-store",
      credentials: "omit",
      referrerPolicy: "no-referrer",
      headers: { Accept: "application/json" },
    });
    return response.ok ? await response.json() : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}
async function resolveCatalog(): Promise<CatalogResult | null> {
  let catalog = parseCatalog(await request(LOCAL_METADATA_URL));
  let source: CatalogResult["source"] = "snapshot";
  const stale = catalog && Date.now() - Date.parse(catalog.generatedAt) > 86400000;
  if (!catalog || stale) {
    const payload = await request(
      `https://api.github.com/repos/${REPO_SLUG}/releases?per_page=100`,
    );
    if (Array.isArray(payload)) {
      const api = selectReleases(payload);
      if (api.stable || api.beta) {
        catalog = api;
        source = "api";
      }
    }
  }
  if (catalog) {
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify(catalog));
    } catch {
      /* optional */
    }
    return { catalog, source };
  }
  try {
    catalog = parseCatalog(JSON.parse(sessionStorage.getItem(CACHE_KEY) ?? "null"));
    if (catalog && Date.now() - Date.parse(catalog.generatedAt) < 7 * 86400000)
      return { catalog, source: "cache" };
  } catch {
    /* unavailable or corrupt cache */
  }
  return null;
}
let pending: Promise<CatalogResult | null> | null = null;
export function loadReleaseCatalog(): Promise<CatalogResult | null> {
  pending ??= resolveCatalog();
  return pending;
}

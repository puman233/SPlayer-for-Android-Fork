/**
 * 下载线路服务层。
 *
 * 职责：构建下载 URL、探测单条线路、并发探测、排序推荐、缓存与手动选择状态。
 * UI 只消费结果，不直接发请求。
 *
 * 探测方式：`GET` + `Range: bytes=0-0`（实测加速线路支持 206 + Content-Range，
 * GitHub 原始线路不支持跨域读取）。HEAD 实测部分代理超时，因此不作为判据。
 *
 * 注意：`Range: bytes=0-0` 测到的是握手 + 服务端响应 + 首包延迟，
 * 不是真实下载吞吐。因此对外只使用「响应时间 / 延迟」这类措辞。
 */

import { AUTO_ROUTE_ID, ORIGINAL_ROUTE_ID, getProbeRoutes, getRoute } from "../data/downloadRoutes";
import type { DownloadRouteConfig } from "../data/downloadRoutes";

export type RouteState = "idle" | "probing" | "available" | "manual-only" | "unavailable";

export interface RouteProbeResult {
  readonly id: string;
  /** 毫秒；探测失败为 null */
  readonly latency: number | null;
  /** 是否可进入自动推荐池 */
  readonly available: boolean;
  /** 是否做过可读探测（配置关闭 autoProbe 时为 false） */
  readonly probeable: boolean;
  readonly state: RouteState;
}

export interface RouteSelectionCache {
  readonly selectedRouteId: string;
  readonly checkedAt: number;
  readonly results: readonly RouteProbeResult[];
}

/** 单线路探测超时：实测线路稳定响应在 200–900ms，3.5s 足够区分不可用线路 */
export const PROBE_TIMEOUT_MS = 3500;
export const ROUTE_CACHE_TTL_MS = 20 * 60 * 1000;

const CACHE_PREFIX = "splayer-download-route";
const MANUAL_KEY = "splayer-download-route-manual";
const PROBE_BYTE_CAP = 2048;

/** GitHub 优先保护阈值 */
const ORIGINAL_PREFER_LATENCY_MS = 800;
const ORIGINAL_PREFER_MARGIN_MS = 150;
/** 延迟接近时按固定优先级，避免每次刷新换线路 */
const TIE_MARGIN_MS = 80;

/** 构建线路下载地址。代理要求原样拼接原始 URL，不能整体编码。 */
export function buildDownloadUrl(originalUrl: string, route: DownloadRouteConfig): string {
  if (!route.baseUrl) return originalUrl;
  const base = route.baseUrl.endsWith("/") ? route.baseUrl : `${route.baseUrl}/`;
  return `${base}${originalUrl}`;
}

function unavailable(
  id: string,
  probeable: boolean,
  latency: number | null = null,
): RouteProbeResult {
  return { id, latency, available: false, probeable, state: "unavailable" };
}

function manualOnly(id: string): RouteProbeResult {
  return { id, latency: null, available: false, probeable: false, state: "manual-only" };
}

/**
 * 只读极少量字节。超过上限说明代理忽略了 Range 并准备返回整个 APK，
 * 立即取消流，避免消耗用户流量。返回 null 表示已按「失败」处理。
 */
async function readSample(response: Response): Promise<Uint8Array | null> {
  if (!response.body) return new Uint8Array();

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        received += value.byteLength;
        chunks.push(value);
      }
      if (received > PROBE_BYTE_CAP) {
        await reader.cancel();
        return null;
      }
    }
  } catch {
    await reader.cancel().catch(() => undefined);
    return null;
  }

  const merged = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return merged;
}

function looksLikeHtml(sample: Uint8Array, contentType: string | null): boolean {
  if (contentType && contentType.toLowerCase().includes("text/html")) return true;
  const head = new TextDecoder("utf-8", { fatal: false })
    .decode(sample.slice(0, 64))
    .trim()
    .toLowerCase();
  return head.startsWith("<!doctype html") || head.startsWith("<html") || head.startsWith("<?xml");
}

/**
 * opaque 可达性判别：仅在可读探测失败后调用一次，
 * 只用来区分「被 CORS 拦（可手动下载）」和「根本连不上」。
 * 它的耗时一律不参与自动排序。
 */
async function isReachableOpaque(url: string, signal: AbortSignal): Promise<boolean> {
  try {
    const response = await fetch(url, {
      method: "GET",
      mode: "no-cors",
      headers: { Range: "bytes=0-0" },
      credentials: "omit",
      cache: "no-store",
      signal,
    });
    await response.body?.cancel().catch(() => undefined);
    return true;
  } catch {
    return false;
  }
}

/** 探测单条线路。每条线路使用独立 AbortController，互不影响。 */
export async function probeRoute(
  route: DownloadRouteConfig,
  originalUrl: string,
  timeoutMs: number = PROBE_TIMEOUT_MS,
): Promise<RouteProbeResult> {
  if (!route.enabled) return unavailable(route.id, false);
  if (!route.autoProbe) return manualOnly(route.id);

  const url = buildDownloadUrl(originalUrl, route);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const started = performance.now();

  try {
    const response = await fetch(url, {
      method: "GET",
      headers: { Range: "bytes=0-0" },
      signal: controller.signal,
      mode: "cors",
      credentials: "omit",
      cache: "no-store",
      redirect: "follow",
    });

    const latency = Math.round(performance.now() - started);
    const contentType = response.headers.get("content-type");

    if (response.status !== 200 && response.status !== 206) {
      await response.body?.cancel().catch(() => undefined);
      return unavailable(route.id, true, latency);
    }

    if (response.status === 206) {
      const contentRange = response.headers.get("content-range") ?? "";
      if (!/^bytes 0-0\/\d+$/.test(contentRange.trim())) {
        await response.body?.cancel().catch(() => undefined);
        return unavailable(route.id, true, latency);
      }
    }

    if (response.status === 200 && contentType && contentType.toLowerCase().includes("text/html")) {
      await response.body?.cancel().catch(() => undefined);
      return unavailable(route.id, true, latency);
    }

    const sample = await readSample(response);
    if (sample === null) return unavailable(route.id, true, latency);
    if (looksLikeHtml(sample, contentType)) return unavailable(route.id, true, latency);

    return { id: route.id, latency, available: true, probeable: true, state: "available" };
  } catch {
    // 可读探测失败：用一次 opaque 请求区分 CORS 受限与完全不可达
    const reachable = await isReachableOpaque(url, controller.signal);
    return reachable ? manualOnly(route.id) : unavailable(route.id, true);
  } finally {
    clearTimeout(timer);
  }
}

/** 并发探测所有自动池线路，整体耗时约等于最慢的一条。 */
export async function probeRoutes(
  routes: readonly DownloadRouteConfig[],
  originalUrl: string,
  timeoutMs: number = PROBE_TIMEOUT_MS,
): Promise<RouteProbeResult[]> {
  const settled = await Promise.allSettled(
    routes.map((route) => probeRoute(route, originalUrl, timeoutMs)),
  );

  return settled.map((entry, index) => {
    const route = routes[index];
    if (entry.status === "fulfilled") return entry.value;
    return unavailable(route ? route.id : `route-${index}`, true);
  });
}

export function probeConfiguredRoutes(
  originalUrl: string,
  timeoutMs: number = PROBE_TIMEOUT_MS,
): Promise<RouteProbeResult[]> {
  return probeRoutes(getProbeRoutes(), originalUrl, timeoutMs);
}

/**
 * 自动选线：
 * 1. 过滤不可用 / 不可探测的线路
 * 2. 按延迟升序
 * 3. GitHub 原始线路优先保护（仅在它可探测时生效）
 * 4. 全部代理失败 → 回到 GitHub 原始线路
 * 5. 延迟接近时按固定优先级，避免抖动
 */
export function selectBestRoute(results: readonly RouteProbeResult[]): string {
  const original = results.find((item) => item.id === ORIGINAL_ROUTE_ID);

  const candidates = results
    .filter((item) => item.probeable && item.available && item.latency !== null)
    .map((item) => ({ result: item, route: getRoute(item.id) }))
    .filter(
      (item): item is { result: RouteProbeResult; route: DownloadRouteConfig } =>
        item.route !== undefined && !item.route.original,
    )
    .sort(
      (a, b) =>
        (a.result.latency ?? 0) - (b.result.latency ?? 0) || a.route.priority - b.route.priority,
    );

  const best = candidates[0];
  if (!best) return ORIGINAL_ROUTE_ID;

  const bestLatency = best.result.latency ?? 0;

  if (original && original.probeable && original.available && original.latency !== null) {
    const slowerEnough = bestLatency <= original.latency - ORIGINAL_PREFER_MARGIN_MS;
    if (original.latency <= ORIGINAL_PREFER_LATENCY_MS && !slowerEnough) {
      return ORIGINAL_ROUTE_ID;
    }
  }

  const near = candidates.filter(
    (item) => (item.result.latency ?? 0) - bestLatency < TIE_MARGIN_MS,
  );
  return near.reduce(
    (acc, item) => (item.route.priority < acc.route.priority ? item : acc),
    near[0] ?? best,
  ).route.id;
}

function isProbeResult(value: unknown): value is RouteProbeResult {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return (
    typeof record["id"] === "string" &&
    (record["latency"] === null || typeof record["latency"] === "number") &&
    typeof record["available"] === "boolean" &&
    typeof record["probeable"] === "boolean" &&
    typeof record["state"] === "string"
  );
}

/** 缓存 key 必须带 Release tag，换版本后自然失效。 */
export function routeCacheKey(tag: string): string {
  return `${CACHE_PREFIX}-${tag}`;
}

export function readRouteCache(tag: string): RouteSelectionCache | null {
  try {
    const raw = sessionStorage.getItem(routeCacheKey(tag));
    if (!raw) return null;

    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null) return null;
    const record = parsed as Record<string, unknown>;

    const checkedAt = record["checkedAt"];
    const selectedRouteId = record["selectedRouteId"];
    const results = record["results"];

    if (typeof checkedAt !== "number" || Date.now() - checkedAt > ROUTE_CACHE_TTL_MS) return null;
    if (typeof selectedRouteId !== "string") return null;
    if (!Array.isArray(results) || !results.every(isProbeResult)) return null;

    return { selectedRouteId, checkedAt, results };
  } catch {
    return null;
  }
}

export function writeRouteCache(tag: string, selection: RouteSelectionCache): void {
  try {
    sessionStorage.setItem(routeCacheKey(tag), JSON.stringify(selection));
  } catch {
    // 隐私模式或存储已满时忽略，缓存不是必需品。
  }
}

/** 手动选择只在本次会话内尊重，刷新后仍保留。 */
export function readManualRouteId(): string | null {
  try {
    const value = sessionStorage.getItem(MANUAL_KEY);
    if (!value) return null;
    return value === AUTO_ROUTE_ID ? null : value;
  } catch {
    return null;
  }
}

export function writeManualRouteId(routeId: string): void {
  try {
    if (routeId === AUTO_ROUTE_ID) sessionStorage.removeItem(MANUAL_KEY);
    else sessionStorage.setItem(MANUAL_KEY, routeId);
  } catch {
    // 忽略存储异常。
  }
}

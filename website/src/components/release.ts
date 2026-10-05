/**
 * 下载区块。
 *
 * 页面 HTML 里已经带了一份可用的静态 fallback（架构说明 + 前往 Releases 的入口）。
 * 这里只在 GitHub API 成功返回时把它升级成带版本、日期、大小和直链的表格；
 * 失败时什么都不做，因此不会出现 loading、白屏或错误弹窗。
 *
 * 下载线路：拿到 Release 附件后，为每条线路现算下载地址（不预先写死所有 URL）。
 * 探测在下载区进入视口后才开始，全部逻辑在 services/downloadRoutes.ts。
 */

import { loadLatestRelease } from "../services/github";
import type { ReleaseAsset, ReleaseInfo } from "../services/github";
import {
  AUTO_ROUTE_ID,
  ORIGINAL_ROUTE_ID,
  getRoute,
  isSelectableRoute,
} from "../data/downloadRoutes";
import {
  buildDownloadUrl,
  probeConfiguredRoutes,
  readManualRouteId,
  readRouteCache,
  selectBestRoute,
  writeManualRouteId,
  writeRouteCache,
} from "../services/downloadRoutes";
import type { RouteProbeResult } from "../services/downloadRoutes";
import { mountRouteSelector } from "./downloadRouteSelector";
import type { RouteSelectorHandle } from "./downloadRouteSelector";
import { ABI_SPECS, assetMatchesAbi, isInstallableApk } from "../utils/abi";
import { formatBytes, formatDate } from "../utils/format";

/** 只用推荐架构做代表文件探测，结果应用于全部 ABI */
const PROBE_ABI = "arm64-v8a";
const IDLE_FALLBACK_DELAY_MS = 1500;

interface DownloadState {
  readonly tag: string;
  readonly originalUrls: Map<string, string>;
  readonly links: Map<string, HTMLAnchorElement>;
  /** 用户当前选择（可能是 AUTO_ROUTE_ID） */
  routeId: string;
  /** 实际生效的线路 id */
  resolvedRouteId: string;
}

let state: DownloadState | null = null;
let selector: RouteSelectorHandle | null = null;
let observer: IntersectionObserver | null = null;
let probing = false;

export function mountReleasePanel(): void {
  const panel = document.getElementById("release-panel");
  if (!panel) return;

  void loadLatestRelease().then((release) => {
    if (!release) return;
    applyRelease(panel, release);
  });
}

function applyRelease(panel: HTMLElement, release: ReleaseInfo): void {
  const candidates = release.assets.filter((asset) => isInstallableApk(asset.name));

  const matched = new Map<string, ReleaseAsset>();
  for (const spec of ABI_SPECS) {
    const asset = candidates.find((item) => assetMatchesAbi(item.name, spec.id));
    if (asset) matched.set(spec.id, asset);
  }

  // 一个可识别的 APK 都没有时保持 fallback，避免出现空表格。
  if (matched.size === 0) return;

  const originalUrls = new Map<string, string>();
  const links = new Map<string, HTMLAnchorElement>();

  for (const row of panel.querySelectorAll<HTMLTableRowElement>("tr[data-abi]")) {
    const abi = row.dataset["abi"];
    const asset = abi ? matched.get(abi) : undefined;

    if (!asset) {
      // 当前 Release 缺少该架构：只隐藏这一行，不提示错误。
      row.hidden = true;
      continue;
    }

    const sizeCell = row.querySelector<HTMLElement>("[data-size]");
    if (sizeCell) sizeCell.textContent = formatBytes(asset.size) || "—";

    const link = createDownloadLink(asset);
    const actionCell = row.querySelector<HTMLElement>("[data-action]");
    if (actionCell) actionCell.replaceChildren(link);

    if (abi) {
      links.set(abi, link);
      originalUrls.set(abi, asset.downloadUrl);
    }
  }

  const tagSlot = panel.querySelector<HTMLElement>("#release-tag");
  if (tagSlot) {
    const link = document.createElement("a");
    link.href = release.htmlUrl;
    link.rel = "noopener";
    link.textContent = release.tag;
    tagSlot.replaceChildren(link);
  }

  const dateSlot = panel.querySelector<HTMLElement>("#release-date");
  if (dateSlot) {
    const formatted = formatDate(release.publishedAt);
    dateSlot.textContent = formatted ? `发布于 ${formatted}` : "";
  }

  panel.dataset["mode"] = "release";

  state = {
    tag: release.tag,
    originalUrls,
    links,
    routeId: AUTO_ROUTE_ID,
    resolvedRouteId: ORIGINAL_ROUTE_ID,
  };
  setupRoutes(panel);
}

function createDownloadLink(asset: ReleaseAsset): HTMLAnchorElement {
  const link = document.createElement("a");
  link.className = "btn btn-ghost btn-sm";
  link.href = asset.downloadUrl;
  link.rel = "noopener";
  link.textContent = "下载";
  link.setAttribute("aria-label", `下载 ${asset.name}`);
  return link;
}

/* ---------- 下载线路 ---------- */

function activeRouteId(): string {
  if (!state) return ORIGINAL_ROUTE_ID;
  return state.routeId === AUTO_ROUTE_ID ? state.resolvedRouteId : state.routeId;
}

/** 按当前线路为每个 ABI 现算下载地址 */
function refreshDownloadLinks(): void {
  if (!state) return;

  const route = getRoute(activeRouteId()) ?? getRoute(ORIGINAL_ROUTE_ID);
  if (!route) return;

  for (const [abi, link] of state.links) {
    const original = state.originalUrls.get(abi);
    if (original) link.href = buildDownloadUrl(original, route);
  }
}

function setupRoutes(panel: HTMLElement): void {
  if (!state) return;

  selector = selector ?? mountRouteSelector(onRouteSelected);
  if (!selector) return;
  selector.show();

  const manual = readManualRouteId();
  if (manual && manual !== AUTO_ROUTE_ID && isSelectableRoute(manual)) {
    state.routeId = manual;
    state.resolvedRouteId = manual;
    selector.setValue(manual);
    selector.showManual(manual);
    refreshDownloadLinks();
    return;
  }

  const cached = readRouteCache(state.tag);
  if (cached) {
    applySelection(cached.selectedRouteId, cached.results);
    return;
  }

  scheduleProbe(panel);
}

/** 下载区进入视口后才探测；不支持 IntersectionObserver 时退化为延迟 1.5 秒 */
function scheduleProbe(panel: HTMLElement): void {
  if (probing) return;

  const start = (): void => {
    void runProbe();
  };

  if (typeof IntersectionObserver === "undefined") {
    window.setTimeout(start, IDLE_FALLBACK_DELAY_MS);
    return;
  }

  observer?.disconnect();
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return;
      observer?.disconnect();
      observer = null;
      start();
    },
    { rootMargin: "0px 0px -10% 0px" },
  );

  observer.observe(document.getElementById("download") ?? panel);
}

async function runProbe(): Promise<void> {
  if (!state || probing) return;

  const original = state.originalUrls.get(PROBE_ABI) ?? [...state.originalUrls.values()][0];
  if (!original) return;

  const tag = state.tag;
  probing = true;
  selector?.showProbing();

  try {
    const results = await probeConfiguredRoutes(original);
    if (!state || state.tag !== tag) return;

    const best = selectBestRoute(results);
    writeRouteCache(tag, { selectedRouteId: best, checkedAt: Date.now(), results });
    applySelection(best, results);
  } finally {
    probing = false;
  }
}

function applySelection(routeId: string, results: readonly RouteProbeResult[]): void {
  if (!state || !selector) return;

  state.resolvedRouteId = routeId;
  refreshDownloadLinks();

  if (state.routeId !== AUTO_ROUTE_ID) {
    selector.showManual(state.routeId);
    return;
  }

  const hasUsableProxy = results.some((item) => item.probeable && item.available);
  const latency = results.find((item) => item.id === routeId)?.latency ?? null;
  selector.showAutoResult(routeId, latency, !hasUsableProxy);
}

/** 用户手动选线后不再自动覆盖，本次会话内一直尊重该选择 */
function onRouteSelected(routeId: string): void {
  if (!state || !selector) return;

  writeManualRouteId(routeId);

  if (routeId !== AUTO_ROUTE_ID && isSelectableRoute(routeId)) {
    state.routeId = routeId;
    state.resolvedRouteId = routeId;
    refreshDownloadLinks();
    selector.showManual(routeId);
    return;
  }

  state.routeId = AUTO_ROUTE_ID;
  const cached = readRouteCache(state.tag);
  if (cached) {
    applySelection(cached.selectedRouteId, cached.results);
    return;
  }

  void runProbe();
}

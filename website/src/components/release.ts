/** Download UI: shared channel renderer with IP-country default and manual override. */
import { loadReleaseCatalog, RELEASES_URL } from "../services/github";
import type { Channel, ReleaseInfo } from "../services/github";
import { getRoute, getProbeRoutes, ORIGINAL_ROUTE_ID, AUTO_ROUTE_ID } from "../data/downloadRoutes";
import { detectCountry } from "../services/region";
import { createReleaseNotes } from "./releaseNotes";
import {
  buildDownloadUrl,
  probeConfiguredRoutes,
  selectBestRoute,
} from "../services/downloadRoutes";
import { mountRouteSelector } from "./downloadRouteSelector";
import { assetMatchesAbi } from "../utils/abi";
import { formatBytes, formatDate } from "../utils/format";

export function mountReleasePanel(): void {
  const panel = document.getElementById("release-panel");
  const status = document.getElementById("release-status");
  if (!panel || !status) return;
  let current: ReleaseInfo | null = null;
  let routeId = ORIGINAL_ROUTE_ID;
  let defaultRouteId = ORIGINAL_ROUTE_ID;
  let manualSelection = false;
  let generation = 0;
  const refreshLinks = (): void => {
    const route = getRoute(routeId) ?? getRoute(ORIGINAL_ROUTE_ID);
    if (!route) return;
    for (const link of panel.querySelectorAll<HTMLAnchorElement>("a[data-original]")) {
      link.href = buildDownloadUrl(link.dataset["original"] ?? "", route);
    }
  };
  const selector = mountRouteSelector((selected) => {
    manualSelection = true;
    generation++;
    if (selected !== AUTO_ROUTE_ID) {
      routeId = selected;
      refreshLinks();
      selector?.showManual(selected);
      return;
    }
    const asset = current?.assets[0];
    if (!asset) return;
    const token = generation;
    selector?.showProbing();
    void probeConfiguredRoutes(asset.downloadUrl)
      .then((results) => {
        if (token !== generation) return;
        routeId = selectBestRoute(results);
        refreshLinks();
        selector?.showAutoResult(
          routeId,
          results.find((r) => r.id === routeId)?.latency ?? null,
          !results.some((r) => r.available),
        );
      })
      .catch(() => {
        if (token !== generation) return;
        routeId = ORIGINAL_ROUTE_ID;
        refreshLinks();
        selector?.showManual(routeId);
      });
  });
  const render = (release: ReleaseInfo | null, channel: Channel): void => {
    generation++;
    current = release;
    if (!manualSelection) routeId = defaultRouteId;
    selector?.setValue(routeId);
    selector?.showManual(routeId);
    panel.dataset["mode"] = release ? "release" : "fallback";
    const tag = document.getElementById("release-tag");
    const date = document.getElementById("release-date");
    if (tag) tag.textContent = release?.tag ?? "暂无可用的已验证安装包";
    if (date) date.textContent = release ? `发布于 ${formatDate(release.publishedAt)}` : "";
    if (selector && release) selector.show();
    const picker = document.getElementById("route-picker");
    if (picker) picker.hidden = !release;
    for (const row of panel.querySelectorAll<HTMLTableRowElement>("tr[data-abi]")) {
      const abi = row.dataset["abi"] ?? "";
      const asset = release?.assets.find((a) => assetMatchesAbi(a.name, abi));
      row.hidden = release !== null && !asset;
      const size = row.querySelector<HTMLElement>("[data-size]");
      if (size) size.textContent = asset ? formatBytes(asset.size) : "—";
      const action = row.querySelector<HTMLElement>("[data-action]");
      if (!action) continue;
      action.replaceChildren();
      if (!asset) continue;
      const link = document.createElement("a");
      link.className = "btn btn-ghost btn-sm";
      link.href = asset.downloadUrl;
      link.dataset["original"] = asset.downloadUrl;
      link.textContent = "下载 APK";
      link.setAttribute("aria-label", `下载 ${asset.name}`);
      const filename = document.createElement("span");
      filename.className = "asset-name";
      filename.textContent = asset.name;
      const details = document.createElement("details");
      details.className = "digest";
      const summary = document.createElement("summary");
      summary.textContent = "SHA-256";
      const code = document.createElement("code");
      code.textContent =
        asset.digest?.replace(/^sha256:/i, "") ??
        "发布来源未提供摘要，请查看 Release 附件中的校验资料。";
      details.append(summary, code);
      action.append(link, filename, details);
    }
    // Newly rendered channel links must inherit the resolved or manually chosen route.
    refreshLinks();
    for (const link of panel.querySelectorAll<HTMLAnchorElement>("[data-release-link]")) {
      link.href = release?.htmlUrl ?? RELEASES_URL;
      link.textContent = release ? "查看此版本发布说明" : "前往 GitHub Releases 查找版本";
    }
    const warning = document.getElementById("channel-warning");
    if (warning)
      warning.textContent =
        channel === "beta"
          ? "Beta 为测试版本，可能存在问题；安装前请阅读发布说明并备份重要数据。"
          : "默认推荐稳定版。请按设备实际 ABI 选择，网站不会通过浏览器推测架构。";
  };
  void loadReleaseCatalog()
    .then((result) => {
      if (!result) {
        status.textContent = "版本数据暂时不可用，请前往 GitHub Releases 下载。";
        return;
      }
      const { catalog, source } = result;
      status.textContent =
        source === "api"
          ? "数据来自本次 GitHub API 查询。"
          : `${source === "cache" ? "会话缓存" : "站点快照"} · 生成于 ${new Date(catalog.generatedAt).toLocaleString("zh-CN")}；可能不是最新，请核对发布页。`;
      render(catalog.stable, "stable");
      const choice = document.getElementById("release-channel");
      if (choice instanceof HTMLSelectElement) {
        choice.disabled = false;
        choice.addEventListener("change", () => {
          const channel: Channel = choice.value === "beta" ? "beta" : "stable";
          render(catalog[channel], channel);
        });
      }
      const hero = document.getElementById("hero-version");
      if (hero && catalog.stable) hero.textContent = `稳定版 ${catalog.stable.tag}（数据见下载区）`;
      const updates = document.getElementById("release-updates");
      if (updates) {
        for (const channel of ["stable", "beta"] as const) {
          const release = catalog[channel];
          if (!release) continue;
          const article = document.createElement("article");
          const title = document.createElement("h3");
          const link = document.createElement("a");
          link.href = release.htmlUrl;
          link.textContent = `${channel === "stable" ? "稳定版" : "Beta"} ${release.tag}`;
          title.append(link);
          const date = document.createElement("p");
          date.className = "muted";
          date.textContent = formatDate(release.publishedAt);
          const details = createReleaseNotes(release);
          article.append(title, date, details);
          updates.append(article);
        }
        updates.querySelector("[data-updates-fallback]")?.remove();
      }
    })
    .catch(() => {
      status.textContent = "版本读取失败，请使用 GitHub Releases 入口。";
    });
  // No geo banner or permission prompt. Explicit manual choices always win.
  void detectCountry().then((country) => {
    if (country !== "CN") return;
    defaultRouteId = getProbeRoutes()[0]?.id ?? ORIGINAL_ROUTE_ID;
    if (manualSelection) return;
    routeId = defaultRouteId;
    refreshLinks();
    selector?.setValue(routeId);
    selector?.showManual(routeId);
  });
}

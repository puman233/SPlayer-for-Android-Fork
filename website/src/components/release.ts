/**
 * 下载区块。
 *
 * 页面 HTML 里已经带了一份可用的静态 fallback（架构说明 + 前往 Releases 的入口）。
 * 这里只在 GitHub API 成功返回时把它升级成带版本、日期、大小和直链的表格；
 * 失败时什么都不做，因此不会出现 loading、白屏或错误弹窗。
 */

import { loadLatestRelease } from "../services/github";
import type { ReleaseAsset, ReleaseInfo } from "../services/github";
import { ABI_SPECS, assetMatchesAbi, isInstallableApk } from "../utils/abi";
import { formatBytes, formatDate } from "../utils/format";

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

    const actionCell = row.querySelector<HTMLElement>("[data-action]");
    if (actionCell) {
      actionCell.replaceChildren(createDownloadLink(asset));
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

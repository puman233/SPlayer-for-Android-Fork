import type { ReleaseInfo } from "../services/releaseModel";

/** Load parser/sanitizer only when notes are opened; failure keeps a source link. */
export function createReleaseNotes(release: ReleaseInfo): HTMLDetailsElement {
  const details = document.createElement("details");
  const summary = document.createElement("summary");
  summary.textContent = "展开发布说明";
  const content = document.createElement("div");
  let loaded = false;
  let pending = false;
  details.append(summary, content);
  details.addEventListener("toggle", () => {
    if (!details.open || loaded || pending) return;
    pending = true;
    content.textContent = "正在加载发布说明…";
    content.setAttribute("aria-busy", "true");
    void import("./markdown")
      .then(({ renderMarkdown }) => {
        content.replaceChildren(
          renderMarkdown(release.body || "此版本未提供正文，请打开版本链接查看。"),
        );
        loaded = true;
      })
      .catch(() => {
        const link = document.createElement("a");
        link.href = release.htmlUrl;
        link.textContent = "发布说明加载失败，前往 GitHub 查看";
        content.replaceChildren(link);
      })
      .finally(() => {
        pending = false;
        content.removeAttribute("aria-busy");
      });
  });
  return details;
}

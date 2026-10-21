/**
 * 界面预览与首页截图。
 *
 * 只渲染 `src/data/screenshots.ts` 中登记过的真实截图，图片全部随站点一起部署，
 * 不引用任何外部图床。清单为空时直接收起「界面预览」区块，不显示占位文字。
 */

import { SCREENSHOTS } from "../data/screenshots";
import type { ScreenshotEntry } from "../data/screenshots";

export function mountGallery(): void {
  const gallery = document.getElementById("shot-gallery");
  if (!gallery) return;

  if (SCREENSHOTS.length === 0) {
    document.getElementById("screenshots")?.remove();
    return;
  }

  const figures = document.createDocumentFragment();
  for (const entry of SCREENSHOTS) {
    figures.append(createFigure(entry));
  }

  gallery.replaceChildren(figures);
  gallery.dataset["state"] = "filled";

  mountPreview(gallery);

  mountHeroShots();
}

function screenshotUrl(file: string): string {
  return `${import.meta.env.BASE_URL}screenshots/${file}`;
}

function createFigure(entry: ScreenshotEntry): HTMLElement {
  const figure = document.createElement("figure");
  figure.dataset["category"] = entry.category;

  const image = document.createElement("img");
  image.src = screenshotUrl(entry.file);
  image.alt = `SPlayer ${entry.title}`;
  image.width = entry.width;
  image.height = entry.height;
  image.loading = "lazy";
  image.decoding = "async";

  const caption = document.createElement("figcaption");

  const title = document.createElement("span");
  title.className = "shot-title";
  title.textContent = entry.title;
  caption.append(title);

  if (entry.caption) {
    const note = document.createElement("span");
    note.className = "shot-note";
    note.textContent = entry.caption;
    caption.append(note);
  }

  const button = document.createElement("button");
  button.type = "button";
  button.className = "shot-open";
  button.setAttribute("aria-label", `放大预览：${entry.title}`);
  button.dataset["file"] = entry.file;
  button.append(image);
  figure.append(button, caption);
  return figure;
}

function mountPreview(gallery: HTMLElement): void {
  const dialog = document.createElement("dialog");
  dialog.className = "shot-dialog";
  dialog.setAttribute("aria-label", "应用界面预览");
  const toolbar = document.createElement("div");
  toolbar.className = "preview-toolbar";
  const title = document.createElement("span");
  const image = document.createElement("img");
  const controls = ["上一张", "下一张", "放大", "关闭"].map((text) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-ghost";
    button.textContent = text;
    return button;
  });
  toolbar.append(title, ...controls);
  const viewport = document.createElement("div");
  viewport.className = "preview-viewport";
  viewport.append(image);
  dialog.append(toolbar, viewport);
  document.body.append(dialog);
  let index = 0;
  let opener: HTMLElement | null = null;
  const show = (next: number): void => {
    index = (next + SCREENSHOTS.length) % SCREENSHOTS.length;
    const entry = SCREENSHOTS[index];
    if (!entry) return;
    image.src = screenshotUrl(entry.file);
    image.alt = `SPlayer ${entry.title}（既有界面参考）`;
    title.textContent = `${entry.title} ${index + 1}/${SCREENSHOTS.length}`;
    viewport.dataset["zoom"] = "false";
    controls[2]?.setAttribute("aria-pressed", "false");
    if (controls[2]) controls[2].textContent = "放大";
  };
  gallery.addEventListener("click", (event) => {
    const target =
      event.target instanceof Element
        ? event.target.closest<HTMLButtonElement>("button[data-file]")
        : null;
    if (!target) return;
    opener = target;
    show(SCREENSHOTS.findIndex((entry) => entry.file === target.dataset["file"]));
    dialog.showModal();
  });
  controls[0]?.addEventListener("click", () => show(index - 1));
  controls[1]?.addEventListener("click", () => show(index + 1));
  controls[2]?.addEventListener("click", () => {
    const zoom = viewport.dataset["zoom"] !== "true";
    viewport.dataset["zoom"] = String(zoom);
    controls[2]?.setAttribute("aria-pressed", String(zoom));
    if (controls[2]) controls[2].textContent = zoom ? "适应窗口" : "放大";
  });
  controls[3]?.addEventListener("click", () => dialog.close());
  dialog.addEventListener("close", () => opener?.focus());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });
  dialog.addEventListener("keydown", (event) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      show(index - 1);
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      show(index + 1);
    }
  });
}

/** 首页右侧：一张手机竖屏截图 + 一张宽屏截图，窄屏只保留手机截图。 */
function mountHeroShots(): void {
  const slot = document.getElementById("hero-shots");
  if (!slot) return;

  const phone =
    SCREENSHOTS.find((item) => item.hero === true && item.category === "mobile") ??
    SCREENSHOTS.find((item) => item.category === "mobile");

  const wide =
    SCREENSHOTS.find((item) => item.category === "desktop") ??
    SCREENSHOTS.find((item) => item.category === "landscape");

  if (!phone && !wide) return;

  const fragment = document.createDocumentFragment();
  if (phone) fragment.append(createHeroFigure(phone, "hero-figure--phone", true));
  if (wide) fragment.append(createHeroFigure(wide, "hero-figure--wide", false));

  slot.replaceChildren(fragment);
  slot.hidden = false;
  document.getElementById("hero-grid")?.setAttribute("data-has-shot", "true");
}

function createHeroFigure(
  entry: ScreenshotEntry,
  modifier: string,
  highPriority: boolean,
): HTMLElement {
  const figure = document.createElement("figure");
  figure.className = `hero-figure ${modifier}`;

  const image = document.createElement("img");
  image.src = screenshotUrl(entry.file);
  image.alt = `SPlayer ${entry.title}`;
  image.width = entry.width;
  image.height = entry.height;
  image.decoding = "async";

  if (highPriority) {
    image.loading = "eager";
    image.fetchPriority = "high";
  } else {
    // 宽屏截图在窄屏下不显示，用 lazy 避免手机上白下载。
    image.loading = "lazy";
  }

  figure.append(image);
  return figure;
}

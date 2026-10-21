import { marked } from "marked";
import DOMPurify from "dompurify";

/** GFM formatting with no active HTML, external image requests or embedded content. */
export function renderMarkdown(markdown: string): HTMLElement {
  const body = document.createElement("div");
  body.className = "release-markdown";
  const html = marked.parse(markdown.replace(/^[\u200B-\u200F\uFEFF]+/, ""), {
    async: false,
    gfm: true,
  });
  body.append(
    DOMPurify.sanitize(html, {
      RETURN_DOM_FRAGMENT: true,
      ALLOWED_TAGS: [
        "p",
        "br",
        "hr",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "strong",
        "em",
        "del",
        "s",
        "blockquote",
        "ul",
        "ol",
        "li",
        "pre",
        "code",
        "a",
        "table",
        "thead",
        "tbody",
        "tr",
        "th",
        "td",
        "input",
      ],
      ALLOWED_ATTR: ["href", "title", "start", "align", "type", "checked", "disabled"],
      ALLOW_DATA_ATTR: false,
      ALLOW_ARIA_ATTR: false,
    }),
  );
  // Resolve repository-relative references against the source repository, not Pages.
  const base = "https://github.com/puman233/SPlayer-for-Android-Fork/blob/dev/";
  for (const anchor of body.querySelectorAll<HTMLAnchorElement>("a")) {
    const raw = anchor.getAttribute("href");
    if (!raw) continue;
    try {
      const url = new URL(raw, base);
      if (!["https:", "http:"].includes(url.protocol)) anchor.removeAttribute("href");
      else {
        anchor.href = url.href;
        anchor.rel = "noopener noreferrer";
      }
    } catch {
      anchor.removeAttribute("href");
    }
  }
  for (const input of body.querySelectorAll<HTMLInputElement>("input")) {
    if (input.type !== "checkbox") input.remove();
    else {
      input.disabled = true;
      input.setAttribute("aria-label", input.checked ? "已完成" : "未完成");
    }
  }
  // Headings stay within the release article's h3 hierarchy.
  for (const heading of body.querySelectorAll("h1,h2,h3,h4,h5,h6")) {
    const replacement = document.createElement("h4");
    replacement.append(...heading.childNodes);
    heading.replaceWith(replacement);
  }
  for (const table of body.querySelectorAll("table")) {
    const wrapper = document.createElement("div");
    wrapper.className = "markdown-table";
    wrapper.tabIndex = 0;
    wrapper.setAttribute("role", "region");
    wrapper.setAttribute("aria-label", "发布说明表格，可横向滚动");
    table.replaceWith(wrapper);
    wrapper.append(table);
  }
  for (const block of body.querySelectorAll("pre")) {
    block.tabIndex = 0;
    block.setAttribute("role", "region");
    block.setAttribute("aria-label", "代码块，可横向滚动");
  }
  return body;
}

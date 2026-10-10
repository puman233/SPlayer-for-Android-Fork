import DOMPurify from "dompurify";
import { marked } from "marked";

/** Release notes may contain raw HTML. Only ordinary web links and images are accepted. */
export const isAllowedUpdateUrl = (value: string, image = false): boolean => {
  if (!image && value.startsWith("#")) return true;
  try {
    const url = new URL(value);
    return ["https:", "http:"].includes(url.protocol) && !url.username && !url.password;
  } catch {
    return false;
  }
};

export const sanitizeUpdateHtml = (html: string): string => {
  if (typeof document === "undefined" || !DOMPurify.isSupported) return "";
  const clean = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ["p", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "strong", "em", "del", "blockquote", "pre", "code", "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td", "a", "img"],
    ALLOWED_ATTR: ["href", "src", "alt", "title", "class", "colspan", "rowspan", "start"],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
  });
  const template = document.createElement("template");
  template.innerHTML = clean;
  template.content.querySelectorAll("a").forEach((link) => {
    if (!isAllowedUpdateUrl(link.getAttribute("href") || "")) link.removeAttribute("href");
    link.setAttribute("target", "_blank");
    link.setAttribute("rel", "noopener noreferrer");
  });
  template.content.querySelectorAll("img").forEach((image) => {
    if (!isAllowedUpdateUrl(image.getAttribute("src") || "", true)) image.removeAttribute("src");
  });
  return template.innerHTML;
};

export const renderUpdateMarkdown = async (markdown: string): Promise<string> =>
  sanitizeUpdateHtml(await marked(markdown));

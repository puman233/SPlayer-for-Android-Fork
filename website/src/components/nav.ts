/** 顶部导航：滚动状态、移动端展开菜单、当前区块高亮。 */

const DESKTOP_QUERY = "(min-width: 768px)";

export function mountNav(): void {
  const header = document.getElementById("site-header");
  const toggle = document.getElementById("nav-toggle");
  const nav = document.getElementById("site-nav");

  if (!header) return;

  mountScrollState(header);
  mountActiveSection();
  mountDisclosure(toggle, nav);
}

function mountScrollState(header: HTMLElement): void {
  const sync = (): void => {
    header.dataset["scrolled"] = window.scrollY > 4 ? "true" : "false";
  };

  sync();
  window.addEventListener("scroll", sync, { passive: true });
}

function mountDisclosure(toggle: HTMLElement | null, nav: HTMLElement | null): void {
  if (!toggle || !nav) return;

  const desktop = window.matchMedia(DESKTOP_QUERY);

  const setOpen = (open: boolean): void => {
    toggle.setAttribute("aria-expanded", String(open));
    toggle.setAttribute("aria-label", open ? "关闭导航菜单" : "打开导航菜单");
    nav.dataset["open"] = String(open);
  };

  const isOpen = (): boolean => toggle.getAttribute("aria-expanded") === "true";

  const close = (restoreFocus: boolean): void => {
    if (!isOpen()) return;
    setOpen(false);
    if (restoreFocus) toggle.focus();
  };

  setOpen(false);

  toggle.addEventListener("click", (event) => {
    event.stopPropagation();
    setOpen(!isOpen());
  });

  nav.addEventListener("click", (event) => {
    if (event.target instanceof Element && event.target.closest("a")) close(false);
  });

  document.addEventListener("click", (event) => {
    if (!isOpen() || desktop.matches) return;
    const target = event.target;
    if (target instanceof Node && (nav.contains(target) || toggle.contains(target))) return;
    close(false);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") close(true);
  });

  const onBreakpointChange = (): void => {
    if (desktop.matches) setOpen(false);
  };

  desktop.addEventListener("change", onBreakpointChange);
}

function mountActiveSection(): void {
  const links = Array.from(document.querySelectorAll<HTMLAnchorElement>("a[data-nav-link]"));
  if (links.length === 0 || typeof IntersectionObserver === "undefined") return;

  const sections = links
    .map((link) => {
      const id = link.getAttribute("href")?.replace(/^#/, "");
      return id ? document.getElementById(id) : null;
    })
    .filter((section): section is HTMLElement => section !== null);

  if (sections.length === 0) return;

  const setCurrent = (id: string): void => {
    for (const link of links) {
      const isCurrent = link.getAttribute("href") === `#${id}`;
      if (isCurrent) link.setAttribute("aria-current", "true");
      else link.removeAttribute("aria-current");
    }
  };

  const visible = new Set<string>();

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) visible.add(entry.target.id);
        else visible.delete(entry.target.id);
      }

      const first = sections.find((section) => visible.has(section.id));
      if (first) setCurrent(first.id);
      else if (window.scrollY < 8) setCurrent("top");
    },
    { rootMargin: "-20% 0px -70% 0px", threshold: 0 },
  );

  for (const section of sections) observer.observe(section);
}

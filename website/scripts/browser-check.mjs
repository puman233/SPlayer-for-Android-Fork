/** Real Chromium checks via installed agent-browser; run against pnpm preview.
 * Set AGENT_BROWSER_BIN to the native CLI executable on Windows.
 * Output artifacts belong outside the repository (BROWSER_REPORT_DIR).
 */
import { createBrowserDriver } from "./browser-driver.mjs";
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
const { command, evaluate, withPageInit, site } = createBrowserDriver();
const output = process.env.BROWSER_REPORT_DIR;
const report = [];
if (output) mkdirSync(output, { recursive: true });
function check(name, passed, detail = null) {
  report.push({ name, passed: Boolean(passed), detail });
  if (!passed) console.error(`FAIL ${name}: ${JSON.stringify(detail)}`);
}
command("network", "unroute");
command("open", site);
command("wait", "#release-panel[data-mode=release]");
for (const width of process.env.BROWSER_FAILURES_ONLY
  ? []
  : [320, 360, 390, 412, 768, 1024, 1440, 844]) {
  command("set", "viewport", String(width), width === 844 ? "390" : "900");
  for (const theme of ["dark", "light"]) {
    command("select", "#theme-select", theme);
    const layout = evaluate(() => ({
      width: innerWidth,
      scroll: document.documentElement.scrollWidth,
      theme: document.documentElement.dataset.theme,
      clipped: [
        ...document.querySelectorAll(
          "h1,h2,.hero-actions a,.release-actions a,#theme-select,#release-channel",
        ),
      ]
        .filter((el) => {
          const r = el.getBoundingClientRect();
          return r.width && (r.left < -1 || r.right > innerWidth + 1);
        })
        .map((el) => el.outerHTML.slice(0, 120)),
      broken: [...document.images]
        .filter((img) => img.currentSrc && img.complete && !img.naturalWidth)
        .map((img) => img.src),
    }));
    check(
      `${width}/${theme}: no page overflow, clipped headings/buttons or broken images`,
      layout.scroll <= width + 1 &&
        !layout.clipped.length &&
        !layout.broken.length &&
        layout.theme === theme,
      layout,
    );
    if (width < 768) {
      command("click", "#nav-toggle");
      check(
        `${width}/${theme}: mobile menu opens`,
        evaluate(
          () => document.querySelector("#nav-toggle").getAttribute("aria-expanded") === "true",
        ),
      );
      command("press", "Escape");
      check(
        `${width}/${theme}: Escape closes menu and restores focus`,
        evaluate(
          () =>
            document.querySelector("#nav-toggle").getAttribute("aria-expanded") === "false" &&
            document.activeElement.id === "nav-toggle",
        ),
      );
    }
    command("select", "#release-channel", "beta");
    const beta = evaluate(() => ({
      tag: document.querySelector("#release-tag").textContent,
      links: [...document.querySelectorAll("a[data-original]")].map((el) => el.href),
      rows: [...document.querySelectorAll("tr[data-abi]")].filter((el) => !el.hidden).length,
    }));
    check(
      `${width}/${theme}: Beta tag and APK URLs agree`,
      /beta/.test(beta.tag) &&
        beta.links.length === beta.rows &&
        beta.links.every((url) => url.includes(`/download/${beta.tag}/`)),
      beta,
    );
    command("select", "#route-select", "gh-proxy");
    check(
      `${width}/${theme}: explicit proxy selection`,
      evaluate(() =>
        [...document.querySelectorAll("a[data-original]")].every((el) =>
          el.href.startsWith("https://gh-proxy.org/https://github.com/"),
        ),
      ),
    );
    command("select", "#release-channel", "stable");
    check(
      `${width}/${theme}: switching back preserves manual route and restores rows`,
      evaluate(
        () =>
          !document.querySelector("#release-tag").textContent.includes("beta") &&
          document.querySelector("#route-select").value === "gh-proxy" &&
          [...document.querySelectorAll("a[data-original]")].every((el) =>
            el.href.startsWith("https://gh-proxy.org/https://github.com/"),
          ),
      ),
    );
    command("select", "#route-select", "github");
    command("click", ".shot-open");
    check(
      `${width}/${theme}: modal opens`,
      evaluate(() => document.querySelector("dialog").open),
    );
    command("press", "ArrowRight");
    check(
      `${width}/${theme}: next screenshot via keyboard`,
      evaluate(() => document.querySelector(".preview-toolbar span").textContent.includes("2/7")),
    );
    command("click", ".preview-toolbar button:nth-of-type(3)");
    check(
      `${width}/${theme}: zoom control works`,
      evaluate(() => document.querySelector(".preview-viewport").dataset.zoom === "true"),
    );
    command("press", "Escape");
    check(
      `${width}/${theme}: modal closes and returns focus`,
      evaluate(
        () =>
          !document.querySelector("dialog").open &&
          document.activeElement.className === "shot-open",
      ),
    );
    command("click", ".faq-item summary");
    check(
      `${width}/${theme}: FAQ opens`,
      evaluate(() => document.querySelector(".faq-item").open),
    );
    command("click", ".faq-item summary");
    check(
      `${width}/${theme}: FAQ closes`,
      evaluate(() => !document.querySelector(".faq-item").open),
    );
    // Opening a digest must not create overflow on small cards.
    command("click", ".digest summary");
    check(
      `${width}/${theme}: digest wraps`,
      evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
    );
    command("click", ".digest summary");
    command("eval", "window.scrollTo(0,0)");
    if (output && [320, 768, 1440].includes(width))
      command("screenshot", resolve(output, `${width}-${theme}.png`));
  }
}
command("set", "viewport", "390", "844");
command("select", "#theme-select", "system");
check(
  "system theme preference",
  evaluate(
    () =>
      document.documentElement.dataset.theme ===
      (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"),
  ),
);
check(
  "no unsolicited proxy requests",
  evaluate(
    () =>
      !performance
        .getEntriesByType("resource")
        .some((entry) => /gh-proxy\.org|gh\.monlor\.com|ghproxy\.imciel\.com/.test(entry.name)),
  ),
);
const resources = evaluate(() =>
  performance
    .getEntriesByType("resource")
    .filter((entry) => entry.responseStatus >= 400)
    .map((entry) => entry.name),
);
check("Pages subpath resource status", resources.length === 0, resources);
const catalog = JSON.parse(
  readFileSync(new URL("../public/latest-release.json", import.meta.url), "utf8"),
);
const metadataPattern = "**/latest-release.json";
const apiPattern = "https://api.github.com/**";
// Missing rows must be restored on the next channel render; notes stay plain text.
const partial = structuredClone(catalog);
partial.stable.assets = partial.stable.assets.filter((a) => a.name.includes("x86_64"));
partial.stable.body = '<img src=x onerror="window.injected=true">';
command("network", "route", metadataPattern, "--body", JSON.stringify(partial));
command("open", site);
command("wait", "#release-panel[data-mode=release]");
check(
  "partial stable release hides only absent ABIs",
  evaluate(
    () =>
      [...document.querySelectorAll("tr[data-abi]")]
        .filter((row) => !row.hidden)
        .map((row) => row.dataset.abi)
        .join() === "x86_64",
  ),
);
command("select", "#release-channel", "beta");
check(
  "Beta restores previously hidden ABI rows",
  evaluate(
    () => [...document.querySelectorAll("tr[data-abi]")].filter((row) => !row.hidden).length === 4,
  ),
);
check(
  "notes cannot inject HTML",
  evaluate(
    () => !window.injected && !document.querySelector("#release-updates .release-markdown img"),
  ),
);
const empty = structuredClone(catalog);
empty.beta.assets = [];
command("network", "route", metadataPattern, "--body", JSON.stringify(empty));
command("open", site);
command("wait", "#release-panel[data-mode=release]");
command("select", "#release-channel", "beta");
check(
  "empty Beta shows fallback without stale APK links",
  evaluate(
    () =>
      document.querySelector("#release-panel").dataset.mode === "fallback" &&
      !document.querySelector("a[data-original]") &&
      document.querySelector("#route-picker").hidden,
  ),
);
// Both data sources unavailable, with storage denied/empty: static fallback survives.
command("network", "route", metadataPattern, "--body", "{}");
command("network", "route", apiPattern, "--abort");
command("eval", "sessionStorage.clear()");
command("open", site);
command("wait", "--text", "版本数据暂时不可用");
check(
  "API failure keeps visible error and real Releases fallback",
  evaluate(
    () =>
      document.querySelector("#release-panel").dataset.mode === "fallback" &&
      !document.querySelector("a[data-original]") &&
      [...document.querySelectorAll(".release-actions a")].some(
        (a) => a.href === "https://github.com/puman233/SPlayer-for-Android-Fork/releases",
      ),
  ),
);
// Refill storage, then prove cached data is labelled after both sources fail.
command("network", "route", metadataPattern, "--body", JSON.stringify(catalog));
command("open", site);
command("wait", "#release-panel[data-mode=release]");
command("network", "route", metadataPattern, "--body", "{}");
command("open", site);
command("wait", "--text", "会话缓存");
check(
  "cache provenance is explicit",
  evaluate(() => document.querySelector("#release-status").textContent.includes("可能不是最新")),
);
const apiRelease = {
  tag_name: catalog.stable.tag,
  prerelease: false,
  draft: false,
  published_at: catalog.stable.publishedAt,
  html_url: catalog.stable.htmlUrl,
  assets: catalog.stable.assets.map((a) => ({
    name: a.name,
    size: a.size,
    state: "uploaded",
    digest: a.digest,
    browser_download_url: a.downloadUrl,
  })),
};
// CLI response mocks omit CORS headers. Use CDP to install a reviewed fetch fixture
// before app boot; this verifies fallback logic, not the real GitHub network.
const source = `const nativeFetch = window.fetch.bind(window); window.fetch = (url, init) => String(url).startsWith('https://api.github.com/') ? Promise.resolve(new Response(${JSON.stringify(JSON.stringify([apiRelease]))}, {status:200, headers:{'Content-Type':'application/json'}})) : nativeFetch(url, init);`;
await withPageInit(source, async (navigate) => {
  command("eval", "sessionStorage.clear()");
  await navigate();
  command("wait", "--text", "数据来自本次 GitHub API 查询");
  check(
    "mock API success recovers when static metadata fails",
    evaluate(() => document.querySelector("#release-panel").dataset.mode === "release"),
  );
});
command("network", "unroute");
command("open", site);
command("wait", "#release-panel[data-mode=release]");
const a11y = command("a11y");
check("automated accessibility: no violations", a11y.counts.violations === 0, a11y.counts);
if (output) {
  writeFileSync(resolve(output, "a11y.json"), JSON.stringify(a11y, null, 2));
  writeFileSync(resolve(output, "browser-report.json"), JSON.stringify(report, null, 2));
}
console.log(`${report.filter((r) => r.passed).length}/${report.length} browser checks passed`);
if (report.some((r) => !r.passed)) process.exitCode = 1;

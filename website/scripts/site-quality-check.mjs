import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createBrowserDriver } from "./browser-driver.mjs";
const { command, evaluate, withPageInit, site } = createBrowserDriver();
const output = process.env.BROWSER_REPORT_DIR;
if (output) mkdirSync(output, { recursive: true });
const results = [];
function check(name, pass, detail = null) {
  results.push({ name, pass: Boolean(pass), detail });
  if (!pass) console.error(`FAIL ${name}: ${JSON.stringify(detail)}`);
}
const catalog = JSON.parse(
  readFileSync(new URL("../public/latest-release.json", import.meta.url), "utf8"),
);
const markdown = [
  "## 更新说明",
  "",
  "**加粗**与*斜体*、~~已移除~~。",
  "",
  "- 第一项",
  "- 第二项",
  "",
  "1. 安装",
  "2. 打开",
  "",
  "> 请保留数据。",
  "",
  "| 架构 | 说明 |",
  "| --- | --- |",
  `| arm64-v8a | ${"long-value-".repeat(20)} |`,
  "",
  "```js",
  `console.log('${"long-code-".repeat(30)}');`,
  "```",
  "",
  "- [x] 已验证",
  "",
  "[项目](https://github.com/puman233/SPlayer-for-Android-Fork)",
  "[帮助](./docs/android-development.md)",
  "[无效](javascript:alert(1))",
  "<img src=https://example.invalid/pixel onerror=window.injected=true>",
  "<svg onload=window.injected=true><script>window.injected=true</script></svg>",
  "<iframe src=https://example.invalid></iframe><input type=text autofocus><div id=site-header>重复ID</div>",
].join("\n");
catalog.stable.body = markdown;
function fixture(country, delay = 0) {
  return `
  const nativeFetch = window.fetch.bind(window);
  window._countryRequests = 0; window._probeSignals = []; window._alerts=[];
  window.alert = (...args) => window._alerts.push(args);
  window.fetch = async (url, options={}) => {
    if (String(url).endsWith('latest-release.json')) return new Response(${JSON.stringify(JSON.stringify(catalog))},{headers:{'Content-Type':'application/json'}});
    if (String(url)==='https://api.country.is/') {
      window._countryRequests++;
      ${delay ? `await new Promise(resolve => setTimeout(resolve, ${delay}));` : ""}
      ${country === "offline" ? "throw new TypeError('offline');" : `return new Response(${JSON.stringify(JSON.stringify({ country }))},{headers:{'Content-Type':'application/json'}});`}
    }
    if (/gh-proxy.org|gh.monlor.com|ghproxy.imciel.com/.test(String(url))) {
      window._probeSignals.push(options.signal);
      if (options.mode==='cors') throw new TypeError('CORS unavailable');
      return {body:null};
    }
    return nativeFetch(url,options);
  };`;
}
command("network", "unroute");
command("open", site);
try {
  for (const country of ["CN", "US", "HK", "MO", "TW", null, "offline"]) {
    await withPageInit(fixture(country), async (navigate) => {
      await navigate();
      command("wait", "#release-panel[data-mode=release]");
      const selected = evaluate(() => document.querySelector("#route-select").value);
      check(
        `country ${country}: exact regional default`,
        selected === (country === "CN" ? "gh-proxy" : "github"),
        selected,
      );
      check(
        `country ${country}: no geo alert and one lookup`,
        evaluate(() => window._alerts.length === 0 && window._countryRequests === 1),
      );
      check(
        `country ${country}: no IP persisted`,
        evaluate(
          () =>
            ![...Object.values(localStorage), ...Object.values(sessionStorage)].some((value) =>
              /"ip"\s*:/.test(value),
            ),
        ),
      );
      if (country === "CN") {
        command("select", "#release-channel", "beta");
        check(
          "CN keeps third-party default on Beta",
          evaluate(() =>
            [...document.querySelectorAll("a[data-original]")].every((a) =>
              a.href.startsWith("https://gh-proxy.org/https://github.com/"),
            ),
          ),
        );
        command("select", "#route-select", "github");
        command("select", "#release-channel", "stable");
        check(
          "explicit GitHub choice survives channel change",
          evaluate(
            () =>
              document.querySelector("#route-select").value === "github" &&
              [...document.querySelectorAll("a[data-original]")].every((a) =>
                a.href.startsWith("https://github.com/"),
              ),
          ),
        );
      }
    });
  }
  await withPageInit(fixture("CN", 1100), async (navigate) => {
    await navigate();
    command("wait", "#release-panel[data-mode=release]");
    command("select", "#route-select", "gh-monlor");
    command("wait", "1400");
    check(
      "late country response cannot replace user's route",
      evaluate(() => document.querySelector("#route-select").value === "gh-monlor"),
    );
  });
  await withPageInit(fixture("US"), async (navigate) => {
    await navigate();
    command("wait", "#release-panel[data-mode=release]");
    check(
      "Markdown parser is lazy before disclosure",
      evaluate(
        () =>
          !performance
            .getEntriesByType("resource")
            .some((entry) => /\/markdown-.*\.js/.test(entry.name)),
      ),
    );
    check(
      "system CSS theme before app initialization",
      evaluate(() => {
        const theme = document.documentElement.dataset.theme;
        delete document.documentElement.dataset.theme;
        const bg = getComputedStyle(document.body).backgroundColor;
        document.documentElement.dataset.theme = theme;
        return (
          bg ===
          (matchMedia("(prefers-color-scheme: dark)").matches
            ? "rgb(17, 19, 24)"
            : "rgb(255, 255, 255)")
        );
      }),
    );
    command("click", "#release-updates article:first-child summary");
    command("wait", ".release-markdown table");
    check(
      "Markdown headings, lists, table, code and emphasis render",
      evaluate(() =>
        ["h4", "ul", "ol", "table", "pre code", "blockquote", "strong", "em", "del"].every(
          (selector) => document.querySelector(`.release-markdown ${selector}`),
        ),
      ),
    );
    check(
      "unsafe HTML and script URLs removed",
      evaluate(
        () =>
          !window.injected &&
          !document.querySelector(
            ".release-markdown img,.release-markdown svg,.release-markdown iframe,.release-markdown script,.release-markdown [onerror],.release-markdown [onload],.release-markdown [autofocus],.release-markdown [id],.release-markdown a[href^='javascript:']",
          ),
      ),
    );
    check(
      "task checkboxes are inert",
      evaluate(() =>
        [...document.querySelectorAll(".release-markdown input")].every(
          (input) => input.type === "checkbox" && input.disabled,
        ),
      ),
    );
    check(
      "relative links resolve to repository source",
      evaluate(() =>
        [...document.querySelectorAll(".release-markdown a")].some(
          (a) =>
            a.href ===
            "https://github.com/puman233/SPlayer-for-Android-Fork/blob/dev/docs/android-development.md",
        ),
      ),
    );
    check(
      "Markdown table and code are keyboard-scrollable",
      evaluate(
        () =>
          document.querySelector(".markdown-table").tabIndex === 0 &&
          document.querySelector(".release-markdown pre").tabIndex === 0,
      ),
    );
    for (const width of [320, 360, 390, 412, 768, 1024, 1440]) {
      command("set", "viewport", String(width), "900");
      for (const theme of ["dark", "light"]) {
        command("select", "#theme-select", theme);
        const state = evaluate(() => ({
          overflow: document.documentElement.scrollWidth > innerWidth + 1,
          listStyle: getComputedStyle(document.querySelector(".release-markdown ul")).listStyleType,
          table:
            document.querySelector(".markdown-table").scrollWidth >
            document.querySelector(".markdown-table").clientWidth,
        }));
        check(
          `${width}/${theme}: Markdown stays within page and lists retain markers`,
          !state.overflow && state.listStyle === "disc",
          state,
        );
        if (output && [390, 1440].includes(width)) {
          command("scrollintoview", "#release-updates");
          command("screenshot", resolve(output, `markdown-${width}-${theme}.png`));
        }
      }
    }
    command("set", "viewport", "390", "844");
    command("click", "#nav-toggle");
    command("focus", "#site-nav li:last-child a");
    command("press", "Tab");
    command("press", "Tab");
    check(
      "keyboard leaving mobile header closes the menu",
      evaluate(
        () =>
          !document.querySelector("header").contains(document.activeElement) &&
          document.querySelector("#nav-toggle").getAttribute("aria-expanded") === "false",
      ),
    );
    command("select", "#route-select", "auto");
    command("wait", "--text", "已回退到 GitHub 原始线路");
    check(
      "opaque probe requests are aborted when probing completes",
      evaluate(
        () =>
          window._probeSignals.length > 0 && window._probeSignals.every((signal) => signal.aborted),
      ),
    );
    evaluate(() => {
      for (const details of document.querySelectorAll(".faq-item,.digest")) details.open = true;
    });
    for (const theme of ["light", "dark"]) {
      command("select", "#theme-select", theme);
      // Buttons transition colors for 120ms; inspect the completed theme, not a mixed frame.
      command("wait", "200");
      const audit = command("a11y");
      check(
        `${theme}: expanded Markdown, FAQ and digest accessibility`,
        audit.counts.violations === 0,
        audit.counts,
      );
      if (output)
        writeFileSync(
          resolve(output, `expanded-a11y-${theme}.json`),
          JSON.stringify(audit, null, 2),
        );
    }
  });
} finally {
  command("network", "unroute");
  if (output)
    writeFileSync(resolve(output, "quality-report.json"), JSON.stringify(results, null, 2));
}
console.log(`${results.filter((r) => r.pass).length}/${results.length} site quality checks passed`);
if (results.some((r) => !r.pass)) process.exitCode = 1;

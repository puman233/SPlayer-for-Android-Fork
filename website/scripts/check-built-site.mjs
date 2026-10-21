/** Validate the emitted artifact, including public files Vite does not rewrite. */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
const dist = new URL("../dist/", import.meta.url);
const origin = "https://puman233.github.io";
const base = "/SPlayer-for-Android-Fork/";
const html = readFileSync(new URL("index.html", dist), "utf8");
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
if (new Set(ids).size !== ids.length) throw Error("Duplicate HTML ids");
for (const [, id] of html.matchAll(/href="#([^"]+)"/g)) {
  if (!ids.includes(id)) throw Error(`Broken anchor: #${id}`);
}
let count = 0;
const references = [...html.matchAll(/(?:src|href)="([^"#]+)"/g)].map((match) => match[1]);
for (const value of new Set(references)) {
  const url = new URL(value, `${origin}${base}`);
  if (url.origin !== origin) continue;
  if (!url.pathname.startsWith(base)) throw Error(`Reference escapes Pages base: ${value}`);
  const path = decodeURIComponent(url.pathname.slice(base.length)) || "index.html";
  if (!existsSync(fileURLToPath(new URL(path, dist))))
    throw Error(`Missing build resource: ${path}`);
  count++;
}
for (const file of [
  "latest-release.json",
  "site.webmanifest",
  "sitemap.xml",
  "robots.txt",
  "THIRD-PARTY-NOTICES.txt",
]) {
  if (!existsSync(new URL(file, dist))) throw Error(`Missing public artifact: ${file}`);
}
console.log(`[site-artifact] ${count} local references and all anchors/public artifacts verified`);

/** Node 24: shared validator; tokens are used only in server request headers. */
import { readFile, writeFile, rename } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { parseCatalog, selectReleases, REPO_SLUG } from "../src/services/releaseModel.ts";
const OUT = new URL("../public/latest-release.json", import.meta.url);
export async function generate({ request = fetch, output = OUT } = {}) {
  try {
    const headers = { Accept: "application/vnd.github+json", "User-Agent": "splayer-website" };
    if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    const all = [];
    for (let page = 1; ; page++) {
      if (page > 100) throw new Error("Release pagination exceeded limit");
      const response = await request(
        `https://api.github.com/repos/${REPO_SLUG}/releases?per_page=100&page=${page}`,
        { headers, signal: AbortSignal.timeout(20000) },
      );
      if (!response.ok) throw new Error(`GitHub API ${response.status}`);
      const list = await response.json();
      if (!Array.isArray(list)) throw new Error("Invalid release list");
      all.push(...list);
      if (list.length < 100) break;
    }
    const catalog = selectReleases(all);
    if (!catalog.stable) throw new Error("No valid stable APK release");
    const temp = new URL(`${output.href}.tmp`);
    await writeFile(temp, `${JSON.stringify(catalog, null, 2)}\n`);
    await rename(temp, output);
    return { catalog, retained: false };
  } catch (error) {
    let catalog = null;
    try {
      catalog = parseCatalog(JSON.parse(await readFile(output, "utf8")));
    } catch {
      /* fail closed */
    }
    if (!catalog?.stable) throw error;
    console.warn(
      "[release-metadata] Fetch failed; retaining validated snapshot with original timestamp.",
    );
    return { catalog, retained: true };
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const { catalog, retained } = await generate();
    console.log(
      `[release-metadata] stable=${catalog.stable?.tag} beta=${catalog.beta?.tag ?? "none"} retained=${retained}`,
    );
  } catch {
    console.error("[release-metadata] No valid snapshot; refusing deployment.");
    process.exitCode = 1;
  }
}

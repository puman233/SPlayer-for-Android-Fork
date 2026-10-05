/**
 * 生成站点用的 Release metadata。
 *
 * 在 GitHub Pages 构建阶段运行，把 GitHub API 的 latest release 固化成静态文件
 * `website/public/latest-release.json`，让浏览器不用访问 api.github.com 也能拿到
 * 版本号、发布时间与 APK 列表。
 *
 * 只接受非 draft、非 prerelease 的正式 Release。
 * 输出里不包含 token、download_count、author、uploader 等任何无关字段。
 *
 * 用法：
 *   node scripts/generate-release-metadata.mjs
 *
 * 环境变量：
 *   GITHUB_TOKEN  可选。仅用于提高 API 速率上限，不会写入输出文件。
 */

import { readFile, writeFile } from "node:fs/promises";

const REPO_SLUG = "puman233/SPlayer-for-Android-Fork";
const API_URL = `https://api.github.com/repos/${REPO_SLUG}/releases/latest`;
const OUT_FILE = new URL("../public/latest-release.json", import.meta.url);

const REQUEST_TIMEOUT_MS = 20000;

function log(message) {
  process.stdout.write(`[release-metadata] ${message}\n`);
}

/** 只挑出正式 APK 附件，其余字段一律不进输出。 */
function collectApkAssets(rawAssets) {
  const assets = [];

  for (const item of Array.isArray(rawAssets) ? rawAssets : []) {
    if (typeof item !== "object" || item === null) continue;

    const name = item.name;
    const size = item.size;
    const downloadUrl = item.browser_download_url;

    if (typeof name !== "string" || name.length === 0) continue;
    if (typeof downloadUrl !== "string" || downloadUrl.length === 0) continue;
    if (typeof size !== "number" || !Number.isFinite(size) || size <= 0) continue;
    if (!name.toLowerCase().endsWith(".apk")) continue;

    assets.push({ name, size, downloadUrl });
  }

  return assets;
}

function buildMetadata(payload) {
  if (typeof payload !== "object" || payload === null) {
    throw new Error("API 响应不是对象");
  }

  // 只接受正式 Release
  if (payload.draft === true) throw new Error("latest release 是 draft，拒绝生成");
  if (payload.prerelease === true) throw new Error("latest release 是 prerelease，拒绝生成");

  const tag = typeof payload.tag_name === "string" ? payload.tag_name.trim() : "";
  if (!tag) throw new Error("缺少 tag_name");

  const assets = collectApkAssets(payload.assets);
  if (assets.length === 0) throw new Error("latest release 里没有可用的 APK 附件");

  const publishedAt = typeof payload.published_at === "string" ? payload.published_at : "";
  const updatedAt = typeof payload.updated_at === "string" ? payload.updated_at : "";
  const htmlUrl =
    typeof payload.html_url === "string" && payload.html_url.length > 0
      ? payload.html_url
      : `https://github.com/${REPO_SLUG}/releases/tag/${tag}`;

  return {
    tag,
    publishedAt,
    updatedAt,
    htmlUrl,
    generatedAt: new Date().toISOString(),
    assets,
  };
}

async function readExisting() {
  try {
    return JSON.parse(await readFile(OUT_FILE, "utf8"));
  } catch {
    return null;
  }
}

async function fetchLatestRelease() {
  const headers = {
    Accept: "application/vnd.github+json",
    "User-Agent": "splayer-fork-website-metadata",
  };

  // token 只用于请求头，绝不写入输出文件
  const token = process.env.GITHUB_TOKEN;
  if (token) headers.Authorization = `Bearer ${token}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(API_URL, {
      headers,
      signal: controller.signal,
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error(`GitHub API 返回 ${response.status}`);
    }

    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

async function main() {
  const existing = await readExisting();

  try {
    const payload = await fetchLatestRelease();
    const metadata = buildMetadata(payload);

    await writeFile(OUT_FILE, `${JSON.stringify(metadata, null, 2)}\n`, "utf8");

    log(`已写入 ${OUT_FILE.pathname}`);
    log(
      `  tag=${metadata.tag} assets=${metadata.assets.length} generatedAt=${metadata.generatedAt}`,
    );
    for (const asset of metadata.assets) {
      log(`  - ${asset.name} (${asset.size} B)`);
    }
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);

    if (existing) {
      // 保留上一次成功的结果，保证部署不会因为 API 抖动而失败
      log(`获取失败：${reason}`);
      log(`保留已有 metadata（tag=${existing.tag}），继续部署。`);
      return;
    }

    log(`获取失败：${reason}`);
    log("本地也没有可用的 metadata，无法继续。");
    process.exitCode = 1;
  }
}

await main();

import { CapacitorHttp } from "@capacitor/core";
import { ANDROID_RELEASE_BY_TAG_API_URL, ANDROID_REPOSITORY_URL } from "../../config/repository";

export interface AndroidReleaseAsset {
  name: string;
  url: string;
  sha256: string;
  size: number;
  abi?: string;
}

interface GitHubReleaseAsset {
  name?: unknown;
  browser_download_url?: unknown;
  digest?: unknown;
  size?: unknown;
}

const APK_NAME_RE = /\.apk$/i;

export const parseAssetSize = (text: string): number => {
  const match = text.match(/([\d.]+)\s*(KB|MB|GB)/i);
  if (!match) return 0;
  const value = Number(match[1]);
  const power = { KB: 1, MB: 2, GB: 3 }[match[2].toUpperCase() as "KB" | "MB" | "GB"];
  return Number.isFinite(value) ? Math.round(value * 1024 ** power) : 0;
};

export const parseExpandedReleaseAssets = (html: string): AndroidReleaseAsset[] => {
  const document = new DOMParser().parseFromString(html, "text/html");
  return Array.from(document.querySelectorAll<HTMLAnchorElement>('a[href*="/releases/download/"]'))
    .map((anchor) => {
      const name = anchor.textContent?.trim() || "";
      const rowText = anchor.closest("li")?.textContent || "";
      const sha256 = rowText.match(/sha256:([a-f0-9]{64})/i)?.[1]?.toLowerCase() || "";
      return {
        name,
        url: new URL(anchor.getAttribute("href") || "", ANDROID_REPOSITORY_URL).toString(),
        sha256,
        size: parseAssetSize(rowText),
      };
    })
    .filter((asset) => APK_NAME_RE.test(asset.name) && asset.sha256);
};

export const parseReleaseApiAssets = (data: unknown): AndroidReleaseAsset[] => {
  let payload = data;
  if (typeof data === "string") {
    try {
      payload = JSON.parse(data);
    } catch {
      return [];
    }
  }
  const assets = (payload as { assets?: unknown })?.assets;
  if (!Array.isArray(assets)) return [];
  return assets
    .map((value: GitHubReleaseAsset) => {
      const digest = typeof value.digest === "string" ? value.digest : "";
      return {
        name: typeof value.name === "string" ? value.name : "",
        url: typeof value.browser_download_url === "string" ? value.browser_download_url : "",
        sha256: digest.match(/^sha256:([a-f0-9]{64})$/i)?.[1]?.toLowerCase() || "",
        size: typeof value.size === "number" && value.size > 0 ? value.size : 0,
      };
    })
    .filter((asset) => APK_NAME_RE.test(asset.name) && asset.url && asset.sha256);
};

export const formatAssetSize = (size: number): string => {
  if (!Number.isFinite(size) || size <= 0) return "大小未知";
  const mib = size / 1024 / 1024;
  return `${mib.toFixed(mib >= 10 ? 1 : 2)} MB`;
};

const includesAbi = (name: string, abi: string): boolean => {
  if (abi.toLowerCase() === "x86" && /x86_64/i.test(name)) return false;
  const escaped = abi.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(?:^|[-_.])${escaped}(?:[-_.]|$)`, "i").test(name);
};

export const selectAndroidApkAsset = (
  assets: AndroidReleaseAsset[],
  supportedAbis: string[],
): AndroidReleaseAsset | null => {
  const signedApks = assets.filter(
    (asset) => APK_NAME_RE.test(asset.name) && !/unsigned/i.test(asset.name) && asset.sha256,
  );
  for (const abi of supportedAbis) {
    const asset = signedApks.find((candidate) => includesAbi(candidate.name, abi));
    if (asset) return { ...asset, abi };
  }
  return null;
};

export const fetchAndroidReleaseAssets = async (tag: string): Promise<AndroidReleaseAsset[]> => {
  const normalizedTag = encodeURIComponent(tag.trim());
  try {
    const apiResponse = await CapacitorHttp.get({
      url: `${ANDROID_RELEASE_BY_TAG_API_URL}/${normalizedTag}`,
      headers: {
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "SPlayer-Android-Updater",
      },
    });
    if (apiResponse.status >= 200 && apiResponse.status < 300) {
      const apiAssets = parseReleaseApiAssets(apiResponse.data);
      if (apiAssets.length) return apiAssets;
    }
  } catch (error) {
    console.warn("GitHub Release API 附件读取失败，尝试官方页面", error);
  }

  // 匿名 API 额度耗尽时，继续使用 GitHub 官方附件页面兜底。
  const url = `${ANDROID_REPOSITORY_URL}/releases/expanded_assets/${normalizedTag}`;
  const response = await CapacitorHttp.get({ url });
  if (response.status < 200 || response.status >= 300 || typeof response.data !== "string") {
    throw new Error(`Release assets request failed: HTTP ${response.status}`);
  }
  return parseExpandedReleaseAssets(response.data);
};

import { CapacitorHttp } from "@capacitor/core";
import { ANDROID_REPOSITORY_URL } from "../../config/repository";

export interface AndroidReleaseAsset {
  name: string;
  url: string;
  sha256: string;
  abi?: string;
}

const APK_NAME_RE = /\.apk$/i;

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
      };
    })
    .filter((asset) => APK_NAME_RE.test(asset.name) && asset.sha256);
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
  const url = `${ANDROID_REPOSITORY_URL}/releases/expanded_assets/${normalizedTag}`;
  const response = await CapacitorHttp.get({ url });
  if (response.status < 200 || response.status >= 300 || typeof response.data !== "string") {
    throw new Error(`Release assets request failed: HTTP ${response.status}`);
  }
  return parseExpandedReleaseAssets(response.data);
};

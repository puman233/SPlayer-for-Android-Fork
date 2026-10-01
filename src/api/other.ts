import axios from "axios";
import { CapacitorHttp } from "@capacitor/core";
import { ANDROID_RELEASES_API_URL, ANDROID_RELEASES_ATOM_URL } from "@/config/repository";
import { isCapacitorAndroid } from "@/utils/env";
import request from "@/utils/request";

interface GithubReleaseLike {
  tag_name: string;
  body: string;
  published_at: string;
  html_url: string;
  prerelease: boolean;
  draft: boolean;
}

const parseReleaseFeed = (xml: string): GithubReleaseLike[] => {
  const document = new DOMParser().parseFromString(xml, "application/xml");
  if (document.querySelector("parsererror")) {
    throw new Error("GitHub Releases Atom 响应格式无效");
  }

  return Array.from(document.querySelectorAll("entry"))
    .map((entry) => {
      const id = entry.querySelector("id")?.textContent?.trim() || "";
      const tag = id.slice(id.lastIndexOf("/") + 1);
      const url = entry.querySelector('link[rel="alternate"]')?.getAttribute("href") || "";
      return {
        tag_name: tag,
        body: entry.querySelector("content")?.textContent || "",
        published_at: entry.querySelector("updated")?.textContent?.trim() || "",
        html_url: url,
        prerelease: /-(?:alpha|beta|rc|dev|canary|nightly)(?:[.-]|$)/i.test(tag),
        draft: false,
      };
    })
    .filter((release) => release.tag_name && release.html_url);
};

const shouldUseAtomFallback = (error: unknown): boolean =>
  isCapacitorAndroid &&
  axios.isAxiosError(error) &&
  (error.response?.status === 403 || error.response?.status === 429);

// 获取仓库更新日志
export const updateLog = async (): Promise<GithubReleaseLike[]> => {
  try {
    return await request({
      withCredentials: false,
      url: ANDROID_RELEASES_API_URL,
      headers: {
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      params: { noCookie: true, per_page: 20 },
    });
  } catch (error) {
    if (!shouldUseAtomFallback(error)) throw error;

    // GitHub API 的匿名额度按出口 IP 计算，模拟器/公共网络很容易共享耗尽。
    // Android 原生 HTTP 不受 WebView CORS 限制，因此用官方 Releases Atom 源兜底。
    const response = await CapacitorHttp.get({ url: ANDROID_RELEASES_ATOM_URL });
    if (response.status < 200 || response.status >= 300 || typeof response.data !== "string") {
      throw error;
    }
    return parseReleaseFeed(response.data);
  }
};

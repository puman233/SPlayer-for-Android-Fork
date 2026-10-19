import { ANDROID_RELEASES_API_URL } from "@/config/repository";
import request from "@/utils/request";

interface GithubReleaseLike {
  tag_name: string;
  body: string;
  published_at: string;
  html_url: string;
  prerelease: boolean;
  draft: boolean;
}

// 获取仓库更新日志
export const updateLog = async (): Promise<GithubReleaseLike[]> => {
  // Atom 没有 draft/prerelease 权威标记，不能作为通道选择的依据。
  // 保留原始 HTTP 错误，让检查入口显示限流/网络提示并退出 loading。
  const releases = new Map<string, GithubReleaseLike>();
  const pageSize = 20;
  for (let page = 1; page <= 50; page++) {
    const result: unknown = await request({
      withCredentials: false,
      url: ANDROID_RELEASES_API_URL,
      headers: {
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      params: { noCookie: true, per_page: pageSize, page },
    });
    if (
      !Array.isArray(result) ||
      result.some(
        (release) =>
          !release ||
          typeof release !== "object" ||
          typeof release.tag_name !== "string" ||
          typeof release.prerelease !== "boolean" ||
          typeof release.draft !== "boolean" ||
          typeof release.html_url !== "string" ||
          (release.body !== null && typeof release.body !== "string"),
      )
    ) {
      throw new Error("GitHub Releases API 响应格式无效");
    }
    for (const release of result) {
      if (!releases.has(release.tag_name))
        releases.set(release.tag_name, { ...release, body: release.body ?? "" });
    }
    if (result.length < pageSize) return [...releases.values()];
  }
  throw new Error("GitHub Releases 列表过长，未能完成更新检查，请稍后重试");
};

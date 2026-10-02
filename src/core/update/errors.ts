import axios from "axios";

export const getUpdateCheckErrorMessage = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 403 || error.response?.status === 429) {
      return "GitHub 请求次数已达上限，请稍后重试";
    }
    if (error.response?.status === 404) {
      return "未找到更新仓库，请检查仓库地址或网络代理";
    }
    if (error.code === "ECONNABORTED" || !error.response) {
      return "无法连接 GitHub，请检查网络后重试";
    }
  }
  return "检查更新失败，请稍后重试";
};

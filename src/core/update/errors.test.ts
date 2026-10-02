import assert from "node:assert/strict";
import { AxiosError } from "axios";
import { describe, it } from "node:test";
import { getUpdateCheckErrorMessage } from "./errors.ts";

describe("update check error messages", () => {
  it("为断网和超时返回可操作提示", () => {
    assert.equal(
      getUpdateCheckErrorMessage(new AxiosError("Network Error", "ERR_NETWORK")),
      "无法连接 GitHub，请检查网络后重试",
    );
    assert.equal(
      getUpdateCheckErrorMessage(new AxiosError("timeout", "ECONNABORTED")),
      "无法连接 GitHub，请检查网络后重试",
    );
  });

  it("区分限流、仓库不存在和未知错误", () => {
    const limited = new AxiosError("limited");
    limited.response = { status: 403 } as never;
    const missing = new AxiosError("missing");
    missing.response = { status: 404 } as never;
    assert.equal(getUpdateCheckErrorMessage(limited), "GitHub 请求次数已达上限，请稍后重试");
    assert.equal(getUpdateCheckErrorMessage(missing), "未找到更新仓库，请检查仓库地址或网络代理");
    assert.equal(getUpdateCheckErrorMessage(new Error("bad data")), "检查更新失败，请稍后重试");
  });
});

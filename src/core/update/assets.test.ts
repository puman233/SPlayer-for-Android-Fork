import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  formatAssetSize,
  parseAssetSize,
  parseReleaseApiAssets,
  selectAndroidApkAsset,
  type AndroidReleaseAsset,
} from "./assets.ts";

const asset = (name: string): AndroidReleaseAsset => ({
  name,
  url: `https://example.test/${name}`,
  sha256: "a".repeat(64),
  size: 64 * 1024 * 1024,
});

describe("GitHub Release asset parsing", () => {
  it("读取官方 digest、下载地址和文件大小", () => {
    const assets = parseReleaseApiAssets({
      assets: [
        {
          name: "app-x86_64-release.apk",
          browser_download_url: "https://example.test/app.apk",
          digest: `sha256:${"B".repeat(64)}`,
          size: 65_677_430,
        },
        {
          name: "app-arm64-v8a-release.apk",
          browser_download_url: "https://example.test/unsigned.apk",
          digest: null,
          size: 1,
        },
      ],
    });
    assert.deepEqual(assets, [
      {
        name: "app-x86_64-release.apk",
        url: "https://example.test/app.apk",
        sha256: "b".repeat(64),
        size: 65_677_430,
      },
    ]);
  });

  it("格式化附件大小并处理未知大小", () => {
    assert.equal(formatAssetSize(65_677_430), "62.6 MB");
    assert.equal(formatAssetSize(0), "大小未知");
    assert.equal(parseAssetSize("SHA256:abc 62.6 MB"), 65_640_858);
    assert.equal(parseAssetSize("没有大小"), 0);
  });
});

describe("Android update asset selection", () => {
  const assets = [
    asset("app-armeabi-v7a-release.apk"),
    asset("app-arm64-v8a-release.apk"),
    asset("app-x86-release.apk"),
    asset("app-x86_64-release.apk"),
  ];

  it("按设备 ABI 优先级选择 APK", () => {
    assert.equal(
      selectAndroidApkAsset(assets, ["x86_64", "arm64-v8a"])?.name,
      "app-x86_64-release.apk",
    );
    assert.equal(
      selectAndroidApkAsset(assets, ["arm64-v8a", "armeabi-v7a"])?.name,
      "app-arm64-v8a-release.apk",
    );
  });

  it("x86 不会误匹配 x86_64", () => {
    assert.equal(selectAndroidApkAsset([asset("app-x86_64-release.apk")], ["x86"]), null);
  });

  it("拒绝未签名或缺少校验摘要的 APK", () => {
    const unsigned = asset("app-arm64-v8a-release-unsigned.apk");
    const withoutDigest = { ...asset("app-arm64-v8a-release.apk"), sha256: "" };
    assert.equal(selectAndroidApkAsset([unsigned, withoutDigest], ["arm64-v8a"]), null);
  });
  it("无匹配 ABI 时选择 universal，并排除 debug 包", () => {
    assert.equal(
      selectAndroidApkAsset([asset("app-universal-release.apk")], ["riscv64"])?.abi,
      "universal",
    );
    assert.equal(selectAndroidApkAsset([asset("app-arm64-v8a-debug.apk")], ["arm64-v8a"]), null);
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { selectAndroidApkAsset, type AndroidReleaseAsset } from "./assets.ts";

const asset = (name: string): AndroidReleaseAsset => ({
  name,
  url: `https://example.test/${name}`,
  sha256: "a".repeat(64),
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
});

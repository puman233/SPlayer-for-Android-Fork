import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { compareVersions, isVersionNewer, parseVersion } from "./version.ts";

describe("update version comparison", () => {
  it("支持 v 前缀、缺省段和构建元数据", () => {
    assert.equal(compareVersions("v3.1", "3.0.9"), 1);
    assert.equal(compareVersions("3.0.8+build.12", "v3.0.8"), 0);
  });

  it("稳定版高于同版本预发布版", () => {
    assert.equal(compareVersions("3.1.0", "3.1.0-rc.2"), 1);
    assert.equal(isVersionNewer("v3.1.0-rc.2", "3.1.0"), false);
  });

  it("按 SemVer 规则比较预发布标识", () => {
    assert.equal(compareVersions("3.1.0-rc.10", "3.1.0-rc.2"), 1);
    assert.equal(compareVersions("3.1.0-beta", "3.1.0-rc"), -1);
  });

  it("拒绝非版本标签", () => {
    assert.equal(parseVersion("latest"), null);
    assert.equal(compareVersions("nightly", "3.0.8"), null);
  });
});

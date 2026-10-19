import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { compareVersions, filterUpdateReleases, isVersionNewer, parseVersion } from "./version.ts";

describe("update version comparison", () => {
  it("混合发布按版本选择最高项，默认通道不受错误 prerelease 标记影响", () => {
    const releases = [
      { tag_name: "v3.0.15", prerelease: false },
      { tag_name: "v3.0.16-beta.7", prerelease: true },
      { tag_name: "v3.0.16-beta.6", prerelease: false },
      { tag_name: "v3.0.16", prerelease: false, draft: true },
      { tag_name: "v3.0.14", prerelease: false },
    ];
    assert.deepEqual(
      filterUpdateReleases(releases).map((r) => r.tag_name),
      ["v3.0.15", "v3.0.14"],
    );
    assert.deepEqual(
      filterUpdateReleases(releases, true).map((r) => r.tag_name),
      ["v3.0.16-beta.7", "v3.0.16-beta.6", "v3.0.15", "v3.0.14"],
    );
    assert.equal(releases[0].tag_name, "v3.0.15", "筛选不得重排缓存原数组");
  });

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
    for (const version of ["3.0.16-", "3.0.16-beta..1", "3.0.16-beta/7", "3.0.16-beta.01"]) {
      assert.equal(parseVersion(version), null, version);
    }
    assert.equal(compareVersions("3.0.16-beta-x.2", "3.0.16-beta-x.1"), 1);
  });

  it("Beta 选择不降级，并允许同核心 Beta 升 Stable", () => {
    const tags = ["v3.0.15", "v3.0.16-beta.6", "v3.0.16-beta.7", "v3.0.16"];
    const candidates = tags.map((tag_name) => ({ tag_name, prerelease: tag_name.includes("-") }));
    const beta = filterUpdateReleases(candidates.slice(0, 3), true)[0].tag_name;
    assert.equal(beta, "v3.0.16-beta.7");
    assert.equal(isVersionNewer(beta, "3.0.15"), true);
    assert.equal(isVersionNewer(beta, "3.0.16-beta.6"), true);
    assert.equal(isVersionNewer(beta, "3.0.16-beta.7"), false);
    assert.equal(isVersionNewer(beta, "3.0.16"), false);
    assert.equal(
      isVersionNewer(filterUpdateReleases(candidates)[0].tag_name, "3.0.16-beta.7"),
      true,
    );
    assert.equal(isVersionNewer("v3.0.15", "3.0.16-beta.7"), false);
  });

  it("稳定通道排除草稿、预发布和非法标签", () => {
    const releases = [
      { tag_name: "nightly", draft: false, prerelease: false },
      { tag_name: "v3.1.0-rc.1", draft: false, prerelease: true },
      { tag_name: "v3.0.9", draft: true, prerelease: false },
      { tag_name: "v3.0.8", draft: false, prerelease: false },
    ];
    assert.deepEqual(filterUpdateReleases(releases), [releases[3]]);
    assert.deepEqual(filterUpdateReleases(releases, true), [releases[1], releases[3]]);
    assert.deepEqual(filterUpdateReleases([]), []);
  });
});

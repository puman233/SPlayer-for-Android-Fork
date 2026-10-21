import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import {
  parseRelease,
  parseCatalog,
  selectReleases,
  RELEASES_URL,
} from "../src/services/releaseModel.ts";
import { assetMatchesAbi, isInstallableApk } from "../src/utils/abi.ts";
import { generate } from "../scripts/generate-release-metadata.mjs";

const release = (tag = "v1.0.0", prerelease = false, published_at = "2026-01-01T00:00:00Z") => ({
  tag_name: tag,
  prerelease,
  draft: false,
  published_at,
  html_url: `${RELEASES_URL}/tag/${tag}`,
  body: "<img src=x onerror=alert(1)> Keep this as plain text.",
  assets: [
    {
      name: "app-arm64-v8a-release.apk",
      size: 1234,
      state: "uploaded",
      digest: `sha256:${"a".repeat(64)}`,
      browser_download_url: `${RELEASES_URL}/download/${tag}/app-arm64-v8a-release.apk`,
    },
  ],
});

test("channels use published_at rather than input order", () => {
  const c = selectReleases([
    release("v1.0.1-beta.1", true),
    release(),
    release("v1.0.1-beta.2", true, "2026-02-01T00:00:00Z"),
  ]);
  assert.equal(c.stable.tag, "v1.0.0");
  assert.equal(c.beta.tag, "v1.0.1-beta.2");
});
for (const [label, mutate] of [
  [
    "draft",
    (r) => {
      r.draft = true;
    },
  ],
  [
    "missing prerelease flag",
    (r) => {
      delete r.prerelease;
    },
  ],
  [
    "mismatched channel",
    (r) => {
      r.prerelease = true;
    },
  ],
  [
    "invalid tag",
    (r) => {
      r.tag_name = "latest";
    },
  ],
  [
    "invalid date",
    (r) => {
      r.published_at = "no";
    },
  ],
  [
    "future date",
    (r) => {
      r.published_at = "2999-01-01T00:00:00Z";
    },
  ],
  [
    "javascript release URL",
    (r) => {
      r.html_url = "javascript:alert(1)";
    },
  ],
  [
    "wrong repository",
    (r) => {
      r.assets[0].browser_download_url = "https://github.com/other/repo/a.apk";
    },
  ],
  [
    "javascript asset",
    (r) => {
      r.assets[0].browser_download_url = "javascript:alert(1)";
    },
  ],
  [
    "wrong release tag",
    (r) => {
      r.assets[0].browser_download_url = r.assets[0].browser_download_url.replace(
        "v1.0.0/",
        "v9.0.0/",
      );
    },
  ],
  [
    "invalid size",
    (r) => {
      r.assets[0].size = -1;
    },
  ],
  [
    "pending upload",
    (r) => {
      r.assets[0].state = "new";
    },
  ],
  [
    "empty assets",
    (r) => {
      r.assets = [];
    },
  ],
  [
    "debug APK",
    (r) => {
      r.assets[0].name = "app-arm64-v8a-debug.apk";
    },
  ],
  [
    "unknown ABI",
    (r) => {
      r.assets[0].name = "app-mips-release.apk";
    },
  ],
])
  test(`reject ${label}`, () => {
    const r = release();
    mutate(r);
    assert.equal(parseRelease(r), null);
  });

test("preserve valid digest; omit invented or invalid digest", () => {
  const r = release();
  assert.match(parseRelease(r).assets[0].digest, /^sha256:/);
  r.assets[0].digest = "fake";
  assert.equal(parseRelease(r).assets[0].digest, undefined);
});
test("normalize catalog and keep notes as text", () => {
  const c = selectReleases([release()]);
  assert.deepEqual(parseCatalog(c), c);
  assert.match(c.stable.body, /<img/);
  assert.equal(c.beta, null);
});
test("reject malformed and swapped catalogs", () => {
  assert.equal(parseCatalog({ tag: "old" }), null);
  const c = selectReleases([release()]);
  assert.equal(parseCatalog({ ...c, stable: null, beta: c.stable }), null);
  assert.equal(parseCatalog({ ...c, generatedAt: "bad" }), null);
});
test("ABI boundary avoids x86/x86_64 confusion", () => {
  assert.equal(assetMatchesAbi("app-x86_64-release.apk", "x86"), false);
  assert.equal(assetMatchesAbi("app-x86_64-release.apk", "x86_64"), true);
  assert.equal(isInstallableApk("app-arm64-v8a-unsigned.apk"), false);
});
test("generator paginates, atomically writes and retains only validated snapshots", async () => {
  const folder = await mkdtemp(join(tmpdir(), "splayer-site-"));
  const output = pathToFileURL(join(folder, "release.json"));
  try {
    let requests = 0;
    const result = await generate({
      output,
      request: async () => ({
        ok: true,
        json: async () =>
          ++requests === 1
            ? Array.from({ length: 100 }, () => release("v1.0.1-beta.1", true))
            : [release()],
      }),
    });
    assert.equal(requests, 2);
    assert.equal(result.retained, false);
    const before = await readFile(output, "utf8");
    const retained = await generate({
      output,
      request: async () => {
        throw Error("offline");
      },
    });
    assert.equal(retained.retained, true);
    assert.equal(await readFile(output, "utf8"), before);
    await writeFile(output, '{"arbitrary":true}');
    await assert.rejects(generate({ output, request: async () => ({ ok: false, status: 403 }) }));
  } finally {
    await rm(folder, { recursive: true, force: true });
  }
});

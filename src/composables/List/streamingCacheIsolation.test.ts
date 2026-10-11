import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

function fixture() {
  const files = new Map<string, string>(); const removed: string[] = [];
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./useListDataCache.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, { module, exports: module.exports, TextDecoder, console,
    require: () => ({ useCacheManager: () => ({
      set: async (_: string, key: string, data: string) => { files.set(key, data); },
      get: async (_: string, key: string) => ({ success: files.has(key), data: files.has(key) ? new TextEncoder().encode(files.get(key)) : null }),
      remove: async (_: string, key: string) => { removed.push(key); files.delete(key); },
    }) }),
  });
  return { cache: module.exports.useListDataCache(), files, removed };
}

test("identical playlist IDs on different server/account scopes never share offline data", async () => {
  const f = fixture();
  await f.cache.saveCache("streaming-playlist", "same", { name: "A" }, [{ id: 1 }], true, "scope-A");
  await f.cache.saveCache("streaming-playlist", "same", { name: "B" }, [{ id: 2 }], true, "scope-B");
  assert.equal((await f.cache.loadCache("streaming-playlist", "same", "scope-A")).detail.name, "A");
  assert.equal((await f.cache.loadCache("streaming-playlist", "same", "scope-B")).detail.name, "B");
});

test("legacy streaming caches remain untouched and are never assigned to a new identity", async () => {
  const f = fixture(); const legacy = JSON.stringify({ version: 2, type: "streaming-playlist", id: "same", detail: { name: "Unowned" }, songs: [{ id: 99 }] });
  f.files.set("streaming-playlist-same.json", legacy);
  assert.equal(await f.cache.loadCache("streaming-playlist", "same", "scope-A"), null);
  assert.equal(await f.cache.loadCache("streaming-playlist", "same"), null);
  assert.equal(f.files.get("streaming-playlist-same.json"), legacy);
  assert.equal(f.removed.length, 0);
});

test("cache payload identity mismatches cannot render or delete someone else's data", async () => {
  const f = fixture();
  await f.cache.saveCache("streaming-playlist", "same", { name: "A" }, [{ id: 1 }], true, "scope-A");
  const [key, original] = [...f.files.entries()][0];
  for (const changed of [{ scope: "scope-B" }, { id: "other" }, { type: "playlist" }]) {
    f.files.set(key, JSON.stringify({ ...JSON.parse(original), ...changed }));
    assert.equal(await f.cache.loadCache("streaming-playlist", "same", "scope-A"), null);
    assert.equal(f.files.has(key), true);
  }
  assert.equal(f.removed.length, 0);
});

test("ordinary playlist cache naming and partial-cache continuation remain compatible", async () => {
  const f = fixture();
  await f.cache.saveCache("playlist", 42, { name: "Ordinary", count: 100 }, [{ id: 1 }], false);
  assert.equal(f.files.has("playlist-42.json"), true);
  const restored = await f.cache.loadCache("playlist", 42);
  assert.equal(restored.complete, false); assert.equal(restored.songs[0].id, 1);
  assert.equal(f.cache.checkNeedsUpdate(restored, { count: 100 }), false);
});

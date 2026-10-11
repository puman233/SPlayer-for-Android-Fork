import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

function fixture() {
  const entries = new Map<string, string>();
  let now = 1000;
  const storage = { getItem: (key: string) => entries.get(key) ?? null, setItem: (key: string, value: string) => entries.set(key, value) };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./cache.ts", import.meta.url), "utf8"), { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports, require: () => ({ requestFailureCategory: () => "network" }),
    window: { localStorage: storage, sessionStorage: storage },
    Date: class extends Date { constructor() { super(now); } }, console: { log() {}, error() {} },
  });
  return { get: module.exports.getCacheData, entries, advance: (ms: number) => { now += ms; } };
}

test("R27: time=-1 caches permanently across repeated calls", async () => {
  const f = fixture(); let count = 0;
  const fetch = async () => ++count;
  assert.equal(await f.get(fetch, { key: "fixture", time: -1 }), 1);
  f.advance(1000000);
  assert.equal(await f.get(fetch, { key: "fixture", time: -1 }), 1);
  assert.equal(count, 1);
});

test("R27: legacy permanent sentinels remain readable without clearing storage", async () => {
  for (const expiry of [-1, 0]) {
    const f = fixture(); f.entries.set("legacy", JSON.stringify({ value: "saved", expiry }));
    assert.equal(await f.get(async () => "network", { key: "legacy", time: -1 }), "saved");
  }
});

test("R27: immediate expiry, TTL and forced refresh retain their established behavior", async () => {
  const f = fixture(); let count = 0; const fetch = async () => ++count;
  assert.equal(await f.get(fetch, { key: "zero", time: 0 }), 1);
  assert.equal(await f.get(fetch, { key: "zero", time: 0 }), 2);
  assert.equal(await f.get(fetch, { key: "ttl", time: 1 }), 3);
  f.advance(59999); assert.equal(await f.get(fetch, { key: "ttl", time: 1 }), 3);
  f.advance(1); assert.equal(await f.get(fetch, { key: "ttl", time: 1 }), 4);
  assert.equal(await f.get(fetch, { key: "ttl", time: 1, useCache: false }), 5);
});

test("R27: a failed refresh propagates the original failure and preserves cached bytes", async () => {
  const f = fixture(); const bytes = JSON.stringify({ value: "saved", expiry: -1 }); f.entries.set("fixture", bytes);
  const error = new Error("fixture failure");
  await assert.rejects(f.get(async () => { throw error; }, { key: "fixture", time: -1, useCache: false }), (actual: unknown) => actual === error);
  assert.equal(f.entries.get("fixture"), bytes);
});

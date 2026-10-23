import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

function fixture() {
  const requests: AbortSignal[] = [];
  const module = { exports: {} as any };
  const imports: Record<string, any> = {
    "@/utils/env": { isElectron: false, isCapacitorAndroid: true },
    "@/stores": { useSettingStore: () => ({ amllDbServer: "https://lyrics.example/%s" }) },
  };
  runInNewContext(transpileModule(readFileSync(new URL("./song.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, {
    module, exports: module.exports, require: (name: string) => imports[name] || {},
    AbortController, setTimeout, clearTimeout,
    fetch: async (_url: string, options: RequestInit) => new Promise((_resolve, reject) => {
      const signal = options.signal!;
      requests.push(signal);
      signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")), { once: true });
    }),
  });
  return { fetchTtml: module.exports.songLyricTTML, requests };
}

test("the lyric source deadline cancels the real pending TTML fetch adapter", async () => {
  const f = fixture(), deadline = new AbortController();
  const pending = f.fetchTtml(1, deadline.signal);
  assert.equal(f.requests.length, 1);
  assert.equal(f.requests[0].aborted, false);
  deadline.abort();
  assert.equal(f.requests[0].aborted, true);
  assert.equal(await pending, null);
});

test("an already obsolete TTML source does not start HTTP work", async () => {
  const f = fixture(), deadline = new AbortController();
  deadline.abort();
  assert.equal(await f.fetchTtml(1, deadline.signal), null);
  assert.equal(f.requests.length, 0);
});

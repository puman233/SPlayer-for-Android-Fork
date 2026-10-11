import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { createPinia, defineStore } from "pinia";
import * as vue from "vue";
import * as lodash from "lodash-es";

function fixture(databases = new Map<string, Map<string, any>>()) {
  const imports: Record<string, any> = {
    vue, pinia: { defineStore }, "lodash-es": lodash,
    "@/utils/auth": { isLogin: () => false },
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "unknown" },
    "./music": { useMusicStore: () => ({}) }, "./status": { useStatusStore: () => ({}) },
    localforage: { default: { createInstance: ({ name }: any) => {
      if (!databases.has(name)) databases.set(name, new Map());
      const db = databases.get(name)!;
      return { keys: async () => [...db.keys()], getItem: async (key: string) => db.get(key) ?? null,
        setItem: async (key: string, value: any) => { db.set(key, structuredClone(value)); return value; } };
    } } },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./data.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, { module, exports: module.exports, require: (name: string) => imports[name] || {},
    localStorage: { getItem: () => null }, window: {}, console });
  return { store: module.exports.useDataStore(createPinia()), databases };
}

test("partial download preserves verified audio receipt and directory through a cold store restore", async () => {
  const f = fixture();
  await f.store.addDownloadingSong({ id: 1, name: "fixture" }, "standard");
  const audio = { path: "content://fixture/audio", bytes: 4, sha256: "0".repeat(64),
    fileName: "fixture", fileType: "wav", directoryUri: "content://fixture/tree/original",
    subPath: "歌手/专辑", targetPath: "Music/歌手/专辑" };
  await f.store.saveDownloadedAudio(1, audio, "partial");
  const restored = fixture(f.databases);
  await restored.store.loadData();
  assert.equal(restored.store.downloadingSongs[0].status, "partial");
  assert.deepEqual(restored.store.downloadingSongs[0].audio, audio);
  assert.equal(restored.store.downloadingSongs[0].progress, 100);
});

test("restored download progress drives Vue reactive effects after restarting the task", async () => {
  const f = fixture();
  await f.store.addDownloadingSong({ id: 1, name: "fixture" }, "standard");
  const restored = fixture(f.databases);
  await restored.store.loadData();
  restored.store.updateDownloadStatus(1, "downloading");
  let visible = -1;
  const stop = vue.effect(() => { visible = restored.store.downloadingSongs[0].progress; });
  restored.store.updateDownloadProgress(1, 50, "1MB", "2MB");
  assert.equal(visible, 50);
  vue.stop(stop);
});

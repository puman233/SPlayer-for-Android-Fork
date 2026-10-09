import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

const tick = () => new Promise<void>((resolve) => setImmediate(resolve));
const deferred = () => {
  let resolve!: (value: any) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

// Native/HTTP, persistence and platform boundaries; the actual manager and strategy run unchanged.
function fixture(startup: Promise<any> | (() => Promise<any>) = Promise.resolve(), random = globalThis.crypto) {
  const requests: any[] = [];
  const songs: any[] = [];
  const messages: string[] = [];
  let progress: (event: any) => void = () => {};
  let cancelFails = false;
  const settings = {
    downloadPath: "Music", androidDownloadDirectoryUri: "fixture://directory",
    downloadMeta: false, downloadLyric: false, folderStrategy: "none", downloadThreadCount: 1,
  };
  const data = {
    downloadingSongs: songs,
    addDownloadingSong: (song: any, quality: string) => songs.push({ song, quality, status: "waiting" }),
    removeDownloadingSong: (id: number) => { const i = songs.findIndex((s) => s.song.id === id); if (i >= 0) songs.splice(i, 1); },
    updateDownloadStatus: (id: number, status: string) => { const s = songs.find((s) => s.song.id === id); if (s) s.status = status; },
    markDownloadFailed: (id: number) => data.updateDownloadStatus(id, "failed"),
    updateDownloadProgress: (id: number, percent: number) => { const s = songs.find((s) => s.song.id === id); if (s) s.progress = percent; },
  };
  const native = {
    resetDownloads: () => typeof startup === "function" ? startup() : startup,
    downloadFile: (options: any) => {
      const result = deferred();
      requests.push({ ...options, result, running: true, cancelled: false, writes: 0 });
      return result.promise.finally(() => { requests.find((r) => r.result === result).running = false; });
    },
    cancelDownload: async ({ taskId }: any) => {
      if (cancelFails) throw new Error("cancel transport failed");
      const r = requests.find((r) => r.taskId === taskId);
      if (r) r.cancelled = true;
      // Cancellation request acknowledgement is deliberately earlier than resource shutdown.
      return { status: "cancelling" };
    },
    addListener: async (_name: string, callback: typeof progress) => { progress = callback; return { remove: async () => {} }; },
  };
  const imports: Record<string, any> = {
    "@/stores": { useSettingStore: () => settings, useDataStore: () => data },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@/api/song": { songDownloadUrl: async (id: number) => ({ code: 200, data: { url: `http://fixture/${id}`, type: "mp3" } }) },
    "@/api/album": {}, "@/api/qqmusic": {}, "@/utils/meta": { songLevelData: { standard: { level: "standard" } } },
    "@/utils/format": { getPlayerInfoObj: (song: any) => ({ name: song.name, artist: "artist", album: "album" }) },
    "@/plugins/androidDownload": { AndroidDownload: native },
    "file-saver": {}, "lodash-es": { cloneDeep: structuredClone },
    // No lyrics requested in these scheduling tests; never replace an exercised collaborator.
    "./LyricProcessor": { LyricProcessor: new Proxy({}, { get: () => { throw new Error("unexpected lyric processing"); } }) },
  };
  const module = { exports: {} as any };
  const source = readFileSync(new URL("./DownloadManager.ts", import.meta.url), "utf8");
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports,
    require: (name: string) => { if (!(name in imports)) throw new Error(`unconfigured boundary ${name}`); return imports[name]; },
    console: { log() {}, error() {} }, crypto: random,
    window: { $message: { success: (s: string) => messages.push(s), error: (s: string) => messages.push(s) } },
  });
  const manager = module.exports.downloadManager;
  return { manager, songs, requests, messages, settings, emit: (e: any) => progress(e), failCancel: () => { cancelFails = true; },
    add: async (id: number) => { await manager.addDownload({ id, name: `song${id}`, album: "album", artists: "artist" }, "standard"); await tick(); },
  };
}

test("deleting an active download cancels native IO and retains its slot until shutdown", async () => {
  const f = fixture();
  await f.add(1);
  f.manager.removeDownload(1);
  await f.add(2);
  assert.equal(f.requests[0].cancelled, true, "native request must receive cancellation");
  assert.equal(f.requests.length, 1, "B must wait for A's resources to stop");
  f.requests[0].result.resolve({ status: "cancelled" });
  await tick();
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests.filter((r) => r.running).length, 1);
});

test("a deleted and re-added song has a new identity and ignores old completion/progress", async () => {
  const f = fixture();
  await f.add(1);
  const old = f.requests[0];
  f.manager.removeDownload(1);
  await f.add(1);
  f.emit({ taskId: old.taskId, bytesRead: 900, contentLength: 1000, percent: 0.9 });
  assert.equal(f.songs[0].progress, undefined);
  old.result.resolve({ status: "success", taskId: old.taskId });
  await tick();
  assert.equal(f.songs.length, 1);
  assert.equal(f.messages.length, 0);
  assert.notEqual(f.requests[1].taskId, old.taskId);
  assert.equal(f.songs[0].status, "downloading");
  f.emit({ taskId: old.taskId, bytesRead: 1000, contentLength: 1000, percent: 1 });
  assert.equal(f.songs[0].progress, undefined);
  f.emit({ taskId: f.requests[1].taskId, bytesRead: 500, contentLength: 1000, percent: 0.5 });
  assert.equal(f.songs[0].progress, 50);
});

test("deleting a queued song never starts a native request", async () => {
  const f = fixture();
  await f.add(1);
  await f.add(2);
  f.manager.removeDownload(2);
  f.requests[0].result.resolve({ status: "success" });
  await tick();
  assert.equal(f.requests.length, 1);
  assert.equal(f.songs.length, 0);
});

test("failed cancellation retains actual resource occupancy and suppresses late failure", async () => {
  const f = fixture();
  await f.add(1);
  f.failCancel();
  f.manager.removeDownload(1);
  await f.add(2);
  assert.equal(f.requests.length, 1);
  f.requests[0].result.reject(new Error("native failed after cancellation"));
  await tick();
  assert.equal(f.requests.length, 2);
  assert.equal(f.songs[0].song.id, 2);
  assert.equal(f.songs[0].status, "downloading");
  assert.equal(f.messages.length, 1, "only cancellation transport error is shown");
});

test("native failure releases its slot and retry starts once", async () => {
  const f = fixture();
  await f.add(1);
  await f.add(2);
  f.requests[0].result.reject(new Error("native IO failure"));
  await tick();
  assert.equal(f.requests.length, 2);
  f.manager.retryDownload(1);
  f.manager.retryDownload(1);
  f.requests[1].result.resolve({ status: "success" });
  await tick();
  assert.equal(f.requests.length, 3);
  assert.notEqual(f.requests[0].taskId, f.requests[2].taskId);
});

test("restored downloading entries are restarted as fresh tasks", async () => {
  const f = fixture();
  f.songs.push({ song: { id: 1, name: "song1", album: "album" }, quality: "standard", status: "downloading" });
  f.manager.init();
  await tick();
  assert.equal(f.requests.length, 1);
  assert.ok(f.requests[0].taskId);
});

test("default names and artist/album relative paths remain unchanged", async () => {
  const f = fixture();
  Object.assign(f.settings, { fileNameFormat: "artist-title", folderStrategy: "artist-album" });
  await f.add(1);
  assert.equal(f.requests[0].fileName, "artist - song1.mp3");
  assert.equal(f.requests[0].subPath, "artist/album");
  assert.equal(f.requests[0].directoryUri, "fixture://directory");
});

test("restoring a WebView waits for stale native workers to settle before opening requests", async () => {
  const oldWorkers = deferred();
  const f = fixture(oldWorkers.promise);
  await f.add(1);
  assert.equal(f.requests.length, 0, "native resources from the previous WebView still occupy slots");
  oldWorkers.resolve({});
  await tick();
  assert.equal(f.requests.length, 1);
});

test("failed native startup can be retried without permanently poisoning the queue", async () => {
  let failStartup = true;
  const f = fixture(() => failStartup ? Promise.reject(new Error("startup failed")) : Promise.resolve());
  await f.add(1);
  assert.equal(f.songs[0].status, "failed");
  failStartup = false;
  f.manager.retryDownload(1);
  await tick();
  assert.equal(f.requests.length, 1);
});

test("older Android WebViews without randomUUID still get unique download instances", async () => {
  const f = fixture(Promise.resolve(), { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) } as Crypto);
  await f.add(1);
  assert.match(f.requests[0].taskId, /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
  const old = f.requests[0].taskId;
  f.requests[0].result.resolve({ status: "success" });
  await tick();
  await f.add(1);
  assert.notEqual(f.requests[1].taskId, old);
});

test("deletion racing a resolved native completion cannot report success for the removed task", async () => {
  const f = fixture();
  await f.add(1);
  f.requests[0].result.resolve({ status: "success" });
  f.manager.removeDownload(1);
  await f.add(1);
  assert.equal(f.messages.length, 0);
  assert.equal(f.songs.length, 1);
  assert.equal(f.requests.length, 2);
  assert.equal(f.songs[0].status, "downloading");
});

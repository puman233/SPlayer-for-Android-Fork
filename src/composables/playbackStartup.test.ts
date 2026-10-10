import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";

function deferred() {
  let resolve!: (value?: any) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function fixture(nativeState: any = { playing: false }, native = true, songPresent = true, holdNative = false) {
  const data = deferred(); const query = deferred();
  let mount!: () => Promise<void>; let unmount!: () => void;
  let initCount = 0;
  let postSetupCount = 0;
  const calls: any[] = [];
  const status: any = { autoClose: { enable: false }, currentTime: 0, playLoading: true, playStatus: false };
  const song = { id: 1, name: "fixture", duration: 60000 };
  const audio = { src: "fixture://audio", paused: true, duration: 60, currentTime: 0,
    pause() { audio.paused = true; }, seek(time: number) { audio.currentTime = time; },
    clearForcePaused() {}, async resume() { audio.paused = false; }, addEventListener() {},
  };
  const settings = { autoPlay: false, memoryLastSeek: true, checkAndMigrate() {} };
  const controllerImports: Record<string, any> = {
    "@/stores": { useStatusStore: () => status, useSettingStore: () => settings,
      useDataStore: () => ({}), useMusicStore: () => ({}) },
    "./AudioManager": { useAudioManager: () => audio },
    "./PlayModeManager": { PlayModeManager: class {} },
    "./MediaSessionManager": { mediaSessionManager: { updateState() {} } },
    "lodash-es": { throttle: (fn: any) => Object.assign(fn, { cancel() {} }) },
  };
  const controllerModule = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("../core/player/PlayerController.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText, { module: controllerModule, exports: controllerModule.exports,
    require: (name: string) => controllerImports[name] || {}, window: {}, console: { log() {} } });
  // Pause, play and seek are the real public controller commands; only the startup
  // source-loading adapter is replaced so we can observe an unwanted reload.
  const player: any = Object.assign(controllerModule.exports.usePlayerController(), {
    playSong: (options: any) => { calls.push(options); player.currentRequestToken++; },
    restoreAndroidDesktopLyric: async () => {}, playModeSyncIpc() {}, syncAndroidPlaybackContext() {},
    setupSongUI(_song: any, seek: number) { status.currentTime = seek; }, applyReplayGain() {},
    async afterPlaySetup() { postSetupCount++; },
  });
  const imports: Record<string, any> = {
    vue: { onMounted: (fn: any) => { mount = fn; }, onUnmounted: (fn: any) => { unmount = fn; }, watch() {} },
    "@/stores": { useDataStore: () => ({ loadData: () => data.promise }), useStatusStore: () => status,
      useSettingStore: () => settings, useShortcutStore: () => ({}) },
    "@/core/player/PlayerController": { usePlayerController: () => player },
    "@/core/player/AudioManager": { useAudioManager: () => ({ engineType: native ? "android-native" : "element", init: () => initCount++ }) },
    "@/plugins/androidNativePlayback": { AndroidNativePlayback: { getState: () => query.promise } },
    "@/utils/format": { getPlaySongData: () => songPresent ? song : null },
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "unknown" },
    "@/core/player/MediaSessionManager": { mediaSessionManager: { init() {} } },
    "@/core/resource/DownloadManager": { useDownloadManager: () => ({ init() {} }) },
    "@capacitor/app": { App: { addListener: async () => ({ remove() {} }) } },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@vueuse/core": { useEventListener() {} }, "lodash-es": { debounce: (fn: any) => fn },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./useInit.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText, { module, exports: module.exports, require: (name: string) => imports[name] || {},
    window: {}, console: { warn() {}, error() {} }, setTimeout() {} });
  module.exports.useInit(); if (!holdNative) query.resolve(nativeState);
  return { mount: () => mount(), unmount: () => unmount(), data, query, player, calls, status, settings,
    initCount: () => initCount, postSetupCount: () => postSetupCount };
}

test("startup restoration cannot overwrite a playback command issued while persistence loads", async () => {
  const f = fixture(); const job = f.mount(); f.player.currentRequestToken++;
  f.data.resolve(); await job; assert.equal(f.calls.length, 0);
});

test("Activity recreation adopts matching running native audio without a paused reload", async () => {
  const f = fixture({ playing: true, src: "fixture://audio", songId: 1, positionMs: 9000, durationMs: 60000 });
  const job = f.mount(); f.data.resolve(); await job;
  assert.equal(f.calls.length, 0); assert.equal(f.initCount(), 1);
  assert.equal(f.status.playStatus, true); assert.equal(f.status.currentTime, 9000); assert.equal(f.status.playLoading, false);
  assert.equal(f.player.currentAudioSource.url, "fixture://audio");
});

test("unmounted startup never initializes playback after its data read finishes", async () => {
  const f = fixture(); const job = f.mount(); f.unmount(); f.data.resolve(); await job;
  assert.equal(f.calls.length, 0);
});

test("a late native startup snapshot cannot supersede a newer playback command", async () => {
  const f = fixture(undefined, true, true, true); const job = f.mount(); f.data.resolve();
  await new Promise<void>(resolve => setImmediate(resolve)); f.player.currentRequestToken++;
  f.query.resolve({ playing: true, src: "fixture://old", songId: 1 }); await job;
  assert.equal(f.calls.length, 0); assert.equal(f.initCount(), 0);
});

test("unavailable native startup query keeps the normal cold-start policy", async () => {
  const f = fixture(undefined, true, true, true); const job = f.mount(); f.data.resolve();
  await new Promise<void>(resolve => setImmediate(resolve)); f.query.reject(new Error("fixture plugin unavailable"));
  await job; assert.equal(f.calls.length, 1); assert.equal(f.calls[0].autoPlay, false);
});

test("native state without matching playlist identity is not adopted", async () => {
  const f = fixture({ playing: true, src: "fixture://audio" }, true, false); const job = f.mount();
  f.data.resolve(); await job; assert.equal(f.calls.length, 1); assert.equal(f.initCount(), 0);
});

for (const [name, state, native] of [
  ["paused native", { playing: false }, true],
  ["different native song", { playing: true, src: "fixture://other", songId: 2 }, true],
  ["HTML audio engine", { playing: true, src: "fixture://audio", songId: 1 }, false],
] as const) {
  test(`${name} retains the normal startup autoplay preference`, async () => {
    const f = fixture(state, native); const job = f.mount(); f.data.resolve(); await job;
    assert.equal(f.calls.length, 1); assert.equal(f.calls[0].autoPlay, false);
  });
}

test("a pause during startup cannot be replaced by a late playing native snapshot", async () => {
  const f = fixture(undefined, true, true, true); const job = f.mount(); f.data.resolve();
  await new Promise<void>(resolve => setImmediate(resolve)); await f.player.pause();
  f.query.resolve({ playing: true, src: "fixture://audio", songId: 1, positionMs: 9000 }); await job;
  assert.equal(f.status.playStatus, false); assert.equal(f.calls.length, 0); assert.equal(f.initCount(), 1);
});

test("a seek during startup is preserved instead of the older native position", async () => {
  const f = fixture(undefined, true, true, true); const job = f.mount(); f.data.resolve();
  await new Promise<void>(resolve => setImmediate(resolve)); f.player.setSeek(12000);
  f.query.resolve({ playing: true, src: "fixture://audio", songId: 1, positionMs: 9000 }); await job;
  assert.equal(f.status.currentTime, 12000); assert.equal(f.calls.length, 0);
});

test("a cold-start pause overrides autoplay without discarding source initialization", async () => {
  const f = fixture({ playing: false }); f.settings.autoPlay = true;
  const job = f.mount(); await f.player.pause(); f.data.resolve(); await job;
  assert.equal(f.calls.length, 1); assert.equal(f.calls[0].autoPlay, false);
});

test("a cold-start seek overrides the disabled remember-position preference", async () => {
  const f = fixture({ playing: false }); f.settings.memoryLastSeek = false;
  const job = f.mount(); f.player.setSeek(12000); f.data.resolve(); await job;
  assert.equal(f.calls.length, 1); assert.equal(f.calls[0].seek, 12000);
});

test("a resume of an existing source wins over cold-start autoplay false", async () => {
  const f = fixture({ playing: false }); const job = f.mount(); await f.player.play();
  f.data.resolve(); await job;
  assert.equal(f.calls.length, 1); assert.equal(f.calls[0].autoPlay, true);
});

test("native startup adoption retains the existing metadata and scrobbler setup", async () => {
  const f = fixture({ playing: true, src: "fixture://audio", songId: 1, positionMs: 9000 });
  const job = f.mount(); f.data.resolve(); await job;
  assert.equal(f.postSetupCount(), 1); assert.equal(f.calls.length, 0);
});

test("post-setup failure after native adoption does not cause a duplicate paused reload", async () => {
  const f = fixture({ playing: true, src: "fixture://audio", songId: 1, positionMs: 9000 });
  f.player.afterPlaySetup = async () => { throw new Error("fixture metadata unavailable"); };
  const job = f.mount(); f.data.resolve(); await job;
  assert.equal(f.calls.length, 0); assert.equal(f.status.playStatus, true); assert.equal(f.initCount(), 1);
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";

const tick = () => new Promise<void>(resolve => setImmediate(resolve));
function deferred() {
  let resolve!: (value?: any) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  // Observe plugin failures independently, so the red test reports the broken
  // caller contract rather than contaminating subsequent tests with rejections.
  void promise.catch(() => {});
  return { promise, resolve, reject };
}

function fixture(delayedRegistration = false) {
  const listeners = new Map<number, { name: string; callback: (value: any) => void }>();
  const registrations: ReturnType<typeof deferred>[] = [];
  const commands: { name: string; args: any; result: ReturnType<typeof deferred> }[] = [];
  let id = 0;
  const document = Object.assign(new EventTarget(), { visibilityState: "visible" });
  const native: any = {
    addListener: (name: string, callback: (value: any) => void) => {
      const key = ++id; listeners.set(key, { name, callback });
      const handle = { remove: async () => { listeners.delete(key); } };
      if (!delayedRegistration) return Promise.resolve(handle);
      const d = deferred(); registrations.push(d);
      return d.promise.then(() => handle);
    },
  };
  for (const name of ["load", "play", "pause", "stop", "seek", "getState", "setVolume", "setRate", "setEqualizer", "enableVisualizer"]) {
    native[name] = (args: any) => {
      const result = deferred(); commands.push({ name, args, result }); return result.promise;
    };
  }
  const events = { LOAD_START: "loadstart", TIME_UPDATE: "timeupdate", PAUSE: "pause", PLAY: "play",
    ERROR: "error", ENDED: "ended", SEEKING: "seeking", SEEKED: "seeked", CAN_PLAY: "canplay" };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./AndroidNativeAudioPlayer.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText, {
    module, exports: module.exports, EventTarget, Event, CustomEvent, performance, document, atob,
    console: { warn() {}, debug() {}, error() {} },
    require: (name: string) => name === "@/plugins/androidNativePlayback" ? { AndroidNativePlayback: native }
      : name === "@/utils/env" ? { isCapacitorAndroid: true }
      : name === "./BaseAudioPlayer" ? { AUDIO_EVENTS: events }
      : name === "@/utils/requestDiagnostics" ? { requestFailureCategory: () => "unknown" } : {},
  });
  const player = new module.exports.AndroidNativeAudioPlayer();
  return { player, listeners, registrations, commands, document,
    emit: (name: string, value: any) => { for (const listener of [...listeners.values()]) if (listener.name === name) listener.callback(value); } };
}

test("destroy during listener registration removes late handles and ignores old callbacks", async () => {
  const f = fixture(true); f.player.init();
  const old = [...f.listeners.values()][0].callback;
  f.player.destroy();
  for (let i = 0; i < 8; i++) { f.registrations[i]?.resolve(); await tick(); }
  old({ src: "obsolete", paused: false, positionMs: 9000 });
  assert.equal(f.listeners.size, 0);
  assert.equal(f.player.src, ""); assert.equal(f.player.paused, true);
});

test("load rejection reaches the awaited caller and leaves playback paused", async () => {
  const f = fixture(); const failure = new Error("fixture load failure");
  const job = f.player.play("fixture://audio"); f.commands.find(c => c.name === "load")!.result.reject(failure);
  await assert.rejects(job, error => error === failure);
  assert.equal(f.player.paused, true); assert.notEqual(f.player.getErrorCode(), 0);
});

const state = (src = "fixture://audio", paused = true) => ({ src, paused, playing: !paused,
  ready: true, durationMs: 60000, positionMs: 5000, errorCode: 0 });
async function loaded(f: ReturnType<typeof fixture>, paused = true) {
  const job = f.player.play("fixture://audio", { autoPlay: !paused });
  f.commands.find(c => c.name === "load")!.result.resolve(state(undefined, paused)); await job; await tick();
}

test("resume waits for confirmation and rejection does not invent playback", async () => {
  const f = fixture(); await loaded(f); const failure = new Error("fixture unavailable");
  const job = f.player.resume(); assert.equal(f.player.paused, true);
  f.commands.find(c => c.name === "play")!.result.reject(failure);
  await assert.rejects(job, error => error === failure); assert.equal(f.player.paused, true);
  const retry = f.player.resume(); f.commands.filter(c => c.name === "play")[1].result.resolve(state(undefined, false));
  await retry; assert.equal(f.player.paused, false); assert.equal(f.player.getErrorCode(), 0);
});

for (const command of ["pause", "stop"] as const) {
  test(`${command} rejection reconciles the actual native source and playback state`, async () => {
    const f = fixture(); await loaded(f, false); f.player[command]();
    f.commands.find(c => c.name === command)!.result.reject(new Error("fixture command rejected")); await tick();
    f.commands.filter(c => c.name === "getState").at(-1)!.result.resolve(state(undefined, false)); await tick();
    assert.equal(f.player.src, "fixture://audio"); assert.equal(f.player.paused, false);
    assert.ok(f.player.currentTime >= 5);
  });
}

test("old command rejection and recovery cannot overwrite a newer load", async () => {
  const f = fixture(); await loaded(f, false); f.player.pause();
  f.commands.find(c => c.name === "pause")!.result.reject(new Error("old pause")); await tick();
  const job = f.player.play("fixture://new", { autoPlay: false });
  f.commands.filter(c => c.name === "load")[1].result.resolve(state("fixture://new", true)); await job;
  f.commands.filter(c => c.name === "getState").at(-1)!.result.resolve(state(undefined, false)); await tick();
  assert.equal(f.player.src, "fixture://new"); assert.equal(f.player.paused, true);
});

test("failed load permits retry and an older rejection leaves the new source untouched", async () => {
  const f = fixture(); const old = f.player.play("fixture://old");
  const next = f.player.play("fixture://new");
  f.commands.filter(c => c.name === "load")[1].result.resolve(state("fixture://new", false)); await next;
  f.commands.filter(c => c.name === "load")[0].result.reject(new Error("old load")); await assert.rejects(old);
  assert.equal(f.player.src, "fixture://new"); assert.equal(f.player.paused, false); assert.equal(f.player.getErrorCode(), 0);
});

test("repeated init is idempotent and destroy removes native and DOM event handlers", async () => {
  const f = fixture(); await loaded(f); f.player.init(); f.player.init(); await tick();
  assert.equal(f.listeners.size, 6); f.player.destroy(); await tick();
  assert.equal(f.listeners.size, 0);
  const before = f.commands.length; f.document.dispatchEvent(new Event("visibilitychange")); await tick();
  assert.equal(f.commands.length, before); assert.equal(f.player.src, "");
});

test("old registration completion cannot remove the current lifecycle's listeners", async () => {
  const f = fixture(true); f.player.init(); f.player.destroy(); f.player.init();
  const oldCallback = [...f.listeners.values()][0].callback;
  for (let i = 1; i < 7; i++) { f.registrations[i]?.resolve(); await tick(); }
  f.registrations[0].resolve(); await tick();
  assert.equal(f.listeners.size, 6); oldCallback(state("obsolete", false));
  assert.equal(f.player.src, "");
  f.emit("playbackStateChanged", state("current", false)); assert.equal(f.player.src, "current");
  f.player.destroy(); await tick(); assert.equal(f.listeners.size, 0);
});

test("visibility synchronization ignores hidden pages and stale completion after destroy", async () => {
  const f = fixture(); await loaded(f); f.document.visibilityState = "hidden";
  f.document.dispatchEvent(new Event("visibilitychange")); assert.equal(f.commands.filter(c => c.name === "getState").length, 1);
  f.document.visibilityState = "visible"; f.document.dispatchEvent(new Event("visibilitychange"));
  const oldQuery = f.commands.filter(c => c.name === "getState").at(-1)!;
  f.player.destroy(); f.player.init(); await tick();
  oldQuery.result.resolve(state("obsolete", false)); await tick();
  assert.equal(f.player.src, ""); assert.equal(f.player.currentTime, 0); assert.equal(f.player.paused, true);
});

test("unavailable state query after a control rejection is handled without another rejection", async () => {
  const f = fixture(); await loaded(f, false); f.player.pause();
  f.commands.find(c => c.name === "pause")!.result.reject(new Error("unavailable")); await tick();
  f.commands.filter(c => c.name === "getState").at(-1)!.result.reject(new Error("still unavailable")); await tick();
  assert.equal(f.player.getErrorCode(), 2);
});

test("foreground refresh adopts the actual paused state even without a position drift", async () => {
  const f = fixture(); await loaded(f, false);
  f.document.dispatchEvent(new Event("visibilitychange"));
  f.commands.filter(c => c.name === "getState").at(-1)!.result.resolve({ ...state(undefined, true), positionMs: 0 }); await tick();
  assert.equal(f.player.paused, true);
});

test("destroy waits behind pending visualizer enable before disabling the native resource", async () => {
  const f = fixture(); f.player.init(); const enable = f.player.enableVisualizer(true); await tick();
  const request = f.commands.find(c => c.name === "enableVisualizer")!;
  f.player.destroy(); request.result.resolve({ granted: true }); await enable; await tick();
  const disable = f.commands.filter(c => c.name === "enableVisualizer")[1];
  assert.ok(disable, "pending enable must not leave native FFT active after destruction");
  assert.equal(disable.args.enable, false); disable.result.resolve({ granted: true }); await tick();
});

test("a recreated frontend engine adopts already running native playback", async () => {
  const f = fixture(); f.player.init();
  const query = f.commands.find(c => c.name === "getState"); assert.ok(query);
  query.result.resolve(state("fixture://background", false)); await tick();
  assert.equal(f.player.src, "fixture://background"); assert.equal(f.player.paused, false);
  assert.ok(f.player.currentTime >= 5);
});

test("failed replacement load notifies playback consumers that audio is paused", async () => {
  const f = fixture(); await loaded(f, false); let playing = true;
  f.player.addEventListener("pause", () => { playing = false; });
  const job = f.player.play("fixture://replacement");
  f.commands.filter(c => c.name === "load")[1].result.reject(new Error("replacement failed"));
  await assert.rejects(job); assert.equal(playing, false); assert.equal(f.player.paused, true);
});

test("foreground query started before a seek cannot reset the newer position", async () => {
  const f = fixture(); await loaded(f); f.document.dispatchEvent(new Event("visibilitychange"));
  const query = f.commands.filter(c => c.name === "getState").at(-1)!;
  f.player.seek(12); query.result.resolve(state()); await tick();
  assert.equal(f.player.currentTime, 12);
});

test("visualizer acquisition before first play initializes and enables the native resource", async () => {
  const f = fixture(); const job = f.player.enableVisualizer(true); await tick();
  const request = f.commands.find(c => c.name === "enableVisualizer"); assert.ok(request);
  request.result.resolve({ granted: true }); assert.equal(await job, true);
  f.player.destroy(); await tick();
  f.commands.filter(c => c.name === "enableVisualizer").at(-1)!.result.resolve({ granted: true });
});

test("a control recovery query cannot rewind a seek issued after the query began", async () => {
  const f = fixture(); await loaded(f, false); f.player.pause();
  f.commands.find(c => c.name === "pause")!.result.reject(new Error("old pause")); await tick();
  const recovery = f.commands.filter(c => c.name === "getState").at(-1)!;
  f.player.seek(12); recovery.result.resolve(state(undefined, true)); await tick();
  assert.equal(f.player.currentTime, 12);
});

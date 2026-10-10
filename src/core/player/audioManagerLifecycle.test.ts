import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import { TypedEventTarget } from "../../utils/TypedEventTarget";

function fixture() {
  let finish!: () => void;
  const pending = new Promise<void>(resolve => { finish = resolve; });
  const instances: MediaAdapter[] = [];
  const timers: Array<{ fn: () => void; cancelled: boolean }> = [];
  class MediaAdapter extends EventTarget {
    capabilities = { supportsRate: false }; src = ""; paused = true; currentTime = 0;
    destroyed = false;
    constructor() { super(); instances.push(this); }
    init() {} destroy() { this.destroyed = true; this.paused = true; }
    async play(src: string) { this.src = src; if (src === "pending") await pending; this.paused = false; }
    pause() { this.paused = true; this.dispatchEvent(new Event("pause")); } stop() { this.paused = true; this.src = ""; }
    setVolume() {} setAudioDelayCompensation() {}
  }
  const settings = { playbackEngine: "web-audio", audioEngine: "element" };
  const imports: Record<string, any> = {
    "@/stores": { useSettingStore: () => settings },
    "@/utils/env": { isCapacitorAndroid: false, isElectron: false },
    "@/utils/TypedEventTarget": { TypedEventTarget },
    "../audio-player/AudioElementPlayer": { AudioElementPlayer: MediaAdapter },
    "../audio-player/BaseAudioPlayer": { AUDIO_EVENTS: { PLAY: "play", PAUSE: "pause", TIME_UPDATE: "timeupdate", ERROR: "error" } },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./AudioManager.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText, { module, exports: module.exports, window: {}, console: { log() {}, warn() {}, error() {} },
    watch() {}, setTimeout: (fn: () => void) => { timers.push({ fn, cancelled: false }); return timers.length; },
    clearTimeout: (id: number) => { timers[id - 1].cancelled = true; },
    require: (name: string) => imports[name] || {} });
  return { audio: module.exports.useAudioManager(), finish, instances,
    runTimers: () => { for (const timer of [...timers]) if (!timer.cancelled) timer.fn(); } };
}

test("cancelled pending crossfade cannot pause or replace the newer active audio", async () => {
  const f = fixture(); await f.audio.play("initial");
  const crossfade = f.audio.crossfadeTo("pending", { duration: 1 });
  f.audio.stop(); await f.audio.play("new-song"); f.finish(); await crossfade;
  assert.equal(f.audio.src, "new-song"); assert.equal(f.audio.paused, false);
  assert.equal(f.instances[1].destroyed, true);
});

test("destroy followed by init restores forwarding exactly once", async () => {
  const f = fixture(); let pauses = 0; f.audio.addEventListener("pause", () => pauses++);
  f.audio.init(); f.audio.pause(); assert.equal(pauses, 1);
  f.audio.destroy(); f.audio.init(); f.audio.init(); f.audio.pause(); assert.equal(pauses, 2);
});

test("cancelled deferred crossfade never destroys the engine reused for a new song", async () => {
  const f = fixture(); await f.audio.play("initial");
  await f.audio.crossfadeTo("ready", { duration: 1, uiSwitchDelay: 3 });
  await f.audio.play("new-song"); f.runTimers();
  assert.equal(f.instances[0].destroyed, false); assert.equal(f.audio.src, "new-song"); assert.equal(f.audio.paused, false);
  assert.equal(f.instances[1].destroyed, true);
});

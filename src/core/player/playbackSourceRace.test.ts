import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

const deferred = () => {
  let resolve!: (value: any) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const song = (id: number) => ({ id, name: `song${id}`, duration: 60000, type: "radio" });

// Exercise the full controller through its public commands. Deferred source and
// audio adapters represent network/media execution; no controller methods are replaced.
function fixture() {
  const pending: ReturnType<typeof deferred>[] = [];
  const notices: string[] = [];
  let loadCompletion: Promise<any> | undefined;
  let resumeCompletion: Promise<any> | undefined;
  const retryDelays: ReturnType<typeof deferred>[] = [];
  const settings = { enableAutomix: false, enableReplayGain: false, showSpectrums: true, lastfm: { enabled: false } };
  const status: any = { currentTime: 1000, duration: 60000, playStatus: true, playLoading: false,
    playRate: 1, playVolume: 1, abLoop: {}, $patch(fn: (state: any) => void) { fn(status); } };
  const music: any = { playSong: song(1), lyricSongId: 1 };
  const callbacks = new Map<string, (event?: any) => void>();
  const audio = {
    src: "audio1", paused: false, currentTime: 1, duration: 60, engineType: "element",
    capabilities: { supportsRate: false },
    addEventListener: (name: string, callback: (event?: any) => void) => callbacks.set(name, callback),
    stop() { audio.src = ""; audio.paused = true; },
    pause() { audio.paused = true; callbacks.get("pause")?.(); },
    async play(url: string, options: any) {
      audio.src = url; audio.currentTime = options.seek; audio.paused = !options.autoPlay;
      callbacks.get(options.autoPlay ? "play" : "pause")?.();
      await loadCompletion;
    },
    seek(time: number) { audio.currentTime = time; }, setVolume() {}, setReplayGain() {}, setPendingSeek() {},
    clearForcePaused() {}, async resume() { await resumeCompletion; },
  };
  const noop = () => {};
  const imports: Record<string, any> = {
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "unknown" },
    "@/stores": { useSettingStore: () => settings, useStatusStore: () => status, useMusicStore: () => music, useDataStore: () => ({}) },
    "@/utils/env": { isCapacitorAndroid: false, isElectron: false, isMac: false },
    "./AudioManager": { useAudioManager: () => audio },
    "./SongManager": { useSongManager: () => ({ clearPrefetch: noop, getAudioSource: () => { const d = deferred(); pending.push(d); return d.promise; } }) },
    "./LyricManager": { useLyricManager: () => ({ handleLyric: (s: any) => { music.lyricSongId = s.id; } }) },
    "@/core/automix/AutomixManager": { useAutomixManager: () => ({ resetNextAnalysisCache: noop, resetAutomixScheduling: noop }) },
    "@/utils/format": { getPlaySongData: () => music.playSong, getPlayerInfoObj: () => ({ name: "fixture", artist: "fixture" }) },
    "@/utils/color": { getCoverColor: noop },
    "@/utils/helper": { sleep: () => { const d = deferred(); retryDelays.push(d); return d.promise; } },
    "@/core/audio-player/BaseAudioPlayer": { AudioErrorCode: { ABORTED: 1, DOM_ABORT: 20, SRC_NOT_SUPPORTED: 4 } },
    "@/utils/time": { calculateProgress: (time: number, duration: number) => time / duration },
    "@/utils/lastfmScrobbler": { default: { resume: noop, pause: noop } },
    "./MediaSessionManager": { mediaSessionManager: { updatePlaybackStatus: noop, updateMetadata: noop, updateState: noop } },
    "./PlayModeManager": { PlayModeManager: class {} },
    "./PlayerIpc": new Proxy({}, { get: () => noop }),
    "lodash-es": { throttle: (fn: any) => Object.assign(fn, { cancel: noop }) },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./PlayerController.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, {
    module, exports: module.exports, Error,
    require: (name: string) => imports[name] || {},
    console: { log: noop, warn: noop, error: noop },
    window: { document: {}, $message: { error: (s: string) => notices.push(s), warning: (s: string) => notices.push(s) } },
    setTimeout: () => 0, clearTimeout: noop,
  });
  return { controller: module.exports.usePlayerController(), status, music, audio, pending, notices,
    retryDelays, emitError: () => callbacks.get("error")?.({ detail: { errorCode: 2 } }),
    delayLoad: (promise?: Promise<any>) => { loadCompletion = promise; },
    delayResume: (promise: Promise<any>) => { resumeCompletion = promise; } };
}

test("late quality response cannot replace the new song's audio, lyric identity or loading state", async () => {
  const f = fixture();
  const quality = f.controller.switchQuality(1000);
  const next = f.controller.playSong({ song: song(2), autoPlay: true });
  f.pending[1].resolve({ url: "audio2", quality: "standard", source: "default" });
  await next;
  f.pending[0].resolve({ url: "audio1-high", quality: "lossless", source: "default" });
  await quality;
  assert.deepEqual({ src: f.audio.src, song: f.music.playSong.id, lyric: f.music.lyricSongId,
    playing: f.status.playStatus, loading: f.status.playLoading },
  { src: "audio2", song: 2, lyric: 2, playing: true, loading: false });
});

test("a delayed error retry cannot restart or seek the newer paused song", async () => {
  const f = fixture(); f.emitError(); assert.equal(f.retryDelays.length, 1);
  const next = f.controller.playSong({ song: song(2), autoPlay: false, seek: 6000 });
  f.pending[0].resolve({ url: "audio2" }); await next;
  f.retryDelays[0].resolve(undefined); await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.pending.length, 1); assert.equal(f.audio.src, "audio2");
  assert.equal(f.status.playStatus, false); assert.equal(f.status.currentTime, 6000);
  assert.equal(f.status.playLoading, false);
});

test("pausing during error recovery cancels the delayed automatic restart", async () => {
  const f = fixture(); f.emitError(); await f.controller.pause();
  f.retryDelays[0].resolve(undefined); await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.pending.length, 0); assert.equal(f.audio.paused, true); assert.equal(f.status.playStatus, false);
});

test("an old resume AbortError does not restart the newer paused song", async () => {
  const f = fixture(); await f.controller.pause(); const d = deferred(); f.delayResume(d.promise);
  const old = f.controller.play(); const next = f.controller.playSong({ song: song(2), autoPlay: false });
  f.pending[0].resolve({ url: "audio2" }); await next;
  const error = new Error("old resume aborted"); error.name = "AbortError"; d.reject(error);
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.pending.length, 1); assert.equal(f.audio.src, "audio2"); assert.equal(f.status.playStatus, false);
  await old;
});

test("three quality changes resolve out of order and only the latest owns the source", async () => {
  const f = fixture();
  const jobs = [f.controller.switchQuality(), f.controller.switchQuality(), f.controller.switchQuality()];
  f.pending[2].resolve({ url: "latest", quality: "lossless" }); await jobs[2];
  f.pending[0].resolve({ url: "first" }); await jobs[0];
  f.pending[1].resolve({ url: "second" }); await jobs[1];
  assert.equal(f.audio.src, "latest"); assert.equal(f.status.songQuality, "lossless");
  assert.equal(f.music.lyricSongId, 1); assert.equal(f.status.playLoading, false);
});

test("audio source switch cannot stop or load over a later song", async () => {
  const f = fixture(); const old = f.controller.switchAudioSource("alternative");
  const next = f.controller.playSong({ song: song(2), autoPlay: false, seek: 4000 });
  f.pending[1].resolve({ url: "audio2" }); await next;
  f.pending[0].resolve({ url: "old-alternative" }); await old;
  assert.equal(f.audio.src, "audio2"); assert.equal(f.audio.paused, true);
  assert.equal(f.status.currentTime, 4000); assert.equal(f.music.lyricSongId, 2);
});

test("stale failures do not clear a current loading state or display a failure notice", async () => {
  const f = fixture(); const old = f.controller.switchQuality(); const latest = f.controller.switchQuality();
  f.pending[0].reject(new Error("old failure")); await old;
  assert.equal(f.status.playLoading, true); assert.deepEqual(f.notices, []);
  f.pending[1].resolve({ url: "latest" }); await latest;
  assert.equal(f.audio.src, "latest"); assert.equal(f.status.playLoading, false);
});

test("a late old success cannot undo a newer failed switch; subsequent retry remains possible", async () => {
  const f = fixture(); const old = f.controller.switchQuality(); const latest = f.controller.switchAudioSource("alternative");
  f.pending[1].reject(new Error("new failure")); await latest;
  f.pending[0].resolve({ url: "stale" }); await old;
  assert.equal(f.audio.src, "audio1"); assert.equal(f.status.playLoading, false); assert.equal(f.notices.length, 1);
  const retry = f.controller.switchQuality(); f.pending[2].resolve({ url: "retry" }); await retry;
  assert.equal(f.audio.src, "retry"); assert.equal(f.status.playStatus, true);
});

test("an already paused quality switch does not start playback", async () => {
  const f = fixture(); await f.controller.pause(); const job = f.controller.switchQuality(1000);
  f.pending[0].resolve({ url: "paused-quality" }); await job;
  assert.equal(f.audio.paused, true); assert.equal(f.status.playStatus, false);
  assert.equal(f.status.currentTime, 1000);
});

test("old media completion cannot publish its seek after a newer song starts", async () => {
  const f = fixture(); const completion = deferred(); f.delayLoad(completion.promise);
  const old = f.controller.switchQuality(12000); f.pending[0].resolve({ url: "old" });
  await new Promise<void>(resolve => setImmediate(resolve));
  f.delayLoad(); const next = f.controller.playSong({ song: song(2), seek: 3000, autoPlay: false });
  f.pending[1].resolve({ url: "audio2" }); await next;
  completion.resolve(undefined); await old;
  assert.equal(f.audio.src, "audio2"); assert.equal(f.status.currentTime, 3000);
  assert.equal(f.status.playStatus, false); assert.equal(f.music.lyricSongId, 2); assert.equal(f.status.playLoading, false);
});

test("seek during media preparation is not reset by the load completion", async () => {
  const f = fixture(); const completion = deferred(); f.delayLoad(completion.promise);
  const job = f.controller.playSong({ song: song(2), seek: 1000 }); f.pending[0].resolve({ url: "audio2" });
  await new Promise<void>(resolve => setImmediate(resolve));
  f.controller.setSeek(7000); completion.resolve(undefined); await job;
  assert.equal(f.status.currentTime, 7000); assert.equal(f.audio.currentTime, 7);
  assert.equal(f.audio.src, "audio2"); assert.equal(f.music.lyricSongId, 2);
});

test("pausing and seeking while quality resolution is pending preserves the latest user intent", async () => {
  const f = fixture();
  const quality = f.controller.switchQuality(1000);
  await f.controller.pause();
  f.controller.setSeek(9000);
  f.pending[0].resolve({ url: "audio1-high", quality: "lossless" });
  await quality;
  assert.deepEqual({ src: f.audio.src, paused: f.audio.paused, playing: f.status.playStatus,
    position: f.status.currentTime, loading: f.status.playLoading },
  { src: "audio1-high", paused: true, playing: false, position: 9000, loading: false });
});

test("a paused source lookup cannot resume or reset a newer seek when it completes", async () => {
  const f = fixture(); const job = f.controller.playSong({ song: song(2), seek: 1000 });
  await f.controller.pause(); f.controller.setSeek(9000);
  f.pending[0].resolve({ url: "audio2" }); await job;
  assert.equal(f.audio.paused, true); assert.equal(f.status.playStatus, false);
  assert.equal(f.status.currentTime, 9000); assert.equal(f.audio.currentTime, 9);
});

test("a late resume AbortError cannot undo a newer pause of the same song", async () => {
  const f = fixture(); await f.controller.pause(); const d = deferred(); f.delayResume(d.promise);
  const job = f.controller.play(); await f.controller.pause();
  const error = new Error("old resume aborted"); error.name = "AbortError"; d.reject(error);
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.pending.length, 0); assert.equal(f.status.playStatus, false); assert.equal(f.audio.paused, true);
  await job;
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { DJ_MODE_KEYWORDS } from "../../utils/meta";
import { QualityType } from "../../types/main";

const deferred = () => {
  let resolve!: (value: any) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const song = (id: number) => ({ id, name: `song${id}`, duration: 60000, type: "radio" });

// Exercise the full controller through its public commands. Deferred source and
// audio adapters represent network/media execution; no controller methods are replaced.
function fixture(native = false) {
  let now = 10000;
  const listeningEvents: string[] = [];
  const loadedSources: string[] = [];
  const loadedOptions: any[] = [];
  const metadataUpdates: any[] = [];
  const data: any = { playList: [] };
  const pending: ReturnType<typeof deferred>[] = [];
  const notices: string[] = [];
  let loadCompletion: Promise<any> | undefined;
  let resumeCompletion: Promise<any> | undefined;
  let metadataCompletion: Promise<any> | undefined;
  let contextCompletion: Promise<any> | undefined;
  const retryDelays: ReturnType<typeof deferred>[] = [];
  const settings = { enableAutomix: false, enableReplayGain: false, showSpectrums: true, disableDjMode: false, lastfm: { enabled: false } };
  const status: any = { currentTime: 1000, duration: 60000, playStatus: true, playLoading: false,
    playRate: 1, playVolume: 1, abLoop: {}, getSongOffset: () => 0,
    $patch(update: any) { if (typeof update === "function") update(status); else Object.assign(status, update); } };
  const music: any = { playSong: song(1), lyricSongId: 1, songLyric: { lrcData: [], yrcData: [] } };
  const callbacks = new Map<string, (event?: any) => void>();
  const audio = {
    src: "audio1", paused: false, currentTime: 1, duration: 60, engineType: native ? "android-native" : "element",
    capabilities: { supportsRate: false },
    addEventListener: (name: string, callback: (event?: any) => void) => callbacks.set(name, callback),
    stop() { audio.src = ""; audio.paused = true; },
    pause() { audio.paused = true; callbacks.get("pause")?.(); },
    async play(url: string, options: any) {
      loadedSources.push(url); loadedOptions.push(options);
      audio.src = url; audio.currentTime = options.seek; audio.paused = !options.autoPlay;
      callbacks.get(options.autoPlay ? "play" : "pause")?.();
      await loadCompletion;
    },
    seek(time: number) { audio.currentTime = time; }, setVolume() {}, setReplayGain() {}, setPendingSeek() {},
    clearForcePaused() {}, async resume() { await resumeCompletion; },
  };
  const noop = () => {};
  const imports: Record<string, any> = {
    vue: { toRaw: (value: any) => value },
    "@/types/main": { QualityType },
    "@/utils/meta": { DJ_MODE_KEYWORDS },
    "@/utils/calc": { calculateLyricIndex: () => -1 },
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "unknown" },
    "@/stores": { useSettingStore: () => settings, useStatusStore: () => status, useMusicStore: () => music, useDataStore: () => data },
    "@/utils/env": { isCapacitorAndroid: native, isElectron: false, isMac: false },
    "./AudioManager": { useAudioManager: () => audio },
    "./SongManager": { useSongManager: () => ({ clearPrefetch: noop, prefetchCover: noop, getAudioSource: () => { const d = deferred(); pending.push(d); return d.promise; } }) },
    "./LyricManager": { useLyricManager: () => ({ handleLyric: (s: any) => { music.lyricSongId = s.id; } }) },
    "@/core/automix/AutomixManager": { useAutomixManager: () => ({ resetNextAnalysisCache: noop, resetAutomixScheduling: noop }) },
    "@/utils/format": { getPlaySongData: () => data.playList[status.playIndex] || music.playSong, getPlayerInfoObj: () => ({ name: "fixture", artist: "fixture" }) },
    "@/utils/color": { getCoverColor: noop },
    "@/utils/helper": { handleSongQuality: () => undefined, sleep: () => { const d = deferred(); retryDelays.push(d); return d.promise; } },
    "@/core/audio-player/BaseAudioPlayer": { AudioErrorCode: { ABORTED: 1, DOM_ABORT: 20, SRC_NOT_SUPPORTED: 4 } },
    "@/utils/time": { calculateProgress: (time: number, duration: number) => time / duration },
    "@/utils/lastfmScrobbler": { default: { resume: () => listeningEvents.push("resume"), pause: () => listeningEvents.push("pause") } },
    "./MediaSessionManager": { mediaSessionManager: { updatePlaybackStatus: noop, updateMetadata: (song: any, source: string) => { metadataUpdates.push({ songId: song?.id, source }); return metadataCompletion; }, syncAndroidApiContext: () => contextCompletion, buildAndroidMetadata: () => ({ songId: music.playSong.id, title: music.playSong.name, artist: "fixture", album: "fixture", coverUrl: "", durationMs: 60000 }), updateState: noop } },
    "./PlayModeManager": { PlayModeManager: class {} },
    "./PlayerIpc": new Proxy({}, { get: () => noop }),
    "lodash-es": { throttle: (fn: any) => Object.assign(fn, { cancel: noop }) },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./PlayerController.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, {
    module, exports: module.exports, Error,
    Date: class extends Date { static now() { return now; } },
    require: (name: string) => imports[name] || {},
    console: { log: noop, warn: noop, error: noop },
    window: { document: {}, $message: { error: (s: string) => notices.push(s), warning: (s: string) => notices.push(s) } },
    setTimeout: () => 0, clearTimeout: noop,
  });
  return { controller: module.exports.usePlayerController(), status, music, audio, data, settings, pending, notices,
    listeningEvents, loadedSources, loadedOptions, metadataUpdates, emit: (name: string) => callbacks.get(name)?.(),
    retryDelays, emitError: (errorCode = 2) => callbacks.get("error")?.({ detail: { errorCode } }),
    advance: (ms: number) => { now += ms; },
    delayContext: (promise?: Promise<any>) => { contextCompletion = promise; },
    delayMetadata: (promise?: Promise<any>) => { metadataCompletion = promise; },
    delayLoad: (promise?: Promise<any>) => { loadCompletion = promise; },
    delayResume: (promise: Promise<any>) => { resumeCompletion = promise; } };
}

test("native track identity survives a reordered web queue", async () => {
  const f = fixture(true);
  f.data.playList = [song(3), song(2), song(1)];
  await f.controller.applyNativeTrackChanged(0, 2, undefined, "auto");
  assert.equal(f.music.playSong.id, 2);
  assert.equal(f.music.lyricSongId, 2);
  assert.equal(f.status.playIndex, 1);
});

test("an unknown native track cannot assign unrelated indexed lyrics", async () => {
  const f = fixture(true);
  f.data.playList = [song(1), song(2)];
  await f.controller.applyNativeTrackChanged(1, 999, undefined, "next");
  assert.equal(f.music.playSong.id, 1);
  assert.equal(f.music.lyricSongId, 1);
});

test("legacy native auto-next with an unknown positive ID cannot choose another song", async () => {
  const f = fixture(true);
  f.data.playList = [song(1), song(2)];
  f.status.playIndex = 0;
  await f.controller.applyNativeAutoNext(999);
  assert.equal(f.music.playSong.id, 1);
  assert.equal(f.music.lyricSongId, 1);
});

test("a terminal native error cannot start a second web retry loop or skip the song", async () => {
  const f = fixture(true);
  f.emitError();
  await new Promise<void>((resolve) => setImmediate(resolve));
  assert.equal(f.retryDelays.length, 0);
  assert.equal(f.pending.length, 0);
  assert.equal(f.music.playSong.id, 1);
  assert.equal(f.status.playLoading, false);
  assert.equal(f.status.playStatus, false);
});

for (const native of [false, true]) {
  test(`an unavailable source keeps the requested song paused instead of wrapping the queue (${native ? "native" : "web"})`, async () => {
    const f = fixture(native);
    f.data.playList = [song(1), song(2)];
    f.status.playIndex = 1;
    const request = f.controller.playSong({ song: f.data.playList[1], autoPlay: true });
    f.pending[0].resolve({ id: 2, url: undefined });
    await new Promise<void>((resolve) => setImmediate(resolve));
    assert.equal(f.retryDelays.length, 0);
    await request;
    assert.equal(f.music.playSong.id, 2);
    assert.equal(f.status.playIndex, 1);
    assert.equal(f.status.playStatus, false);
    assert.equal(f.status.playLoading, false);
    assert.equal(f.loadedSources.length, 0);
  });
}

for (const radio of [false, true]) {
  test(`exhausted web recovery stops the current ${radio ? "radio" : "song"} without advancing the queue`, async () => {
    const f = fixture();
    f.music.playSong = { ...song(1), type: radio ? "radio" : "song", ...(radio ? { dj: { id: 99 } } : {}) };
    f.data.playList = [f.music.playSong, { ...song(2), type: "song" }];
    f.status.playIndex = 0;
    for (let attempt = 0; attempt < 3; attempt++) {
      f.advance(1000);
      f.emitError();
      f.retryDelays[attempt].resolve(undefined);
      await new Promise<void>((resolve) => setImmediate(resolve));
      f.pending[attempt].resolve({ id: 1, url: "audio1" });
      await new Promise<void>((resolve) => setImmediate(resolve));
    }
    f.advance(1000);
    f.emitError();
    await new Promise<void>((resolve) => setImmediate(resolve));
    assert.equal(f.retryDelays.length, 3);
    assert.equal(f.pending.length, 3);
    assert.equal(f.music.playSong.id, 1);
    assert.equal(f.status.playIndex, 0);
    assert.equal(f.status.playStatus, false);
    assert.equal(f.status.playLoading, false);
});
}

test("stable web playback earns a new bounded recovery budget", async () => {
  const f = fixture();
  f.music.playSong = { ...song(1), type: "song" };
  f.data.playList = [f.music.playSong];
  f.status.playIndex = 0;
  for (let attempt = 0; attempt < 4; attempt++) {
    if (attempt === 3) {
      f.audio.currentTime = 1; f.emit("timeupdate");
      f.audio.currentTime = 11; f.emit("timeupdate");
    }
    f.advance(1000); f.emitError();
    assert.equal(f.retryDelays.length, attempt + 1);
    f.retryDelays[attempt].resolve(undefined);
    await new Promise<void>((resolve) => setImmediate(resolve));
    f.pending[attempt].resolve({ id: 1, url: "audio1" });
    await new Promise<void>((resolve) => setImmediate(resolve));
  }
  assert.equal(f.music.playSong.id, 1);
  assert.equal(f.status.playStatus, true);
});

for (const local of [false, true]) {
  test(`a permanent ${local ? "local file" : "unsupported format"} error preserves the selected song`, async () => {
    const f = fixture();
    if (local) f.music.playSong.path = "file:///missing-test.wav";
    f.emitError(local ? 2 : 4);
    await new Promise<void>((resolve) => setImmediate(resolve));
    assert.equal(f.retryDelays.length, 0);
    assert.equal(f.status.playStatus, false);
    assert.equal(f.music.playSong.id, 1);
  });
}

test("R11: actual controller buffering events suspend and restore Last.fm listening", () => {
  const f = fixture(); f.emit("waiting"); f.emit("playing");
  assert.deepEqual(f.listeningEvents, ["pause", "resume"]);
});

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

test("switching source updates the active source used by native queue synchronization", async () => {
  const f = fixture();
  const first = f.controller.playSong({ song: song(1), autoPlay: true });
  f.pending[0].resolve({ url: "official-url", source: "official", quality: "standard" });
  await first;
  const change = f.controller.switchAudioSource("netease");
  f.pending[1].resolve({ url: "third-party-url", source: "netease", quality: "lossless" });
  await change;
  assert.equal(f.audio.src, "third-party-url");
  assert.equal(f.controller.currentAudioSource?.url, "third-party-url");
  assert.equal(f.controller.currentAudioSource?.source, "netease");
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


test("rapid song selections load only the final target when older source responses finish late", async () => {
  const f = fixture();
  const first = f.controller.playSong({ song: song(2) });
  const middle = f.controller.playSong({ song: song(3) });
  const final = f.controller.playSong({ song: song(4) });
  f.pending[2].resolve({ url: "final-audio4" });
  await final;
  f.pending[0].resolve({ url: "stale-audio2" });
  f.pending[1].resolve({ url: "stale-audio3" });
  await Promise.all([first, middle]);
  assert.deepEqual(f.loadedSources, ["final-audio4"]);
  assert.equal(f.audio.src, "final-audio4");
  assert.equal(f.music.playSong.id, 4);
  assert.equal(f.music.lyricSongId, 4);
  assert.equal(f.status.playLoading, false);
});


test("native audio preserves pause and seek while API context synchronizes", async () => {
  const f = fixture(true), metadata = deferred();
  f.delayContext(metadata.promise);
  const job = f.controller.loadAndPlay("prepared-audio", true, 1000);
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.deepEqual(f.loadedSources, []);
  await f.controller.pause();
  f.controller.setSeek(9000);
  metadata.resolve(undefined);
  await job;
  assert.deepEqual(f.loadedSources, ["prepared-audio"]);
  assert.equal(f.audio.paused, true);
  assert.equal(f.audio.currentTime, 9);
});

test("an obsolete API-context completion cannot load an older selected song", async () => {
  const f = fixture(true), metadata = deferred();
  f.delayContext(metadata.promise);
  const first = f.controller.playSong({ song: song(2) });
  f.pending[0].resolve({ url: "old-native-audio" });
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.deepEqual(f.loadedSources, []);
  f.delayContext();
  const final = f.controller.playSong({ song: song(3) });
  f.pending[1].resolve({ url: "final-native-audio" });
  await final;
  metadata.resolve(undefined);
  await first;
  assert.deepEqual(f.loadedSources, ["final-native-audio"]);
  assert.equal(f.music.playSong.id, 3);
});

test("native cached audio carries identity in its load instead of waiting for a metadata reply", async () => {
  const f = fixture(true), metadata = deferred();
  f.delayMetadata(metadata.promise);
  const job = f.controller.loadAndPlay("cached-final-audio", true, 0);
  await new Promise<void>(resolve => setImmediate(resolve));
  try {
    assert.deepEqual([...f.loadedSources], ["cached-final-audio"]);
    assert.equal(f.loadedOptions[0].metadata.songId, 1);
  } finally {
    metadata.resolve(undefined);
    await job;
  }
});


test("a superseded native crossfade cannot stage another track's metadata after context sync", async () => {
  const f = fixture(true), context = deferred();
  f.delayContext(context.promise);
  const obsolete = f.controller.loadAndPlay("obsolete-crossfade", true, 0, { duration: 1 });
  await new Promise<void>(resolve => setImmediate(resolve));
  f.delayContext();
  const latest = f.controller.playSong({ song: song(2) });
  f.pending[0].resolve({ url: "latest-native-audio" });
  await latest;
  context.resolve(undefined);
  await obsolete;
  assert.deepEqual([...f.metadataUpdates], []);
  assert.deepEqual([...f.loadedSources], ["latest-native-audio"]);
});


test("a natural end at the last track with repeat off cannot restart the old first song", async () => {
  const f = fixture(true);
  f.data.playList = [song(1), song(2)];
  f.status.playIndex = 1;
  f.status.repeatMode = "off";
  f.music.playSong = song(2);
  const ended = f.controller.nextOrPrev("next", true, true);
  await new Promise<void>(resolve => setImmediate(resolve));
  try {
    assert.equal(f.pending.length, 0, "natural completion must not request another source");
    assert.equal(f.status.playIndex, 1);
    assert.equal(f.status.playStatus, false);
    assert.equal(f.status.playLoading, false);
    assert.deepEqual([...f.loadedSources], []);
  } finally {
    for (const request of f.pending) request.resolve({ url: "unexpected-first-audio" });
    await ended;
  }
});


test("automatic repeat-off completion does not wrap past a blocked last track", async () => {
  const f = fixture(true);
  f.data.playList = [song(1), { ...song(2), name: "DJ blocked track" }];
  f.settings.disableDjMode = true;
  f.status.playIndex = 0;
  f.status.repeatMode = "off";
  await f.controller.nextOrPrev("next", true, true);
  assert.equal(f.pending.length, 0);
  assert.equal(f.status.playIndex, 0);
  assert.equal(f.status.playLoading, false);
  assert.equal(f.status.playStatus, false);
});

test("manual next and enabled repeat modes retain their existing wrap or replay behavior", async () => {
  for (const [repeatMode, autoEnd, expectedIndex] of [["off", false, 0], ["list", true, 0], ["one", true, 1]] as const) {
    const f = fixture(true);
    f.data.playList = [song(1), song(2)];
    f.status.playIndex = 1;
    f.status.repeatMode = repeatMode;
    f.music.playSong = song(2);
    const job = f.controller.nextOrPrev("next", true, autoEnd);
    await new Promise<void>(resolve => setImmediate(resolve));
    try {
      assert.equal(f.pending.length, 1, `${repeatMode}/${autoEnd} must still prepare playback`);
      assert.equal(f.status.playIndex, expectedIndex);
    } finally {
      for (const request of f.pending) request.resolve({ url: "compatible-audio" });
      await job;
    }
    assert.deepEqual([...f.loadedSources], ["compatible-audio"]);
    assert.equal(f.music.playSong.id, expectedIndex + 1);
  }
});

test("non-native natural completion also stops at repeat-off list end", async () => {
  const f = fixture();
  f.data.playList = [song(1)];
  f.status.playIndex = 0;
  f.status.repeatMode = "off";
  await f.controller.nextOrPrev("next", true, true);
  assert.equal(f.pending.length, 0);
  assert.equal(f.status.playIndex, 0);
  assert.equal(f.status.playLoading, false);
  assert.equal(f.status.playStatus, false);
});

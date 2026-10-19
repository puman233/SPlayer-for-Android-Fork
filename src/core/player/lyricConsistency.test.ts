import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import * as lodash from "lodash-es";
import { DOMParser } from "@xmldom/xmldom";

const previousDomParser = (globalThis as any).DOMParser;
(globalThis as any).DOMParser = DOMParser;
test.after(() => {
  if (previousDomParser) (globalThis as any).DOMParser = previousDomParser;
  else delete (globalThis as any).DOMParser;
});

const require = createRequire(import.meta.url);
const payload = (translation = "translation one", roman = "roman one", timing = 400) => ({
  code: 200,
  lrc: { lyric: "[00:01.000]hello" },
  tlyric: { lyric: `[00:01.000]${translation}` },
  romalrc: { lyric: `[00:01.000]${roman}` },
  yrc: { lyric: `[1000,1000](1000,${timing},0)hello` },
});

// Real manager, scheduler, LRC/YRC parsers and post-processors. Only HTTP,
// persistence, settings and platform facilities are replaced at their seams.
function fixture() {
  let response = payload();
  let ttmlResponse: string | null | Promise<string | null> =
    '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><body><div><p itunes:key="L1" begin="00:00:01.000" end="00:00:03.000"><span begin="00:00:01.000" end="00:00:02.000">hello </span><span begin="00:00:02.000" end="00:00:03.000">world</span></p></div></body></tt>';
  const pending: Array<{ resolve: (value: any) => void; reject: (error: Error) => void }> = [];
  let delayed = false;
  let commits = 0;
  let clock = 0, timerId = 0;
  const timers = new Map<number, { at: number; fn: () => void }>();
  const music: any = {
    songLyric: { lrcData: [], yrcData: [] },
    setSongLyric(data: any) {
      music.songLyric = data;
      commits++;
    },
  };
  const status: any = { lyricLoading: true };
  const settings = {
    lyricPriority: "official",
    cacheEnabled: false,
    enableOnlineTTMLLyric: false,
    showWordLyrics: false,
  };
  const cache = new Map<string, any>();
  const boundaries: Record<string, any> = {
    "@/utils/requestDiagnostics": { requestFailureCategory: (error: unknown) => String(error) },
    "@/stores": {
      useMusicStore: () => music,
      useStatusStore: () => status,
      useSettingStore: () => settings,
    },
    "@/api/song": {
      songLyric: () =>
        delayed ? new Promise((resolve, reject) => pending.push({ resolve, reject })) : Promise.resolve(response),
      songLyricTTML: async () => ttmlResponse,
    },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@/core/resource/CacheManager": { useCacheManager: () => ({ isAvailable: () => false }) },
    "lodash-es": lodash,
  };
  const load = (filename: string): any => {
    const path = resolve(filename);
    if (cache.has(path)) return cache.get(path);
    const module = { exports: {} };
    cache.set(path, module.exports);
    runInNewContext(
      transpileModule(readFileSync(path, "utf8"), {
        compilerOptions: {
          module: ModuleKind.CommonJS,
          target: ScriptTarget.ES2022,
        },
      }).outputText,
      {
        module,
        exports: module.exports,
        console: { log() {}, warn() {}, error() {} },
        window: {},
        setTimeout: (fn: () => void, ms: number) => { const id = ++timerId; timers.set(id, { at: clock + ms, fn }); return id; },
        clearTimeout: (id: number) => timers.delete(id),
        require: (name: string) => {
          if (name in boundaries) return boundaries[name];
          if (name.startsWith("@/utils/lyric/") || name === "@/core/player/androidLyricScheduler")
            return load(name.replace("@/", "src/") + ".ts");
          if (name.startsWith(".")) return load(resolve(dirname(path), name + ".ts"));
          if (name === "@applemusic-like-lyrics/lyric") return require(name);
          return {};
        },
      },
    );
    return module.exports;
  };
  const manager = load("src/core/player/LyricManager.ts").useLyricManager();
  const run = (id = 1) => manager.handleLyric({ id, name: `song${id}`, type: "song" });
  return {
    manager,
    music,
    status,
    settings,
    pending,
    run,
    commits: () => commits,
    advance: (ms: number) => { clock += ms; for (const [id, t] of timers) if (t.at <= clock) { timers.delete(id); t.fn(); } },
    respondTtml: (value: string | null | Promise<string | null>) => {
      ttmlResponse = value;
    },
    respond: (value: any) => {
      response = value;
    },
    delay: () => {
      delayed = true;
    },
  };
}

test("auto mode shows ready official YRC while TTML is pending, then upgrades to TTML", async () => {
  const f = fixture();
  f.settings.lyricPriority = "auto";
  f.settings.enableOnlineTTMLLyric = true;
  let finish!: (value: string | null) => void;
  f.respondTtml(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const request = f.run();
  await new Promise<void>((resolve) => setImmediate(resolve));
  try {
    assert.equal(f.music.songLyric.lrcData[0]?.words[0].word, "hello");
    assert.equal(f.music.songLyric.yrcData[0]?.words.length, 1);
    assert.equal(f.status.lyricLoading, false);
    assert.equal(f.status.usingTTMLLyric, false);
  } finally {
    finish(
      '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><body><div><p itunes:key="L1" begin="00:00:01.000" end="00:00:03.000"><span begin="00:00:01.000" end="00:00:02.000">hello </span><span begin="00:00:02.000" end="00:00:03.000">world</span></p></div></body></tt>',
    );
    await request;
  }
  assert.equal(f.status.usingTTMLLyric, true);
  assert.equal(f.music.songLyric.yrcData[0].words.length, 2);
});

test("enabling TTML refreshes the current song and enables word rendering", async () => {
  const f = fixture();
  f.music.playSong = { id: 1, name: "current song", type: "song" };
  await f.run();
  assert.equal(f.status.usingTTMLLyric, false);
  await f.manager.setOnlineTTML(true);
  assert.equal(f.settings.enableOnlineTTMLLyric, true);
  assert.equal(f.settings.showWordLyrics, true);
  assert.equal(f.status.usingTTMLLyric, true);
  assert.equal(f.music.songLyric.yrcData[0].words.length, 2);
  assert.equal(f.music.songLyric.yrcData[0].words[1].startTime, 2000);
  await f.manager.setOnlineTTML(false);
  assert.equal(f.settings.lyricPriority, "auto");
  assert.equal(f.status.usingTTMLLyric, false);
});

test("auto mode shows plain LRC before an unavailable TTML response finishes", async () => {
  const f = fixture();
  f.settings.lyricPriority = "auto";
  f.settings.enableOnlineTTMLLyric = true;
  f.respond({ code: 200, lrc: { lyric: "[00:01.000]plain lyric" } });
  let finish!: (value: null) => void;
  f.respondTtml(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const request = f.run();
  await new Promise<void>((resolve) => setImmediate(resolve));
  try {
    assert.equal(f.music.songLyric.lrcData[0]?.words[0].word, "plain lyric");
    assert.equal(f.status.lyricLoading, false);
  } finally {
    finish(null);
    await request;
  }
  assert.equal(f.status.usingTTMLLyric, false);
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "plain lyric");
});

test("a pending old auto-mode TTML cannot replace the next song after partial lyrics", async () => {
  const f = fixture();
  f.settings.lyricPriority = "auto";
  f.settings.enableOnlineTTMLLyric = true;
  let finish!: (value: string | null) => void;
  f.respondTtml(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  const old = f.run(1);
  await new Promise<void>((resolve) => setImmediate(resolve));
  assert.equal(f.music.songLyric.lrcData[0]?.words[0].word, "hello");
  f.settings.lyricPriority = "official";
  f.respond({ code: 200, lrc: { lyric: "[00:01.000]next song" } });
  await f.run(2);
  finish(null);
  await old;
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "next song");
  assert.equal(f.status.usingTTMLLyric, false);
});

test("a failed optional TTML request does not erase ready official lyrics", async () => {
  const f = fixture();
  f.settings.lyricPriority = "auto";
  f.settings.enableOnlineTTMLLyric = true;
  let reject!: (error: Error) => void;
  f.respondTtml(
    new Promise((_, fail) => {
      reject = fail;
    }),
  );
  const request = f.run();
  await new Promise<void>((resolve) => setImmediate(resolve));
  assert.equal(f.music.songLyric.lrcData[0]?.words[0].word, "hello");
  reject(new Error("TTML transport failed"));
  await request;
  assert.equal(f.music.songLyric.lrcData[0]?.words[0].word, "hello");
  assert.equal(f.status.usingTTMLLyric, false);
});

test("an explicit TTML toggle ignores official lyrics prefetched under the old preference", async () => {
  const f = fixture();
  f.music.playSong = { id: 1, name: "current song", type: "song" };
  await f.manager.prefetchLyric(f.music.playSong);
  await f.manager.setOnlineTTML(true);
  assert.equal(f.status.usingTTMLLyric, true);
  assert.equal(f.music.songLyric.yrcData[0].words.length, 2);
});

test("missing TTML retains the real fallback instead of claiming TTML adoption", async () => {
  const f = fixture();
  f.music.playSong = { id: 1, name: "current song", type: "song" };
  f.respondTtml(null);
  await f.manager.setOnlineTTML(true);
  assert.equal(f.status.usingTTMLLyric, false);
  assert.equal(f.music.songLyric.lrcData.length, 1);
});

test("ready preferred TTML renders without waiting for a blocked fallback LRC request", async () => {
  const f = fixture();
  f.music.playSong = { id: 1, name: "current song", type: "song" };
  f.delay();
  let complete = false;
  const selected = f.manager.setOnlineTTML(true).then(() => {
    complete = true;
  });
  await new Promise<void>((resolve) => setImmediate(resolve));
  try {
    assert.equal(
      complete,
      true,
      "available preferred lyrics must not wait for a lower-priority HTTP response",
    );
    assert.equal(f.status.usingTTMLLyric, true);
    assert.equal(f.music.songLyric.yrcData[0].words[1].startTime, 2000);
    assert.equal(f.music.songLyric.lrcData[0].words[0].word, "hello world");
  } finally {
    f.pending.forEach((request) => request.resolve(payload()));
    await selected;
  }
});

test("a late official prefetch cannot be consumed after the user selects TTML", async () => {
  const f = fixture();
  f.music.playSong = { id: 1, name: "current song", type: "song" };
  f.delay();
  const old = f.manager.prefetchLyric(f.music.playSong);
  await new Promise<void>((resolve) => setImmediate(resolve));
  const selected = f.manager.setOnlineTTML(true);
  await new Promise<void>((resolve) => setImmediate(resolve));
  f.pending[1]?.resolve(payload());
  await selected;
  f.pending[0].resolve(payload());
  await old;
  const next = f.manager.handleLyric(f.music.playSong);
  await new Promise<void>((resolve) => setImmediate(resolve));
  f.pending[2]?.resolve(payload());
  await next;
  assert.equal(f.status.usingTTMLLyric, true);
  assert.equal(f.music.songLyric.yrcData[0].words.length, 2);
});

test("a translation-only HTTP lyric update reaches the final store", async () => {
  const f = fixture();
  await f.run();
  f.respond(payload("translation two"));
  await f.run();
  assert.equal(f.music.songLyric.lrcData[0].translatedLyric, "translation two");
  assert.equal(f.commits(), 2);
  assert.equal(f.status.lyricLoading, false);
});

test("identical parsed lyrics retain their store reference without another render commit", async () => {
  const f = fixture();
  await f.run();
  const previous = f.music.songLyric;
  await f.run();
  assert.equal(f.music.songLyric, previous);
  assert.equal(f.commits(), 1);
});

test("romanization-only changes reach the final store", async () => {
  const f = fixture();
  await f.run();
  f.respond(payload("translation one", "roman two"));
  await f.run();
  assert.equal(f.music.songLyric.lrcData[0].romanLyric, "roman two");
  assert.equal(f.commits(), 2);
});

test("word timing-only changes reach the final store", async () => {
  const f = fixture();
  await f.run();
  f.respond(payload("translation one", "roman one", 700));
  await f.run();
  assert.equal(f.music.songLyric.yrcData[0].words[0].endTime, 1700);
  assert.equal(f.commits(), 2);
});

test("line end time changes from a YRC response are not treated as equal", async () => {
  const f = fixture();
  await f.run();
  f.respond({ ...payload(), yrc: { lyric: "[1000,2000](1000,400,0)hello" } });
  await f.run();
  assert.equal(f.music.songLyric.yrcData[0].endTime, 3000);
  assert.equal(f.commits(), 2);
});

for (const field of ["isBG", "isDuet", "romanWord", "startTime"] as const) {
  test(`a previously rendered ${field} difference triggers a fresh store commit`, async () => {
    const f = fixture();
    await f.run();
    const line = f.music.songLyric.yrcData[0];
    if (field === "isBG" || field === "isDuet") line[field] = true;
    else if (field === "romanWord") line.words[0].romanWord = "old roman";
    else line.words[0].startTime = 1100;
    await f.run();
    assert.equal(f.commits(), 2);
    const current = f.music.songLyric.yrcData[0];
    if (field === "isBG" || field === "isDuet") assert.equal(current[field], false);
    else if (field === "romanWord") assert.ok(!current.words[0].romanWord);
    else assert.equal(current.words[0].startTime, 1000);
  });
}

test("empty and populated lyrics alternate without leaving previous rendered content", async () => {
  const f = fixture();
  await f.run();
  f.respond({ code: 200 } as any);
  await f.run();
  assert.equal(f.music.songLyric.lrcData.length, 0);
  assert.equal(f.music.songLyric.yrcData.length, 0);
  f.respond(payload());
  await f.run();
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "hello");
  assert.equal(f.commits(), 3);
});

test("old HTTP lyrics cannot replace a new song's rendered content", async () => {
  const f = fixture();
  f.delay();
  const old = f.run(1);
  const next = f.run(2);
  await new Promise<void>((resolve) => setImmediate(resolve));
  f.pending[1].resolve({ ...payload(), lrc: { lyric: "[00:01.000]new song" } });
  await next;
  f.pending[0].resolve(payload());
  await old;
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "new song");
  assert.equal(f.status.lyricLoading, false);
  assert.equal(f.commits(), 1);
});


test("a stalled lyric request leaves loading within its deadline and cannot commit after expiry", async () => {
  const f = fixture(); f.delay();
  let completed = false;
  const job = f.run().then(() => { completed = true; });
  await new Promise<void>(resolve => setImmediate(resolve));
  f.advance(12000);
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.status.lyricLoading, false);
  assert.equal(completed, true);
  f.pending[0].resolve(payload()); await job;
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.music.songLyric.lrcData.length, 0);
});

test("a superseded failed request cannot clear the next song loading state", async () => {
  const f = fixture(); f.delay();
  const old = f.run(1), next = f.run(2);
  await new Promise<void>(resolve => setImmediate(resolve));
  // Trigger a real parser failure after the older HTTP response returns.
  f.pending[0].resolve({ code: 200, lrc: { lyric: { malformed: true } } });
  await old;
  assert.equal(f.status.lyricLoading, true);
  f.pending[1].resolve(payload()); await next;
  assert.equal(f.status.lyricLoading, false);
});


test("an expired optional lyric upgrade preserves already displayed official words", async () => {
  const f = fixture();
  f.settings.lyricPriority = "auto"; f.settings.enableOnlineTTMLLyric = true;
  let finish!: (value: string | null) => void;
  f.respondTtml(new Promise(resolve => { finish = resolve; }));
  const job = f.run();
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "hello");
  f.advance(12000); await job;
  finish('<tt/>');
  await new Promise<void>(resolve => setImmediate(resolve));
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "hello");
  assert.equal(f.status.lyricLoading, false);
});


test("auto mode publishes ready TTML without waiting for a stalled official request", async () => {
  const f = fixture(); f.delay();
  f.settings.lyricPriority = "auto"; f.settings.enableOnlineTTMLLyric = true;
  const job = f.run();
  try {
    await new Promise<void>(resolve => setImmediate(resolve));
    assert.equal(f.status.lyricLoading, false);
    assert.equal(f.status.usingTTMLLyric, true);
    assert.equal(f.music.songLyric.yrcData[0].words[1].startTime, 2000);
  } finally { f.pending.forEach(p => p.resolve(payload())); await job; }
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import * as lodash from "lodash-es";

const require = createRequire(import.meta.url);
const payload = (translation = "translation one", roman = "roman one", timing = 400) => ({
  code: 200, lrc: { lyric: "[00:01.000]hello" }, tlyric: { lyric: `[00:01.000]${translation}` },
  romalrc: { lyric: `[00:01.000]${roman}` }, yrc: { lyric: `[1000,1000](1000,${timing},0)hello` },
});

// Real manager, scheduler, LRC/YRC parsers and post-processors. Only HTTP,
// persistence, settings and platform facilities are replaced at their seams.
function fixture() {
  let response = payload();
  const pending: Array<{ resolve: (value: any) => void }> = [];
  let delayed = false;
  let commits = 0;
  const music: any = { songLyric: { lrcData: [], yrcData: [] }, setSongLyric(data: any) { music.songLyric = data; commits++; } };
  const status: any = { lyricLoading: true };
  const settings = { lyricPriority: "official", cacheEnabled: false };
  const cache = new Map<string, any>();
  const boundaries: Record<string, any> = {
    "@/stores": { useMusicStore: () => music, useStatusStore: () => status, useSettingStore: () => settings },
    "@/api/song": { songLyric: () => delayed ? new Promise(resolve => pending.push({ resolve })) : Promise.resolve(response) },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@/core/resource/CacheManager": { useCacheManager: () => ({ isAvailable: () => false }) },
    "lodash-es": lodash,
  };
  const load = (filename: string): any => {
    const path = resolve(filename); if (cache.has(path)) return cache.get(path);
    const module = { exports: {} }; cache.set(path, module.exports);
    runInNewContext(transpileModule(readFileSync(path, "utf8"), { compilerOptions: {
      module: ModuleKind.CommonJS, target: ScriptTarget.ES2022,
    } }).outputText, {
      module, exports: module.exports, console: { log() {}, warn() {}, error() {} }, window: {},
      require: (name: string) => {
        if (name in boundaries) return boundaries[name];
        if (name.startsWith("@/utils/lyric/") || name === "@/core/player/androidLyricScheduler") return load(name.replace("@/", "src/") + ".ts");
        if (name.startsWith(".")) return load(resolve(dirname(path), name + ".ts"));
        if (name === "@applemusic-like-lyrics/lyric") return require(name);
        return {};
      },
    });
    return module.exports;
  };
  const manager = load("src/core/player/LyricManager.ts").useLyricManager();
  const run = (id = 1) => manager.handleLyric({ id, name: `song${id}`, type: "song" });
  return { manager, music, status, pending, run, commits: () => commits,
    respond: (value: any) => { response = value; }, delay: () => { delayed = true; } };
}

test("a translation-only HTTP lyric update reaches the final store", async () => {
  const f = fixture(); await f.run(); f.respond(payload("translation two")); await f.run();
  assert.equal(f.music.songLyric.lrcData[0].translatedLyric, "translation two");
  assert.equal(f.commits(), 2); assert.equal(f.status.lyricLoading, false);
});

test("identical parsed lyrics retain their store reference without another render commit", async () => {
  const f = fixture(); await f.run(); const previous = f.music.songLyric; await f.run();
  assert.equal(f.music.songLyric, previous); assert.equal(f.commits(), 1);
});

test("romanization-only changes reach the final store", async () => {
  const f = fixture(); await f.run(); f.respond(payload("translation one", "roman two")); await f.run();
  assert.equal(f.music.songLyric.lrcData[0].romanLyric, "roman two");
  assert.equal(f.commits(), 2);
});

test("word timing-only changes reach the final store", async () => {
  const f = fixture(); await f.run(); f.respond(payload("translation one", "roman one", 700)); await f.run();
  assert.equal(f.music.songLyric.yrcData[0].words[0].endTime, 1700); assert.equal(f.commits(), 2);
});

test("line end time changes from a YRC response are not treated as equal", async () => {
  const f = fixture(); await f.run();
  f.respond({ ...payload(), yrc: { lyric: "[1000,2000](1000,400,0)hello" } }); await f.run();
  assert.equal(f.music.songLyric.yrcData[0].endTime, 3000); assert.equal(f.commits(), 2);
});

for (const field of ["isBG", "isDuet", "romanWord", "startTime"] as const) {
  test(`a previously rendered ${field} difference triggers a fresh store commit`, async () => {
    const f = fixture(); await f.run();
    const line = f.music.songLyric.yrcData[0];
    if (field === "isBG" || field === "isDuet") line[field] = true;
    else if (field === "romanWord") line.words[0].romanWord = "old roman";
    else line.words[0].startTime = 1100;
    await f.run(); assert.equal(f.commits(), 2);
    const current = f.music.songLyric.yrcData[0];
    if (field === "isBG" || field === "isDuet") assert.equal(current[field], false);
    else if (field === "romanWord") assert.ok(!current.words[0].romanWord);
    else assert.equal(current.words[0].startTime, 1000);
  });
}

test("empty and populated lyrics alternate without leaving previous rendered content", async () => {
  const f = fixture(); await f.run(); f.respond({ code: 200 } as any); await f.run();
  assert.equal(f.music.songLyric.lrcData.length, 0); assert.equal(f.music.songLyric.yrcData.length, 0);
  f.respond(payload()); await f.run(); assert.equal(f.music.songLyric.lrcData[0].words[0].word, "hello");
  assert.equal(f.commits(), 3);
});

test("old HTTP lyrics cannot replace a new song's rendered content", async () => {
  const f = fixture(); f.delay(); const old = f.run(1); const next = f.run(2);
  await new Promise<void>(resolve => setImmediate(resolve));
  f.pending[1].resolve({ ...payload(), lrc: { lyric: "[00:01.000]new song" } }); await next;
  f.pending[0].resolve(payload()); await old;
  assert.equal(f.music.songLyric.lrcData[0].words[0].word, "new song");
  assert.equal(f.status.lyricLoading, false); assert.equal(f.commits(), 1);
});

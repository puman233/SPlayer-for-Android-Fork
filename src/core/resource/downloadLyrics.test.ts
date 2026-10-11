import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import * as parser from "../../utils/lyric/lyricParser";
import * as ass from "../../utils/assGenerator";
import * as lyric from "@applemusic-like-lyrics/lyric";
import { requestFailureCategory } from "../../utils/requestDiagnostics";

const tick = () => new Promise<void>((resolve) => setImmediate(resolve));
const lrc = "[00:01.000]合成歌词\n[00:02.000]第二行";

function load(file: string, imports: Record<string, unknown>, globals: Record<string, unknown> = {}) {
  const module = { exports: {} as any };
  const source = readFileSync(new URL(file, import.meta.url), "utf8");
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports,
    require: (name: string) => {
      if (!(name in imports)) throw new Error(`Unconfigured adapter: ${name}`);
      return imports[name];
    },
    console, ...globals,
  });
  return module.exports;
}

// Exercise the actual strategy and lyric processor; replace only platform/storage/HTTP adapters.
function fixture() {
  const settings: Record<string, any> = {
    downloadPath: "Music", androidDownloadDirectoryUri: "content://fixture/tree/music",
    downloadMeta: false, downloadLyric: true, folderStrategy: "none", downloadThreadCount: 1,
    downloadLyricEncoding: "utf-8", downloadMakeYrc: false, downloadSaveAsAss: false,
  };
  const songs: any[] = [];
  const files = new Map<string, Buffer>();
  const requests: any[] = [];
  const messages: string[] = [];
  let release!: (result: any) => void;
  const processor = load("./LyricProcessor.ts", {
    "@/utils/lyric/lyricParser": parser, "@/utils/assGenerator": ass,
    "@applemusic-like-lyrics/lyric": lyric,
    "@/utils/opencc": { getConverter: async () => (text: string) => text },
  });
  const native = {
    releaseDownloadTask: async () => {},
    verifyDownloadedFile: async () => ({ valid: true }),
    addListener: async () => ({ remove: async () => {} }), resetDownloads: async () => {},
    downloadFile: (options: any) => {
      requests.push(options);
      return new Promise((resolve) => { release = resolve; });
    },
    cancelDownload: async () => ({ status: "cancelling" }),
    writeTextFile: async (options: any) => {
      const path = `${options.directoryUri}/${options.subPath || ""}/${options.fileName}`;
      files.set(path, Buffer.from(options.content, "utf8"));
      return { status: "success", path };
    },
  };
  const data = {
    downloadingSongs: songs,
    addDownloadingSong: (song: any, quality: string) => songs.push({ song, quality, status: "waiting" }),
    updateDownloadStatus: (id: number, status: string) => { const item = songs.find((s) => s.song.id === id); if (item) item.status = status; },
    updateDownloadProgress() {},
    markDownloadFailed: (id: number) => data.updateDownloadStatus(id, "failed"),
    saveDownloadedAudio: async (id: number, audio: any, status: string) => {
      const item = songs.find((s) => s.song.id === id);
      if (item) { item.audio = structuredClone(audio); item.status = status; }
    },
    removeDownloadingSong: (id: number) => { const i = songs.findIndex((s) => s.song.id === id); if (i >= 0) songs.splice(i, 1); },
  };
  const api = {
    songDownloadUrl: async () => ({ code: 200, data: { url: "http://fixture/audio", type: "mp3" } }),
    songLyric: async () => ({ lrc: { lyric: lrc } }),
    songLyricTTML: async () => "",
  };
  const { downloadManager: manager } = load("./DownloadManager.ts", {
    "@/utils/requestDiagnostics": { requestFailureCategory },
    "@/stores": { useSettingStore: () => settings, useDataStore: () => data },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@/api/song": api, "@/api/album": {}, "@/api/qqmusic": {},
    "@/utils/meta": { songLevelData: { standard: { level: "standard" } } },
    "@/utils/format": { getPlayerInfoObj: (song: any) => ({ name: song.name, artist: "歌手 A", album: "专辑 B" }) },
    "@/plugins/androidDownload": { AndroidDownload: native },
    "file-saver": {}, "lodash-es": { cloneDeep: structuredClone }, "./LyricProcessor": processor,
  }, {
    crypto: globalThis.crypto,
    console: { log() {}, error() {} },
    window: { $message: { success: (s: string) => messages.push(s), warning: (s: string) => messages.push(s), error: (s: string) => messages.push(s) } },
  });
  return { manager, settings, songs, files, requests, native, api, messages, data, processor: processor.LyricProcessor,
    add: async () => { await manager.addDownload({ id: 1, name: "歌曲", album: "专辑", artists: "歌手" }, "standard"); await tick(); },
    finish: async (status = "success") => { release({ status, taskId: requests.at(-1).taskId, path: "content://fixture/document/audio", bytes: 4, sha256: "0".repeat(64) }); await tick(); },
  };
}

test("Android basic lyrics are saved as LRC bytes even with metadata disabled", async () => {
  const f = fixture();
  await f.add();
  await f.finish();
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), lrc);
  assert.equal(f.songs.length, 0);
});

test("audio and LRC use the task's original artist/album directory after settings change", async () => {
  const f = fixture();
  f.settings.folderStrategy = "artist-album";
  await f.add();
  assert.equal(f.requests[0].subPath, "歌手 A/专辑 B");
  f.settings.downloadPath = "New Music";
  f.settings.androidDownloadDirectoryUri = "content://fixture/tree/new";
  f.settings.folderStrategy = "none";
  await f.finish();
  assert.equal(f.files.get("content://fixture/tree/music/歌手 A/专辑 B/歌曲.lrc")?.toString("utf8"), lrc);
});

test("lyric HTTP failure does not prevent the audio request and reports partial success", async () => {
  const f = fixture();
  f.api.songLyric = async () => { throw new Error("fixture HTTP 500"); };
  await f.add();
  assert.equal(f.requests.length, 1);
  await f.finish();
  assert.equal(f.songs[0]?.status, "partial");
  assert.equal(f.files.size, 0);
  assert.ok(f.messages.some((message) => message.includes("音频已保存")));
});

test("SAF lyric failure retains an audio-success partial result rather than download failure", async () => {
  const f = fixture();
  f.native.writeTextFile = async () => { throw new Error("fixture permission revoked"); };
  await f.add();
  await f.finish();
  assert.equal(f.songs[0]?.status, "partial");
  assert.ok(f.messages.some((message) => message.includes("音频已保存")));
});

test("partial retry supplements lyrics without redownloading audio or moving to a changed directory", async () => {
  const f = fixture();
  f.api.songLyric = async () => { throw new Error("fixture timeout"); };
  await f.add();
  await f.finish();
  f.settings.androidDownloadDirectoryUri = "content://fixture/tree/new";
  f.api.songLyric = async () => ({ lrc: { lyric: lrc } });
  f.manager.retryDownload(1);
  await tick();
  assert.equal(f.requests.length, 1, "verified audio must not be transferred again");
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), lrc);
  assert.equal(f.songs.length, 0);
});

for (const failure of ["HTTP 404", "HTTP 500", "timeout", "network disconnected"]) {
  test(`audio completes despite lyric ${failure}`, async () => {
    const f = fixture();
    f.api.songLyric = async () => { throw new Error(`fixture ${failure}`); };
    await f.add();
    assert.equal(f.requests.length, 1);
    await f.finish();
    assert.equal(f.songs[0]?.status, "partial");
  });
}

test("disabled lyrics perform no lyric requests or sidecar writes", async () => {
  const f = fixture();
  f.settings.downloadLyric = false;
  f.settings.downloadMakeYrc = true;
  f.api.songLyric = async () => { throw new Error("must not request"); };
  await f.add();
  await f.finish();
  assert.equal(f.files.size, 0);
  assert.equal(f.songs.length, 0);
});

test("empty lyric response does not create an empty LRC", async () => {
  const f = fixture();
  f.api.songLyric = async () => ({ lrc: { lyric: "" } });
  await f.add();
  await f.finish();
  assert.equal(f.files.size, 0);
  assert.equal(f.songs.length, 0);
});

test("failed TTML lookup still saves valid basic LRC", async () => {
  const f = fixture();
  f.settings.downloadMakeYrc = true;
  f.api.songLyricTTML = async () => { throw new Error("fixture HTTP 404"); };
  await f.add();
  await f.finish();
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), lrc);
  assert.equal(f.songs.length, 0);
});

test("cancelled late lyric response cannot download audio or publish lyrics", async () => {
  const f = fixture();
  let resolve!: (response: any) => void;
  f.api.songLyric = () => new Promise((done) => { resolve = done; });
  await f.add();
  f.manager.removeDownload(1);
  resolve({ lrc: { lyric: lrc } });
  await tick();
  assert.equal(f.requests.length, 0);
  assert.equal(f.files.size, 0);
  assert.equal(f.messages.length, 0);
});

test("a changed completed audio receipt prevents sidecar writes and never overwrites the audio", async () => {
  const f = fixture();
  f.api.songLyric = async () => { throw new Error("fixture failure"); };
  await f.add(); await f.finish();
  f.native.verifyDownloadedFile = async () => ({ valid: false });
  f.manager.retryDownload(1);
  await tick();
  assert.equal(f.requests.length, 1);
  assert.equal(f.files.size, 0);
  assert.equal(f.songs[0].status, "failed");
});

test("non UTF-8 TTML declares the encoding actually selected for the native writer", async () => {
  const f = fixture();
  const result = await f.processor.generateVerbatimContent("<?xml version='1.0' encoding='UTF-8'?><tt>lyrics</tt>", "", null, { downloadLyricEncoding: "utf-16" });
  assert.match(result.content, /encoding='utf-16'/);
  const withoutDeclaration = await f.processor.generateVerbatimContent("<tt>lyrics</tt>", "", null, { downloadLyricEncoding: "gbk" });
  assert.match(withoutDeclaration.content, /^<\?xml.*encoding="gbk"/);
  const utf8 = await f.processor.generateVerbatimContent("<?xml version='1.0' encoding='GBK'?><tt>lyrics</tt>", "", null, { downloadLyricEncoding: "utf-8" });
  assert.match(utf8.content, /encoding='utf-8'/);
});

test("translation and romaji remain associated with basic LRC timestamps", async () => {
  const f = fixture();
  f.settings.downloadLyricTranslation = true; f.settings.downloadLyricRomaji = true;
  f.api.songLyric = async () => ({ lrc: { lyric: "[00:01.000]原文" }, tlyric: { lyric: "[00:01.000]translation" }, romalrc: { lyric: "[00:01.000]romaji" } });
  await f.add(); await f.finish();
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), "[00:01.000]原文\n[00:01.000]translation\n[00:01.000]romaji");
});

test("YRC produces real TTML and ASS while keeping basic LRC", async () => {
  const f = fixture();
  f.settings.downloadMakeYrc = true; f.settings.downloadSaveAsAss = true;
  f.api.songLyric = async () => ({ lrc: { lyric: lrc }, yrc: { lyric: "[1000,1000](1000,500,0)合(1500,500,0)成" } });
  await f.add(); await f.finish();
  assert.match(f.files.get("content://fixture/tree/music//歌曲.ttml")?.toString("utf8") || "", /<tt[^>]*xmlns=/);
  assert.match(f.files.get("content://fixture/tree/music//歌曲.ass")?.toString("utf8") || "", /\[Script Info\]/);
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), lrc);
});

test("receipt persistence failure does not mislabel verified audio as an audio-download failure", async () => {
  const f = fixture();
  const save = f.data.saveDownloadedAudio;
  f.data.saveDownloadedAudio = async (...args: Parameters<typeof save>) => {
    await save(...args);
    throw new Error("fixture database write failure");
  };
  await f.add(); await f.finish();
  assert.equal(f.songs[0]?.status, "partial");
  assert.ok(f.messages.some((message) => message.includes("音频已保存")));
});

test("a lyric parser exception still saves verified audio and retains a partial task", async () => {
  const f = fixture();
  f.processor.processBasic = async () => { throw new Error("fixture parse failure"); };
  await f.add(); await f.finish();
  assert.equal(f.requests.length, 1);
  assert.equal(f.songs[0]?.status, "partial");
  assert.equal(f.songs[0]?.audio?.path, "content://fixture/document/audio");
});

test("a late sidecar completion after removal cannot remove a re-added song or start more old writes", async () => {
  const f = fixture();
  f.settings.downloadMakeYrc = true; f.settings.downloadSaveAsAss = true;
  f.api.songLyric = async () => ({ lrc: { lyric: lrc }, yrc: { lyric: "[1000,1000](1000,1000,0)合成" } });
  let settle!: (result: any) => void;
  const writes: any[] = [];
  f.native.writeTextFile = (options: any) => {
    writes.push(options);
    return new Promise((done) => { settle = done; });
  };
  await f.add(); await f.finish();
  assert.equal(writes.length, 1);
  const oldId = writes[0].taskId;
  f.manager.removeDownload(1);
  await f.add();
  settle({ status: "success", path: "content://fixture/document/old-lrc" });
  await tick(); await tick();
  assert.equal(writes.length, 1);
  assert.equal(f.songs.length, 1);
  assert.notEqual(f.requests.at(-1)?.taskId, oldId);
  assert.equal(f.messages.length, 0);
});

test("retry pressed while partial receipt persistence is settling starts once after the old task releases", async () => {
  const f = fixture();
  f.api.songLyric = async () => { throw new Error("fixture lyric failure"); };
  let releaseReceipt!: () => void;
  const save = f.data.saveDownloadedAudio;
  f.data.saveDownloadedAudio = async (id, audio, status) => {
    await save(id, audio, status);
    if (status === "partial") await new Promise<void>((resolve) => { releaseReceipt = resolve; });
  };
  await f.add(); await f.finish();
  assert.equal(f.songs[0].status, "partial");
  f.api.songLyric = async () => ({ lrc: { lyric: lrc } });
  f.manager.retryDownload(1); f.manager.retryDownload(1);
  assert.equal(f.requests.length, 1);
  releaseReceipt(); await tick(); await tick();
  assert.equal(f.requests.length, 1);
  assert.equal(f.files.get("content://fixture/tree/music//歌曲.lrc")?.toString("utf8"), lrc);
  assert.equal(f.songs.length, 0);
});

test("removing a settling partial task discards its queued retry and cannot warn or alter a re-added task", async () => {
  const f = fixture();
  f.api.songLyric = async () => { throw new Error("fixture lyric failure"); };
  let releaseReceipt!: () => void;
  const save = f.data.saveDownloadedAudio;
  f.data.saveDownloadedAudio = async (id, audio, status) => {
    await save(id, audio, status);
    if (status === "partial") await new Promise<void>((resolve) => { releaseReceipt = resolve; });
  };
  await f.add(); await f.finish();
  f.manager.retryDownload(1); f.manager.removeDownload(1);
  f.api.songLyric = async () => ({ lrc: { lyric: lrc } });
  await f.add();
  releaseReceipt(); await tick(); await tick();
  assert.equal(f.requests.length, 2);
  assert.equal(f.songs[0].status, "downloading");
  assert.equal(f.messages.length, 0);
  await f.finish();
  assert.equal(f.songs.length, 0);
  assert.equal(f.files.size, 1);
});

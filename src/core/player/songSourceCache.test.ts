import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

function fixture(
  unlockSongUrl?: (...args: any[]) => Promise<any>,
  songQuality?: (...args: any[]) => Promise<any>,
) {
  const settings = { songLevel: "lossless", disableAiAudio: false, songUnlockServer: [] };
  const storage = new Map<string, string>();
  const account = { token: "test-account-1" };
  const noop = () => {};
  const imports: Record<string, any> = {
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "unknown" },
    "@/api/song": {
      unlockSongUrl,
      songQuality,
      songUrl: async (_id: number, level: string) => ({
        data: [
          {
            url: `https://audio.test/${level}.mp3`,
            level,
            md5: level,
          },
        ],
      }),
    },
    "@/stores": {
      useSettingStore: () => settings,
      useDataStore: () => ({ getAccountToken: () => account.token }),
    },
    "@/types/main": { QualityType: { HQ: "HQ", SQ: "SQ" } },
    "@/utils/env": { isCapacitorAndroid: true, isElectron: false },
    "@/utils/meta": { AI_AUDIO_LEVELS: ["jymaster", "sky", "jyeffect"] },
    "@/utils/helper": {
      handleSongQuality: (data: any) => (data.level === "lossless" ? "SQ" : "HQ"),
    },
    "@/plugins/androidNativePlayback": {
      AndroidNativePlayback: { isPromotedAudioReady: async () => ({ ready: true }) },
    },
  };
  const module = { exports: {} as any };
  runInNewContext(
    transpileModule(readFileSync(new URL("./SongManager.ts", import.meta.url), "utf8"), {
      compilerOptions: { module: ModuleKind.CommonJS },
    }).outputText,
    {
      module,
      exports: module.exports,
      require: (name: string) => imports[name] || {},
      console: { log: noop, warn: noop, error: noop },
      window: { setTimeout, clearTimeout },
      localStorage: {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => storage.set(key, value),
      },
    },
  );
  return {
    manager: module.exports.useSongManager(),
    settings,
    api: imports["@/api/song"],
    account,
  };
}

test("a new quality URL is not replaced by the previous quality's promoted audio", async () => {
  const { manager, settings } = fixture();
  await manager.getOnlineUrl(186016);
  settings.songLevel = "exhigh";
  const result = await manager.getOnlineUrl(186016);
  assert.deepEqual(
    { url: result.url, quality: result.quality },
    {
      url: "https://audio.test/exhigh.mp3",
      quality: "HQ",
    },
  );
});

test("Auto unlock can still reuse promoted audio after the quality URL has been refreshed", async () => {
  const { manager, settings } = fixture();
  await manager.getOnlineUrl(186016);
  settings.songLevel = "exhigh";
  await manager.getOnlineUrl(186016);
  const cached = await manager.getUnlockSongUrl({
    id: 186016,
    name: "cached song",
    artists: "artist",
  });
  assert.equal(cached.url, "https://audio.test/exhigh.mp3");
});

test("a successful preferred unlock source is not held by an unfinished lower-priority request", async () => {
  const first = deferred<any>(),
    lower = deferred<any>();
  const calls: string[] = [];
  const { manager, settings } = fixture(async (_id, _keyword, server) => {
    calls.push(server);
    return server === "netease" ? first.promise : lower.promise;
  });
  settings.songUnlockServer.push(
    ...([
      { key: "netease", enabled: true },
      { key: "kuwo", enabled: true },
    ] as any),
  );
  let settled = false;
  const task = manager
    .getUnlockSongUrl({ id: 729001, name: "source timing", artists: "artist" })
    .then((value: any) => {
      settled = true;
      return value;
    });
  try {
    await new Promise(setImmediate);
    assert.deepEqual(calls, ["netease", "kuwo"]);
    first.resolve({ code: 200, url: "https://audio.test/preferred.mp3" });
    await new Promise(setImmediate);
    assert.equal(
      settled,
      true,
      "valid preferred source must return without waiting for lower-priority request",
    );
    assert.equal((await task).source, "netease");
  } finally {
    first.resolve({ code: 404 });
    lower.resolve({ code: 404 });
    await task;
  }
});

test("normal lossless source can resolve without a separate quality-metadata round trip", async () => {
  const quality = deferred<any>();
  const { manager } = fixture(undefined, () => quality.promise);
  let settled = false;
  const task = manager.getOnlineUrl(729002).then((value: any) => {
    settled = true;
    return value;
  });
  try {
    await new Promise(setImmediate);
    assert.equal(
      settled,
      true,
      "a normal playable URL must not wait for unrelated quality metadata",
    );
    assert.equal((await task).url, "https://audio.test/lossless.mp3");
  } finally {
    quality.resolve({ data: { sq: { br: 1000 } } });
    await task;
  }
});

test("normal quality fallback still tries exhigh when lossless has no playable URL", async () => {
  const { manager, api } = fixture();
  const calls: string[] = [];
  api.songUrl = async (_id: number, level: string) => {
    calls.push(level);
    return {
      data: [{ url: level === "exhigh" ? "https://audio.test/fallback.mp3" : null, level }],
    };
  };
  assert.equal((await manager.getOnlineUrl(729003)).url, "https://audio.test/fallback.mp3");
  assert.deepEqual(calls, ["lossless", "exhigh"]);
});
test("a fast lower-priority result does not override a pending preferred source", async () => {
  const first = deferred<any>();
  const { manager, settings } = fixture(async (_id, _keyword, server) =>
    server === "netease" ? first.promise : { code: 200, url: "https://audio.test/lower.mp3" },
  );
  settings.songUnlockServer.push(
    ...([
      { key: "netease", enabled: true },
      { key: "kuwo", enabled: true },
    ] as any),
  );
  let settled = false;
  const task = manager
    .getUnlockSongUrl({ id: 729004, name: "priority", artists: "artist" })
    .then((value: any) => {
      settled = true;
      return value;
    });
  try {
    await new Promise(setImmediate);
    assert.equal(settled, false);
  } finally {
    first.resolve({ code: 200, url: "https://audio.test/preferred.mp3" });
  }
  assert.equal((await task).source, "netease");
});
test("Dolby compatibility check and fallback remain active", async () => {
  let queries = 0;
  const { manager, settings, api } = fixture(undefined, async () => {
    queries++;
    return { data: { sq: { br: 1000 } } };
  });
  settings.songLevel = "dolby";
  const requested: string[] = [];
  api.songUrl = async (_id: number, level: string) => {
    requested.push(level);
    return { data: [{ url: "https://audio.test/dolby-fallback.mp3", level }] };
  };
  await manager.getOnlineUrl(729005);
  assert.equal(queries, 1);
  assert.deepEqual(requested, ["lossless"]);
});

test("unsupported AI quality still falls back using metadata", async () => {
  let queries = 0;
  const { manager, settings, api } = fixture(undefined, async () => {
    queries++;
    return { data: { hr: { br: 2000 } } };
  });
  settings.songLevel = "jymaster";
  const levels: string[] = [];
  api.songUrl = async (_id: number, level: string) => {
    levels.push(level);
    return { data: [{ url: "https://audio.test/hires.mp3", level }] };
  };
  await manager.getOnlineUrl(729006);
  assert.equal(queries, 1);
  assert.deepEqual(levels, ["hires"]);
});

test("concurrent identical official source requests share one pending network operation", async () => {
  const reply = deferred<any>();
  const { manager, api } = fixture();
  let calls = 0;
  api.songUrl = async () => {
    calls++;
    return reply.promise;
  };
  const first = manager.getOnlineUrl(729007),
    second = manager.getOnlineUrl(729007);
  try {
    assert.equal(calls, 1, "preload and play must not duplicate the same pending URL request");
  } finally {
    reply.resolve({ data: [{ url: "https://audio.test/shared.mp3", level: "lossless" }] });
    await Promise.all([first, second]);
  }
});

test("pending official requests of different qualities remain separate and completed URLs are not memoized", async () => {
  const reply = deferred<void>();
  const { manager, api, settings } = fixture();
  const levels: string[] = [];
  api.songUrl = async (_id: number, level: string) => {
    levels.push(level);
    await reply.promise;
    return { data: [{ url: "https://audio.test/" + level + ".mp3", level }] };
  };
  const first = manager.getOnlineUrl(729008);
  settings.songLevel = "exhigh";
  const second = manager.getOnlineUrl(729008);
  reply.resolve();
  const [a, b] = await Promise.all([first, second]);
  assert.equal(a.url, "https://audio.test/lossless.mp3");
  assert.equal(b.url, "https://audio.test/exhigh.mp3");
  await manager.getOnlineUrl(729008);
  assert.deepEqual(levels, ["lossless", "exhigh", "exhigh"]);
});

test("different account sessions do not share a pending official request", async () => {
  const reply = deferred<void>();
  const { manager, api, account } = fixture();
  let calls = 0;
  api.songUrl = async () => {
    calls++;
    await reply.promise;
    return { data: [{ url: "https://audio.test/session.mp3", level: "lossless" }] };
  };
  const first = manager.getOnlineUrl(729009);
  account.token = "test-account-2";
  const second = manager.getOnlineUrl(729009);
  try {
    assert.equal(calls, 2);
  } finally {
    reply.resolve();
    await Promise.all([first, second]);
  }
});

test("concurrent identical fallback requests share the pending provider request", async () => {
  const reply = deferred<any>();
  let calls = 0;
  const { manager, settings } = fixture(async () => {
    calls++;
    return reply.promise;
  });
  settings.songUnlockServer.push(...([{ key: "netease", enabled: true }] as any));
  const song = { id: 729010, name: "fallback dedup", artists: "artist" };
  const first = manager.getUnlockSongUrl(song),
    second = manager.getUnlockSongUrl(song);
  try {
    await new Promise(setImmediate);
    assert.equal(calls, 1);
  } finally {
    reply.resolve({ code: 200, url: "https://audio.test/shared-fallback.mp3" });
    await Promise.all([first, second]);
  }
});

test("explicit fallback providers are not merged with each other", async () => {
  const reply = deferred<any>();
  const calls: string[] = [];
  const { manager } = fixture(async (_id, _keyword, server) => {
    calls.push(server);
    await reply.promise;
    return { code: 200, url: "https://audio.test/" + server + ".mp3" };
  });
  const song = { id: 729011, name: "source isolation", artists: "artist" };
  const first = manager.getUnlockSongUrl(song, "netease"),
    second = manager.getUnlockSongUrl(song, "kuwo");
  try {
    assert.deepEqual(calls, ["netease", "kuwo"]);
  } finally {
    reply.resolve({});
    await Promise.all([first, second]);
  }
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import { createPinia, defineStore } from "pinia";
import * as vue from "vue";
import * as lodash from "lodash-es";

const deferred = () => {
  let resolve!: (value: any) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const flush = () => new Promise<void>((resolve) => setImmediate(resolve));
function fixture() {
  const databases = new Map<string, Map<string, any>>();
  const storage = new Map<string, string>();
  const cookies = new Map<string, string>([["MUSIC_U", "fixture-A"]]);
  const requests: { name: string; request: ReturnType<typeof deferred> }[] = [];
  const notices: string[] = [];
  const timers: (() => void)[] = [];
  const modules = new Map<string, any>();
  const reads = new Map<string, ReturnType<typeof deferred>>();
  const music = { dailySongsData: { timestamp: 1, list: [{ id: 9 }] }, personalFM: { playIndex: 0, list: [{ id: 10 }] } };
  const status = { personalFmMode: true };
  const api = new Proxy({}, { get: (_, name: string) => () => { const request = deferred(); requests.push({ name, request }); return request.promise; } });
  const imports: Record<string, any> = {
    pinia: { defineStore }, vue, "lodash-es": lodash,
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "network" },
    "@/utils/format": { formatSongsList: (v: any) => v, formatCoverList: (v: any) => v, formatArtistsList: (v: any) => v },
    "@/utils/time": { isBeforeSixAM: () => false }, "./time": { isBeforeSixAM: () => false },
    "@/utils/env": { isElectron: false }, "./env": { isElectron: false },
    "@/router": { default: { push: () => {} } }, "@/core/player/PlayerController": { usePlayerController: () => ({ applySongLikeState() {} }) },
    "./cookie": { getCookie: (key: string) => cookies.get(key), removeCookie: (key: string) => cookies.delete(key), setCookies: () => {} },
    "./music": { useMusicStore: () => music }, "@/stores/music": { useMusicStore: () => music },
    "./status": { useStatusStore: () => status },
    localforage: { default: { createInstance: ({ name }: any) => {
      if (!databases.has(name)) databases.set(name, new Map());
      const db = databases.get(name)!;
      return { keys: async () => [...db.keys()], getItem: async (key: string) => reads.get(key)?.promise ?? db.get(key) ?? null,
        setItem: async (key: string, value: any) => { db.set(key, structuredClone(value)); return value; },
        clear: () => { throw new Error("Whole database deletion is forbidden"); } };
    } } },
  };
  const pinia = createPinia();
  const localStorage = { getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key) };
  const document = { get cookie() { return [...cookies].map(([k, v]) => `${k}=${v}`).join("; "); },
    set cookie(value: string) { const [key, val] = value.split(";")[0].split("="); cookies.set(key, val); } };
  const load = (name: string): any => {
    if (modules.has(name)) return modules.get(name).exports;
    if (name === "@/stores") return { useDataStore: () => store, useMusicStore: () => music, useLocalStore: () => ({}), useStatusStore: () => status };
    if (name.startsWith("@/api/")) return api;
    if (imports[name]) return imports[name];
    const path = name === "@/utils/auth" ? "../utils/auth.ts" : name === "@/stores/data" ? "./data.ts" : name === "@/core/player/SongManager" ? "../core/player/SongManager.ts" : null;
    if (!path) return {};
    const module = { exports: {} as any }; modules.set(name, module);
    runInNewContext(transpileModule(readFileSync(new URL(path, import.meta.url), "utf8"), {
      compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
    }).outputText, { module, exports: module.exports, require: load, localStorage, document, console,
      sessionStorage: { clear: () => { throw new Error("Do not clear unrelated session data"); } },
      window: { $message: new Proxy({}, { get: () => (s: string) => notices.push(s) }), location: { reload() {} } },
      setTimeout: (fn: () => void) => { timers.push(fn); return timers.length; }, clearTimeout() {} });
    return module.exports;
  };
  const store = load("@/stores/data").useDataStore(pinia);
  store.userLoginStatus = true; store.userData = { userId: 1, name: "fixture A" };
  return { store, auth: load("@/utils/auth"), load, databases, storage, cookies, requests, music, status, notices, timers, reads };
}

test("logout clears private memory but preserves local music, downloads and saved accounts", async () => {
  const f = fixture();
  f.store.cloudPlayList = [{ id: 7 }]; f.store.likeSongsList.data = [{ id: 8 }];
  f.store.localPlayList = [{ id: 4 }]; f.store.downloadingSongs = [{ song: { id: 5 } }];
  f.store.userList = [{ userId: 2, name: "fixture B" }];
  await f.store.clearUserData();
  assert.equal(f.store.cloudPlayList.length, 0);
  assert.equal(f.store.likeSongsList.data.length, 0);
  assert.equal(f.music.dailySongsData.list.length, 0);
  assert.equal(f.store.localPlayList[0].id, 4);
  assert.equal(f.store.downloadingSongs[0].song.id, 5);
  assert.equal(f.store.userList[0].userId, 2);
  assert.equal(f.status.personalFmMode, false);
});

test("A to B to A restores only each account's persisted likes and cloud list", async () => {
  const f = fixture();
  await f.store.setUserLikeData("songs", [11]); await f.store.setCloudPlayList([{ id: 12 }]);
  await f.store.clearUserData(); f.store.userData = { userId: 2 }; f.store.userLoginStatus = true;
  await f.store.loadData();
  assert.equal(f.store.userLikeData.songs.length, 0); assert.equal(f.store.cloudPlayList.length, 0);
  await f.store.setUserLikeData("songs", [21]); await f.store.setCloudPlayList([{ id: 22 }]);
  await f.store.clearUserData(); f.store.userData = { userId: 1 }; f.store.userLoginStatus = true;
  await f.store.loadData();
  assert.equal(f.store.userLikeData.songs[0], 11); assert.equal(f.store.cloudPlayList[0].id, 12);
});

test("a late A likes request cannot mutate B after switching or returning to A", async () => {
  const f = fixture(); const old = f.auth.updateUserLikeSongs();
  await f.store.clearUserData(); f.store.userData = { userId: 2 }; f.store.userLoginStatus = true;
  await f.store.setUserLikeData("songs", [21]);
  f.requests[0].request.resolve({ ids: [11] }); await old; await flush();
  assert.equal(f.store.userLikeData.songs[0], 21);
});

test("offline logout completes locally before a remote request settles and is idempotent", async () => {
  const f = fixture(); f.storage.set("unrelated-setting", "keep");
  const first = f.auth.toLogout(); const second = f.auth.toLogout();
  await flush();
  assert.equal(f.cookies.has("MUSIC_U"), false);
  assert.equal(f.store.userLoginStatus, false);
  await Promise.all([first, second]);
  assert.equal(f.requests.filter((p) => p.name === "logout").length, 1);
  f.requests[0].request.reject(new Error("fixture offline")); await flush();
  assert.equal(f.storage.get("unrelated-setting"), "keep");
});

test("late profile and daily recommendation responses cannot restore an exited account", async () => {
  const f = fixture(); const profile = f.auth.updateUserData(); const daily = f.auth.updateDailySongsData(true);
  await f.store.clearUserData();
  f.requests.find((r) => r.name === "userAccount")!.request.resolve({ profile: { userId: 1 } });
  f.requests.find((r) => r.name === "dailyRecommend")!.request.resolve({ data: { dailySongs: [{ id: 91 }] } });
  await daily; await flush();
  assert.equal(f.music.dailySongsData.list.length, 0);
  assert.equal(f.requests.some((r) => r.name === "userDetail"), false);
  await profile;
  assert.equal(f.store.userData.userId, 0);
});

test("a late personal FM response cannot restore an exited account's recommendations", async () => {
  const f = fixture(); f.music.personalFM.list = [];
  const pending = f.load("@/core/player/SongManager").useSongManager().initPersonalFM(false);
  await f.store.clearUserData();
  f.requests.find((r) => r.name === "personalFm")!.request.resolve({ data: [{ id: 77 }] });
  await pending;
  assert.equal(f.music.personalFM.list.length, 0);
});

test("a late personal FM refresh cannot mutate the newly selected account", async () => {
  const f = fixture(); const pending = f.load("@/core/player/SongManager").useSongManager().refreshPersonalFM();
  await f.store.clearUserData(); f.store.userData = { userId: 2 }; f.store.userLoginStatus = true;
  f.music.personalFM.list = [{ id: 22 }];
  f.requests.find((r) => r.name === "personalFm")!.request.resolve({ data: [{ id: 77 }] });
  await pending;
  assert.equal(f.music.personalFM.list[0].id, 22);
});

test("account cache restoration cannot overwrite likes and recommendations written while it reads", async () => {
  const f = fixture();
  const likes = deferred(); const daily = deferred();
  f.reads.set(f.store.accountDataKey("songs"), likes);
  f.reads.set(f.store.accountDataKey("dailySongsData"), daily);
  const restore = f.store.loadAccountData();
  await f.store.setUserLikeData("songs", [42]);
  await f.store.setDailySongsData({ timestamp: 42, list: [{ id: 42 }] });
  likes.resolve([11]); daily.resolve({ timestamp: 11, list: [{ id: 11 }] }); await restore;
  assert.equal(f.store.userLikeData.songs[0], 42);
  assert.equal(f.music.dailySongsData.list[0].id, 42);
});

test("a confirmed profile mismatch clears the local session but retains all saved accounts", async () => {
  const f = fixture();
  f.store.userList = [{ userId: 2, name: "fixture B", loginType: "qr", cookies: { MUSIC_U: "fixture-B" }, lastLoginTime: Date.now() }];
  const switching = f.auth.switchAccount(2); await flush();
  f.requests.find((r) => r.name === "userAccount")!.request.resolve({ profile: { userId: 1 } });
  await switching;
  assert.equal(f.store.userLoginStatus, false);
  assert.equal(f.cookies.has("MUSIC_U"), false);
  assert.equal(f.store.userList.length, 2);
});

test("offline account selection retains the selected identity and saved data", async () => {
  const f = fixture();
  f.store.userList = [{ userId: 2, name: "fixture B", loginType: "qr", cookies: { MUSIC_U: "fixture-B" }, lastLoginTime: Date.now() }];
  const switching = f.auth.switchAccount(2); await flush();
  f.requests.find((r) => r.name === "userAccount")!.request.reject(new Error("fixture offline"));
  await switching;
  assert.equal(f.store.userData.userId, 2);
  assert.equal(f.store.userLoginStatus, true);
  assert.equal(f.cookies.get("MUSIC_U"), "fixture-B");
});

test("cold-start writes by A cannot suppress B's offline cache restoration", async () => {
  const f = fixture();
  // Ensure the global load actually visits the delayed key.
  await f.store.setHistory({ id: 99 });
  const history = deferred(); f.reads.set("historyList", history);
  const startup = f.store.loadData();
  await f.store.setUserLikeData("songs", [11]);
  await f.store.setCloudPlayList([{ id: 12 }]);
  await f.store.clearUserData(); f.store.userData = { userId: 2 }; f.store.userLoginStatus = true;
  const db = [...f.databases.values()].find((d) => d.has("account-v1:qr:1:songs"))!;
  db.set("account-v1:qr:2:songs", [22]);
  db.set("account-v1:qr:2:cloudPlayList", [{ id: 23 }]);
  await f.store.loadAccountData();
  const likes = f.store.userLikeData.songs[0]; const cloud = f.store.cloudPlayList[0]?.id;
  history.resolve([]); await startup;
  assert.equal(likes, 22); assert.equal(cloud, 23);
});

test("an old profile restore cannot start synchronization for the newly selected account", async () => {
  const f = fixture(); const likes = deferred();
  f.reads.set(f.store.accountDataKey("songs"), likes);
  const old = f.auth.updateSpecialUserData({ userId: 1, nickname: "fixture A" });
  await f.store.clearUserData(); f.store.userData = { userId: 2 }; f.store.userLoginStatus = true;
  likes.resolve([11]); await flush();
  const started = f.requests.length;
  for (const pending of f.requests) pending.request.resolve({ playlist: [] });
  await old;
  assert.equal(started, 0);
});

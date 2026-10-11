import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { ref, computed, watch } from "vue";

const deferred = () => {
  let resolve!: (value: any) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const flush = () => new Promise<void>((resolve) => setImmediate(resolve));

function fixture() {
  const db = new Map<string, any>();
  const pending: { kind: string; server: any; request: ReturnType<typeof deferred> }[] = [];
  const adapter = new Proxy({}, { get: (_, kind: string) => (server: any) => {
    const request = deferred(); pending.push({ kind, server: { ...server }, request }); return request.promise;
  } });
  const imports: Record<string, any> = {
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "network" },
    "@/api/streaming": { subsonic: adapter, jellyfin: adapter, emby: adapter, webdav: adapter },
    localforage: { default: { createInstance: () => ({
      getItem: async (key: string) => db.get(key),
      setItem: async (key: string, value: any) => { db.set(key, structuredClone(value)); },
    }) } },
  };
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./streaming.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS },
  }).outputText, { module, exports: module.exports, ref, computed, watch, crypto: globalThis.crypto,
    require: (name: string) => imports[name] || {}, console, setTimeout, clearTimeout });
  return { store: module.exports.useStreamingStore(), pending, db };
}
const config = (name: string) => ({ name, type: "subsonic", url: `https://${name}.invalid`, username: "fixture", password: "fixture-password" });

test("a late A connection cannot replace B's active identity and connection status", async () => {
  const f = fixture(); await flush();
  const a = await f.store.addServer(config("A")); const b = await f.store.addServer(config("B"));
  const first = f.store.connectToServer(a.id); const second = f.store.connectToServer(b.id);
  f.pending[1].request.resolve({ version: "B" }); await second;
  f.pending[0].request.resolve({ version: "A" }); await first;
  assert.equal(f.store.activeServerId.value, b.id);
  assert.equal(f.store.connectionStatus.value.serverVersion, "B");
});

test("late A songs cannot commit to B or clear B's in-flight loading", async () => {
  const f = fixture(); await flush();
  const a = await f.store.addServer(config("A")); const b = await f.store.addServer(config("B"));
  const first = f.store.connectToServer(a.id); f.pending[0].request.resolve({ version: "A" }); await first;
  const oldSongs = f.store.fetchSongs();
  const second = f.store.connectToServer(b.id); f.pending[2].request.resolve({ version: "B" }); await second;
  const newSongs = f.store.fetchSongs();
  f.pending[1].request.resolve([{ id: 1, name: "A-private" }]);
  assert.equal((await oldSongs).length, 0);
  assert.equal(f.store.songs.value.length, 0);
  assert.equal(f.store.loading.value, true);
  f.pending[3].request.resolve([{ id: 2, name: "B-private" }]); await newSongs;
  assert.equal(f.store.songs.value[0].name, "B-private");
  assert.equal(f.store.loading.value, false);
});

test("disconnect invalidates authentication and every late list response", async () => {
  const f = fixture(); await flush(); const a = await f.store.addServer(config("A"));
  const connect = f.store.connectToServer(a.id); f.pending[0].request.resolve({ version: "A" }); await connect;
  const tasks = [f.store.fetchArtists(), f.store.fetchAlbums(), f.store.fetchPlaylists(), f.store.fetchPlaylistSongs("same-id"), f.store.fetchAlbumSongs("same-id")];
  f.store.disconnect();
  for (const p of f.pending.slice(1)) p.request.resolve(p.kind === "getPlaylist" || p.kind === "getAlbum" ? { songs: [{ id: 8 }] } : [{ id: 8 }]);
  for (const value of await Promise.all(tasks)) assert.equal(value.length, 0);
  assert.equal(f.store.loading.value, false);
  const late = f.store.connectToServer(a.id); f.store.disconnect();
  f.pending.at(-1)!.request.resolve({ version: "A" });
  assert.equal(await late, false); assert.equal(f.store.isConnected.value, false);
});

test("editing a server's account rotates opaque cache identity and invalidates old requests", async () => {
  const f = fixture(); await flush(); const a = await f.store.addServer(config("A"));
  const connected = f.store.connectToServer(a.id); f.pending[0].request.resolve({ version: "A" }); await connected;
  const scope = f.store.getCacheScope(); assert.ok(scope);
  const old = f.store.fetchSongs();
  await f.store.updateServer(a.id, { username: "fixture-other", password: "fixture-other-password" });
  assert.notEqual(f.store.getCacheScope(), scope);
  f.pending[1].request.resolve([{ id: 1 }]); assert.equal((await old).length, 0);
  assert.equal(f.store.isConnected.value, false);
  assert.equal(f.store.songs.value.length, 0);
});

test("legacy server namespace and selection survive a failed offline connection", async () => {
  const f = fixture(); await flush();
  f.store.servers.value = [{ ...config("legacy"), id: "legacy" }];
  const pending = f.store.connectToServer("legacy");
  f.pending[0].request.reject(new Error("fixture offline")); await pending; await flush();
  const saved = f.db.get("servers");
  assert.equal(saved?.[0]?.cacheScope, f.store.getCacheScope());
  assert.equal(f.db.get("activeServerId"), "legacy");
});

test("saved A and B identities on the same server retain distinct cache namespaces when revisited", async () => {
  const f = fixture(); await flush();
  const a = await f.store.addServer({ ...config("same"), username: "fixture-A" });
  const b = await f.store.addServer({ ...config("same"), username: "fixture-B" });
  let index = 0;
  const connect = async (id: string) => { const p = f.store.connectToServer(id); f.pending[index++].request.resolve({ version: "same" }); await p; return f.store.getCacheScope(); };
  const aScope = await connect(a.id); const bScope = await connect(b.id); const restored = await connect(a.id);
  assert.notEqual(aScope, bScope); assert.equal(aScope, restored);
  assert.ok(!aScope.includes("fixture"));
});

test("connection observers see the newly authenticated credentials before starting data requests", async () => {
  const f = fixture(); await flush();
  const a = await f.store.addServer({ ...config("A"), type: "jellyfin" });
  let observed: any;
  const stop = watch(f.store.isConnected, (connected) => {
    if (connected) observed = { ...f.store.servers.value.find((s: any) => s.id === a.id) };
  }, { flush: "sync" });
  try {
    const connecting = f.store.connectToServer(a.id);
    f.pending[0].request.resolve({ accessToken: "fixture-access", userId: "fixture-user" });
    await flush();
    f.pending[1].request.resolve({ version: "A" });
    await connecting;
    assert.equal(observed?.accessToken, "fixture-access");
    assert.equal(observed?.userId, "fixture-user");
  } finally { stop(); }
});

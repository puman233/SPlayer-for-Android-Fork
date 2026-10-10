import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync, existsSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve, dirname } from "node:path";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import axios, { AxiosError, type AxiosAdapter } from "axios";
import { createPinia, defineStore } from "pinia";
import * as vue from "vue";
import * as lodash from "lodash-es";
import { parse, compileScript } from "@vue/compiler-sfc";

const require = createRequire(import.meta.url);
const fixture = {
  cookie: "fixture_cookie_private", authorization: "fixture_authorization_private",
  token: "fixture_token_private", session: "fixture_session_private",
  secret: "fixture_api_secret_private", url: "fixture_url_private",
};

// Load complete application modules; replace only HTTP, settings and timer boundaries.
// Browser-only collaborators outside these operations throw if unexpectedly invoked.
const harness = () => {
  const logs: unknown[][] = [], notices: string[] = [], requests: unknown[] = [];
  const settings = { lastfm: { apiKey: "fixture_api_key", apiSecret: fixture.secret,
    sessionKey: fixture.session, username: "fixture_user", enabled: true,
    nowPlayingEnabled: true, scrobbleEnabled: false } };
  let failure: AxiosError | null = null;
  const failures: AxiosError[] = [];
  let mode: "failure" | "success" = "failure";
  let code = "ECONNABORTED";
  let status: number | undefined;
  let now = 0;
  const timers = new Map<number, () => void>();
  const adapter: AxiosAdapter = async (config) => {
    requests.push(config);
    if (mode === "success") return { data: { session: { key: fixture.session }, scrobbles: { accepted: 1 } }, status: 200, statusText: "OK", headers: {}, config };
    config.headers.set("X-SPlayer-Cookie", fixture.cookie);
    config.headers.set("Authorization", fixture.authorization);
    config.headers.set("X-Token", fixture.token);
    config.params = { ...config.params, token: fixture.token };
    config.data = String(config.data || "") + "&sk=" + fixture.session;
    config.url = "/fixture?token=" + fixture.url;
    failure = new AxiosError(fixture.secret, code, config, undefined, status ? {
      data: { error: 6, message: fixture.secret }, status, statusText: fixture.secret, headers: {}, config,
    } : undefined);
    failures.push(failure); throw failure;
  };
  const http = { ...axios, create: (config: Parameters<typeof axios.create>[0]) => {
    const client = axios.create(config); client.defaults.adapter = adapter; return client;
  } };
  const sharedClient = http.create({});
  const modules = new Map<string, Record<string, any>>();
  const fetchReply = { body: { "subsonic-response": { status: "failed", error: { code: 40, message: fixture.token } } } as any };
  const mounted: Array<() => unknown> = [];
  const componentVue = { ...vue, onMounted: (callback: () => unknown) => mounted.push(callback), onBeforeUnmount: () => {}, onUnmounted: () => {} };
  const unavailable = () => { throw new Error("Unexpected unrelated browser operation"); };
  const boundaries: Record<string, unknown> = {
    axios: { ...axios, __esModule: true, default: http },
    "@/stores": { useSettingStore: () => settings },
    "@/utils/request": { __esModule: true, default: async (config: any) => (await sharedClient.request(config)).data },
    "@/utils/auth": { isLogin: unavailable },
    "@/utils/format": { formatCategoryList: unavailable },
    "@/core/player/PlayerController": { usePlayerController: () => ({}) },
    "@/api/song": { songDetail: unavailable },
    "@/utils/env": { isElectron: false, isCapacitorNative: true, isCapacitorAndroid: true },
    "@/core/player/PlayerIpc": {}, "naive-ui": { NA: {} },
    "@vueuse/core": { useElementSize: () => ({ height: vue.ref(0), stop: () => {} }) },
    localforage: { __esModule: true, default: { createInstance: () => ({ getItem: async () => null, setItem: async () => {} }) } },
    "music-metadata": { parseBlob: unavailable },
    vue: componentVue, pinia: { defineStore }, "lodash-es": lodash,
  };
  const fakeConsole = Object.fromEntries(["log", "info", "warn", "error", "debug"].map(level => [level, (...args: unknown[]) => logs.push(args)]));
  const listeners = new Map<string, (event: any) => void>();
  const app = { config: {} as any, use: () => {}, directive: () => {}, mount: () => {} };
  const browser = {
    $message: { error: (message: string) => notices.push(message) },
    addEventListener: (name: string, callback: (event: any) => void) => listeners.set(name, callback),
    Capacitor: {
      logToNative: (call: unknown) => logs.push([call]),
      logFromNative: (result: unknown) => logs.push([result]),
    },
  };
  Object.assign(boundaries, {
    "@/router": {}, "@/utils/instruction": {},
    "./utils/env": { isElectron: false, isCapacitorAndroid: false },
    "./utils/embeddedApi": {}, "pinia-plugin-persistedstate": { __esModule: true, default: () => {} },
    pinia: { defineStore, createPinia },
    vue: { ...componentVue, createApp: () => app },
  });
  const load = (filename: string): Record<string, any> => {
    const path = resolve(filename);
    if (modules.has(path)) return modules.get(path)!;
    const module = { exports: {} }; modules.set(path, module.exports);
    const source = readFileSync(path, "utf8");
    const script = path.endsWith(".vue") ? compileScript(parse(source).descriptor, { id: "credential-fixture" }).content : source;
    const compiled = ts.transpileModule(script, { compilerOptions: {
      target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true,
    } }).outputText;
    runInNewContext(compiled, { ...componentVue, module, exports: module.exports,
      require: (specifier: string) => {
        if (specifier in boundaries) return boundaries[specifier];
        if (specifier.endsWith(".scss")) return {};
        if (specifier.endsWith(".vue")) return { __esModule: true, default: {} };
        if (specifier.startsWith("@/")) {
          const path = "src/" + specifier.slice(2);
          return load(existsSync(path + ".ts") ? path + ".ts" : path + "/index.ts");
        }
        if (specifier.startsWith(".")) return load(resolve(dirname(path), specifier + ".ts"));
        return require(specifier);
    }, console: fakeConsole, URLSearchParams, localStorage: { getItem: () => null, setItem: unavailable, removeItem: unavailable }, window: browser,
      Date: class extends Date { static now() { return now; } },
      fetch: async (url: string) => { requests.push({ url }); return { ok: true, status: 200, json: async () => fetchReply.body }; },
      setTimeout: (callback: () => void) => { const id = timers.size + 1; timers.set(id, callback); return id; },
      clearTimeout: (id: number) => timers.delete(id),
    }, { filename: path });
    return module.exports;
  };
  return { logs, notices, requests, settings, load, timers, failures, mounted, browser, listeners, app, fetchReply,
    failure: () => failure, succeed: () => { mode = "success"; },
    fail: (nextCode: string, nextStatus?: number) => { mode = "failure"; code = nextCode; status = nextStatus; },
    advance: (milliseconds: number) => { now += milliseconds; },
  };
};

const assertSafe = (logs: unknown[][]) => {
  assert.ok(logs.length > 0, "Non-sensitive diagnostics remain available");
  for (const args of logs) for (const arg of args) {
    assert.equal(typeof arg, "string", "Raw errors or request/response objects must never reach Console");
    for (const secret of Object.values(fixture)) assert.ok(!String(arg).includes(secret), "Sensitive fixture must not enter Console");
  }
};

test("playlist category consumer preserves rejection and emits only safe timeout diagnostics", async () => {
  const h = harness();
  const store = h.load("src/stores/data.ts").useDataStore(createPinia());
  await assert.rejects(store.getPlaylistCatList(), error => h.failures.includes(error as AxiosError));
  assert.equal(h.requests.length, 2);
  assert.equal(store.catData.cats.length, 0);
  assertSafe(h.logs);
  assert.deepEqual(h.logs[0], ["Error getting playlist cat list:", "timeout"]);
});

test("Last.fm authorization failure preserves the error but does not log token or reflected secrets", async () => {
  const h = harness(); h.fail("ERR_BAD_RESPONSE", 403);
  await assert.rejects(h.load("src/api/lastfm.ts").getSession(fixture.token), error => error === h.failure());
  assert.equal(h.requests.length, 1);
  assertSafe(h.logs);
  assert.deepEqual(h.logs[0], ["Last.fm API 错误:", "http"]);
  assert.ok(h.notices.length > 0);
  for (const notice of h.notices) for (const secret of Object.values(fixture)) assert.ok(!notice.includes(secret));
});

test("Last.fm POST scrobble failure preserves the error without logging session data", async () => {
  const h = harness(); h.fail("ERR_NETWORK");
  await assert.rejects(h.load("src/api/lastfm.ts").scrobbleTrack(fixture.session, "Fixture track", "Fixture artist", 1), error => error === h.failure());
  assert.equal(h.requests.length, 1);
  assertSafe(h.logs);
  assert.deepEqual(h.logs[0], ["Last.fm API POST 错误:", "network"]);
});

test("upper Scrobbler now-playing failure does not log the original credential-bearing error again", async () => {
  const h = harness();
  const scrobbler = h.load("src/utils/lastfmScrobbler.ts").default;
  scrobbler.startPlaying("Fixture track", "Fixture artist", "Fixture album", 60);
  await new Promise(setImmediate);
  assert.equal(h.requests.length, 1);
  assertSafe(h.logs.filter(args => String(args[0]).includes("错误") || String(args[0]).includes("失败")));
  scrobbler.stop();
});

test("upper Scrobbler scheduled submission failure stays safe and follows existing retry operations", async () => {
  const h = harness(); h.settings.lastfm.nowPlayingEnabled = false; h.settings.lastfm.scrobbleEnabled = true;
  const scrobbler = h.load("src/utils/lastfmScrobbler.ts").default;
  scrobbler.startPlaying("Fixture track", "Fixture artist", "Fixture album", 60);
  h.advance(31000);
  const fire = () => { const callbacks = [...h.timers.values()]; h.timers.clear(); callbacks.forEach(callback => callback()); };
  fire(); await new Promise(setImmediate);
  assert.equal(h.requests.length, 1);
  assertSafe(h.logs.filter(args => String(args[0]).includes("错误") || String(args[0]).includes("失败")));
  h.succeed(); scrobbler.resume(); fire(); await new Promise(setImmediate);
  assert.equal(h.requests.length, 2, "One retry only after the existing resume operation");
  scrobbler.resume(); scrobbler.stop(); await new Promise(setImmediate);
  assert.equal(h.requests.length, 2, "Successful submission must not be duplicated");
});

for (const [code, status, category] of [
  ["ECONNABORTED", undefined, "timeout"], ["ETIMEDOUT", undefined, "timeout"],
  ["ERR_CANCELED", undefined, "cancelled"], ["ERR_NETWORK", undefined, "network"],
  ["ERR_BAD_RESPONSE", 500, "http"], [fixture.secret, undefined, "unknown"],
] as const) {
  test(`Last.fm ${category} failure keeps rejection, safe notices and does not add retries (${code === fixture.secret ? "untrusted code" : code})`, async () => {
    const h = harness(); h.fail(code, status);
    await assert.rejects(h.load("src/api/lastfm.ts").getSession(fixture.token), error => error === h.failure());
    assert.equal(h.requests.length, 1);
    assertSafe(h.logs);
    assert.deepEqual(h.logs[0], ["Last.fm API 错误:", category]);
    for (const notice of h.notices) for (const secret of Object.values(fixture)) assert.ok(!notice.includes(secret));
  });
}

test("Last.fm successful authorization and scrobble responses and signatures are preserved", async () => {
  const h = harness(); h.succeed();
  const api = h.load("src/api/lastfm.ts");
  assert.equal((await api.getSession(fixture.token)).session.key, fixture.session);
  assert.equal((await api.scrobbleTrack(fixture.session, "Fixture track", "Fixture artist", 1)).scrobbles.accepted, 1);
  assert.equal(h.requests.length, 2);
  const [get, post] = h.requests as Array<{ params: Record<string, string>; data: string }>;
  assert.equal(get.params.token, fixture.token);
  assert.equal(get.params.api_sig.length, 32);
  assert.equal(new URLSearchParams(post.data).get("sk"), fixture.session);
  assert.equal(new URLSearchParams(post.data).get("api_sig")?.length, 32);
  assert.equal(h.logs.length, 0);
});

test("Last.fm unauthorized response retains existing disconnect behavior", async () => {
  const h = harness(); h.fail("ERR_BAD_RESPONSE", 401);
  await assert.rejects(h.load("src/api/lastfm.ts").getSession(fixture.token), error => error === h.failure());
  assert.equal(h.settings.lastfm.sessionKey, "");
  assert.equal(h.settings.lastfm.username, "");
  assertSafe(h.logs);
});

test("Android MessagePort callbacks use the same safe diagnostics as the fallback native entry", () => {
  const h = harness();
  const output = Object.fromEntries(["warn", "error"].map(name => [name, (...args: unknown[]) => h.logs.push(args)]));
  const event = { data: fixture.session }; let received: unknown;
  Object.assign(h.browser, { console: output, androidBridge: { onmessage: (value: unknown) => { received = value; output.error(fixture.token); return 23; } } });
  h.load("src/main.ts");
  assert.equal((h.browser as any).androidBridge.onmessage(event), 23);
  assert.equal(received, event); assertSafe(h.logs);
});

test("diagnostic projection cannot replace business failure when error getters throw", () => {
  const h = harness();
  const error = Object.defineProperty({ isAxiosError: true }, "code", { get: () => { throw new Error(fixture.secret); } });
  assert.equal(h.load("src/utils/requestDiagnostics.ts").requestFailureCategory(error), "unknown");
});

for (const [code, status, category] of [
  ["ERR_BAD_RESPONSE", 401, "http"], ["ERR_BAD_RESPONSE", 403, "http"],
  ["ERR_BAD_RESPONSE", 500, "http"], ["ECONNABORTED", undefined, "timeout"],
  ["ERR_CANCELED", undefined, "cancelled"], ["ERR_NETWORK", undefined, "network"],
] as const) {
  test(`artist songs lifecycle consumes credential-bearing ${category} failure safely (${status ?? code})`, async () => {
    const h = harness(); h.fail(code, status);
    const scope = vue.effectScope();
    try {
      scope.run(() => h.load("src/views/Artist/songs.vue").default.setup({ id: 1 }, { expose: () => {}, emit: () => {} }));
      await Promise.all(h.mounted.map(callback => callback()));
      assert.equal(h.requests.length, 1);
      assertSafe(h.logs); assert.equal(h.logs[0][1], category);
    } finally { scope.stop(); }
  });

  test(`comment list watcher consumes both credential-bearing ${category} failures safely (${status ?? code})`, async () => {
    const h = harness(); h.fail(code, status);
    const scope = vue.effectScope();
    try {
      const state: any = scope.run(() => h.load("src/components/List/ListComment.vue").default.setup({ id: 1, type: 0 }, { expose: () => {} }));
      await new Promise(setImmediate);
      assert.equal(h.requests.length, 2); assert.equal(state.commentLoading.value, false);
      assertSafe(h.logs); assert.equal(h.logs[0][1], category);
      assert.deepEqual(h.notices, ["获取评论数据失败"]);
    } finally { scope.stop(); }
  });
}

test("Last.fm connection settings callback does not expose signed request or reflected server message", async () => {
  const h = harness(); h.fail("ERR_BAD_RESPONSE", 403); h.settings.lastfm.sessionKey = "";
  const config = h.load("src/components/Setting/config/network.ts").useNetworkSettings();
  const seen = new Set();
  const walk = (value: any): any => {
    if (!value || typeof value !== "object" || vue.isRef(value) || seen.has(value)) return undefined;
    seen.add(value);
    return value.key === "lastfm_connect" ? value : Object.values(value).map(walk).find(Boolean);
  };
  const item = walk(config);
  assert.ok(item, "Invoke the actual public settings action");
  await item.action();
  assert.equal(h.requests.length, 1); assertSafe(h.logs);
  assert.ok(h.notices.length > 0);
  for (const notice of h.notices) for (const secret of Object.values(fixture)) assert.ok(!notice.includes(secret));
});

test("Vue global handler cannot re-log credential-bearing errors or nested causes", () => {
  const h = harness(); h.load("src/main.ts");
  const error = new AxiosError(fixture.token, "ERR_NETWORK", { headers: { Authorization: fixture.authorization } } as any);
  h.app.config.errorHandler(error, null, fixture.session);
  h.app.config.errorHandler(new Error(fixture.token, { cause: error }), null, fixture.session);
  assertSafe(h.logs);
});

test("unhandled rejection diagnostics prevent browser default raw credential output", () => {
  const h = harness(); h.load("src/main.ts");
  const error = new Error(fixture.token, { cause: { config: { data: fixture.session } } });
  let prevented = false;
  assert.ok(h.listeners.has("unhandledrejection"));
  h.listeners.get("unhandledrejection")!({ reason: error, preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true); assertSafe(h.logs);
});

test("Capacitor packet diagnostics never print credential-bearing calls or native results", () => {
  const h = harness(); h.load("src/main.ts");
  h.browser.Capacitor.logToNative({ options: { cookie: fixture.cookie, url: fixture.url } });
  h.browser.Capacitor.logFromNative({ data: { session: fixture.session }, error: { message: fixture.token } });
  assertSafe(h.logs);
});

test("native orphan callbacks and window errors cannot bypass the bridge diagnostic boundary", () => {
  const h = harness();
  const result = { error: new Error(fixture.token, { cause: { config: { data: fixture.session } } }) };
  let received: unknown, throwFailure = false;
  Object.assign(h.browser.Capacitor, { fromNative: (value: unknown) => { received = value; (h.browser as any).console.warn(result.error); (h.browser as any).console.error(fixture.token); if (throwFailure) throw result.error; return 17; } });
  Object.assign(h.browser, { console: Object.fromEntries(["warn", "error", "debug"].map(name => [name, (...args: unknown[]) => h.logs.push(args)])), onerror: (...args: unknown[]) => h.logs.push(args) });
  const output = (h.browser as any).console, originalWarn = output.warn, originalError = output.error;
  h.load("src/main.ts");
  assert.equal((h.browser.Capacitor as any).fromNative(result), 17);
  assert.equal(received, result, "Native callback payload must remain unchanged");
  throwFailure = true;
  assert.throws(() => (h.browser.Capacitor as any).fromNative(result), error => error === result.error);
  assert.equal(output.warn, originalWarn); assert.equal(output.error, originalError);
  assert.equal((h.browser as any).onerror(fixture.token, fixture.url, 1, 2, result.error), true);
  assertSafe(h.logs);
});

test("generic request cache preserves original rejection without stringifying its nested cause", async () => {
  const h = harness();
  Object.assign(h.browser, { sessionStorage: { getItem: () => null } });
  const error = new Error(fixture.token, { cause: { config: { data: fixture.session } } });
  await assert.rejects(h.load("src/utils/cache.ts").getCacheData(() => Promise.reject(error), { key: "fixture", time: 1 }), value => value === error);
  assertSafe(h.logs);
});

test("cached request payload returns unchanged without printing its credentials", async () => {
  const h = harness(); const value = { token: fixture.token, session: fixture.session };
  Object.assign(h.browser, { sessionStorage: { getItem: () => JSON.stringify({ value, expiry: 0 }) } });
  assert.equal(JSON.stringify(await h.load("src/utils/cache.ts").getCacheData(() => { throw new Error("Cache miss"); }, { key: fixture.url, time: 1 })), JSON.stringify(value));
  assertSafe(h.logs);
});

test("embedded Node exception and rejection consumers do not serialize error causes", () => {
  const logs: unknown[][] = [], handlers = new Map<string, (error: unknown) => void>();
  const module = { exports: {} };
  runInNewContext(ts.transpileModule(readFileSync("API/mobile-entry.ts", "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports, require: (name: string) => name.includes("requestDiagnostics") ? { requestFailureCategory: harness().load("src/utils/requestDiagnostics.ts").requestFailureCategory } : {},
    process: { env: {}, on: (name: string, callback: (error: unknown) => void) => handlers.set(name, callback) },
    console: { error: (...args: unknown[]) => logs.push(args) },
  });
  for (const name of ["uncaughtException", "unhandledRejection"]) handlers.get(name)!(new Error(fixture.token, { cause: { config: { data: fixture.session } } }));
  assertSafe(logs);
});

test("actual Subsonic reflected failure remains a failed connection with safe public status", async () => {
  const h = harness(); const store = h.load("src/stores/streaming.ts").useStreamingStore();
  await new Promise(setImmediate);
  const server = await store.addServer({ name: "Fixture", type: "subsonic", url: "https://example.invalid", username: "fixture-user", password: fixture.secret });
  assert.equal(await store.connectToServer(server.id), false);
  assert.equal(h.requests.length, 1);
  assert.ok(String((h.requests[0] as any).url).includes("/rest/ping?"));
  assert.equal(store.connectionStatus.value.connected, false); assert.equal(store.loading.value, false);
  assert.equal(store.connectionStatus.value.error, "连接失败，请检查服务器地址、凭据和网络");
  for (const secret of Object.values(fixture)) assert.ok(!JSON.stringify([h.logs, h.notices, store.connectionStatus.value]).includes(secret));
});

test("actual Subsonic successful connection still preserves server status", async () => {
  const h = harness(); h.fetchReply.body = { "subsonic-response": { status: "ok", version: "1.16.1", serverVersion: "fixture-version" } };
  const store = h.load("src/stores/streaming.ts").useStreamingStore();
  await new Promise(setImmediate);
  const status = await store.testConnection({ name: "Fixture", type: "subsonic", url: "https://example.invalid", username: "fixture-user", password: fixture.secret });
  assert.equal(status.connected, true); assert.equal(status.serverVersion, "fixture-version");
  assert.equal(h.requests.length, 1);
});

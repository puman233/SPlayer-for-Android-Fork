import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import { createPinia, defineStore } from "pinia";
import * as vue from "vue";
import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";

const require = createRequire(import.meta.url);
// Use Axios's real browser adapter pipeline, replacing only the network transport.
const axios = require(resolve(dirname(require.resolve("axios/package.json")), "dist/browser/axios.cjs"));

function fixture() {
  const useSettings = defineStore("lastfm-identity-fixture", {
    state: () => ({ lastfm: { sessionKey: "fixture-A", username: "fixture-user-A", apiKey: "fixture-api", apiSecret: "fixture-secret" } }),
  });
  const settings = useSettings(createPinia());
  const notices: string[] = [];
  const logs: unknown[][] = [];
  const pending: Array<{ config: InternalAxiosRequestConfig; reject: (error: unknown) => void; resolve: (reply: unknown) => void }> = [];
  const client: AxiosInstance = axios.create({ adapter: (config: InternalAxiosRequestConfig) =>
    new Promise((resolve, reject) => pending.push({ config, resolve, reject })) });
  const modules = new Map<string, any>();
  function load(name: string) {
    if (modules.has(name)) return modules.get(name);
    const module = { exports: {} as any };
    modules.set(name, module.exports);
    runInNewContext(ts.transpileModule(readFileSync(new URL(name, import.meta.url), "utf8"), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true, target: ts.ScriptTarget.ES2022 },
    }).outputText, {
      module, exports: module.exports, URLSearchParams,
      require: (specifier: string) => specifier === "axios" ? { ...axios, create: () => client }
        : specifier === "@/stores" ? { useSettingStore: () => settings }
        : specifier === "@/utils/requestDiagnostics" ? load("../utils/requestDiagnostics.ts")
        : specifier === "vue" ? vue : require(specifier),
      console: { error: (...args: unknown[]) => logs.push(args) },
      window: { $message: { error: (message: string) => notices.push(message) } },
    });
    return module.exports;
  }
  const api = load("./lastfm.ts");
  const sent = async (count = 1) => {
    for (let i = 0; i < 30 && pending.length < count; i++) await Promise.resolve();
    assert.equal(pending.length, count, "real Axios adapter must receive the request");
  };
  const fail = (index: number, status?: number, apiCode = 9, code = "ERR_BAD_REQUEST") => {
    const request = pending[index];
    const response = status === undefined ? undefined : { status, data: { error: apiCode, message: "fixture-reflected-secret" }, config: request.config };
    const error = new axios.AxiosError("fixture-private-error", code, request.config, {}, response);
    request.reject(error);
    return error;
  };
  const session = (id: string) => { settings.lastfm.sessionKey = `fixture-${id}`; settings.lastfm.username = `fixture-user-${id}`; };
  const preserved = (id: string) => {
    assert.ok(settings.lastfm.sessionKey === `fixture-${id}`, "current session must be preserved (values omitted)");
    assert.ok(settings.lastfm.username === `fixture-user-${id}`, "current username must be preserved (values omitted)");
  };
  return { api, settings, pending, sent, fail, session, preserved, notices, logs };
}

const submit = (f: ReturnType<typeof fixture>) => f.api.scrobbleTrack(f.settings.lastfm.sessionKey, "Fixture track", "Fixture artist", 1);

function safeDiagnostics(f: ReturnType<typeof fixture>) {
  assert.ok(f.logs.every(args => args.length === 2 && ["http", "network", "timeout", "cancelled", "unknown"].includes(String(args[1]))));
  const output = JSON.stringify([f.logs, f.notices]);
  assert.ok(!["fixture-A", "fixture-B", "fixture-secret", "fixture-private-error", "fixture-reflected-secret", "fixture-token"].some(value => output.includes(value)), "diagnostics must omit synthetic credentials and reflected messages");
}

test("L01: an old scrobble 401 preserves the newer Last.fm session and rejection", async () => {
  const f = fixture();
  const request = f.api.scrobbleTrack(f.settings.lastfm.sessionKey, "Fixture track", "Fixture artist", 1);
  await f.sent();
  f.session("B");
  const error = f.fail(0, 401);
  await assert.rejects(request, value => value === error);
  f.preserved("B");
  assert.equal(f.notices.length, 0, "stale failures must not claim that the current connection was disconnected");
  safeDiagnostics(f);
});

for (const code of [9, 4, 26]) {
  test(`L02/L03: an old 403/${code} cannot disconnect a newer session`, async () => {
    const f = fixture(); const request = submit(f); await f.sent(); f.session("B");
    const error = f.fail(0, 403, code); await assert.rejects(request, value => value === error);
    f.preserved("B"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
  });
}

for (const status of [401, 403]) {
  test(`L04: current authenticated ${status} disconnects its own session`, async () => {
    const f = fixture(); f.session("B"); const request = submit(f); await f.sent();
    const error = f.fail(0, status); await assert.rejects(request, value => value === error);
    assert.ok(!f.settings.lastfm.sessionKey && !f.settings.lastfm.username);
    assert.equal(f.notices.length, 1); assert.ok(f.notices[0].includes("已断开")); safeDiagnostics(f);
  });
}

test("L05: public and token authorization requests cannot disconnect an existing session", async () => {
  const f = fixture();
  for (const [index, operation] of [() => f.api.getUserInfo("Fixture user"), () => f.api.getSession("fixture-token")].entries()) {
    const request = operation();
    await f.sent(index + 1); const error = f.fail(index, 401);
    await assert.rejects(request, value => value === error); f.preserved("A");
  }
  assert.ok(f.notices.every(message => !message.includes("已断开"))); safeDiagnostics(f);
});

for (const [status, code] of [[undefined, "ERR_NETWORK"], [undefined, "ECONNABORTED"], [undefined, "ERR_CANCELED"], [429, "ERR_BAD_REQUEST"], [500, "ERR_BAD_RESPONSE"], [503, "ERR_BAD_RESPONSE"], [403, "ERR_BAD_REQUEST"]] as const) {
  test(`L06: non-authentication ${status ?? code} does not clear the connection`, async () => {
    const f = fixture(); const request = submit(f); await f.sent();
    const error = f.fail(0, status, 6, code); await assert.rejects(request, value => value === error);
    f.preserved("A"); assert.ok(f.notices.every(message => !message.includes("已断开"))); safeDiagnostics(f);
  });
}

test("L07/L08: synchronous A to B to A changes invalidate the old request", async () => {
  const f = fixture(); const request = submit(f); await f.sent(); f.session("B"); f.session("A");
  const error = f.fail(0, 401); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
});

test("L08: starting authorization invalidates an old request even before the key changes", async () => {
  const f = fixture(); const request = submit(f); await f.sent();
  const authorization = f.api.getAuthToken(); await f.sent(2);
  const tokenReply = { token: "fixture-token" };
  f.pending[1].resolve({ status: 200, data: tokenReply, headers: {}, config: f.pending[1].config });
  assert.equal(await authorization, tokenReply);
  const error = f.fail(0, 401); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
});

test("L08: explicit disconnect and same-key reconnect invalidate the old request", async () => {
  const f = fixture(); const request = submit(f); await f.sent(); f.api.disconnect(); f.session("A");
  const error = f.fail(0, 403); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
});

test("L08: first caller scope teardown cannot disable later identity tracking", async () => {
  const f = fixture(); const scope = vue.effectScope();
  const request = scope.run(() => submit(f))!;
  await f.sent(); scope.stop(); f.session("B"); f.session("A");
  const error = f.fail(0, 401); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
});

test("L08: changed API credentials invalidate pending ownership without clearing the session", async () => {
  const f = fixture(); const request = submit(f); await f.sent();
  f.settings.lastfm.apiKey = "fixture-api-new"; f.settings.lastfm.apiSecret = "fixture-secret-new";
  const error = f.fail(0, 403); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.equal(f.notices.length, 0); safeDiagnostics(f);
});

test("L09: scrobble, nowPlaying, love and unlove keep signed POST semantics and results", async () => {
  const f = fixture();
  const operations = [() => submit(f), () => f.api.updateNowPlaying(f.settings.lastfm.sessionKey, "Fixture track", "Fixture artist"),
    () => f.api.loveTrack(f.settings.lastfm.sessionKey, "Fixture track", "Fixture artist"),
    () => f.api.unloveTrack(f.settings.lastfm.sessionKey, "Fixture track", "Fixture artist")];
  for (const [index, operation] of operations.entries()) {
    const request = operation(); await f.sent(index + 1);
    const config = f.pending[index].config; const body = new URLSearchParams(config.data);
    assert.equal(config.method, "post"); assert.ok(body.get("sk") === f.settings.lastfm.sessionKey);
    assert.equal(body.get("api_sig")?.length, 32);
    assert.ok(!String(JSON.stringify((config as any).lastfmIdentity)).includes("fixture-"), "request ownership metadata contains no credentials");
    const result = { success: true, operation: index };
    f.pending[index].resolve({ status: 200, data: result, headers: {}, config });
    assert.equal(await request, result); f.preserved("A");
  }
  assert.equal(f.logs.length, 0); assert.equal(f.notices.length, 0);
});

test("L10: duplicate failures disconnect once and a newer retry keeps its own outcome", async () => {
  const f = fixture(); const first = submit(f); const second = submit(f); await f.sent(2);
  const e1 = f.fail(0, 401); await assert.rejects(first, value => value === e1);
  const e2 = f.fail(1, 401); await assert.rejects(second, value => value === e2);
  assert.equal(f.notices.length, 1);
  f.session("B"); const retry = submit(f); await f.sent(3);
  const result = { scrobbles: { accepted: 1 } };
  f.pending[2].resolve({ status: 200, data: result, headers: {}, config: f.pending[2].config });
  assert.equal(await retry, result); f.preserved("B"); safeDiagnostics(f);
});

test("L10: requests with a foreign session key cannot clear the current session", async () => {
  const f = fixture(); const request = f.api.loveTrack("fixture-foreign", "Fixture track", "Fixture artist");
  await f.sent(); const error = f.fail(0, 401); await assert.rejects(request, value => value === error);
  f.preserved("A"); assert.ok(f.notices.every(message => !message.includes("已断开"))); safeDiagnostics(f);
});

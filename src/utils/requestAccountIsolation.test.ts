import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import axios from "axios";
import axiosRetry from "axios-retry";

test("an API request waiting for startup retains its original credential after account switch", async () => {
  let cookie = "fixture-A"; let ready!: () => void;
  const startup = new Promise<void>((resolve) => { ready = resolve; });
  let captured = "";
  const client = axios.create({ adapter: async (config) => {
    captured = String(config.headers.get("X-SPlayer-Cookie"));
    return { data: { code: 200 }, status: 200, statusText: "OK", headers: {}, config };
  } });
  const imports: Record<string, any> = {
    axios: { ...axios, default: { ...axios, create: () => client } },
    "axios-retry": { default: axiosRetry },
    "@/stores": { useSettingStore: () => ({ proxyProtocol: "off", useRealIP: false }) },
    "./cookie": { getCookie: () => cookie }, "./auth": { isLogin: () => 1 },
    "./env": { isCapacitorAndroid: true, isCapacitorNative: true },
    "./embeddedApi": { EMBEDDED_API_BASE_URL: "http://127.0.0.1:42780", waitForEmbeddedApiReady: () => startup },
    "./requestRecovery": { createNetworkFailureNotice: () => ({ success() {}, fail: () => false }) },
  };
  const module = { exports: {} as any };
  const source = readFileSync(new URL("./request.ts", import.meta.url), "utf8").replaceAll("import.meta.env", "({})");
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports, require: (name: string) => imports[name], console, URL, performance,
    window: { $message: {} },
  });
  const pending = module.exports.default({ url: "/user/playlist", params: { uid: 1 } });
  cookie = "fixture-B"; ready(); await pending;
  assert.equal(captured, "MUSIC_U=fixture-A;os=pc;");
});

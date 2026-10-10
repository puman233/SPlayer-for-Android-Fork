import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync, existsSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import { requestFailureCategory } from "../utils/requestDiagnostics";

const require = createRequire(import.meta.url);

test("actual Netease dependency error logger preserves reflected failure without exposing credentials", async () => {
  const logs: unknown[][] = [], requests: any[] = [];
  const secret = "fixture_vendor_token";
  const consoleBoundary = Object.fromEntries(["log", "info", "warn", "error", "debug"].map(level => [level, (...args: unknown[]) => logs.push(args)]));
  if (existsSync("API/runtimeDiagnostics.ts")) {
    const module = { exports: {} };
    runInNewContext(ts.transpileModule(readFileSync("API/runtimeDiagnostics.ts", "utf8"), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
      module, exports: module.exports, console: consoleBoundary, require: () => ({ requestFailureCategory }),
    });
  }
  const vendorRequire = createRequire(require.resolve("@neteasecloudmusicapienhanced/api/util/request.js"));
  const module = { exports: null as any };
  runInNewContext(readFileSync(vendorRequire.resolve("./request"), "utf8"), {
    module, exports: module.exports, console: consoleBoundary, global: {}, URL, URLSearchParams,
    require: (name: string) => {
      if (name === "fs") return { readFileSync: () => "fixture_anonymous_token", existsSync: () => false };
      if (name === "axios") return { default: async (config: any) => { requests.push(config); throw Object.assign(new Error(secret), { config }); } };
      return vendorRequire(name);
    },
  });
  try {
    await assert.rejects(module.exports("https://example.invalid/fixture", {}, { crypto: "api", cookie: { MUSIC_U: "fixture_vendor_cookie" } }), (error: any) => error.status === 502 && error.body.msg === secret);
    assert.equal(requests.length, 1); assert.ok(logs.length > 0, "Safe diagnostics remain available");
    assert.ok(!JSON.stringify(logs).includes(secret));
    assert.ok(!JSON.stringify(logs).includes("fixture_vendor_cookie"));
    assert.ok(logs.every(args => args.every(arg => typeof arg === "string")));
  } finally { requests.forEach(request => { request.httpAgent?.destroy(); request.httpsAgent?.destroy(); }); }
});

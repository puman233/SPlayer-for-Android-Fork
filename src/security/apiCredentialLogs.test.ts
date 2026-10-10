import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { Writable } from "node:stream";
import { execFileSync } from "node:child_process";
import ts from "typescript";
import { requestFailureCategory } from "../utils/requestDiagnostics";

const require = createRequire(import.meta.url);
const secrets = ["fixture_api_cookie", "fixture_api_authorization", "fixture_api_token", "fixture_api_session"];

test("actual Fastify request, 404 and API error logs exclude credential URL/header/body/cause", async () => {
  const lines: string[] = [], received: any[] = [], servers: any[] = [];
  const stream = new Writable({ write(chunk, _encoding, done) { lines.push(String(chunk)); done(); } });
  const fastify = require("fastify");
  const factory = (options: any) => {
    const server = fastify({ ...options, logger: { ...(typeof options.logger === "object" ? options.logger : {}), stream } });
    // The module starts automatically; replace only the external listener boundary.
    server.listen = async () => "fixture"; servers.push(server); return server;
  };
  const failure = Object.assign(new Error(secrets[2], { cause: { config: { data: secrets[3] } } }), {
    isAxiosError: true, code: "ERR_BAD_RESPONSE", config: { headers: { Authorization: secrets[1] } },
    status: 403, body: { code: 403, message: secrets[2] }, response: { status: 403 },
  });
  const vendor = { playlist_catlist: async (params: unknown) => { received.push(params); throw failure; } };
  const module = { exports: {} as any };
  const original = process.env.SPLAYER_AUDIT_BASELINE === "1"
    ? execFileSync("git", ["show", "HEAD:API/server.ts"], { encoding: "utf8" })
    : readFileSync("API/server.ts", "utf8");
  // CJS test loader must not shadow its require or evaluate import.meta in a VM.
  const source = original.replace("const require = createRequire(import.meta.url);", "const vendorRequire = createRequire('file:///fixture-api.js');")
    .replace('require("@neteasecloudmusicapienhanced/api/generateConfig.js")', 'vendorRequire("@neteasecloudmusicapienhanced/api/generateConfig.js")');
  runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true } }).outputText, {
    module, exports: module.exports, process: { env: { SP_EMBEDDED: "1" } },
    console: { error: (...args: unknown[]) => lines.push(JSON.stringify(args)) },
    require: (name: string) => {
      if (name === "fastify") return factory;
      if (name === "@neteasecloudmusicapienhanced/api") return vendor;
      if (name === "module") return { createRequire: () => () => async () => {} };
      if (name === "change-case") return { pathCase: (value: string) => value.replaceAll("_", "/") };
      if (name === "../src/utils/requestDiagnostics") return { requestFailureCategory };
      if (name === "./runtimeDiagnostics") return {};
      return require(name);
    },
  });
  try {
    await new Promise(setImmediate);
    const server = await module.exports.createStandaloneApiServer();
    const response = await server.inject({ method: "POST", url: "/api/netease/playlist/catlist?token=" + secrets[2],
      headers: { origin: "https://localhost", "x-splayer-cookie": secrets[0], authorization: secrets[1] }, payload: { sk: secrets[3] } });
    assert.equal(response.statusCode, 403);
    assert.equal(response.json().message, secrets[2], "The public API response contract is unchanged; UI consumers must not display its raw message");
    assert.equal(received[0].cookie, secrets[0]); assert.equal(received[0].sk, secrets[3]);
    assert.equal((await server.inject("/unknown?token=" + secrets[2])).statusCode, 404);
    await new Promise(setImmediate);
    const output = lines.join(""); assert.ok(output.includes("Netease API request failed"));
    for (const secret of secrets) assert.ok(!output.includes(secret), "Credential must not appear in real framework logs");
    assert.ok(output.includes('"category":"http"'));
  } finally { await Promise.all(servers.map(server => server.close())); }
});

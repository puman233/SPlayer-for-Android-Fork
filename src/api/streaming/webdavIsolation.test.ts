import assert from "node:assert/strict";
import test from "node:test";
import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import { fileURLToPath, pathToFileURL } from "node:url";
import type { AddressInfo } from "node:net";
import type { StreamingServerConfig } from "@/types/streaming";
const repo = fileURLToPath(new URL("../../../", import.meta.url)).replace(/[\\/]$/, "");
const require = createRequire(repo + "/package.json");
const ts = require("typescript");
const { DOMParser } = require("@xmldom/xmldom");
const types = { exports: {} };
runInNewContext(
  ts.transpileModule(readFileSync(repo + "/src/types/main.ts", "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText,
  { module: types, exports: types.exports, require },
);
const metadata = await import(pathToFileURL(require.resolve("music-metadata")).href);
const chunk = (id, data) => {
  const h = Buffer.alloc(8);
  h.write(id);
  h.writeUInt32LE(data.length, 4);
  return Buffer.concat([h, data, data.length % 2 ? Buffer.alloc(1) : Buffer.alloc(0)]);
};
const wave = (title) => {
  const fmt = Buffer.alloc(16);
  fmt.writeUInt16LE(1, 0);
  fmt.writeUInt16LE(1, 2);
  fmt.writeUInt32LE(16000, 4);
  fmt.writeUInt32LE(32000, 8);
  fmt.writeUInt16LE(2, 12);
  fmt.writeUInt16LE(16, 14);
  const body = Buffer.concat([
    Buffer.from("WAVE"),
    chunk("fmt ", fmt),
    chunk("LIST", Buffer.concat([Buffer.from("INFO"), chunk("INAM", Buffer.from(title + "\0"))])),
    chunk("data", Buffer.alloc(32000)),
  ]);
  const h = Buffer.alloc(8);
  h.write("RIFF");
  h.writeUInt32LE(body.length, 4);
  return Buffer.concat([h, body]);
};
async function fixture(holdOldMedia = false) {
  let releaseOld!: () => void;
  let oldArrived!: () => void;
  const held = new Promise<void>((resolve) => {
    releaseOld = resolve;
  });
  const arrived = new Promise<void>((resolve) => {
    oldArrived = resolve;
  });
  let requests = 0,
    media = 0;
  const server = createServer((req, res) => {
    requests++;
    const who = Buffer.from((req.headers.authorization || "").slice(6), "base64")
      .toString()
      .startsWith("B:")
      ? "Bbb"
      : "Aaa";
    const audio = wave(who);
    if (req.method === "PROPFIND") {
      res.writeHead(207, { "Content-Type": "application/xml" });
      res.end(
        '<?xml version="1.0"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>/shared.wav</D:href><D:propstat><D:prop><D:resourcetype/><D:getcontentlength>' +
          audio.length +
          "</D:getcontentlength><D:getlastmodified>Mon, 01 Jan 2024 00:00:00 GMT</D:getlastmodified><D:getcontenttype>audio/wav</D:getcontenttype></D:prop></D:propstat></D:response></D:multistatus>",
      );
    } else {
      media++;
      const reply = () => {
        res.writeHead(200, { "Content-Type": "audio/wav" });
        res.end(audio);
      };
      if (holdOldMedia && who === "Aaa") {
        oldArrived();
        void held.then(reply);
      } else reply();
    }
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const m = { exports: {} as typeof import("./webdav") };
  runInNewContext(
    ts.transpileModule(readFileSync(repo + "/src/api/streaming/webdav.ts", "utf8"), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    }).outputText,
    {
      module: m,
      exports: m.exports,
      require: (name) =>
        name === "@/types/main"
          ? types.exports
          : name === "music-metadata"
            ? metadata
            : name === "@/utils/requestDiagnostics"
              ? { requestFailureCategory: () => "fixture" }
              : require(name),
      DOMParser,
      URL,
      fetch,
      Blob,
      Uint8Array,
      TextEncoder,
      btoa,
      unescape,
      encodeURIComponent,
      Date,
      console,
    },
  );
  const a: StreamingServerConfig = {
    id: "same-server",
    name: "Synthetic WebDAV",
    type: "webdav",
    url: "http://127.0.0.1:" + (server.address() as AddressInfo).port,
    username: "A",
    password: "synthetic-only",
    cacheScope: "scope-A",
  };
  const b = { ...a, username: "B", cacheScope: "scope-B" };
  return {
    api: m.exports,
    a,
    b,
    counts: () => ({ requests, media }),
    arrived,
    releaseOld,
    close: () =>
      new Promise((resolve) => {
        server.closeAllConnections();
        server.close(resolve);
      }),
  };
}
test("changing WebDAV credentials/cacheScope must not reuse the prior library index", async () => {
  const f = await fixture();
  try {
    const a = await f.api.getSongs(f.a);
    assert.equal(a[0].name, "Aaa");
    const b = await f.api.getSongs(f.b);
    console.log(
      "INDEX_OBSERVATION",
      JSON.stringify({ first: a[0].name, second: b[0].name, ...f.counts() }),
    );
    assert.equal(b[0].name, "Bbb");
  } finally {
    await f.close();
  }
});
test("explicit index invalidation must also isolate cached tags after credentials change", async () => {
  const f = await fixture();
  try {
    const a = await f.api.getSongs(f.a);
    assert.equal(a[0].name, "Aaa");
    f.api.invalidateLibrary(f.a.id);
    const b = await f.api.getSongs(f.b);
    console.log(
      "TAG_OBSERVATION",
      JSON.stringify({ first: a[0].name, second: b[0].name, ...f.counts() }),
    );
    assert.equal(b[0].name, "Bbb");
  } finally {
    await f.close();
  }
});

test("late old account scan cannot replace the current account library", async () => {
  const f = await fixture(true);
  try {
    const old = f.api.getSongs(f.a);
    await f.arrived;
    assert.equal((await f.api.getSongs(f.b))[0].name, "Bbb");
    f.releaseOld();
    assert.equal((await old)[0].name, "Aaa");
    assert.equal((await f.api.getSongs(f.b))[0].name, "Bbb");
  } finally {
    f.releaseOld();
    await f.close();
  }
});

test("legacy configs without a scope cannot reuse a previous credential cache", async () => {
  const f = await fixture();
  try {
    assert.equal((await f.api.getSongs({ ...f.a, cacheScope: undefined }))[0].name, "Aaa");
    assert.equal((await f.api.getSongs({ ...f.b, cacheScope: undefined }))[0].name, "Bbb");
  } finally {
    await f.close();
  }
});

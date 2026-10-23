import test from "node:test";
import assert from "node:assert/strict";
import { probeTransientHttpFailure } from "./httpMediaFailure";

test("only explicit temporary HTTP responses justify media recovery", async () => {
  for (const status of [200, 206, 401, 403, 404, 408, 415, 416, 429, 500, 501, 502, 503, 504]) {
    let options: RequestInit | undefined;
    const fetcher: typeof fetch = async (_input, init) => {
      options = init;
      return new Response(null, { status });
    };
    assert.equal(await probeTransientHttpFailure("https://media.example/track", new AbortController().signal, fetcher),
      [408, 429, 500, 502, 503, 504].includes(status));
    assert.equal(options?.credentials, "same-origin");
    assert.deepEqual(options?.headers, { Range: "bytes=0-0" });
    assert.equal(options?.cache, "no-store");
    assert.equal(options?.signal?.aborted, true, "IO must stop after headers, including successful/ignored Range");
  }
});

test("CORS, opaque responses and local media cannot become temporary HTTP evidence", async () => {
  const blocked: typeof fetch = async () => { throw new TypeError("Failed to fetch"); };
  assert.equal(await probeTransientHttpFailure("https://media.example/track", new AbortController().signal, blocked), false);
  const opaque: typeof fetch = async () => ({ status: 503, type: "opaque", body: null }) as Response;
  assert.equal(await probeTransientHttpFailure("https://media.example/track", new AbortController().signal, opaque), false);
  for (const url of ["file:///music.wav", "content://media/1", "blob:https://localhost/id", ""]) {
    let called = false;
    const fetcher: typeof fetch = async () => { called = true; return new Response(null, { status: 503 }); };
    assert.equal(await probeTransientHttpFailure(url, new AbortController().signal, fetcher), false);
    assert.equal(called, false);
  }
});

test("a pause cancels actual pending fetch and cannot produce late retry evidence", async () => {
  const command = new AbortController();
  let requestSignal: AbortSignal | undefined;
  const fetcher: typeof fetch = async (_input, init) => {
    requestSignal = init?.signal ?? undefined;
    return new Promise<Response>((_resolve, reject) => requestSignal?.addEventListener("abort", () => reject(new Error("cancelled"))));
  };
  const pending = probeTransientHttpFailure("https://media.example/track", command.signal, fetcher);
  command.abort();
  assert.equal(await pending, false);
  assert.equal(requestSignal?.aborted, true);
});

test("a server that never sends headers has a bounded diagnostic lifetime", async () => {
  let aborted = false;
  const fetcher: typeof fetch = async (_input, init) => new Promise<Response>((_resolve, reject) => {
    init?.signal?.addEventListener("abort", () => { aborted = true; reject(new Error("timeout")); });
  });
  assert.equal(await probeTransientHttpFailure("https://media.example/track", new AbortController().signal, fetcher, 15), false);
  assert.equal(aborted, true);
});

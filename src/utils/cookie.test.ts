import test from "node:test";
import assert from "node:assert/strict";
import { setCookies, getCookie, removeCookie } from "./cookie";

test("saving a fixture session never writes credentials to any console channel", (t) => {
  const oldDocument = Object.getOwnPropertyDescriptor(globalThis, "document");
  const oldStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const cookies = new Map<string, string>();
  const storage = new Map<string, string>();
  const output: unknown[][] = [];
  for (const level of ["log", "info", "warn", "error", "debug"] as const)
    t.mock.method(console, level, (...args: unknown[]) => output.push(args));
  Object.defineProperty(globalThis, "document", { configurable: true, value: {
    get cookie() { return [...cookies].map(([k, v]) => `${k}=${v}`).join("; "); },
    set cookie(value: string) {
      const [pair, ...attributes] = value.split(";");
      const index = pair.indexOf("=");
      const name = pair.slice(0, index);
      const expires = attributes.find((v) => v.trim().startsWith("expires="));
      if (expires && new Date(expires.trim().slice(8)).getTime() < Date.now()) cookies.delete(name);
      else cookies.set(name, pair.slice(index + 1));
    },
  } });
  Object.defineProperty(globalThis, "localStorage", { configurable: true, value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  } });
  t.after(() => {
    if (oldDocument) Object.defineProperty(globalThis, "document", oldDocument); else Reflect.deleteProperty(globalThis, "document");
    if (oldStorage) Object.defineProperty(globalThis, "localStorage", oldStorage); else Reflect.deleteProperty(globalThis, "localStorage");
  });
  setCookies("fixture_session=abc==");
  assert.equal(output.length, 0, "even truncated credentials must never be logged");
  // R26 parsing remains independent: do not change its currently truncated value here.
  setCookies("fixture_session=restored; fixture_csrf=synthetic");
  assert.equal(getCookie("fixture_session"), "restored");
  cookies.clear();
  assert.equal(getCookie("fixture_session"), "restored", "local fallback still restores sessions");
  removeCookie("fixture_session");
  assert.equal(getCookie("fixture_session"), null);
  assert.equal(output.length, 0);
  t.mock.method(globalThis, "decodeURIComponent", () => { throw new Error("fixture_session=synthetic_private_value"); });
  setCookies("fixture_session=abc%invalid");
  assert.equal(output.length, 1);
  assert.deepEqual(output[0], ["Cookie URL解码失败，使用原始值"]);
});

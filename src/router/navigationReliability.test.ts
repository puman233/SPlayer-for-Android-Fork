import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import * as routing from "vue-router";

function fixture() {
  let loggedIn = false;
  let opened = 0;
  const module = { exports: {} as any };
  const routes = [
    { path: "/", component: {} }, { path: "/private", component: {}, meta: { needLogin: true } },
    { path: "/client", component: {}, meta: { needApp: true } }, { path: "/403", component: {} },
  ];
  const imports: Record<string, any> = {
    "vue-router": { ...routing, createWebHashHistory: routing.createMemoryHistory },
    "@/utils/modal": { openUserLogin: () => { opened++; } },
    "@/utils/env": { isElectron: false, isCapacitorAndroid: false },
    "@/utils/auth": { isLogin: () => loggedIn }, "./routes": { default: routes },
  };
  const source = readFileSync(new URL("./index.ts", import.meta.url), "utf8").replace("import.meta.env.BASE_URL", '"/"');
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports, require: (name: string) => imports[name], console,
    window: { $loadingBar: { start() {}, error() {}, finish() {} }, $message: { warning() {} } },
    requestAnimationFrame: (fn: () => void) => fn(), document: { getElementById: () => null },
  });
  return { router: module.exports.default as routing.Router, login: () => { loggedIn = true; }, opened: () => opened };
}

test("R22: denied login navigation settles as aborted and a later authorized navigation works", async () => {
  const f = fixture(); await f.router.push("/");
  const result = await Promise.race([f.router.push("/private"), new Promise(resolve => setTimeout(() => resolve("pending"), 80))]);
  assert.notEqual(result, "pending");
  assert.equal(routing.isNavigationFailure(result, routing.NavigationFailureType.aborted), true);
  assert.equal(f.opened(), 1); assert.equal(f.router.currentRoute.value.path, "/");
  f.login(); await f.router.push("/private"); assert.equal(f.router.currentRoute.value.path, "/private");
});

test("R22: web-only client restriction redirects once to 403", async () => {
  const f = fixture(); await f.router.push("/"); await f.router.push("/client");
  assert.equal(f.router.currentRoute.value.path, "/403"); assert.equal(f.opened(), 0);
});

test("R22: repeated denied navigation does not leave promises pending", async () => {
  const f = fixture(); await f.router.push("/");
  for (let i = 0; i < 3; i++) {
    const result = await f.router.push("/private");
    assert.equal(routing.isNavigationFailure(result, routing.NavigationFailureType.aborted), true);
  }
  assert.equal(f.opened(), 3); assert.equal(f.router.currentRoute.value.path, "/");
});

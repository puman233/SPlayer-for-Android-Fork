import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { parse, compileScript } from "@vue/compiler-sfc";
import * as vue from "vue";
import { watchDebounced } from "@vueuse/core";
import Fuse from "fuse.js";

const song = (id: number, name: string) => ({ id, name });
function fixture() {
  const account = vue.ref("A");
  const store = { cloudPlayList: [song(1, "Alpha"), song(2, "Beta")], userLoginStatus: true,
    getAccountToken: () => account.value, isAccountCurrent: (token: string) => token === account.value,
    setCloudPlayList: async (data: any[]) => { store.cloudPlayList = data; } };
  let response: () => Promise<any> = async () => ({ data: [], count: 0, size: 0, maxSize: 1 });
  const unmount: Array<() => void> = [];
  const helper = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("../utils/helper.ts", import.meta.url), "utf8"), { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module: helper, exports: helper.exports, require: (name: string) => name === "fuse.js" ? { default: Fuse } : {}, console,
  });
  const imports: Record<string, any> = {
    vue,
    "@/stores": { useDataStore: () => store }, "@/api/cloud": { userCloud: () => response() },
    "@/utils/format": { formatSongsList: (data: any[]) => data }, "@/utils/helper": helper.exports,
    "@/utils/modal": { openBatchList() {} }, "@/utils/requestDiagnostics": { requestFailureCategory: () => "network" },
    "@/core/player/PlayerController": { usePlayerController: () => ({}) },
  };
  const module = { exports: {} as any };
  const script = compileScript(parse(readFileSync(new URL("./Cloud.vue", import.meta.url), "utf8")).descriptor, { id: "cloud-reliability" }).content;
  runInNewContext(transpileModule(script, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    ...vue, module, exports: module.exports, require: (name: string) => imports[name] || {}, watchDebounced,
    onMounted() {}, onActivated() {}, onBeforeUnmount: (fn: () => void) => unmount.push(fn),
    useRouter: () => ({ currentRoute: vue.ref({ name: "cloud" }) }), console: { warn() {} },
  });
  const scope = vue.effectScope();
  const page = scope.run(() => module.exports.default.setup({}, { expose() {} }));
  return { page, store, account, reply: (fn: () => Promise<any>) => { response = fn; },
    dispose: () => { unmount.forEach(fn => fn()); scope.stop(); } };
}
const searchSettled = () => new Promise(resolve => setTimeout(resolve, 350));

test("R20: first and later page failures release loading and permit retry", async () => {
  const f = fixture();
  try {
    f.reply(async () => { throw Error("fixture failure"); }); await f.page.getAllCloudMusic();
    assert.equal(f.page.loading.value, false);
    let requests = 0;
    f.reply(async () => { if (++requests === 2) throw Error("fixture later page"); return { data: [song(3, "Gamma")], count: 600, size: 0, maxSize: 1 }; });
    await f.page.getAllCloudMusic(); assert.equal(f.page.loading.value, false);
    f.reply(async () => ({ data: [], count: 0, size: 0, maxSize: 1 }));
    await f.page.getAllCloudMusic(); assert.equal(f.page.loading.value, false); assert.equal(f.page.cloudData.value.length, 0);
  } finally { f.dispose(); }
});

test("R21: unmatched search exposes an empty playback list, clearing restores all songs", async () => {
  const f = fixture();
  try {
    assert.equal(f.page.listDataShow.value.length, 2);
    f.page.searchValue.value = "Alpha"; await searchSettled(); assert.equal(f.page.listDataShow.value.length, 1);
    f.page.searchValue.value = "ZZZZZZ"; await searchSettled(); assert.equal(f.page.listDataShow.value.length, 0);
    f.page.searchValue.value = ""; await searchSettled(); assert.equal(f.page.listDataShow.value.length, 2);
  } finally { f.dispose(); }
});

test("R20/R21: late old page does not replace a newer refresh or clear its loading", async () => {
  const f = fixture(); let finishOld!: (v: any) => void; let finishNew!: (v: any) => void;
  try {
    f.reply(() => new Promise(resolve => { finishOld = resolve; })); const old = f.page.getAllCloudMusic();
    f.reply(() => new Promise(resolve => { finishNew = resolve; })); const latest = f.page.getAllCloudMusic();
    finishOld({ data: [song(3, "Old")], count: 1, size: 0, maxSize: 1 }); await old;
    assert.equal(f.page.loading.value, true); assert.equal(f.page.cloudData.value[0].name, "Alpha");
    finishNew({ data: [song(4, "New")], count: 1, size: 0, maxSize: 1 }); await latest;
    assert.equal(f.page.loading.value, false); assert.equal(f.page.cloudData.value[0].name, "New");
  } finally { f.dispose(); }
});

test("R21: rapid queries, whitespace and a new source cannot expose stale matches", async () => {
  const f = fixture();
  try {
    f.page.searchValue.value = "Alpha"; await searchSettled(); assert.equal(f.page.listDataShow.value.length, 1);
    f.page.searchValue.value = "Beta"; f.page.searchValue.value = "ZZZZZZ";
    assert.equal(f.page.listDataShow.value.length, 0); await searchSettled(); assert.equal(f.page.listDataShow.value.length, 0);
    f.page.searchValue.value = "Alpha"; f.page.cloudData.value = [song(5, "Other")];
    await searchSettled(); assert.equal(f.page.listDataShow.value.length, 0);
    f.page.searchValue.value = "   "; assert.equal(f.page.listDataShow.value.length, 1);
  } finally { f.dispose(); }
});

test("R20/R21: unmount and account switch reject stale request results", async () => {
  for (const action of ["unmount", "account"] as const) {
    const f = fixture(); let finish!: (v: any) => void;
    f.reply(() => new Promise(resolve => { finish = resolve; })); const pending = f.page.getAllCloudMusic();
    if (action === "unmount") f.dispose(); else f.account.value = "B";
    finish({ data: [song(8, "Old-private")], count: 1, size: 0, maxSize: 1 }); await pending;
    assert.equal(f.page.cloudData.value.some((s: any) => s.name === "Old-private"), false);
    if (action === "account") f.dispose();
  }
});

test("R21: pending debounce does not commit after the page is destroyed", async () => {
  const f = fixture(); f.page.searchValue.value = "Alpha"; await vue.nextTick(); f.dispose();
  await searchSettled(); assert.equal(f.page.searchData.value.length, 0);
});

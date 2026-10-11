import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { parse, compileScript } from "@vue/compiler-sfc";
import * as vue from "vue";

function fixture() {
  let resolve!: (data: any) => void;
  const delayed = new Promise<any>((yes) => { resolve = yes; });
  let scope = "scope-A";
  let fetches = 0;
  const store = { sessionRevision: vue.ref(1), activeServerId: vue.ref("A"), isConnected: vue.ref(false),
    playlists: vue.ref([]), getCacheScope: () => scope,
    fetchPlaylistSongs: async () => { fetches++; return [{ id: 21 }]; } };
  const detail = { detailData: vue.ref(null), listData: vue.ref([]), loading: vue.ref(false), headerHeight: vue.ref(0),
    setDetailData: (v: any) => { detail.detailData.value = v; }, setListData: (v: any) => { detail.listData.value = v; },
    setLoading: (v: boolean) => { detail.loading.value = v; }, getSongListHeight: () => 100 };
  const imports: Record<string, any> = {
    vue, "@/utils/requestDiagnostics": { requestFailureCategory: () => "network" },
    "@/stores": { useStreamingStore: () => store }, "@/utils/helper": { renderIcon: () => {} },
    "@/composables/List/useListDetail": { useListDetail: () => detail },
    "@/composables/List/useListSearch": { useListSearch: () => ({ searchValue: vue.ref(""), searchData: vue.ref([]), displayData: detail.listData, clearSearch() {}, performSearch() {} }) },
    "@/composables/List/useListScroll": { useListScroll: () => ({ listScrolling: vue.ref(false), handleListScroll() {}, resetScroll() {} }) },
    "@/composables/List/useListActions": { useListActions: () => ({ playAllSongs() {} }) },
    "@/composables/List/useListDataCache": { useListDataCache: () => ({ loadCache: (_type: string, _id: string, requestedScope?: string) => requestedScope === "scope-B" ? Promise.resolve(null) : delayed, saveCache: () => { throw Error("Old data must not be cached"); } }) },
  };
  const source = compileScript(parse(readFileSync(new URL("./streaming-playlist.vue", import.meta.url), "utf8")).descriptor, { id: "isolation-fixture" }).content;
  const module = { exports: {} as any };
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    ...vue, onMounted() {}, onBeforeUnmount() {}, onBeforeRouteUpdate() {}, useDebounceFn: (v: any) => v,
    useRouter: () => ({ currentRoute: vue.ref({ query: { id: "same" } }) }),
    module, exports: module.exports, require: (name: string) => imports[name] || {}, console,
    window: { $message: { error() {} } },
  });
  const effects = vue.effectScope();
  const page = effects.run(() => module.exports.default.setup({}, { expose() {} }));
  return { page, detail, store, resolve, switchToB: () => { scope = "scope-B"; store.activeServerId.value = "B"; store.sessionRevision.value++; }, fetches: () => fetches, effects };
}

test("a cached A playlist finishing after switching to B cannot render A or write B's cache", async () => {
  const { page, detail, resolve, switchToB, effects } = fixture();
  const pending = page.getPlaylistDetail("same");
  switchToB();
  resolve({ detail: { name: "A-private" }, songs: [{ id: 1 }] }); await pending;
  assert.equal(detail.listData.value.length, 0);
  assert.notEqual((detail.detailData.value as any)?.name, "A-private");
  effects.stop();
});

test("a playlist without cached data loads when the selected server finishes connecting", async () => {
  const f = fixture();
  try {
    f.switchToB(); await new Promise((resolve) => setImmediate(resolve));
    assert.equal(f.fetches(), 0);
    f.store.isConnected.value = true;
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(f.fetches(), 1);
    assert.equal((f.detail.listData.value[0] as any)?.id, 21);
  } finally { f.effects.stop(); }
});

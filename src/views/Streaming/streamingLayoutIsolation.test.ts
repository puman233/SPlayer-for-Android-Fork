import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import { parse, compileScript } from "@vue/compiler-sfc";
import * as vue from "vue";

test("an old artist tab refresh cannot continue requesting random songs for the new server", async () => {
  const pending: (() => void)[] = []; let randomRequests = 0;
  const store = { sessionRevision: vue.ref(1), activeServerId: vue.ref("A"), activeServer: vue.ref({ id: "A" }),
    isConnected: vue.ref(true), servers: vue.ref([]), songs: vue.ref([]), artists: vue.ref([]), albums: vue.ref([]), playlists: vue.ref([]),
    fetchArtists: () => new Promise<void>((resolve) => pending.push(resolve)), fetchRandomSongs: async () => { randomRequests++; return []; } };
  const imports: Record<string, any> = { vue, "@/utils/requestDiagnostics": { requestFailureCategory: () => "network" },
    "@/stores": { useStreamingStore: () => store, useSettingStore: () => ({}) },
    "@/composables/useDevice": { useDevice: () => ({ isPhone: vue.ref(true) }) },
    "@/utils/helper": { renderIcon() {} }, "@/core/player/PlayerController": { usePlayerController: () => ({}) },
    "@/utils/modal": {} };
  const source = compileScript(parse(readFileSync(new URL("./layout.vue", import.meta.url), "utf8")).descriptor, { id: "streaming-isolation" }).content;
  const module = { exports: {} as any };
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 } }).outputText, {
    ...vue, watch() {}, onMounted() {}, onBeforeUnmount() {},
    useRouter: () => ({ currentRoute: vue.ref({ name: "streaming-artists" }) }),
    module, exports: module.exports, require: (name: string) => imports[name] || {}, console, window: { $message: { error() {} } },
  });
  const page = module.exports.default.setup({}, { expose() {} });
  const old = page.forceRefreshCurrentTab();
  store.sessionRevision.value = 2; store.activeServerId.value = "B";
  const current = page.forceRefreshCurrentTab();
  pending[0](); await old;
  assert.equal(randomRequests, 0);
  assert.equal(page.loading.value, true);
  pending[1](); await current;
  assert.equal(randomRequests, 1); assert.equal(page.loading.value, false);
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { parse, compileScript } from "@vue/compiler-sfc";
import { renderToString } from "@vue/server-renderer";
import * as vue from "vue";
import { sampleSize } from "lodash-es";
import { useLikedHomeCover } from "../../composables/useLikedHomeCover.ts";

// Render the actual home and both card templates. Only network, animation and UI primitives are stubbed.
async function renderHome(compact: boolean, playlists: any[], songs: any[] = []) {
  const data = vue.reactive({
    userData: { userId: 1 },
    loginType: "qr",
    userLikeData: { playlists },
    likeSongsList: { detail: { id: 101 }, data: songs },
    getAccountToken: () => "account:1",
  });
  const image = vue.defineComponent({
    props: ["src"],
    setup: (props) => () => vue.h("img", { src: props.src }),
  });
  const slot = vue.defineComponent({
    setup: (_, ctx) => () => vue.h("div", ctx.slots.default?.()),
  });
  const imports: Record<string, any> = {
    vue,
    "@/composables/useLikedHomeCover": { useLikedHomeCover },
    "lodash-es": { sampleSize },
    "naive-ui": { NText: slot },
    "@/stores": {
      useDataStore: () => data,
      useMusicStore: () => ({ dailySongsData: { list: [] } }),
      useStatusStore: () => ({}),
      useSettingStore: () => ({ hiddenCovers: { home: false }, homePageSections: [] }),
    },
    "@/utils/auth": { isLogin: () => 1 },
    "@/utils/helper": { coverLoaded() {} },
    "@/api/playlist": {
      playlistAllSongs: async () => ({
        songs: [{ id: 1, cover: "https://example.test/plain-album.jpg" }],
      }),
    },
    "@/utils/format": { formatSongsList: (items: any[]) => items },
    "@vueuse/core": {
      useMediaQuery: () => vue.ref(compact),
      useResizeObserver() {},
      useElementVisibility: () => vue.ref(false),
      useDocumentVisibility: () => vue.ref("hidden"),
      usePreferredReducedMotion: () => vue.ref("reduce"),
    },
  };
  const load = (path: string): any => {
    const module = { exports: {} as any };
    const script = compileScript(
      parse(readFileSync(new URL(path, import.meta.url), "utf8")).descriptor,
      { id: path, inlineTemplate: true },
    ).content;
    runInNewContext(
      transpileModule(script, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText,
      {
        ...vue,
        module,
        exports: module.exports,
        console,
        useRouter: () => ({ push() {} }),
        require: (name: string) =>
          name === "@/components/Card/HomeRecommendationCard.vue"
            ? { default: load("../../components/Card/HomeRecommendationCard.vue") }
            : name.endsWith(".vue")
              ? { default: slot }
              : imports[name] || {},
      },
    );
    return module.exports.default;
  };
  const home = load("./HomeOnline.vue");
  const setup = home.setup;
  home.setup = async (...args: any[]) => {
    const render = setup(...args);
    await vue.nextTick();
    await vue.nextTick();
    return render;
  };
  const app = vue.createSSRApp(home);
  for (const name of ["n-flex", "n-card", "n-text", "n-h3", "ArtistList", "CoverList", "SvgIcon"])
    app.component(name, slot);
  for (const name of ["n-image", "s-image"]) app.component(name, image);
  app.component("SongListCard", load("../../components/Card/SongListCard.vue"));
  app.component("PersonalFM", { render: () => null });
  return renderToString(app);
}

for (const compact of [true, false]) {
  test(`logged-in home displays liked playlist artwork before opening its songs (${compact ? "phone" : "tablet"})`, async () => {
    const html = await renderHome(compact, [{ id: 101, cover: "https://example.test/liked.jpg" }]);
    assert.ok(html.includes('src="https://example.test/plain-album.jpg"'), html);
    assert.ok(!html.includes('src="https://example.test/liked.jpg"'));
  });
  test(`liked artwork uses cached album art instead of the server heart composite (${compact ? "phone" : "tablet"})`, async () => {
    const html = await renderHome(
      compact,
      [
        {
          id: 101,
          cover: "https://example.test/full.jpg",
          coverSize: { m: "https://example.test/medium.jpg" },
        },
      ],
      [{ id: 1, cover: "https://example.test/song.jpg" }],
    );
    assert.ok(!html.includes('src="https://example.test/medium.jpg"'));
    assert.ok(html.includes('src="https://example.test/song.jpg"'));
  });
  test(`song artwork remains available without a playlist image (${compact ? "phone" : "tablet"})`, async () => {
    const html = await renderHome(compact, [], [{ id: 1, cover: "https://example.test/song.jpg" }]);
    assert.ok(html.includes('src="https://example.test/song.jpg"'));
  });
}

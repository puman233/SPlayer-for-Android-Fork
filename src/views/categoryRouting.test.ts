import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "vue/compiler-sfc";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";
import * as Vue from "vue";
import * as Router from "vue-router";
import { useCategoryRequest } from "../composables/useCategoryRequest";

// 使用实际布局、路由与 KeepAlive；仅替换 DOM 动画和业务子页。
function layout(
  group: string,
  settings: any,
  refreshSeq: any,
  overrides: Record<string, any> = {},
  file = `${group}/layout.vue`,
) {
  const source = readFileSync(new URL(`./${file}`, import.meta.url), "utf8")
    .replace(/<Transition\b[^>]*>/g, "<div>")
    .replaceAll("</Transition>", "</div>");
  const { descriptor } = parse(source);
  const compiled = compileScript(descriptor, { id: group, inlineTemplate: true });
  const output = transpileModule(compiled.content, {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} as any };
  const imports: Record<string, any> = {
    vue: Vue,
    "@/stores": {
      useSettingStore: () => settings,
      useDataStore: () => ({ loginType: "qr", userLikeData: {} }),
    },
    "@/composables/useDevice": { useDevice: () => ({ isPhone: true }) },
    "@/composables/useViewRefresh": { useViewRefresh: () => ({ refreshSeq }) },
    "@/composables/useCategoryRequest": { useCategoryRequest },
    ...overrides,
  };
  runInNewContext(output, {
    ...Vue,
    ...Router,
    module,
    exports: module.exports,
    require: (name: string) => {
      assert.ok(imports[name], name);
      return imports[name];
    },
  });
  return module.exports.default;
}

type Host = {
  type: string;
  text: string;
  children: Host[];
  parent: Host | null;
  props: Record<string, any>;
};
const node = (type: string): Host => ({ type, text: "", children: [], parent: null, props: {} });
const renderer = Vue.createRenderer<Host, Host>({
  createElement: node,
  createText: (text) => Object.assign(node("text"), { text }),
  createComment: () => node("comment"),
  setText: (n, text) => {
    n.text = text;
  },
  setElementText: (n, text) => {
    n.text = text;
    n.children = [];
  },
  parentNode: (n) => n.parent,
  nextSibling: (n) => n.parent?.children[n.parent.children.indexOf(n) + 1] || null,
  patchProp(n, key, _old, value) {
    n.props[key] = value;
  },
  insert(n, parent, anchor) {
    if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
    n.parent = parent;
    const index = anchor ? parent.children.indexOf(anchor) : -1;
    if (index < 0) parent.children.push(n);
    else parent.children.splice(index, 0, n);
  },
  remove(n) {
    if (n.parent) n.parent.children.splice(n.parent.children.indexOf(n), 1);
    n.parent = null;
  },
});
const text = (n: Host): string => n.text + n.children.map(text).join("");
const find = (n: Host, type: string): Host | undefined =>
  n.type === type ? n : n.children.map((child) => find(child, type)).find(Boolean);
const flush = async () => {
  await new Promise<void>((done) => setImmediate(done));
  await Vue.nextTick();
};
const deferred = () => {
  let resolve!: (value: any) => void, reject!: (error: Error) => void;
  const promise = new Promise<any>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};

async function discovery(file: string) {
  const calls: { kind: string; args: any[]; pending: ReturnType<typeof deferred> }[] = [];
  const request =
    (kind: string) =>
    (...args: any[]) => {
      const pending = deferred();
      calls.push({ kind, args, pending });
      return pending.promise;
    };
  const view = layout(
    "Discover",
    { hiddenCovers: {} },
    Vue.ref(0),
    {
      "@/api/artist": { artistTypeList: request("artists") },
      "@/api/playlist": { topPlaylist: request("toplists") },
      "@/api/rec": { newAlbumsAll: request("albums"), newSongs: request("songs") },
      "@/utils/format": {
        formatArtistsList: (v: any) => v,
        formatCoverList: (v: any) => v,
        formatSongsList: (v: any) => v,
      },
    },
    `Discover/${file}.vue`,
  );
  const router = Router.createRouter({
    history: Router.createMemoryHistory(),
    routes: [{ path: `/discover/${file}`, name: `discover-${file}`, component: view }],
  });
  const root = node("root"),
    app = renderer.createApp({ render: () => Vue.h(Router.RouterView) });
  for (const kind of ["ArtistList", "CoverList", "SongList"])
    app.component(
      kind,
      Vue.defineComponent({
        props: ["data", "type", "loading", "loadMore"],
        emits: ["loadMore"],
        setup(props, { emit }) {
          return () =>
            Vue.h(
              "list",
              { onLoadMore: () => emit("loadMore") },
              `${kind}:${props.type}:${JSON.stringify(props.data)}`,
            );
        },
      }),
    );
  app.config.warnHandler = () => {};
  app.use(router);
  await router.push(`/discover/${file}`);
  app.mount(root);
  await flush();
  return { calls, root, router, app };
}

test("实际歌手页：参数、分页去重、快速筛选、失败和重试", async () => {
  const f = await discovery("artists");
  try {
    assert.deepEqual(f.calls[0].args, [-1, -1, -1, 0, 50]);
    f.calls[0].pending.resolve({ artists: [{ id: 1, name: "first" }], more: true });
    await flush();
    assert.ok(text(f.root).includes("first"));
    find(f.root, "list")!.props.onLoadMore();
    find(f.root, "list")!.props.onLoadMore();
    assert.equal(f.calls.length, 2);
    assert.equal(f.calls[1].args[3], 50);
    f.calls[1].pending.reject(new Error("offline"));
    await flush();
    assert.ok(find(f.root, "n-alert")!.props.title.includes("加载失败"));
    find(f.root, "n-button")!.props.onClick();
    assert.equal(f.calls[2].args[3], 50, "失败页重试不跳页");
    f.calls[2].pending.resolve({ artists: [{ id: 2, name: "second" }], more: false });
    await flush();
    assert.ok(text(f.root).includes("first") && text(f.root).includes("second"));
    await f.router.push("/discover/artists?initial=A&type=17");
    assert.equal(f.calls[3].args[1], 0, "其他地区的0不能被当作默认全部");
    await f.router.push("/discover/artists?initial=B");
    f.calls[4].pending.resolve({ artists: [{ id: 4, name: "latest-B" }], more: false });
    await flush();
    f.calls[3].pending.resolve({ artists: [{ id: 3, name: "stale-A" }], more: true });
    await flush();
    assert.ok(text(f.root).includes("latest-B"));
    assert.ok(!text(f.root).includes("stale-A"));
  } finally {
    f.app.unmount();
  }
});

test("实际最新音乐页：专辑/歌曲分发、迟到结果与空结果", async () => {
  const f = await discovery("new");
  try {
    assert.equal(f.calls[0].kind, "albums");
    assert.deepEqual(f.calls[0].args, ["ALL", 50, 0]);
    await f.router.push("/discover/new?type=1&area=1");
    assert.equal(f.calls[1].kind, "songs");
    assert.deepEqual(f.calls[1].args, [7]);
    f.calls[1].pending.resolve({ data: [{ id: 2, name: "new-song" }] });
    await flush();
    f.calls[0].pending.resolve({ albums: [{ id: 1, name: "old-album" }], total: 1 });
    await flush();
    assert.ok(text(f.root).includes("SongList") && text(f.root).includes("new-song"));
    assert.ok(!text(f.root).includes("old-album"));
    await f.router.push("/discover/new?type=0&area=4");
    assert.deepEqual(f.calls[2].args, ["JP", 50, 0]);
    f.calls[2].pending.resolve({ albums: [], total: 0 });
    await flush();
    assert.ok(text(f.root).includes("CoverList:album:[]"));
  } finally {
    f.app.unmount();
  }
});

test("实际排行榜页：官方/精选分类和错误重试", async () => {
  const f = await discovery("toplists");
  try {
    assert.equal(f.calls[0].kind, "toplists");
    f.calls[0].pending.reject(new Error("unavailable"));
    await flush();
    assert.ok(find(f.root, "n-alert")!.props.title.includes("加载失败"));
    find(f.root, "n-button")!.props.onClick();
    f.calls[1].pending.resolve({
      list: [
        { id: 1, name: "official-chart", ToplistType: "S", tracks: [] },
        { id: 2, name: "selected-chart", tracks: [] },
      ],
    });
    await flush();
    assert.equal(find(f.root, "SongListCard")!.props.title, "official-chart");
    assert.ok(text(f.root).includes("selected-chart"));
  } finally {
    f.app.unmount();
  }
});

test("实际新碟页：分页失败重试保持offset，不重复或跳过上一页", async () => {
  const f = await discovery("new");
  try {
    f.calls[0].pending.resolve({ albums: [{ id: 1, name: "page-one" }], total: 100 });
    await flush();
    find(f.root, "list")!.props.onLoadMore();
    assert.deepEqual(f.calls[1].args, ["ALL", 50, 50]);
    f.calls[1].pending.reject(new Error("page two unavailable")); await flush();
    assert.ok(find(f.root, "n-alert")!.props.title.includes("加载失败"));
    find(f.root, "n-button")!.props.onClick();
    assert.deepEqual(f.calls[2].args, ["ALL", 50, 50]);
    f.calls[2].pending.resolve({ albums: [{ id: 2, name: "page-two" }], total: 100 });
    await flush();
    assert.ok(text(f.root).includes("page-one") && text(f.root).includes("page-two"));
    assert.equal(text(f.root).split("page-one").length - 1, 1);
  } finally { f.app.unmount(); }
});

for (const [file, category, method, listType] of [
  ["albums", "albums", "updateUserLikeAlbums", "album"],
  ["artists", "artists", "updateUserLikeArtists", "artist"],
  ["videos", "mvs", "updateUserLikeMvs", "video"],
  ["radios", "djs", "updateUserLikeDjs", "radio"],
]) {
  test(`实际收藏${file}页：独立数据与错误重试`, async () => {
    const data = Vue.reactive({
      userLikeData: { albums: [], artists: [], mvs: [], djs: [], playlists: [{ id: 99 }] },
    }) as any;
    const requests: ReturnType<typeof deferred>[] = [];
    const request = () => {
      const pending = deferred();
      requests.push(pending);
      return pending.promise.then((items) => {
        data.userLikeData[category] = items;
      });
    };
    const inner = layout(
      "Like",
      {},
      Vue.ref(0),
      {
        "@/stores": { useDataStore: () => data, useSettingStore: () => ({ hiddenCovers: {} }) },
        "@/utils/auth": {
          updateUserLikeAlbums:
            method === "updateUserLikeAlbums" ? request : () => assert.fail("错误接口"),
          updateUserLikeArtists:
            method === "updateUserLikeArtists" ? request : () => assert.fail("错误接口"),
          updateUserLikeMvs:
            method === "updateUserLikeMvs" ? request : () => assert.fail("错误接口"),
          updateUserLikeDjs:
            method === "updateUserLikeDjs" ? request : () => assert.fail("错误接口"),
        },
      },
      "Like/CategoryList.vue",
    );
    const wrapper = layout(
      "Like",
      {},
      Vue.ref(0),
      { "./CategoryList.vue": { default: inner } },
      `Like/${file}.vue`,
    );
    const root = node("root"),
      app = renderer.createApp(wrapper);
    for (const kind of ["ArtistList", "CoverList"])
      app.component(
        kind,
        Vue.defineComponent({
          props: ["data", "type", "loading"],
          setup(props) {
            return () =>
              Vue.h("list", `${kind}:${props.type || "artist"}:${JSON.stringify(props.data)}`);
          },
        }),
      );
    app.config.warnHandler = () => {};
    app.mount(root);
    try {
      assert.equal(requests.length, 1);
      requests[0].reject(new Error("offline"));
      await flush();
      assert.ok(find(root, "n-alert")!.props.title.includes("加载失败"));
      assert.ok(!text(root).includes("99"));
      find(root, "n-button")!.props.onClick();
      requests[1].resolve([{ id: 12, name: `${file}-fixture` }]);
      await flush();
      assert.ok(text(root).includes(`:${listType}:`) && text(root).includes(`${file}-fixture`));
      assert.equal(data.userLikeData.playlists[0].id, 99);
    } finally {
      app.unmount();
    }
  });
}

for (const group of ["Discover", "Like"]) {
  for (const keepAlive of [true, false]) {
    test(`${group}: 分类切换、返回与刷新隔离 (KeepAlive=${keepAlive})`, async () => {
      const prefix = group.toLowerCase();
      const categories =
        group === "Discover"
          ? ["playlists", "toplists", "artists", "new"]
          : ["playlists", "albums", "artists", "videos", "radios"];
      const refreshSeq = Vue.ref(0);
      const settings = Vue.reactive({ useKeepAlive: keepAlive, routeAnimation: "none" });
      const mounts: Record<string, number> = {};
      const router = Router.createRouter({
        history: Router.createMemoryHistory(),
        routes: [
          {
            path: `/${prefix}`,
            component: layout(group, settings, refreshSeq),
            children: categories.map((category) => ({
              path: category,
              name: `${prefix}-${category}`,
              component: Vue.defineComponent({
                setup() {
                  mounts[category] = (mounts[category] || 0) + 1;
                  return () => Vue.h("article", `content:${category}`);
                },
              }),
            })),
          },
        ],
      });
      const root = node("root");
      const app = renderer.createApp({ render: () => Vue.h(Router.RouterView) });
      app.config.warnHandler = () => {};
      app.use(router);
      await router.push(`/${prefix}/playlists`);
      app.mount(root);
      try {
        for (const category of [...categories, ...[...categories].reverse()]) {
          await router.push({ name: `${prefix}-${category}` });
          await Vue.nextTick();
          assert.ok(
            text(root).includes(`content:${category}`),
            `错误内容 ${category}: ${text(root)}`,
          );
          for (const other of categories.filter((v) => v !== category)) {
            assert.ok(!text(root).includes(`content:${other}`), `串页 ${other}`);
          }
        }
        const before = mounts.playlists;
        refreshSeq.value++;
        await Vue.nextTick();
        assert.equal(mounts.playlists, before + 1, "长按刷新应重新挂载当前分类");
        await router.push({ name: `${prefix}-artists` });
        await Vue.nextTick();
        assert.ok(text(root).includes("content:artists"), "刷新后仍应正确切换分类");
      } finally {
        app.unmount();
      }
    });
  }
}

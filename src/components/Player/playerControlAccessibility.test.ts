import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";
import * as vue from "vue";
import { NButton } from "naive-ui";
import { debounce, throttle } from "lodash-es";

// Actual SFC render/setup and Naive Button; browser default key activation is tested on Android.
function fixture(file: string) {
  type Node = { tag: string; props: Record<string, any>; children: Node[]; parent?: Node;
    style: Record<string, string>;
    listeners: Map<string, Set<(e: any) => void>>; addEventListener: (key: string, fn: any) => void;
    removeEventListener: (key: string, fn: any) => void; classList: { add(): void; remove(): void }; };
  const node = (tag: string): Node => {
    const listeners = new Map<string, Set<(e: any) => void>>();
    return { tag, props: {}, children: [], style: {}, listeners, classList: { add() {}, remove() {} },
      addEventListener(key, fn) { if (!listeners.has(key)) listeners.set(key, new Set()); listeners.get(key)!.add(fn); },
      removeEventListener(key, fn) { listeners.get(key)?.delete(fn); },
    };
  };
  const renderer = vue.createRenderer<Node, Node>({
    createElement: node, createText: () => node("text"), createComment: () => node("comment"),
    insert(child, parent, anchor) {
      if (child.parent) { const i = child.parent.children.indexOf(child); if (i >= 0) child.parent.children.splice(i, 1); }
      child.parent = parent; const i = anchor ? parent.children.indexOf(anchor) : -1;
      if (i < 0) parent.children.push(child); else parent.children.splice(i, 0, child);
    },
    remove(child) { const list = child.parent?.children; if (list) list.splice(list.indexOf(child), 1); },
    parentNode: child => child.parent || null,
    nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] || null,
    patchProp(el, key, _prev, next) { el.props[key] = next; }, setText() {}, setElementText() {},
  });
  const music = vue.reactive({ playSong: { id: 1, type: "song", path: "/fixture.wav" }, personalFMSong: { id: 2 } });
  const status = vue.reactive({ shuffleMode: "off", repeatMode: "off", shuffleIcon: "Shuffle", repeatIcon: "Repeat",
    playStatus: false, playLoading: false, personalFmMode: false, playerMetaShow: true, songCommentCount: 0,
    effectivePureLyricMode: false, isImmersiveFullscreen: false, showFullPlayer: true, automixFxSeq: 0, automixEndedSeq: 0 });
  const calls: string[] = [];
  const shortcuts = { playOrPause: { shortcut: "Space", name: "播放 / 暂停" } };
  const player = { toggleShuffle: () => { calls.push("shuffle"); status.shuffleMode = status.shuffleMode === "off" ? "on" : "off"; },
    toggleRepeat: () => { calls.push("repeat"); status.repeatMode = status.repeatMode === "off" ? "list" : status.repeatMode === "list" ? "one" : "off"; },
    nextOrPrev: (direction: string) => calls.push(direction), playOrPause: () => { calls.push("play"); status.playStatus = !status.playStatus; } };
  const imports: Record<string, any> = {
    vue, "lodash-es": { debounce, throttle }, "@vueuse/core": { useIntersectionObserver() {} },
    "@/stores": { useMusicStore: () => music, useStatusStore: () => status, useDataStore: () => ({ isLikeSong: () => false }),
      useShortcutStore: () => ({ shortcutList: shortcuts }),
      useSettingStore: () => ({ fullscreenPlayerElements: { like: false, addToPlaylist: false, download: false, comments: false, commentCount: false } }) },
    "@/core/player/PlayerController": { usePlayerController: () => player },
    "@/core/player/SongManager": { useSongManager: () => ({ personalFMTrash: () => calls.push("trash") }) },
    "@/composables/useTimeFormat": { useTimeFormat: () => ({ timeDisplay: vue.ref(["00:00", "03:00"]), toggleTimeFormat() {} }) },
    "@/composables/useDevice": { useDevice: () => ({ isPad: vue.ref(false) }) },
    "@/utils/auth": {}, "@/utils/modal": {}, "@/api/comment": {}, "@/utils/format": { formatCommentCount: () => "0" },
  };
  const load = (source: string) => {
    const module = { exports: {} as any };
    runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 } }).outputText,
      { ...vue, module, exports: module.exports, require: (name: string) => imports[name] || {}, console,
        window: { clearTimeout, setTimeout } });
    return module.exports;
  };
  const directive = load(readFileSync(new URL("../../utils/instruction.ts", import.meta.url), "utf8")).debounceDirective;
  const globalKey = load(readFileSync(new URL("../../composables/useInit.ts", import.meta.url), "utf8") + "\nexport { keyDownEvent as keyboardConsumer };" ).keyboardConsumer;
  const descriptor = parse(readFileSync(new URL(file, import.meta.url), "utf8")).descriptor;
  const component = load(compileScript(descriptor, { id: "r24-fixture", inlineTemplate: true }).content).default;
  const app = renderer.createApp(component, { pageIndex: 0, pageCount: 2, persistent: false });
  // Use the installed library's SSR style adapter, without pretending to have a browser DOM.
  app.provide("@css-render/vue3-ssr", { styles: [], ids: new Set() });
  app.component("NButton", NButton); app.directive("debounce", directive);
  app.component("SvgIcon", { props: ["name", "size", "depth"], setup: props => () => vue.h("svg", { "data-icon": props.name }) });
  for (const name of ["PlayerSlider", "PlayerQuickActionsMenu", "PlayerRightMenu"]) app.component(name, { render: () => vue.h("stub") });
  for (const name of ["NFlex", "NBadge"]) app.component(name, { setup: (_p, { slots }) => () => vue.h("layout", {}, slots.default?.()) });
  const root = node("root"); app.mount(root);
  const all = (n = root): Node[] => [n, ...n.children.flatMap(c => all(c))];
  const controls = () => all().filter(n => /mode-btn|ctrl-btn|btn-icon|play-pause|play-btn/.test(String(n.props.class || "")));
  const click = (el: Node) => {
    const event = { target: el, stopPropagation() {}, preventDefault() {} };
    const fn = el.props.onClick; if (fn) for (const handler of Array.isArray(fn) ? fn : [fn]) handler(event);
    for (const listener of [...(el.listeners.get("click") || [])]) listener(event);
  };
  const key = (el: Node, value: string) => {
    let stopped = false; let prevented = false;
    const event = { key: value, code: value === " " ? "Space" : value, target: { tagName: el.tag.toUpperCase() },
      stopPropagation: () => { stopped = true; }, preventDefault: () => { prevented = true; }, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false };
    for (let current: Node | undefined = el; current && !stopped; current = current.parent) {
      const handlers = current.props.onKeydown;
      for (const handler of handlers ? Array.isArray(handlers) ? handlers : [handlers] : []) handler(event);
    }
    return { stopped, prevented, event };
  };
  return { controls, all, click, key, calls, status, music, globalKey, dispose: () => { app.unmount(); globalKey.cancel(); } };
}

for (const file of ["./MobilePlayerBottomControls.vue", "./PlayerControl.vue"]) {
  test(`R24-01/02 ${file} renders named native transport buttons`, () => {
    const f = fixture(file);
    try {
      const controls = f.controls(); assert.equal(controls.length, 5);
      assert.ok(controls.every(n => n.tag === "button"), "all transport controls must be actual buttons");
      assert.ok(controls.every(n => n.props["aria-label"]), "each button needs a name");
      assert.ok(controls.every(n => n.props.tabindex !== -1), "play button must not disable focus");
    } finally { f.dispose(); }
  });

  test(`R24-07/08 ${file} exposes live shuffle and all repeat states`, async () => {
    const f = fixture(file);
    try {
      const [shuffle,,,, repeat] = f.controls();
      assert.equal(shuffle.props["aria-pressed"], false);
      f.click(shuffle); await vue.nextTick(); assert.equal(shuffle.props["aria-pressed"], true);
      f.status.shuffleMode = "heartbeat"; await vue.nextTick(); assert.match(shuffle.props["aria-label"], /心动/);
      f.status.shuffleMode = "off"; await vue.nextTick(); assert.equal(shuffle.props["aria-pressed"], false);
      assert.equal(repeat.props["aria-label"], "循环模式：关闭");
      for (const label of ["循环模式：列表循环", "循环模式：单曲循环", "循环模式：关闭"]) {
        f.click(repeat); await vue.nextTick(); assert.equal(repeat.props["aria-label"], label);
        assert.equal(repeat.props["aria-pressed"], undefined);
      }
    } finally { f.dispose(); }
  });

  test(`R24-06/09 ${file} actual click dispatch and debounce remain intact`, async (t) => {
    t.mock.timers.enable({ apis: ["setTimeout", "Date"] });
    const f = fixture(file);
    try {
      const [,prev,,next] = f.controls();
      f.click(prev); f.click(prev); f.click(next); f.click(next);
      assert.deepEqual(f.calls, file.includes("Mobile") ? ["prev", "prev", "next", "next"] : ["prev", "next"]);
      t.mock.timers.tick(301); f.click(prev);
      assert.equal(f.calls.filter(x => x === "prev").length, file.includes("Mobile") ? 3 : 2);
      await vue.nextTick();
    } finally { f.dispose(); }
  });

  test(`R24-10 ${file} Enter and Space stay local without suppressing native activation`, () => {
    const f = fixture(file);
    try {
      for (const control of f.controls()) for (const value of ["Enter", " "]) {
        const result = f.key(control, value);
        assert.equal(result.stopped, true, "activation must not reach the configurable global shortcut listener");
        assert.equal(result.prevented, false, "browser must retain native key activation");
      }
      const tab = f.key(f.controls()[0], "Tab"); assert.equal(tab.stopped, false); assert.equal(tab.prevented, false);
    } finally { f.dispose(); }
  });

  test(`R24-11 ${file} radio/FM do not render empty or inactive mode buttons`, async () => {
    const f = fixture(file);
    try {
      f.music.playSong.type = "radio"; await vue.nextTick();
      assert.equal(f.controls().length, 3); assert.ok(f.controls().every(n => n.props["aria-label"]));
      f.status.personalFmMode = true; await vue.nextTick();
      assert.equal(f.controls().length, 3);
      assert.equal(f.controls()[0].props["aria-label"], file.includes("Mobile") ? "上一曲" : "不喜欢这首歌");
    } finally { f.dispose(); }
  });

  test(`R24-13 ${file} unmount releases directive listeners before a fresh mount`, () => {
    const first = fixture(file); const previous = first.controls()[1];
    first.dispose();
    assert.equal(previous.listeners.get("click")?.size || 0, 0);
    const second = fixture(file);
    try {
      second.click(second.controls()[1]); assert.deepEqual(second.calls, ["prev"]);
      assert.deepEqual(first.calls, []);
    } finally { second.dispose(); }
  });

  test(`R24-10 ${file} actual global shortcut cannot duplicate a local activation`, (t) => {
    t.mock.timers.enable({ apis: ["setTimeout", "Date"] });
    const f = fixture(file);
    try {
      const [shuffle] = f.controls();
      const local = f.key(shuffle, " ");
      if (!local.stopped) f.globalKey(local.event);
      f.click(shuffle); t.mock.timers.tick(101);
      assert.deepEqual(f.calls, ["shuffle"]);
      // The same unchanged consumer still dispatches the configurable shortcut outside controls.
      f.globalKey({ ...local.event, target: { tagName: "DIV" } }); t.mock.timers.tick(101);
      assert.deepEqual(f.calls, ["shuffle", "play"]);
    } finally { f.dispose(); }
  });
}

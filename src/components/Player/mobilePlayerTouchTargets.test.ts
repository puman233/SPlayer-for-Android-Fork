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
function fixture(pageCount = 2) {
  const file = "./MobilePlayerBottomControls.vue";
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
  const updates: number[] = [];
  const props = vue.reactive({ pageIndex: 0, pageCount, "onUpdate:pageIndex": (value: number) => { updates.push(value); props.pageIndex = value; } });
  const app = renderer.createApp({ setup: () => () => vue.h(component, props) });
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
  const pages = () => all().filter(n => /^切换到第/.test(String(n.props["aria-label"] || "")));
  return { pages, updates, props, controls, all, click, key, calls, status, music, globalKey, dispose: () => { app.unmount(); globalKey.cancel(); } };
}


for (const count of [1, 2, 3]) {
  test(`R23 T04/T10 ${count} pages render independently named native buttons`, () => {
    const f = fixture(count);
    try {
      assert.equal(f.pages().length, count);
      for (const button of f.pages()) assert.equal(button.tag, "button");
      assert.equal(f.pages()[0].props["aria-current"], "page");
    } finally { f.dispose(); }
  });
  test(`R23 T08 ${count} pages emit one update per actual click`, async () => {
    const f = fixture(count);
    try {
      for (let i = 0; i < count; i++) {
        f.click(f.pages()[i]); await vue.nextTick();
        assert.deepEqual(f.updates, Array.from({ length: i + 1 }, (_, index) => index));
        assert.equal(f.pages()[i].props["aria-current"], "page");
      }
    } finally { f.dispose(); }
  });
}
test("R23 T09 pagination keeps native activation and does not duplicate configurable global Space", (t) => {
  t.mock.timers.enable({ apis: ["setTimeout", "Date"] });
  const f = fixture(3);
  try {
    const button = f.pages()[1];
    for (const value of ["Enter", " "]) {
      const key = f.key(button, value);
      assert.equal(key.stopped, true); assert.equal(key.prevented, false);
      assert.deepEqual(f.updates, [], "no manual keydown page activation before native click");
      if (!key.stopped) f.globalKey(key.event);
    }
    f.click(button); t.mock.timers.tick(101);
    assert.deepEqual(f.updates, [1]); assert.deepEqual(f.calls, []);
  } finally { f.dispose(); }
});
test("R23 T10 page availability changes preserve exactly one active marker", async () => {
  const f = fixture(3);
  try {
    f.click(f.pages()[2]); await vue.nextTick();
    f.props.pageCount = 1; f.props.pageIndex = 0; await vue.nextTick();
    assert.equal(f.pages().length, 1); assert.equal(f.pages()[0].props["aria-current"], "page");
    f.props.pageCount = 2; await vue.nextTick();
    assert.equal(f.pages().length, 2); assert.equal(f.pages().filter(p => p.props["aria-current"] === "page").length, 1);
  } finally { f.dispose(); }
});

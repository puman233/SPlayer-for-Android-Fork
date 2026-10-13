import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import test from "node:test";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";
import * as vue from "vue";
import { routeLocationKey } from "vue-router";

// Time, browser events and layout are system adapters; the actual composable and Vue run unchanged.
function fixture(mode: "handle" | "longpress" = "longpress") {
  class Events {
    listeners = new Map<string, Set<(event: any) => void>>();
    addEventListener(type: string, fn: (event: any) => void) {
      if (!this.listeners.has(type)) this.listeners.set(type, new Set());
      this.listeners.get(type)!.add(fn);
    }
    removeEventListener(type: string, fn: (event: any) => void) {
      this.listeners.get(type)?.delete(fn);
    }
    emit(type: string, event: any = {}) {
      for (const fn of [...(this.listeners.get(type) || [])]) fn(event);
    }
    get count() { return [...this.listeners.values()].reduce((sum, set) => sum + set.size, 0); }
  }
  const window = new Events();
  const document = Object.assign(new Events(), { visibilityState: "visible" });
  let now = 0;
  let id = 0;
  const timers = new Map<number, { at: number; fn: () => void }>();
  const frames = new Map<number, () => void>();
  let scrollTop = 0;
  const songs = ["A", "B", "C", "D", "E"];
  const reordered: number[][] = [];
  let reorderError: Error | null = null;
  let afterReorder: (() => void) | null = null;
  const module = { exports: {} as any };
  runInNewContext(transpileModule(readFileSync(new URL("./useDragSort.ts", import.meta.url), "utf8"), {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText, {
    ...vue, module, exports: module.exports, require: (name: string) => name === "vue-router" ? { routeLocationKey } : vue, window, document,
    navigator: { vibrate() {} },
    setTimeout: (fn: () => void, delay: number) => {
      timers.set(++id, { at: now + delay, fn }); return id;
    },
    clearTimeout: (key: number) => timers.delete(key),
    requestAnimationFrame: (fn: () => void) => { frames.set(++id, fn); return id; },
    cancelAnimationFrame: (key: number) => frames.delete(key),
  });
  const virtualScrollRef = vue.ref({
    wrapperRef: { getBoundingClientRect: () => ({ top: 0, bottom: 200 }) },
    getScrollTop: () => scrollTop,
    scrollTo: (top: number) => { scrollTop = top; },
    getDropInfoByOffset: (offset: number) => ({
      index: Math.min(4, Math.max(0, Math.floor(offset / 40))),
      position: offset % 40 < 20 ? "top" : "bottom",
    }),
  });
  type Node = { type: string; children: Node[]; parent?: Node; props: Record<string, any> };
  const node = (type: string): Node => ({ type, children: [], props: {} });
  const renderer = vue.createRenderer<Node, Node>({
    createElement: node, createText: () => node("text"), createComment: () => node("comment"),
    insert(child, parent, anchor) {
      if (child.parent) {
        const old = child.parent.children.indexOf(child);
        if (old >= 0) child.parent.children.splice(old, 1);
      }
      child.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      if (index < 0) parent.children.push(child); else parent.children.splice(index, 0, child);
    },
    remove(child) {
      const siblings = child.parent?.children;
      if (siblings) siblings.splice(siblings.indexOf(child), 1);
    },
    parentNode: child => child.parent || null,
    nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] || null,
    setText() {}, setElementText() {}, patchProp(el, key, _prev, value) { el.props[key] = value; },
  });
  let drag: any;
  const Consumer = { setup() {
    drag = module.exports.useDragSort({ virtualScrollRef, itemCount: vue.computed(() => songs.length),
      triggerMode: mode, onReorder(from: number, to: number) {
        reordered.push([from, to]);
        if (reorderError) throw reorderError;
        songs.splice(to, 0, songs.splice(from, 1)[0]);
        afterReorder?.();
      },
    });
    return () => vue.h("list", {}, [
      drag.dragLabelData.value ? vue.h("label") : null,
      drag.dropIndicator.index >= 0 ? vue.h("drop-line") : null,
    ]);
  } };
  const active = vue.ref(true);
  const route = vue.reactive({ fullPath: "/playlist?id=fixture" });
  const app = renderer.createApp({ setup: () => () => vue.h(vue.KeepAlive, {}, {
    default: () => active.value ? vue.h(Consumer) : vue.h({ render: () => vue.h("blank") }),
  }) });
  const root = node("root");
  app.provide(routeLocationKey, route as any);
  app.mount(root);
  const touch = (type: string, y = 60, identifier = 7, touches?: any[]) => {
    const point = { identifier, clientX: 60, clientY: y };
    return { type, cancelable: true, preventDefault() {},
      touches: touches ?? (type === "touchend" || type === "touchcancel" ? [] : [point]),
      changedTouches: [point],
    };
  };
  const mouse = (type: string, y = 60) => ({ type, clientX: 60, clientY: y,
    button: 0, cancelable: true, preventDefault() {} });
  const tick = (ms: number) => {
    now += ms;
    for (const [key, timer] of [...timers]) if (timer.at <= now) { timers.delete(key); timer.fn(); }
  };
  const frame = () => { const pending = [...frames]; frames.clear(); pending.forEach(([, fn]) => fn()); };
  const clean = async () => {
    await vue.nextTick();
    assert.equal(drag.isDragging.value, false);
    assert.equal(drag.draggedIndex.value, -1);
    assert.equal(drag.dropIndicator.index, -1);
    assert.equal(drag.dropIndicator.position, "none");
    assert.equal(drag.dragLabelData.value, null);
    assert.equal(root.children[0]?.children.some(n => n.type === "label" || n.type === "drop-line") ?? false, false);
    assert.equal(window.count + document.count, 0);
    assert.equal(timers.size + frames.size, 0);
  };
  return { drag, window, document, touch, mouse, tick, frame, clean, songs, reordered, timers, frames,
    get scrollTop() { return scrollTop; },
    down: (index = 1) => drag.handlePointerDown(touch("touchstart"), index, songs[index]),
    failReorder: () => { reorderError = new Error("fixture reorder failure"); },
    onReorder: (fn: () => void) => { afterReorder = fn; },
    deactivate: async () => { active.value = false; await vue.nextTick(); },
    reactivate: async () => { active.value = true; await vue.nextTick(); },
    navigate: () => { route.fullPath = "/"; },
    dispose: () => app.unmount(),
  };
}

test("D01 normal longpress moves one song once and releases resources", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(299); assert.equal(f.drag.isDragging.value, false);
    f.tick(1); assert.equal(f.drag.isDragging.value, true);
    f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchend", f.touch("touchend", 130));
    assert.deepEqual(f.songs, ["A", "C", "B", "D", "E"]);
    assert.deepEqual(f.reordered, [[1, 2]]);
    await f.clean();
  } finally { f.dispose(); }
});

test("committed route change cancels before KeepAlive deactivation or leave animation", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 195));
    f.navigate();
    assert.equal(f.drag.isDragging.value, false);
    const before = f.scrollTop; f.frame(); f.frame();
    assert.equal(f.scrollTop, before); await f.clean();
    f.window.emit("touchend", f.touch("touchend")); assert.equal(f.reordered.length, 0);
  } finally { f.dispose(); }
});

for (const running of [false, true]) test(`KeepAlive route leave cancels ${running ? "active" : "pending"} drag and reentry works`, async () => {
  const f = fixture();
  try {
    f.down(); if (running) { f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 195)); }
    await f.deactivate(); f.tick(1000); f.frame(); await f.clean();
    f.down(); f.tick(1000); await f.clean();
    assert.equal(f.reordered.length, 0);
    await f.reactivate(); f.down(); f.tick(300);
    f.window.emit("touchmove", f.touch("touchmove", 130)); f.window.emit("touchend", f.touch("touchend"));
    assert.deepEqual(f.songs, ["A", "C", "B", "D", "E"]); await f.clean();
  } finally { f.dispose(); }
});

test("pending mouse longpress and the exact 5px threshold retain normal sorting", async () => {
  const f = fixture();
  try {
    f.drag.handlePointerDown(f.mouse("mousedown"), 1, "B");
    f.window.emit("mousemove", f.mouse("mousemove", 65)); f.tick(300);
    assert.equal(f.drag.isDragging.value, true);
    f.window.emit("mousemove", f.mouse("mousemove", 130));
    f.window.emit("mouseup", f.mouse("mouseup"));
    assert.deepEqual(f.songs, ["A", "C", "B", "D", "E"]); await f.clean();
  } finally { f.dispose(); }
});

test("already queued old timer and frame cannot affect a new session", async () => {
  const f = fixture();
  try {
    f.down(); const oldTimer = [...f.timers.values()][0].fn;
    f.window.emit("touchcancel", f.touch("touchcancel"));
    f.down(); oldTimer(); assert.equal(f.drag.isDragging.value, false);
    f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 195));
    const oldFrame = [...f.frames.values()][0];
    f.window.emit("touchcancel", f.touch("touchcancel"));
    f.down(); f.tick(300); const before = f.scrollTop;
    oldFrame(); assert.equal(f.scrollTop, before);
    f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchend", f.touch("touchend"));
    assert.equal(f.reordered.length, 1); await f.clean();
  } finally { f.dispose(); }
});

test("reorder callback may begin a new session without old cleanup destroying it", async () => {
  const f = fixture();
  try {
    f.onReorder(() => f.down());
    f.down(); f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchend", f.touch("touchend"));
    assert.equal(f.reordered.length, 1);
    f.tick(300); assert.equal(f.drag.isDragging.value, true);
    f.window.emit("touchcancel", f.touch("touchcancel"));
    await f.clean();
  } finally { f.dispose(); }
});

test("D02 touchcancel removes rendered feedback, stops scrolling and never reorders", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(300);
    f.window.emit("touchmove", f.touch("touchmove", 195)); f.frame();
    assert.ok(f.scrollTop > 0);
    f.window.emit("touchcancel", f.touch("touchcancel", 195));
    const stopped = f.scrollTop;
    f.frame(); f.frame();
    assert.equal(f.scrollTop, stopped);
    assert.equal(f.reordered.length, 0);
    assert.deepEqual(f.songs, ["A", "B", "C", "D", "E"]);
    await f.clean();
  } finally { f.dispose(); }
});

for (const [name, cancel] of [
  ["touchcancel", (f: ReturnType<typeof fixture>) => f.window.emit("touchcancel", f.touch("touchcancel"))],
  ["blur", (f: ReturnType<typeof fixture>) => f.window.emit("blur")],
  ["hidden", (f: ReturnType<typeof fixture>) => {
    f.document.visibilityState = "hidden"; f.document.emit("visibilitychange");
  }],
] as const) {
  for (const active of [false, true]) test(`D03/D06/D07 ${name} cancels ${active ? "active" : "pending"} session`, async () => {
    const f = fixture();
    try {
      f.down(); if (active) { f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 195)); }
      cancel(f); f.tick(1000); f.frame(); f.frame();
      assert.equal(f.reordered.length, 0); await f.clean();
    } finally { f.dispose(); }
  });
}

test("D03 pending movement beyond 5px cleans resources without activation", async () => {
  const f = fixture();
  try {
    f.down(); f.window.emit("touchmove", f.touch("touchmove", 66)); f.tick(1000);
    await f.clean(); assert.equal(f.reordered.length, 0);
  } finally { f.dispose(); }
});

test("D04/D08/D12 cancelled events do not revive state and a fresh session sorts", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchcancel", f.touch("touchcancel"));
    f.window.emit("touchcancel", f.touch("touchcancel"));
    f.window.emit("touchmove", f.touch("touchmove", 190));
    f.window.emit("touchend", f.touch("touchend"));
    assert.equal(f.reordered.length, 0); await f.clean();
    f.down(); f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchend", f.touch("touchend"));
    f.window.emit("touchend", f.touch("touchend"));
    assert.deepEqual(f.songs, ["A", "C", "B", "D", "E"]);
    assert.equal(f.reordered.length, 1); await f.clean();
  } finally { f.dispose(); }
});

for (const active of [false, true]) test(`D05 unmount cleans ${active ? "active" : "pending"} drag`, async () => {
  const f = fixture();
  f.down(); if (active) { f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 195)); }
  f.dispose(); f.tick(1000); f.frame();
  assert.equal(f.reordered.length, 0); await f.clean();
  f.down(); f.tick(1000); await f.clean();
});

test("D09 another finger ending cannot commit the active touch", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(300); f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchend", f.touch("touchend", 130, 8, [{ identifier: 7, clientX: 60, clientY: 130 }]));
    assert.equal(f.reordered.length, 0);
    f.window.emit("touchcancel", f.touch("touchcancel")); await f.clean();
  } finally { f.dispose(); }
});

test("D09 second touch cancels conservatively before pending or active movement", async () => {
  const f = fixture();
  try {
    f.down(); f.tick(300);
    f.window.emit("touchmove", f.touch("touchmove", 130));
    f.window.emit("touchstart", f.touch("touchstart", 60, 8, [
      { identifier: 7, clientX: 60, clientY: 60 }, { identifier: 8, clientX: 80, clientY: 60 },
    ]));
    await f.clean();
    f.window.emit("touchend", f.touch("touchend"));
    assert.equal(f.reordered.length, 0); await f.clean();
  } finally { f.dispose(); }
});

test("D11 mouse handle drag sorts once; unchanged release does not reorder", async () => {
  const f = fixture("handle");
  try {
    f.drag.handlePointerDown(f.mouse("mousedown"), 1, "B");
    f.window.emit("mousemove", f.mouse("mousemove", 130));
    f.window.emit("mouseup", f.mouse("mouseup"));
    assert.deepEqual(f.songs, ["A", "C", "B", "D", "E"]);
    await f.clean();
    f.drag.handlePointerDown(f.mouse("mousedown"), 1, "C");
    f.window.emit("mouseup", f.mouse("mouseup"));
    assert.equal(f.reordered.length, 1); await f.clean();
  } finally { f.dispose(); }
});

test("reorder failure still removes all resources and preserves the thrown failure", async () => {
  const f = fixture();
  try {
    f.failReorder(); f.down(); f.tick(300);
    f.window.emit("touchmove", f.touch("touchmove", 130));
    assert.throws(() => f.window.emit("touchend", f.touch("touchend")), /fixture reorder failure/);
    await f.clean();
    f.window.emit("touchend", f.touch("touchend"));
    assert.equal(f.reordered.length, 1);
  } finally { f.dispose(); }
});

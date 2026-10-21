import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { test } from "node:test";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";
import * as Vue from "vue";

type HostNode = {
  parent: HostNode | null;
  children: HostNode[];
  style: Record<string, string>;
  text: string;
  appendChild(child: HostNode): void;
  remove(): void;
};

const node = (): HostNode => ({
  parent: null, children: [], style: {}, text: "",
  appendChild(child) { child.remove(); child.parent = this; this.children.push(child); },
  remove() {
    if (this.parent) this.parent.children.splice(this.parent.children.indexOf(this), 1);
    this.parent = null;
  },
});

// The actual Vue lifecycle and Core factory run against a minimal host. The public
// renderer prop supplies a resource-tracking adapter instead of a GPU renderer.
function fixture() {
  const require = createRequire(import.meta.url);
  const corePath = require.resolve("@applemusic-like-lyrics/core");
  const coreModule = { exports: {} as Record<string, unknown> };
  runInNewContext(readFileSync(corePath, "utf8"), {
    module: coreModule, exports: coreModule.exports, require: createRequire(corePath),
    MouseEvent: class extends Event {}, EventTarget, Event, console, performance,
    document: { createElement: node },
  });
  const source = readFileSync(new URL("./BackgroundRender.vue", import.meta.url), "utf8");
  const { descriptor } = parse(source);
  const script = compileScript(descriptor, { id: "background-lifecycle", inlineTemplate: true });
  const compiled = transpileModule(script.content, {
    compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
  }).outputText;
  const componentModule = { exports: {} as { default?: Vue.Component } };
  runInNewContext(compiled, {
    ...Vue, module: componentModule, exports: componentModule.exports,
    require: (name: string) => name === "vue" ? Vue : coreModule.exports,
    setTimeout, clearTimeout,
  });
  const active = new Set<TrackingRenderer>();
  const created: TrackingRenderer[] = [];
  class TrackingRenderer {
    disposed = 0;
    paused = false;
    constructor() { active.add(this); created.push(this); }
    setAlbum() { return Promise.resolve(); }
    setFPS() {}
    setFlowSpeed() {}
    setRenderScale() {}
    setLowFreqVolume() {}
    setHasLyric() {}
    pause() { this.paused = true; }
    resume() { this.paused = false; }
    dispose() { this.disposed++; active.delete(this); }
  }
  const host = Vue.createRenderer<HostNode, HostNode>({
    createElement: node, createText: node, createComment: node,
    insert(child, parent, anchor = null) {
      child.remove(); child.parent = parent;
      const index = anchor ? parent.children.indexOf(anchor) : -1;
      parent.children.splice(index < 0 ? parent.children.length : index, 0, child);
    },
    remove: child => child.remove(),
    setText: (child, text) => { child.text = text; },
    setElementText: (child, text) => { child.text = text; },
    parentNode: child => child.parent,
    nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
    patchProp() {},
  });
  const mount = () => {
    assert.ok(componentModule.exports.default);
    const app = host.createApp(componentModule.exports.default, { renderer: TrackingRenderer });
    app.mount(node());
    return app;
  };
  return { mount, active, created };
}

test("background unmount releases its renderer exactly once", t => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const f = fixture();
  for (let i = 0; i < 4; i++) {
    const app = f.mount();
    assert.equal(f.active.size, 1);
    const renderer = f.created[i];
    assert.equal(renderer.paused, false);
    app.unmount();
    t.mock.timers.tick(1000);
    assert.equal(renderer.disposed, 1);
    assert.equal(f.active.size, 0);
  }
  t.mock.timers.tick(1000);
  assert.ok(f.created.every(renderer => renderer.disposed === 1));
});

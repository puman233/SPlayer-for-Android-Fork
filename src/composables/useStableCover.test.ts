import test from "node:test";
import assert from "node:assert/strict";
import { effectScope, ref, nextTick } from "vue";
import { useStableCover } from "./useStableCover.ts";

function fixture(urls = ["A"], active = true) {
  const sources = ref(urls),
    visible = ref(active),
    scope = effectScope();
  const state = scope.run(() =>
    useStableCover({ sources: () => sources.value, active: () => visible.value }),
  )!;
  const show = () => state.ready(state.pending.value!.key, async () => {});
  return { ...state, show, sources, visible, scope };
}

test("a replacement cannot remove the visible cover before image decoding completes", async () => {
  const f = fixture();
  try {
    await f.show();
    f.sources.value = ["B"];
    assert.equal(f.current.value?.source, "A");
    let decoded!: () => void;
    const pending = f.ready(
      f.pending.value!.key,
      () =>
        new Promise((resolve) => {
          decoded = resolve;
        }),
    );
    assert.equal(f.current.value?.source, "A");
    decoded();
    await pending;
    assert.equal(f.current.value?.source, "B");
  } finally {
    f.scope.stop();
  }
});

test("failed images and empty refresh data retain the last successfully decoded frame", async () => {
  const f = fixture();
  try {
    await f.show();
    f.sources.value = ["B"];
    await f.ready(f.pending.value!.key, async () => {
      throw Error("decode failed");
    });
    assert.equal(f.current.value?.source, "A");
    f.sources.value = [];
    assert.equal(f.current.value?.source, "A");
    f.sources.value = ["C"];
    f.failed(f.pending.value!.key);
    assert.equal(f.current.value?.source, "A");
  } finally {
    f.scope.stop();
  }
});

test("late decode callbacks cannot replace a newer refresh or update a disposed card", async () => {
  const f = fixture();
  await f.show();
  f.sources.value = ["B"];
  let decoded!: () => void;
  const old = f.ready(
    f.pending.value!.key,
    () =>
      new Promise((resolve) => {
        decoded = resolve;
      }),
  );
  f.sources.value = ["C"];
  await f.show();
  decoded();
  await old;
  assert.equal(f.current.value?.source, "C");
  f.sources.value = ["D"];
  const last = f.ready(
    f.pending.value!.key,
    () =>
      new Promise((resolve) => {
        decoded = resolve;
      }),
  );
  f.scope.stop();
  decoded();
  await last;
  assert.equal(f.current.value?.source, "C");
});

test("visible cards rotate after eight seconds, deduplicate albums, pause when hidden and clean up timers", async (t) => {
  t.mock.timers.enable({ apis: ["setInterval"] });
  const f = fixture(["A", "A", "B"]);
  await f.show();
  t.mock.timers.tick(7999);
  assert.equal(f.pending.value, undefined);
  t.mock.timers.tick(1);
  assert.equal(f.pending.value?.source, "B");
  await f.show();
  assert.equal(f.current.value?.source, "B");
  f.visible.value = false;
  await nextTick();
  t.mock.timers.tick(16000);
  assert.equal(f.pending.value, undefined);
  f.visible.value = true;
  await nextTick();
  t.mock.timers.tick(8000);
  assert.equal(f.pending.value?.source, "A");
  f.scope.stop();
  t.mock.timers.tick(16000);
  assert.equal(f.pending.value, undefined);
});

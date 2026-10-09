import test from "node:test";
import assert from "node:assert/strict";
import { ensureFloatingLyricVisible, type FloatingLyricState } from "./floatingLyricLifecycle";

test("missing service restores only after the real window attaches", async () => {
  const states: FloatingLyricState[] = [
    { visible: false, granted: true },
    { visible: false, granted: true },
    { visible: true, granted: true },
  ];
  let starts = 0;
  assert.equal(await ensureFloatingLyricVisible({
    getFloatingLyricState: async () => states.shift()!,
    showFloatingLyric: async () => { starts++; },
  }, async () => {}), true);
  assert.equal(starts, 1);
});

test("existing overlay is retained without another service start", async () => {
  assert.equal(await ensureFloatingLyricVisible({
    getFloatingLyricState: async () => ({ visible: true, granted: true }),
    showFloatingLyric: async () => { throw new Error("must not restart"); },
  }), true);
});

test("permission loss and failed window attachment never report enabled", async () => {
  let starts = 0;
  const api = {
    getFloatingLyricState: async () => ({ visible: false, granted: false }),
    showFloatingLyric: async () => { starts++; },
  };
  assert.equal(await ensureFloatingLyricVisible(api, async () => {}), false);
  assert.equal(starts, 0);
  assert.equal(await ensureFloatingLyricVisible({
    ...api,
    getFloatingLyricState: async () => ({ visible: false, granted: true }),
  }, async () => {}), false);
});

test("service start rejection propagates to the controller error path", async () => {
  await assert.rejects(ensureFloatingLyricVisible({
    getFloatingLyricState: async () => ({ visible: false, granted: true }),
    showFloatingLyric: async () => { throw new Error("start denied"); },
  }), /start denied/);
});

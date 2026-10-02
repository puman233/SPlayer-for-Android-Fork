import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { resolveAvailableViewport, resolveScaledViewport } from "./viewport.ts";

describe("viewport", () => {
  it("优先使用 visualViewport 的实际可见区域", () => {
    assert.deepEqual(
      resolveAvailableViewport({
        layoutWidth: 360,
        layoutHeight: 640,
        visualWidth: 360,
        visualHeight: 420,
      }),
      { width: 360, height: 420 },
    );
  });

  it("visualViewport 不可用时回退布局视口", () => {
    assert.deepEqual(
      resolveAvailableViewport({
        layoutWidth: 1097,
        layoutHeight: 617,
        visualWidth: 0,
        visualHeight: Number.NaN,
      }),
      { width: 1097, height: 617 },
    );
  });

  it("页面缩放后仍覆盖同一实际视口", () => {
    assert.deepEqual(resolveScaledViewport(1097, 617, 125), {
      ratio: 1.25,
      cssWidth: 877.6,
      cssHeight: 493.6,
    });
  });

  it("限制异常缩放值", () => {
    assert.deepEqual(resolveScaledViewport(360, 640, 0), {
      ratio: 1,
      cssWidth: 360,
      cssHeight: 640,
    });
    assert.equal(resolveScaledViewport(360, 640, 400).ratio, 2);
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { normalizeFontScale, resolveAdaptiveLayout, resolveAdaptiveTokens } from "./adaptive.ts";

describe("自适应空间策略", () => {
  it("宽高等级边界独立，不依赖硬件或朝向", () => {
    for (const [width, expected] of [
      [599, "compact"],
      [600, "medium"],
      [839, "medium"],
      [840, "expanded"],
    ] as const) {
      assert.equal(resolveAdaptiveLayout(width, 1000).widthClass, expected);
    }
    for (const [height, expected] of [
      [479, "compact"],
      [480, "medium"],
      [899, "medium"],
      [900, "expanded"],
    ] as const) {
      assert.equal(resolveAdaptiveLayout(1000, height).heightClass, expected);
    }
    assert.equal(resolveAdaptiveLayout(900, 1200).canUseTwoColumns, true);
  });
  it("分屏变窄、可见区域变矮时回退单栏", () => {
    assert.equal(resolveAdaptiveLayout(1098, 618).canUseTwoColumns, true);
    assert.equal(resolveAdaptiveLayout(550, 618).canUseTwoColumns, false);
    assert.equal(resolveAdaptiveLayout(1098, 360).canUseTwoColumns, false);
    assert.equal(resolveAdaptiveLayout(618, 1098).canUseTwoColumns, false);
  });
  it("保留强制模式且不允许强制平板挤爆窄窗口", () => {
    assert.equal(resolveAdaptiveLayout(1098, 618, "phone").canUseTwoColumns, false);
    assert.equal(resolveAdaptiveLayout(600, 618, "pad").canUseTwoColumns, true);
    assert.equal(resolveAdaptiveLayout(599, 618, "pad").canUseTwoColumns, false);
    assert.equal(resolveAdaptiveLayout(900, 479, "pad").canUseTwoColumns, false);
  });
  it("异常尺寸不能产生双栏或非有限比例", () => {
    const result = resolveAdaptiveLayout(Number.NaN, Number.POSITIVE_INFINITY);
    assert.deepEqual(
      [result.width, result.height, result.aspectRatio, result.canUseTwoColumns],
      [0, 0, 0, false],
    );
    assert.equal(resolveAdaptiveLayout(-1, 0).canUseTwoColumns, false);
  });
  it("间距有限调整，触控与字体不会随窗口无限放大", () => {
    const compact = resolveAdaptiveTokens("compact");
    const expanded = resolveAdaptiveTokens("expanded");
    assert.equal(compact.horizontalPadding, 16);
    assert.equal(expanded.horizontalPadding, 24);
    assert.equal(compact.control.minimumTouchTarget, 48);
    assert.deepEqual(compact.typography, expanded.typography);
    assert.deepEqual(compact.icon, expanded.icon);
  });
  it("保留有效系统字号并防御异常值", () => {
    for (const scale of [0.85, 1, 1.15, 1.3, 1.5, 2])
      assert.equal(normalizeFontScale(scale), scale);
    for (const scale of [0, -1, Number.NaN, Number.POSITIVE_INFINITY])
      assert.equal(normalizeFontScale(scale), 1);
  });
});

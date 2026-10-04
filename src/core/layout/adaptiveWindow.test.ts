import assert from "node:assert/strict";
import { it } from "node:test";
import { resolveAdaptiveWindow } from "./adaptiveWindow.ts";

it("安全区和窗口变化决定可用空间与分类", () => {
  const phone = resolveAdaptiveWindow(800, 400, { top: 20, right: 30, bottom: 20, left: 30 }, 1.3);
  assert.equal(phone.availableWidth, 740);
  assert.equal(phone.availableHeight, 360);
  assert.equal(phone.widthClass, "medium");
  assert.equal(phone.heightClass, "compact");
  assert.equal(phone.orientation, "landscape");
  assert.equal(phone.fontScale, 1.3);
  const resized = resolveAdaptiveWindow(400, 800, phone.insets);
  assert.equal(resized.orientation, "portrait");
  assert.equal(resized.widthClass, "compact");
  assert.equal(resolveAdaptiveWindow(1000, 700, phone.insets).widthClass, "expanded");
});

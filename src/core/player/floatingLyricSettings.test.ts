import assert from "node:assert/strict";
import { test } from "node:test";
import { migrateFloatingLyricSettings, floatingLyricPayload } from "./floatingLyricSettings";

test("首次设置使用自动字号与统一蓝色", () => {
  const config = migrateFloatingLyricSettings();
  assert.equal(config.fontSizeMode, "AUTO_DEFAULT");
  assert.equal(config.playedColor, "#6BB2FF");
  assert.equal(floatingLyricPayload(config).fontSizeMode, "AUTO_DEFAULT");
});

test("旧配置即使等于旧默认值也保留为手动字号", () => {
  for (const size of [16, 24, 48]) {
    const config = migrateFloatingLyricSettings({ fontSize: size, playedColor: "#fe7971" });
    assert.equal(config.fontSize, size);
    assert.equal(config.fontSizeMode, "USER_DEFINED");
    assert.equal(config.playedColor, "#fe7971");
    assert.deepEqual(migrateFloatingLyricSettings(config), config);
  }
});

test("已选择自动模式不会因缓存字号而转回手动", () => {
  const config = migrateFloatingLyricSettings({ fontSize: 30, fontSizeMode: "AUTO_DEFAULT" });
  assert.equal(config.fontSizeMode, "AUTO_DEFAULT");
});

test("损坏字号回退且不丢失颜色", () => {
  const config = migrateFloatingLyricSettings({ fontSize: NaN, playedColor: "#123456" });
  assert.equal(config.fontSizeMode, "AUTO_DEFAULT");
  assert.equal(config.playedColor, "#123456");
  assert.ok(Number.isFinite(config.fontSize));
});

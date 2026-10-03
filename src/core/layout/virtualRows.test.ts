import { test } from "node:test";
import assert from "node:assert/strict";
import { findVisibleRowIndex } from "./virtualRows";

test("滚入末尾留白时不能回到首行触发全量渲染", () => {
  const tops = Array.from({ length: 200 }, (_, i) => i * 170);
  assert.equal(
    findVisibleRowIndex(
      tops,
      tops.map(() => 170),
      34100,
    ),
    199,
  );
});

test("字号或宽度改变行高后，旧偏移超过新总高仍保持末尾范围", () => {
  assert.equal(findVisibleRowIndex([0, 90, 210], [90, 120, 60], 500), 2);
});

test("不同行高与行边界使用实际定位表", () => {
  const tops = [0, 90, 260];
  const heights = [90, 170, 120];
  assert.equal(findVisibleRowIndex(tops, heights, 89), 0);
  assert.equal(findVisibleRowIndex(tops, heights, 90), 1);
  assert.equal(findVisibleRowIndex(tops, heights, 259), 1);
  assert.equal(findVisibleRowIndex(tops, heights, 260), 2);
});

test("空列表和无效偏移安全回退", () => {
  assert.equal(findVisibleRowIndex([], [], 0), 0);
  assert.equal(findVisibleRowIndex([0], [48], Number.NaN), 0);
  assert.equal(findVisibleRowIndex([0], [48], -1), 0);
});

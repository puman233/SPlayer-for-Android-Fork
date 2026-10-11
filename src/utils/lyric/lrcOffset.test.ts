import test from "node:test";
import assert from "node:assert/strict";
import { parseLrc } from "./parseLrc";
import { parseSmartLrc } from "./lyricParser";

test("R28: a positive global offset advances ordinary LRC line and word timing", () => {
  const input = "[offset:1000]\n[00:02.000]fixture\n[00:04.000]next";
  const line = parseLrc(input)[0];
  assert.equal(line.startTime, 1000); assert.equal(line.words[0].startTime, 1000);
  assert.equal(line.endTime, 3000); assert.equal(parseSmartLrc(input).lines[0].startTime, 1000);
});

test("R28: negative offset delays lines, including their synthetic final end", () => {
  const line = parseLrc("[offset:-500]\n[00:01.000]fixture")[0];
  assert.equal(line.startTime, 1500); assert.equal(line.endTime, 11500);
});

test("R28: last valid offset is global even when declared after timestamps", () => {
  const line = parseLrc("[offset:100]\n[00:02.000]fixture\n[offset:500]\n[offset:invalid]")[0];
  assert.equal(line.startTime, 1500); assert.equal(line.words[0].word, "fixture");
});

test("R28: clamping does not merge distinct early lines into translations", () => {
  const lines = parseLrc("[offset:3000]\n[00:01.000]one\n[00:02.000]two\n[00:04.000]three");
  assert.equal(lines.length, 3); assert.equal(lines[0].startTime, 0); assert.equal(lines[1].startTime, 0);
  assert.equal(lines[0].translatedLyric, ""); assert.equal(lines[1].translatedLyric, "");
  assert.equal(lines[2].startTime, 1000);
});

test("R28: metadata offset preserves simultaneous translation and romanization", () => {
  const lines = parseLrc("[offset:500]\n[00:02.000][00:04.000]main\n[00:02.000]translation\n[00:02.000]roman");
  assert.equal(lines.length, 2); assert.equal(lines[0].translatedLyric, "translation");
  assert.equal(lines[0].romanLyric, "roman"); assert.equal(lines[1].startTime, 3500);
});

test("R28: invalid and absent offsets preserve original timing", () => {
  for (const metadata of ["", "[offset:NaN]", "[offset:999999999999999999999999999]"]) {
    assert.equal(parseLrc(metadata + "\n[00:02.000]fixture")[0].startTime, 2000);
  }
});

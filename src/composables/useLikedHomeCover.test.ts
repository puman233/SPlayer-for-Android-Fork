import test from "node:test";
import assert from "node:assert/strict";
import { effectScope, reactive } from "vue";
import { useLikedHomeCover } from "./useLikedHomeCover.ts";

test("a late album response cannot restore the previous account's home artwork", async () => {
  const state = reactive({ account: "A", id: 101 });
  const replies: Array<(url: string[]) => void> = [];
  const scope = effectScope();
  const cover = scope.run(() =>
    useLikedHomeCover({
      account: () => state.account,
      playlistId: () => state.id,
      cachedCovers: () => [],
      load: () => new Promise((resolve) => replies.push(resolve)),
    }),
  )!;
  state.account = "B";
  replies[1](["https://example.test/B.jpg"]);
  await Promise.resolve();
  assert.deepEqual(cover.value, ["https://example.test/B.jpg"]);
  replies[0](["https://example.test/A.jpg"]);
  await Promise.resolve();
  assert.deepEqual(cover.value, ["https://example.test/B.jpg"]);
  state.id = 0;
  assert.deepEqual(cover.value, []);
  scope.stop();
});

test("cached album artwork avoids a request and survives offline operation", () => {
  const scope = effectScope();
  let requests = 0;
  const cover = scope.run(() =>
    useLikedHomeCover({
      account: () => "A",
      playlistId: () => 101,
      cachedCovers: () => ["https://example.test/album.jpg"],
      load: async () => {
        requests++;
        throw Error("must not request cached artwork");
      },
    }),
  )!;
  assert.deepEqual(cover.value, ["https://example.test/album.jpg"]);
  assert.equal(requests, 0);
  scope.stop();
});

test("a response arriving after home disposal cannot change its artwork", async () => {
  let finish!: (url: string[]) => void;
  const scope = effectScope();
  const cover = scope.run(() =>
    useLikedHomeCover({
      account: () => "A",
      playlistId: () => 101,
      cachedCovers: () => [],
      load: () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    }),
  )!;
  scope.stop();
  finish(["https://example.test/late.jpg"]);
  await Promise.resolve();
  assert.deepEqual(cover.value, []);
});

test("an empty liked playlist uses default artwork without introducing a heart image", async () => {
  const scope = effectScope();
  const cover = scope.run(() =>
    useLikedHomeCover({
      account: () => "A",
      playlistId: () => 101,
      cachedCovers: () => [],
      load: async () => [],
    }),
  )!;
  await Promise.resolve();
  assert.deepEqual(cover.value, []);
  scope.stop();
});

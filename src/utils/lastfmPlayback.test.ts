import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";

function fixture() {
  let now = 1000;
  let timerId = 0;
  const timers = new Map<number, { at: number; callback: () => void }>();
  const submissions: string[] = [];
  const settings = { lastfm: { enabled: true, sessionKey: "fixture-session", scrobbleEnabled: true, nowPlayingEnabled: false } };
  const replies: Array<Promise<unknown>> = [];
  const module = { exports: {} as any };
  const source = readFileSync(new URL("./lastfmScrobbler.ts", import.meta.url), "utf8");
  runInNewContext(transpileModule(source, { compilerOptions: { module: ModuleKind.CommonJS } }).outputText, {
    module, exports: module.exports,
    require: (name: string) => name === "@/stores" ? { useSettingStore: () => settings }
      : name === "@/api/lastfm" ? { updateNowPlaying: async () => {}, scrobbleTrack: async (_key: string, name: string) => { submissions.push(name); await replies.shift(); } }
      : { requestFailureCategory: () => "network" },
    Date: class extends Date { static now() { return now; } },
    setTimeout: (callback: () => void, delay: number) => { const id = ++timerId; timers.set(id, { at: now + delay, callback }); return id; },
    clearTimeout: (id: number) => timers.delete(id),
    console: { log() {}, error() {} },
  });
  const advance = async (ms: number) => {
    const target = now + ms;
    for (;;) {
      const due = [...timers].filter(([, t]) => t.at <= target).sort((a, b) => a[1].at - b[1].at)[0];
      if (!due) break;
      now = due[1].at; timers.delete(due[0]); due[1].callback(); await Promise.resolve(); await Promise.resolve();
    }
    now = target; await Promise.resolve(); await Promise.resolve();
  };
  return { player: module.exports.default, submissions, advance, replies, settings };
}

test("R11: a long pause does not satisfy the effective listening threshold", async () => {
  const f = fixture();
  f.player.startPlaying("A", "fixture", undefined, 300);
  await f.advance(1000); f.player.pause();
  await f.advance(180000); f.player.resume(); await f.advance(0);
  assert.equal(f.submissions.length, 0);
  await f.advance(148999); assert.equal(f.submissions.length, 0);
  await f.advance(1); assert.deepEqual(f.submissions, ["A"]);
});

test("R12: a previous track's late success cannot suppress the new track's submission", async () => {
  const f = fixture();
  let finish!: () => void;
  f.replies.push(new Promise<void>(resolve => { finish = resolve; }));
  f.player.startPlaying("A", "fixture", undefined, 60); await f.advance(30000);
  f.player.startPlaying("B", "fixture", undefined, 60);
  finish(); await f.advance(0); await f.advance(30000);
  assert.deepEqual(f.submissions, ["A", "B"]);
});

test("R11: restoring a paused track must not accumulate time before the first play event", async () => {
  const f = fixture(); f.player.startPlaying("A", "fixture", undefined, 60, false);
  await f.advance(180000); assert.equal(f.submissions.length, 0);
  f.player.resume(); await f.advance(29999); assert.equal(f.submissions.length, 0);
  await f.advance(1); assert.deepEqual(f.submissions, ["A"]);
});

test("R12: changing credentials cannot submit the old playback to the new Last.fm account", async () => {
  const f = fixture(); f.player.startPlaying("A", "fixture", undefined, 60);
  f.settings.lastfm.sessionKey = "fixture-new-account";
  await f.advance(30000); assert.equal(f.submissions.length, 0);
});

test("R12: repeated resume and stop while a submission is pending never duplicate it", async () => {
  const f = fixture(); let finish!: () => void;
  f.replies.push(new Promise<void>(resolve => { finish = resolve; }));
  f.player.startPlaying("A", "fixture", undefined, 60); await f.advance(30000);
  f.player.pause(); f.player.resume(); f.player.resume(); await f.advance(0); f.player.stop();
  assert.deepEqual(f.submissions, ["A"]); finish(); await f.advance(0);
});

test("R11: repeated pauses and stop do not count the paused interval twice", async () => {
  const f = fixture(); f.player.startPlaying("A", "fixture", undefined, 60);
  await f.advance(10000); f.player.pause(); await f.advance(180000); f.player.pause(); f.player.stop();
  assert.equal(f.submissions.length, 0);
});

test("R12: old request failure cannot inhibit a new track", async () => {
  const f = fixture(); let fail!: (error: Error) => void;
  f.replies.push(new Promise<void>((_resolve, reject) => { fail = reject; }));
  f.player.startPlaying("A", "fixture", undefined, 60); await f.advance(30000);
  f.player.startPlaying("B", "fixture", undefined, 60); fail(Error("fixture-network"));
  await f.advance(0); await f.advance(30000); assert.deepEqual(f.submissions, ["A", "B"]);
});

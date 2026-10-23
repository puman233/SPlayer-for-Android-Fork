import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind, ScriptTarget } from "typescript";

function fixture(login = 1, local = false, initialQueue?: any[]) {
  const status = { shuffleMode: "off", playIndex: 0 };
  const music = {
    playSong: { id: 5, name: "fixture", type: "song", ...(local ? { path: "/fixture.wav" } : {}) },
    playPlaylistId: 0,
  };
  let list: any[] = initialQueue || [music.playSong];
  let original: any[] = [];
  let nextWrite: Promise<void> | undefined;
  let originalRead: Promise<any[]> | undefined;
  const queueCommits: any[] = [];
  const messages: string[] = [],
    seeds: number[] = [];
  const loggedIn = login;
  let response: any = { code: 200, data: [{ id: 11, name: "recommendation" }] };
  let resolveResponse: ((value: any) => void) | undefined,
    rejectResponse: ((error: Error) => void) | undefined;
  const data = {
    get playList() {
      return list;
    },
    getUserLikePlaylist: async () => ({ detail: { id: 99 }, data: [{ id: 101 }] }),
    setOriginalPlayList: async (v: any[]) => {
      original = v;
    },
    getOriginalPlayList: async () => originalRead ?? original,
    clearOriginalPlayList: async () => {
      original = [];
    },
    setPlayList: async (v: any[]) => {
      list = v;
      const pending = nextWrite;
      nextWrite = undefined;
      await pending;
    },
  };
  const noop = () => {};
  const imports: Record<string, any> = {
    "@/utils/requestDiagnostics": { requestFailureCategory: () => "fixture" },
    "@/api/playlist": {
      heartRateList: async (seed: number) => {
        seeds.push(seed);
        if (response === "deferred")
          return new Promise((resolve, reject) => {
            resolveResponse = resolve;
            rejectResponse = reject;
          });
        if (response instanceof Error) throw response;
        return response;
      },
    },
    "@/stores": {
      useStatusStore: () => status,
      useMusicStore: () => music,
      useDataStore: () => data,
    },
    "@/utils/auth": { isLogin: () => loggedIn },
    "@/utils/env": { isElectron: false },
    "@/utils/format": { formatSongsList: (v: any[]) => v },
    "@/utils/helper": { shuffleArray: (v: any[]) => v.slice().reverse() },
    "@/utils/modal": { openUserLogin: () => messages.push("login") },
    axios: { default: { isCancel: () => false } },
    "./PlayerIpc": {},
  };
  const module = { exports: {} as any };
  runInNewContext(
    transpileModule(readFileSync(new URL("./PlayModeManager.ts", import.meta.url), "utf8"), {
      compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
    }).outputText,
    {
      module,
      exports: module.exports,
      require: (key: string) => imports[key] || {},
      AbortController,
      console: { log: noop, error: noop, warn: noop },
      window: {
        $message: {
          loading: () => ({ destroy: noop }),
          success: (v: string) => messages.push(v),
          warning: (v: string) => messages.push(v),
          error: (v: string) => messages.push(v),
        },
      },
    },
  );
  const manager = new module.exports.PlayModeManager(async () => {
    queueCommits.push({ list: list.map(song => song.id), index: status.playIndex, song: music.playSong.id });
  });
  return {
    manager,
    status,
    messages,
    seeds,
    music, queueCommits,
    delayNextWrite: (promise: Promise<void>) => { nextWrite = promise; },
    delayOriginalRead: (promise?: Promise<any[]>) => { originalRead = promise; },
    list: () => list,
    respond: (v: any) => {
      response = v;
    },
    reject: (e: Error) => rejectResponse?.(e),
    resolve: (v: any) => resolveResponse?.(v),
  };
}

test("the shared mode command cycles off, shuffle on, heartbeat and off", () => {
  const f = fixture();
  let mode = "off";
  const sequence = [mode];
  for (let i = 0; i < 4; i++) {
    mode = f.manager.calculateNextShuffleMode(mode);
    sequence.push(mode);
  }
  assert.deepEqual(sequence, ["off", "on", "heartbeat", "off", "on"]);
});

test("heart mode can enter, exit and enter again through the existing public command", async () => {
  const f = fixture();
  await f.manager.toggleShuffle("heartbeat");
  assert.equal(f.status.shuffleMode, "heartbeat");
  assert.equal(f.list().length, 2);
  await f.manager.toggleShuffle("off");
  assert.equal(f.status.shuffleMode, "off");
  await f.manager.toggleShuffle("heartbeat");
  assert.equal(f.status.shuffleMode, "heartbeat");
  assert.equal(f.seeds.length, 2);
});

test("cycling shuffle to heart and off restores the original ordered queue", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  await f.manager.toggleShuffle("on");
  assert.deepEqual(
    Array.from(f.list(), (song: any) => song.id),
    [7, 6, 5],
  );
  await f.manager.toggleShuffle("heartbeat");
  await f.manager.toggleShuffle("off");
  assert.deepEqual(
    Array.from(f.list(), (song: any) => song.id),
    [5, 6, 7],
  );
});
test("guest, empty recommendations and network failures do not report heart mode success", async () => {
  for (const failure of ["guest", "empty", "network"]) {
    const f = fixture(failure === "guest" ? 0 : 1);
    if (failure === "empty") f.respond({ code: 200, data: [] });
    if (failure === "network") f.respond(new Error("offline"));
    await f.manager.toggleShuffle("heartbeat");
    assert.equal(f.status.shuffleMode, "off");
    assert.equal(
      f.messages.some((v) => v === "心动模式已开启"),
      false,
    );
  }
});
test("a local song uses a liked online seed instead of its unrelated file identifier", async () => {
  const f = fixture(1, true);
  await f.manager.toggleShuffle("heartbeat");
  assert.equal(f.seeds[0], 101);
});

test("a failed heart transition still lets the next cycle turn shuffle off", async () => {
  for (const failure of ["guest", "empty", "network"]) {
    const f = fixture(failure === "guest" ? 0 : 1);
    f.status.shuffleMode = "on";
    if (failure === "empty") f.respond({ code: 200, data: [] });
    if (failure === "network") f.respond(new Error("offline"));
    await f.manager.toggleShuffle(f.manager.calculateNextShuffleMode(f.status.shuffleMode));
    assert.equal(f.status.shuffleMode, "on");
    await f.manager.toggleShuffle(f.manager.calculateNextShuffleMode(f.status.shuffleMode));
    assert.equal(f.status.shuffleMode, "off");
  }
});
test("an aborted heart request cannot roll a newer explicit off mode back to shuffle on", async () => {
  const f = fixture();
  f.status.shuffleMode = "on";
  f.respond("deferred");
  const pending = f.manager.toggleShuffle("heartbeat");
  for (let i = 0; i < 20 && !f.seeds.length; i++) await Promise.resolve();
  assert.equal(f.seeds.length, 1, "the old HTTP request must actually be in flight");
  await f.manager.toggleShuffle("off");
  f.reject(new Error("aborted"));
  await pending;
  assert.equal(f.status.shuffleMode, "off");
});

test("a committed shuffled queue publishes an aligned current track before persistence finishes", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  let release!: () => void;
  f.delayNextWrite(new Promise<void>(resolve => { release = resolve; }));
  const pending = f.manager.toggleShuffle("on");
  for (let i = 0; i < 20; i++) await Promise.resolve();
  const committed = f.queueCommits.at(-1);
  release(); await pending;
  assert.ok(committed, "The native queue must receive the actual mode commit");
  assert.equal(committed.list[committed.index], committed.song);
  assert.deepEqual(Array.from(committed.list), [7, 6, 5]);
});

test("an obsolete off read cannot replace a newer heart queue", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  await f.manager.toggleShuffle("on");
  let release!: (value: any[]) => void;
  f.delayOriginalRead(new Promise<any[]>(resolve => { release = resolve; }));
  const older = f.manager.toggleShuffle("off");
  await Promise.resolve();
  f.delayOriginalRead(undefined);
  await f.manager.toggleShuffle("heartbeat");
  const latest = Array.from(f.list(), song => song.id);
  release([{ id: 5 }, { id: 6 }, { id: 7 }]); await older;
  assert.equal(f.status.shuffleMode, "heartbeat");
  assert.deepEqual(Array.from(f.list(), song => song.id), latest);
});

test("late shuffle persistence cannot overwrite the index of a newer heart queue", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  let release!: () => void;
  f.delayNextWrite(new Promise<void>(resolve => { release = resolve; }));
  const older = f.manager.toggleShuffle("on");
  for (let i = 0; i < 20; i++) await Promise.resolve();
  f.music.playSong = { id: 6, name: "advanced", type: "song" };
  await f.manager.toggleShuffle("heartbeat");
  release(); await older;
  assert.equal(f.status.shuffleMode, "heartbeat");
  assert.equal(f.list()[f.status.playIndex].id, f.music.playSong.id);
});

test("leaving heart mode keeps its actually playing recommendation before the restored original order", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  await f.manager.toggleShuffle("heartbeat");
  f.music.playSong = { id: 11, name: "recommendation", type: "song" };
  await f.manager.toggleShuffle("off");
  assert.equal(f.list()[f.status.playIndex].id, 11);
  assert.deepEqual(Array.from(f.list(), song => song.id), [11, 5, 6, 7]);
});

test("direct heart to shuffle transitions retain the original off order", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  for (const mode of ["on", "heartbeat", "on", "heartbeat", "on", "off"]) await f.manager.toggleShuffle(mode);
  assert.deepEqual(Array.from(f.list(), song => song.id), [5, 6, 7]);
});

test("rapid overlapping mode commands cannot replace the saved original order with a shuffled snapshot", async () => {
  const f = fixture(1, false, [{ id: 5 }, { id: 6 }, { id: 7 }]);
  await f.manager.toggleShuffle("on");
  await Promise.all(["off", "on", "heartbeat", "off"].map(mode => f.manager.toggleShuffle(mode)));
  assert.equal(f.status.shuffleMode, "off");
  assert.deepEqual(Array.from(f.list(), song => song.id), [5, 6, 7]);
});

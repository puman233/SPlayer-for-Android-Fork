import assert from "node:assert/strict";
import { it } from "node:test";
import { createEmbeddedRecovery, createNetworkFailureNotice } from "./requestRecovery.ts";

it("并发超时只提示一次，其他接口成功不解除故障", () => {
  const notice = createNetworkFailureNotice();
  assert.equal(notice.fail("lyrics"), true);
  assert.equal(notice.fail("comments"), false);
  notice.success("home");
  assert.equal(notice.fail("lyrics"), false);
  notice.success("lyrics");
  assert.equal(notice.fail("comments"), false);
  notice.success("comments");
  assert.equal(notice.fail("lyrics"), true);
});

it("本地服务健康时外网超时不重载", async () => {
  let reloads = 0;
  const recover = createEmbeddedRecovery(
    async () => true,
    async () => {
      reloads++;
      return true;
    },
  );
  assert.equal(await recover(), false);
  assert.equal(reloads, 0);
});

it("并发故障合并健康检查与热重载", async () => {
  let checks = 0,
    reloads = 0;
  let release!: (value: boolean) => void;
  const gate = new Promise<boolean>((resolve) => {
    release = resolve;
  });
  const recover = createEmbeddedRecovery(
    async () => {
      checks++;
      return gate;
    },
    async () => {
      reloads++;
      return true;
    },
  );
  const first = recover(),
    second = recover();
  assert.equal(first, second);
  release(false);
  assert.deepEqual(await Promise.all([first, second]), [true, true]);
  assert.equal(checks, 1);
  assert.equal(reloads, 1);
});

it("恢复失败有冷却，冷却结束允许再次尝试", async () => {
  let time = 0,
    reloads = 0;
  const recover = createEmbeddedRecovery(
    async () => false,
    async () => {
      reloads++;
      return false;
    },
    () => time,
  );
  assert.equal(await recover(), false);
  time = 59999;
  assert.equal(await recover(), false);
  assert.equal(reloads, 1);
  time = 60000;
  assert.equal(await recover(), false);
  assert.equal(reloads, 2);
});

it("健康检查异常也不会产生无界恢复循环", async () => {
  let checks = 0;
  const recover = createEmbeddedRecovery(
    async () => {
      checks++;
      throw new Error("offline");
    },
    async () => true,
    () => 1,
  );
  assert.equal(await recover(), false);
  assert.equal(await recover(), false);
  assert.equal(checks, 1);
});

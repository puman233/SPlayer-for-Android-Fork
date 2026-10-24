import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { transpileModule, ModuleKind } from "typescript";
import { ref } from "vue";

function fixture() {
  let unmount!: () => void;
  const module = { exports: {} as any };
  runInNewContext(
    transpileModule(readFileSync(new URL("./useCategoryRequest.ts", import.meta.url), "utf8"), {
      compilerOptions: { module: ModuleKind.CommonJS },
    }).outputText,
    {
      module,
      exports: module.exports,
      require: () => ({
        ref,
        onBeforeUnmount: (fn: () => void) => {
          unmount = fn;
        },
      }),
    },
  );
  return { ...module.exports.useCategoryRequest(), unmount: () => unmount() };
}
const deferred = () => {
  let resolve!: (value: number) => void;
  const promise = new Promise<number>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};
test("快速切换筛选只接受最新结果，旧请求不能结束新请求加载状态", async () => {
  const f = fixture(),
    old = deferred(),
    latest = deferred();
  const results: number[] = [];
  const first = f.run(
    () => old.promise,
    (v: number) => results.push(v),
  );
  const second = f.run(
    () => latest.promise,
    (v: number) => results.push(v),
  );
  old.resolve(1);
  await first;
  assert.equal(f.loading.value, true);
  assert.deepEqual(results, []);
  latest.resolve(2);
  await second;
  assert.deepEqual(results, [2]);
  assert.equal(f.loading.value, false);
});
test("失败可重试，卸载后的请求不能写回结果", async () => {
  const f = fixture();
  let value = 0;
  await f.run(
    () => Promise.reject(new Error("offline")),
    () => {
      value++;
    },
  );
  assert.ok(f.error.value);
  assert.equal(f.loading.value, false);
  await f.run(
    () => Promise.resolve(2),
    (v: number) => {
      value = v;
    },
  );
  assert.equal(f.error.value, "");
  assert.equal(value, 2);
  const delayed = deferred();
  const pending = f.run(
    () => delayed.promise,
    (v: number) => {
      value = v;
    },
  );
  f.unmount();
  delayed.resolve(3);
  await pending;
  assert.equal(value, 2);
});

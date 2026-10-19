import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { createPinia, defineStore } from "pinia";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";

test("a fresh setting store keeps Beta updates opt-in without changing another profile", () => {
  const module = { exports: {} as Record<string, (...args: unknown[]) => any> };
  const imports: Record<string, unknown> = {
    pinia: { defineStore },
    "@/core/player/SongManager": { SongUnlockServer: {} },
    "@/utils/meta": { defaultAMLLDbServer: "https://example.invalid" },
    "./migrations/settingMigrations": { CURRENT_SETTING_SCHEMA_VERSION: 0, settingMigrations: {} },
  };
  runInNewContext(
    transpileModule(readFileSync(new URL("./setting.ts", import.meta.url), "utf8"), {
      compilerOptions: { module: ModuleKind.CommonJS, target: ScriptTarget.ES2022 },
    }).outputText,
    { module, exports: module.exports, require: (key: string) => imports[key] ?? {}, localStorage: {} },
  );
  const optedIn = module.exports.useSettingStore(createPinia());
  assert.equal(optedIn.checkBetaUpdates, false);
  optedIn.checkBetaUpdates = true;
  assert.equal(module.exports.useSettingStore(createPinia()).checkBetaUpdates, false);
  assert.equal(optedIn.checkBetaUpdates, true);
});

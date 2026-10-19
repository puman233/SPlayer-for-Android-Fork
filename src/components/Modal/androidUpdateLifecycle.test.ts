import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";
import * as vue from "vue";

test("closing the update window during native version lookup cannot attach listeners later", async () => {
  let resolveInfo!: (value: unknown) => void;
  const info = new Promise((resolve) => {
    resolveInfo = resolve;
  });
  let attached = 0;
  const nativeListener = async () => {
    attached++;
    return {
      remove: async () => {
        attached--;
      },
    };
  };
  const imports: Record<string, unknown> = {
    vue,
    "@capacitor/app": { App: { getInfo: () => info, addListener: nativeListener } },
    "@/../package.json": { default: { version: "3.0.16-beta.6" } },
    "@/plugins/androidAppUpdate": { AndroidAppUpdate: { addListener: nativeListener } },
    "@/core/update/changelog": { sanitizeUpdateHtml: () => "" },
    "@/core/update/assets": {},
    "@/utils/helper": {},
    "@capacitor/core": {},
  };
  const descriptor = parse(
    readFileSync(new URL("./AndroidUpdateApp.vue", import.meta.url), "utf8"),
  ).descriptor;
  const source = compileScript(descriptor, { id: "update-lifecycle" }).content;
  const module = { exports: {} as any };
  runInNewContext(
    transpileModule(source, {
      compilerOptions: {
        module: ModuleKind.CommonJS,
        target: ScriptTarget.ES2022,
        esModuleInterop: false,
      },
    }).outputText,
    {
      module,
      exports: module.exports,
      require: (key: string) => imports[key] ?? {},
      console,
      window: { $message: { success() {}, error() {} } },
    },
  );
  const renderer = vue.createRenderer<object, object>({
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    insert() {},
    remove() {},
    parentNode: () => null,
    nextSibling: () => null,
    patchProp() {},
    setText() {},
    setElementText() {},
  });
  const component = module.exports.default;
  component.render = () => null;
  const app = renderer.createApp(component, {
    data: {
      version: "v3.1.0",
      changelog: "",
      url: "https://github.com/owner/repo",
      prerelease: false,
    },
  });
  app.mount({});
  app.unmount();
  resolveInfo({ id: "top.imsyy.splayer.android", version: "3.0.16-beta.6" });
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.equal(attached, 0, "an unmounted window must not retain native listeners");
});

import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { ModuleKind, ScriptTarget, transpileModule } from "typescript";
import { filterUpdateReleases } from "../core/update/version";

// Real exported request function; only HTTP/platform adapters vary at the agreed update seam.
function fixture(response: unknown, error?: Error) {
  const module = { exports: {} as { updateLog: () => Promise<unknown> } };
  const source = readFileSync(new URL("./other.ts", import.meta.url), "utf8");
  const rateError = error;
  const imports: Record<string, unknown> = {
    "@/utils/request": {
      default: async (options: unknown) => {
        if (rateError) throw rateError;
        return typeof response === "function" ? response(options) : response;
      },
    },
    "@/config/repository": {
      ANDROID_RELEASES_API_URL: "https://api.github.com/repos/owner/repo/releases",
    },
    "@/utils/env": { isCapacitorAndroid: true },
    axios: { default: { isAxiosError: () => true } },
    "@capacitor/core": { CapacitorHttp: { get: async () => ({ status: 200, data: "<feed />" }) } },
  };
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
      require: (name: string) => imports[name],
      DOMParser: class {
        parseFromString() {
          return { querySelector: () => null, querySelectorAll: () => [] };
        }
      },
    },
  );
  return module.exports;
}

test("rate limits never turn an Atom feed without channel metadata into stable release data", async () => {
  const error = Object.assign(new Error("rate limit"), { response: { status: 403 } });
  await assert.rejects(fixture(null, error).updateLog(), /rate limit/);
});

test("a stable release beyond a full Beta page remains discoverable", async () => {
  const release = (tag_name: string, prerelease: boolean) => ({
    tag_name,
    prerelease,
    draft: false,
    body: "release",
    html_url: "https://github.com/owner/repo/releases/tag/" + tag_name,
    published_at: "2026-10-16T00:00:00Z",
  });
  const result = await fixture((options: { params: { page?: number } }) =>
    options.params.page === 2
      ? [release("v3.0.15", false)]
      : Array.from({ length: 20 }, (_, i) => release("v3.0.16-beta." + (i + 1), true)),
  ).updateLog();
  const stable = filterUpdateReleases(
    result as Array<{ tag_name: string; prerelease: boolean; draft: boolean }>,
  );
  assert.deepEqual(
    Array.from(stable, (r) => r.tag_name),
    ["v3.0.15"],
  );
});

test("malformed release API responses produce an explicit error", async () => {
  await assert.rejects(fixture({ message: "bad payload" }).updateLog(), /响应格式/);
  await assert.rejects(
    fixture([
      { tag_name: "v3.1.0", body: "release", html_url: "https://github.com/owner/repo" },
    ]).updateLog(),
    /响应格式/,
  );
});

test("authoritative draft and prerelease flags survive the HTTP adapter unchanged", async () => {
  const releases = [
    {
      tag_name: "v3.1.0",
      body: "Release",
      published_at: "2026-10-16T00:00:00Z",
      html_url: "https://github.com/owner/repo/releases/tag/v3.1.0",
      prerelease: true,
      draft: false,
    },
  ];
  // Transfer across the VM's realm just as the HTTP JSON adapter does.
  assert.deepEqual(JSON.parse(JSON.stringify(await fixture(releases).updateLog())), releases);
});

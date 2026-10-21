import { test } from "node:test";
import assert from "node:assert/strict";
import { detectCountry, parseCountry, COUNTRY_URL } from "../src/services/region.ts";
import { formatBytes } from "../src/utils/format.ts";

test("only exact ISO country codes are accepted, without inferring China from locale", () => {
  assert.equal(parseCountry({ country: "CN", ip: "not-retained" }), "CN");
  for (const country of ["HK", "MO", "TW", "US"]) assert.equal(parseCountry({ country }), country);
  for (const payload of [
    null,
    [],
    { country: "cn" },
    { country: "China" },
    { country: 86 },
    { timezone: "Asia/Shanghai" },
  ])
    assert.equal(parseCountry(payload), null);
});
test("country lookup omits credentials and referrer, and returns country only", async () => {
  const country = await detectCountry(async (url, options) => {
    assert.equal(url, COUNTRY_URL);
    assert.equal(options.credentials, "omit");
    assert.equal(options.referrerPolicy, "no-referrer");
    return { ok: true, json: async () => ({ country: "CN", ip: "discarded" }) };
  });
  assert.equal(country, "CN");
});
test("offline, HTTP failure and invalid payload degrade to unknown country", async () => {
  for (const request of [
    async () => {
      throw Error("offline");
    },
    async () => ({ ok: false }),
    async () => ({ ok: true, json: async () => ({ country: null }) }),
  ])
    assert.equal(await detectCountry(request), null);
});
test("country lookup aborts an unresponsive request", async () => {
  assert.equal(
    await detectCountry(
      (_url, { signal }) =>
        new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () => reject(Error("timeout")), { once: true });
        }),
    ),
    null,
  );
});
test("binary byte counts use MiB rather than decimal MB", () => {
  assert.equal(formatBytes(1048576), "1.0 MiB");
});

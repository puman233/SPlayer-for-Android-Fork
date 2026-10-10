import test from "node:test";
import assert from "node:assert/strict";
import { isAllowedUpdateUrl, sanitizeUpdateHtml } from "./changelog";

test("update notes reject executable, embedded and credential-bearing URL schemes", () => {
  for (const url of ["javascript:alert(1)", "java\nscript:alert(1)", "data:text/html,<script>1</script>", "data:image/svg+xml,<svg/>", "intent://fixture", "file:///private", "//evil.example", "https://user:password@example.org/"])
    assert.equal(isAllowedUpdateUrl(url), false, url);
  assert.equal(isAllowedUpdateUrl("https://github.com/puman233/SPlayer-for-Android-Fork/releases"), true);
  assert.equal(isAllowedUpdateUrl("http://example.org/image.png", true), true);
  assert.equal(isAllowedUpdateUrl("#heading"), true);
  assert.equal(isAllowedUpdateUrl("#heading", true), false);
});

test("HTML rendering fails closed when no browser DOM is available", () => {
  assert.equal(sanitizeUpdateHtml("<img onerror='alert(1)'>"), "");
});

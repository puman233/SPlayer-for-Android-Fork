import { requestFailureCategory } from "../src/utils/requestDiagnostics";

// Installed API dependencies log reflected response bodies themselves, before callers
// can catch them. Keep a closed diagnostic boundary before loading those dependencies.
const labels = new Set([
  "[embedded-api] uncaughtException", "[embedded-api] unhandledRejection",
  "[embedded-api] Fetch TTML lyric failed", "[embedded-api] Unblock request failed",
  "[embedded-api] Netease API request failed", "[embedded-api] reload failed",
  "[embedded-api] startEmbeddedApiServer failed", "[embedded-api] ready",
]);
const categories = new Set(["timeout", "cancelled", "network", "http", "unknown"]);

for (const level of ["log", "info", "warn", "error", "debug", "dir", "table", "trace"] as const) {
  if (typeof console[level] !== "function") continue;
  const write = console[level].bind(console);
  console[level] = (...args: unknown[]) => {
    const known = typeof args[0] === "string" && labels.has(args[0]);
    const category = known && typeof args[1] === "string" && categories.has(args[1])
      ? args[1] : requestFailureCategory(args.find(value => value && typeof value === "object"));
    // Never serialize an unknown string, error, response, configuration or cause.
    if (level === "dir" || level === "table" || level === "trace") {
      console.debug("[API dependency diagnostic]", category);
    } else {
      write(known ? args[0] : "[API dependency diagnostic]", category);
    }
  };
}

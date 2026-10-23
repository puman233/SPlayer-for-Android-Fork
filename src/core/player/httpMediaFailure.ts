const TRANSIENT_HTTP_STATUSES = new Set([408, 429, 500, 502, 503, 504]);

/** Positive HTTP evidence only; CORS/timeout/format failures remain unclassified. */
export async function probeTransientHttpFailure(
  url: string,
  signal: AbortSignal,
  fetcher: typeof fetch = fetch,
  timeoutMs = 2000,
): Promise<boolean> {
  if (signal.aborted || !/^https?:\/\//i.test(url)) return false;
  const request = new AbortController();
  const abort = () => request.abort();
  signal.addEventListener("abort", abort, { once: true });
  const timeout = setTimeout(abort, timeoutMs);
  try {
    const response = await fetcher(url, {
      method: "GET",
      headers: { Range: "bytes=0-0" },
      // Match anonymous HTML media: no cross-origin cookies or authorization.
      credentials: "same-origin",
      cache: "no-store",
      signal: request.signal,
    });
    void response.body?.cancel().catch(() => {});
    return !signal.aborted && !request.signal.aborted
      && response.type !== "opaque" && TRANSIENT_HTTP_STATUSES.has(response.status);
  } catch {
    return false;
  } finally {
    clearTimeout(timeout);
    signal.removeEventListener("abort", abort);
    // Stop IO even when a server ignores Range and starts a full response.
    request.abort();
  }
}

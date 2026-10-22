/** Test-only browser orchestration; no dependency or code from this file ships. */
import { execFileSync } from "node:child_process";
export function createBrowserDriver() {
  const binary = process.env.AGENT_BROWSER_BIN ?? "agent-browser";
  const session = process.env.BROWSER_SESSION ?? "splayer-website-check";
  const site = process.env.BROWSER_SITE ?? "http://127.0.0.1:4174/SPlayer-for-Android-Fork/";
  function command(...args) {
    if (args[0] === "open") command("network", "route", "https://api.country.is/", "--abort");
    if (args[0] === "network" && args[1] === "route") command("network", "unroute", args[2]);
    const raw = execFileSync(binary, ["--session", session, "--json", ...args], {
      encoding: "utf8",
      timeout: 30000,
    });
    const result = JSON.parse(raw);
    if (!result.success) throw Error(JSON.stringify(result.error));
    return result.data;
  }
  const evaluate = (fn) => command("eval", `(${fn.toString()})()`).result;
  async function withPageInit(source, check) {
    const socket = new WebSocket(command("get", "cdp-url").cdpUrl);
    await new Promise((resolve, reject) => {
      socket.addEventListener("open", resolve, { once: true });
      socket.addEventListener("error", reject, { once: true });
    });
    let id = 0;
    const calls = new Map();
    socket.addEventListener("message", (event) => {
      const data = JSON.parse(event.data),
        entry = calls.get(data.id);
      if (entry) {
        clearTimeout(entry.timer);
        calls.delete(data.id);
        if (data.error) entry.reject(Error(JSON.stringify(data.error)));
        else entry.resolve(data.result);
      }
    });
    const rpc = (method, params = {}, sessionId) =>
      new Promise((resolve, reject) => {
        const requestId = ++id;
        const timer = setTimeout(() => {
          calls.delete(requestId);
          reject(Error(`CDP timeout: ${method}`));
        }, 10000);
        calls.set(requestId, { resolve, reject, timer });
        socket.send(
          JSON.stringify({ id: requestId, method, params, ...(sessionId ? { sessionId } : {}) }),
        );
      });
    let attached, init;
    try {
      const { targetInfos } = await rpc("Target.getTargets");
      const target = targetInfos.find((t) => t.type === "page" && t.url.startsWith(site));
      if (!target) throw Error("Isolated test target missing");
      attached = await rpc("Target.attachToTarget", { targetId: target.targetId, flatten: true });
      await rpc("Page.enable", {}, attached.sessionId);
      init = await rpc("Page.addScriptToEvaluateOnNewDocument", { source }, attached.sessionId);
      return await check(() => rpc("Page.navigate", { url: site }, attached.sessionId));
    } finally {
      if (init)
        await rpc(
          "Page.removeScriptToEvaluateOnNewDocument",
          { identifier: init.identifier },
          attached.sessionId,
        );
      if (attached) await rpc("Target.detachFromTarget", { sessionId: attached.sessionId });
      socket.close();
    }
  }
  return { command, evaluate, withPageInit, site };
}

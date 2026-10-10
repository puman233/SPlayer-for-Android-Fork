import { requestFailureCategory } from "@/utils/requestDiagnostics";

/** Capacitor debug packet tracing otherwise serializes plugin credentials and URLs. */
const bridge = (window as unknown as {
  Capacitor?: { logToNative?: (...args: unknown[]) => void; logFromNative?: (...args: unknown[]) => void; fromNative?: (...args: unknown[]) => unknown; toNative?: (...args: unknown[]) => unknown };
}).Capacitor;

if (bridge) {
  bridge.logToNative = () => console.debug("[Native bridge] request");
  bridge.logFromNative = () => console.debug("[Native bridge] response");
  const safeCallback = (callback: (...args: unknown[]) => unknown, receiver: unknown) =>
    (...args: unknown[]) => {
      // The SDK logs orphan results and synchronous callback exceptions directly,
      // even with packet tracing disabled. Preserve the payload and callback behavior.
      const output = window.console || console;
      const warn = output.warn, error = output.error;
      const safe = (write: (...values: unknown[]) => void) => (...values: unknown[]) => {
        // Exceptions may also be strings; no raw callback diagnostics are trusted.
        write.call(output, "[Native bridge] callback failure", requestFailureCategory(values.find(value => value !== null && typeof value === "object")));
      };
      output.warn = safe(warn); output.error = safe(error);
      try { return callback.apply(receiver, args); }
      finally { output.warn = warn; output.error = error; }
    };
  if (bridge.fromNative) bridge.fromNative = safeCallback(bridge.fromNative, bridge);
  if (bridge.toNative) bridge.toNative = safeCallback(bridge.toNative, bridge);
  // Android's message transport calls the SDK's private returnResult directly,
  // bypassing fromNative. Guard that actual transport without touching the event.
  const transport = (window as unknown as { androidBridge?: { onmessage?: (...args: unknown[]) => unknown } }).androidBridge;
  if (transport?.onmessage) transport.onmessage = safeCallback(transport.onmessage, transport);
}

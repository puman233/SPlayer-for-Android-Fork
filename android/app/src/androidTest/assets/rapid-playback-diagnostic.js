// Test-only timings at public WebView/Capacitor boundaries; never record arguments.
window.rapidTrace = [];
window.rapidTraceStart = Date.now();
window.rapidNativePromise = window.Capacitor.nativePromise;
window.Capacitor.nativePromise = function (plugin, method, options) {
  if (plugin !== "AndroidNativePlayback")
    return rapidNativePromise.apply(this, arguments);
  const entry = { method, start: Date.now() - rapidTraceStart };
  rapidTrace.push(entry);
  return rapidNativePromise.apply(this, arguments).then(
    value => { entry.end = Date.now() - rapidTraceStart; return value; },
    error => { entry.end = Date.now() - rapidTraceStart; entry.failed = true; throw error; },
  );
};
window.rapidLongTasks = [];
if (window.PerformanceObserver?.supportedEntryTypes.includes("longtask")) {
  window.rapidObserver = new PerformanceObserver(list => {
    for (const task of list.getEntries())
      rapidLongTasks.push({ start: performance.timeOrigin + task.startTime - rapidTraceStart, duration: task.duration });
  });
  rapidObserver.observe({ type: "longtask", buffered: false });
}

// Test-only timings at public WebView/Capacitor boundaries; never record arguments.
window.rapidTrace = [];
window.rapidTraceStart = Date.now();
window.rapidNativePromise = window.Capacitor.nativePromise;
window.Capacitor.nativePromise = function (plugin, method, _options) {
  if (plugin !== "AndroidNativePlayback")
    return window.rapidNativePromise.apply(this, arguments);
  const entry = { method, start: Date.now() - window.rapidTraceStart };
  window.rapidTrace.push(entry);
  return window.rapidNativePromise.apply(this, arguments).then(
    value => { entry.end = Date.now() - window.rapidTraceStart; return value; },
    error => { entry.end = Date.now() - window.rapidTraceStart; entry.failed = true; throw error; },
  );
};
window.rapidLongTasks = [];
if (window.PerformanceObserver?.supportedEntryTypes.includes("longtask")) {
  window.rapidObserver = new PerformanceObserver(list => {
    for (const task of list.getEntries())
      window.rapidLongTasks.push({ start: performance.timeOrigin + task.startTime - window.rapidTraceStart, duration: task.duration });
  });
  window.rapidObserver.observe({ type: "longtask", buffered: false });
}

window.rapidStorageSetItem = Storage.prototype.setItem;
Storage.prototype.setItem = function (key, _value) {
  if (!["status-store", "music-store", "setting-store"].includes(key))
    return window.rapidStorageSetItem.apply(this, arguments);
  const entry = { storage: key, start: Date.now() - window.rapidTraceStart };
  window.rapidTrace.push(entry);
  try {
    return window.rapidStorageSetItem.apply(this, arguments);
  } finally {
    entry.end = Date.now() - window.rapidTraceStart;
  }
};

// Controlled HTTP only: production controller, native queue, HTTP client and lyric parser.
window.prepareHttpLyricSequence = async (base, count = 21, duration = 4000, options = {}) => {
  await window.p7Init();
  const data = window.fixturePinia._s.get("data");
  window.httpSequenceSaved = { list: data.playList, original: data.originalPlayList, history: data.setHistory };
  data.setHistory = async () => {};
  // Clear the previous native window before redirecting its resolver context.
  // Otherwise updateContext can prefetch three unrelated saved-queue songs into
  // this scenario's server. The original window is rebuilt by restore below.
  await window.Capacitor.nativePromise("AndroidNativePlayback", "updateQueueContext", {
    windowTracks: [], windowCurrentIndex: -1,
    hasPreviousOutsideWindow: false, hasNextOutsideWindow: false, repeatMode: "off",
  });
  Object.assign(window.fixtureSetting, { useNextPrefetch: count > 1, lyricPriority: "official",
    enableOnlineTTMLLyric: false, enableQQMusicLyric: false, enableAutomix: false,
    cacheEnabled: false, useSongUnlock: false, songLevel: "standard", androidAllowMixWithOthers: options.allowMix ?? true });
  Object.assign(window.fixtureStatus, { repeatMode: "off", shuffleMode: "off", personalFmMode: false, playIndex: 0, showFullPlayer: false });
  if ((options.allowMix ?? true) && window.__SPLAYER_AUDIO_MANAGER__.engineType !== "android-native") {
    const resource = performance.getEntriesByType("resource").find((entry) => /\/stores-[^/]+\.js/.test(entry.name));
    if (!resource) throw Error("Built public audio module unavailable");
    const exports = Object.values(await import(resource.name));
    window.httpSequenceAudioGetter = exports.find((value) => typeof value === "function"
      && String(value).includes("playbackEngine") && String(value).includes("audioEngine") && String(value).includes("new "));
    if (!window.httpSequenceAudioGetter) throw Error("Built public audio getter unavailable");
    window.httpSequenceSaved.engine = window.__SPLAYER_AUDIO_MANAGER__.engineType;
    window.fixtureSetting.audioEngine = "element";
    window.__SPLAYER_AUDIO_MANAGER__.destroy();
    delete window.__SPLAYER_AUDIO_MANAGER__;
    window.httpSequenceAudioGetter();
    window.__SPLAYER_PLAYER_CONTROLLER__.rebindAudioEvents();
    if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== "android-native") throw Error("Actual native engine required");
  }
  window.httpSequenceOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    const original = new URL(String(url), location.href);
    if (/\/(lyric|song\/url)/.test(original.pathname)) {
      url = base + (original.pathname.includes("lyric") ? "/lyric" : "/song/url") + original.search;
    }
    return window.httpSequenceOpen.call(this, method, url, ...args);
  };
  window.httpSequenceNative = window.Capacitor.nativePromise;
  window.Capacitor.nativePromise = function (plugin, method, options) {
    // The production online resolver upgrades HTTP to HTTPS. This loopback fixture
    // has no TLS endpoint; map only this test server's URLs at the transport seam.
    if (plugin === "AndroidNativePlayback" && options) {
      const secureBase = base.replace(/^http:/, "https:");
      options = JSON.parse(JSON.stringify(options), (_, value) =>
        typeof value === "string" && value.startsWith(secureBase + "/")
          ? base + value.slice(secureBase.length) : value);
    }
    if (plugin === "AndroidNativePlayback" && method === "syncApiContext") {
      options = { ...options, apiBaseUrl: base, cookie: "" };
    }
    return window.httpSequenceNative.call(this, plugin, method, options);
  };
  // The production API sync can already be deduplicated before this fixture is
  // installed. Explicitly configure the actual native HTTP boundary as well.
  await window.Capacitor.nativePromise("AndroidNativePlayback", "syncApiContext", {
    apiBaseUrl: base, cookie: "", songLevel: "standard", disableAiAudio: false, playSongDemo: false,
  });
  data.playList = Array.from({ length: count }, (_, i) => ({ id: 991700 + i, type: "song",
    name: "HTTP sequence " + (991700 + i), duration, cover: "/images/song.jpg",
    artists: [{ id: 1, name: "Synthetic artist" }], album: { id: 1, name: "Synthetic album" } }));
  data.originalPlayList = data.playList.slice();
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  if (options.beforePlay) await options.beforePlay();
  await controller.setDesktopLyricShow(true);
  window.httpSequenceSelectedAt = Date.now();
  await controller.playSong({ song: data.playList[0], autoPlay: true });
  window.httpSequenceReady = true;
};
window.restoreHttpLyricSequence = async () => {
  await window.__SPLAYER_PLAYER_CONTROLLER__.pause();
  if (window.httpSequenceOpen) XMLHttpRequest.prototype.open = window.httpSequenceOpen;
  if (window.httpSequenceNative) window.Capacitor.nativePromise = window.httpSequenceNative;
  if (window.httpSequenceSaved) {
    const data = window.fixturePinia._s.get("data");
    data.playList = window.httpSequenceSaved.list;
    data.originalPlayList = window.httpSequenceSaved.original;
    data.setHistory = window.httpSequenceSaved.history;
  }
  if (window.httpSequenceSaved?.engine) {
    const playing = window.p7Saved.status.playStatus;
    window.p7Saved.status.playStatus = false;
    await window.p7Restore();
    window.__SPLAYER_AUDIO_MANAGER__.destroy();
    delete window.__SPLAYER_AUDIO_MANAGER__;
    window.httpSequenceAudioGetter();
    window.__SPLAYER_PLAYER_CONTROLLER__.rebindAudioEvents();
    if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== window.httpSequenceSaved.engine) throw Error("Original engine not restored");
    if (window.p7Saved.music.playSong.id) await window.__SPLAYER_PLAYER_CONTROLLER__.playSong({
      song: window.p7Saved.music.playSong, autoPlay: playing, seek: window.p7Saved.status.currentTime,
    });
  } else await window.p7Restore();
  await window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext();
};

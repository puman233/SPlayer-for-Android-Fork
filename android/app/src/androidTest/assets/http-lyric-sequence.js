// Controlled HTTP only: production controller, native queue, HTTP client and lyric parser.
window.prepareHttpLyricSequence = async (base, count = 21, duration = 4000) => {
  await window.p7Init();
  const data = window.fixturePinia._s.get("data");
  window.httpSequenceSaved = { list: data.playList, original: data.originalPlayList, history: data.setHistory };
  data.setHistory = async () => {};
  Object.assign(window.fixtureSetting, { useNextPrefetch: count > 1, lyricPriority: "official",
    enableOnlineTTMLLyric: false, enableQQMusicLyric: false, enableAutomix: false,
    cacheEnabled: false, useSongUnlock: false, songLevel: "standard", androidAllowMixWithOthers: true });
  Object.assign(window.fixtureStatus, { repeatMode: "off", shuffleMode: "off", personalFmMode: false, playIndex: 0, showFullPlayer: false });
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
  data.playList = Array.from({ length: count }, (_, i) => ({ id: 991700 + i, type: "song",
    name: "HTTP sequence " + (991700 + i), duration, cover: "/images/song.jpg",
    artists: [{ id: 1, name: "Synthetic artist" }], album: { id: 1, name: "Synthetic album" } }));
  data.originalPlayList = data.playList.slice();
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  await controller.setDesktopLyricShow(true);
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
  await window.p7Restore();
  await window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext();
};

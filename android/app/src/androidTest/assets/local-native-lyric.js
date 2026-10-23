// Select the real production native engine; retain the original paused transport.
window.prepareLocalNativeLyric = async () => {
  const resource = performance.getEntriesByType("resource").find((entry) => /\/stores-[^/]+\.js/.test(entry.name));
  if (!resource) throw Error("Built public audio module unavailable");
  const exports = Object.values(await import(resource.name));
  window.localLyricAudioGetter = exports.find((value) => typeof value === "function"
    && String(value).includes("playbackEngine") && String(value).includes("audioEngine") && String(value).includes("new "));
  if (!window.localLyricAudioGetter) throw Error("Public audio getter unavailable");
  window.localLyricTransport = {
    engine: window.__SPLAYER_AUDIO_MANAGER__.engineType,
    source: window.__SPLAYER_AUDIO_MANAGER__.src,
    position: window.fixtureStatus.currentTime,
    playing: window.fixtureStatus.playStatus,
    mix: window.fixtureSetting.androidAllowMixWithOthers,
    audioEngine: window.fixtureSetting.audioEngine,
  };
  await window.__SPLAYER_PLAYER_CONTROLLER__.pause();
  window.fixtureSetting.androidAllowMixWithOthers = true;
  window.fixtureSetting.audioEngine = "element";
  window.__SPLAYER_AUDIO_MANAGER__.destroy();
  delete window.__SPLAYER_AUDIO_MANAGER__;
  window.localLyricAudioGetter();
  window.__SPLAYER_PLAYER_CONTROLLER__.rebindAudioEvents();
  if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== "android-native") throw Error("Actual native engine required");
  window.localLyricNativeReady = true;
};
window.restoreLocalNativeLyric = async () => {
  if (!window.localLyricTransport) return;
  const saved = window.localLyricTransport;
  window.fixtureSetting.androidAllowMixWithOthers = saved.mix;
  window.fixtureSetting.audioEngine = saved.audioEngine;
  window.__SPLAYER_AUDIO_MANAGER__.destroy();
  delete window.__SPLAYER_AUDIO_MANAGER__;
  window.localLyricAudioGetter();
  window.__SPLAYER_PLAYER_CONTROLLER__.rebindAudioEvents();
  if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== saved.engine) throw Error("Original engine not restored");
  if (saved.source) await window.__SPLAYER_PLAYER_CONTROLLER__.loadAndPlay(saved.source, saved.playing, saved.position);
  else window.fixtureStatus.playStatus = saved.playing;
  window.localLyricNativeRestored = true;
};

// Auth and HTTP are controlled only inside the isolated debug package.
window.prepareModeSequence = async (base, duration) => {
  await window.prepareHttpLyricSequence(base, 6, duration);
  const data = window.fixturePinia._s.get("data");
  window.modeInitialSongs = data.playList.slice();
  window.modeSavedAuth = { login: data.userLoginStatus, type: data.loginType,
    cookie: localStorage.getItem("cookie-MUSIC_U") };
  data.userLoginStatus = true;
  data.loginType = "cookie";
  localStorage.setItem("cookie-MUSIC_U", "isolated-mode-fixture");
  window.fixturePinia._s.get("music").playPlaylistId = 991799;
  window.modeSavedOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...args) {
    const target = new URL(String(url), location.href);
    if (target.pathname.endsWith("/playmode/intelligence/list")) url = base + "/playmode/intelligence/list" + target.search;
    return window.modeSavedOpen.call(this, method, url, ...args);
  };
  window.modeTrace = [];
  window.modeSet = async (mode) => {
    await window.__SPLAYER_PLAYER_CONTROLLER__.toggleShuffle(mode);
    const music = window.fixturePinia._s.get("music");
    const snapshot = { mode: window.fixtureStatus.shuffleMode, list: data.playList.map(song => song.id),
      songId: music.playSong.id, index: window.fixtureStatus.playIndex };
    window.modeTrace.push(snapshot);
    return snapshot;
  };
  window.modeReady = true;
};
window.restoreModeSequence = async () => {
  const data = window.fixturePinia._s.get("data");
  if (window.modeSavedAuth) {
    data.userLoginStatus = window.modeSavedAuth.login;
    data.loginType = window.modeSavedAuth.type;
    if (window.modeSavedAuth.cookie === null) localStorage.removeItem("cookie-MUSIC_U");
    else localStorage.setItem("cookie-MUSIC_U", window.modeSavedAuth.cookie);
  }
  if (window.modeSavedOpen) XMLHttpRequest.prototype.open = window.modeSavedOpen;
  await window.restoreHttpLyricSequence();
  window.modeRestored = true;
};

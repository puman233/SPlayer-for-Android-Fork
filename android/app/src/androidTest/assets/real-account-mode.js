// Real account and real HTTP only. Never assign login state, cookies or user data.
window.prepareRealAccountMode = async () => {
  const p = window.realAccountPinia;
  const d = p._s.get("data"), s = p._s.get("status"), m = p._s.get("music"), settings = p._s.get("setting");
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  if (!window.realAccountWitness().loggedIn || !window.realAccountWitness().authenticated) throw Error("Real authenticated account required");
  window.realAccountSavedPlayback = {
    list: d.playList.slice(), original: (await d.getOriginalPlayList())?.slice() || [],
    song: m.playSong, playlistId: m.playPlaylistId, index: s.playIndex,
    mode: s.shuffleMode, repeat: s.repeatMode, overlay: s.showDesktopLyric,
    position: s.currentTime, playing: s.playStatus,
    mix: settings.androidAllowMixWithOthers, audioEngine: settings.audioEngine,
    engine: window.__SPLAYER_AUDIO_MANAGER__.engineType,
    rate: s.playRate,
  };
  const liked = await d.getUserLikePlaylist();
  const songs = liked?.data?.filter(song => song.type === "song" && !song.path).slice(0, 30);
  if (!songs || songs.length < 3) throw Error("Real liked playlist needs at least three online tracks");
  await controller.pause();
  if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== "android-native") {
    const resource = performance.getEntriesByType("resource").find(e => /\/stores-[^/]+\.js/.test(e.name));
    if (!resource) throw Error("Public audio module unavailable");
    window.realAccountAudioGetter = Object.values(await import(resource.name)).find(value => typeof value === "function"
      && String(value).includes("playbackEngine") && String(value).includes("audioEngine") && String(value).includes("new "));
    if (!window.realAccountAudioGetter) throw Error("Public audio getter unavailable");
    settings.androidAllowMixWithOthers = true; settings.audioEngine = "element";
    window.__SPLAYER_AUDIO_MANAGER__.destroy(); delete window.__SPLAYER_AUDIO_MANAGER__;
    window.realAccountAudioGetter(); controller.rebindAudioEvents();
  }
  if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== "android-native") throw Error("Actual native engine required");
  window.realAccountModeTrace = [];
  window.realAccountHeartResponses = [];
  const requests = new WeakMap();
  window.realAccountXhrOpen = XMLHttpRequest.prototype.open;
  window.realAccountXhrSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
    requests.set(this, new URL(String(url), location.href).pathname.includes("/playmode/intelligence/list"));
    return window.realAccountXhrOpen.call(this, method, url, ...rest);
  };
  XMLHttpRequest.prototype.send = function(...args) {
    if (requests.get(this)) this.addEventListener("loadend", () => {
      let body;
      try { body = this.responseType === "json" ? this.response : JSON.parse(this.responseText); } catch { /* no payload */ }
      window.realAccountHeartResponses.push({ status: this.status, code: body?.code,
        ids: body?.data?.map(item => item.songInfo?.id).filter(Boolean) || [] });
    }, { once: true });
    return window.realAccountXhrSend.apply(this, args);
  };
  window.realAccountSnapshot = () => ({
    mode: s.shuffleMode, list: d.playList.map(song => song.id), index: s.playIndex,
    songId: m.playSong.id, loading: s.playLoading, lyricLoading: s.lyricLoading,
    lyrics: m.songLyric.lrcData.slice(0, 3).map(line => line.words.map(word => word.word).join("")),
    engine: window.__SPLAYER_AUDIO_MANAGER__.engineType, account: window.realAccountWitness(),
  });
  window.realAccountSetMode = async mode => {
    await controller.toggleShuffle(mode);
    const snapshot = window.realAccountSnapshot();
    window.realAccountModeTrace.push(snapshot);
    if (snapshot.mode !== mode) throw Error("Real mode command did not commit");
    return snapshot;
  };
  await controller.toggleShuffle("off");
  controller.toggleRepeat("off");
  await controller.setDesktopLyricShow(true);
  await controller.updatePlayList(songs, songs.find(song => song.id === m.playSong.id) || songs[0],
    Number(liked.detail.id), { play: true, showTip: false });
  controller.setRate(2);
  window.realAccountModeReady = true;
};
window.restoreRealAccountPlayback = async () => {
  const saved = window.realAccountSavedPlayback;
  const p = window.realAccountPinia, d = p._s.get("data"), s = p._s.get("status"), settings = p._s.get("setting");
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  if (window.realAccountXhrOpen) XMLHttpRequest.prototype.open = window.realAccountXhrOpen;
  if (window.realAccountXhrSend) XMLHttpRequest.prototype.send = window.realAccountXhrSend;
  if (!saved) return;
  await controller.pause();
  await controller.toggleShuffle(saved.mode);
  controller.toggleRepeat(saved.repeat);
  settings.androidAllowMixWithOthers = saved.mix; settings.audioEngine = saved.audioEngine;
  if (window.realAccountAudioGetter) {
    window.__SPLAYER_AUDIO_MANAGER__.destroy(); delete window.__SPLAYER_AUDIO_MANAGER__;
    window.realAccountAudioGetter(); controller.rebindAudioEvents();
  }
  await controller.updatePlayList(saved.list, saved.song, saved.playlistId,
    { play: false, showTip: false, keepHeartbeatMode: true });
  // Restore the entry queue exactly after the normal mode command's reordering.
  await d.setPlayList(saved.list); s.playIndex = saved.index;
  await d.setOriginalPlayList(saved.original);
  await controller.syncAndroidPlaybackContext();
  if (saved.song?.id && saved.list.length) {
    await controller.playSong({ song: saved.song, autoPlay: saved.playing, seek: saved.position });
  }
  controller.setRate(Number.isFinite(saved.rate) ? saved.rate : 1);
  await controller.setDesktopLyricShow(saved.overlay);
  window.realAccountPlaybackRestored = true;
};

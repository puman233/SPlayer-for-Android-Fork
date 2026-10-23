// Real account and real HTTP only. Never assign login state, cookies or user data.
window.prepareRealAccountMode = async () => {
  window.realAccountPlaybackChanged = false;
  window.realAccountPreparationCancelled = false;
  window.realAccountPreparationStage = 'saving-playback';
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
  window.realAccountPreparationStage = 'loading-request-interface';
  const resource = performance.getEntriesByType('resource').find(e=>/\/stores-[^/]+\.js/.test(e.name));
  const exports = Object.values(await import(resource.name));
  window.realAccountRequest = exports.find(value=>typeof value==='function' && String(value).includes('_splayerCookieSnapshot'));
  if (!window.realAccountRequest) throw Error('Application request interface unavailable');
  let liked = await d.getUserLikePlaylist();
  if (!liked?.data?.length) {
    window.realAccountPreparationStage = 'reading-real-playlist';
    // A newly logged-in installation need not have a populated liked-song DB.
    // Read the actual account playlist without persisting or exporting its cookie.
    const lists = await window.realAccountRequest({url:'/user/playlist',params:{limit:50,offset:0,uid:d.userData.userId}});
    if (lists.code !== 200) throw Error(`Real playlist API returned ${lists.code}`);
    const detail = lists.playlist?.find(list=>list.creator?.userId===d.userData.userId);
    if (!detail) throw Error('Real account playlist unavailable');
    const response = await window.realAccountRequest({url:'/playlist/track/all',params:{id:detail.id,limit:30,offset:0}});
    liked = { detail, data: response.songs?.map(song=>({ id: song.id, name: song.name,
      type: 'song', duration: song.dt, artists: song.ar, album: song.al, cover: song.al?.picUrl })) };
  }
  const songs = liked?.data?.filter(song => song.type === "song" && !song.path).slice(0, 30);
  if (!songs || songs.length < 3) throw Error("Real liked playlist needs at least three online tracks");
  if (window.realAccountPreparationCancelled) throw Error('Real playlist preparation cancelled');
  window.realAccountPlaybackChanged = true;
  window.realAccountPreparationStage = 'starting-native-playback';
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
  window.realAccountRestartCase = async () => {
    await controller.toggleShuffle('off');
    const seed = songs.find(song=>song.id===2749049796) || songs[0];
    await controller.updatePlayList(songs, seed, Number(liked.detail.id), {play:true,showTip:false});
    await controller.playSong({song:seed,autoPlay:true});
    controller.setRate(1);
  };
  window.realAccountCheckNextSource = async () => {
    const id = d.playList[s.playIndex+1]?.id;
    const response = await window.realAccountRequest({url:'/song/url/v1',params:{id,level:'exhigh'}});
    const entry = response?.data?.find(song=>song.id===id);
    const trial = entry?.freeTrialInfo != null;
    return {id,available:!!entry?.url && (!trial || settings.playSongDemo),trial,
      allowTrial:settings.playSongDemo,code:entry?.code ?? response?.code,durationMs:entry?.time};
  };
  await controller.toggleShuffle("off");
  controller.toggleRepeat("off");
  await controller.setDesktopLyricShow(true);
  await controller.updatePlayList(songs, songs.find(song => song.id === m.playSong.id) || songs[0],
    Number(liked.detail.id), { play: true, showTip: false });
  // Recreating an engine leaves no loaded source even when the saved song ID
  // matches; updatePlayList intentionally only resumes that same song.
  await controller.playSong({ song: m.playSong, autoPlay: true });
  controller.setRate(window.realNaturalMatrix ? 1 : 2);
  window.realAccountPreparationStage = 'ready';
  window.realAccountModeReady = true;
};
window.restoreRealAccountPlayback = async () => {
  window.realAccountPreparationCancelled = true;
  const saved = window.realAccountSavedPlayback;
  const p = window.realAccountPinia, d = p._s.get("data"), s = p._s.get("status"), settings = p._s.get("setting");
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  if (window.realAccountXhrOpen) XMLHttpRequest.prototype.open = window.realAccountXhrOpen;
  if (window.realAccountXhrSend) XMLHttpRequest.prototype.send = window.realAccountXhrSend;
  if (!saved || !window.realAccountPlaybackChanged) {
    window.realAccountPlaybackRestored = true;
    return;
  }
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

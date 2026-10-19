// Real public metadata/audio/lyric requests; no credentials or media URLs are reported.
window.prepareOnlineTransition = async () => {
  window.onlinePhase = "fixture";
  await window.p7Init();
  const data = window.fixturePinia._s.get("data");
  window.onlineSaved = {
    list: data.playList,
    history: data.setHistory,
    playing: window.p7Saved.status.playStatus,
  };
  data.setHistory = async () => {};
  window.fixtureSetting.lastfm.enabled = false;
  window.fixtureSetting.useNextPrefetch = true;
  window.fixtureSetting.lyricPriority = "official";
  window.fixtureStatus.personalFmMode = false;
  window.fixtureStatus.shuffleMode = "off";
  window.fixtureStatus.repeatMode = "off";
  window.onlinePhase = "api-ready";
  for (let attempt = 0; attempt < 60; attempt++) {
    try {
      if ((await fetch("http://127.0.0.1:1145/api")).ok) break;
    } catch {
      /* The embedded API may still be starting. */
    }
    if (attempt === 59) throw Error("Local API not ready");
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  window.onlinePhase = "public-metadata";
  const abort = new AbortController(),
    timeout = setTimeout(() => abort.abort(), 20000);
  let details;
  try {
    details = await (
      await fetch(
        "http://127.0.0.1:1145/api/netease/song/detail?ids=28941713," +
          (window.onlineTargetSongId || 186016) + "&noCookie=true",
        { signal: abort.signal },
      )
    ).json();
  } finally {
    clearTimeout(timeout);
  }
  if (details.songs?.length !== 2)
    throw Error(
      "Public metadata unavailable: code=" +
        details.code +
        ", count=" +
        (details.songs?.length ?? 0),
    );
  window.onlineSongs = details.songs.map((song) => ({
    id: song.id,
    name: song.name,
    type: "song",
    artists: song.ar,
    album: song.al,
    duration: song.dt,
    cover: song.al?.picUrl,
  }));
  data.playList = window.onlineSongs;
  window.fixtureStatus.playIndex = 0;
  window.onlineTrace = [];
  window.onlineActions = [];
  window.onlineMethods = {};
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  for (const name of ["playSong", "nextOrPrev", "applyNativeTrackChanged", "applyNativeAutoNext"]) {
    window.onlineMethods[name] = controller[name];
    controller[name] = function (...args) {
      window.onlineActions.push({
        at: Date.now(), name, current: window.p7Stores.music.playSong.id,
        index: window.fixtureStatus.playIndex,
        songId: name === "playSong" ? args[0]?.song?.id : name === "applyNativeTrackChanged" ? args[1] : undefined,
        direction: name === "nextOrPrev" ? args[0] : undefined,
        autoEnd: name === "nextOrPrev" ? args[2] : undefined,
        source: name === "applyNativeTrackChanged" ? args[3] : undefined,
      });
      return window.onlineMethods[name].apply(this, args);
    };
  }
  window.onlineNativeEvents = [];
  window.onlineObservedSources = {};
  window.onlineNativeHandles = [];
  const native = window.Capacitor.Plugins?.AndroidNativePlayback;
  if (native?.addListener) for (const kind of ["ended", "customAction", "error", "playbackStateChanged"]) {
    window.onlineNativeHandles.push(await native.addListener(kind, (event) => {
      // Keep the source observed on entry: later load() state may briefly carry the
      // preceding metadata ID until the following metadata sync completes.
      if (event.songId && event.src && !window.onlineObservedSources[event.songId])
        window.onlineObservedSources[event.songId] = event.src;
      window.onlineNativeEvents.push({at: Date.now(), kind, action: event.action, songId: event.songId,
        index: event.playListIndex, source: event.source, durationMs: event.durationMs, errorCode: event.errorCode,
        positionMs: event.positionMs, ready: event.ready, playing: event.playing});
    }));
  }
  window.onlineSetLyric = window.p7Stores.music.setSongLyric;
  window.p7Stores.music.setSongLyric = function (value, ...rest) {
    window.onlineTrace.push({
      at: Date.now(),
      songId: this.playSong.id,
      lines: value.lrcData?.length || 0,
    });
    return window.onlineSetLyric.call(this, value, ...rest);
  };
  window.onlinePhase = "play-first";
  await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(true);
  window.onlineFirstStarted = Date.now();
  await window.__SPLAYER_PLAYER_CONTROLLER__.playSong({
    song: window.onlineSongs[0],
    autoPlay: true,
  });
  window.onlinePrepared = true;
};
window.restoreOnlineTransition = async () => {
  for (const handle of window.onlineNativeHandles || []) await handle.remove();
  for (const [name, method] of Object.entries(window.onlineMethods || {})) window.__SPLAYER_PLAYER_CONTROLLER__[name] = method;
  if (window.onlineSaved) {
    const data = window.fixturePinia._s.get("data");
    data.playList = window.onlineSaved.list;
    data.setHistory = window.onlineSaved.history;
  }
  if (window.onlineSetLyric) window.p7Stores.music.setSongLyric = window.onlineSetLyric;
  const playing = window.onlineSaved?.playing ?? window.p7Saved.status.playStatus;
  await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(
    Boolean(window.p7Saved.status.showDesktopLyric),
  );
  // Restore the original transport once, rather than resuming the test audio.
  window.p7Saved.status.playStatus = false;
  await window.p7Restore();
  if (window.p7Saved.music.playSong.id) {
    await window.__SPLAYER_PLAYER_CONTROLLER__.playSong({
      song: window.p7Saved.music.playSong,
      autoPlay: playing,
      seek: window.p7Saved.status.currentTime,
    });
  }
  window.onlineRestored = true;
};

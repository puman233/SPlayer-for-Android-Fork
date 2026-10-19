// Public cache bridge supplies deterministic lyrics; the real toggle, parser and renderer run.
window.prepareTTML = async () => {
  const id = 99007100;
  window.ttmlKeys = [`${id}.ttml`, `${id}.json`];
  window.ttmlCacheBackup = [];
  for (const key of window.ttmlKeys) {
    window.ttmlCacheBackup.push(
      await window.Capacitor.nativePromise("AndroidCache", "read", { type: "lyrics", key }),
    );
  }
  const xml =
    '<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><body><div><p itunes:key="L1" begin="00:00:01.000" end="00:00:03.000"><span begin="00:00:01.000" end="00:00:02.000">hello </span><span begin="00:00:02.000" end="00:00:03.000">world</span></p></div></body></tt>';
  const lrc = JSON.stringify({
    code: 200,
    lrc: { lyric: "[00:01.000]Fallback line\n[00:03.000]Next line" },
  });
  window.ttmlFixtureXml = xml;
  for (const [index, text] of [xml, lrc].entries()) {
    const result = await window.Capacitor.nativePromise("AndroidCache", "write", {
      type: "lyrics",
      key: window.ttmlKeys[index],
      data: btoa(text),
    });
    if (!result.success) throw Error("Test lyric cache write failed");
  }
  Object.assign(window.fixtureSetting, {
    cacheEnabled: true,
    useAMLyrics: false,
    enableQQMusicLyric: false,
    enableOnlineTTMLLyric: false,
    lyricPriority: "official",
    showWordLyrics: false,
    autoHidePlayerMeta: false,
  });
  window.p7Stores.music.playSong = { ...window.p7Songs[0], path: undefined };
  window.p7SetLyrics(false);
  window.ttmlReady = true;
};
window.restoreTTML = async () => {
  for (const [index, key] of (window.ttmlKeys || []).entries()) {
    const cached = window.ttmlCacheBackup?.[index];
    if (!cached) continue;
    await window.Capacitor.nativePromise("AndroidCache", cached.hit ? "write" : "remove", {
      type: "lyrics",
      key,
      ...(cached.hit ? { data: cached.data } : {}),
    });
  }
  window.ttmlRestored = true;
};

// Isolated engine selection and HTTP transport only. Production controller/media run unchanged.
window.observeWebProbeAndPause = () => {
  window.webNetOriginalFetch = window.fetch;
  window.fetch = function (input, init) {
    const pending = window.webNetOriginalFetch.call(this, input, init);
    if (init?.headers?.Range === "bytes=0-0") {
      window.webNetObservedProbeSignal = init.signal;
      window.webNetProbePauseTimer = setTimeout(() => {
        const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
        window.webNetProbePendingAtPause = !!controller.mediaFailureProbe && !init.signal.aborted;
        controller.pause(true);
        window.webNetProbePauseComplete = true;
      }, 300);
    }
    return pending;
  };
};

window.prepareWebNetwork = async (base) => {
  window.webNetPhase = "module";
  const resource = performance.getEntriesByType("resource").find((entry) => /\/stores-[^/]+\.js/.test(entry.name));
  if (!resource) throw Error("Built public audio module unavailable");
  const exports = Object.values(await import(resource.name));
  window.webNetGetAudio = exports.find((value) => typeof value === "function"
    && String(value).includes("playbackEngine") && String(value).includes("audioEngine") && String(value).includes("new "));
  if (!window.webNetGetAudio) throw Error("Built public audio getter unavailable");
  window.webNetSavedEngine = window.__SPLAYER_AUDIO_MANAGER__.engineType;
  window.webNetRecreateAudio = () => {
    window.__SPLAYER_AUDIO_MANAGER__?.destroy();
    delete window.__SPLAYER_AUDIO_MANAGER__;
    const audio = window.webNetGetAudio();
    window.__SPLAYER_PLAYER_CONTROLLER__.rebindAudioEvents();
    return audio;
  };
  window.webNetEvents = [];
  window.webNetSources = [];
  window.webNetStartedAt = Date.now();
  window.webNetSrcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, "src");
  Object.defineProperty(HTMLMediaElement.prototype, "src", {
    ...window.webNetSrcDescriptor,
    set(value) {
      const secureBase = base.replace(/^http:/, "https:");
      const url = String(value);
      window.webNetSrcDescriptor.set.call(this,
        url.startsWith(secureBase + "/") ? base + url.slice(secureBase.length) : value);
    },
  });
  window.webNetPhase = "fixture";
  await window.prepareHttpLyricSequence(base, 1, 180000, {
    allowMix: false,
    beforePlay: async () => {
      window.webNetPhase = "engine";
      window.fixtureSetting.audioEngine = "element";
      const audio = window.webNetRecreateAudio();
      if (audio.engineType !== "element") throw Error("Production element selection failed");
      window.webNetElement = audio.engine.audioElement;
      if (!(window.webNetElement instanceof HTMLAudioElement)) throw Error("Real HTMLAudioElement unavailable");
      for (const type of ["play", "playing", "waiting", "pause", "seeking", "seeked", "ended", "error"]) {
        audio.addEventListener(type, (event) => window.webNetEvents.push({
          type, atMs: Date.now() - window.webNetStartedAt,
          positionMs: Math.round(window.webNetElement.currentTime * 1000),
          code: event.detail?.errorCode, mediaCode: window.webNetElement.error?.code,
        }));
      }
      const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
      if (window.webNetPauseOnMediaError) audio.addEventListener("error", () => {
        if (window.webNetRecoveryPauseComplete) return;
        window.webNetRecoveryAtPause = controller.retryInfo.count;
        window.webNetErrorCodeAtPause = window.webNetElement.error?.code;
        controller.pause(true);
        window.webNetRecoveryPauseComplete = true;
      });
      window.webNetPrepareSource = controller.prepareAudioSource;
      controller.prepareAudioSource = function (...args) {
        const source = { songId: args[0]?.id, atMs: Date.now() - window.webNetStartedAt };
        window.webNetSources.push(source);
        return window.webNetPrepareSource.apply(this, args).then((value) => {
          source.available = !!value.audioSource?.url;
          return value;
        }, (error) => { source.error = error?.name; throw error; });
      };
      window.webNetPhase = "play";
    },
  });
  window.webNetSnapshot = () => {
    const element = window.webNetElement;
    const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
    return {
      engine: window.__SPLAYER_AUDIO_MANAGER__.engineType,
      realElement: element instanceof HTMLAudioElement,
      positionMs: Math.round(element.currentTime * 1000), durationMs: Math.round(element.duration * 1000),
      paused: element.paused, readyState: element.readyState, networkState: element.networkState,
      mediaCode: element.error?.code || 0, sourceBound: element.currentSrc.startsWith(base + "/"),
      retryCount: controller.retryInfo.count, progressStart: controller.retryProgressStart,
      mediaFailurePending: !!controller.mediaFailureProbe,
      songId: window.fixturePinia._s.get("music").playSong.id,
      storePositionMs: window.fixtureStatus.currentTime,
      playing: window.fixtureStatus.playStatus, loading: window.fixtureStatus.playLoading,
      visibility: document.visibilityState, sourceCalls: window.webNetSources.length,
    };
  };
  window.webNetPrepared = true;
};

window.restoreWebNetwork = async () => {
  clearTimeout(window.webNetProbePauseTimer);
  if (window.webNetOriginalFetch) window.fetch = window.webNetOriginalFetch;
  const controller = window.__SPLAYER_PLAYER_CONTROLLER__;
  await controller.pause();
  if (window.webNetPrepareSource) controller.prepareAudioSource = window.webNetPrepareSource;
  if (window.webNetSrcDescriptor) Object.defineProperty(HTMLMediaElement.prototype, "src", window.webNetSrcDescriptor);
  const playing = window.p7Saved.status.playStatus;
  window.p7Saved.status.playStatus = false;
  await window.restoreHttpLyricSequence();
  window.webNetRecreateAudio();
  await controller.setDesktopLyricShow(Boolean(window.p7Saved.status.showDesktopLyric));
  if (window.p7Saved.music.playSong.id) await controller.playSong({
    song: window.p7Saved.music.playSong, autoPlay: playing, seek: window.p7Saved.status.currentTime,
  });
  if (window.__SPLAYER_AUDIO_MANAGER__.engineType !== window.webNetSavedEngine) throw Error("Original engine not restored");
  window.webNetRestored = true;
};

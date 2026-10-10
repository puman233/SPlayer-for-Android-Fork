import { isCapacitorAndroid } from "@/utils/env";
import { requestFailureCategory } from "@/utils/requestDiagnostics";
import type { PluginListenerHandle } from "@capacitor/core";
import {
  AndroidNativePlayback,
  type AndroidNativePlaybackState,
  type AndroidNativeEndedEvent,
  type AndroidNativeErrorEvent,
} from "@/plugins/androidNativePlayback";
import type {
  EngineCapabilities,
  FadeCurve,
  IPlaybackEngine,
  PauseOptions,
  PlayOptions,
} from "./IPlaybackEngine";
import { AUDIO_EVENTS } from "./BaseAudioPlayer";

/**
 * Android 原生播放器（参照 SPlayer-ROM-Compat 实现）
 *
 * 核心设计：
 * 1. TS 用 performance.now() 插值拥有 currentTime，不接收原生周期上报（避免 seek-to-zero / 进度回退）
 * 2. load() 原子传入 url+seek+autoPlay
 * 3. play/resume 等待原生确认；同步控制指令处理拒绝并恢复原生态
 * 4. state 事件只更新 src/duration/paused/error，不动 _currentTime
 * 5. 仅暂停 seek 完成后才从原生确认位置
 */
export class AndroidNativeAudioPlayer extends EventTarget implements IPlaybackEngine {
  public readonly capabilities: EngineCapabilities = {
    supportsRate: true,
    supportsSinkId: false,
    supportsEqualizer: true,
    // Java 端用 FftAudioProcessor 在 ExoPlayer 渲染链上做 FFT
    supportsSpectrum: true,
  };

  private _src = "";
  private _duration = 0;
  private _currentTime = 0;
  private _paused = true;
  private _volume = 1;
  private _rate = 1;
  private _errorCode = 0;
  private _replayGain = 1;
  private isInitialized = false;
  private listenersBound = false;
  private listenerHandles: PluginListenerHandle[] = [];
  private lifecycle = 0;
  private commandRevision = 0;
  private visibilityHandler: (() => void) | null = null;

  /** 上一次 _currentTime / lastTimeSyncAt 校准时刻 (performance.now()) */
  private lastTimeSyncAt = 0;

  /** 最后一次 JS 主动 seek 时刻，用于短期屏蔽 native 周期事件回拖进度 */
  private seekPendingAt = 0;
  /** seek 后多久内只接受 authoritative 事件，避开 native 处理 seek 前的旧周期事件 */
  private static readonly SEEK_DRIFT_LOCK_MS = 800;

  /** ended 事件去重（3 秒内同一 src 只派发一次） */
  private static readonly ENDED_DEDUP_MS = 3000;
  private lastEndedEventAt = 0;
  private lastEndedSrc = "";

  /** 频谱缓存：Java ~20Hz 推送，rAF 同步读取 */
  private latestFftData: Uint8Array = new Uint8Array(256);
  private latestLowFreq = 0;
  /** 频谱采集是否已启用（避免重复 enable 调用） */
  private visualizerEnabled = false;
  private visualizerRequested = false;
  /** 频谱操作串行化队列，防止快速 mount/unmount 导致原生态与 TS 标记不一致 */
  private visualizerOpQueue: Promise<unknown> = Promise.resolve();

  public init(): void {
    if (this.isInitialized) return;
    this.isInitialized = true;
    const lifecycle = ++this.lifecycle;
    if (!this.listenersBound && isCapacitorAndroid) {
      this.listenersBound = true;
      void this.bindNativeListeners(lifecycle).catch((error) => {
        if (!this.isActive(lifecycle)) return;
        console.warn("[AndroidNativeAudioPlayer] listener registration failed:", requestFailureCategory(error));
        this.destroy();
      });
      this.bindVisibilitySync(lifecycle);
      const revision = this.commandRevision;
      const seekPendingAt = this.seekPendingAt;
      void AndroidNativePlayback.getState().then(state => {
        if (!this.isActive(lifecycle) || revision !== this.commandRevision || seekPendingAt !== this.seekPendingAt) return;
        this.applyStructuralState(state);
        this._currentTime = Math.max(0, state.positionMs || 0) / 1000;
        this.lastTimeSyncAt = performance.now();
        this.dispatchEvent(new Event(AUDIO_EVENTS.TIME_UPDATE));
      }).catch(() => {});
    }
  }

  /** 回前台时拉取 native 位置，纠正 WebView 冻结导致的 JS 估算落后 */
  private isActive(lifecycle: number): boolean {
    return this.isInitialized && lifecycle === this.lifecycle;
  }

  private bindVisibilitySync(lifecycle: number): void {
    if (typeof document === "undefined") return;
    this.visibilityHandler = () => {
      if (!this.isActive(lifecycle)) return;
      if (document.visibilityState !== "visible") return;
      if (!this._src) return;
      const src = this._src;
      const revision = this.commandRevision;
      const seekPendingAt = this.seekPendingAt;
      void AndroidNativePlayback.getState()
        .then((state) => {
          if (!this.isActive(lifecycle) || this._src !== src || revision !== this.commandRevision || seekPendingAt !== this.seekPendingAt) return;
          if (!state || typeof state.positionMs !== "number") return;
          if (state.src && state.src !== src) return;
          this.applyStructuralState(state);
          // 与当前估算差距 > 500ms 才覆盖，避免来回波动
          const estimatedMs = this.estimateCurrentTime() * 1000;
          if (Math.abs(state.positionMs - estimatedMs) <= 500) return;
          const safePositionSec = Math.max(0, state.positionMs) / 1000;
          this._currentTime = safePositionSec;
          this.lastTimeSyncAt = performance.now();
          if (typeof state.durationMs === "number" && state.durationMs > 0) {
            this._duration = state.durationMs / 1000;
          }
          this.dispatchEvent(new Event(AUDIO_EVENTS.TIME_UPDATE));
        })
        .catch(() => {});
    };
    document.addEventListener("visibilitychange", this.visibilityHandler);
  }

  public destroy(): void {
    this.lifecycle++;
    this.commandRevision++;
    if (this.visibilityHandler && typeof document !== "undefined") {
      document.removeEventListener("visibilitychange", this.visibilityHandler);
    }
    this.visibilityHandler = null;
    this.isInitialized = false;
    this.listenersBound = false;
    this._src = "";
    this._duration = 0;
    this._currentTime = 0;
    this._paused = true;
    this.lastTimeSyncAt = 0;
    this.seekPendingAt = 0;
    this.lastEndedEventAt = 0;
    this.lastEndedSrc = "";
    // 关闭频谱采集；同步清零缓存，防止下次 init 前 getFrequencyData 仍返回旧鼓点
    if (this.visualizerEnabled || this.visualizerRequested) {
      const cleanup = this.visualizerOpQueue.then(async () => {
        await AndroidNativePlayback.enableVisualizer({ enable: false });
      });
      this.visualizerOpQueue = cleanup.catch(() => {});
    }
    this.visualizerEnabled = false;
    this.visualizerRequested = false;
    this.latestFftData.fill(0);
    this.latestLowFreq = 0;
    void this.releaseListeners();
  }

  // 频谱可视化

  /**
   * 获取最近一次 Java 推送的 FFT 数据（长度 256，与 PlayerSpectrum 格式一致）。
   * 返回内部缓存引用，调用方不应修改。
   */
  public getFrequencyData(): Uint8Array {
    return this.latestFftData;
  }

  /**
   * 获取最近一次 Java 端推送的低频音量 [0.0, 1.0]，用于 AMLL 流体背景鼓点驱动。
   */
  public getLowFrequencyVolume(): number {
    return this.latestLowFreq;
  }

  /**
   * 按需启用/停用频谱采集（FftAudioProcessor，无需权限）。
   * 不抛错；失败时 getFrequencyData 返回零数组（静默降级）。
   */
  public async enableVisualizer(enable: boolean): Promise<boolean> {
    if (!isCapacitorAndroid) return false;
    if (!this.isInitialized) this.init();
    const lifecycle = this.lifecycle;
    this.visualizerRequested = enable;
    // 串行化避免并发 enable(true)/enable(false) 与 TS flag 错位
    const op = this.visualizerOpQueue.then(async () => {
      if (!this.isActive(lifecycle)) return false;
      if (this.visualizerEnabled === enable) return true;
      try {
        const result = await AndroidNativePlayback.enableVisualizer({ enable });
        if (!this.isActive(lifecycle)) return false;
        // 仅原生确认成功才更新 flag；disable 路径直接置 false，避免 fast-path 跳过锁死
        if (result.granted) {
          this.visualizerEnabled = enable;
        } else if (!enable) {
          this.visualizerEnabled = false;
        }
        if (!enable) {
          this.latestFftData.fill(0);
          this.latestLowFreq = 0;
        }
        return result.granted;
      } catch (error) {
        console.warn("[AndroidNativeAudioPlayer] enableVisualizer failed:", requestFailureCategory(error));
        if (!this.isActive(lifecycle)) return false;
        // 异常路径置反值，强制下次同语义请求重试原生
        this.visualizerEnabled = !enable;
        return false;
      }
    });
    this.visualizerOpQueue = op.catch(() => {});
    return op;
  }

  public async play(url?: string, options?: PlayOptions): Promise<void> {
    if (!this.isInitialized) this.init();
    const shouldPlay = options?.autoPlay ?? true;
    const seekSeconds = options?.seek && options.seek > 0 ? options.seek : 0;

    if (url) {
      const revision = ++this.commandRevision;
      const lifecycle = this.lifecycle;
      // 乐观更新本地状态（UI 立即反映期望的 seek 位置）。
      // 注意：即使 shouldPlay=true，这里也强制 _paused=true，让 estimateCurrentTime() 冻结在 seekSeconds，
      // 避免 ExoPlayer 在 prep/buffer 阶段（实际未开播）TS 的估算时钟先于真实播放累加，
      // 等到首次 progressChanged 时进度条直接跳到一个非 0 的错误位置。
      // 真实开播由 Java 端 playbackStateChanged(paused=false) 触发 applyStructuralState 翻转 _paused 并重置 lastTimeSyncAt。
      this._src = url;
      this._currentTime = seekSeconds;
      this._duration = 0;
      this._paused = true;
      this._errorCode = 0;
      this.lastTimeSyncAt = performance.now();
      this.lastEndedEventAt = 0;
      this.lastEndedSrc = "";

      // 一次性把 url+positionMs+autoPlay 交给 Java，原子完成 prepare+seekTo+playWhenReady
      const positionMs = seekSeconds > 0 ? Math.round(seekSeconds * 1000) : 0;
      this.dispatchEvent(new Event(AUDIO_EVENTS.LOAD_START));
      try {
        const state = await AndroidNativePlayback.load({ url, positionMs, autoPlay: shouldPlay });
        if (this.isActive(lifecycle) && revision === this.commandRevision) this.applyStructuralState(state);
      } catch (error) {
        if (this.isActive(lifecycle) && revision === this.commandRevision) {
          this._paused = true;
          this._errorCode = 2;
          this.dispatchEvent(new Event(AUDIO_EVENTS.PAUSE));
        }
        throw error;
      }
      return;
    }

    // 无 URL：仅执行 seek 与/或 resume
    if (seekSeconds > 0) {
      this.seek(seekSeconds);
    }

    if (!shouldPlay || !this._paused) return;

    await this.resume(options);
  }

  public async resume(_options?: { fadeIn?: boolean; fadeDuration?: number }): Promise<void> {
    if (!this.isInitialized) this.init();
    const revision = ++this.commandRevision;
    const lifecycle = this.lifecycle;
    try {
      const state = await AndroidNativePlayback.play();
      if (this.isActive(lifecycle) && revision === this.commandRevision) {
        if (state.errorCode === 0) this._errorCode = 0;
        this.applyStructuralState(state);
      }
    } catch (error) {
      if (this.isActive(lifecycle) && revision === this.commandRevision) this._errorCode = 2;
      throw error;
    }
  }

  private async recoverControlFailure(error: unknown, revision: number, lifecycle: number): Promise<void> {
    if (!this.isActive(lifecycle) || revision !== this.commandRevision) return;
    console.warn("[AndroidNativeAudioPlayer] control failed:", requestFailureCategory(error));
    const seekPendingAt = this.seekPendingAt;
    try {
      const state = await AndroidNativePlayback.getState();
      if (!this.isActive(lifecycle) || revision !== this.commandRevision || seekPendingAt !== this.seekPendingAt) return;
      this.applyStructuralState(state);
      this._currentTime = Math.max(0, state.positionMs || 0) / 1000;
      this.lastTimeSyncAt = performance.now();
      // The controller may have optimistically paused; confirm even unchanged engine state.
      this.dispatchEvent(new Event(this._paused ? AUDIO_EVENTS.PAUSE : AUDIO_EVENTS.PLAY));
    } catch {
      if (!this.isActive(lifecycle) || revision !== this.commandRevision || seekPendingAt !== this.seekPendingAt) return;
      this._errorCode = 2;
    }
  }

  public pause(_options?: PauseOptions): void {
    const revision = ++this.commandRevision;
    const lifecycle = this.lifecycle;
    // 暂停瞬间快照当前估算时间，作为新基准
    this._currentTime = this.estimateCurrentTime();
    this._paused = true;
    this.lastTimeSyncAt = performance.now();
    void AndroidNativePlayback.pause().catch(error => this.recoverControlFailure(error, revision, lifecycle));
    this.dispatchEvent(new Event(AUDIO_EVENTS.PAUSE));
  }

  public stop(): void {
    const revision = ++this.commandRevision;
    const lifecycle = this.lifecycle;
    this.lastTimeSyncAt = 0;
    this.lastEndedEventAt = 0;
    this.lastEndedSrc = "";
    this._paused = true;
    this._currentTime = 0;
    this._src = "";
    this._duration = 0;
    void AndroidNativePlayback.stop().catch(error => this.recoverControlFailure(error, revision, lifecycle));
    this.dispatchEvent(new Event(AUDIO_EVENTS.PAUSE));
  }

  public seek(time: number): void {
    const revision = this.commandRevision;
    const lifecycle = this.lifecycle;
    const safeTime = Math.max(0, time);

    this._currentTime = safeTime;
    this.lastTimeSyncAt = performance.now();
    this.seekPendingAt = this.lastTimeSyncAt;

    this.dispatchEvent(new Event(AUDIO_EVENTS.SEEKING));
    void AndroidNativePlayback.seek({
      positionMs: Math.max(0, Math.round(safeTime * 1000)),
    }).catch(error => this.recoverControlFailure(error, revision, lifecycle));
    this.dispatchEvent(new Event(AUDIO_EVENTS.SEEKED));
  }

  public setVolume(value: number): void {
    this._volume = Math.max(0, Math.min(1, value));
    if (isCapacitorAndroid) {
      void AndroidNativePlayback.setVolume({ volume: this._volume }).catch(() => {});
    }
  }

  public getVolume(): number {
    return this._volume;
  }

  public setRate(rate: number): void {
    // 速率变化前先快照当前估算位置，防止后续插值把切换前的时间用新速率重算
    this._currentTime = this.estimateCurrentTime();
    this.lastTimeSyncAt = performance.now();
    this._rate = rate;
    if (isCapacitorAndroid) {
      void AndroidNativePlayback.setRate({ rate }).catch(() => {});
    }
  }

  public setFilterGain(index: number, value: number): void {
    if (!isCapacitorAndroid || index < 0 || index >= 10) return;
    const gains = this.eqBands.length ? [...this.eqBands] : Array(10).fill(0);
    gains[index] = Math.max(-12, Math.min(12, value));
    this.eqBands = gains;
    void AndroidNativePlayback.setEqualizer({ bands: gains }).catch(() => {});
  }

  private eqBands: number[] = Array(10).fill(0);

  public getRate(): number {
    return this._rate;
  }

  public setAudioDelayCompensation(_offset: number): void {}

  public async setSinkId(_deviceId: string): Promise<void> {}

  public setReplayGain(gain: number): void {
    this._replayGain = gain;
  }

  public getErrorCode(): number {
    return this._errorCode;
  }

  public get duration(): number {
    return this._duration;
  }

  public get currentTime(): number {
    return this.estimateCurrentTime();
  }

  public get paused(): boolean {
    return this._paused;
  }

  public get src(): string {
    return this._src;
  }

  /** 基于 _currentTime + (now - lastTimeSyncAt) * rate 估算当前播放位置 */
  private estimateCurrentTime(): number {
    if (this._paused || this.lastTimeSyncAt <= 0) return this._currentTime;
    const elapsed = ((performance.now() - this.lastTimeSyncAt) / 1000) * this._rate;
    const estimated = this._currentTime + Math.max(elapsed, 0);
    if (this._duration > 0) return Math.min(estimated, this._duration);
    return estimated;
  }

  // ========== 原生事件监听 ==========

  private async retainListener(registration: Promise<PluginListenerHandle>, lifecycle: number): Promise<boolean> {
    const handle = await registration;
    if (!this.isActive(lifecycle)) {
      await handle.remove();
      return false;
    }
    this.listenerHandles.push(handle);
    return true;
  }

  private async bindNativeListeners(lifecycle: number) {
    // playbackStateChanged: 只接受结构性字段，不动 currentTime
    if (!await this.retainListener(
      AndroidNativePlayback.addListener("playbackStateChanged", (event) => {
        if (!this.isActive(lifecycle)) return;
        this.applyStructuralState(event);
      }), lifecycle,
    )) return;

    // progressChanged：以 native 为唯一进度源，每个事件都同步基准（JS 估算只做 250ms 之间的 60fps 平滑）。
    // 唯一例外：JS 端刚发起 seek 后短期内 (SEEK_DRIFT_LOCK_MS)，只信任 authoritative 事件，
    // 否则 native 还没处理完 seek 时残留的旧周期事件会把进度回拖到 seek 前位置。
    if (!await this.retainListener(
      AndroidNativePlayback.addListener("progressChanged", (event) => {
        if (!this.isActive(lifecycle)) return;
        const nextDuration = Math.max(0, event.durationMs) / 1000;
        if (nextDuration > 0) this._duration = nextDuration;
        const isAuthoritative = event.authoritative === true;
        const inSeekLock =
          this.seekPendingAt > 0 &&
          performance.now() - this.seekPendingAt < AndroidNativeAudioPlayer.SEEK_DRIFT_LOCK_MS;
        if (isAuthoritative || !inSeekLock) {
          const safePositionSec = Math.max(0, event.positionMs) / 1000;
          this._currentTime = safePositionSec;
          this.lastTimeSyncAt = performance.now();
          if (isAuthoritative) {
            this.seekPendingAt = 0;
            this.dispatchEvent(new Event(AUDIO_EVENTS.SEEKING));
            this.dispatchEvent(new Event(AUDIO_EVENTS.SEEKED));
          }
        }
        this.dispatchEvent(new Event(AUDIO_EVENTS.TIME_UPDATE));
      }), lifecycle,
    )) return;

    if (!await this.retainListener(
      AndroidNativePlayback.addListener("ended", (event: AndroidNativeEndedEvent) => {
        if (!this.isActive(lifecycle)) return;
        const now = performance.now();
        if (
          this._src &&
          this._src === this.lastEndedSrc &&
          now - this.lastEndedEventAt < AndroidNativeAudioPlayer.ENDED_DEDUP_MS
        ) {
          return;
        }
        const endedSrc = this._src;
        this.lastEndedSrc = endedSrc;
        this.lastEndedEventAt = now;

        const endDuration = Math.max(0, event.durationMs) / 1000;
        if (endDuration > 0) this._duration = endDuration;
        this._currentTime = this._duration > 0 ? this._duration : this._currentTime;
        this._paused = true;
        this.lastTimeSyncAt = performance.now();
        this.dispatchEvent(new Event(AUDIO_EVENTS.TIME_UPDATE));
        // 同步比对 _src 与事件捕获的 endedSrc：若已切换说明 ENDED 来自旧轨，丢弃。
        // 不再走异步 getState()，避免 IPC 期间用户切歌导致 legitimate 自然终止被吞掉。
        if (this._src !== endedSrc) return;
        this.dispatchEvent(new Event(AUDIO_EVENTS.ENDED));
      }), lifecycle,
    )) return;

    if (!await this.retainListener(
      AndroidNativePlayback.addListener("error", (event: AndroidNativeErrorEvent) => {
        if (!this.isActive(lifecycle)) return;
        this._errorCode = event.errorCode || 2;
        this.dispatchEvent(
          new CustomEvent(AUDIO_EVENTS.ERROR, {
            detail: { originalEvent: new Event("error"), errorCode: this._errorCode },
          }),
        );
      }), lifecycle,
    )) return;

    // visualizerData: Java 端 Visualizer FFT 数据 ~30Hz 推送，缓存到本地以便 rAF 同步读取。
    // Payload 为 base64 字符串（256 字节），相比 number[] 跨桥体积 -70%，atob 单次调用解码。
    if (!await this.retainListener(
      AndroidNativePlayback.addListener("visualizerData", (event) => {
        if (!this.isActive(lifecycle)) return;
        if (typeof event.fftB64 === "string" && event.fftB64.length > 0) {
          // atob 把 base64 解为二进制字符串，长度 = 原 byte 数
          // 直接逐字节写入既有 Uint8Array，无中间分配
          const decoded = atob(event.fftB64);
          const len = Math.min(decoded.length, this.latestFftData.length);
          for (let i = 0; i < len; i++) {
            this.latestFftData[i] = decoded.charCodeAt(i);
          }
        }
        if (typeof event.lowFreq === "number") {
          this.latestLowFreq = event.lowFreq;
        }
      }), lifecycle,
    )) return;

    if (!await this.retainListener(
      AndroidNativePlayback.addListener("diagnosticLog", (event) => {
        if (!this.isActive(lifecycle)) return;
        console.debug(`[AndroidNativePlayback] [${event.tag}] ${event.message}`);
      }), lifecycle,
    )) return;
  }

  private async releaseListeners() {
    const handles = [...this.listenerHandles];
    this.listenerHandles = [];
    await Promise.allSettled(handles.map((handle) => handle.remove()));
  }

  /**
   * 从原生 playbackStateChanged 事件更新「结构性」状态。
   *
   * **不更新 _currentTime / lastTimeSyncAt**——位置由 TS 用 performance.now() 插值。
   * 只接受：src / durationMs / paused / playing / playbackRate / ready / errorCode。
   */
  private applyStructuralState(state: Partial<AndroidNativePlaybackState>) {
    if (typeof state.src === "string" && state.src) {
      // 检测 Java 端单方面换轨（gapless 预排队 handleAutoAdvanceOnEnded 走 customAction("autoNext") 路径，
      // 不会调 TS 的 play()/load()，所以 TS 引擎状态不会被重置）。
      // 若 src 和当前不同，把位置 / paused / duration / 估算时钟基准全部归零，
      // 防止 estimateCurrentTime 用旧 _currentTime + elapsed 算出错误进度，
      // 等同一事件后续把 paused=false 翻过来时进度条就从 0 起步。
      if (state.src !== this._src) {
        this._src = state.src;
        this._currentTime = 0;
        this._duration = 0;
        this._paused = true;
        this.lastTimeSyncAt = performance.now();
        this.lastEndedEventAt = 0;
        this.lastEndedSrc = "";
      } else {
        this._src = state.src;
      }
    }

    if (typeof state.durationMs === "number") {
      const nextDuration = Math.max(0, state.durationMs) / 1000;
      if (nextDuration > 0) this._duration = nextDuration;
    }

    // 处理 paused / playing 状态
    let nextPaused: boolean | null = null;
    if (typeof state.paused === "boolean") {
      nextPaused = state.paused;
    } else if (typeof state.playing === "boolean") {
      nextPaused = !state.playing;
    }

    if (nextPaused !== null) {
      const wasPaused = this._paused;
      if (wasPaused !== nextPaused) {
        if (nextPaused) {
          // 切换到暂停：快照当前估算时间作为新基准
          this._currentTime = this.estimateCurrentTime();
        }
        this._paused = nextPaused;
        this.lastTimeSyncAt = performance.now();
        this.dispatchEvent(new Event(nextPaused ? AUDIO_EVENTS.PAUSE : AUDIO_EVENTS.PLAY));
      }
    }

    if (typeof state.ready === "boolean" && state.ready) {
      this.dispatchEvent(new Event(AUDIO_EVENTS.CAN_PLAY));
    }

    if (typeof state.playbackRate === "number" && state.playbackRate > 0) {
      this._rate = state.playbackRate;
    }

    if (typeof state.errorCode === "number" && state.errorCode !== 0) {
      this._errorCode = state.errorCode;
    }
  }

  // 兼容 IPlaybackEngine 可选接口
  public rampVolumeTo(value: number, _duration: number, _curve?: FadeCurve): void {
    this.setVolume(value);
  }
}

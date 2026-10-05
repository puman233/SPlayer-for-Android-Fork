<script lang="ts">
type MobilePageType = "comment" | "info" | "lyric";
let savedPageType: MobilePageType = "info";
</script>

<template>
  <div
    ref="mobileStart"
    :class="[
      'full-player-mobile',
      {
        'pad-portrait': isPadDevice,
        'phone-portrait': isPhoneDevice,
        'controls-hidden': !controlsVisible,
        'lyric-active': currentPageType === 'lyric',
        'comment-active': currentPageType === 'comment',
      },
    ]"
    :style="{
      '--lyric-h-offset': lyricHeaderHorizontalPadding,
      '--cover-size': `${Math.max(0, Math.min(coverWidth, coverHeight / (settingStore.playerType === 'record' ? 1.45 : 1), 380))}px`,
      '--top-bar-height': `${topBarHeight}px`,
      '--lyric-header-height': `${lyricHeaderHeight}px`,
      '--lyric-footer-height': `${lyricFooterHeight}px`,
      '--cover-bottom': `${coverBottom}px`,
      '--pad-portrait-lrc-size': padPortraitLyricSize,
      '--pad-portrait-lrc-tran-size': padPortraitLyricTranSize,
      '--pad-portrait-lrc-roma-size': padPortraitLyricRomaSize,
    }"
    @pointerdown.capture="pointerDown"
    @touchstart.capture.passive="touchStart"
    @click.capture="interact"
    @wheel.capture.passive="interact"
    @keydown.capture="interact"
  >
    <Transition name="mobile-controls">
      <div
        v-show="controlsVisible || isPhoneDevice"
        ref="topBarRef"
        class="top-bar"
        :class="{ 'portrait-controls-hidden': isPhoneDevice && !controlsVisible }"
        :inert="isPhoneDevice && !controlsVisible"
        :aria-hidden="isPhoneDevice && !controlsVisible"
      >
        <!-- 左：进入横屏沉浸式（仅 Android 手机有意义） -->
        <div
          v-if="showPureLyricButton && currentPageType === 'lyric'"
          :class="['btn pure-btn', { open: statusStore.pureLyricMode }]"
          @click.stop="togglePureLyricMode"
        >
          <SvgIcon name="TextPlay" :size="26" />
        </div>
        <div v-else-if="canEnterImmersive" class="btn" @click.stop="enterImmersive">
          <SvgIcon name="Fullscreen" :size="24" />
        </div>
        <div v-else class="btn-placeholder" aria-hidden="true" />
        <!-- 右：下拉关闭 -->
        <div class="btn" @click.stop="statusStore.showFullPlayer = false">
          <SvgIcon name="Down" :size="26" />
        </div>
      </div>
    </Transition>

    <!-- 下拉手势捕获区：信息页覆盖顶栏 + 封面区域；歌词页限定在歌曲信息块 -->
    <div
      v-show="controlsVisible"
      ref="dragHandleRef"
      class="drag-handle"
      :style="dragHandleStyle"
      aria-hidden="true"
    />

    <div
      :class="[
        'mobile-content',
        { swiping: isHorizontalSwipe, 'no-transition': pageTransitionDisabled },
      ]"
      :style="{ transform: contentTransform, '--page-count': totalPages }"
      @click.stop
    >
      <div v-if="hasComment" class="page comment-page">
        <PlayerComment
          :active="pageIndex === commentIdx"
          :song-data-visible="controlsVisible"
          embedded
          class="mobile-comment"
        />
      </div>

      <div class="page info-page">
        <div ref="coverSectionRef" class="cover-section">
          <PlayerCover :no-lyric="true" />
        </div>

        <div class="info-group">
          <div class="song-info-bar">
            <div class="info-section">
              <PlayerData
                :center="false"
                :light="false"
                :phone-portrait="isPhoneDevice"
                class="mobile-data"
              >
                <template #actions="{ compact }">
                  <div class="info-actions" :class="{ compact }">
                    <div
                      v-if="musicStore.playSong.type !== 'radio'"
                      class="action-btn"
                      @click.stop="
                        toLikeSong(
                          musicStore.playSong,
                          !dataStore.isLikeSong(musicStore.playSong.id),
                        )
                      "
                    >
                      <SvgIcon
                        :name="
                          dataStore.isLikeSong(musicStore.playSong.id)
                            ? 'Favorite'
                            : 'FavoriteBorder'
                        "
                        :size="26"
                        :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
                      />
                    </div>
                    <n-button
                      v-if="isPhoneDevice"
                      text
                      class="portrait-desktop-lyric action-btn"
                      aria-label="桌面歌词"
                      :aria-pressed="statusStore.showDesktopLyric"
                      @click.stop="player.setDesktopLyricShow(!statusStore.showDesktopLyric)"
                    >
                      <SvgIcon
                        name="DesktopLyric"
                        :size="26"
                        :class="{ liked: statusStore.showDesktopLyric }"
                      />
                    </n-button>
                    <div
                      v-if="!compact"
                      class="action-btn"
                      @click.stop="
                        openPlaylistAdd([musicStore.playSong], !!musicStore.playSong.path)
                      "
                    >
                      <SvgIcon name="AddList" :size="26" />
                    </div>
                    <n-badge
                      v-if="showPortraitPlaylistButton"
                      :value="dataStore.playList?.length ?? 0"
                      :show="settingStore.showPlaylistCount"
                      :max="9999"
                    >
                      <div class="action-btn" @click.stop="statusStore.playListShow = true">
                        <SvgIcon name="PlayList" :size="26" />
                      </div>
                    </n-badge>
                    <!-- 快捷操作菜单 -->
                    <PlayerQuickActionsMenu :compact-portrait="compact">
                      <template v-if="compact" #footer>
                        <div class="portrait-overflow-actions">
                          <n-button
                            text
                            @click="
                              openPlaylistAdd([musicStore.playSong], !!musicStore.playSong.path)
                            "
                            ><SvgIcon name="AddList" :size="24" />添加到歌单</n-button
                          >
                        </div>
                      </template>
                    </PlayerQuickActionsMenu>
                  </div>
                </template>
              </PlayerData>
            </div>
          </div>
        </div>
      </div>

      <div v-if="hasLyric" class="page lyric-page">
        <Transition name="mobile-controls">
          <div
            ref="lyricHeaderRef"
            v-show="controlsVisible || isPhoneDevice"
            class="lyric-header"
            :class="{ 'portrait-controls-hidden': isPhoneDevice && !controlsVisible }"
            :inert="isPhoneDevice && !controlsVisible"
            :aria-hidden="isPhoneDevice && !controlsVisible"
          >
            <div
              class="lyric-cover"
              data-no-page-swipe
              @pointerdown.stop
              @pointerup="onLyricCoverPointerUp"
            >
              <s-image
                :src="musicStore.getSongCover('s')"
                cache-type="covers"
                class="lyric-cover-image"
              />
            </div>
            <div class="lyric-info">
              <div class="name text-hidden">
                {{
                  settingStore.hideBracketedContent
                    ? removeBrackets(musicStore.playSong.name)
                    : musicStore.playSong.name
                }}
              </div>
              <div class="artist text-hidden">{{ artistName }}</div>
            </div>
            <div
              v-if="musicStore.playSong.type !== 'radio'"
              class="action-btn"
              @click.stop="
                toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
              "
            >
              <SvgIcon
                :name="dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'"
                :size="24"
                :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
              />
            </div>
          </div>
        </Transition>
        <div class="lyric-main">
          <PlayerLyric />
        </div>
      </div>
    </div>

    <Transition name="mobile-controls">
      <MobilePlayerBottomControls
        ref="lyricFooterRef"
        v-if="currentPageType !== 'comment'"
        v-show="controlsVisible || isPhoneDevice"
        :class="{ 'portrait-controls-hidden': isPhoneDevice && !controlsVisible }"
        :inert="isPhoneDevice && !controlsVisible"
        :aria-hidden="isPhoneDevice && !controlsVisible"
        :page-count="totalPages"
        :page-index="pageIndex"
        :large="isPadDevice"
        @update:page-index="pageIndex = $event"
      />
    </Transition>

    <!-- 移动端竖屏频谱：贴底浮层，与封面/歌词页共享展示 -->
    <PlayerSpectrum
      v-if="settingStore.showSpectrums && currentPageType !== 'comment'"
      class="mobile-spectrum"
      :color="statusStore.mainColor ? `rgb(${statusStore.mainColor})` : 'rgb(239 239 239)'"
      :show="true"
      :height="56"
    />
  </div>
</template>

<script setup lang="ts">
import { useMobilePlayerControls } from "@/composables/useMobilePlayerControls";
import type { ComponentPublicInstance } from "vue";
import { PLAYER_META_HOLD_KEY } from "@/composables/usePlayerMetaHold";
import { useSwipe, useElementSize, useElementBounding, useEventListener } from "@vueuse/core";
import { useMusicStore, useStatusStore, useDataStore, useSettingStore } from "@/stores";
import { useDevice } from "@/composables/useDevice";
import { useOrientationTransition } from "@/composables/useOrientationTransition";
import { isCapacitorAndroid } from "@/utils/env";
import { toLikeSong } from "@/utils/auth";
import { openPlaylistAdd } from "@/utils/modal";
import { removeBrackets } from "@/utils/format";
import { usePlayerController } from "@/core/player/PlayerController";
import { getFontSize } from "@/utils/style";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const dataStore = useDataStore();
const player = usePlayerController();

const LYRIC_HEADER_MAX_PADDING = 60;

// 沉浸式横屏入口：方向锁交给 native SENSOR_LANDSCAPE
// 走硬件层 isPhoneDevice，防止平板竖屏（布局是手机 UI）误冒出沉浸式入口
const { isPadDevice, isPhoneDevice, isPhonePortrait } = useDevice();
const canEnterImmersive = computed(() => isCapacitorAndroid && isPhoneDevice.value);
// 纯净模式按钮：手机竖屏 / 平板竖屏 + 有歌词 + 非电台
const showPureLyricButton = computed(
  () => isPhonePortrait.value && musicStore.isHasLrc && musicStore.playSong.type !== "radio",
);
const showPortraitPlaylistButton = computed(
  () => isPhonePortrait.value && !statusStore.personalFmMode,
);
const togglePureLyricMode = () => {
  statusStore.pureLyricMode = !statusStore.pureLyricMode;
};
// 接入电影感切换协调器：Backdrop + Hero + Stagger 三层动效
const orientationTransition = useOrientationTransition();
const enterImmersive = async () => {
  await orientationTransition.enter(musicStore.songCover);
};

// Hero 流转的起点位置（竖屏 cover 容器）
const coverSectionRef = ref<HTMLElement | null>(null);
watch(coverSectionRef, (el) => orientationTransition.setCoverEl(el, "portrait"));
onBeforeUnmount(() => orientationTransition.setCoverEl(null, "portrait"));

const mobileStart = ref<HTMLElement | null>(null);
const topBarRef = ref<HTMLElement | null>(null);
const lyricHeaderRef = ref<HTMLElement | null>(null);
const lyricFooterRef = ref<ComponentPublicInstance | null>(null);
const { height: lyricHeaderHeight } = useElementBounding(lyricHeaderRef);
const { height: lyricFooterHeight } = useElementBounding(lyricFooterRef);
const dragHandleRef = ref<HTMLElement | null>(null);
const { width: coverWidth, height: coverHeight } = useElementSize(coverSectionRef);
const { height: topBarHeight } = useElementSize(topBarRef);
const { bottom: coverViewportBottom } = useElementBounding(coverSectionRef);
const { top: playerViewportTop } = useElementBounding(mobileStart);
const coverBottom = computed(() => coverViewportBottom.value - playerViewportTop.value);

// 歌词/评论可用性
const hasLyric = computed(() => musicStore.isHasLrc && musicStore.playSong.type !== "radio");
// 纯净模式下仍保留评论页：用户可从最左侧滑出，按需查看评论
const hasComment = computed(() => {
  if (musicStore.playSong.path) return false;
  if (settingStore.fullscreenPlayerElements?.comments === false) return false;
  const id = musicStore.playSong.id;
  return typeof id === "number" && id > 0;
});

// 总页数（信息页恒存在，评论/歌词按需）
const totalPages = computed(() => 1 + (hasComment.value ? 1 : 0) + (hasLyric.value ? 1 : 0));
// 页面顺序: [评论 | 信息 | 歌词]
// 评论页索引（恒为 0，若不存在则 -1）
const commentIdx = computed(() => (hasComment.value ? 0 : -1));
// 信息页索引（评论存在则后移一位）
const infoIdx = computed(() => (hasComment.value ? 1 : 0));
// 歌词页索引（在信息页之后）
const lyricIdx = computed(() => (hasLyric.value ? infoIdx.value + 1 : -1));

// 上次访问的页面类型 → 当前布局下的实际索引；不存在则回退到信息页
const resolveSavedPageIndex = (type: MobilePageType): number => {
  if (type === "comment" && hasComment.value) return commentIdx.value;
  if (type === "lyric" && hasLyric.value) return lyricIdx.value;
  return infoIdx.value;
};

// 默认落在「信息」页，并尽量恢复上次浏览的页面类型
const pageIndex = ref(resolveSavedPageIndex(savedPageType));
const pageTransitionDisabled = ref(false);
const pageSwipeBlocked = ref(false);
let pageTransitionTimer = 0;
let lastLyricCoverTapAt = 0;
let lastLyricCoverTapX = 0;
let lastLyricCoverTapY = 0;

const LYRIC_COVER_DOUBLE_TAP_DELAY = 320;
const LYRIC_COVER_DOUBLE_TAP_DISTANCE = 24;

const applyGlobalLyricOffsetToCurrentSong = () => {
  if (!settingStore.globalLyricOffsetEnabled || !settingStore.globalLyricOffsetDoubleClickApply) {
    return;
  }
  const currentSongId = musicStore.playSong?.id;
  if (!currentSongId) return;

  const offsetValue = settingStore.globalLyricOffsetValue;
  const currentOffset = statusStore.getSongOffset(currentSongId);
  const sign = offsetValue > 0 ? "+" : "";

  if (settingStore.globalLyricOffsetAlwaysApply) {
    if (currentOffset === offsetValue) {
      statusStore.setSongOffset(currentSongId, -offsetValue);
      window.$message?.success("本歌曲将临时关闭偏移");
    } else {
      statusStore.resetSongOffset(currentSongId);
      window.$message?.success(`已恢复全局偏移: ${sign}${offsetValue}ms`);
    }
  } else {
    if (currentOffset === offsetValue) {
      statusStore.resetSongOffset(currentSongId);
      window.$message?.success(`已关闭单曲偏移: ${sign}${offsetValue}ms`);
    } else {
      statusStore.setSongOffset(currentSongId, offsetValue);
      window.$message?.success(`已开启单曲偏移: ${sign}${offsetValue}ms`);
    }
  }
};

// 歌词页封面使用双点，避免和移动端下滑手势冲突
const onLyricCoverPointerUp = (event: PointerEvent) => {
  event.stopPropagation();
  if (event.cancelable) event.preventDefault();
  if (event.pointerType === "mouse" && event.button !== 0) return;

  const now = Date.now();
  const dx = event.clientX - lastLyricCoverTapX;
  const dy = event.clientY - lastLyricCoverTapY;
  const isDoubleTap =
    now - lastLyricCoverTapAt <= LYRIC_COVER_DOUBLE_TAP_DELAY &&
    Math.hypot(dx, dy) <= LYRIC_COVER_DOUBLE_TAP_DISTANCE;

  if (isDoubleTap) {
    lastLyricCoverTapAt = 0;
    applyGlobalLyricOffsetToCurrentSong();
    return;
  }

  lastLyricCoverTapAt = now;
  lastLyricCoverTapX = event.clientX;
  lastLyricCoverTapY = event.clientY;
};

const lyricHeaderHorizontalPadding = computed(() => {
  const padding = Math.max(0, settingStore.lyricHorizontalOffset);
  return `${Math.min(padding, LYRIC_HEADER_MAX_PADDING)}px`;
});

const padPortraitLyricSize = computed(() =>
  getFontSize(Math.min(settingStore.lyricFontSize, 40), settingStore.lyricFontSizeMode),
);
const padPortraitLyricTranSize = computed(() =>
  getFontSize(Math.min(settingStore.lyricTranFontSize, 20), settingStore.lyricFontSizeMode),
);
const padPortraitLyricRomaSize = computed(() =>
  getFontSize(Math.min(settingStore.lyricRomaFontSize, 16), settingStore.lyricFontSizeMode),
);

// 当前页面类型
const currentPageType = computed<MobilePageType>(() => {
  if (pageIndex.value === commentIdx.value) return "comment";
  if (pageIndex.value === lyricIdx.value) return "lyric";
  return "info";
});

const {
  visible: controlsVisible,
  interact,
  pointerDown,
  pointerEnd,
  touchStart,
  touchEnd,
  resetPointers,
  hold,
} = useMobilePlayerControls(currentPageType, isPhoneDevice.value ? 2000 : 4000);
provide(PLAYER_META_HOLD_KEY, hold);
// 只观察事件，不消费歌词点击、滚动、长按和进度条拖动
useEventListener(window, "pointerup", pointerEnd, { capture: true, passive: true });
useEventListener(window, "pointercancel", pointerEnd, { capture: true, passive: true });
useEventListener(window, "touchend", touchEnd, { capture: true, passive: true });
useEventListener(window, "touchcancel", touchEnd, { capture: true, passive: true });
useEventListener(window, "blur", resetPointers);

// 下拉关闭手势捕获区：信息页覆盖顶栏 + 封面区域；歌词页限定在歌曲信息块
const dragHandleStyle = computed(() => {
  if (currentPageType.value === "info") {
    return {
      top: "0",
      left: "0",
      right: "0",
      height: "var(--cover-bottom)",
    };
  }
  if (currentPageType.value === "lyric") {
    return {
      top: "var(--top-bar-height)",
      // 歌词页保留下滑手势，但避开左侧封面区域，防止封面双点被捕获层吃掉
      left: "calc(20px + var(--lyric-h-offset, 0px) + 72px)",
      right: "72px",
      height: "74px",
    };
  }
  return {
    top: "0",
    left: "0",
    right: "0",
    height: "var(--top-bar-height)",
  };
});

// 顶部区域下拉关闭：整个全屏播放器（含背景蒙层）跟手下移，露出底部主页面
let dragValue = 0;
const CLOSE_THRESHOLD = 120;
const REVEAL_DISTANCE = 480;
const SPRING_TRANSITION =
  "transform 0.32s cubic-bezier(0.22, 1, 0.36, 1), opacity 0.32s cubic-bezier(0.22, 1, 0.36, 1)";

// 缓存目标元素，避免 touchmove 每帧 DOM 查询
let parentEl: HTMLElement | null = null;
let mainEl: HTMLElement | null = null;
let rafId = 0;
let pendingDy = 0;

const writeStyles = (dy: number) => {
  if (parentEl) {
    if (dy <= 0) {
      parentEl.style.transform = "";
    } else {
      const scale = Math.max(0.92, 1 - dy / 2400);
      parentEl.style.transform = `translate3d(0, ${dy}px, 0) scale(${scale})`;
    }
  }
  if (mainEl) {
    if (dy <= 0) {
      mainEl.style.opacity = "";
      mainEl.style.transform = "";
    } else {
      const progress = Math.min(dy / REVEAL_DISTANCE, 1);
      mainEl.style.opacity = String(progress);
      mainEl.style.transform = `scale(${0.9 + progress * 0.1})`;
    }
  }
};

// 通过 rAF 节流，避免 touchmove 高频写样式导致掉帧
const scheduleFlush = (dy: number) => {
  pendingDy = dy;
  if (rafId) return;
  rafId = requestAnimationFrame(() => {
    rafId = 0;
    writeStyles(pendingDy);
  });
};

const beginDrag = () => {
  parentEl = (mobileStart.value?.parentElement as HTMLElement | null) ?? null;
  mainEl = document.getElementById("main");
  // 拖动期间禁用过渡 + 提示合成层，避免每帧重排
  if (parentEl) {
    parentEl.style.transition = "none";
    parentEl.style.willChange = "transform";
    parentEl.style.transformOrigin = "50% 0";
    parentEl.style.borderRadius = "28px";
    parentEl.style.backfaceVisibility = "hidden";
    parentEl.style.backdropFilter = "blur(48px)";
    parentEl.style.contain = "paint";
  }
  if (mainEl) {
    mainEl.style.transition = "none";
    mainEl.style.willChange = "transform, opacity";
  }
};

const clearWillChange = () => {
  if (parentEl) parentEl.style.willChange = "";
  if (mainEl) mainEl.style.willChange = "";
};

const resetInlineStyles = () => {
  if (parentEl) {
    parentEl.style.transition = "";
    parentEl.style.transform = "";
    parentEl.style.borderRadius = "";
    parentEl.style.transformOrigin = "";
    parentEl.style.willChange = "";
    parentEl.style.backfaceVisibility = "";
    parentEl.style.backdropFilter = "";
    parentEl.style.contain = "";
  }
  if (mainEl) {
    mainEl.style.transition = "";
    mainEl.style.opacity = "";
    mainEl.style.transform = "";
    mainEl.style.willChange = "";
  }
};

// 方向锁，避免左右翻页手势触发下拉
let directionLock: "h" | "v" | null = null;
let dragStarted = false;
const DIRECTION_LOCK_TOLERANCE = 8;

const { lengthX: topLengthX, lengthY: topLengthY } = useSwipe(dragHandleRef, {
  threshold: 0,
  passive: true,
  onSwipeStart: () => {
    directionLock = null;
    dragStarted = false;
  },
  onSwipe: () => {
    // 横向手势锁定后直接放行给翻页 useSwipe 处理
    if (directionLock === "h") return;
    if (!directionLock) {
      const ax = Math.abs(topLengthX.value);
      const ay = Math.abs(topLengthY.value);
      if (Math.max(ax, ay) < DIRECTION_LOCK_TOLERANCE) return;
      directionLock = ay > ax ? "v" : "h";
      if (directionLock === "h") return;
    }
    if (!dragStarted) {
      beginDrag();
      dragStarted = true;
    }
    // useSwipe 的 lengthY = startY - currentY，下滑时为负值
    const dy = -topLengthY.value;
    // 仅允许向下拖动；上滑做强阻尼
    dragValue = dy >= 0 ? dy : dy * 0.2;
    scheduleFlush(Math.max(dragValue, 0));
  },
  onSwipeEnd: () => {
    const wasDragging = dragStarted;
    directionLock = null;
    dragStarted = false;
    if (!wasDragging) return;
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = 0;
    }
    if (dragValue > CLOSE_THRESHOLD) {
      // 触发关闭：清掉 inline transition 让父级 @leave 钩子从当前位移继续滑出
      if (parentEl) {
        parentEl.style.transition = "";
      }
      // 同步把主页面平滑回到完全可见，避免卡片滑出过程中仍然可见拖动半透明状态
      if (mainEl) {
        mainEl.style.transition =
          "opacity 0.32s ease, transform 0.32s cubic-bezier(0.22, 1, 0.36, 1)";
        mainEl.style.opacity = "1";
        mainEl.style.transform = "scale(1)";
      }
      statusStore.showFullPlayer = false;
      window.setTimeout(() => {
        clearWillChange();
        resetInlineStyles();
      }, 400);
    } else {
      // 取消关闭：弹簧回弹（border-radius 不参与过渡，立即清掉避免末尾突变）
      if (parentEl) {
        parentEl.style.transition = SPRING_TRANSITION;
        parentEl.style.borderRadius = "";
      }
      if (mainEl) mainEl.style.transition = SPRING_TRANSITION;
      writeStyles(0);
      window.setTimeout(() => {
        clearWillChange();
        resetInlineStyles();
      }, 360);
    }
    dragValue = 0;
  },
});

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId);
  if (pageTransitionTimer) window.clearTimeout(pageTransitionTimer);
  resetInlineStyles();
});

const disablePageTransitionOnce = () => {
  pageTransitionDisabled.value = true;
  if (pageTransitionTimer) window.clearTimeout(pageTransitionTimer);
  pageTransitionTimer = window.setTimeout(() => {
    pageTransitionTimer = 0;
    pageTransitionDisabled.value = false;
  }, 80);
};

const artistName = computed(() => {
  const artists = musicStore.playSong.artists;
  if (Array.isArray(artists)) {
    return artists.map((artist) => artist.name).join(" / ");
  }
  return (artists as string) || "未知艺术家";
});

// 页面可用性变化时按页面语义迁移 pageIndex
watch([hasComment, hasLyric], (_n, [prevHasComment, prevHasLyric]) => {
  disablePageTransitionOnce();
  const prevCommentIdx = prevHasComment ? 0 : -1;
  const prevInfoIdx = prevHasComment ? 1 : 0;
  const prevLyricIdx = prevHasLyric ? prevInfoIdx + 1 : -1;

  let prevType: MobilePageType = "info";
  if (pageIndex.value === prevCommentIdx) prevType = "comment";
  else if (pageIndex.value === prevLyricIdx) prevType = "lyric";

  pageIndex.value = resolveSavedPageIndex(prevType);
});

// 同步缓存页面语义
watch(currentPageType, (t) => {
  savedPageType = t;
});

const isNoPageSwipeTarget = (event: TouchEvent) => {
  const target = event.target;
  return target instanceof HTMLElement && Boolean(target.closest("[data-no-page-swipe]"));
};

const { direction, isSwiping, lengthX, lengthY } = useSwipe(mobileStart, {
  threshold: 5,
  passive: true,
  onSwipeStart: (event) => {
    pageSwipeBlocked.value = isNoPageSwipeTarget(event);
  },
  onSwipeEnd: () => {
    if (pageSwipeBlocked.value) {
      pageSwipeBlocked.value = false;
      return;
    }
    if (totalPages.value <= 1) return;
    // 仅在主方向为水平时触发翻页，避免上下滑动歌词/评论误触
    if (Math.abs(lengthX.value) <= Math.abs(lengthY.value)) return;

    if (direction.value === "left" && lengthX.value > 100) {
      pageIndex.value = Math.min(pageIndex.value + 1, totalPages.value - 1);
    } else if (direction.value === "right" && lengthX.value < -100) {
      pageIndex.value = Math.max(pageIndex.value - 1, 0);
    }
  },
});

// 当前滑动是否为水平方向（用于跟手位移）
const isHorizontalSwipe = computed(
  () =>
    !pageSwipeBlocked.value && isSwiping.value && Math.abs(lengthX.value) > Math.abs(lengthY.value),
);

const contentTransform = computed(() => {
  const pageWidthPct = 100 / totalPages.value;
  const baseOffset = pageIndex.value * pageWidthPct;
  if (!isHorizontalSwipe.value || totalPages.value <= 1) {
    return `translateX(-${baseOffset}%)`;
  }

  let pixelOffset = lengthX.value;
  if (pageIndex.value === 0 && pixelOffset < 0) {
    pixelOffset *= 0.3;
  }
  if (pageIndex.value === totalPages.value - 1 && pixelOffset > 0) {
    pixelOffset *= 0.3;
  }

  return `translateX(calc(-${baseOffset}% - ${pixelOffset}px))`;
});
</script>

<style lang="scss" scoped>
.full-player-mobile {
  --mobile-safe-top: var(--safe-area-top, env(safe-area-inset-top));
  --mobile-safe-bottom: var(--safe-area-bottom);

  width: 100%;
  height: 100%;
  position: relative;
  padding: 0 var(--safe-area-right) 0 var(--safe-area-left);
  overflow: hidden;
  display: flex;
  flex-direction: column;

  // 频谱贴底浮层：absolute 锚定容器底部，避开 PlayerSpectrum 默认 fixed + z-index:-1
  :deep(.mobile-spectrum) {
    position: absolute;
    z-index: 1;
    opacity: 0.45 !important;
  }

  .drag-handle {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 0;
    z-index: 5;
    pointer-events: auto;
    background: transparent;
    touch-action: none;
  }

  .top-bar {
    position: relative;
    flex: 0 0 auto;
    min-height: calc(56px + var(--mobile-safe-top));
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: var(--mobile-safe-top) 20px 0;
    z-index: 10;

    .btn-placeholder {
      width: 40px;
      height: 40px;
    }

    .btn {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.2s;

      &:active {
        background-color: rgba(255, 255, 255, 0.1);
      }

      .n-icon {
        color: rgb(var(--main-cover-color));
        opacity: 0.8;
      }

      &.pure-btn {
        border-radius: 8px;
        opacity: 0.6;
        transition:
          opacity 0.3s,
          background-color 0.3s,
          transform 0.3s;

        &.open {
          opacity: 1;
        }

        .n-icon {
          opacity: 1;
        }

        &:active {
          transform: scale(0.95);
        }
      }
    }
  }

  .mobile-content {
    flex: 1;
    min-height: 0;
    display: flex;
    width: calc(var(--page-count, 1) * 100%);
    height: auto;
    transition: transform 0.3s cubic-bezier(0.25, 1, 0.5, 1);
    // 横滑交给 useSwipe
    touch-action: pan-y;

    &.swiping,
    &.no-transition {
      transition: none;
    }

    .page {
      width: calc(100% / var(--page-count, 1));
      flex: 0 0 calc(100% / var(--page-count, 1));
      height: 100%;
      position: relative;
    }
  }

  .info-page {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 0 20px 12px;
    overflow: hidden;

    .cover-section {
      width: 100%;
      flex: 1 1 0;
      min-height: 0;
      margin-bottom: clamp(4px, 1.5dvh, 16px);
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;

      :deep(.player-cover) {
        width: var(--cover-size);
        max-width: 100%;
        flex-shrink: 0;
        &.record {
          margin-bottom: 0;
          .cover-img {
            width: 100%;
            height: 100%;
            min-width: 0;
          }
        }
      }
    }

    .info-group {
      flex: 0 1 auto;
      max-height: 60%;
      overflow-y: auto;
      overscroll-behavior: contain;
      width: 100%;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }

    .song-info-bar {
      width: 100%;
      margin-bottom: clamp(4px, 1.5dvh, 20px);

      .info-section {
        width: 100%;

        :deep(.mobile-data) {
          width: 100%;
          max-width: 100%;
          min-width: 0;
          margin-top: 0;
          padding: 0;

          .name {
            margin-left: 0;

            .name-text {
              max-width: 100%;
            }
          }

          .info-actions {
            display: flex;
            flex-wrap: nowrap;
            gap: 12px;
            align-items: center;
            flex-shrink: 0;
            margin-left: auto;
            // 保证 More 按钮与歌单计数徽标不重叠：给徽标容器足够右侧间距
            :deep(.n-badge) {
              margin-right: 12px;
            }

            .action-btn {
              flex-shrink: 0;
              min-width: 0;
            }

            .qa-trigger--mobile {
              width: 36px;
              height: 36px;
              flex-shrink: 0;
            }
          }
        }
      }
    }

    .action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 50%;
      cursor: pointer;
      transition: background-color 0.2s;

      &:active {
        background-color: rgba(255, 255, 255, 0.1);
      }

      .n-icon {
        color: rgb(var(--main-cover-color));
        opacity: 0.6;
        transition:
          opacity 0.2s,
          transform 0.2s;

        &.liked {
          fill: rgb(var(--main-cover-color));
          opacity: 1;
        }
      }
    }
  }

  .info-page :deep(.mobile-data) {
    .meta-actions-row {
      flex-wrap: wrap;
    }
    .play-meta {
      flex: 1 1 auto;
    }
    .artists,
    .album,
    .dj {
      min-width: 0;
    }
    .artists .n-icon,
    .album .n-icon,
    .dj .n-icon {
      flex-shrink: 0;
    }
    .ar-list {
      min-width: 0;
      flex: 1;
    }
    .ar-list .ar {
      display: inline;
      overflow-wrap: anywhere;
    }
    .album .name-text,
    .dj .name-text {
      min-width: 0;
      flex: 1;
    }
  }

  &.comment-active,
  &.controls-hidden {
    padding-bottom: var(--mobile-safe-bottom);
  }
  .mobile-controls-enter-active,
  .mobile-controls-leave-active {
    transition: opacity 0.2s ease;
  }
  .mobile-controls-enter-from,
  .mobile-controls-leave-to {
    opacity: 0;
  }

  .lyric-page {
    overflow: hidden;
    padding: 0 20px;
    display: flex;
    flex-direction: column;

    .lyric-header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      flex-shrink: 0;
      padding-top: 8px;
      padding-left: var(--lyric-h-offset, 0px);
      padding-right: var(--lyric-h-offset, 0px);
      // 喜欢按钮位于下拉手势区上方，需要抬高层级保持可点击
      .action-btn {
        position: relative;
        z-index: 11;
      }

      .lyric-cover {
        position: relative;
        z-index: 11;
        width: 50px;
        height: 50px;
        flex-shrink: 0;
        border-radius: 6px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        overflow: hidden;
        touch-action: manipulation;

        .lyric-cover-image {
          width: 100%;
          height: 100%;
        }

        :deep(img) {
          width: 100%;
          height: 100%;
          border-radius: 6px;
        }
      }

      .lyric-info {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        justify-content: center;

        .name {
          font-size: 18px;
          font-weight: bold;
          margin-bottom: 2px;
        }

        .artist {
          font-size: 13px;
          opacity: 0.6;
        }
      }
    }

    .lyric-main {
      flex: 1;
      min-height: 0;
      position: relative;
    }
  }

  .comment-page {
    padding: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    // 小屏视觉调优
    :deep(.mobile-comment) {
      .song-data {
        height: 80px;
        margin: 0 16px 12px;
        padding: 0 14px;

        .cover-img {
          width: 56px;
          height: 56px;
          border-radius: 10px;
        }

        .title {
          font-size: 17px;
        }

        .artist {
          font-size: 12px;
        }

        .actions {
          gap: 8px;

          .close {
            width: 36px;
            height: 36px;
          }
        }
      }

      .comment-scroll .n-scrollbar-content {
        padding: 0 16px;
      }

      .placeholder {
        height: 56px;
        padding-bottom: 10px;

        &:last-child {
          height: 0;
          padding-top: 24px;
        }

        .title {
          font-size: 18px;

          .n-icon {
            margin-right: 4px;
          }
        }
      }
    }
  }

  @media (max-width: 512px) {
    .top-bar {
      padding: var(--mobile-safe-top) 16px 0;
    }

    .info-page {
      padding: 0 16px 8px;

      .song-info-bar {
        margin-bottom: 16px;
      }
    }

    .lyric-page {
      padding: 0 16px;

      .lyric-header {
        gap: 12px;
      }
    }

    .comment-page {
      padding: 0;

      :deep(.mobile-comment) {
        .song-data {
          margin: 0 12px 10px;
          padding: 0 12px;
        }

        .comment-scroll {
          .n-scrollbar-content {
            padding: 0 12px;
          }
        }
      }
    }
  }

  &.phone-portrait {
    &.controls-hidden {
      padding-bottom: var(--mobile-safe-bottom);
      padding-top: var(--mobile-safe-top);
      .top-bar,
      .lyric-header,
      .mobile-player-bottom-controls {
        position: absolute;
        left: 0;
        width: 100%;
      }
      .top-bar,
      .lyric-header {
        top: 0;
      }
      .mobile-player-bottom-controls {
        bottom: 0;
      }
    }
    .portrait-controls-hidden {
      opacity: 0;
      visibility: hidden;
      pointer-events: none;
    }
    .top-bar,
    .lyric-header,
    .mobile-player-bottom-controls {
      transition:
        opacity 0.2s ease,
        visibility 0.2s;
    }
    // 仅歌词页使用覆盖控件；播放页保留原有流式排版。
    &.lyric-active {
      padding-top: var(--mobile-safe-top);
      padding-bottom: var(--mobile-safe-bottom);
      .top-bar,
      .mobile-player-bottom-controls {
        position: absolute;
        left: 0;
        width: 100%;
      }
      .top-bar {
        top: 0;
      }
      .mobile-player-bottom-controls {
        bottom: 0;
        z-index: 12;
      }
      .lyric-page .lyric-header {
        position: absolute;
        top: var(--top-bar-height);
        left: 0;
        width: 100%;
        padding-inline: calc(16px + var(--lyric-h-offset, 0px));
        z-index: 11;
      }
      .lyric-main {
        clip-path: inset(
          calc(var(--top-bar-height) + var(--lyric-header-height) + 20px) 0
            max(0px, calc(var(--lyric-footer-height) - var(--mobile-safe-bottom)))
        );
      }
      &.controls-hidden .lyric-main {
        clip-path: none;
      }
    }
    .info-page .song-info-bar .info-section :deep(.mobile-data) {
      .meta-actions-row {
        flex-wrap: nowrap;
        gap: 4px;
      }
      .play-meta {
        flex: 0 0 auto;
      }
      .info-actions {
        gap: 8px;
        align-self: flex-end;
        .action-btn,
        .qa-trigger--mobile {
          width: 48px;
          height: 48px;
        }
        &.compact {
          gap: 2px;
          > .n-badge {
            margin-right: 4px;
          }
          .action-btn,
          .qa-trigger--mobile {
            width: 40px;
          }
        }
      }
    }
  }

  &.pad-portrait {
    .top-bar {
      min-height: calc(72px + var(--mobile-safe-top));
      padding: var(--mobile-safe-top) 32px 0;

      .btn,
      .btn-placeholder {
        width: 52px;
        height: 52px;
      }
    }

    .info-page {
      padding: 0 clamp(32px, 6vw, 56px) 16px;

      .info-group {
        max-width: 640px;
      }

      .song-info-bar {
        margin-bottom: 28px;

        .info-section {
          :deep(.mobile-data) {
            .name .name-text {
              font-size: 34px;
            }

            .alia {
              font-size: 22px;
            }

            .artists .ar-list .ar,
            .album,
            .dj {
              font-size: 20px;
            }

            .play-meta .meta-item {
              font-size: 14px;
              padding: 3px 8px;
            }

            .info-actions {
              gap: 16px;
              flex-wrap: nowrap;

              :deep(.n-badge) {
                margin-right: 16px;
              }

              .qa-trigger--mobile {
                width: 52px;
                height: 52px;
                flex-shrink: 0;
              }
            }
          }
        }
      }

      .action-btn {
        width: 52px;
        height: 52px;
      }
    }

    .lyric-page {
      padding: 0 20px;

      .lyric-header {
        gap: 20px;
        margin-bottom: 28px;

        .lyric-cover {
          width: 64px;
          height: 64px;
          border-radius: 10px;

          .lyric-cover-image {
            width: 100%;
            height: 100%;
          }

          :deep(img) {
            border-radius: 10px;
          }
        }

        .lyric-info {
          .name {
            font-size: 22px;
          }

          .artist {
            font-size: 15px;
          }
        }

        .action-btn {
          width: 52px;
          height: 52px;
        }
      }

      .lyric-main {
        flex: 1;
        --lrc-size: var(--pad-portrait-lrc-size);
        --lrc-tran-size: var(--pad-portrait-lrc-tran-size);
        --lrc-roma-size: var(--pad-portrait-lrc-roma-size);
        --lrc-left-padding: var(--lyric-h-offset, 0px);
        --amll-lyric-left-padding: var(--lyric-h-offset, 0px);
        --amll-lyric-horizontal-padding: var(--lyric-h-offset, 0px);

        :deep(.player-lyric) {
          mask: linear-gradient(
            180deg,
            hsla(0, 0%, 100%, 0) 0,
            hsla(0, 0%, 100%, 0.6) 4%,
            #fff 9%,
            #fff 78%,
            hsla(0, 0%, 100%, 0.6) 90%,
            hsla(0, 0%, 100%, 0)
          );
        }

        :deep(.lyric-scroll-container) {
          padding-left: var(--lyric-h-offset, 0px);
          padding-right: 20px;
        }

        :deep(.am-lyric) {
          padding: 0;
        }

        :deep(.am-lyric .amll-lyric-player > div) {
          padding-left: var(--lyric-h-offset, 0px);
          padding-right: var(--lyric-h-offset, 0px);
        }

        :deep(.lyric-menu) {
          display: none;
        }
      }
    }
  }
}
.portrait-overflow-actions {
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 4px;
  .n-button {
    min-height: 48px;
    justify-content: flex-start;
  }
  .n-icon {
    margin-right: 8px;
  }
}
</style>

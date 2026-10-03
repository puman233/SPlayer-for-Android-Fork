<template>
  <div
    class="full-player-mobile-landscape"
    :style="{
      '--amll-landscape-font-size': amllLandscapeFontSize,
      '--lrc-landscape-size': lrcLandscapeSize,
      '--lrc-landscape-tran-size': lrcLandscapeTranSize,
      '--lrc-landscape-roma-size': lrcLandscapeRomaSize,
      '--landscape-cover-size': `${coverSize}px`,
      '--landscape-lyric-padding-x': landscapeLyricPaddingX,
    }"
  >
    <PlayerMenu persistent />
    <div class="landscape-content">
      <!-- 左：封面 + 紧凑信息 -->
      <div ref="leftRef" class="left-section" :class="{ 'show-comment': showComment }">
        <PlayerComment
          v-if="showComment"
          class="landscape-comment"
          embedded
          :active="showComment"
        />
        <template v-else>
          <div ref="coverRef" class="landscape-cover" data-stagger="cover">
            <!-- 复用 PlayerCover：跟随 settingStore.playerType / dynamicCover 走动态封面逻辑 -->
            <PlayerCover />
          </div>
          <div ref="infoRef" class="info" data-stagger="title">
            <PlayerData :center="true" :light="false" />
          </div>
        </template>
      </div>

      <!-- 右：歌词 -->
      <div class="right-section" data-stagger="lyric">
        <PlayerLyric v-if="!noLrc" persistent />
        <div v-else class="no-lrc">
          <SvgIcon name="MusicNote" :size="36" :depth="3" />
          <span>暂无歌词</span>
        </div>
      </div>
    </div>
    <PlayerControl persistent />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useMusicStore, useSettingStore, useStatusStore } from "@/stores";
import { useOrientationTransition } from "@/composables/useOrientationTransition";
import PlayerComment from "@/components/Player/PlayerComponents/PlayerComment.vue";
import PlayerLyric from "@/components/Player/PlayerLyric/index.vue";
import PlayerCover from "@/components/Player/PlayerMeta/PlayerCover.vue";
import PlayerData from "@/components/Player/PlayerMeta/PlayerData.vue";
import PlayerMenu from "@/components/Player/PlayerMenu.vue";
import PlayerControl from "@/components/Player/PlayerControl.vue";
import { useResizeObserver } from "@vueuse/core";

const musicStore = useMusicStore();
const settingStore = useSettingStore();
const statusStore = useStatusStore();

// Hero 流转的终点位置（横屏 cover 容器）
const orientationTransition = useOrientationTransition();
const coverRef = ref<HTMLElement | null>(null);
watch(coverRef, (el) => orientationTransition.setCoverEl(el, "landscape"));
onBeforeUnmount(() => orientationTransition.setCoverEl(null, "landscape"));

const noLrc = computed(() => {
  const noNormalLrc = !musicStore.isHasLrc;
  const noYrcAvailable = !musicStore.isHasYrc || !settingStore.showWordLyrics;
  return noNormalLrc && noYrcAvailable;
});

const showComment = computed(
  () =>
    statusStore.showPlayerComment &&
    !musicStore.playSong.path &&
    (!statusStore.effectivePureLyricMode || statusStore.isImmersiveFullscreen),
);

// 字号独立绑定 lyricFontSizeLandscape；翻译/罗马音按 0.5 / 0.43 缩放
const amllLandscapeFontSize = computed(() => `${settingStore.lyricFontSizeLandscape}px`);
const lrcLandscapeSize = computed(() => `${settingStore.lyricFontSizeLandscape}px`);
const lrcLandscapeTranSize = computed(
  () => `${Math.round(settingStore.lyricFontSizeLandscape * 0.5)}px`,
);
const lrcLandscapeRomaSize = computed(
  () => `${Math.round(settingStore.lyricFontSizeLandscape * 0.43)}px`,
);

// 测量内容区和元信息，封面只使用真实剩余空间
const leftRef = ref<HTMLElement | null>(null);
const infoRef = ref<HTMLElement | null>(null);
const coverSize = ref(0);
const measureCover = () => {
  const left = leftRef.value;
  if (!left) return;
  const style = getComputedStyle(left);
  const usableWidth =
    left.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
  const usableHeight =
    left.clientHeight -
    parseFloat(style.paddingTop) -
    parseFloat(style.paddingBottom) -
    (infoRef.value?.offsetHeight || 0) -
    parseFloat(style.rowGap || "0");
  const ratio = settingStore.playerType === "record" ? 1.45 : 1;
  coverSize.value = Math.max(0, Math.min(usableWidth, usableHeight / ratio, 320));
};
useResizeObserver([leftRef, infoRef], measureCover);
watch(() => settingStore.playerType, measureCover, { flush: "post" });
const landscapeLyricPaddingX = computed(() => `${settingStore.landscapeLyricPaddingX}px`);
</script>

<style lang="scss" scoped>
.full-player-mobile-landscape {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  container: landscape-player / inline-size;
  box-sizing: border-box;
  padding: var(--safe-area-top, 0px) var(--safe-area-right, 0px) var(--safe-area-bottom, 0px)
    var(--safe-area-left, 0px);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  color: rgb(var(--main-cover-color));

  .landscape-content {
    display: grid;
    grid-template-columns: minmax(0, 0.4fr) minmax(0, 0.6fr);
    grid-template-rows: minmax(0, 1fr);
    flex: 1;
    min-height: 0;
    overflow: hidden;
    container-type: size;
  }
  :deep(.player-menu) {
    position: relative;
    flex: 0 0 auto;
    min-height: 48px;
    .drag-dom {
      height: 48px;
      margin: 0;
    }
    .menu-icon {
      width: 48px;
      height: 48px;
    }
  }
  :deep(.player-control) {
    position: relative;
    flex: 0 0 auto;
    height: auto;
    --play-control-touch-size: 48px;
    --play-control-gap: 0px;
    overflow: visible;
    .control-content {
      grid-template-columns: repeat(2, minmax(0, 1fr));
      grid-template-rows: auto auto;
      gap: 4px;
    }
    .center {
      grid-column: 1 / -1;
      grid-row: 1;
      min-width: 0;
      max-height: none;
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 8px;
    }
    .left,
    .right {
      min-width: 0;
      grid-row: 2;
      height: auto;
      padding: 0;
      overflow-x: auto;
      flex-wrap: nowrap;
      gap: 0;
    }
    .right-menu {
      flex-wrap: nowrap;
      gap: 0;
      width: max-content;
    }
    .menu-icon {
      width: 48px;
      height: 48px;
      box-sizing: border-box;
      flex-shrink: 0;
    }
    .left > * {
      flex-shrink: 0;
    }
    .btn {
      gap: 0;
      width: 100%;
      justify-content: center;
    }
    .btn-icon {
      margin: 0;
      width: 48px;
      height: 48px;
      flex: 1 1 0;
    }
    .slider {
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
    }
  }

  @container landscape-player (max-width: 560px) {
    :deep(.player-control .center) {
      grid-template-columns: minmax(0, 1fr);
    }
  }

  .left-section {
    min-width: 0;
    min-height: 0;
    box-sizing: border-box;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 8px 16px;
    gap: 12px;

    &.show-comment {
      align-items: stretch;
      justify-content: stretch;
      padding: 6px 10px;
      gap: 0;
      transform: none;
    }

    .landscape-comment {
      width: 100%;
      height: 100%;
      min-height: 0;
      border-radius: 14px;
      background-color: rgba(var(--main-cover-color), 0.06);
      :deep(.song-data) {
        height: 64px;
        margin: 0 0 8px;
        padding: 0 10px;
        border-radius: 10px;
      }
      :deep(.song-data .cover-img) {
        width: 44px;
        height: 44px;
        border-radius: 9px;
      }
      :deep(.song-data .title) {
        font-size: 14px;
      }
      :deep(.song-data .artist) {
        font-size: 12px;
      }
      :deep(.song-data .actions) {
        gap: 6px;
      }
      :deep(.song-data .actions .close) {
        width: 32px;
        height: 32px;
      }
      :deep(.comment-scroll .n-scrollbar-content) {
        padding: 0 8px;
      }
      :deep(.placeholder) {
        height: 54px;
        padding-bottom: 10px;
      }
      :deep(.placeholder .title) {
        font-size: 16px;
      }
    }

    .landscape-cover {
      // 参照原封面逻辑，横屏限制上限
      width: var(--landscape-cover-size);
      height: auto;
      aspect-ratio: v-bind('settingStore.playerType === "record" ? 1 / 1.45 : 1');
      flex-shrink: 0;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 12px 28px rgba(0, 0, 0, 0.28);
      background-color: rgba(255, 255, 255, 0.06);
      // 重置 PlayerCover 默认尺寸约束
      :deep(.player-cover) {
        width: 100% !important;
        height: 100% !important;
        max-width: none !important;
        max-height: none !important;
        border-radius: 0 !important;
        background: transparent !important;
        box-shadow: none !important;
      }
      :deep(.cover-img),
      :deep(.dynamic-cover) {
        width: 100% !important;
        height: 100% !important;
        object-fit: cover;
      }
    }

    .info {
      width: 100%;
      min-height: 0;
      max-height: 60%;
      overflow: auto;
      flex-shrink: 0;
      text-align: center;
      :deep(.player-data) {
        width: 100%;
        max-width: none;
        margin-top: 0;
        padding: 0;
      }
      :deep(.name-text) {
        font-size: clamp(16px, 3cqh, 24px);
      }

      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 3px;
    }
  }

  .right-section {
    flex: 1;
    height: 100%;
    min-width: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    mix-blend-mode: var(--lyric-blend-mode);
    overflow: hidden;

    .no-lrc {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 100%;
      opacity: 0.6;
      font-size: 13px;
    }

    // === 修复 DefaultLyric 横屏被 300px placeholder 顶下 ===
    :deep(.default-lyric),
    :deep(.lyric-scroll-container) {
      // 歌词区保留菜单宽度，避免文字被菜单覆盖
      padding-right: max(52px, var(--landscape-lyric-padding-x, 20px)) !important;
      padding-left: var(--landscape-lyric-padding-x, 20px) !important;
    }
    :deep(.lyric-scroll-container) {
      .placeholder:first-child {
        // 原 300px 顶占位会把横屏歌词挤出
        height: 35cqh !important;
      }
    }
    // 菜单保留且可滚动，不以隐藏功能换取空间
    :deep(.lyric-menu) {
      width: 48px;
      padding: 0;
      opacity: 0.8;
      pointer-events: auto;
      overflow-y: auto;
      justify-content: flex-start;
      gap: 4px;
      .menu-icon {
        min-height: 48px;
        flex-shrink: 0;
      }
      .time {
        min-height: 36px;
        flex-shrink: 0;
      }
    }

    // === AMLL：字号独立 ===
    // AMLyric 在 .amll-lyric-player 用 inline style 设 --amll-lp-font-size，
    // 必须 !important 直接覆盖
    :deep(.amll-lyric-player) {
      --amll-lp-font-size: var(--amll-landscape-font-size) !important;
    }
    :deep(.am-lyric) {
      // 收紧 AMLL 左右 padding（原硬编 80px），改为滑块控制
      padding-right: max(52px, var(--landscape-lyric-padding-x, 20px)) !important;
      padding-left: var(--landscape-lyric-padding-x, 20px) !important;
    }

    // === DefaultLyric：三组字号独立 ===
    // DefaultLyric inline style 设 --lrc-size 等，必须 !important 覆盖
    :deep(.lyric) {
      --lrc-size: var(--lrc-landscape-size) !important;
      --lrc-tran-size: var(--lrc-landscape-tran-size) !important;
      --lrc-roma-size: var(--lrc-landscape-roma-size) !important;
    }
  }
}
</style>

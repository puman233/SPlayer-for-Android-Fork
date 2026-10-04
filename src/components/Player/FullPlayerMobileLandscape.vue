<template>
  <div
    ref="rootRef"
    class="full-player-mobile-landscape"
    :data-controls-visible="controls.visible.value"
    :data-width-class="windowInfo.widthClass"
    :data-height-class="windowInfo.heightClass"
    :style="{
      '--amll-landscape-font-size': `${settingStore.lyricFontSizeLandscape}px`,
      '--lrc-landscape-size': `${settingStore.lyricFontSizeLandscape}px`,
      '--lrc-landscape-tran-size': `${Math.round(settingStore.lyricFontSizeLandscape * 0.5)}px`,
      '--lrc-landscape-roma-size': `${Math.round(settingStore.lyricFontSizeLandscape * 0.43)}px`,
      '--landscape-cover-size': `${coverSize}px`,
      '--landscape-lyric-padding-x': `${settingStore.landscapeLyricPaddingX}px`,
    }"
    @click.capture="controls.interact"
    @pointerdown.capture="controls.pointerDown"
    @touchstart.capture.passive="controls.touchStart"
    @keydown.capture="controls.interact"
  >
    <header class="landscape-header">
      <n-button class="collapse" circle quaternary aria-label="收起播放器" @click.stop="collapse">
        <template #icon><SvgIcon name="Down" :size="24" /></template>
      </n-button>
    </header>
    <!-- 控制层独立于内容；隐藏时立即关闭命中，背景随控件淡出 -->
    <Transition name="landscape-controls">
      <section
        v-show="controls.visible.value"
        class="landscape-control-overlay"
        :inert="!controls.visible.value"
        :aria-hidden="!controls.visible.value"
        aria-label="播放控制"
      >
        <div class="landscape-tool-row">
          <div :id="metaTarget" class="landscape-meta" />
          <div :id="toolsTarget" class="landscape-tools" />
        </div>
        <PlayerControl persistent />
      </section>
    </Transition>
    <div class="landscape-content">
      <div ref="leftRef" class="left-section" :class="{ 'show-comment': showComment }">
        <PlayerComment
          v-if="showComment"
          class="landscape-comment"
          embedded
          :active="showComment"
        />
        <template v-else>
          <div ref="coverRef" class="landscape-cover" data-stagger="cover">
            <PlayerCover compact />
          </div>
          <div ref="infoRef" class="info" data-stagger="title">
            <PlayerData :center="true" :light="false" :meta-target="`#${metaTarget}`" />
          </div>
        </template>
      </div>
      <div class="right-section" data-stagger="lyric">
        <PlayerLyric
          v-if="!noLrc"
          :controls-visible="controls.visible.value"
          :tools-target="`#${toolsTarget}`"
        />
        <div v-else class="no-lrc">
          <SvgIcon name="MusicNote" :size="36" :depth="3" />
          <span>暂无歌词</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, provide, ref, useId, watch } from "vue";
import { useEventListener, useResizeObserver } from "@vueuse/core";
import { useMusicStore, useSettingStore, useStatusStore } from "@/stores";
import { useOrientationTransition } from "@/composables/useOrientationTransition";
import {
  useMobilePlayerControls,
  type MobilePlayerPage,
} from "@/composables/useMobilePlayerControls";
import { PLAYER_META_HOLD_KEY } from "@/composables/usePlayerMetaHold";
import { resolveAdaptiveWindow } from "@/core/layout/adaptiveWindow";
import PlayerComment from "@/components/Player/PlayerComponents/PlayerComment.vue";
import PlayerLyric from "@/components/Player/PlayerLyric/index.vue";
import PlayerCover from "@/components/Player/PlayerMeta/PlayerCover.vue";
import PlayerData from "@/components/Player/PlayerMeta/PlayerData.vue";
import PlayerControl from "@/components/Player/PlayerControl.vue";

const musicStore = useMusicStore();
const settingStore = useSettingStore();
const statusStore = useStatusStore();
const orientationTransition = useOrientationTransition();
const page = ref<MobilePlayerPage>("lyric");
const controls = useMobilePlayerControls(page, 3000, false);
provide(PLAYER_META_HOLD_KEY, controls.hold);
const metaTarget = `landscape-meta-${useId()}`;
const toolsTarget = `landscape-tools-${useId()}`;
const rootRef = ref<HTMLElement | null>(null);
const leftRef = ref<HTMLElement | null>(null);
const infoRef = ref<HTMLElement | null>(null);
const coverRef = ref<HTMLElement | null>(null);
const coverSize = ref(0);
const windowInfo = ref(resolveAdaptiveWindow(0, 0, { top: 0, right: 0, bottom: 0, left: 0 }));

watch(coverRef, (el) => orientationTransition.setCoverEl(el, "landscape"));
onBeforeUnmount(() => orientationTransition.setCoverEl(null, "landscape"));
useEventListener(window, "pointerup", controls.pointerEnd);
useEventListener(window, "pointercancel", controls.pointerEnd);
useEventListener(window, "touchend", controls.touchEnd, { passive: true });
useEventListener(window, "touchcancel", controls.touchEnd, { passive: true });
useEventListener(window, "blur", controls.resetPointers);

// 队列弹层开启时保持入口；其他菜单由子组件的 hold 接口处理。
const queueOpen = computed(() => statusStore.playListShow);
// 当前组件提供的接口无法在自身 inject，队列在此直接持有。
let queueHoldAcquired = false;
watch(
  queueOpen,
  (open) => {
    if (open && !queueHoldAcquired) {
      queueHoldAcquired = true;
      controls.hold.acquire();
    } else if (!open && queueHoldAcquired) {
      queueHoldAcquired = false;
      controls.hold.release();
    }
  },
  { immediate: true },
);

const collapse = async () => {
  if (statusStore.isImmersiveFullscreen) await orientationTransition.exit(musicStore.songCover);
  statusStore.showFullPlayer = false;
};
const noLrc = computed(
  () => !musicStore.isHasLrc && (!musicStore.isHasYrc || !settingStore.showWordLyrics),
);
const showComment = computed(
  () =>
    statusStore.showPlayerComment &&
    !musicStore.playSong.path &&
    (!statusStore.effectivePureLyricMode || statusStore.isImmersiveFullscreen),
);

// 封面只使用元信息之外的实际剩余高度，控制层不参与测量。
const measureContent = () => {
  const root = rootRef.value;
  const left = leftRef.value;
  if (!root || !left) return;
  const rootStyle = getComputedStyle(root);
  windowInfo.value = resolveAdaptiveWindow(
    root.clientWidth,
    root.clientHeight,
    {
      top: parseFloat(rootStyle.paddingTop) || 0,
      right: parseFloat(rootStyle.paddingRight) || 0,
      bottom: parseFloat(rootStyle.paddingBottom) || 0,
      left: parseFloat(rootStyle.paddingLeft) || 0,
    },
    parseFloat(rootStyle.fontSize) /
      parseFloat(getComputedStyle(document.documentElement).fontSize),
  );
  const style = getComputedStyle(left);
  const width = left.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
  const height =
    left.clientHeight -
    parseFloat(style.paddingTop) -
    parseFloat(style.paddingBottom) -
    (infoRef.value?.offsetHeight || 0) -
    parseFloat(style.rowGap || "0");
  coverSize.value = Math.max(0, Math.min(width, height));
};
useResizeObserver([rootRef, leftRef, infoRef], measureContent);
</script>

<style lang="scss" scoped>
.full-player-mobile-landscape {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  container: landscape-player / size;
  padding: var(--safe-area-top, 0px) var(--safe-area-right, 0px) var(--safe-area-bottom, 0px)
    var(--safe-area-left, 0px);
  display: flex;
  flex-direction: column;
  color: rgb(var(--main-cover-color));

  .landscape-header {
    flex: 0 0 auto;
    display: flex;
    padding-inline: 8px;
    .collapse {
      width: 48px;
      height: 48px;
      color: inherit;
    }
  }
  .landscape-content {
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(0, 0.36fr) minmax(0, 0.64fr);
    grid-template-rows: minmax(0, 1fr);
    container-type: size;
  }
  .left-section {
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 8px 16px 16px;
    gap: 12px;
  }
  .landscape-cover {
    width: var(--landscape-cover-size);
    aspect-ratio: 1;
    flex-shrink: 0;
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 12px 28px #00000030;
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
    max-height: 50%;
    overflow: auto;
    flex-shrink: 0;
    :deep(.player-data) {
      width: 100%;
      max-width: none;
      margin: 0;
      padding: 0;
    }
    :deep(.name .name-text) {
      font-size: clamp(18px, 4cqh, 28px);
    }
    :deep(.artists),
    :deep(.album),
    :deep(.dj) {
      font-size: 1rem;
      min-width: 0;
    }
    :deep(.artists .n-icon),
    :deep(.album .n-icon) {
      flex-shrink: 0;
    }
    :deep(.ar-list) {
      min-width: 0;
    }
    :deep(.album .name-text) {
      font-size: inherit;
    }
  }
  .landscape-comment {
    width: 100%;
    height: 100%;
    min-height: 0;
  }
  .show-comment {
    align-items: stretch;
  }
  .right-section {
    min-width: 0;
    min-height: 0;
    overflow: hidden;
    mix-blend-mode: var(--lyric-blend-mode);
    .no-lrc {
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      opacity: 0.6;
    }
    :deep(.default-lyric),
    :deep(.lyric-scroll-container),
    :deep(.am-lyric) {
      padding-inline: min(var(--landscape-lyric-padding-x, 20px), 4cqw) !important;
    }
    :deep(.lyric-scroll-container .placeholder:first-child) {
      height: 35cqh !important;
    }
    :deep(.amll-lyric-player) {
      --amll-lp-font-size: var(--amll-landscape-font-size) !important;
    }
    :deep(.lyric) {
      --lrc-size: var(--lrc-landscape-size) !important;
      --lrc-tran-size: var(--lrc-landscape-tran-size) !important;
      --lrc-roma-size: var(--lrc-landscape-roma-size) !important;
    }
  }
  .landscape-control-overlay {
    position: absolute;
    z-index: 10;
    inset-inline: var(--safe-area-left, 0px) var(--safe-area-right, 0px);
    bottom: var(--safe-area-bottom, 0px);
    max-height: calc(100% - var(--safe-area-top, 0px) - var(--safe-area-bottom, 0px) - 48px);
    overflow: auto;
    padding: 8px 16px 12px;
    background: rgba(20, 20, 24, 0.96);
    border-radius: 20px 20px 0 0;
    &[inert] {
      pointer-events: none;
    }
  }
  .landscape-tool-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
  }
  .landscape-meta,
  .landscape-tools {
    min-width: 0;
  }
  .landscape-meta :deep(.play-meta) {
    gap: 6px !important;
  }
  .landscape-meta :deep(.meta-item) {
    font-size: 0.875rem;
    border-radius: 8px;
    padding: 4px 8px;
    border: 1px solid rgba(var(--main-cover-color), 0.4);
    &.clickable {
      cursor: pointer;
    }
  }
  .landscape-tools :deep(.lyric-menu) {
    position: static;
    flex-flow: row nowrap !important;
    height: auto;
    width: auto;
    padding: 0;
    opacity: 1;
    pointer-events: auto;
    gap: 0 !important;
    .divider {
      display: none;
    }
    .menu-icon,
    .time {
      min-width: 48px;
      min-height: 48px;
      box-sizing: border-box;
      margin: 0;
    }
    .n-icon {
      font-size: 24px;
    }
  }
  :deep(.player-control) {
    position: relative;
    height: auto;
    overflow: visible;
    --play-control-touch-size: 48px;
    --play-control-gap: 0px;
    .control-content {
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      grid-template-rows: auto auto;
      gap: 8px;
    }
    .center {
      grid-column: 1 / -1;
      grid-row: 1;
      min-width: 0;
      max-height: none;
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 16px;
    }
    .left,
    .right {
      grid-row: 2;
      min-width: 0;
      height: auto;
      padding: 0;
      overflow-x: auto;
      flex-wrap: nowrap;
      gap: 0 !important;
    }
    .right-menu {
      flex-wrap: nowrap;
      gap: 0 !important;
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
    .left > .menu-icon:first-child {
      display: none;
    }
    .btn {
      justify-content: center;
      width: 100%;
    }
    .btn-icon {
      width: 48px;
      height: 48px;
      margin: 0;
      flex-shrink: 0;
    }
    .slider {
      width: 100%;
      min-width: 0;
      font-size: 0.875rem;
    }
    .slider .n-slider {
      min-width: 0;
    }
  }
  @container landscape-player (max-width: 600px) {
    :deep(.player-control .center) {
      grid-template-columns: minmax(0, 1fr);
      gap: 4px;
    }
  }
}
.landscape-controls-enter-active,
.landscape-controls-leave-active {
  transition: opacity 0.2s ease;
}
.landscape-controls-enter-from,
.landscape-controls-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .landscape-controls-enter-active,
  .landscape-controls-leave-active {
    transition: none;
  }
}
</style>

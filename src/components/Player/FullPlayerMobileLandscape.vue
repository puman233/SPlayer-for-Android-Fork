<template>
  <div
    ref="rootRef"
    class="full-player-mobile-landscape"
    :data-controls-visible="controls.visible.value"
    :style="{
      '--landscape-cover-size': coverSize + 'px',
      '--lrc-landscape-size': settingStore.lyricFontSizeLandscape + 'px',
      '--lrc-landscape-tran-size': Math.round(settingStore.lyricFontSizeLandscape * 0.5) + 'px',
      '--lrc-landscape-roma-size': Math.round(settingStore.lyricFontSizeLandscape * 0.43) + 'px',
      '--landscape-lyric-padding-x': settingStore.landscapeLyricPaddingX + 'px',
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
      <div
        class="header-actions auto-controls"
        :inert="!controls.visible.value"
        :aria-hidden="!controls.visible.value"
      >
        <n-button circle quaternary aria-label="切换沉浸式全屏" @click.stop="toggleFullscreen">
          <template #icon
            ><SvgIcon
              :name="statusStore.isImmersiveFullscreen ? 'FullscreenExit' : 'Fullscreen'"
              :size="24"
          /></template>
        </n-button>
        <n-popover
          v-model:show="toolsOpen"
          trigger="click"
          placement="bottom-end"
          :show-arrow="false"
        >
          <template #trigger>
            <n-button circle quaternary aria-label="歌词工具">
              <template #icon><SvgIcon name="Replay5" :size="24" /></template>
            </n-button>
          </template>
          <div :id="toolsTarget" class="landscape-lyric-tools" />
        </n-popover>
      </div>
    </header>
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
            <PlayerData center :controls-visible="controls.visible.value" />
          </div>
        </template>
      </div>
      <div class="right-section" data-stagger="lyric">
        <PlayerLyric
          v-if="!noLrc"
          persistent
          :tools-target="toolsOpen ? '#' + toolsTarget : undefined"
        />
        <div v-else class="no-lrc">
          <SvgIcon name="MusicNote" :size="36" :depth="3" /><span>暂无歌词</span>
        </div>
      </div>
    </div>
    <!-- 隐藏控件时让歌词使用底部空间。 -->
    <section
      class="landscape-controls auto-controls"
      :inert="!controls.visible.value"
      :aria-hidden="!controls.visible.value"
    >
      <PlayerControl persistent phone-landscape />
    </section>
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
import { PHONE_LANDSCAPE_HOLD_KEY } from "@/composables/usePlayerMetaPopoverHold";
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
const controls = useMobilePlayerControls(page, 2000);
provide(PLAYER_META_HOLD_KEY, controls.hold);
provide(PHONE_LANDSCAPE_HOLD_KEY, controls.hold);
const toolsTarget = "phone-landscape-tools-" + useId();
const toolsOpen = ref(false);
const rootRef = ref<HTMLElement | null>(null);
const leftRef = ref<HTMLElement | null>(null);
const infoRef = ref<HTMLElement | null>(null);
const coverRef = ref<HTMLElement | null>(null);
const coverSize = ref(0);
watch(coverRef, (el) => orientationTransition.setCoverEl(el, "landscape"));
onBeforeUnmount(() => orientationTransition.setCoverEl(null, "landscape"));
useEventListener(window, "pointerup", controls.pointerEnd);
useEventListener(window, "pointercancel", controls.pointerEnd);
useEventListener(window, "touchend", controls.touchEnd, { passive: true });
useEventListener(window, "touchcancel", controls.touchEnd, { passive: true });
useEventListener(window, "blur", controls.resetPointers);

// 弹层在 body 中，使用持有状态保持其入口，关闭后重新计时。
let overlayHeld = false;
watch(
  () => toolsOpen.value || statusStore.playListShow,
  (open) => {
    if (open && !overlayHeld) {
      overlayHeld = true;
      controls.hold.acquire();
    } else if (!open && overlayHeld) {
      overlayHeld = false;
      controls.hold.release();
    }
  },
  { immediate: true },
);

const collapse = async () => {
  if (statusStore.isImmersiveFullscreen) await orientationTransition.exit(musicStore.songCover);
  statusStore.showFullPlayer = false;
};
const toggleFullscreen = async () => {
  if (statusStore.isImmersiveFullscreen) await orientationTransition.exit(musicStore.songCover);
  else await orientationTransition.enter(musicStore.songCover);
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

const measureCover = () => {
  const left = leftRef.value;
  if (!left) return;
  const style = getComputedStyle(left);
  const width = left.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
  const height =
    left.clientHeight -
    parseFloat(style.paddingTop) -
    parseFloat(style.paddingBottom) -
    (infoRef.value?.offsetHeight || 0) -
    parseFloat(style.rowGap || "0");
  coverSize.value = Math.max(0, Math.min(width * 0.66, height));
};
useResizeObserver([leftRef, infoRef], measureCover);
</script>

<style lang="scss" scoped>
.full-player-mobile-landscape {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  container: landscape-player / size;
  padding: var(--safe-area-top, 0px) var(--safe-area-right, 0px) var(--safe-area-bottom, 0px)
    var(--safe-area-left, 0px);
  display: grid;
  grid-template-rows: 48px minmax(0, 1fr) 84px;
  &[data-controls-visible="false"] {
    grid-template-rows: 48px minmax(0, 1fr) 0px;
    .landscape-controls {
      padding: 0;
      overflow: hidden;
    }
  }
  color: rgb(var(--main-cover-color));
  .landscape-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 12px;
  }
  .header-actions {
    display: flex;
    gap: 4px;
  }
  .landscape-header .n-button {
    width: 48px;
    height: 48px;
    color: inherit;
  }
  .auto-controls {
    transition:
      opacity 0.2s ease,
      visibility 0.2s;
  }
  &[data-controls-visible="false"] .auto-controls {
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
  }
  .landscape-content {
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(0, 0.4fr) minmax(0, 0.6fr);
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
    padding: 4px 16px;
    gap: 10px;
  }
  .landscape-cover {
    width: var(--landscape-cover-size);
    aspect-ratio: 1;
    flex-shrink: 0;
    border-radius: 14px;
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
    max-height: 55%;
    overflow: auto;
    flex-shrink: 0;
    :deep(.player-data) {
      width: 100%;
      max-width: none;
      margin: 0;
      padding: 0;
    }
    :deep(.alia) {
      width: 100%;
      text-align: center;
      font-size: 13px;
      margin: 4px 0;
    }
    :deep(.name) {
      margin-bottom: 6px;
    }
    :deep(.name .name-text) {
      font-size: clamp(16px, 5cqh, 22px);
    }
    :deep(.player-data > .n-flex) {
      gap: 4px !important;
      flex-flow: row wrap !important;
      justify-content: center !important;
    }
    :deep(.artists),
    :deep(.album),
    :deep(.dj) {
      font-size: 12px;
      line-height: 1.3;
      margin: 0;
      min-width: 0;
    }
    :deep(.artists .n-icon),
    :deep(.album .n-icon) {
      display: none;
    }
    :deep(.album .name-text) {
      font-size: inherit;
    }
    :deep(.artists),
    :deep(.album),
    :deep(.dj) {
      max-width: 100%;
    }
    :deep(.artists .ar) {
      font-size: 12px;
    }
    :deep(.meta-actions-row) {
      order: 3;
      margin-top: 4px;
      transition:
        opacity 0.2s,
        visibility 0.2s;
    }
    :deep(.play-meta) {
      gap: 6px !important;
      justify-content: center !important;
    }
    :deep(.meta-item) {
      font-size: 11px;
      border: 1px solid rgba(var(--main-cover-color), 0.35);
      border-radius: 6px;
      padding: 2px 5px;
    }
  }
  &[data-controls-visible="false"] :deep(.meta-actions-row) {
    display: none;
  }
  .landscape-comment {
    width: 100%;
    height: 100%;
    min-height: 0;
  }
  .right-section {
    min-width: 0;
    min-height: 0;
    overflow: hidden;
    mix-blend-mode: var(--lyric-blend-mode);
    :deep(.player-lyric > .lyric-menu) {
      display: none;
    }
    :deep(.default-lyric),
    :deep(.lyric-scroll-container),
    :deep(.am-lyric) {
      padding-inline: min(var(--landscape-lyric-padding-x), 3cqw) !important;
    }
    :deep(.lyric-scroll-container .placeholder:first-child) {
      height: 27cqh !important;
    }
    :deep(.amll-lyric-player) {
      --amll-lp-font-size: var(--lrc-landscape-size) !important;
    }
    :deep(.lyric) {
      --lrc-size: var(--lrc-landscape-size) !important;
      --lrc-tran-size: var(--lrc-landscape-tran-size) !important;
      --lrc-roma-size: var(--lrc-landscape-roma-size) !important;
    }
    .no-lrc {
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      opacity: 0.6;
    }
  }
  .landscape-controls {
    min-height: 0;
    padding: 0 12px 4px;
  }
  :deep(.player-control) {
    --play-control-touch-size: 48px;
    position: relative;
    height: 100%;
    overflow: visible;
    .control-content {
      grid-template-columns: max-content minmax(0, 1fr) max-content;
      gap: 8px;
      align-items: center;
    }
    .center {
      height: 100%;
      max-height: none;
      justify-content: space-between;
      min-width: 0;
    }
    .btn {
      width: 100%;
      justify-content: space-between;
    }
    .btn-icon {
      width: 48px;
      height: 48px;
      margin: 0;
      flex-shrink: 0;
    }
    .play-pause {
      width: 48px;
      height: 48px;
      --n-width: 48px;
      --n-height: 48px;
      margin: 0;
      flex-shrink: 0;
      border: 1px solid rgba(var(--main-cover-color), 0.2);
    }
    .slider {
      width: 100%;
      font-size: 11px;
      min-width: 0;
      height: 24px;
    }
    .slider .n-slider {
      min-width: 0;
      margin-inline: 8px;
    }
    .left,
    .right {
      width: max-content;
      max-width: 100%;
      align-self: start;
      margin-top: 4px;
      padding: 0 4px;
      height: 40px;
      border: 1px solid rgba(var(--main-cover-color), 0.15);
      border-radius: 14px;
      background: rgba(var(--main-cover-color), 0.06);
      flex-wrap: nowrap;
      gap: 0 !important;
      overflow-x: auto;
      overflow-y: hidden;
      justify-content: center;
    }
    .right {
      justify-self: end;
    }
    .left > .menu-icon:first-child {
      display: none;
    }
    .left > * {
      flex-shrink: 0;
    }
    .menu-icon {
      width: 40px;
      height: 40px;
      padding: 8px;
      box-sizing: border-box;
      flex-shrink: 0;
    }
    .right-menu {
      flex-wrap: nowrap;
      gap: 0 !important;
    }
    .right-menu > .n-badge.hidden,
    .right-menu > .quality-tag,
    .right-menu > div:has(.quality-tag) {
      display: none;
    }
    .right-menu .n-badge {
      margin-right: 0 !important;
      order: 0;
    }
    .right-menu > .menu-icon {
      order: 1;
    }
    .qa-trigger .n-icon {
      transform: rotate(90deg);
    }
    .n-badge-sup {
      display: none;
    }
  }
  @container landscape-player (max-width: 600px) {
    :deep(.player-control .control-content) {
      gap: 4px;
    }
    :deep(.player-control .menu-icon) {
      width: 32px;
      padding: 6px;
    }
    :deep(.player-control .btn-icon) {
      width: 40px;
    }
  }
}
.landscape-lyric-tools :deep(.lyric-menu) {
  position: static;
  width: auto;
  height: auto;
  padding: 0;
  flex-flow: row wrap !important;
  opacity: 1;
  pointer-events: auto;
  gap: 4px !important;
  .menu-icon,
  .time {
    width: 40px;
    height: 40px;
    margin: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
  }
  .divider {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .auto-controls {
    transition: none !important;
  }
}
</style>

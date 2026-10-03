<template>
  <n-button
    v-if="available"
    class="desktop-lyrics-button"
    :class="{ enabled: state === 'ON' }"
    :data-desktop-lyrics-state="state"
    :aria-label="
      state === 'ON'
        ? '关闭桌面歌词'
        : state === 'PERMISSION_REQUIRED'
          ? '授权并开启桌面歌词'
          : '开启桌面歌词'
    "
    :aria-pressed="state === 'ON'"
    :title="state === 'PERMISSION_REQUIRED' ? '桌面歌词需要悬浮窗权限' : '桌面歌词'"
    :loading="busy"
    :disabled="!ready || busy"
    :type="state === 'ON' ? 'primary' : 'default'"
    quaternary
    @click.stop="toggle"
  >
    <template #icon><SvgIcon name="DesktopLyric2" :size="24" /></template>
    <span v-if="!compact">桌面歌词</span>
  </n-button>
</template>

<script setup lang="ts">
import { usePlayerController } from "@/core/player/PlayerController";
import {
  desktopLyricsState as state,
  desktopLyricsBusy as busy,
  desktopLyricsReady as ready,
} from "@/core/player/desktopLyricsState";
import { useSettingStore } from "@/stores";
import { isCapacitorAndroid, isElectron } from "@/utils/env";

defineProps<{ compact?: boolean }>();
const settings = useSettingStore();
const player = usePlayerController();
const available = computed(
  () => (isCapacitorAndroid || isElectron) && settings.fullscreenPlayerElements.desktopLyric,
);
const toggle = () => player.setDesktopLyricShow(state.value !== "ON");
</script>

<style scoped lang="scss">
.desktop-lyrics-button {
  min-width: var(--adaptive-touch-target, 48px);
  min-height: var(--adaptive-touch-target, 48px);
  height: auto;
  flex-shrink: 0;
  color: rgb(var(--main-cover-color));
  :deep(svg) {
    max-width: 100%;
    max-height: 100%;
  }
  :deep(.n-icon),
  :deep(.n-button__icon) {
    width: 24px;
    height: 24px;
    flex: 0 0 24px;
  }
  :deep(.n-button__content) {
    white-space: normal;
    line-height: 1.4;
    padding: var(--adaptive-space-xs, 4px) 0;
  }
  &.enabled {
    background-color: rgba(var(--main-cover-color), 0.18);
  }
}
</style>

<template>
  <n-flex
    :size="persistent ? 0 : 8"
    :wrap="!persistent"
    align="center"
    class="right-menu"
    :class="{ persistent }"
  >
    <!-- 音质 -->
    <template v-if="!controlsOnly && settingStore.showPlayerQuality">
      <n-popselect
        v-if="isOnlineSong"
        v-model:show="showQualityPopover"
        :value="currentPlayingLevel"
        :options="qualityOptions"
        trigger="manual"
        placement="top"
        @update:value="handleQualitySelect"
        @clickoutside="handleClickOutside"
      >
        <template #header>
          <n-flex class="quality-title" size="small" vertical>
            <span class="title">音质切换</span>
            <span class="tip">以账号具体权限为准</span>
          </n-flex>
        </template>
        <div ref="qualityTagRef">
          <n-tag
            class="quality-tag hidden"
            type="primary"
            size="small"
            @click.stop="handleQualityClick"
          >
            {{ getQualityName(statusStore.songQuality) }}
          </n-tag>
        </div>
      </n-popselect>
      <n-popover v-else trigger="hover" placement="top" :show-arrow="false">
        <template #trigger>
          <n-tag class="quality-tag hidden" type="primary" size="small">
            {{ getQualityName(statusStore.songQuality) }}
          </n-tag>
        </template>
        <span>当前歌曲不支持切换音质</span>
      </n-popover>
    </template>
    <!-- 桌面歌词 -->
    <n-badge
      v-if="
        !controlsOnly &&
        (isElectron || isCapacitorAndroid) &&
        settingStore.fullscreenPlayerElements.desktopLyric
      "
      value="ON"
      :show="statusStore.showDesktopLyric"
      class="hidden"
    >
      <div class="menu-icon hidden" @click.stop="player.toggleDesktopLyric()">
        <SvgIcon name="DesktopLyric2" :depth="statusStore.showDesktopLyric ? 1 : 3" />
      </div>
    </n-badge>
    <!-- 其他控制 -->
    <n-dropdown
      v-model:show="controlsOpen"
      :trigger="isPad || phoneLandscape ? 'click' : 'hover'"
      v-if="(controlsOnly || !phoneLandscape) && settingStore.fullscreenPlayerElements.moreSettings"
      :options="controlsOptions"
      :show-arrow="false"
      @select="handleControls"
    >
      <div class="menu-icon hidden">
        <SvgIcon name="Controls" /><span v-if="controlsOnly">更多播放设置</span>
      </div>
    </n-dropdown>
    <!-- 音量 -->
    <n-popover
      v-if="!controlsOnly"
      v-model:show="volumeOpen"
      :trigger="volumeClickTrigger ? 'click' : 'hover'"
      :show-arrow="false"
      :style="{ padding: 0 }"
    >
      <template #trigger>
        <button
          type="button"
          class="menu-icon hidden volume-trigger"
          aria-label="调节音量"
          aria-haspopup="dialog"
          :aria-expanded="volumeOpen"
          @click.stop
          @keydown.enter.stop.prevent="volumeOpen = true"
          @keydown.space.stop.prevent="volumeOpen = true"
          @wheel="player.setVolume"
        >
          <SvgIcon :name="statusStore.playVolumeIcon" />
        </button>
      </template>
      <div class="volume-change" role="dialog" aria-label="调节播放音量" @wheel="player.setVolume">
        <n-slider
          v-model:value="statusStore.playVolume"
          :tooltip="false"
          :min="0"
          :max="1"
          :step="0.01"
          vertical
          @update:value="(val: number) => player.setVolume(val)"
        />
        <n-text class="slider-num hidden">{{ statusStore.playVolumePercent }}%</n-text>
        <button
          type="button"
          class="volume-mute"
          :aria-label="statusStore.playVolume === 0 ? '取消静音' : '静音'"
          :aria-pressed="statusStore.playVolume === 0"
          @click.stop="player.toggleMute"
        >
          <SvgIcon :name="statusStore.playVolumeIcon" :size="22" />
        </button>
      </div>
    </n-popover>
    <!-- 播放列表 -->
    <n-badge
      v-if="!controlsOnly && !statusStore.personalFmMode"
      :value="dataStore.playList?.length ?? 0"
      :show="settingStore.showPlaylistCount"
      :max="9999"
      :style="{
        marginRight: settingStore.showPlaylistCount ? '12px' : null,
      }"
    >
      <div class="menu-icon" @click.stop="statusStore.playListShow = !statusStore.playListShow">
        <SvgIcon name="PlayList" />
      </div>
    </n-badge>
  </n-flex>
</template>

<script setup lang="ts">
import { usePlayerController } from "@/core/player/PlayerController";
import { useDataStore, useSettingStore, useStatusStore, useMusicStore } from "@/stores";
import { isElectron, isCapacitorAndroid } from "@/utils/env";
import { renderIcon } from "@/utils/helper";
import { openAutoClose, openChangeRate, openEqualizer, openABLoop } from "@/utils/modal";
import { useAudioManager } from "@/core/player/AudioManager";
import type { DropdownOption } from "naive-ui";
import { useQualityControl } from "@/composables/useQualityControl";
import { useBackClosable } from "@/composables/useAndroidBack";
import { usePlayerMetaPopoverHold } from "@/composables/usePlayerMetaPopoverHold";
import { useDevice } from "@/composables/useDevice";
import { useMediaQuery } from "@vueuse/core";

const props = defineProps<{ persistent?: boolean; phoneLandscape?: boolean; controlsOnly?: boolean }>();

const dataStore = useDataStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const musicStore = useMusicStore();
const player = usePlayerController();
const { isPad } = useDevice();

const {
  currentPlayingLevel,
  qualityOptions,
  loadQualities,
  handleQualitySelect,
  getQualityName,
  isOnlineSong,
} = useQualityControl();

const showQualityPopover = ref(false);
const controlsOpen = ref(false);
const volumeOpen = ref(false);
const coarsePointer = useMediaQuery("(pointer: coarse)");
const volumeClickTrigger = computed(() => isPad.value || props.phoneLandscape || coarsePointer.value);
usePlayerMetaPopoverHold(showQualityPopover);
usePlayerMetaPopoverHold(controlsOpen);
usePlayerMetaPopoverHold(volumeOpen);
useBackClosable(showQualityPopover);
const qualityTagRef = ref<HTMLElement | null>(null);

const handleQualityClick = async () => {
  if (showQualityPopover.value) {
    showQualityPopover.value = false;
  } else {
    await loadQualities();
    if (qualityOptions.value.length > 0) {
      showQualityPopover.value = true;
    }
  }
};

// 点击外部关闭音质选择
const handleClickOutside = (e: MouseEvent) => {
  if (qualityTagRef.value && qualityTagRef.value.contains(e.target as Node)) {
    return;
  }
  showQualityPopover.value = false;
};

// 更多功能
const audioManager = useAudioManager();

const controlsOptions = computed<DropdownOption[]>(() => [
  {
    label: "均衡器",
    key: "equalizer",
    icon: renderIcon("Eq"),
    disabled: !audioManager.capabilities.supportsEqualizer,
  },
  {
    label: "自动关闭",
    key: "autoClose",
    icon: renderIcon("TimeAuto"),
  },
  {
    label: "AB 循环",
    key: "abLoop",
    icon: renderIcon("Repeat"),
  },
  {
    label: "播放速度",
    key: "rate",
    disabled: !audioManager.capabilities.supportsRate,
    icon: renderIcon("PlayRate"),
  },
]);

// 更多功能选择
const handleControls = (key: string) => {
  switch (key) {
    case "equalizer":
      if (!audioManager.capabilities.supportsEqualizer) {
        window.$message.warning("当前引擎不支持均衡器功能");
        return;
      }
      openEqualizer();
      break;
    case "autoClose":
      openAutoClose();
      break;
    case "abLoop":
      openABLoop();
      break;
    case "rate":
      openChangeRate();
      break;
  }
};

watch(
  () => musicStore.playSong.id,
  () => {
    statusStore.availableQualities = [];
    if (showQualityPopover.value && statusStore.availableQualities.length === 0) {
      showQualityPopover.value = false;
    }
  },
);

watch([() => dataStore.userData.vipType, () => settingStore.disableAiAudio], () => {
  statusStore.availableQualities = [];
});
</script>

<style scoped lang="scss">
.right-menu {
  .volume-trigger {
    border: 0;
    background: none;
    color: inherit;
    font: inherit;
    min-width: 48px;
    min-height: 48px;
    touch-action: manipulation;
  }
  .menu-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 8px;
    border-radius: 8px;
    transition:
      background-color 0.3s,
      transform 0.3s;
    cursor: pointer;
    .n-icon {
      font-size: 22px;
      color: var(--primary-hex);
    }
    &:hover {
      transform: scale(1.1);
      background-color: rgba(var(--primary), 0.28);
    }
    &:active {
      transform: scale(1);
    }
  }
  :deep(.n-badge-sup) {
    background-color: rgba(var(--primary), 0.28);
    backdrop-filter: blur(20px);
    // font-size: 10px;
    .n-base-slot-machine {
      color: var(--primary-hex);
    }
  }
  .quality-tag {
    height: 26px;
    padding: 0 8px;
    border-radius: 8px;
    cursor: pointer;
  }
  @media (max-width: 810px) {
    &:not(.persistent) .hidden {
      display: none;
    }
  }
}
.quality-title {
  .title {
    font-size: 14px;
    line-height: normal;
  }
  .tip {
    font-size: 12px;
    opacity: 0.6;
  }
}
.volume-change {
  padding: 12px;
  display: flex;
  flex-direction: column;
  height: auto;
  width: max-content;
  min-width: 72px;
  max-width: calc(100vw - 24px);
  gap: 8px;
  align-items: center;
  .n-slider {
    height: 140px;
    flex: 0 0 140px;
  }
  .slider-num {
    margin-top: 0;
    font-size: 13px;
    white-space: nowrap;
  }
  .volume-mute {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    height: 48px;
    border: 0;
    border-radius: 8px;
    color: inherit;
    background: rgba(var(--primary), 0.12);
    cursor: pointer;
  }
}
</style>

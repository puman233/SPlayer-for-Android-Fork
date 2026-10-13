<template>
  <div :class="['mobile-player-bottom-controls', { large }]" data-no-page-swipe @click.stop>
    <div class="progress-section">
      <span class="time" @click="toggleTimeFormat">{{ timeDisplay[0] }}</span>
      <PlayerSlider class="player" :show-tooltip="false" />
      <span class="time" @click="toggleTimeFormat">{{ timeDisplay[1] }}</span>
    </div>

    <div class="control-section" @keydown.enter.stop @keydown.space.stop>
      <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
        <button type="button" class="mode-btn" :aria-label="statusStore.shuffleMode === 'heartbeat' ? '随机播放：心动模式' : '随机播放'" :aria-pressed="statusStore.shuffleMode !== 'off'" @click.stop="player.toggleShuffle()">
          <SvgIcon
            :name="statusStore.shuffleIcon"
            :size="24"
            :depth="statusStore.shuffleMode === 'off' ? 3 : 1"
          />
        </button>
      </template>
      <div v-else class="placeholder" />

      <button type="button" class="ctrl-btn" aria-label="上一曲" @click.stop="player.nextOrPrev('prev')">
        <SvgIcon name="SkipPrev" :size="36" />
      </button>

      <n-button
        :loading="statusStore.playLoading"
        class="play-btn"
        type="primary"
        strong
        secondary
        circle
        :aria-label="statusStore.playStatus ? '暂停' : '播放'"
        @click.stop="player.playOrPause()"
      >
        <template #icon>
          <Transition name="fade" mode="out-in">
            <SvgIcon
              :key="statusStore.playStatus ? 'Pause' : 'Play'"
              :name="statusStore.playStatus ? 'Pause' : 'Play'"
              :size="40"
            />
          </Transition>
        </template>
      </n-button>

      <button type="button" class="ctrl-btn" aria-label="下一曲" @click.stop="player.nextOrPrev('next')">
        <SvgIcon name="SkipNext" :size="36" />
      </button>

      <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
        <button type="button" class="mode-btn" :aria-label="repeatLabel" @click.stop="player.toggleRepeat()">
          <SvgIcon
            :name="statusStore.repeatIcon"
            :size="24"
            :depth="statusStore.repeatMode === 'off' ? 3 : 1"
          />
        </button>
      </template>
      <div v-else class="placeholder" />
    </div>

    <div
      class="pagination"
      :aria-label="`播放器第 ${pageIndex + 1} 页，共 ${pageCount} 页`"
      @keydown.enter.stop
      @keydown.space.stop
    >
      <button
        v-for="i in pageCount"
        :key="i"
        type="button"
        :class="['dot', { active: pageIndex === i - 1 }]"
        :aria-label="`切换到第 ${i} 页`"
        :aria-current="pageIndex === i - 1 ? 'page' : undefined"
        @click="emit('update:pageIndex', i - 1)"
      >
        <span class="dot-marker" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useTimeFormat } from "@/composables/useTimeFormat";
import { usePlayerController } from "@/core/player/PlayerController";
import { useMusicStore, useStatusStore } from "@/stores";

withDefaults(
  defineProps<{
    pageCount: number;
    pageIndex: number;
    large?: boolean;
  }>(),
  { large: false },
);

const emit = defineEmits<{
  "update:pageIndex": [value: number];
}>();

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const player = usePlayerController();
const repeatLabel = computed(() => `循环模式：${{ off: "关闭", list: "列表循环", one: "单曲循环" }[statusStore.repeatMode]}`);
const { timeDisplay, toggleTimeFormat } = useTimeFormat();
</script>

<style scoped lang="scss">
.mobile-player-bottom-controls {
  position: relative;
  z-index: 9;
  flex: 0 0 auto;
  width: 100%;
  padding: 4px clamp(16px, 5vw, 24px) calc(10px + var(--mobile-safe-bottom));
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(24px, auto) 60px 48px;
  row-gap: clamp(6px, 1.5vh, 14px);
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.18) 100%);

  .progress-section {
    min-width: 0;
    display: flex;
    align-items: center;

    .time {
      width: 40px;
      flex: 0 0 40px;
      font-size: 12px;
      text-align: center;
      color: rgb(var(--main-cover-color));
      opacity: 0.6;
      font-variant-numeric: tabular-nums;
    }

    .player {
      min-width: 0;
      margin: 0 12px;
    }
  }

  .control-section {
    min-width: 0;
    width: 100%;
    max-width: 420px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .placeholder,
    .mode-btn {
      width: 48px;
      height: 48px;
      flex: 0 0 48px;
    }

    .mode-btn,
    .ctrl-btn {
      appearance: none;
      border: 0;
      padding: 0;
      background: transparent;
      font: inherit;
      color: inherit;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;

      &:focus-visible {
        outline: 2px solid rgb(var(--main-cover-color));
        outline-offset: -2px;
      }

      .n-icon {
        color: rgb(var(--main-cover-color));
      }
    }

    .mode-btn {
      opacity: 0.8;
    }

    .ctrl-btn {
      width: 50px;
      height: 50px;
      flex: 0 0 50px;
    }

    .play-btn {
      &:focus-visible {
        outline: 2px solid rgb(var(--main-cover-color));
        outline-offset: -2px;
      }
      width: 60px;
      height: 60px;
      flex: 0 0 60px;
      font-size: 26px;
      background-color: rgba(var(--main-cover-color), 0.2);
      color: rgb(var(--main-cover-color));
      transition: transform 0.2s;

      &.n-button--primary-type {
        --n-color: rgba(var(--main-cover-color), 0.14);
        --n-color-hover: rgba(var(--main-cover-color), 0.2);
        --n-color-focus: rgba(var(--main-cover-color), 0.2);
        --n-color-pressed: rgba(var(--main-cover-color), 0.12);
      }

      &:active {
        transform: scale(0.95);
      }
    }

    // 保留实际触控目标；320px视口仍能容纳五个控件及间距。
    .placeholder,
    .mode-btn,
    .ctrl-btn,
    .play-btn {
      min-width: 48px;
      min-height: 48px;
      flex-shrink: 0;
    }
  }

  .pagination {
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;

    .dot {
      appearance: none;
      width: 48px;
      height: 48px;
      min-width: 48px;
      min-height: 48px;
      flex: 0 0 48px;
      padding: 0;
      border: 0;
      background: transparent;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;

      &:focus-visible {
        outline: 2px solid rgb(var(--main-cover-color));
        outline-offset: -2px;
      }
    }

    .dot-marker {
      width: 6px;
      height: 6px;
      flex: 0 0 auto;
      padding: 0;
      border: 0;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.2);
      transition:
        width 0.3s,
        background-color 0.3s,
        opacity 0.3s;

    }

    .dot.active .dot-marker {
        width: 16px;
        border-radius: 4px;
        background-color: rgb(var(--main-cover-color));
        opacity: 0.8;
    }
  }

  &.large {
    padding-right: clamp(32px, 6vw, 56px);
    padding-left: clamp(32px, 6vw, 56px);
    grid-template-rows: minmax(28px, auto) 76px 48px;

    .progress-section {
      .time {
        width: 52px;
        flex-basis: 52px;
        font-size: 14px;
      }

      .player {
        margin: 0 16px;
      }
    }

    .control-section {
      max-width: 520px;

      .placeholder,
      .mode-btn {
        width: 52px;
        height: 52px;
        flex-basis: 52px;
      }

      .ctrl-btn {
        width: 64px;
        height: 64px;
        flex-basis: 64px;
      }

      .play-btn {
        width: 76px;
        height: 76px;
        flex-basis: 76px;
      }
    }

    .pagination {
      gap: 10px;

      .dot-marker {
        width: 8px;
        height: 8px;

      }

      .dot.active .dot-marker {
        width: 22px;
      }
    }
  }
}

@media (max-height: 700px) {
  .mobile-player-bottom-controls {
    padding-top: 2px;
    row-gap: 6px;
  }
}
</style>

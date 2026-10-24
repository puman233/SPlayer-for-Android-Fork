<template>
  <n-card class="personal-fm">
    <!-- 封面 -->
    <StableCoverImage
      v-if="!settingStore.hiddenCovers.personalFM"
      :src="musicStore.personalFMSong?.coverSize?.m || musicStore.personalFMSong?.cover"
      class="cover"
    />
    <!-- 信息 -->
    <Transition name="fade" mode="out-in">
      <div
        :key="musicStore.personalFMSong?.id"
        :class="['info', { 'no-cover': settingStore.hiddenCovers.personalFM }]"
      >
        <div class="fm-metadata">
          <n-text class="name text-hidden" :title="musicStore.personalFMSong?.name">
            {{ musicStore.personalFMSong?.name || "未知曲目" }}
          </n-text>
          <div v-if="Array.isArray(musicStore.personalFMSong?.artists)" class="artists text-hidden">
            <SvgIcon name="Artist" :depth="3" />
            <n-text v-for="ar in musicStore.personalFMSong.artists" :key="ar.id" class="ar">
              {{ ar.name }}
            </n-text>
          </div>
          <div v-if="isObject(musicStore.personalFMSong.album)" class="album text-hidden">
            <SvgIcon name="Album" :depth="3" />
            <n-text class="album-text">
              {{ musicStore.personalFMSong.album?.name || "未知专辑" }}
            </n-text>
          </div>
          <div class="radio">
            <SvgIcon :depth="3" name="Radio" />
            <n-text :depth="3">私人FM</n-text>
          </div>
        </div>
        <!-- 功能 -->
        <n-flex :wrap="false" class="menu" align="center">
          <!-- 不喜欢 -->
          <button
            type="button"
            aria-label="不喜欢这首私人 FM 歌曲"
            class="menu-icon"
            v-debounce="
              () =>
                songManager.personalFMTrash(musicStore.personalFMSong?.id, () =>
                  player.nextOrPrev('next'),
                )
            "
          >
            <SvgIcon class="icon" size="18" name="ThumbDown" />
          </button>
          <!-- 播放暂停 -->
          <n-button
            :loading="statusStore.personalFmMode && statusStore.playLoading"
            :aria-label="
              statusStore.personalFmMode && statusStore.playStatus ? '暂停私人 FM' : '播放私人 FM'
            "
            :focusable="false"
            class="play"
            strong
            secondary
            circle
            @click.stop="fmPlayOrPause"
          >
            <template #icon>
              <Transition name="fade" mode="out-in">
                <SvgIcon :key="playIcon" :name="playIcon" :size="32" />
              </Transition>
            </template>
          </n-button>
          <!-- 下一曲 -->
          <button
            type="button"
            class="menu-icon"
            aria-label="下一首私人 FM"
            @click.stop="fmPlayNext"
          >
            <SvgIcon size="26" name="SkipNext" />
          </button>
        </n-flex>
      </div>
    </Transition>
  </n-card>
</template>

<script setup lang="ts">
import { usePlayerController } from "@/core/player/PlayerController";
import { useSongManager } from "@/core/player/SongManager";
import { useMusicStore, useStatusStore, useSettingStore } from "@/stores";
import StableCoverImage from "@/components/Card/StableCoverImage.vue";
import { debounce, isObject } from "lodash-es";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();

const player = usePlayerController();
const songManager = useSongManager();

// 播放图标
const playIcon = computed(() =>
  statusStore.personalFmMode ? (statusStore.playStatus ? "Pause" : "Play") : "Play",
);

// 播放暂停
const fmPlayOrPause = () => {
  if (statusStore.personalFmMode) {
    player.playOrPause();
  } else {
    // 更改播放模式
    statusStore.personalFmMode = true;
    statusStore.shuffleMode = "off";
    player.playSong();
  }
};

// 下一曲
const fmPlayNext = debounce(() => {
  statusStore.personalFmMode = true;
  statusStore.shuffleMode = "off";
  player.nextOrPrev("next");
}, 300);

onMounted(() => songManager.initPersonalFM());
</script>

<style lang="scss" scoped>
.personal-fm {
  position: relative;
  width: 100%;
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  :deep(.n-card-content) {
    display: flex;
    align-items: center;
    padding: 20px;
    width: 100%;
  }
  .cover {
    width: 160px;
    height: 160px;
    min-width: 160px;
    border-radius: 8px;
    margin-right: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
    box-shadow: 0 0 10px 6px #00000008;
    :deep(img) {
      width: 100%;
      height: 100%;
    }
  }
  .info {
    height: 100%;
    display: flex;
    flex-direction: column;
    .n-text {
      line-height: normal;
    }
    .name {
      font-size: 22px;
      font-weight: bold;
    }
    .artists {
      margin-top: 2px;
      font-size: 14px;
      .n-icon {
        font-size: 18px;
        margin-right: 4px;
        transform: translateY(3px);
      }
      .ar {
        display: inline-flex;
        transition: opacity 0.3s;
        opacity: 0.6;
        &::after {
          content: "/";
          margin: 0 4px;
        }
        &:last-child {
          &::after {
            display: none;
          }
        }
      }
    }
    .album {
      font-size: 14px;
      .n-icon {
        font-size: 18px;
        margin-right: 4px;
        transform: translateY(3px);
      }
      .album-text {
        transition: opacity 0.3s;
        opacity: 0.6;
      }
    }
    .menu {
      margin-top: auto;
      .play {
        width: 46px;
        height: 46px;
        .n-icon {
          color: var(--primary-hex);
          transition: opacity 0.1s ease-in-out;
        }
      }
      .menu-icon {
        border: 0;
        padding: 0;
        background: transparent;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 38px;
        height: 38px;
        border-radius: 50%;
        color: var(--primary-hex);
        transition:
          background-color 0.3s,
          transform 0.3s;
        cursor: pointer;
        &:hover {
          transform: scale(1.1);
          background-color: rgba(var(--primary), 0.08);
        }
        &:active {
          transform: scale(1);
        }
      }
    }
    &.no-cover {
      width: 100%;
      align-items: center;
      justify-content: center;
      text-align: center;
      .name {
        font-size: 24px;
        margin-bottom: 6px;
      }
      .artists {
        margin-top: 0;
        font-size: 15px;
        opacity: 0.8;
      }
      .menu {
        margin-top: 28px;
        justify-content: center;
        gap: 8px;
        .menu-icon {
          width: 42px;
          height: 42px;
          background-color: rgba(var(--primary), 0.06);
          &:hover {
            background-color: rgba(var(--primary), 0.12);
          }
        }
        .play {
          width: 52px;
          height: 52px;
        }
      }
    }
  }
  .radio {
    position: absolute;
    right: 20px;
    bottom: 14px;
    display: flex;
    align-items: center;
    .n-icon {
      margin-right: 4px;
      transform: translateY(-1px);
    }
  }
  &:hover {
    border-color: rgba(var(--primary), 0.6);
  }
  @media (max-width: 1200px) and (min-width: 769px) {
    .radio {
      display: none;
    }
  }
  @media (max-width: 768px) {
    height: 120px;
    .cover {
      min-width: 80px;
      height: 80px;
      width: 80px;
    }
    .info {
      .name {
        font-size: 16px;
      }
      .album {
        display: none;
      }
      .menu {
        margin-top: 8px;
        .play {
          width: 36px;
          height: 36px;
          .n-icon {
            font-size: 28px !important;
          }
        }
        .menu-icon {
          width: 26px;
          height: 26px;
        }
      }
    }
  }
  @media (max-width: 599px) {
    min-width: 0;
    height: auto;
    min-height: 0;
    :deep(.n-card-content) {
      flex-wrap: nowrap;
      padding: 8px;
      gap: 8px;
    }
    .cover {
      margin-right: 0;
      align-self: flex-start;
      width: clamp(88px, 30vw, 112px);
      height: clamp(88px, 30vw, 112px);
      min-width: clamp(88px, 30vw, 112px);
    }
    .fm-metadata {
      min-width: 0;
      min-height: 0;
      flex: 0 0 auto;
    }
    .info {
      flex: 1;
      min-width: 0;
      height: auto;
      min-height: clamp(88px, 30vw, 112px);
      gap: 4px;
      &.no-cover {
        height: auto;
        .fm-metadata {
          max-height: none;
        }
      }
      .name {
        font-size: clamp(15px, 4.5vw, 16px);
      }
      .name,
      .artists {
        display: block;
        line-clamp: unset;
        -webkit-line-clamp: unset;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .artists .ar {
        display: inline;
      }
      .menu {
        flex-wrap: wrap !important;
        gap: 4px !important;
        margin-top: auto;
        flex: 0 0 auto;
        .menu-icon,
        .play {
          width: 48px;
          height: 48px;
          min-width: 48px;
          min-height: 48px;
          flex: 0 0 48px;
        }
        .play {
          background: radial-gradient(
            circle,
            rgba(var(--primary), 0.12) 0 18px,
            transparent 19px
          ) !important;
          :deep(.n-button__border),
          :deep(.n-button__state-border) {
            display: none;
          }
          .n-icon {
            font-size: 24px !important;
          }
        }
      }
    }
    .radio {
      position: static;
      order: -1;
      margin-top: 0;
      justify-content: flex-start;
      .n-icon,
      .n-text {
        font-size: 12px;
      }
    }
  }
}
</style>

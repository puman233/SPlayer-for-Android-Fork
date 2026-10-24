<!-- 歌曲列表卡片 -->
<template>
  <n-card :style="{ height: `${height}px` || 'auto' }" :class="['song-data-card', size]">
    <n-flex v-if="size === 'normal'" align="center" justify="space-between" class="title">
      <n-text class="name">{{ title }}</n-text>
      <n-text v-if="description" depth="3" class="desc">{{ description }}</n-text>
    </n-flex>
    <div class="content">
      <!-- 封面 -->
      <div v-if="!hiddenCover" class="cover">
        <StableCoverImage
          v-if="stableCover && (covers !== undefined || cover)"
          :sources="covers ?? [cover || '']"
        />
        <n-image v-else-if="cover" :src="cover" preview-disabled lazy @load="coverLoaded">
          <template #placeholder>
            <div class="cover-loading">
              <img src="/images/song.jpg?asset" class="loading-img" alt="loading-img" />
            </div>
          </template>
        </n-image>
        <div v-else-if="stableCover" class="cover-list">
          <StableCoverImage
            v-for="(item, index) in stableSongs"
            :key="index"
            :src="item.coverSize?.m || item.cover"
          />
        </div>
        <TransitionGroup v-else tag="div" name="fade" class="cover-list">
          <n-image
            v-for="item in songList"
            :key="item.id"
            :src="item.coverSize?.m || item.cover"
            preview-disabled
            lazy
            @load="coverLoaded"
          >
            <template #placeholder>
              <div class="cover-loading">
                <img src="/images/song.jpg?asset" class="loading-img" alt="loading-img" />
              </div>
            </template>
          </n-image>
        </TransitionGroup>
        <!-- 播放 -->
        <SvgIcon :size="36" name="Play" class="play" />
      </div>
      <!-- 信息 -->
      <div v-if="size === 'small'" class="info" :class="{ center: hiddenCover }">
        <n-text v-if="typeof title === 'string'" class="name text-hidden">
          {{ title }}
        </n-text>
        <component v-else :is="title" />
        <n-text v-if="description" depth="3" class="desc text-hidden">
          {{ description }}
        </n-text>
      </div>
      <div v-else class="info">
        <slot name="info" />
      </div>
    </div>
  </n-card>
</template>

<script lang="ts" setup>
import type { SongType } from "@/types/main";
import { coverLoaded } from "@/utils/helper";
import { sampleSize } from "lodash-es";
import { VNodeChild } from "vue";
import StableCoverImage from "./StableCoverImage.vue";

const props = defineProps<{
  size: "normal" | "small";
  title: string | VNodeChild;
  data?: SongType[];
  description?: string;
  loading?: boolean;
  height?: number;
  cover?: string;
  covers?: string[];
  stableCover?: boolean;
  hiddenCover?: boolean;
}>();

// 列表前三首
const songList = computed(() => sampleSize(props.data, 3));
const retainedSongs = shallowRef<SongType[]>([]);
watch(
  () => props.data,
  (songs) => {
    if (props.stableCover && songs?.length) retainedSongs.value = sampleSize(songs, 3);
  },
  { immediate: true },
);
const stableSongs = computed(() =>
  retainedSongs.value.length
    ? retainedSongs.value
    : [{ cover: "/images/song.jpg?asset", coverSize: undefined }],
);
</script>

<style lang="scss" scoped>
.song-data-card {
  border-radius: 12px;
  cursor: pointer;
  :deep(.n-card-content) {
    display: flex;
    height: 100%;
    padding: 16px;
  }
  .content {
    display: flex;
    width: 100%;
    .cover {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      aspect-ratio: 1/1;
      margin-right: 20px;
      .cover-list {
        width: 100%;
        height: 100%;
      }
      .n-image,
      .stable-cover {
        width: 100%;
        height: 100%;
        position: absolute;
        border-radius: 8px;
        overflow: hidden;
        top: 0;
        transform-origin: right bottom;
        z-index: 3;
        &:nth-of-type(2) {
          transform: scale(0.9) rotate(6deg);
          opacity: 0.9;
          z-index: 2;
        }
        &:nth-of-type(3) {
          transform: scale(0.8) rotate(12deg);
          opacity: 0.8;
          z-index: 1;
        }
      }
      .play {
        position: absolute;
        z-index: 5;
        opacity: 0;
        transition: opacity 0.3s;
      }
    }
    .info {
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: space-evenly;
      &.center {
        align-items: center;
        text-align: center;
      }
      .name {
        font-size: 18px;
        font-weight: bold;
      }
    }
  }
  &:hover {
    border-color: rgba(var(--primary), 0.6);
    .cover {
      :deep(img) {
        filter: brightness(0.4);
      }
      .play {
        opacity: 1;
      }
    }
  }
  &.normal {
    :deep(.n-card-content) {
      flex-direction: column;
    }
    .title {
      margin-bottom: 12px;
      line-height: normal;
      .name {
        font-size: 18px;
        font-weight: bold;
      }
      .desc {
        font-size: 12px;
      }
    }
    .content {
      height: 100%;
    }
  }
}
</style>

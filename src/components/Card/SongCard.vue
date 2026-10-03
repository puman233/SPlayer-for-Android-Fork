<template>
  <div class="song-card">
    <div :class="['song-content', { play: musicStore.playSong.id === song.id }]">
      <!-- 序号 -->
      <AdaptiveButton
        icon-only
        quaternary
        class="num"
        :aria-label="`播放或暂停 ${song.name}`"
        @dblclick.stop
        @click.stop="
          musicStore.playSong.id === song.id ? player.playOrPause() : player.addNextSong(song, true)
        "
      >
        <n-text v-if="musicStore.playSong.id !== song.id" depth="3">
          {{ index + 1 }}
        </n-text>
        <SvgIcon v-else :size="22" name="Music" />
        <!-- 播放暂停 -->
        <SvgIcon :size="28" :name="statusStore.playStatus ? 'Pause' : 'Play'" class="status" />
        <!-- 播放 -->
        <SvgIcon :size="28" name="Play" class="play" />
      </AdaptiveButton>
      <!-- 标题 -->
      <div class="title">
        <!-- 封面 -->
        <s-image
          v-if="!hiddenCover"
          :key="song.cover"
          :src="song.coverSize?.s || song.cover"
          cache-type="covers"
          class="cover"
        />
        <!-- 信息 -->
        <n-flex size="small" class="info" vertical>
          <!-- 名称 -->
          <div class="name">
            <n-ellipsis
              :line-clamp="1"
              :tooltip="{
                placement: 'top',
                width: 'trigger',
              }"
              class="name-text"
            >
              {{
                settingStore.hideBracketedContent
                  ? removeBrackets(song?.name)
                  : song?.name || "未知曲目"
              }}
              <n-text
                v-if="song.alia?.length && !settingStore.hideBracketedContent"
                class="alia"
                depth="3"
              >
                ({{ song.alia }})
              </n-text>
            </n-ellipsis>
          </div>
          <n-flex :size="4" :wrap="true" class="desc" align="center">
            <!-- 音质 -->
            <AdaptiveTag
              v-if="song?.quality && settingStore.showSongQuality"
              :type="qualityColor"
              class="quality"
              round
            >
              {{ song.quality }}
            </AdaptiveTag>
            <!-- 原唱翻唱 -->
            <template v-if="settingStore.showSongOriginalTag">
              <AdaptiveTag v-if="song.originCoverType === 1" :bordered="false" type="primary" round>
                原
              </AdaptiveTag>
              <AdaptiveTag v-if="song.originCoverType === 2" :bordered="false" type="info" round>
                翻唱
              </AdaptiveTag>
            </template>
            <!-- 特权 -->
            <template v-if="settingStore.showSongPrivilegeTag">
              <AdaptiveTag v-if="song.free === 1" :bordered="false" type="error" round>
                VIP
              </AdaptiveTag>
              <AdaptiveTag v-if="song.free === 4" :bordered="false" type="error" round>
                EP
              </AdaptiveTag>
              <!-- 云盘 -->
              <AdaptiveTag v-if="song?.pc" :bordered="false" class="cloud" type="info" round>
                <template #icon>
                  <SvgIcon name="Cloud" />
                </template>
              </AdaptiveTag>
            </template>
            <!-- MV -->
            <AdaptiveTag
              v-if="song?.mv"
              :bordered="false"
              class="mv"
              interactive
              type="warning"
              round
              @click.stop="
                router.push({
                  name: 'video',
                  query: { id: song.mv },
                })
              "
            >
              MV
            </AdaptiveTag>
            <!-- 脏标 -->
            <AdaptiveTag
              v-if="
                settingStore.showSongExplicitTag && song.mark && song.mark & EXPLICIT_CONTENT_MARK
              "
              :bordered="false"
              class="explicit"
              type="error"
              round
              title="Explicit Content"
            >
              E
            </AdaptiveTag>
            <!-- 歌手 -->
            <template v-if="settingStore.showSongArtist">
              <div v-if="Array.isArray(song.artists)" class="artists">
                <n-text
                  v-for="ar in song.artists"
                  :key="ar.id"
                  class="ar"
                  @click="openJumpArtist(song.artists, ar.id)"
                >
                  {{ settingStore.hideBracketedContent ? removeBrackets(ar.name) : ar.name }}
                </n-text>
              </div>
              <div v-else-if="song.type === 'radio'" class="artists">
                <n-text class="ar"> 电台节目 </n-text>
              </div>
              <div v-else class="artists" @click="openJumpArtist(song.artists)">
                <n-text class="ar">
                  {{
                    settingStore.hideBracketedContent
                      ? removeBrackets(song.artists)
                      : song.artists || "未知艺术家"
                  }}
                </n-text>
              </div>
            </template>
          </n-flex>
        </n-flex>
      </div>
      <!-- 专辑 -->
      <div
        v-if="song.type !== 'radio' && !hiddenAlbum && !isPhone && settingStore.showSongAlbum"
        class="album text-hidden"
      >
        <n-text
          v-if="isObject(song.album)"
          class="album-text"
          @click="
            router.push({
              name: 'album',
              query: { id: song.album?.id },
            })
          "
        >
          {{ albumName }}
        </n-text>
        <n-text v-else class="album-text">
          {{ albumName }}
        </n-text>
      </div>
      <!-- 操作 -->
      <div
        v-if="song.type !== 'radio' && settingStore.showSongOperations"
        class="actions"
        @click.stop
        @dblclick.stop
      >
        <!-- 喜欢歌曲 -->
        <AdaptiveButton
          v-if="!isPhone"
          icon-only
          quaternary
          aria-label="收藏歌曲"
          @click.stop="toLikeSong(song, !dataStore.isLikeSong(song.id))"
          ><SvgIcon
            :name="dataStore.isLikeSong(song.id) ? 'Favorite' : 'FavoriteBorder'"
            :size="20"
        /></AdaptiveButton>
        <!-- 移动端菜单 -->
        <AdaptiveButton
          v-else
          icon-only
          quaternary
          aria-label="歌曲更多操作"
          @click.stop="emit('show-menu', $event)"
          ><SvgIcon name="More" :size="20"
        /></AdaptiveButton>
      </div>
      <!-- 更新日期 -->
      <n-text v-if="song.type === 'radio' && !isPhone" class="meta date" depth="3">
        {{ formatTimestamp(song.updateTime) }}
      </n-text>
      <!-- 播放量 -->
      <n-text v-if="song.type === 'radio' && !isPhone" class="meta" depth="3">
        {{ formatNumber(song.playCount || 0) }}
      </n-text>
      <!-- 时长 -->
      <n-text v-if="!isPhone && settingStore.showSongDuration" class="meta" depth="3">
        {{ msToTime(song.duration) }}
      </n-text>
      <!-- 大小 -->
      <n-text v-if="song.size && !hiddenSize && !isPhone" class="meta size" depth="3">
        {{ formatFileSize(song.size) }}
      </n-text>
    </div>
  </div>
</template>

<script setup lang="ts">
import { QualityType, type SongType } from "@/types/main";
import { useStatusStore, useMusicStore, useDataStore, useSettingStore } from "@/stores";
import { formatNumber, formatFileSize } from "@/utils/helper";
import { openJumpArtist } from "@/utils/modal";
import { removeBrackets } from "@/utils/format";
import { toLikeSong } from "@/utils/auth";
import { isObject } from "lodash-es";
import { formatTimestamp, msToTime } from "@/utils/time";
import { usePlayerController } from "@/core/player/PlayerController";
import { useDevice } from "@/composables/useDevice";
import { EXPLICIT_CONTENT_MARK } from "@/utils/meta";

const props = defineProps<{
  // 歌曲
  song: SongType;
  // 索引
  index: number;
  // 隐藏信息
  hiddenCover?: boolean;
  hiddenAlbum?: boolean;
  hiddenSize?: boolean;
}>();

const emit = defineEmits<{
  "show-menu": [event: MouseEvent];
}>();

const { isPhone } = useDevice();
const router = useRouter();
const dataStore = useDataStore();
const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();

const player = usePlayerController();

// 歌曲数据
const song = toRef(props, "song");

// 音质颜色
const qualityColor = computed(() => {
  if (song.value.quality === QualityType.HiRes) return "warning";
  if (song.value.quality === QualityType.SQ) return "warning";
  if (song.value.quality === QualityType.HQ) return "info";
  return "primary";
});

// 专辑名称
const albumName = computed(() => {
  const album = song.value.album;
  const name = isObject(album) ? album.name : album;
  return (settingStore.hideBracketedContent ? removeBrackets(name) : name) || "未知专辑";
});
</script>

<style lang="scss" scoped>
.song-card {
  min-height: 90px;
  height: auto;
  padding-bottom: var(--adaptive-space-sm, 8px);
  cursor: pointer;

  // 紧凑窗口减少留白，行高交由虚拟列表测量。
  @media (max-width: 768px) {
    .song-content {
      padding: 6px 10px;
    }
    .title {
      padding: 4px 14px 4px 0;
      .cover {
        width: 46px;
        height: 46px;
        min-width: 46px;
        border-radius: 7px;
        margin-right: 10px;
      }
      .info {
        .name {
          font-size: 14px;
        }
        .desc {
          font-size: 12px;
        }
      }
    }
  }

  .song-content {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    flex: 1;
    border-radius: 12px;
    border: 2px solid rgba(var(--primary), 0.12);
    background-color: var(--surface-container-hex);
    transition:
      transform 0.1s,
      background-color 0.3s var(--n-bezier),
      border-color 0.3s var(--n-bezier);
    &.play {
      border-color: rgba(var(--primary), 0.58);
      background-color: rgba(var(--primary), 0.28);
    }
    // &:active {
    //   transform: scale(0.99);
    // }
    &:hover {
      border-color: rgba(var(--primary), 0.58);
      .num {
        .n-text,
        .n-icon {
          opacity: 0;
        }
        .play {
          opacity: 1;
          transform: scale(1);
        }
      }
      &.play {
        .num {
          .play {
            display: none;
          }
          .status {
            opacity: 1;
            transform: scale(1);
          }
        }
      }
    }
  }
  .num {
    position: relative;
    display: flex;
    justify-content: center;
    align-items: center;
    width: 48px;
    min-width: 48px;
    min-height: 48px;
    font-weight: bold;
    margin-right: 12px;
    .n-icon {
      transition:
        opacity 0.3s,
        transform 0.3s;
      :deep(.svg-container) {
        color: var(--primary-hex);
      }
    }
    .status,
    .play {
      position: absolute;
      opacity: 0;
      transform: scale(0.8);
      transition:
        opacity 0.3s,
        transform 0.3s;
      &:active {
        opacity: 0.6 !important;
      }
    }
  }
  .title {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    padding: 4px 20px 4px 0;
    .cover {
      width: 50px;
      height: 50px;
      min-width: 50px;
      border-radius: 8px;
      margin-right: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    }
    .info {
      min-width: 0;
      flex: 1;
      .name {
        display: flex;
        flex-direction: row;
        align-items: center;
        line-height: normal;
        font-size: 16px;
      }
      .desc {
        min-width: 0;
        margin-top: 2px;
        font-size: 13px;
        .n-tag {
          --n-height: 18px;
          font-size: 10px;
          cursor: pointer;
          pointer-events: none;
          &:last-child {
            margin-right: 0;
          }
        }
        .quality {
          font-size: 10px;
        }
        .cloud {
          padding: 0 10px;
          align-items: center;
          justify-content: center;
          :deep(.n-tag__icon) {
            margin-right: 0;
            width: 100%;
          }
          .n-icon {
            font-size: 12px;
            color: var(--n-text-color);
          }
        }
        .mv {
          pointer-events: auto;
        }
      }
      .artists {
        flex: 1 1 100%;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        .ar {
          display: inline;
          transition: opacity 0.3s;
          opacity: 0.6;
          cursor: pointer;
          &::after {
            content: "/";
            margin: 0 4px;
          }
          &:last-child {
            &::after {
              display: none;
            }
          }
          &:hover {
            opacity: 0.8;
          }
        }
      }
    }
    .sort {
      margin-left: 6px;
      &::after {
        content: " )";
      }
      &::before {
        content: "( ";
      }
    }
  }
  .album {
    flex: 1;
    min-width: 0;
    line-clamp: 2;
    -webkit-line-clamp: 2;
    padding-right: 20px;
    &:hover {
      .album-text {
        color: var(--primary-hex);
      }
    }
  }
  .actions {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 48px;
    flex: 0 0 48px;
    .n-icon {
      color: var(--primary-hex);
      transition: transform 0.3s;
      cursor: pointer;
      &:hover {
        transform: scale(1.15);
      }
      &:active {
        transform: scale(1);
      }
    }
  }
  .meta {
    width: 50px;
    font-size: 13px;
    text-align: center;
    &.size {
      width: 60px;
    }
    &.date {
      width: 80px;
    }
  }
  &.header {
    border: none;
    background-color: transparent;
    .n-text {
      opacity: 0.6;
    }
    .title {
      position: relative;
      padding: 0 20px 0 0;
      &.has-sort {
        &::after {
          content: "";
          position: absolute;
          opacity: 0;
          top: 0;
          left: -8px;
          width: 100%;
          height: 100%;
          border-radius: 8px;
          background-color: rgba(var(--primary), 0.08);
          transition: opacity 0.3s;
        }
        &:hover {
          &::after {
            opacity: 1;
          }
        }
      }
    }
  }
}
</style>

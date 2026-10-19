<!-- 每日推荐 -->
<template>
  <div class="daily-songs">
    <div class="title">
      <n-text class="name">每日推荐</n-text>
      <div class="tip">
        <Transition name="fade" mode="out-in">
          <n-text :key="updatedTime" depth="3">
            根据你的音乐口味 ·
            {{ updatedTime ? "更新于 " + updatedTime : "每日 6:00 更新" }}
          </n-text>
        </Transition>
      </div>
      <n-flex class="menu">
        <n-button
          :focusable="false"
          type="primary"
          size="large"
          strong
          secondary
          round
          v-debounce="() => player.updatePlayList(musicStore.dailySongsData.list)"
        >
          <template #icon>
            <SvgIcon name="Play" />
          </template>
          播放全部
        </n-button>
        <!-- 更多 -->
        <n-dropdown
          :options="moreOptions"
          :menu-props="() => ({ class: 'daily-recommendation-menu' })"
          trigger="click"
          placement="bottom-start"
        >
          <n-button :focusable="false" size="large" class="more" circle strong secondary>
            <template #icon>
              <SvgIcon name="List" />
            </template>
          </n-button>
        </n-dropdown>
      </n-flex>
    </div>
    <!-- 列表 -->
    <SongList
      :data="musicStore.dailySongsData.list"
      :loading="true"
      height="auto"
      isDailyRecommend
      disabledSort
    />
  </div>
</template>

<script setup lang="ts">
import type { DropdownOption } from "naive-ui";
import { useMusicStore } from "@/stores";
import { updateDailySongsData } from "@/utils/auth";
import { formatTimestamp } from "@/utils/time";
import { renderIcon } from "@/utils/helper";
import { openBatchList } from "@/utils/modal";
import { usePlayerController } from "@/core/player/PlayerController";

const player = usePlayerController();
const musicStore = useMusicStore();

// 更新日期
const updatedTime = computed(() =>
  formatTimestamp(musicStore.dailySongsData.timestamp || 0, "MM-DD HH:mm"),
);

// 更多操作
const moreOptions = computed<DropdownOption[]>(() => [
  {
    label: "更新日推",
    key: "refresh",
    props: {
      onClick: async () => {
        await updateDailySongsData(true);
      },
    },
    icon: renderIcon("Refresh"),
  },
  {
    label: "批量操作",
    key: "batch",
    props: {
      onClick: () => openBatchList(musicStore.dailySongsData.list, false),
    },
    icon: renderIcon("Batch"),
  },
]);

onActivated(updateDailySongsData);
onMounted(updateDailySongsData);
</script>

<style lang="scss" scoped>
.daily-songs {
  .title {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    height: 300px;
    margin-bottom: 20px;
    .name {
      font-size: 55px;
      font-weight: bold;
      animation: fade-spacing 1s cubic-bezier(0.4, 0, 0.2, 1) forwards;
    }
    .tip {
      font-size: 16px;
      margin-top: 6px;
      opacity: 0;
      animation: fade-down 0.5s cubic-bezier(0.4, 0, 0.2, 1) forwards;
      animation-delay: 0.5s;
    }
    .menu {
      margin-top: 30px;
    }
  }
  .song-list {
    height: auto;
  }
}

@media (max-width: 599px) {
  :global(.daily-recommendation-menu) {
    max-width: calc(100vw - 24px);
  }
  :global(.daily-recommendation-menu .n-dropdown-option-body) {
    height: auto;
    min-height: 48px;
    padding: 8px 0;
    line-height: 1.4;
    align-items: center;
  }
  :global(.daily-recommendation-menu .n-dropdown-option-body__label) {
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .daily-songs .title {
    height: auto;
    padding: 24px 0 20px;
    margin-bottom: 12px;
    gap: 8px;
    .name,
    .tip {
      max-width: 100%;
      text-align: center;
      overflow-wrap: anywhere;
    }
    .tip {
      margin-top: 0;
    }
    .menu {
      max-width: 100%;
      justify-content: center;
      margin-top: 8px;
      :deep(.n-button) {
        min-width: 48px;
        min-height: 48px;
        height: auto;
      }
      :deep(.n-button__content) {
        white-space: normal;
      }
      :deep(.n-button__icon) {
        flex-shrink: 0;
      }
    }
  }
}
</style>

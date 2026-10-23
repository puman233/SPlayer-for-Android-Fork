<template>
  <div class="home-local">
    <n-flex class="library-actions" :size="12" align="center">
      <n-button
        class="open-local-library"
        type="primary"
        strong
        secondary
        round
        @click="router.push('/local/songs')"
      >
        <template #icon><SvgIcon name="Music" /></template>
        本地音乐
      </n-button>
      <n-button strong secondary round @click="openLocalMusicDirectoryModal">
        <template #icon><SvgIcon name="FolderCog" /></template>
        本地目录管理
      </n-button>
      <n-button strong secondary round @click="router.push('/history')">
        <template #icon><SvgIcon name="History" /></template>
        最近播放
      </n-button>
    </n-flex>
    <template v-if="localStore.localSongs.length">
      <n-flex class="library-heading" justify="space-between" align="center">
        <n-h2>本地音乐 · {{ localStore.localSongs.length }} 首</n-h2>
        <n-button secondary round @click="player.updatePlayList(localStore.localSongs)">
          <template #icon><SvgIcon name="Play" /></template>
          播放
        </n-button>
      </n-flex>
      <SongList :data="localStore.localSongs" :hidden-cover="!settingStore.showLocalCover" />
    </template>
    <n-empty v-else class="library-empty" description="添加音乐目录，开始聆听本地歌曲">
      <template #extra>
        <n-button type="primary" secondary round @click="openLocalMusicDirectoryModal"
          >添加音乐目录</n-button
        >
      </template>
    </n-empty>
  </div>
</template>

<script setup lang="ts">
import { useLocalStore, useSettingStore } from "@/stores";
import { usePlayerController } from "@/core/player/PlayerController";
import { openLocalMusicDirectoryModal } from "@/utils/modal";

const router = useRouter();
const localStore = useLocalStore();
const settingStore = useSettingStore();
const player = usePlayerController();
</script>

<style lang="scss" scoped>
.home-local {
  .library-actions .n-button {
    min-height: 48px;
  }

  .library-heading {
    margin: 24px 0 16px;

    .n-h2 {
      margin: 0;
    }
  }

  .library-empty {
    padding: 64px 0;
  }
}
</style>

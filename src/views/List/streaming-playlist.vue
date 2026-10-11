<!-- 流媒体歌单详情 -->
<template>
  <div class="playlist-list">
    <ListDetail
      @header-height="headerHeight = $event"
      :detail-data="detailData"
      :list-data="listData"
      :loading="showLoading"
      :list-scrolling="listScrolling"
      :search-value="searchValue"
      :config="listConfig"
      :play-button-text="playButtonText"
      :more-options="moreOptions"
      hide-comment-tab
      @update:search-value="handleSearchUpdate"
      @play-all="playAllSongs"
    />
    <Transition name="fade" mode="out-in">
      <SongList
        v-if="!searchValue || searchData?.length"
        :data="displayData"
        :loading="loading"
        :height="songListHeight"
        @scroll="handleListScroll"
      />
      <n-empty
        v-else
        :description="`搜不到关于 ${searchValue} 的任何歌曲呀`"
        style="margin-top: 60px"
        size="large"
      >
        <template #icon>
          <SvgIcon name="SearchOff" />
        </template>
      </n-empty>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { requestFailureCategory } from "@/utils/requestDiagnostics";
import type { DropdownOption } from "naive-ui";
import type { CoverType } from "@/types/main";
import { useStreamingStore } from "@/stores";
import { renderIcon } from "@/utils/helper";
import { useListDetail } from "@/composables/List/useListDetail";
import { useListSearch } from "@/composables/List/useListSearch";
import { useListScroll } from "@/composables/List/useListScroll";
import { useListActions } from "@/composables/List/useListActions";
import { useListDataCache } from "@/composables/List/useListDataCache";

const router = useRouter();
const streamingStore = useStreamingStore();

const { detailData, listData, loading, headerHeight, getSongListHeight, setDetailData, setListData, setLoading } =
  useListDetail();
const { searchValue, searchData, displayData, clearSearch, performSearch } =
  useListSearch(listData);
const { listScrolling, handleListScroll, resetScroll } = useListScroll();
const { playAllSongs: playAllSongsAction } = useListActions();
const { saveCache, loadCache } = useListDataCache();

// 歌单 ID
const playlistId = computed<string>(() => router.currentRoute.value.query.id as string);

// 列表高度
const songListHeight = computed(() => getSongListHeight(listScrolling.value));

// 列表配置
const listConfig = {
  titleType: "normal" as const,
  showCoverMask: false,
  showPlayCount: false,
  showArtist: false,
  showCreator: false,
  showCount: true,
  searchAlign: "center" as const,
};

// 是否显示加载状态
const showLoading = computed(() => listData.value.length === 0 && loading.value);

// 播放按钮文本
const playButtonText = computed(() => {
  if (showLoading.value) {
    return "加载中...";
  }
  return "播放";
});

// 更多操作
const moreOptions = computed<DropdownOption[]>(() => [
  {
    label: "刷新",
    key: "refresh",
    props: {
      onClick: () => getPlaylistDetail(playlistId.value),
    },
    icon: renderIcon("Refresh"),
  },
]);

let requestRevision = 0;
const clearIdentityView = () => {
  ++requestRevision;
  setDetailData(null); setListData([]); clearSearch(); setLoading(false);
};
watch(() => [streamingStore.sessionRevision.value, streamingStore.getCacheScope(), streamingStore.isConnected.value], () => {
  clearIdentityView();
  if (playlistId.value && streamingStore.getCacheScope()) void getPlaylistDetail(playlistId.value);
}, { flush: "sync" });
onBeforeUnmount(clearIdentityView);

// 获取歌单详情
const getPlaylistDetail = async (id: string, refresh: boolean = false) => {
  if (!id) return;
  const revision = ++requestRevision;
  const scope = streamingStore.getCacheScope();
  const session = streamingStore.sessionRevision.value;
  const current = () => revision === requestRevision && session === streamingStore.sessionRevision.value && scope === streamingStore.getCacheScope();
  if (!scope) { clearIdentityView(); return; }

  setLoading(true);
  clearSearch();
  resetScroll();

  // 1. 先尝试本地缓存（流媒体可能离线，缓存命中可立即显示）
  if (!refresh) {
    const cached = await loadCache("streaming-playlist", id, scope);
    if (!current()) return;
    if (cached) {
      setDetailData(cached.detail);
      setListData(cached.songs);
      setLoading(false);
      // 后台刷新（仅当流媒体已连接）
      if (streamingStore.isConnected.value) {
        void backgroundRefresh(id, scope, current);
      }
      return;
    }
  }

  if (!streamingStore.isConnected.value) {
    window.$message.error("流媒体服务器未连接");
    setLoading(false);
    return;
  }

  try {
    // 从缓存的歌单列表中查找歌单信息
    const playlist = streamingStore.playlists.value.find((p) => p.id === id);
    if (playlist) {
      setDetailData({
        id: Number(playlist.id) || 0,
        name: playlist.name,
        cover: playlist.cover || "/images/album.jpg?asset",
        description: playlist.description,
        count: playlist.songCount || 0,
      } as CoverType);
    }

    // 获取歌单歌曲
    const songs = await streamingStore.fetchPlaylistSongs(id);
    if (!current()) return;
    setListData(songs);

    // 如果之前没有获取到歌单信息，更新歌曲数量
    if (detailData.value && detailData.value.count === 0) {
      detailData.value.count = songs.length;
    }

    // 写入本地缓存（仅当 detail 存在）
    if (detailData.value) {
      await saveCache("streaming-playlist", id, detailData.value, songs, true, scope);
    }
  } catch (error) {
    if (!current()) return;
    console.error("Failed to fetch streaming playlist:", requestFailureCategory(error));
    window.$message.error("获取歌单详情失败");
  } finally {
    if (current()) setLoading(false);
  }
};

// 后台静默刷新：缓存命中时使用，不阻塞 UI
const backgroundRefresh = async (id: string, scope: string, current: () => boolean) => {
  if (!current()) return;
  try {
    const playlist = streamingStore.playlists.value.find((p) => p.id === id);
    if (playlist) {
      setDetailData({
        id: Number(playlist.id) || 0,
        name: playlist.name,
        cover: playlist.cover || "/images/album.jpg?asset",
        description: playlist.description,
        count: playlist.songCount || 0,
      } as CoverType);
    }
    const songs = await streamingStore.fetchPlaylistSongs(id);
    if (!current()) return;
    setListData(songs);
    if (detailData.value) {
      await saveCache("streaming-playlist", id, detailData.value, songs, true, scope);
    }
  } catch (e) {
    if (!current()) return;
    // 后台刷新失败不打扰用户：缓存数据仍可用
    console.warn("[streaming-playlist] background refresh failed", requestFailureCategory(e));
  }
};

// 处理搜索更新
const handleSearchUpdate = (val: string) => {
  searchValue.value = val;
  performSearch(val);
};

// 播放全部歌曲
const playAllSongs = useDebounceFn(() => {
  if (!detailData.value || !displayData.value?.length) return;
  playAllSongsAction(displayData.value);
}, 300);

onBeforeRouteUpdate((to) => {
  const id = to.query.id as string;
  if (id) {
    getPlaylistDetail(id);
  }
});

onMounted(() => {
  if (playlistId.value) {
    getPlaylistDetail(playlistId.value);
  }
});
</script>

<style lang="scss" scoped>
.playlist-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
</style>

<template>
  <div class="home-online">
    <div v-if="isLogin()" class="main-rec">
      <div class="main-rec-grid">
        <n-flex :size="20" class="rec-list" justify="space-between" vertical>
          <SongListCard
            :data="musicStore.dailySongsData.list"
            :title="dailySongsTitle"
            :height="90"
            description="根据你的音乐口味 · 每日更新"
            size="small"
            :hiddenCover="settingStore.hiddenCovers.home"
            @click="router.push({ name: 'daily-songs' })"
          />
          <SongListCard
            :data="dataStore.likeSongsList.data"
            :height="90"
            title="我喜欢的音乐"
            description="发现你独特的音乐品味"
            size="small"
            :hiddenCover="settingStore.hiddenCovers.home"
            @click="router.push({ name: 'like-songs' })"
          />
        </n-flex>
        <PersonalFM />
      </div>
    </div>

    <div v-for="(item, index) in sortedRecData" :key="index" class="rec-public">
      <n-flex
        class="title"
        align="center"
        justify="space-between"
        @click="router.push({ path: item.path ?? undefined })"
      >
        <n-h3 prefix="bar">
          <n-text>{{ item.name }}</n-text>
          <SvgIcon v-if="item.path" :size="26" name="Right" />
        </n-h3>
      </n-flex>

      <ArtistList
        v-if="item.type === 'artist'"
        :data="item.list"
        :loading="true"
        :hiddenCover="settingStore.hiddenCovers.home"
      />
      <CoverList
        v-else
        :data="item.list"
        :type="item.type"
        :loading="true"
        :hiddenCover="settingStore.hiddenCovers.home"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ArtistType, CoverType } from "@/types/main";
import { NText } from "naive-ui";
import { useDataStore, useMusicStore, useSettingStore } from "@/stores";
import { newAlbumsAll, personalized, radarPlaylist, topArtists } from "@/api/rec";
import { allMv } from "@/api/video";
import { radioRecommend } from "@/api/radio";
import { getCacheData } from "@/utils/cache";
import { formatArtistsList, formatCoverList } from "@/utils/format";
import { sleep } from "@/utils/helper";
import { isLogin } from "@/utils/auth";
import SvgIcon from "@/components/Global/SvgIcon.vue";

interface RecItemTypeBase {
  name: string;
  path?: string;
}

interface RecItemArtist extends RecItemTypeBase {
  type: "artist";
  list: ArtistType[];
}

interface RecItemCover extends RecItemTypeBase {
  type: "playlist" | "video" | "radio" | "album";
  list: CoverType[];
}

interface RecDataType {
  playlist: RecItemCover;
  radar: RecItemCover;
  artist: RecItemArtist;
  video: RecItemCover;
  radio: RecItemCover;
  album: RecItemCover;
}

const router = useRouter();
const dataStore = useDataStore();
const musicStore = useMusicStore();
const settingStore = useSettingStore();

const dailySongsTitle = computed(() => {
  if (settingStore.hiddenCovers.home) return "每日推荐";
  const day = new Date().getDate();
  return h("div", { class: "date" }, [
    h("div", { class: "date-icon" }, [
      h(SvgIcon, { name: "Calendar-Empty", size: 30, depth: 2 }),
      h(NText, null, () => day),
    ]),
    h(NText, { class: "name text-hidden" }, () => ["每日推荐"]),
  ]);
});

const recData = ref<RecDataType>({
  playlist: {
    name: isLogin() ? "专属歌单" : "推荐歌单",
    list: [] as CoverType[],
    type: "playlist",
    path: "/discover/playlists",
  },
  radar: {
    name: "雷达歌单",
    list: [] as CoverType[],
    type: "playlist",
  },
  artist: {
    name: "歌手推荐",
    list: [] as ArtistType[],
    type: "artist",
    path: "/discover/artists",
  },
  video: {
    name: "推荐 MV",
    list: [] as CoverType[],
    type: "video",
  },
  radio: {
    name: "推荐播客",
    list: [] as CoverType[],
    type: "radio",
  },
  album: {
    name: "新碟上架",
    list: [] as CoverType[],
    type: "album",
    path: "/discover/new",
  },
});

const sortedRecData = computed(() => {
  return settingStore.homePageSections
    .filter((section) => section.visible)
    .sort((a, b) => a.order - b.order)
    .map((section) => recData.value[section.key as keyof RecDataType])
    .filter(Boolean);
});

const getAllRecData = async () => {
  try {
    await sleep(300);

    try {
      const playlistRes = await getCacheData(
        personalized,
        { key: "playlistRec", time: 10 },
        "playlist",
        isLogin() ? 21 : 20,
      );
      recData.value.playlist.list = formatCoverList(
        playlistRes.result?.filter((pl: any) => !pl.name.includes("私人雷达")),
      );
    } catch (error) {
      console.error("Error getting playlist:", error);
    }

    try {
      const radarRes = await getCacheData(radarPlaylist, { key: "radarRec", time: 30 });
      recData.value.radar.list = formatCoverList(radarRes);
    } catch (error) {
      console.error("Error getting radar:", error);
    }

    try {
      const artistRes = await getCacheData(topArtists, { key: "artistRec", time: 10 }, 6);
      recData.value.artist.list = formatArtistsList(artistRes.artists);
    } catch (error) {
      console.error("Error getting artist:", error);
    }

    try {
      const videoRes = await getCacheData(allMv, { key: "videoRec", time: 10 });
      recData.value.video.list = formatCoverList(videoRes.data);
    } catch (error) {
      console.error("Error getting video:", error);
    }

    try {
      const radioRes = await getCacheData(radioRecommend, { key: "radioRec", time: 10 });
      recData.value.radio.list = formatCoverList(radioRes.djRadios);
    } catch (error) {
      console.error("Error getting radio:", error);
    }

    try {
      const albumRes = await getCacheData(newAlbumsAll, { key: "albumRec", time: 10 });
      recData.value.album.list = formatCoverList(albumRes.albums);
    } catch (error) {
      console.error("Error getting album:", error);
    }
  } catch (error) {
    window.$message.error("个性化推荐获取出错");
    console.error("Error getting personalized data:", error);
  }
};

onActivated(getAllRecData);

onMounted(() => {
  getAllRecData();
});
</script>

<style lang="scss" scoped>
.main-rec {
  .main-rec-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 20px;
  }

  .date {
    display: flex;
    align-items: center;
    margin-bottom: 4px;

    .date-icon {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 4px;

      .n-text {
        position: absolute;
        font-size: 12px;
        color: var(--primary-hex);
        line-height: normal;
        margin-top: 4px;
        transform: scale(0.8);
      }
    }

    .name {
      font-size: 18px;
      font-weight: bold;
    }
  }

  @media (max-width: 768px) {
    .main-rec-grid {
      grid-template-columns: repeat(1, 1fr);
      gap: 12px;
    }

    .rec-list {
      display: grid !important;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;
    }
  }
}

.title {
  margin-top: 28px;
  padding: 0 4px;
  width: max-content;

  .n-h {
    margin: 0;
    display: flex;
    align-items: center;
    cursor: pointer;

    .n-icon {
      opacity: 0;
      transform: translateX(4px);
      transition:
        opacity 0.3s,
        transform 0.3s;
    }

    &:hover {
      .n-icon {
        opacity: 1;
        transform: translateX(0);
      }
    }
  }

  @media (max-width: 768px) {
    margin-top: 20px;
    padding: 0;
  }
}
</style>

<template>
  <div class="like-type">
    <n-alert v-if="error" type="error" :title="error">
      <n-button @click="reload">重试</n-button>
    </n-alert>
    <ArtistList
      v-else-if="category === 'artists'"
      :data="dataStore.userLikeData.artists"
      :loading="loading"
      :hiddenCover="settingStore.hiddenCovers.like"
    />
    <CoverList
      v-else
      :data="dataStore.userLikeData[category]"
      :type="coverType"
      :cols="category === 'mvs' ? '2 600:2 800:3 900:4 1200:5 1400:6' : undefined"
      :loading="loading"
      :hiddenCover="settingStore.hiddenCovers.like"
    />
  </div>
</template>

<script setup lang="ts">
import { useDataStore, useSettingStore } from "@/stores";
import {
  updateUserLikeAlbums,
  updateUserLikeArtists,
  updateUserLikeMvs,
  updateUserLikeDjs,
} from "@/utils/auth";
import { useCategoryRequest } from "@/composables/useCategoryRequest";

const props = defineProps<{ category: "albums" | "artists" | "mvs" | "djs" }>();
const dataStore = useDataStore();
const settingStore = useSettingStore();
const { loading, error, run } = useCategoryRequest();
const loaders = {
  albums: updateUserLikeAlbums,
  artists: updateUserLikeArtists,
  mvs: updateUserLikeMvs,
  djs: updateUserLikeDjs,
};
const coverType = computed(() =>
  props.category === "mvs" ? "video" : props.category === "djs" ? "radio" : "album",
);
const reload = () => run(loaders[props.category], () => {});
onMounted(reload);
</script>

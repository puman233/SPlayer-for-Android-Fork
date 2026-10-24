<template>
  <button ref="card" type="button" class="home-rec-card song-data-card small" :aria-label="title">
    <span v-if="!hiddenCover" ref="media" class="cover" :class="{ flow }">
      <s-image class="art" :src="album" alt="" cache-type="covers" />
      <span class="caption info">
        <span ref="material" class="caption-material" aria-hidden="true" />
        <span class="caption-blur" aria-hidden="true" />
        <span class="caption-blur middle" aria-hidden="true" />
        <span class="caption-blur lower" aria-hidden="true" />
        <span ref="titleElement" class="name">{{ title }}</span>
      </span>
    </span>
    <span v-else class="plain-title info"
      ><span class="name">{{ title }}</span></span
    >
  </button>
</template>

<script setup lang="ts">
import type { SongType } from "@/types/main";
import { useStatusStore } from "@/stores";
import {
  BackgroundRender,
  MeshGradientRenderer,
  type AbstractBaseRenderer,
} from "@applemusic-like-lyrics/core";
import {
  useDocumentVisibility,
  useElementVisibility,
  usePreferredReducedMotion,
  useResizeObserver,
} from "@vueuse/core";

const props = defineProps<{
  title: string;
  data?: SongType[];
  cover?: string;
  hiddenCover?: boolean;
}>();

const statusStore = useStatusStore();
const card = useTemplateRef<HTMLElement>("card");
const media = useTemplateRef<HTMLElement>("media");
const titleElement = useTemplateRef<HTMLElement>("titleElement");
const material = useTemplateRef<HTMLElement>("material");
// Keep the random selection stable until the input list changes, as in SongListCard.
const selectedSong = computed(() => {
  const songs = props.data;
  return songs?.length ? songs[Math.floor(Math.random() * songs.length)] : undefined;
});
const album = computed(
  () =>
    props.cover ||
    selectedSong.value?.coverSize?.m ||
    selectedSong.value?.cover ||
    "/images/song.jpg?asset",
);
const flow = ref(false);
const active = ref(true);
const visible = useElementVisibility(card);
const documentVisibility = useDocumentVisibility();
const reducedMotion = usePreferredReducedMotion();
let renderer: AbstractBaseRenderer | undefined;

// Text zoom changes the rendered text height without changing its CSS font size.
useResizeObserver([media, titleElement], () => {
  if (media.value && titleElement.value) {
    flow.value =
      titleElement.value.getBoundingClientRect().height + 8 > media.value.clientWidth * 0.65;
  }
});

const releaseRenderer = () => {
  const previous = renderer;
  renderer = undefined;
  previous?.dispose();
  material.value?.replaceChildren();
};

const animate = computed(
  () =>
    active.value &&
    !statusStore.showFullPlayer &&
    visible.value &&
    documentVisibility.value === "visible" &&
    reducedMotion.value !== "reduce" &&
    !flow.value &&
    !props.hiddenCover,
);

watch(
  [animate, album, material],
  () => {
    releaseRenderer();
    if (!animate.value || !material.value) return;
    try {
      const current = BackgroundRender.new(MeshGradientRenderer);
      renderer = current;
      const canvas = current.getElement();
      canvas.style.width = "100%";
      canvas.style.height = "100%";
      canvas.style.display = "block";
      material.value.append(canvas);
      current.setFPS(24);
      current.setRenderScale(0.3);
      current.setFlowSpeed(1);
      current.setLowFreqVolume(0);
      current.setHasLyric(false);
      void current.setAlbum(album.value).catch(() => {
        // The cover and local blur remain readable if texture loading fails.
        if (renderer === current) releaseRenderer();
      });
    } catch {
      releaseRenderer();
    }
  },
  { flush: "post" },
);

onActivated(() => {
  active.value = true;
});
onDeactivated(() => {
  active.value = false;
  releaseRenderer();
});
onBeforeUnmount(releaseRenderer);
</script>

<style scoped lang="scss">
.home-rec-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  min-height: 48px;
  padding: 6px 0;
  border: 1px solid rgba(var(--primary), 0.12);
  border-radius: 12px;
  background: var(--surface-container-hex);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  &:focus-visible {
    outline: 2px solid var(--primary-hex);
    outline-offset: 2px;
  }
  &:active {
    background: rgba(var(--primary), 0.15);
  }
  .cover {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    width: 84%;
    min-width: 0;
    border-radius: 8px;
    overflow: hidden;
    clip-path: inset(0 round 8px);
    isolation: isolate;
  }
  .art {
    grid-area: 1 / 1;
    width: 100% !important;
    height: auto !important;
    aspect-ratio: 1;
    align-self: start;
    border-radius: 8px;
    overflow: hidden;
    clip-path: inset(0 round 8px);
  }
  .caption {
    grid-area: 1 / 1;
    align-self: end;
    justify-self: stretch;
    position: relative;
    isolation: isolate;
    z-index: 1;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    box-sizing: border-box;
    padding: 4px 6px;
    color: #fff;
    &::before,
    .caption-material,
    .caption-blur {
      content: "";
      position: absolute;
      inset: 0;
      pointer-events: none;
    }
    &::before {
      z-index: -1;
      background: linear-gradient(transparent, rgba(15, 20, 20, 0.64));
    }
  }
  .caption-material {
    z-index: -3;
    opacity: 0.12;
    mask-image: linear-gradient(transparent, #000);
    -webkit-mask-image: linear-gradient(transparent, #000);
  }
  .caption-blur {
    z-index: -2;
    backdrop-filter: blur(0.5px);
    -webkit-backdrop-filter: blur(0.5px);
    mask-image: linear-gradient(transparent, #000);
    -webkit-mask-image: linear-gradient(transparent, #000);
    &.middle {
      backdrop-filter: blur(1px);
      -webkit-backdrop-filter: blur(1px);
      mask-image: linear-gradient(transparent 25%, #000);
      -webkit-mask-image: linear-gradient(transparent 25%, #000);
    }
    &.lower {
      backdrop-filter: blur(2px);
      -webkit-backdrop-filter: blur(2px);
      mask-image: linear-gradient(transparent 55%, #000);
      -webkit-mask-image: linear-gradient(transparent 55%, #000);
    }
  }
  .name {
    display: block;
    min-width: 0;
    max-width: 100%;
    white-space: normal;
    overflow-wrap: anywhere;
    position: relative;
    font-size: clamp(17px, 4.6vw, 19px);
    font-weight: bold;
    line-height: 1.4;
  }
  .caption .name {
    text-shadow: 0 1px 3px #0007;
  }
  .plain-title {
    padding: 12px 8px;
    align-self: stretch;
  }
  .flow {
    .caption {
      grid-area: 2 / 1;
      color: inherit;
    }
    .caption .name {
      text-shadow: none;
    }
    .caption::before,
    .caption-material,
    .caption-blur {
      display: none;
    }
  }
  @media (prefers-reduced-transparency: reduce) {
    .caption::before {
      background: var(--surface-container-hex);
    }
    .caption {
      color: inherit;
    }
    .caption-material,
    .caption-blur {
      display: none;
    }
  }
}
</style>

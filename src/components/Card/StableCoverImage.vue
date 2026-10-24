<template>
  <div ref="container" class="stable-cover" :data-cover-source="current?.source">
    <img v-if="!current" class="initial" src="/images/song.jpg?asset" alt="" />
    <s-image
      v-for="frame in frames"
      :key="frame.key"
      :src="frame.source"
      :class="['frame', { pending: frame.key !== current?.key }]"
      :alt="alt || ''"
      :observe-visibility="false"
      :native-lazy="false"
      cache-type="covers"
      @load="loaded(frame.key, $event)"
      @error="failed(frame.key)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useTemplateRef, onActivated, onDeactivated } from "vue";
import { useDocumentVisibility, useElementVisibility } from "@vueuse/core";
import { useStatusStore } from "@/stores";
import { useStableCover } from "@/composables/useStableCover";

const props = defineProps<{ src?: string; sources?: string[]; alt?: string }>();
const container = useTemplateRef<HTMLElement>("container");
const visible = useElementVisibility(container);
const documentVisibility = useDocumentVisibility();
const activated = ref(true);
const status = useStatusStore();
const { current, pending, ready, failed } = useStableCover({
  sources: () => props.sources ?? [props.src],
  active: () =>
    activated.value &&
    visible.value &&
    documentVisibility.value === "visible" &&
    !status.showFullPlayer,
});
const frames = computed(() => [current.value, pending.value].filter((frame) => !!frame));
const emit = defineEmits<{ display: [source: string] }>();
const loaded = async (key: number, event: Event) => {
  const image = event.target as HTMLImageElement;
  if (!image.complete || image.naturalWidth < 1) return;
  await ready(key, () => (image.decode ? image.decode() : Promise.resolve()));
  if (current.value?.key === key) emit("display", current.value.source);
};
onActivated(() => {
  activated.value = true;
});
onDeactivated(() => {
  activated.value = false;
});
</script>

<style scoped lang="scss">
.stable-cover {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  .initial,
  .frame {
    position: absolute;
    inset: 0;
    width: 100% !important;
    height: 100% !important;
    object-fit: cover;
  }
  .pending {
    opacity: 0;
    pointer-events: none;
  }
  :deep(img) {
    transition: none !important;
  }
}
</style>

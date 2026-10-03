<template>
  <n-tag class="adaptive-tag" :class="{ interactive }">
    <template v-if="$slots.icon" #icon><slot name="icon" /></template>
    <slot />
  </n-tag>
</template>

<script setup lang="ts">
defineProps<{ interactive?: boolean }>();
</script>

<style scoped lang="scss">
.adaptive-tag {
  height: auto;
  min-height: 24px;
  max-width: 100%;
  flex-shrink: 0;
  padding-top: 3px;
  padding-bottom: 3px;
  :deep(.n-tag__content) {
    min-width: 0;
    flex: 1 1 auto;
    white-space: normal;
    overflow-wrap: anywhere;
    line-height: 1.3;
  }
  :deep(.n-icon) {
    width: 16px;
    height: 16px;
  }
  :deep(svg) {
    max-width: 100%;
    max-height: 100%;
  }
  &.interactive {
    min-width: var(--adaptive-touch-target, 48px);
    min-height: var(--adaptive-touch-target, 48px);
    cursor: pointer;
  }
}
</style>

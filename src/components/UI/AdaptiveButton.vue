<template>
  <n-button class="adaptive-button" :class="{ 'icon-only': iconOnly }">
    <template v-if="$slots.icon" #icon><slot name="icon" /></template>
    <slot />
  </n-button>
</template>

<script setup lang="ts">
defineProps<{ iconOnly?: boolean }>();
</script>

<style scoped lang="scss">
.adaptive-button {
  min-width: var(--adaptive-touch-target, 48px);
  min-height: var(--adaptive-touch-target, 48px);
  height: auto;
  flex-shrink: 0;
  :deep(.n-button__content) {
    min-width: 0;
    flex: 1 1 auto;
    white-space: normal;
    overflow-wrap: anywhere;
    line-height: 1.4;
  }
  :deep(.n-icon),
  :deep(.n-button__icon) {
    width: 24px;
    height: 24px;
    flex: 0 0 24px;
  }
  :deep(svg) {
    max-width: 100%;
    max-height: 100%;
  }
  &.icon-only {
    width: var(--adaptive-touch-target, 48px);
    height: var(--adaptive-touch-target, 48px);
    padding: 0;
  }
}
</style>

<template>
  <div
    :class="['app-shell', `app-shell--${shellMode}`]"
    :data-window-width-class="adaptiveWindow.widthClass"
    :data-window-height-class="adaptiveWindow.heightClass"
    :data-system-font-scale="fontScale"
  >
    <Provider>
      <router-view />
    </Provider>
  </div>
</template>

<script setup lang="ts">
// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) SPlayer-Dev Contributors
// Original source: https://github.com/SPlayer-Dev/SPlayer
import { useDevice, updateSystemFontScale } from "@/composables/useDevice";
import { AndroidNativePlayback } from "@/plugins/androidNativePlayback";
import { isCapacitorAndroid } from "@/utils/env";
import type { PluginListenerHandle } from "@capacitor/core";
import { useImmersive } from "@/composables/useImmersive";
import { useAndroidBack } from "@/composables/useAndroidBack";
import { usePageZoom } from "@/composables/usePageZoom";
import { useSettingStore } from "@/stores";

const { shellMode, deviceModeOverride, adaptiveWindow, dimensions, fontScale } = useDevice();
const settingStore = useSettingStore();
const adaptiveStyle = computed(() => ({
  ...Object.fromEntries(
    Object.entries(dimensions.value.spacing).map(([name, value]) => [
      `--adaptive-space-${name}`,
      `${value}px`,
    ]),
  ),
  ...Object.fromEntries(
    Object.entries(dimensions.value.typography).map(([name, value]) => [
      `--adaptive-font-${name}`,
      `${value}px`,
    ]),
  ),
  "--adaptive-horizontal-padding": `${dimensions.value.horizontalPadding}px`,
  "--adaptive-touch-target": `${dimensions.value.control.minimumTouchTarget}px`,
  "--adaptive-system-font-scale": fontScale.value,
}));

// 写入根节点，使 Teleport 到 #app/body 的播放器和弹层共享同一份 token
watchEffect(() => {
  for (const [name, value] of Object.entries(adaptiveStyle.value)) {
    document.documentElement.style.setProperty(name, String(value));
  }
});

let configurationListener: PluginListenerHandle | undefined;
let disposed = false;
onMounted(async () => {
  if (!isCapacitorAndroid) return;
  try {
    const listener = await AndroidNativePlayback.addListener("uiConfigurationChanged", (event) => {
      if (!disposed) updateSystemFontScale(event.fontScale);
    });
    if (disposed) {
      await listener.remove();
      return;
    }
    configurationListener = listener;
    const configuration = await AndroidNativePlayback.getUiConfiguration();
    if (!disposed) updateSystemFontScale(configuration.fontScale);
  } catch (error) {
    console.error("[AdaptiveLayout] 获取系统字体配置失败", error);
  }
});
onBeforeUnmount(() => {
  disposed = true;
  void configurationListener?.remove();
  for (const name of Object.keys(adaptiveStyle.value)) {
    document.documentElement.style.removeProperty(name);
  }
});

// 设备形态手动覆盖：从设置项同步到 useDevice 模块级 ref
watch(
  () => settingStore.androidDeviceModeOverride,
  (mode) => {
    deviceModeOverride.value = mode ?? "auto";
  },
  { immediate: true },
);

useImmersive();
useAndroidBack();
usePageZoom();
</script>

<style scoped>
.app-shell {
  width: 100%;
  height: 100%;
}
</style>

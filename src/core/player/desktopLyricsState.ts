import { computed, ref } from "vue";
import { AndroidNativePlayback } from "@/plugins/androidNativePlayback";
import { useStatusStore } from "@/stores";
import { isCapacitorAndroid } from "@/utils/env";

export const desktopLyricsBusy = ref(false);
const permission = ref(!isCapacitorAndroid);
const ready = ref(!isCapacitorAndroid);
let revision = 0;

export const desktopLyricsState = computed(() =>
  !permission.value ? "PERMISSION_REQUIRED" : useStatusStore().showDesktopLyric ? "ON" : "OFF",
);
export const desktopLyricsReady = computed(() => ready.value);

/** 以原生窗口为准，持久化开关只作为兼容数据，不冒充运行状态。 */
export const refreshDesktopLyricsState = async (force = false) => {
  if (!isCapacitorAndroid || (desktopLyricsBusy.value && !force)) return;
  const current = ++revision;
  const state = await AndroidNativePlayback.getFloatingLyricState();
  if (current !== revision || (desktopLyricsBusy.value && !force)) return;
  permission.value = state.granted;
  useStatusStore().showDesktopLyric = state.enabled && state.granted;
  ready.value = true;
};

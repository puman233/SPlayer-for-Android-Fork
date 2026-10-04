import { computed, onScopeDispose, ref, watch, type Ref } from "vue";
import { useTimeoutFn } from "@vueuse/core";
import type { PlayerMetaHold } from "./usePlayerMetaHold";

export type MobilePlayerPage = "info" | "lyric" | "comment";

export function useMobilePlayerControls(page: Ref<MobilePlayerPage>, delay = 4000) {
  const visibility = ref({ info: true, lyric: true, comment: true });
  const visible = computed(() => page.value === "info" || visibility.value[page.value]);
  const pointers = new Set<number>();
  const touches = new Set<number>();
  const holds = ref(0);
  let disposed = false;
  const { start, stop } = useTimeoutFn(
    () => {
      if (page.value !== "info" && !pointers.size && !touches.size && !holds.value) {
        visibility.value[page.value] = false;
      }
    },
    delay,
    { immediate: false },
  );

  const interact = () => {
    if (disposed) return;
    visibility.value[page.value] = true;
    stop();
    if (page.value !== "info" && !pointers.size && !touches.size && !holds.value) start();
  };
  const pointerDown = (event: PointerEvent) => {
    pointers.add(event.pointerId);
    // 隐藏时等 click 命中原目标后再恢复，避免布局变化打断歌词点击
    if (visible.value) interact();
    else stop();
  };
  const pointerEnd = (event: PointerEvent) => {
    if (!pointers.delete(event.pointerId)) return;
    if (visible.value) interact();
    else if (!pointers.size && !touches.size && !holds.value) start();
  };
  // 浏览器接管纵向滚动会发 pointercancel，手指实际松开前仍需保持控件
  const touchStart = (event: TouchEvent) => {
    for (const touch of Array.from(event.changedTouches)) touches.add(touch.identifier);
    if (visible.value) interact();
    else stop();
  };
  const touchEnd = (event: TouchEvent) => {
    let changed = false;
    for (const touch of Array.from(event.changedTouches)) {
      if (touches.delete(touch.identifier)) changed = true;
    }
    if (!changed) return;
    if (visible.value) interact();
    else if (!pointers.size && !touches.size && !holds.value) start();
  };
  const hold: PlayerMetaHold = {
    acquire: () => {
      holds.value++;
      interact();
    },
    release: () => {
      holds.value = Math.max(0, holds.value - 1);
      interact();
    },
  };

  // 切页重新计时，主播放页始终显示，不继承其他页的隐藏状态
  watch(page, interact, { immediate: true });
  const resetPointers = () => {
    pointers.clear();
    touches.clear();
    interact();
  };
  onScopeDispose(() => {
    disposed = true;
    stop();
    pointers.clear();
    touches.clear();
  });
  return { visible, interact, pointerDown, pointerEnd, touchStart, touchEnd, resetPointers, hold };
}

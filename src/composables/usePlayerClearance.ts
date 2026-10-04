import { nextTick, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { useDevice } from "@/composables/useDevice";
import { useMusicStore, useStatusStore } from "@/stores";

// Teleport 控件使用实际视口坐标，自动包含页面缩放、系统安全区和底栏高度。
export const usePlayerClearance = () => {
  const bottom = ref(120);
  const { isPhone, availableHeight, availableWidth } = useDevice();
  const music = useMusicStore();
  const status = useStatusStore();
  let observer: ResizeObserver | undefined;
  let mutation: MutationObserver | undefined;
  const measure = () => {
    if (!isPhone.value) {
      bottom.value = 120;
      return;
    }
    const selector =
      music.isHasPlayer && status.showPlayBar
        ? ".main-player.phone-floating.show"
        : ".mobile-bottom-nav";
    const rect = document.querySelector(selector)?.getBoundingClientRect();
    const height = window.visualViewport?.height || window.innerHeight;
    bottom.value = rect ? Math.max(16, height - rect.top + 16) : 80;
  };
  watch(
    [isPhone, availableHeight, availableWidth, () => music.isHasPlayer, () => status.showPlayBar],
    () => nextTick(measure),
  );
  onMounted(() => {
    observer = new ResizeObserver(measure);
    for (const selector of [".main-player", ".mobile-bottom-nav", "#app"]) {
      const element = document.querySelector(selector);
      if (element) observer.observe(element);
    }
    mutation = new MutationObserver(measure);
    const app = document.getElementById("app");
    if (app) mutation.observe(app, { attributes: true, attributeFilter: ["style"] });
    document.addEventListener("transitionend", measure);
    nextTick(measure);
  });
  onBeforeUnmount(() => {
    observer?.disconnect();
    mutation?.disconnect();
    document.removeEventListener("transitionend", measure);
  });
  return bottom;
};

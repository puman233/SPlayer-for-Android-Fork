import { computed, onMounted, watch } from "vue";
import { useSettingStore } from "@/stores";
import { useDevice } from "@/composables/useDevice";
import { isCapacitorAndroid, isElectron } from "@/utils/env";
import { resolveScaledViewport } from "@/core/layout/viewport";

/** 页面缩放 */
export const usePageZoom = () => {
  const settingStore = useSettingStore();
  const { isPad, isPadDevice, isPhone, isPhonePortrait, availableWidth, availableHeight } =
    useDevice();

  const activeZoom = computed(() => {
    if (isElectron) return 100;
    if (isPad.value) return settingStore.padPageZoom;
    if (isPadDevice.value && isPhonePortrait.value) return settingStore.padPortraitPageZoom;
    // 手机端竖屏与横屏沉浸式共享同一缩放值，避免进入沉浸式后界面突变回 100%
    if (isPhone.value) return settingStore.phonePortraitPageZoom;
    return 100;
  });

  // 全面屏底部留白：所有 Android 形态（手机/平板、横/竖）都需避免被系统手势条遮挡
  const fullscreenSafeBottom = computed(() => {
    if (!isCapacitorAndroid) return 0;
    return settingStore.androidFullscreenSafeAreaOptimize ? 32 : 0;
  });

  const fullscreenSafeTop = computed(() => {
    if (!isCapacitorAndroid) return 0;
    if (settingStore.androidShowStatusBar) return 0;
    return 24;
  });

  // 节流：合并同一帧内的多次调用，避免连发 resize
  let resizePending = false;
  const notifyResize = () => {
    if (resizePending) return;
    resizePending = true;
    const fire = () => window.dispatchEvent(new Event("resize"));
    requestAnimationFrame(() => {
      fire();
      resizePending = false;
    });
    // 一次延迟兜底，处理 CSS 变量在某些场景下延迟生效后的二次布局
    setTimeout(fire, 150);
  };

  const apply = (
    zoom: number,
    safeTop: number,
    safeBottom: number,
    viewportWidth: number,
    viewportHeight: number,
  ) => {
    const { ratio, cssWidth, cssHeight } = resolveScaledViewport(
      viewportWidth,
      viewportHeight,
      zoom,
    );

    // 固定 viewport
    let viewport = document.querySelector('meta[name="viewport"]') as HTMLMetaElement | null;
    if (!viewport) {
      viewport = document.createElement("meta");
      viewport.name = "viewport";
      document.head.appendChild(viewport);
    }
    viewport.setAttribute("content", "width=device-width, initial-scale=1, viewport-fit=cover");

    // Teleport 弹层不在 #app 内，需单独使用未缩放的实际可见视口
    const rootEl = document.documentElement;
    rootEl.style.setProperty("--overlay-viewport-width", `${viewportWidth}px`);
    rootEl.style.setProperty("--overlay-viewport-height", `${viewportHeight}px`);
    rootEl.style.setProperty("--android-fullscreen-safe-top", `${safeTop}px`);
    rootEl.style.setProperty("--android-fullscreen-safe-bottom", `${safeBottom}px`);

    // 缩放变量挂到 #app，避免 teleport 到 body 的弹出层继承到反向补偿值
    const appEl = (document.getElementById("app") || document.documentElement) as HTMLElement;
    appEl.style.setProperty("--page-zoom-ratio", String(ratio));
    appEl.style.setProperty("--page-zoom-width", `${cssWidth}px`);
    appEl.style.setProperty("--page-zoom-height", `${cssHeight}px`);
    appEl.style.setProperty("--page-zoom-100vw", `${cssWidth}px`);
    appEl.style.setProperty("--page-zoom-100vh", `${cssHeight}px`);
    appEl.style.setProperty("--page-zoom-100dvh", `${cssHeight}px`);
    appEl.style.setProperty("--page-zoom-60vw", `${cssWidth * 0.6}px`);
    appEl.style.setProperty("--android-fullscreen-safe-top", `${safeTop / ratio}px`);
    appEl.style.setProperty("--android-fullscreen-safe-bottom", `${safeBottom / ratio}px`);

    // 仅在 ratio !== 1 时设置 transform：scale(1) 也会触发 stacking context 与
    // fixed containing block 切换，影响 Electron / 100% 缩放路径的 fixed 后代定位
    if (ratio === 1) {
      appEl.style.removeProperty("transform");
    } else {
      appEl.style.transform = `scale(${ratio})`;
    }
    (rootEl.style as CSSStyleDeclaration & { zoom?: string }).zoom = "";

    notifyResize();
  };

  onMounted(() =>
    apply(
      activeZoom.value,
      fullscreenSafeTop.value,
      fullscreenSafeBottom.value,
      availableWidth.value,
      availableHeight.value,
    ),
  );
  watch(
    [activeZoom, fullscreenSafeTop, fullscreenSafeBottom, availableWidth, availableHeight],
    ([zoom, safeTop, safeBottom, viewportWidth, viewportHeight]) =>
      apply(zoom, safeTop, safeBottom, viewportWidth, viewportHeight),
  );
};

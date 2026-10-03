export type WindowWidthClass = "compact" | "medium" | "expanded";
export type WindowHeightClass = "compact" | "medium" | "expanded";
export type LayoutPreference = "auto" | "phone" | "pad";

// 宽度等级沿用 Android 窗口尺寸语义，双栏另需足够高度容纳播控
export const ADAPTIVE_BREAKPOINTS = {
  mediumWidth: 600,
  expandedWidth: 840,
  mediumHeight: 480,
  expandedHeight: 900,
} as const;

export const normalizeFontScale = (value: number) =>
  Number.isFinite(value) && value > 0 ? value : 1;

export const resolveAdaptiveLayout = (
  width: number,
  height: number,
  preference: LayoutPreference = "auto",
) => {
  const safeWidth = Number.isFinite(width) ? Math.max(0, width) : 0;
  const safeHeight = Number.isFinite(height) ? Math.max(0, height) : 0;
  const widthClass: WindowWidthClass =
    safeWidth < ADAPTIVE_BREAKPOINTS.mediumWidth
      ? "compact"
      : safeWidth < ADAPTIVE_BREAKPOINTS.expandedWidth
        ? "medium"
        : "expanded";
  const heightClass: WindowHeightClass =
    safeHeight < ADAPTIVE_BREAKPOINTS.mediumHeight
      ? "compact"
      : safeHeight < ADAPTIVE_BREAKPOINTS.expandedHeight
        ? "medium"
        : "expanded";
  // 强制平板保留偏好，但不能突破双栏内容的最低空间预算
  const minWidth =
    preference === "pad" ? ADAPTIVE_BREAKPOINTS.mediumWidth : ADAPTIVE_BREAKPOINTS.expandedWidth;
  return {
    width: safeWidth,
    height: safeHeight,
    widthClass,
    heightClass,
    aspectRatio: safeHeight > 0 ? safeWidth / safeHeight : 0,
    canUseTwoColumns: preference !== "phone" && safeWidth >= minWidth && heightClass !== "compact",
  };
};

/** 语义尺寸有限分级；字号由 WebView 统一执行系统缩放，不重复乘 fontScale。 */
export const resolveAdaptiveTokens = (widthClass: WindowWidthClass) => ({
  spacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
  horizontalPadding: widthClass === "compact" ? 16 : widthClass === "medium" ? 20 : 24,
  control: { minimumTouchTarget: 48, large: 64 },
  icon: { small: 18, medium: 24, large: 32 },
  corner: { small: 8, medium: 12, large: 20 },
  typography: { caption: 12, secondary: 14, body: 16, button: 14, title: 20, playerTitle: 24 },
});

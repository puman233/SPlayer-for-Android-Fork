export type WindowSizeClass = "compact" | "medium" | "expanded";

export interface AdaptiveWindowInfo {
  availableWidth: number;
  availableHeight: number;
  orientation: "portrait" | "landscape";
  widthClass: WindowSizeClass;
  heightClass: WindowSizeClass;
  fontScale: number;
  insets: { top: number; right: number; bottom: number; left: number };
}

/** 分类只依赖当前内容约束，不依赖设备型号或物理分辨率。 */
export function resolveAdaptiveWindow(
  width: number,
  height: number,
  insets: AdaptiveWindowInfo["insets"],
  fontScale = 1,
): AdaptiveWindowInfo {
  const availableWidth = Math.max(0, width - insets.left - insets.right);
  const availableHeight = Math.max(0, height - insets.top - insets.bottom);
  return {
    availableWidth,
    availableHeight,
    orientation: availableWidth > availableHeight ? "landscape" : "portrait",
    widthClass: availableWidth < 600 ? "compact" : availableWidth < 840 ? "medium" : "expanded",
    heightClass: availableHeight < 480 ? "compact" : availableHeight < 900 ? "medium" : "expanded",
    fontScale,
    insets,
  };
}

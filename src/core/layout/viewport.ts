export interface ViewportMeasurement {
  layoutWidth: number;
  layoutHeight: number;
  visualWidth?: number;
  visualHeight?: number;
}

export interface ScaledViewport {
  ratio: number;
  cssWidth: number;
  cssHeight: number;
}

const positiveOrZero = (value: number | undefined) =>
  Number.isFinite(value) && Number(value) > 0 ? Number(value) : 0;

/** 获取当前真正可见的 CSS 视口，键盘和浏览器栏变化时优先采用 visualViewport。 */
export const resolveAvailableViewport = ({
  layoutWidth,
  layoutHeight,
  visualWidth,
  visualHeight,
}: ViewportMeasurement) => {
  const safeLayoutWidth = positiveOrZero(layoutWidth);
  const safeLayoutHeight = positiveOrZero(layoutHeight);
  const safeVisualWidth = positiveOrZero(visualWidth);
  const safeVisualHeight = positiveOrZero(visualHeight);

  return {
    width: safeVisualWidth || safeLayoutWidth,
    height: safeVisualHeight || safeLayoutHeight,
  };
};

/** 将实际可见视口反算为缩放容器尺寸，避免百分比高度和 dvh 在多显示面上产生偏差。 */
export const resolveScaledViewport = (
  viewportWidth: number,
  viewportHeight: number,
  zoom: number,
): ScaledViewport => {
  const safeZoom = Math.max(50, Math.min(200, Number(zoom) || 100));
  const ratio = safeZoom / 100;

  return {
    ratio,
    cssWidth: positiveOrZero(viewportWidth) / ratio,
    cssHeight: positiveOrZero(viewportHeight) / ratio,
  };
};

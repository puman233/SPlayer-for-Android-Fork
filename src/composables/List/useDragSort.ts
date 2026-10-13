import type { ComputedRef, Ref } from "vue";
import { routeLocationKey } from "vue-router";
import type VirtualScroll from "@/components/UI/VirtualScroll.vue";

export interface DragSortOptions {
  /** VirtualScroll 组件实例引用 */
  virtualScrollRef: Ref<InstanceType<typeof VirtualScroll> | null>;
  /** 列表项总数 */
  itemCount: ComputedRef<number>;
  /** 排序完成回调 */
  onReorder: (fromIndex: number, toIndex: number) => void;
  /** 滚动区域顶部内边距 */
  paddingTop?: number;
  /** 触发模式: handle（手柄点击立即触发）| longpress（长按触发） */
  triggerMode?: "handle" | "longpress";
  /** 长按延迟毫秒数，默认 300 */
  longPressDelay?: number;
}

export const useDragSort = (options: DragSortOptions) => {
  const {
    virtualScrollRef,
    itemCount,
    onReorder,
    paddingTop = 0,
    triggerMode = "handle",
    longPressDelay = 300,
  } = options;

  const isDragging = ref(false);
  const draggedIndex = ref(-1);
  const targetIndex = ref(-1);
  const dropIndicator = reactive({ index: -1, position: "none" });

  const dragLabelData = ref<{ name: string } | null>(null);
  const dragLabelPosition = reactive({ top: 0, left: 0 });

  let listRect: DOMRect | null = null;
  let autoScrollRafId: number | null = null;
  let pointerKind: "mouse" | "touch" | null = null;
  let touchIdentifier: number | null = null;
  let generation = 0;
  let disposed = false;
  let active = true;

  // 长按相关
  let longPressTimer: ReturnType<typeof setTimeout> | null = null;
  let pendingIndex = -1;
  let pendingLabelText = "";
  let pointerStartX = 0;
  let pointerStartY = 0;
  const MOVE_THRESHOLD = 5;

  // ---- 核心拖拽逻辑 ----

  const getPointerPos = (e: MouseEvent | TouchEvent) => {
    if ("touches" in e) {
      if (pointerKind !== "touch") return null;
      if (e.touches.length !== 1) {
        cancelDrag();
        return null;
      }
      const touch = Array.from(e.touches).find((touch) => touch.identifier === touchIdentifier);
      if (!touch) {
        cancelDrag();
        return null;
      }
      return { x: touch.clientX, y: touch.clientY };
    }
    return pointerKind === "mouse" ? { x: e.clientX, y: e.clientY } : null;
  };

  const activateDrag = (e: MouseEvent | TouchEvent, index: number, labelText: string) => {
    if (!("touches" in e) && e.cancelable) {
      e.preventDefault();
    }

    isDragging.value = true;
    draggedIndex.value = index;
    targetIndex.value = index;
    dragLabelData.value = { name: labelText };

    const wrapper = virtualScrollRef.value?.wrapperRef;
    if (wrapper) {
      listRect = wrapper.getBoundingClientRect();
    }

    updateDragLabelPosition(e);

    if (pointerKind === "touch") {
      window.addEventListener("touchmove", handleDragMove, { passive: false });
      window.addEventListener("touchend", handleDragEnd);
    } else {
      window.addEventListener("mousemove", handleDragMove);
      window.addEventListener("mouseup", handleDragEnd);
    }
  };

  const handleDragMove = (e: MouseEvent | TouchEvent) => {
    if (!isDragging.value) return;
    const pos = getPointerPos(e);
    if (!pos) return;
    if (e.cancelable) e.preventDefault();

    updateDragLabelPosition(e);

    // 每次移动时刷新 listRect，避免因布局变化导致位置偏差
    const wrapper = virtualScrollRef.value?.wrapperRef;
    if (wrapper) {
      listRect = wrapper.getBoundingClientRect();
    }

    if (!listRect) return;
    const { y: clientY } = pos;

    handleAutoScroll(clientY, listRect);
    calculateTargetIndex(clientY);
  };

  const handleDragEnd = (e: MouseEvent | TouchEvent) => {
    if (pointerKind === "touch") {
      if (!("changedTouches" in e)) return;
      if (!Array.from(e.changedTouches).some((touch) => touch.identifier === touchIdentifier)) return;
    } else if (pointerKind !== "mouse" || "touches" in e) {
      return;
    }
    const from = draggedIndex.value;
    const to = targetIndex.value;
    const commit = isDragging.value && from >= 0 && to >= 0 && from !== to;
    // Release before calling external code: throws or a new drag cannot leave/reuse this session.
    cleanupDragState();
    if (commit) onReorder(from, to);
  };

  const cleanupDragState = () => {
    generation += 1;
    stopAutoScroll();
    cancelLongPress();

    isDragging.value = false;
    draggedIndex.value = -1;
    targetIndex.value = -1;
    dropIndicator.index = -1;
    dropIndicator.position = "none";
    dragLabelData.value = null;
    listRect = null;
    pendingIndex = -1;
    pendingLabelText = "";
    pointerStartX = 0;
    pointerStartY = 0;
    pointerKind = null;
    touchIdentifier = null;
    dragLabelPosition.top = 0;
    dragLabelPosition.left = 0;

    window.removeEventListener("mousemove", handleDragMove);
    window.removeEventListener("touchmove", handleDragMove);
    window.removeEventListener("mouseup", handleDragEnd);
    window.removeEventListener("touchend", handleDragEnd);
    window.removeEventListener("mousemove", handleLongPressMove);
    window.removeEventListener("touchmove", handleLongPressMove);
    window.removeEventListener("mouseup", handleLongPressCancel);
    window.removeEventListener("touchend", handleLongPressCancel);
    window.removeEventListener("touchcancel", cancelDrag);
    window.removeEventListener("touchstart", handleTouchStart);
    window.removeEventListener("blur", cancelDrag);
    document.removeEventListener("visibilitychange", handleVisibilityChange);
  };

  const cancelDrag = () => cleanupDragState();
  const route = inject(routeLocationKey, null);
  if (route) {
    // Cancel on committed navigation, before a cached page's leave transition finishes.
    watch(() => route.fullPath, cancelDrag, { flush: "sync" });
  }
  const handleVisibilityChange = () => {
    if (document.visibilityState === "hidden") cancelDrag();
  };
  const handleTouchStart = (e: TouchEvent) => {
    if (pointerKind === "touch" && e.touches.length !== 1) cancelDrag();
  };

  const calculateTargetIndex = (clientY: number, overrideScrollTop?: number) => {
    if (!listRect || !virtualScrollRef.value) return;

    const currentScrollTop = overrideScrollTop ?? (virtualScrollRef.value.getScrollTop() || 0);
    const relativeY = clientY - listRect.top + currentScrollTop - paddingTop;
    const dropInfo = virtualScrollRef.value.getDropInfoByOffset(relativeY);

    let gapIndex = dropInfo.index;
    if (dropInfo.position === "bottom") {
      gapIndex += 1;
    }

    const maxGap = itemCount.value;
    gapIndex = Math.max(0, Math.min(gapIndex, maxGap));

    if (gapIndex === maxGap) {
      dropIndicator.index = maxGap - 1;
      dropIndicator.position = "bottom";
    } else {
      dropIndicator.index = gapIndex;
      dropIndicator.position = "top";
    }

    let insertIndex = draggedIndex.value;

    if (draggedIndex.value < gapIndex) {
      insertIndex = gapIndex - 1;
    } else if (draggedIndex.value > gapIndex) {
      insertIndex = gapIndex;
    }

    targetIndex.value = Math.max(0, Math.min(insertIndex, maxGap - 1));
  };

  const updateDragLabelPosition = (e: MouseEvent | TouchEvent) => {
    const pos = getPointerPos(e);
    if (!pos) return;
    const { x, y } = pos;
    dragLabelPosition.left = x;
    dragLabelPosition.top = y;
  };

  // ---- 自动滚动 ----

  const handleAutoScroll = (clientY: number, rect: DOMRect) => {
    const edgeThreshold = 40;
    const scrollSpeed = 12;

    if (clientY < rect.top + edgeThreshold) {
      startAutoScroll(-scrollSpeed);
    } else if (clientY > rect.bottom - edgeThreshold) {
      startAutoScroll(scrollSpeed);
    } else {
      stopAutoScroll();
    }
  };

  const startAutoScroll = (amount: number) => {
    if (autoScrollRafId !== null) return;

    let expectedScroll = virtualScrollRef.value?.getScrollTop() || 0;
    const session = generation;

    const scrollStep = () => {
      if (session !== generation || !isDragging.value) return;

      const actualScroll = virtualScrollRef.value?.getScrollTop() || 0;
      if (Math.abs(actualScroll - expectedScroll) > Math.abs(amount) * 3) {
        expectedScroll = actualScroll;
      }

      expectedScroll = Math.max(0, expectedScroll + amount);
      virtualScrollRef.value?.scrollTo(expectedScroll, "auto");

      // 刷新 listRect 并使用实际的滚动位置计算目标索引
      const wrapper = virtualScrollRef.value?.wrapperRef;
      if (wrapper) {
        listRect = wrapper.getBoundingClientRect();
      }
      calculateTargetIndex(dragLabelPosition.top, expectedScroll);

      if (session === generation && isDragging.value) {
        autoScrollRafId = requestAnimationFrame(scrollStep);
      }
    };
    autoScrollRafId = requestAnimationFrame(scrollStep);
  };

  const stopAutoScroll = () => {
    if (autoScrollRafId !== null) {
      cancelAnimationFrame(autoScrollRafId);
      autoScrollRafId = null;
    }
  };

  // ---- 长按逻辑 ----

  const cancelLongPress = () => {
    if (longPressTimer !== null) {
      clearTimeout(longPressTimer);
      longPressTimer = null;
    }
  };

  const handleLongPressMove = (e: MouseEvent | TouchEvent) => {
    const pos = getPointerPos(e);
    if (!pos) return;
    const { x, y } = pos;
    const dx = x - pointerStartX;
    const dy = y - pointerStartY;

    if (Math.sqrt(dx * dx + dy * dy) > MOVE_THRESHOLD) {
      cancelDrag();
    }
  };

  const handleLongPressCancel = (e: MouseEvent | TouchEvent) => handleDragEnd(e);

  // ---- 统一入口 ----

  const handlePointerDown = (e: MouseEvent | TouchEvent, index: number, labelText: string) => {
    if (disposed || !active) return;
    if (pointerKind !== null) {
      // A second pointer cancels rather than replacing the first session.
      cancelDrag();
      return;
    }
    if ("touches" in e) {
      if (e.touches.length !== 1) return;
      pointerKind = "touch";
      touchIdentifier = e.touches[0].identifier;
    } else {
      if (e.button !== 0) return;
      pointerKind = "mouse";
    }
    window.addEventListener("touchcancel", cancelDrag);
    window.addEventListener("touchstart", handleTouchStart);
    window.addEventListener("blur", cancelDrag);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    if (triggerMode === "handle") {
      activateDrag(e, index, labelText);
      return;
    }

    // 长按模式
    const pos = getPointerPos(e);
    if (!pos) return;
    const { x, y } = pos;
    pointerStartX = x;
    pointerStartY = y;
    pendingIndex = index;
    pendingLabelText = labelText;

    cancelLongPress();
    const session = generation;
    longPressTimer = setTimeout(() => {
      if (disposed || session !== generation || pendingIndex < 0) return;
      longPressTimer = null;
      // 震动反馈
      navigator.vibrate?.(50);
      // 构造一个具有正确坐标的伪事件用于 activateDrag
      activateDrag(e, pendingIndex, pendingLabelText);

      // 移除长按阶段的监听，拖拽阶段的监听已在 activateDrag 中绑定
      window.removeEventListener("mousemove", handleLongPressMove);
      window.removeEventListener("touchmove", handleLongPressMove);
      window.removeEventListener("mouseup", handleLongPressCancel);
      window.removeEventListener("touchend", handleLongPressCancel);
    }, longPressDelay);

    if (pointerKind === "touch") {
      window.addEventListener("touchmove", handleLongPressMove, { passive: false });
      window.addEventListener("touchend", handleLongPressCancel);
    } else {
      window.addEventListener("mousemove", handleLongPressMove);
      window.addEventListener("mouseup", handleLongPressCancel);
    }
  };

  onUnmounted(() => {
    disposed = true;
    cancelDrag();
  });

  onDeactivated(() => {
    active = false;
    cancelDrag();
  });
  onActivated(() => {
    if (!disposed) active = true;
  });

  return {
    isDragging,
    draggedIndex,
    dropIndicator,
    dragLabelData,
    dragLabelPosition,
    handlePointerDown,
    cleanupDragState,
    cancelDrag,
  };
};

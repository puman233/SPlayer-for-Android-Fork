import { computed, type MaybeRefOrGetter } from "vue";
import { useElementSize } from "@vueuse/core";
import { useDevice } from "@/composables/useDevice";
import { resolveAdaptiveLayout, resolveAdaptiveTokens } from "@/core/layout/adaptive";

/** 局部组件使用真实容器约束，复用窗口的字号来源。 */
export const useAdaptiveLayout = (target: MaybeRefOrGetter<HTMLElement | null | undefined>) => {
  const { width, height } = useElementSize(target);
  const { fontScale } = useDevice();
  const layout = computed(() => resolveAdaptiveLayout(width.value, height.value));
  const dimensions = computed(() => resolveAdaptiveTokens(layout.value.widthClass));
  return { layout, dimensions, fontScale };
};

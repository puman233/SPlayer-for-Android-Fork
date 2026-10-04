import { inject, onBeforeUnmount, watch, type Ref, type InjectionKey } from "vue";
import type { PlayerMetaHold } from "./usePlayerMetaHold";
export const PHONE_LANDSCAPE_HOLD_KEY: InjectionKey<PlayerMetaHold> = Symbol("PhoneLandscapeHold");

/** 弹层打开期间保持控制层，卸载时释放自己的持有状态。 */
export function usePlayerMetaPopoverHold(show: Ref<boolean>) {
  const hold = inject(PHONE_LANDSCAPE_HOLD_KEY, null);
  let acquired = false;
  watch(
    show,
    (visible) => {
      if (visible && !acquired) {
        acquired = true;
        hold?.acquire();
      } else if (!visible && acquired) {
        acquired = false;
        hold?.release();
      }
    },
    { immediate: true, flush: "sync" },
  );
  onBeforeUnmount(() => {
    if (acquired) hold?.release();
  });
}

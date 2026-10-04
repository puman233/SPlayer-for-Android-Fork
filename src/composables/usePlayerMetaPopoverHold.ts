import { inject, onBeforeUnmount, watch, type Ref } from "vue";
import { PLAYER_META_HOLD_KEY } from "./usePlayerMetaHold";

/** 弹层打开期间保持控制层，卸载时释放自己的持有状态。 */
export function usePlayerMetaPopoverHold(show: Ref<boolean>) {
  const hold = inject(PLAYER_META_HOLD_KEY, null);
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

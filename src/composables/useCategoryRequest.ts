import { onBeforeUnmount, ref } from "vue";

/** 分类页只接受最新请求，异常保留明确的重试状态。 */
export const useCategoryRequest = () => {
  const loading = ref(false);
  const error = ref("");
  let revision = 0;
  onBeforeUnmount(() => {
    revision++;
  });
  const run = async <T>(request: () => Promise<T>, apply: (value: T) => void) => {
    const current = ++revision;
    loading.value = true;
    error.value = "";
    try {
      const result = await request();
      if (current !== revision) return false;
      apply(result);
      return true;
    } catch {
      if (current === revision) error.value = "加载失败，请检查网络或登录状态后重试";
      return false;
    } finally {
      if (current === revision) loading.value = false;
    }
  };
  return { loading, error, run };
};

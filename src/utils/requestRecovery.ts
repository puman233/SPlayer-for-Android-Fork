/** 同一轮未恢复请求只提示一次，其他成功请求不重置故障状态。 */
export function createNetworkFailureNotice() {
  const failures = new Set<string>();
  let shown = false;
  return {
    fail(key: string) {
      failures.add(key);
      if (shown) return false;
      shown = true;
      return true;
    },
    success(key: string) {
      failures.delete(key);
      if (!failures.size) shown = false;
    },
  };
}

/** 健康检查与恢复共享一次执行，失败时限制重载频率。 */
export function createEmbeddedRecovery(
  healthy: () => Promise<boolean>,
  reload: () => Promise<boolean>,
  now = Date.now,
  cooldown = 60000,
) {
  let pending: Promise<boolean> | null = null;
  let lastAttempt = -Infinity;
  return () => {
    if (pending) return pending;
    if (now() - lastAttempt < cooldown) return Promise.resolve(false);
    pending = (async () => {
      try {
        if (await healthy()) return false;
        lastAttempt = now();
        return await reload();
      } catch {
        lastAttempt = now();
        return false;
      }
    })().finally(() => {
      pending = null;
    });
    return pending;
  };
}

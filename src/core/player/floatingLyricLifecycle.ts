export interface FloatingLyricState {
  visible: boolean;
  granted: boolean;
}

/** startService 返回时窗口尚未附着，必须等待实际窗口状态再点亮按钮。 */
export async function ensureFloatingLyricVisible(
  api: {
    getFloatingLyricState(): Promise<FloatingLyricState>;
    showFloatingLyric(): Promise<void>;
  },
  wait: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<boolean> {
  const initial = await api.getFloatingLyricState();
  if (initial.visible) return true;
  if (!initial.granted) return false;
  await api.showFloatingLyric();
  for (let attempt = 0; attempt < 40; attempt++) {
    await wait(50);
    const state = await api.getFloatingLyricState();
    if (state.visible) return true;
    if (!state.granted) return false;
  }
  return false;
}

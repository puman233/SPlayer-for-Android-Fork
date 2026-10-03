package top.imsyy.splayer.android.playback;

/** 悬浮歌词的纯布局与滚动策略 */
final class FloatingLyricPolicy {
  static final int DEFAULT_COLOR = 0xFF6BB2FF;
  static final float MIN_FONT_SP = 16f, MAX_FONT_SP = 32f;
  static final long START_HOLD_MS = 650;

  static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(value, max));
  }

  static float defaultFont(float widthDp, float heightDp, float lyricWidthDp,
      float lyricHeightDp, float fontScale, boolean doubleLine) {
    float shortSide = Math.min(widthDp, heightDp);
    float viewportBase = 18f + (float) Math.sqrt(Math.max(0, shortSide - 240f)) * 0.55f;
    float byWidth = (float) Math.sqrt(Math.max(1, lyricWidthDp)) * 1.4f;
    float byHeight = lyricHeightDp / (doubleLine ? 2.8f : 1.6f);
    return clamp(Math.min(viewportBase, Math.min(byWidth, byHeight))
        / Math.max(1f, fontScale), MIN_FONT_SP, MAX_FONT_SP);
  }

  static float normalized(int position, int origin, int extent, int size) {
    return extent <= size ? 0f : clamp((position - origin) / (float) (extent - size), 0f, 1f);
  }

  static int project(float fraction, int origin, int extent, int size) {
    return origin + Math.round(clamp(fraction, 0f, 1f) * Math.max(0, extent - size));
  }

  static float scroll(float overflowPx, float density, long elapsedMs, long durationMs) {
    if (overflowPx <= 0 || elapsedMs <= START_HOLD_MS) return 0f;
    double naturalMs = overflowPx / Math.max(1f, 36f * density) * 1000;
    double readableMs = overflowPx / Math.max(1f, 90f * density) * 1000;
    double budgetMs = durationMs > 0 ? Math.max(1, durationMs - START_HOLD_MS - 250) : naturalMs;
    double travelMs = Math.max(Math.max(600, readableMs), Math.min(20000, Math.min(naturalMs, budgetMs)));
    return overflowPx * (float) Math.min(1, (elapsedMs - START_HOLD_MS) / travelMs);
  }
}

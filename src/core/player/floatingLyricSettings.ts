import defaults from "../../assets/data/lyricConfig";
import type { LyricConfig } from "../../types/desktop-lyric";
import type { AndroidNativeFloatingLyricConfigPayload } from "../../plugins/androidNativePlayback";

export const FLOATING_LYRIC_CONFIG_KEY = "android-desktop-lyric-config";

/** 旧配置保守保留字号，不能靠默认数值猜测用户意图 */
export function migrateFloatingLyricSettings(saved: Partial<LyricConfig> = {}): LyricConfig {
  const validSize = Number.isFinite(saved.fontSize) && Number(saved.fontSize) > 0;
  return {
    ...defaults,
    ...saved,
    fontSize: validSize ? Math.max(10, Math.min(96, Number(saved.fontSize))) : defaults.fontSize,
    fontSizeMode:
      saved.fontSizeMode === "AUTO_DEFAULT" || saved.fontSizeMode === "USER_DEFINED"
        ? saved.fontSizeMode
        : validSize
          ? "USER_DEFINED"
          : "AUTO_DEFAULT",
    playedColor: saved.playedColor || defaults.playedColor,
    configVersion: 1,
  };
}

export function loadFloatingLyricSettings(): LyricConfig {
  try {
    const raw = localStorage.getItem(FLOATING_LYRIC_CONFIG_KEY);
    const parsed = raw ? JSON.parse(raw) : {};
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("桌面歌词配置格式错误");
    }
    const config = migrateFloatingLyricSettings(parsed);
    if (raw) localStorage.setItem(FLOATING_LYRIC_CONFIG_KEY, JSON.stringify(config));
    return config;
  } catch (error) {
    console.warn("[FloatingLyric] 桌面歌词设置读取失败", error);
    return migrateFloatingLyricSettings();
  }
}

export function saveFloatingLyricSettings(config: LyricConfig) {
  localStorage.setItem(
    FLOATING_LYRIC_CONFIG_KEY,
    JSON.stringify(migrateFloatingLyricSettings(config)),
  );
}

export function floatingLyricPayload(config: LyricConfig): AndroidNativeFloatingLyricConfigPayload {
  const {
    playedColor,
    unplayedColor,
    shadowColor,
    backgroundMaskColor,
    textBackgroundMask,
    showTran,
    showWordLyrics,
    isDoubleLine,
    animation,
    fontSize,
    fontSizeMode,
    fontWeight,
    position,
    windowWidthPercent,
    windowHeightDp,
  } = config;
  return {
    playedColor,
    unplayedColor,
    shadowColor,
    backgroundMaskColor,
    textBackgroundMask,
    showTran,
    showWordLyrics,
    isDoubleLine,
    animation,
    fontSize,
    fontSizeMode,
    fontWeight,
    position,
    windowWidthPercent,
    windowHeightDp,
  };
}

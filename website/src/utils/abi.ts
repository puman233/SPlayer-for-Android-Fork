/**
 * APK 附件与 CPU 架构的匹配规则。
 * Release 附件沿用 Gradle 的最终文件名，例如 app-arm64-v8a-release.apk。
 */

export interface AbiSpec {
  /** 架构标识，与页面表格行的 data-abi 对应 */
  readonly id: string;
  /** 适用设备说明 */
  readonly devices: string;
  readonly recommended: boolean;
}

export const ABI_SPECS: readonly AbiSpec[] = [
  { id: "arm64-v8a", devices: "大多数现代 Android 手机和平板", recommended: true },
  { id: "armeabi-v7a", devices: "较旧的 32 位 ARM 设备", recommended: false },
  { id: "x86_64", devices: "部分 Android 模拟器或特殊设备", recommended: false },
  { id: "x86", devices: "少量旧设备", recommended: false },
];

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * 判断附件名是否对应某个架构。
 * 用边界断言而不是 includes，避免 x86 命中 x86_64（x86_64 里的 `_` 被当作标识符字符）。
 */
export function assetMatchesAbi(assetName: string, abi: string): boolean {
  const pattern = new RegExp(`(?:^|[^0-9A-Za-z_])${escapeRegExp(abi)}(?![0-9A-Za-z_])`, "i");
  return pattern.test(assetName);
}

/** Release 里只接受正式 APK，跳过 debug / unsigned 产物。 */
export function isInstallableApk(assetName: string): boolean {
  const name = assetName.toLowerCase();
  if (!name.endsWith(".apk")) return false;
  if (name.includes("debug")) return false;
  if (name.includes("unsigned")) return false;
  return true;
}

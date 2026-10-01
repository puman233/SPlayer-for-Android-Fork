interface ParsedVersion {
  core: number[];
  prerelease: Array<number | string>;
}

const VERSION_PREFIX_RE = /^[=\s]*v?/i;

/**
 * 解析用于应用更新的 SemVer。允许缺少次/修订号和 `v` 前缀，忽略构建元数据。
 * 非法版本返回 null，避免把仓库中非版本标签误判为更新。
 */
export const parseVersion = (input: string): ParsedVersion | null => {
  const normalized = String(input || "")
    .trim()
    .replace(VERSION_PREFIX_RE, "")
    .split("+", 1)[0];
  const [coreText, prereleaseText = ""] = normalized.split("-", 2);
  const coreParts = coreText.split(".");

  if (
    coreParts.length === 0 ||
    coreParts.length > 3 ||
    coreParts.some((part) => !/^\d+$/.test(part))
  ) {
    return null;
  }

  const core = coreParts.map(Number);
  while (core.length < 3) core.push(0);

  const prerelease = prereleaseText
    ? prereleaseText
        .split(".")
        .map((part) => (/^\d+$/.test(part) ? Number(part) : part.toLowerCase()))
    : [];

  if (prerelease.some((part) => part === "")) return null;
  return { core, prerelease };
};

const comparePrerelease = (left: Array<number | string>, right: Array<number | string>): number => {
  if (left.length === 0 && right.length === 0) return 0;
  if (left.length === 0) return 1;
  if (right.length === 0) return -1;

  const length = Math.max(left.length, right.length);
  for (let index = 0; index < length; index++) {
    const a = left[index];
    const b = right[index];
    if (a === undefined) return -1;
    if (b === undefined) return 1;
    if (a === b) continue;
    if (typeof a === "number" && typeof b === "string") return -1;
    if (typeof a === "string" && typeof b === "number") return 1;
    return a > b ? 1 : -1;
  }
  return 0;
};

/** 比较版本：左侧更新返回 1，相同返回 0，更旧返回 -1；非法版本返回 null。 */
export const compareVersions = (left: string, right: string): number | null => {
  const a = parseVersion(left);
  const b = parseVersion(right);
  if (!a || !b) return null;

  for (let index = 0; index < 3; index++) {
    if (a.core[index] !== b.core[index]) return a.core[index] > b.core[index] ? 1 : -1;
  }
  return comparePrerelease(a.prerelease, b.prerelease);
};

export const isVersionNewer = (candidate: string, current: string): boolean =>
  compareVersions(candidate, current) === 1;

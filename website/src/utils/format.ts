/** 数值与日期的显示格式。 */

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "";

  const mb = bytes / (1024 * 1024);
  if (mb >= 1000) return `${Math.round(mb)} MB`;
  return `${mb.toFixed(1)} MB`;
}

/** 把 ISO 时间转成中文日期；无效输入返回空字符串。 */
export function formatDate(iso: string): string {
  if (!iso) return "";

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";

  try {
    return new Intl.DateTimeFormat("zh-CN", {
      year: "numeric",
      month: "long",
      day: "numeric",
    }).format(date);
  } catch {
    return date.toISOString().slice(0, 10);
  }
}

/** 查找与滚动位置相交的首行，末尾留白或旧偏移仍落在最后一行。 */
export const findVisibleRowIndex = (
  tops: readonly number[],
  heights: readonly number[],
  offset: number,
): number => {
  const length = Math.min(tops.length, heights.length);
  if (!length) return 0;
  const position = Number.isFinite(offset) ? Math.max(0, offset) : 0;
  let low = 0;
  let high = length - 1;
  let first = high;
  while (low <= high) {
    const mid = (low + high) >>> 1;
    if (tops[mid] + heights[mid] > position) {
      first = mid;
      high = mid - 1;
    } else {
      low = mid + 1;
    }
  }
  return first;
};

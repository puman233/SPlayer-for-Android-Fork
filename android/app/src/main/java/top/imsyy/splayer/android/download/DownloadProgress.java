package top.imsyy.splayer.android.download;

/** Native progress emission policy; transferred bytes are never inferred from percentages. */
final class DownloadProgress {
  private int lastBucket = -1;
  private long lastMillis = Long.MIN_VALUE;
  boolean shouldEmit(long read, long total, long nowMillis) {
    int bucket = total > 0 ? (int) (Math.min(1.0, (double) read / total) * 20) : -1;
    if (lastMillis == Long.MIN_VALUE || (total > 0 && bucket != lastBucket)
        || nowMillis - lastMillis >= 250) {
      lastBucket = bucket;
      lastMillis = nowMillis;
      return true;
    }
    return false;
  }
}

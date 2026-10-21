package top.imsyy.splayer.android.playback;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

/** 原生播放错误的有界恢复策略。 */
final class PlaybackRecoveryPolicy {
  static final int MAX_ATTEMPTS = 3;
  static final long STABLE_PLAYBACK_RESET_MS = 10_000L;
  private static final long[] RETRY_DELAYS_MS = {750L, 2_000L, 5_000L};

  private PlaybackRecoveryPolicy() {}

  static long retryDelayMs(int attempt) {
    int index = Math.max(0, Math.min(RETRY_DELAYS_MS.length - 1, attempt - 1));
    return RETRY_DELAYS_MS[index];
  }

  static boolean isRecoverable(int errorCode) {
    return errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        || errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        || errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE;
  }

  static boolean isRetryableHttpStatus(int status) {
    return status == 408 || status == 429 || (status >= 500 && status <= 599);
  }

  static boolean isRecoverable(PlaybackException error) {
    if (!isRecoverable(error.errorCode)) return false;
    if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return true;
    Throwable cause = error.getCause();
    for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
      if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
        return isRetryableHttpStatus(((HttpDataSource.InvalidResponseCodeException) cause).responseCode);
      }
    }
    return false;
  }
}

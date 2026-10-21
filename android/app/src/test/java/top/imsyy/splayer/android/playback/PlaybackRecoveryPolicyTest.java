package top.imsyy.splayer.android.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import org.junit.Test;

public class PlaybackRecoveryPolicyTest {
  @Test public void httpRecoveryDistinguishesTemporaryFailuresFromPermissionsAndMissingMedia() {
    for (int status : new int[] {408, 429, 500, 502, 503, 504}) {
      assertTrue(PlaybackRecoveryPolicy.isRetryableHttpStatus(status));
    }
    for (int status : new int[] {400, 401, 403, 404, 410, 416, 200}) {
      assertFalse(PlaybackRecoveryPolicy.isRetryableHttpStatus(status));
    }
    assertFalse(PlaybackRecoveryPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE));
    assertFalse(PlaybackRecoveryPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND));
  }
  @Test
  public void retryDelayUsesBoundedBackoff() {
    assertEquals(750L, PlaybackRecoveryPolicy.retryDelayMs(1));
    assertEquals(2_000L, PlaybackRecoveryPolicy.retryDelayMs(2));
    assertEquals(5_000L, PlaybackRecoveryPolicy.retryDelayMs(3));
    assertEquals(5_000L, PlaybackRecoveryPolicy.retryDelayMs(99));
  }

  @Test
  public void networkAndHttpFailuresAreRecoverable() {
    assertTrue(
        PlaybackRecoveryPolicy.isRecoverable(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
    assertTrue(
        PlaybackRecoveryPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS));
    assertTrue(
        PlaybackRecoveryPolicy.isRecoverable(
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE));
  }

  @Test
  public void permissionAndDecoderFailuresAreNotRetried() {
    assertFalse(
        PlaybackRecoveryPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_NO_PERMISSION));
    assertFalse(
        PlaybackRecoveryPolicy.isRecoverable(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED));
  }
}

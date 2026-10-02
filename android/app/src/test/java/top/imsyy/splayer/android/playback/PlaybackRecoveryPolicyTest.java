package top.imsyy.splayer.android.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import org.junit.Test;

public class PlaybackRecoveryPolicyTest {
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

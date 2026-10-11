package top.imsyy.splayer.android.download;

import org.junit.Test;
import static org.junit.Assert.*;

public class DownloadProgressTest {
  @Test public void unknownLengthStillEmitsTransferredBytesWithThrottle() {
    DownloadProgress progress = new DownloadProgress();
    assertTrue(progress.shouldEmit(8192, -1, 0));
    assertFalse(progress.shouldEmit(16384, -1, 10));
    assertTrue(progress.shouldEmit(24576, -1, 250));
  }
  @Test public void firstSmallProgressIsNotLostToIntegerDivision() {
    assertTrue(new DownloadProgress().shouldEmit(1, 100, 0));
  }
}

package top.imsyy.splayer.android.download;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DownloadSidecarTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void existingLyricsSurviveFailedReplacement() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public OutputStream write(String path) throws IOException { throw new IOException("fixture write denied"); }
    };
    byte[] original = "[00:01.000]用户歌词".getBytes(StandardCharsets.UTF_8);
    File lyric = new File(storage.directory, "song.lrc");
    Files.write(lyric.toPath(), original);
    try {
      DownloadTransfer.writeText("fixture", "replacement".getBytes(StandardCharsets.UTF_8),
          "song.lrc", storage, new DownloadTransfer.Control());
      fail("conflict or write failure must be reported");
    } catch (IOException expected) {}
    assertArrayEquals(original, Files.readAllBytes(lyric.toPath()));
  }

  @Test public void successfulLyricsAreVerifiedAndLeaveNoTemporaryDocuments() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder());
    byte[] content = "[00:01.000]合成歌词".getBytes(StandardCharsets.UTF_8);
    DownloadTransfer.Result result = DownloadTransfer.writeText("fixture", content, "song.lrc", storage, new DownloadTransfer.Control());
    assertEquals("success", result.status);
    assertArrayEquals(content, Files.readAllBytes(new File(result.path).toPath()));
    assertArrayEquals(new String[]{"song.lrc"}, storage.directory.list());
  }

  @Test public void identicalLyricsAreReusedWithoutOpeningAnOutputStream() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public OutputStream write(String path) throws IOException { throw new IOException("never rewrite existing"); }
    };
    byte[] content = "[00:01.000]same".getBytes(StandardCharsets.UTF_8);
    Files.write(new File(storage.directory, "song.lrc").toPath(), content);
    assertEquals("success", DownloadTransfer.writeText("fixture", content, "song.lrc", storage, new DownloadTransfer.Control()).status);
    assertArrayEquals(content, Files.readAllBytes(new File(storage.find("song.lrc")).toPath()));
  }

  @Test public void stagingFailureNeverCreatesPublicLyrics() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder());
    storage.failWrite = true;
    try {
      DownloadTransfer.writeText("fixture", new byte[]{1,2}, "song.lrc", storage, new DownloadTransfer.Control());
      fail("write denied");
    } catch (IOException expected) {}
    assertNull(storage.find("song.lrc"));
    assertEquals(0, storage.directory.list().length);
  }

  @Test public void cancellationDuringStagingDoesNotPublishLyrics() throws Exception {
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public OutputStream write(String path) throws IOException {
        OutputStream output = super.write(path);
        if (!path.endsWith(".part")) return output;
        return new FilterOutputStream(output) {
          public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length); control.requestCancel();
          }
        };
      }
    };
    try {
      DownloadTransfer.writeText("fixture", new byte[20000], "song.lrc", storage, control);
      fail("cancelled");
    } catch (IOException expected) {}
    assertNull(storage.find("song.lrc"));
  }

  @Test public void failedFinalCopyRetainsRecoveryMarkerWithoutDeletingPublicContent() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public OutputStream write(String path) throws IOException {
        if (path.endsWith(".lrc")) throw new IOException("final write denied");
        return super.write(path);
      }
    };
    try {
      DownloadTransfer.writeText("fixture", new byte[]{1,2}, "song.lrc", storage, new DownloadTransfer.Control());
      fail("write denied");
    } catch (IOException expected) {}
    assertNotNull(storage.find(DownloadTransfer.pendingName("song.lrc")));
    assertEquals(storage.find("song.lrc"), DownloadTransfer.readScanMarker(storage, storage.find(DownloadTransfer.pendingName("song.lrc"))));
  }
}

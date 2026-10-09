package top.imsyy.splayer.android.download;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.*;

public class DownloadTransferTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  static class Filesystem implements DownloadTransfer.Storage {
    final File directory;
    boolean failWrite;
    boolean failFinalWrite;
    boolean failFinalDelete;
    boolean failClose;
    boolean failDelete;
    boolean corruptWrite;
    Filesystem(File directory) { this.directory = directory; }
    public String find(String name) { File f = new File(directory, name); return f.exists() ? f.getAbsolutePath() : null; }
    public String create(String name) throws IOException {
      File f = new File(directory, name);
      if (!f.createNewFile()) throw new IOException("already exists");
      return f.getAbsolutePath();
    }
    public InputStream read(String path) throws IOException { return new FileInputStream(path); }
    public OutputStream write(String path) throws IOException {
      if (failWrite && path.endsWith(".part")) throw new IOException("fixture write failure");
      if (failFinalWrite && path.endsWith("song.mp3")) throw new IOException("fixture commit write failure");
      OutputStream output = new FileOutputStream(path);
      if (path.endsWith(".part") && (failClose || corruptWrite)) return new FilterOutputStream(output) {
        public void write(byte[] buffer, int offset, int count) throws IOException {
          if (corruptWrite) super.write(new byte[count], 0, count);
          else out.write(buffer, offset, count);
        }
        public void close() throws IOException { super.close(); if (failClose) throw new IOException("fixture close failed"); }
      };
      return output;
    }
    public boolean delete(String path) { return !failDelete && !(failFinalDelete && path.endsWith("song.mp3")) && new File(path).delete(); }
    public String name(String path) { File f = new File(path); return f.exists() ? f.getName() : null; }
  }

  // Raw local HTTP permits a deliberately short fixed-length body (HTTP server APIs can repair it).
  static class Server implements AutoCloseable {
    final ServerSocket socket = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    Server(String headers, byte[] body) throws IOException {
      executor.submit(() -> {
        try (Socket client = socket.accept()) {
          client.setSoTimeout(2000);
          BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
          while (!reader.readLine().isEmpty()) {}
          OutputStream output = client.getOutputStream();
          output.write(("HTTP/1.1 200 OK\r\n" + headers + "Connection: close\r\n\r\n").getBytes("US-ASCII"));
          output.write(body);
          output.flush();
        } catch (IOException ignored) {}
      });
    }
    String url() { return "http://127.0.0.1:" + socket.getLocalPort() + "/fixture"; }
    public void close() throws IOException { socket.close(); executor.shutdownNow(); }
  }

  @Test public void shortResponseNeverPublishesOrSkipsPartialSong() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    try (Server server = new Server("Content-Length: 100\r\n", new byte[25])) {
      try {
        DownloadTransfer.download("fixture", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("short body must fail, never return success");
      } catch (IOException expected) {}
      assertNull("final audio must not expose a fragment", storage.find("song.mp3"));
    }
  }

  @Test public void cancelledTransferStopsWritingAndRemovesOwnedFragment() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    long[] written = {0};
    try (Server server = new Server("Content-Length: 20000\r\n", new byte[20000])) {
      DownloadTransfer.Result result = DownloadTransfer.download("cancel", server.url(), "song.mp3", storage,
          control, (read, total) -> { written[0] = read; control.cancel(); });
      assertEquals("cancelled", result.status);
      assertTrue("no further writes after cancelling first chunk", written[0] < 20000);
      assertNull(storage.find("song.mp3"));
      assertEquals(0, storage.directory.list().length);
    }
  }

  @Test public void existingPartialUserFileIsNeverSkippedOverwrittenOrDeleted() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    byte[] original = {1, 2, 3};
    Files.write(new File(storage.directory, "song.mp3").toPath(), original);
    try (Server server = new Server("Content-Length: 10\r\n", new byte[10])) {
      try {
        DownloadTransfer.download("retry", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("unverified existing fragment must be reported as a conflict");
      } catch (IOException expected) {}
      assertArrayEquals(original, Files.readAllBytes(new File(storage.directory, "song.mp3").toPath()));
    }
  }

  @Test public void providerWithoutRenameCommitsOnlyVerifiedClosedFile() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    byte[] content = {1, 2, 3, 4};
    try (Server server = new Server("Content-Length: 4\r\n", content)) {
      DownloadTransfer.Result result = DownloadTransfer.download("fallback", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {});
      assertEquals("success", result.status);
      assertArrayEquals(content, Files.readAllBytes(new File(result.path).toPath()));
    }
  }

  @Test public void normalDownloadPublishesVerifiedBytesAndCleansTemporaryFiles() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    byte[] content = {8, 7, 6, 5};
    try (Server server = new Server("Content-Length: 4\r\n", content)) {
      DownloadTransfer.Result result = DownloadTransfer.download("normal", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> assertEquals(4, total));
      assertEquals("success", result.status);
      assertArrayEquals(content, Files.readAllBytes(new File(result.path).toPath()));
      assertArrayEquals(new String[]{"song.mp3"}, storage.directory.list());
    }
  }

  @Test public void unknownLengthChunkedResponseRequiresValidTerminalChunk() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    try (Server server = new Server("Transfer-Encoding: chunked\r\n", "4\r\nABCD\r\n0\r\n\r\n".getBytes("US-ASCII"))) {
      DownloadTransfer.Result result = DownloadTransfer.download("chunked", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> assertEquals(-1, total));
      assertArrayEquals("ABCD".getBytes("US-ASCII"), Files.readAllBytes(new File(result.path).toPath()));
    }
  }

  @Test public void interruptedChunkedResponseNeverPublishesFinalAudio() throws Exception {
    assertRejected("Transfer-Encoding: chunked\r\n", "4\r\nABCD\r\n".getBytes("US-ASCII"), new Filesystem(temporary.newFolder()));
  }

  @Test public void connectionCloseWithoutLengthCannotProveCompleteness() throws Exception {
    assertRejected("", new byte[]{1, 2, 3}, new Filesystem(temporary.newFolder()));
  }

  @Test public void outputWriteFailureIsCleanedAndRetrySucceeds() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    storage.failWrite = true;
    assertRejected("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4}, storage);
    storage.failWrite = false;
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      assertEquals("success", DownloadTransfer.download("retry-ok", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {}).status);
    }
  }

  @Test public void outputCloseFailureCannotBecomeSuccess() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    storage.failClose = true;
    assertRejected("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4}, storage);
  }

  @Test public void providerCorruptionIsDetectedByReadingBackStagedContent() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    storage.corruptWrite = true;
    assertRejected("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4}, storage);
  }

  @Test public void matchingExistingSongIsVerifiedThenSkippedAndPreserved() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    byte[] content = {1, 2, 3, 4};
    File existing = new File(storage.directory, "song.mp3");
    Files.write(existing.toPath(), content);
    long modified = existing.lastModified();
    try (Server server = new Server("Content-Length: 4\r\n", content)) {
      assertEquals("skipped", DownloadTransfer.download("same", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {}).status);
      assertEquals(modified, existing.lastModified());
      assertArrayEquals(content, Files.readAllBytes(existing.toPath()));
      assertArrayEquals(new String[]{"song.mp3"}, storage.directory.list());
    }
  }

  @Test public void preCancelledQueuedTransferDoesNotOpenNetworkOrCreateFiles() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    control.cancel();
    assertEquals("cancelled", DownloadTransfer.download("queued", "http://127.0.0.1:1/unused", "song.mp3", storage,
        control, (read, total) -> fail("must not receive data")).status);
    assertEquals(0, storage.directory.list().length);
  }

  @Test public void cancellationAfterPublicationIsIdempotentAndPreservesCompletedFile() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      DownloadTransfer.Result result = DownloadTransfer.download("complete", server.url(), "song.mp3", storage,
          control, (read, total) -> {});
      control.cancel();
      control.cancel();
      assertEquals("success", result.status);
      assertFalse(control.cancelled);
      assertNotNull(storage.find("song.mp3"));
    }
  }

  @Test public void interruptedOwnedFallbackCommitCanBeRecoveredWithoutTouchingOtherSongs() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    File owned = new File(storage.directory, "song.mp3");
    Files.write(owned.toPath(), new byte[]{1, 2, 3, 4});
    Files.write(new File(storage.directory, DownloadTransfer.pendingName("song.mp3")).toPath(),
        owned.getAbsolutePath().getBytes("UTF-8"));
    Files.write(new File(storage.directory, "other.mp3").toPath(), new byte[]{9});
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      assertEquals("skipped", DownloadTransfer.download("recovered", server.url(), "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {}).status);
      assertArrayEquals(new byte[]{9}, Files.readAllBytes(new File(storage.directory, "other.mp3").toPath()));
    }
  }

  @Test public void pendingMarkerCannotAuthorizeDeletingUnrelatedExistingFile() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    File user = new File(storage.directory, "song.mp3");
    Files.write(user.toPath(), new byte[]{9});
    Files.write(new File(storage.directory, DownloadTransfer.pendingName("song.mp3")).toPath(), "other-uri".getBytes("UTF-8"));
    try {
      DownloadTransfer.download("conflict", "http://127.0.0.1:1/unused", "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {});
      fail("conflict must be reported");
    } catch (IOException expected) {}
    assertArrayEquals(new byte[]{9}, Files.readAllBytes(user.toPath()));
  }

  @Test public void failedCommitCleanupRetainsUnfinishedMarkerAndRetryRecoversOwnedFile() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    storage.failFinalWrite = true;
    storage.failFinalDelete = true;
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      try {
        DownloadTransfer.download("commit-fail", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("must fail incomplete commit");
      } catch (IOException expected) {}
      assertNotNull("unremovable final fragment must remain explicitly unfinished", storage.find(DownloadTransfer.pendingName("song.mp3")));
    }
    storage.failFinalWrite = false;
    storage.failFinalDelete = false;
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      try {
        DownloadTransfer.download("commit-retry", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("historical public fragment cannot grant deletion authority");
      } catch (IOException expected) {}
      assertEquals(0, new File(storage.find("song.mp3")).length());
      assertNotNull(storage.find(DownloadTransfer.pendingName("song.mp3")));
    }
  }

  @Test public void cancellationImmediatelyBeforeCommitCannotPublishSuccess() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      assertEquals("cancelled", DownloadTransfer.download("commit-cancel", server.url(), "song.mp3", storage,
          control, (read, total) -> { if (read == total) control.cancel(); }).status);
      assertEquals(0, storage.directory.list().length);
    }
  }

  @Test public void cancellationOfOneConcurrentTransferCannotCancelAnother() throws Exception {
    Filesystem first = new Filesystem(temporary.newFolder());
    Filesystem second = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control cancelFirst = new DownloadTransfer.Control();
    CountDownLatch bothReceiving = new CountDownLatch(2);
    ExecutorService transfers = Executors.newFixedThreadPool(2);
    try (Server a = new Server("Content-Length: 20000\r\n", new byte[20000]);
         Server b = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      Future<DownloadTransfer.Result> firstResult = transfers.submit(() -> DownloadTransfer.download(
          "concurrent-a", a.url(), "song.mp3", first, cancelFirst, (read, total) -> {
            bothReceiving.countDown();
            awaitBoth(bothReceiving);
            cancelFirst.cancel();
          }));
      Future<DownloadTransfer.Result> secondResult = transfers.submit(() -> DownloadTransfer.download(
          "concurrent-b", b.url(), "song.mp3", second, new DownloadTransfer.Control(), (read, total) -> {
            bothReceiving.countDown(); awaitBoth(bothReceiving);
          }));
      assertEquals("cancelled", firstResult.get(5, TimeUnit.SECONDS).status);
      assertEquals("success", secondResult.get(5, TimeUnit.SECONDS).status);
      assertNull(first.find("song.mp3"));
      assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(new File(second.find("song.mp3")).toPath()));
    } finally { transfers.shutdownNow(); }
  }

  private static void awaitBoth(CountDownLatch latch) {
    try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
  }

  @Test public void cancellationAcknowledgementDoesNotWaitForBlockedProviderWrite() throws Exception {
    CountDownLatch writing = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Filesystem storage = new Filesystem(temporary.newFolder()) {
      public OutputStream write(String path) throws IOException {
        OutputStream output = super.write(path);
        if (!path.endsWith(".part")) return output;
        return new FilterOutputStream(output) {
          public void write(byte[] buffer, int offset, int count) throws IOException {
            writing.countDown();
            awaitBoth(release);
            out.write(buffer, offset, count);
          }
        };
      }
    };
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      Future<DownloadTransfer.Result> transfer = threads.submit(() -> DownloadTransfer.download(
          "blocked-provider", server.url(), "song.mp3", storage, control, (read, total) -> {}));
      assertTrue(writing.await(5, TimeUnit.SECONDS));
      Future<?> cancellation = threads.submit(control::requestCancel);
      try { cancellation.get(1, TimeUnit.SECONDS); }
      finally { release.countDown(); }
      assertEquals("cancelled", transfer.get(5, TimeUnit.SECONDS).status);
      assertEquals(0, storage.directory.list().length);
    } finally { release.countDown(); threads.shutdownNow(); }
  }

  @Test public void publicationWinnerDoesNotBlockCancellationOnProviderMarkerDelete() throws Exception {
    CountDownLatch deleting = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Filesystem storage = new Filesystem(temporary.newFolder()) {
      public boolean delete(String path) {
        if (path.endsWith(".pending")) { deleting.countDown(); awaitBoth(release); }
        return super.delete(path);
      }
    };
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try (Server server = new Server("Content-Length: 4\r\n", new byte[]{1, 2, 3, 4})) {
      Future<DownloadTransfer.Result> transfer = threads.submit(() -> DownloadTransfer.download(
          "publishing", server.url(), "song.mp3", storage, control, (read, total) -> {}));
      assertTrue(deleting.await(5, TimeUnit.SECONDS));
      Future<?> cancellation = threads.submit(control::requestCancel);
      try { assertNull(cancellation.get(1, TimeUnit.SECONDS)); }
      finally { release.countDown(); }
      assertEquals("success", transfer.get(5, TimeUnit.SECONDS).status);
      assertFalse(control.cancelled);
    } finally { release.countDown(); threads.shutdownNow(); }
  }

  @Test public void cancellationClosesActualHttpRequestAndStopsWriting() throws Exception {
    Filesystem storage = new Filesystem(temporary.newFolder());
    DownloadTransfer.Control control = new DownloadTransfer.Control();
    CountDownLatch receiving = new CountDownLatch(1);
    CountDownLatch cancelAccepted = new CountDownLatch(1);
    CountDownLatch disconnected = new CountDownLatch(1);
    ExecutorService workers = Executors.newFixedThreadPool(3);
    try (ServerSocket listener = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
      workers.submit(() -> {
        try (Socket client = listener.accept()) {
          client.setSoTimeout(8000);
          BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
          while (!reader.readLine().isEmpty()) {}
          client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\n".getBytes("US-ASCII"));
          client.getOutputStream().write(new byte[8192]);
          client.getOutputStream().flush();
          // Release an outstanding read after cancellation is accepted; those bytes must never be written.
          if (!cancelAccepted.await(5, TimeUnit.SECONDS)) return;
          client.getOutputStream().write(new byte[8192]);
          client.getOutputStream().flush();
          if (client.getInputStream().read() == -1) disconnected.countDown();
        } catch (Exception ignored) {}
      });
      Future<DownloadTransfer.Result> transfer = workers.submit(() -> DownloadTransfer.download(
          "blocked", "http://127.0.0.1:" + listener.getLocalPort() + "/fixture", "song.mp3", storage,
          control, (read, total) -> receiving.countDown()));
      assertTrue(receiving.await(5, TimeUnit.SECONDS));
      control.requestCancel();
      Future<?> cancellation = workers.submit(control::cancel);
      cancelAccepted.countDown();
      assertEquals("cancelled", transfer.get(8, TimeUnit.SECONDS).status);
      cancellation.get(8, TimeUnit.SECONDS);
      assertTrue("server observes request socket close", disconnected.await(8, TimeUnit.SECONDS));
      assertEquals(0, storage.directory.list().length);
    } finally { workers.shutdownNow(); }
  }

  private void assertRejected(String headers, byte[] body, Filesystem storage) throws Exception {
    try (Server server = new Server(headers, body)) {
      try {
        DownloadTransfer.download("failure", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("must reject incomplete transfer");
      } catch (IOException expected) {}
      assertNull(storage.find("song.mp3"));
      assertEquals(0, storage.directory.list().length);
    }
  }
}

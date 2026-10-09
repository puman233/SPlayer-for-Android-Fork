package top.imsyy.splayer.android.download;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Transfer boundary shared by SAF and isolated JVM HTTP/file regression tests. */
final class DownloadTransfer {
  interface Storage {
    String find(String name) throws IOException;
    String create(String name) throws IOException;
    InputStream read(String path) throws IOException;
    OutputStream write(String path) throws IOException;
    boolean delete(String path) throws IOException;
    String name(String path) throws IOException;
  }
  interface Progress { void update(long read, long total); }
  interface Created { void register(String path) throws IOException; }
  static final class Result {
    final String status;
    final String path;
    Result(String status, String path) { this.status = status; this.path = path; }
  }
  static final class Control {
    final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);
    volatile boolean cancelled;
    volatile boolean completed;
    volatile HttpURLConnection connection;
    synchronized HttpURLConnection requestCancel() {
      if (completed) return null;
      cancelled = true;
      return connection;
    }
    void cancel() {
      HttpURLConnection current = requestCancel();
      if (current != null) current.disconnect();
    }
    void check() throws IOException {
      if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("DOWNLOAD_CANCELLED");
    }
    void write(OutputStream output, byte[] buffer, int count) throws IOException {
      check();
      output.write(buffer, 0, count);
    }
  }

  static String pendingName(String name) {
    return ".splayer-" + hex(digest().digest(name.getBytes(StandardCharsets.UTF_8))) + ".pending";
  }

  static Result download(String taskId, String url, String name, Storage storage,
      Control control, Progress progress) throws IOException {
    // Register creation before any provider metadata IO can fail.
    String[] stagedRef = {null}, finalRef = {null}, pendingRef = {null};
    boolean finalAttempted = false;
    boolean recoveryUnfinished = false;
    HttpURLConnection connection = null;
    boolean published = false;
    try {
      control.check();
      String recovery = recoverPending(name, storage, control);
      recoveryUnfinished = recovery != null;
      String existing = storage.find(name);
      String pending = recovery != null ? recovery : createExact(storage, pendingName(name), path -> pendingRef[0] = path);
      // Journal every created document, including providers that rename a staging file.
      if (recovery == null) writeMarker(storage, pending, "CREATING");
      String staged = createExact(storage, ".splayer-" + taskId + ".part", path -> {
        stagedRef[0] = path;
        if (recovery == null) writeMarker(storage, pending, path);
      });
      connection = (HttpURLConnection) new URL(url).openConnection();
      control.connection = connection;
      connection.setConnectTimeout(30000);
      // Preserve slow-network tolerance; cancellation also disconnects the active connection.
      connection.setReadTimeout(60000);
      connection.setRequestProperty("User-Agent", "SPlayer-for-Android");
      connection.setRequestProperty("Accept-Encoding", "identity");
      control.check();
      int code = connection.getResponseCode();
      control.check();
      // No range was requested; a partial response cannot prove the full song is present.
      if (code != 200) throw new IOException("HTTP_ERROR_" + code);
      long expected = connection.getContentLengthLong();
      String transferEncoding = connection.getHeaderField("Transfer-Encoding");
      String contentEncoding = connection.getHeaderField("Content-Encoding");
      if (contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity"))
        throw new IOException("DOWNLOAD_UNSUPPORTED_CONTENT_ENCODING");
      if (expected < 0 && !"chunked".equalsIgnoreCase(transferEncoding))
        throw new IOException("DOWNLOAD_LENGTH_UNKNOWN");
      MessageDigest receivedHash = digest();
      long total = 0;
      try (InputStream input = connection.getInputStream(); OutputStream output = storage.write(staged)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          control.write(output, buffer, count);
          receivedHash.update(buffer, 0, count);
          total += count;
          control.check();
          progress.update(total, expected);
        }
        control.check();
        output.flush();
      }
      control.check();
      if (total == 0 || (expected >= 0 && total != expected)) throw new IOException("DOWNLOAD_LENGTH_MISMATCH");
      String hash = hex(receivedHash.digest());
      verify(storage, staged, total, hash, control);
      if (existing != null) {
        verify(storage, existing, total, hash, control);
        synchronized (control) {
          control.check();
          control.completed = true;
        }
        // Content equality authorizes completion, never deletion/overwrite of the audio.
        if (recovery != null && !storage.delete(recovery)) throw new IOException("DOWNLOAD_MARKER_CLEANUP_FAILED");
        return new Result("skipped", existing);
      }
      // Persist unfinished state before any document can acquire its final audio name.
      // An indeterminate creation (crash or failed journal write) quarantines this directory.
      writeMarker(storage, pending, "CREATING");
      control.check();
      // SAF rename has neither an atomicity nor a no-replace guarantee. Use a marked,
      // verified copy for every provider instead of risking overwriting a user document.
      if (storage.find(name) != null) throw new IOException("DOWNLOAD_FILE_CONFLICT");
      finalAttempted = true;
      String ownedFinal = createExact(storage, name, path -> {
        finalRef[0] = path;
        writeMarker(storage, pending, path);
      });
      try (InputStream input = storage.read(staged); OutputStream output = storage.write(ownedFinal)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) control.write(output, buffer, count);
        control.check();
        output.flush();
      }
      verify(storage, ownedFinal, total, hash, control);
      // Reserve publication after all audio IO is verified. No provider IO holds this lock:
      // a cancellation request must never block on a slow file write or marker deletion.
      synchronized (control) {
        control.check();
        control.completed = true;
      }
      if (!storage.delete(pending)) throw new IOException("DOWNLOAD_COMMIT_MARKER_FAILED");
      pendingRef[0] = null;
      published = true;
      return new Result("success", ownedFinal);
    } catch (IOException error) {
      if (control.cancelled) return new Result("cancelled", null);
      throw error;
    } catch (RuntimeException error) {
      if (control.cancelled) return new Result("cancelled", null);
      throw new IOException("DOWNLOAD_PROVIDER_FAILED", error);
    } finally {
      control.connection = null;
      if (connection != null) connection.disconnect();
      IOException cleanup = null;
      boolean stagedRemoved = stagedRef[0] == null;
      // Public documents may be externally changed even during this attempt. No SAF
      // URI, timestamp or hash supplies exclusive ownership: preserve and quarantine.
      if (stagedRef[0] != null) {
        try {
          // A provider-renamed staging document may now have an audio name. Preserve it.
          stagedRemoved = (".splayer-" + taskId + ".part").equals(storage.name(stagedRef[0]))
              && storage.delete(stagedRef[0]);
          if (!stagedRemoved) cleanup = new IOException("DOWNLOAD_TEMP_CLEANUP_FAILED");
        }
        catch (IOException | RuntimeException error) { cleanup = new IOException("DOWNLOAD_TEMP_CLEANUP_FAILED", error); }
      }
      if (pendingRef[0] != null && (published || (!finalAttempted && stagedRemoved && !recoveryUnfinished))) {
        try { if (!storage.delete(pendingRef[0])) cleanup = new IOException("DOWNLOAD_MARKER_CLEANUP_FAILED"); }
        catch (IOException | RuntimeException error) { cleanup = new IOException("DOWNLOAD_MARKER_CLEANUP_FAILED", error); }
      }
      if (cleanup != null) throw cleanup;
    }
  }

  private static String createExact(Storage storage, String name, Created created) throws IOException {
    if (storage.find(name) != null) throw new IOException("DOWNLOAD_FILE_CONFLICT");
    String path = storage.create(name);
    if (path == null) throw new IOException("FAILED_TO_CREATE_FILE");
    created.register(path);
    if (!name.equals(storage.name(path))) {
      throw new IOException("DOWNLOAD_PROVIDER_CHANGED_NAME");
    }
    return path;
  }

  private static String recoverPending(String name, Storage storage, Control control) throws IOException {
    String marker = storage.find(pendingName(name));
    if (marker == null) return null;
    control.check();
    String recorded = readMarker(storage, marker);
    control.check();
    String existing = storage.find(name);
    if (existing != null) {
      if (!existing.equals(recorded)) throw new IOException("DOWNLOAD_UNVERIFIED_FILE_CONFLICT");
      // Keep the document and marker until a fresh complete response verifies its bytes.
      return marker;
    }
    // A renamed or ambiguous document is not evidence that it has been deleted.
    String recordedName = storage.name(recorded);
    if (recordedName != null && !recordedName.endsWith(".part"))
      throw new IOException("DOWNLOAD_UNVERIFIED_FILE_CONFLICT");
    if (recordedName == null) {
      // Null metadata also means query failure on SAF, not necessarily deletion.
      try (InputStream ignored = storage.read(recorded)) {
        throw new IOException("DOWNLOAD_UNVERIFIED_FILE_CONFLICT");
      } catch (java.io.FileNotFoundException absent) {
        // No audio is removed. A later no-replace creation must still succeed.
      }
    }
    control.check();
    if (!storage.delete(marker)) throw new IOException("DOWNLOAD_MARKER_CLEANUP_FAILED");
    return null;
  }

  static String readMarker(Storage storage, String marker) throws IOException {
    return readMarker(storage, marker, false);
  }

  static String readScanMarker(Storage storage, String marker) throws IOException {
    // Legacy URI-only markers cannot prove an uninterrupted journal write. Quarantine
    // conservatively; recovery may still verify their exact target without deleting it.
    return readMarker(storage, marker, true);
  }

  private static String readMarker(Storage storage, String marker, boolean requireFrame) throws IOException {
    try (InputStream input = storage.read(marker); ByteArrayOutputStream content = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (content.size() + count > 16384) throw new IOException("DOWNLOAD_INVALID_MARKER");
        content.write(buffer, 0, count);
      }
      String recorded = decodeUtf8(content.toByteArray());
      if (recorded.startsWith("SPLAYER-PENDING-1\n")) {
        String[] frame = recorded.split("\n", -1);
        if (frame.length != 4 || !frame[3].isEmpty()) throw new IOException("DOWNLOAD_INVALID_MARKER");
        byte[] payload;
        try { payload = java.util.Base64.getDecoder().decode(frame[1]); }
        catch (IllegalArgumentException error) { throw new IOException("DOWNLOAD_INVALID_MARKER", error); }
        if (!hex(digest().digest(payload)).equals(frame[2])) throw new IOException("DOWNLOAD_INVALID_MARKER");
        recorded = decodeUtf8(payload);
      } else if (requireFrame) throw new IOException("DOWNLOAD_INVALID_MARKER");
      if (recorded.isEmpty() || recorded.contains("\n") || recorded.contains("\u0000") || recorded.equals("CREATING"))
        throw new IOException("DOWNLOAD_INVALID_MARKER");
      if (!recorded.startsWith("content://") && !recorded.startsWith("/") && !recorded.matches("[A-Za-z]:[\\\\/].*"))
        throw new IOException("DOWNLOAD_INVALID_MARKER");
      return recorded;
    }
  }

  private static void writeMarker(Storage storage, String marker, String owned) throws IOException {
    byte[] payload = owned.getBytes(StandardCharsets.UTF_8);
    String frame = "SPLAYER-PENDING-1\n" + java.util.Base64.getEncoder().encodeToString(payload)
        + "\n" + hex(digest().digest(payload)) + "\n";
    try (OutputStream output = storage.write(marker)) {
      output.write(frame.getBytes(StandardCharsets.UTF_8));
      output.flush();
    }
  }

  private static String decodeUtf8(byte[] bytes) throws IOException {
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
  }

  private static void verify(Storage storage, String path, long size, String hash, Control control) throws IOException {
    MessageDigest actual = digest();
    long count = 0;
    try (InputStream input = storage.read(path)) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = input.read(buffer)) != -1) {
        control.check();
        actual.update(buffer, 0, n);
        count += n;
      }
    }
    control.check();
    if (count != size || !hex(actual.digest()).equals(hash)) throw new IOException("DOWNLOAD_FILE_INTEGRITY_CONFLICT");
  }

  private static MessageDigest digest() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
    return result.toString();
  }
}

package top.imsyy.splayer.android.update;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/** 网络和文件工作仅由更新插件的后台线程调用。 */
final class ApkDownload {
  interface Observer {
    void connection(HttpURLConnection connection);
    void progress(long bytes, long total);
  }

  interface Validator { void validate(File apk) throws Exception; }
  interface FailureObserver { void failed(Exception error); }

  static long fetchSources(List<String> sources, File partial, String sha256, long expectedSize,
      AtomicBoolean cancelled, Observer observer, Validator validator, FailureObserver failure)
      throws Exception {
    Exception lastError = null;
    for (String source : sources) {
      checkCancelled(cancelled);
      try {
        if (partial.exists() && !partial.delete()) throw new IOException("Cannot replace partial APK");
        long bytes = fetch(source, partial, sha256, expectedSize, cancelled, observer);
        validator.validate(partial);
        checkCancelled(cancelled);
        return bytes;
      } catch (Exception error) {
        if (partial.exists() && !partial.delete()) throw new IOException("Cannot remove partial APK", error);
        checkCancelled(cancelled);
        lastError = error;
        failure.failed(error);
      }
    }
    throw lastError != null ? lastError : new IOException("No update sources");
  }

  static long fetch(String url, File partial, String sha256, long expectedSize,
      AtomicBoolean cancelled, Observer observer) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    observer.connection(connection);
    try {
      connection.setConnectTimeout(15_000);
      connection.setReadTimeout(60_000);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestProperty("Accept", "application/octet-stream");
      connection.setRequestProperty("Accept-Encoding", "identity");
      connection.setRequestProperty("User-Agent", "SPlayer-Android-Updater");
      checkCancelled(cancelled);
      int status = connection.getResponseCode();
      // 不使用断点续传；206 不能证明得到完整 APK。
      if (status != 200) throw new IOException("HTTP " + status);
      String type = connection.getContentType();
      if (type != null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
        throw new IOException("Server returned an HTML page");
      }
      long length = connection.getContentLengthLong();
      if (expectedSize > 0 && length > 0 && length != expectedSize) {
        throw new IOException("APK response length differs from Release asset");
      }
      long total = expectedSize > 0 ? expectedSize : length;
      long bytes = 0;
      long lastProgress = 0;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long startedAt = System.nanoTime();
      byte[] buffer = new byte[64 * 1024];
      observer.progress(0, total);
      try (InputStream input = new BufferedInputStream(connection.getInputStream());
          FileOutputStream output = new FileOutputStream(partial, false)) {
        int count;
        while ((count = input.read(buffer)) != -1) {
          checkCancelled(cancelled);
          if (System.nanoTime() - startedAt > 20L * 60 * 1_000_000_000L) {
            throw new IOException("APK download exceeded twenty minutes");
          }
          bytes += count;
          if (total > 0 && bytes > total) throw new IOException("APK exceeds expected size");
          output.write(buffer, 0, count);
          digest.update(buffer, 0, count);
          long now = System.nanoTime();
          if (now - lastProgress >= 250_000_000L) {
            observer.progress(bytes, total);
            lastProgress = now;
          }
        }
        output.getFD().sync();
      }
      checkCancelled(cancelled);
      if (bytes == 0 || (length > 0 && bytes != length) || (total > 0 && bytes != total)) {
        throw new IOException("APK download is incomplete");
      }
      StringBuilder actual = new StringBuilder(64);
      for (byte value : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", value));
      if (!actual.toString().equals(sha256)) throw new SecurityException("SHA-256 mismatch");
      try (ZipFile apk = new ZipFile(partial)) {
        if (apk.getEntry("AndroidManifest.xml") == null) throw new IOException("Invalid APK archive");
      }
      observer.progress(bytes, total);
      return bytes;
    } finally {
      connection.disconnect();
      observer.connection(null);
    }
  }

  private static void checkCancelled(AtomicBoolean cancelled) throws IOException {
    if (cancelled.get() || Thread.currentThread().isInterrupted()) {
      throw new IOException("Download cancelled");
    }
  }
}

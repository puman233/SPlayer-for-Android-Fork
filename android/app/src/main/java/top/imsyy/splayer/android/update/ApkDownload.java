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
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/** 网络和文件工作仅由更新插件的后台线程调用。 */
final class ApkDownload {
  /** 网页同样使用 GET Range 探测，而非仅测试域名或 HEAD 成功。 */
  static List<String> rankSources(List<String> sources, long size, AtomicBoolean cancelled)
      throws Exception {
    checkCancelled(cancelled);
    if (sources.size() < 2) return sources;
    int count = sources.size() - 1; // 官方地址保留为最终回退，不参加公共代理选线。
    ExecutorService pool = Executors.newFixedThreadPool(count);
    Map<HttpURLConnection, Boolean> connections = new ConcurrentHashMap<>();
    List<Future<Long>> probes = new ArrayList<>();
    List<Long> latencies = new ArrayList<>();
    long deadline = System.nanoTime() + 3_500_000_000L;
    try {
      for (int i = 0; i < count; i++) {
        String source = sources.get(i);
        probes.add(pool.submit(() -> probe(source, size, cancelled, connections)));
      }
      for (Future<Long> probe : probes) {
        long latency = Long.MAX_VALUE;
        while (System.nanoTime() < deadline) {
          checkCancelled(cancelled);
          try { latency = probe.get(50, TimeUnit.MILLISECONDS); break; }
          catch (TimeoutException waiting) { /* 保持取消响应，整体探测期限为 3.5 秒。 */ }
          catch (ExecutionException failed) { break; }
        }
        if (latency == Long.MAX_VALUE && probe.isDone()) {
          try { latency = probe.get(); }
          catch (ExecutionException failed) { /* 保留该线路作为探测暂时失败时的下载回退。 */ }
        }
        latencies.add(latency);
      }
      checkCancelled(cancelled);
      List<Integer> order = new ArrayList<>();
      for (int i = 0; i < count; i++) order.add(i);
      order.sort((a, b) -> Long.compare(latencies.get(a), latencies.get(b)));
      List<String> ranked = new ArrayList<>();
      for (int index : order) ranked.add(sources.get(index));
      ranked.add(sources.get(count));
      return ranked;
    } finally {
      for (Future<Long> probe : probes) probe.cancel(true);
      pool.shutdownNow();
      for (HttpURLConnection connection : connections.keySet()) connection.disconnect();
    }
  }

  private static long probe(String source, long size, AtomicBoolean cancelled,
      Map<HttpURLConnection, Boolean> connections) throws Exception {
    long start = System.nanoTime();
    HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
    connections.put(connection, true);
    try {
      connection.setConnectTimeout(3500);
      connection.setReadTimeout(3500);
      connection.setRequestProperty("Range", "bytes=0-0");
      connection.setRequestProperty("Accept-Encoding", "identity");
      connection.setRequestProperty("Cache-Control", "no-cache");
      connection.setRequestProperty("User-Agent", "SPlayer-Android-Updater");
      checkCancelled(cancelled);
      int status = connection.getResponseCode();
      String type = connection.getContentType();
      if (type != null && type.toLowerCase(Locale.ROOT).contains("text/html")) {
        throw new IOException("Proxy probe returned HTML");
      }
      try (InputStream input = connection.getInputStream()) {
        if (status == 206) {
          String range = connection.getHeaderField("Content-Range");
          if (range == null || !range.matches("bytes 0-0/[0-9]+")
              || (size > 0 && !range.equals("bytes 0-0/" + size)) || input.read() != 'P') {
            throw new IOException("Invalid APK range probe");
          }
        } else if (status == 200) {
          long length = connection.getContentLengthLong();
          if ((size > 0 && length > 0 && length != size)
              || input.read() != 'P' || input.read() != 'K'
              || input.read() != 3 || input.read() != 4) {
            throw new IOException("Invalid APK probe");
          }
        } else {
          throw new IOException("Proxy probe HTTP " + status);
        }
        // 不支持 Range 的服务器可能发送整包；样本读完立即断开，避免探测下载整包。
        connection.disconnect();
      }
      checkCancelled(cancelled);
      return System.nanoTime() - start;
    } finally {
      connection.disconnect();
      connections.remove(connection);
    }
  }
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
      connection.setConnectTimeout(10_000);
      connection.setReadTimeout(15_000);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestProperty("Accept", "application/octet-stream");
      connection.setRequestProperty("Accept-Encoding", "identity");
      connection.setRequestProperty("User-Agent", "SPlayer-Android-Updater");
      connection.setRequestProperty("Cache-Control", "no-cache");
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

package top.imsyy.splayer.android.update;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 用本机 HTTP 服务制造失败，执行真实 Android 网络和文件下载。 */
@RunWith(AndroidJUnit4.class)
public class ApkDownloadTest {
  @Test public void realAcceleratedDownloadWithoutSystemProxy() throws Exception {
    android.os.Bundle args = InstrumentationRegistry.getArguments();
    org.junit.Assume.assumeTrue(args.containsKey("apkUrl"));
    assertTrue(context.getPackageName().endsWith(".phase1verify"));
    String proxy = android.provider.Settings.Global.getString(context.getContentResolver(), "http_proxy");
    assertTrue("测试不得使用系统代理", proxy == null || proxy.isEmpty() || ":0".equals(proxy));
    File file = new File(context.getCacheDir(), "real-update-test.part");
    AtomicInteger progressEvents = new AtomicInteger();
    java.util.List<String> attempted = new java.util.ArrayList<>();
    long size = Long.parseLong(args.getString("apkSize"));
    try {
      long bytes = ApkDownload.fetchSources(UpdateDownloadSources.resolve(args.getString("apkUrl"), args.getString("apkSha")),
          file, args.getString("apkSha"), size, cancelled, new ApkDownload.Observer() {
            public void connection(HttpURLConnection connection) { if (connection != null) attempted.add(connection.getURL().toString()); }
            public void progress(long bytes, long total) { if (bytes > 0) progressEvents.incrementAndGet(); }
          }, apk -> {}, error -> android.util.Log.w("UpdateNetworkTest", error.toString()));
      assertEquals(size, bytes);
      assertTrue("必须产生真实下载进度", progressEvents.get() > 0);
      android.util.Log.i("UpdateNetworkTest", "verified bytes=" + bytes + " progress=" + progressEvents.get());
      org.json.JSONObject evidence = new org.json.JSONObject().put("bytes", bytes).put("sha256", args.getString("apkSha")).put("progressEvents", progressEvents.get()).put("systemProxy", proxy).put("attempted", new org.json.JSONArray(attempted));
      java.nio.file.Files.write(new File(context.getExternalFilesDir(null), "network-hotfix.json").toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
    } finally { file.delete(); }
  }
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final ApkDownload.Observer observer = new ApkDownload.Observer() {
    public void connection(HttpURLConnection connection) {}
    public void progress(long bytes, long total) {}
  };

  private byte[] fixture() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
      zip.write("test-only manifest".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }

  private String digest(byte[] bytes) throws Exception {
    StringBuilder result = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      result.append(String.format(Locale.ROOT, "%02x", value));
    }
    return result.toString();
  }

  private final class Server implements AutoCloseable {
    final ServerSocket socket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
    final AtomicInteger requests = new AtomicInteger();
    final Thread thread;
    Server(int status, byte[] payload, String type, int length) throws IOException {
      thread = new Thread(() -> {
        while (!socket.isClosed()) {
          try (Socket client = socket.accept()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {}
            requests.incrementAndGet();
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " Test\r\nContent-Type: " + type
                + "\r\nContent-Length: " + length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            output.write(payload);
            output.flush();
          } catch (IOException ignored) {}
        }
      });
      thread.start();
    }
    String url() { return "http://127.0.0.1:" + socket.getLocalPort() + "/app.apk"; }
    public void close() throws Exception { socket.close(); thread.join(2000); }
  }

  @Test public void proxyFailureFallsBackAndValidates() throws Exception {
    assertTrue(context.getPackageName().endsWith(".phase1verify"));
    byte[] apk = fixture();
    File partial = new File(context.getCacheDir(), "transport-test.part");
    for (int status : new int[]{403, 500, 206, 200}) {
      byte[] bad = "<html>proxy error</html>".getBytes(StandardCharsets.UTF_8);
      try (Server proxy = new Server(status, bad, status == 200 ? "text/html" : "application/octet-stream", bad.length);
          Server origin = new Server(200, apk, "application/octet-stream", apk.length)) {
        AtomicInteger failures = new AtomicInteger();
        long bytes = ApkDownload.fetchSources(Arrays.asList(proxy.url(), origin.url()), partial,
            digest(apk), apk.length, cancelled, observer, file -> {}, error -> failures.incrementAndGet());
        assertEquals(apk.length, bytes);
        assertEquals(1, proxy.requests.get());
        assertEquals(1, origin.requests.get());
        assertEquals(1, failures.get());
        assertArrayEquals(apk, java.nio.file.Files.readAllBytes(partial.toPath()));
      } finally { partial.delete(); }
    }
  }

  @Test public void checksumTruncationAndCancellationNeverProduceInstallFile() throws Exception {
    byte[] apk = fixture();
    File partial = new File(context.getCacheDir(), "transport-test.part");
    for (boolean truncated : new boolean[]{false, true}) {
      byte[] payload = truncated ? Arrays.copyOf(apk, apk.length / 2) : apk;
      try (Server server = new Server(200, payload, "application/octet-stream", apk.length)) {
        try {
          ApkDownload.fetchSources(Collections.singletonList(server.url()), partial,
              truncated ? digest(apk) : "0".repeat(64), apk.length,
              cancelled, observer, file -> {}, error -> {});
          fail("损坏下载必须拒绝");
        } catch (Exception expected) {
          assertFalse(partial.exists());
          assertEquals(1, server.requests.get());
          assertFalse(expected instanceof ConnectException);
        }
      }
    }
    cancelled.set(true);
    try (Server server = new Server(200, apk, "application/octet-stream", apk.length)) {
      try {
        ApkDownload.fetchSources(Collections.singletonList(server.url()), partial, digest(apk),
            apk.length, cancelled, observer, file -> {}, error -> {});
        fail("取消后不能下载");
      } catch (Exception expected) { assertEquals(0, server.requests.get()); }
    }
  }

  @Test public void sourceUrlsAndPackageValidation() throws Exception {
    String url = "https://github.com/puman233/SPlayer-for-Android-Fork/releases/download/v3.0.12/app-arm64-v8a-release.apk";
    assertEquals(Arrays.asList("https://gh-proxy.com/" + url, "https://gh.llkk.cc/" + url, "https://ghfast.top/" + url, url), UpdateDownloadSources.resolve(url));
    for (String invalid : new String[]{"https://api.github.com/releases", "http://github.com/a/b/releases/download/v1/app.apk", "https://github.com.evil/a/b/releases/download/v1/app.apk"}) {
      try { UpdateDownloadSources.resolve(invalid); fail("必须拒绝非官方 APK 地址"); }
      catch (IllegalArgumentException expected) {}
    }
    AndroidAppUpdatePlugin plugin = new AndroidAppUpdatePlugin() {
      @Override public Context getContext() { return context; }
    };
    java.lang.reflect.Method validate = AndroidAppUpdatePlugin.class.getDeclaredMethod("validateInstallableApk", File.class);
    validate.setAccessible(true);
    try {
      validate.invoke(plugin, new File(context.getApplicationInfo().sourceDir));
      fail("相同版本必须拒绝安装");
    } catch (java.lang.reflect.InvocationTargetException expected) {
      assertTrue(expected.getCause().getMessage().contains("not newer"));
    }
  }

  @Test public void tamperingFallbackAndMidTransferCancellation() throws Exception {
    byte[] apk = fixture(), tampered = apk.clone();
    tampered[0] ^= 1;
    File partial = new File(context.getCacheDir(), "transport-test.part");
    try (Server proxy = new Server(200, tampered, "application/octet-stream", apk.length);
        Server origin = new Server(200, apk, "application/octet-stream", apk.length)) {
      assertEquals(apk.length, ApkDownload.fetchSources(Arrays.asList(proxy.url(), origin.url()),
          partial, digest(apk), apk.length, cancelled, observer, file -> {}, error -> {}));
      assertEquals(1, origin.requests.get());
    } finally { partial.delete(); }
    try (Server proxy = new Server(200, apk, "application/octet-stream", apk.length);
        Server origin = new Server(200, apk, "application/octet-stream", apk.length)) {
      ApkDownload.Observer cancelDuringRead = new ApkDownload.Observer() {
        public void connection(HttpURLConnection connection) {}
        public void progress(long bytes, long total) { if (bytes > 0) cancelled.set(true); }
      };
      try {
        ApkDownload.fetchSources(Arrays.asList(proxy.url(), origin.url()), partial, digest(apk),
            apk.length, cancelled, cancelDuringRead, file -> {}, error -> {});
        fail("取消后不得完成下载或回退");
      } catch (IOException expected) {
        assertFalse(partial.exists());
        assertEquals(1, proxy.requests.get());
        assertEquals(0, origin.requests.get());
      }
    }
  }
}

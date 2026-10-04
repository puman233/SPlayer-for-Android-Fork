package top.imsyy.splayer.android.update;

import static org.junit.Assert.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 显式提供公开 Release 参数后，双端执行实际下载与速度测量。 */
@RunWith(AndroidJUnit4.class)
public class LiveReleaseDownloadTest {
  @Test public void realReleaseProxyAndOrigin() throws Exception {
    Bundle args = InstrumentationRegistry.getArguments();
    String original = args.getString("releaseUrl");
    if (original == null) return;
    android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertTrue(context.getPackageName().endsWith(".phase1verify"));
    long size = Long.parseLong(args.getString("releaseSize"));
    String sha = args.getString("releaseSha");
    String mode = args.getString("source", "proxy");
    String url = UpdateDownloadSources.resolve(original).get(mode.equals("proxy") ? 0 : 1);
    File partial = new File(context.getCacheDir(), "live-release.part");
    AtomicInteger progress = new AtomicInteger();
    long start = SystemClock.elapsedRealtime();
    try {
      long bytes = ApkDownload.fetchSources(Collections.singletonList(url), partial, sha, size,
          new AtomicBoolean(), new ApkDownload.Observer() {
            public void connection(HttpURLConnection connection) {}
            public void progress(long downloaded, long total) {
              assertEquals(size, total);
              assertTrue(downloaded >= 0 && downloaded <= size);
              progress.incrementAndGet();
            }
          }, file -> {
            PackageInfo info = context.getPackageManager().getPackageArchiveInfo(file.getPath(),
                PackageManager.GET_SIGNING_CERTIFICATES);
            assertNotNull(info);
            assertEquals("top.imsyy.splayer.android", info.packageName);
            assertEquals(args.getString("releaseVersion"), info.versionName);
            assertEquals(Long.parseLong(args.getString("releaseCode")), info.getLongVersionCode());
            assertNotNull(info.signingInfo);
            assertTrue(info.signingInfo.getApkContentsSigners().length > 0);
          }, error -> Log.e("LiveReleaseDownload", mode + " failed", error));
      assertEquals(size, bytes);
      assertTrue(progress.get() >= 2);
      long elapsed = SystemClock.elapsedRealtime() - start;
      Log.i("LiveReleaseDownload", "RESULT source=" + mode + " bytes=" + bytes
          + " elapsedMs=" + elapsed + " bytesPerSecond=" + bytes * 1000 / Math.max(1, elapsed)
          + " progressEvents=" + progress.get() + " sha256=" + sha);
    } finally {
      assertTrue(!partial.exists() || partial.delete());
    }
  }
}

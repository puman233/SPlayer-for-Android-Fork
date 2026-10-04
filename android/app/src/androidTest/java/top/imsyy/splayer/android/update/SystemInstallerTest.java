package top.imsyy.splayer.android.update;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 使用单独构建的较新隔离签名包检查未知来源权限和系统安装器。 */
@RunWith(AndroidJUnit4.class)
public class SystemInstallerTest {
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    String[] result = new String[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
        web.evaluateJavascript(expression, value -> { result[0]=value; latch.countDown(); }));
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  @Test public void signedNewerPackageOpensPermissionAndInstallerWithoutRedownload() throws Exception {
    android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    String pkg = inst.getTargetContext().getPackageName();
    assertTrue(pkg.endsWith(".phase1verify"));
    File fixture = new File(inst.getTargetContext().getExternalFilesDir(null), "installer-fixture.apk");
    assertTrue("需提供版本更高、签名相同的隔离测试 APK", fixture.isFile());
    File directory = new File(inst.getTargetContext().getCacheDir(), "updates");
    assertTrue(directory.exists() || directory.mkdirs());
    File apk = new File(directory, "installer-fixture.apk");
    java.nio.file.Files.copy(fixture.toPath(), apk.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    long size = apk.length();
    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
    try(FileInputStream input = new FileInputStream(apk)) {
      byte[] buffer = new byte[65536]; int count;
      while((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
    }
    StringBuilder checksum = new StringBuilder();
    for(byte value:digest.digest()) checksum.append(String.format(java.util.Locale.ROOT,"%02x",value));
    inst.getTargetContext().getSharedPreferences("android-app-update",0).edit().putString("fileName","installer-fixture.apk").putString("sha256",checksum.toString()).commit();
    MainActivity activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> web = activity.getBridge().getWebView());
    for(int i=0;i<80&&!"true".equals(js("!!window.Capacitor?.nativePromise"));i++) SystemClock.sleep(200);
    try {
      js("window.cached=null;window.Capacitor.nativePromise('AndroidAppUpdate','getDownloadedApk',{fileName:'installer-fixture.apk',sha256:'"+checksum+"'}).then(x=>window.cached=x).catch(e=>window.cached={error:e.message})");
      for(int i=0;i<60&&"null".equals(js("window.cached"));i++) SystemClock.sleep(200);
      assertEquals("跨弹窗恢复文件必须重新通过摘要和包兼容性校验", "true", js("window.cached?.available===true"));
      assertFalse("外部运行器应先禁止测试包的安装权限", inst.getTargetContext().getPackageManager().canRequestPackageInstalls());
      js("window.installResult=null;window.Capacitor.nativePromise('AndroidAppUpdate','installApk',{fileName:'installer-fixture.apk'}).then(x=>window.installResult=x).catch(e=>window.installResult={error:e.message})");
      for(int i=0;i<60&&"null".equals(js("window.installResult"));i++) SystemClock.sleep(200);
      assertEquals("应先引导安装授权", "true", js("window.installResult?.needsPermission===true"));
      assertTrue(apk.isFile());
      assertEquals(size, apk.length());
      inst.getUiAutomation().executeShellCommand("appops set "+pkg+" REQUEST_INSTALL_PACKAGES allow").close();
      inst.getUiAutomation().executeShellCommand("am start -n "+pkg+"/top.imsyy.splayer.android.MainActivity").close();
      SystemClock.sleep(500);
      js("window.installResult=null;window.Capacitor.nativePromise('AndroidAppUpdate','installApk',{fileName:'installer-fixture.apk'}).then(x=>window.installResult=x).catch(e=>window.installResult={error:e.message})");
      for(int i=0;i<60&&"null".equals(js("window.installResult"));i++) SystemClock.sleep(200);
      assertEquals("授权返回后同一 APK 可以交给系统安装器", "true", js("window.installResult?.needsPermission===false"));
      SystemClock.sleep(500);
      try (InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("dumpsys activity activities"))) {
        String activities = new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("实际系统安装器应启动", activities.lines().anyMatch(line ->
            (line.contains("ResumedActivity") || line.contains("topResumedActivity")) && line.toLowerCase().contains("packageinstaller")));
      }
      assertEquals(size, apk.length());
    } finally {
      inst.getTargetContext().getSharedPreferences("android-app-update",0).edit().clear().commit();
      apk.delete();
      fixture.delete();
    }
  }
}

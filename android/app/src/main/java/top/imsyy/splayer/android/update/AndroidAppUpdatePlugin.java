package top.imsyy.splayer.android.update;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "AndroidAppUpdate")
public class AndroidAppUpdatePlugin extends Plugin {

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
  private final AtomicBoolean downloadInProgress = new AtomicBoolean(false);
  private volatile HttpURLConnection activeConnection;

  @Override
  protected void handleOnDestroy() {
    cancelRequested.set(true);
    disconnectActiveConnection();
    executor.shutdownNow();
  }

  @PluginMethod
  public void getSupportedAbis(PluginCall call) {
    JSArray abis = new JSArray();
    for (String abi : Build.SUPPORTED_ABIS) abis.put(abi);
    JSObject result = new JSObject();
    result.put("abis", abis);
    call.resolve(result);
  }

  @PluginMethod
  public void canInstallApk(PluginCall call) {
    JSObject result = new JSObject();
    result.put("allowed", getContext().getPackageManager().canRequestPackageInstalls());
    call.resolve(result);
  }

  @PluginMethod
  public void getDownloadedApk(PluginCall call) {
    String name = sanitizeFileName(call.getString("fileName", ""));
    String checksum = normalizeSha256(call.getString("sha256", ""));
    if (downloadInProgress.get()) {
      call.resolve(new JSObject().put("available", false));
      return;
    }
    try {
      executor.execute(() -> {
        boolean available = false;
        File apk = new File(new File(getContext().getCacheDir(), "updates"), name);
        android.content.SharedPreferences saved = getContext().getSharedPreferences("android-app-update", 0);
        if (!checksum.isEmpty() && name.endsWith(".apk") && apk.isFile()
            && name.equals(saved.getString("fileName", ""))
            && checksum.equals(saved.getString("sha256", ""))) {
          try {
            // 权限页面可能导致进程重建；恢复时重新检查文件及安装兼容性。
            validateInstallableApk(apk);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(apk)) {
              byte[] buffer = new byte[64 * 1024];
              int count;
              while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            available = checksum.equals(toHex(digest.digest()));
            if (!available) deleteQuietly(apk);
          } catch (Exception error) {
            deleteQuietly(apk);
          }
        }
        JSObject result = new JSObject();
        result.put("available", available);
        result.put("bytesRead", available ? apk.length() : 0);
        call.resolve(result);
      });
    } catch (RuntimeException error) {
      call.reject("Update downloader is unavailable", "UPDATE_DOWNLOAD_FAILED", error);
    }
  }

  @PluginMethod
  public void cancelDownload(PluginCall call) {
    cancelRequested.set(true);
    disconnectActiveConnection();
    call.resolve();
  }

  @PluginMethod
  public void downloadApk(PluginCall call) {
    String url = call.getString("url", "");
    String fileName = sanitizeFileName(call.getString("fileName", "update.apk"));
    String expectedSha256 = normalizeSha256(call.getString("sha256", ""));
    if (url.isEmpty() || !fileName.endsWith(".apk")) {
      call.reject("A valid APK url and fileName are required", "UPDATE_INVALID_ARGUMENT");
      return;
    }
    if (expectedSha256.isEmpty()) {
      call.reject("A valid SHA-256 digest is required", "UPDATE_INVALID_CHECKSUM");
      return;
    }
    try {
      UpdateDownloadSources.resolve(url);
    } catch (IllegalArgumentException error) {
      call.reject("Invalid GitHub Release APK URL", "UPDATE_INVALID_ARGUMENT", error);
      return;
    }
    if (!downloadInProgress.compareAndSet(false, true)) {
      call.reject("Another update download is already running", "UPDATE_DOWNLOAD_BUSY");
      return;
    }

    cancelRequested.set(false);
    try {
      executor.execute(() -> runDownload(call, url, fileName, expectedSha256,
          Math.max(0L, call.getLong("size", 0L))));
    } catch (RuntimeException error) {
      downloadInProgress.set(false);
      call.reject("Update downloader is unavailable", "UPDATE_DOWNLOAD_FAILED", error);
    }
  }

  private void runDownload(
      PluginCall call, String sourceUrl, String fileName, String expectedSha256, long expectedSize) {
    File updateDir = new File(getContext().getCacheDir(), "updates");
    File partial = new File(updateDir, fileName + ".part");
    File target = new File(updateDir, fileName);
    try {
      if (!updateDir.exists() && !updateDir.mkdirs()) {
        throw new IllegalStateException("Cannot create update cache directory");
      }
      if (target.exists() && !target.delete()) throw new IllegalStateException("Cannot replace update");
      long bytesRead = ApkDownload.fetchSources(ApkDownload.rankSources(
          UpdateDownloadSources.resolve(sourceUrl, expectedSha256), expectedSize, cancelRequested), partial,
          expectedSha256, expectedSize, cancelRequested, new ApkDownload.Observer() {
            public void connection(HttpURLConnection connection) { activeConnection = connection; }
            public void progress(long bytes, long total) { notifyProgress(bytes, total); }
          }, this::validateInstallableApk,
          error -> Log.d("SPlayerUpdater", "APK source failed: " + error.getClass().getSimpleName()
              + ": " + error.getMessage()));
      if (cancelRequested.get()) throw new DownloadCancelledException();
      if (!partial.renameTo(target)) throw new IllegalStateException("Cannot finalize downloaded update");
      getContext().getSharedPreferences("android-app-update", 0).edit()
          .putString("fileName", fileName).putString("sha256", expectedSha256).apply();
      JSObject result = new JSObject();
      result.put("fileName", fileName);
      result.put("bytesRead", bytesRead);
      result.put("sha256", expectedSha256);
      call.resolve(result);
    } catch (Exception error) {
      deleteQuietly(partial);
      deleteQuietly(target);
      String code = cancelRequested.get() || error instanceof DownloadCancelledException
          ? "UPDATE_DOWNLOAD_CANCELLED"
          : error instanceof ApkValidationException ? ((ApkValidationException) error).code
          : error instanceof SecurityException ? "UPDATE_CHECKSUM_MISMATCH" : "UPDATE_DOWNLOAD_FAILED";
      call.reject(error.getMessage(), code, error);
    } finally {
      disconnectActiveConnection();
      activeConnection = null;
      cancelRequested.set(false);
      downloadInProgress.set(false);
    }
  }

  @PluginMethod
  public void installApk(PluginCall call) {
    String fileName = sanitizeFileName(call.getString("fileName", ""));
    File apk = new File(new File(getContext().getCacheDir(), "updates"), fileName);
    if (!fileName.endsWith(".apk") || !apk.isFile()) {
      call.reject("Downloaded APK was not found", "UPDATE_APK_NOT_FOUND");
      return;
    }

    try {
      validateInstallableApk(apk);
    } catch (ApkValidationException error) {
      deleteQuietly(apk);
      call.reject(error.getMessage(), error.code, error);
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && !getContext().getPackageManager().canRequestPackageInstalls()) {
      Intent permissionIntent =
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:" + getContext().getPackageName()));
      permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(permissionIntent);
      JSObject result = new JSObject();
      result.put("needsPermission", true);
      call.resolve(result);
      return;
    }

    Uri apkUri =
        FileProvider.getUriForFile(
            getContext(), getContext().getPackageName() + ".fileprovider", apk);
    Intent installIntent = new Intent(Intent.ACTION_VIEW);
    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      getContext().startActivity(installIntent);
    } catch (ActivityNotFoundException | SecurityException error) {
      call.reject("Android system installer is unavailable", "UPDATE_INSTALLER_UNAVAILABLE", error);
      return;
    }

    JSObject result = new JSObject();
    result.put("needsPermission", false);
    call.resolve(result);
  }

  private void validateInstallableApk(File apk) throws ApkValidationException {
    PackageManager packageManager = getContext().getPackageManager();
    PackageInfo archiveInfo =
        packageManager.getPackageArchiveInfo(
            apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
    if (archiveInfo == null || archiveInfo.packageName == null) {
      throw new ApkValidationException("UPDATE_APK_INVALID", "Downloaded APK cannot be parsed");
    }
    if (!getContext().getPackageName().equals(archiveInfo.packageName)) {
      throw new ApkValidationException(
          "UPDATE_PACKAGE_MISMATCH", "Downloaded APK package does not match this app");
    }

    try {
      PackageInfo currentInfo =
          packageManager.getPackageInfo(
              getContext().getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
      if (archiveInfo.getLongVersionCode() <= currentInfo.getLongVersionCode()) {
        throw new ApkValidationException(
            "UPDATE_VERSION_NOT_NEWER", "Downloaded APK version is not newer");
      }
      if (!hasCompatibleSigner(currentInfo, archiveInfo)) {
        throw new ApkValidationException(
            "UPDATE_SIGNATURE_MISMATCH", "Downloaded APK signature does not match this app");
      }
    } catch (PackageManager.NameNotFoundException error) {
      throw new ApkValidationException(
          "UPDATE_APP_NOT_FOUND", "Installed app package cannot be inspected", error);
    }
  }

  private static boolean hasCompatibleSigner(PackageInfo currentInfo, PackageInfo archiveInfo) {
    if (currentInfo.signingInfo == null || archiveInfo.signingInfo == null) return false;
    Set<String> currentSigners = certificateDigests(currentInfo.signingInfo.getApkContentsSigners());
    if (currentInfo.signingInfo.hasMultipleSigners() || archiveInfo.signingInfo.hasMultipleSigners()) {
      return !currentSigners.isEmpty()
          && currentSigners.equals(certificateDigests(archiveInfo.signingInfo.getApkContentsSigners()));
    }
    // 新 APK 的签名历史必须包含当前签名，不能用共同旧证书接受已撤销的签名。
    Set<String> archiveHistory = certificateDigests(archiveInfo.signingInfo.getSigningCertificateHistory());
    return !currentSigners.isEmpty() && archiveHistory.containsAll(currentSigners);
  }

  private static Set<String> certificateDigests(Signature[] signatures) {
    Set<String> result = new HashSet<>();
    if (signatures == null) return result;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Signature signature : signatures) result.add(toHex(digest.digest(signature.toByteArray())));
    } catch (Exception ignored) {
      // SHA-256 是 Android 必备算法；异常时按签名不兼容处理
    }
    return result;
  }

  private void notifyProgress(long bytesRead, long contentLength) {
    JSObject event = new JSObject();
    event.put("bytesRead", bytesRead);
    event.put("contentLength", contentLength);
    event.put(
        "percent",
        contentLength > 0 ? Math.min(100d, bytesRead * 100d / contentLength) : -1d);
    notifyListeners("downloadProgress", event);
  }

  private static String sanitizeFileName(String value) {
    String name = value == null ? "" : new File(value).getName();
    return name.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private static String normalizeSha256(String value) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("sha256:")) normalized = normalized.substring(7);
    return normalized.matches("[a-f0-9]{64}") ? normalized : "";
  }

  private static String toHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
    return result.toString();
  }

  private static void deleteQuietly(File file) {
    if (file.exists()) file.delete();
  }

  private void disconnectActiveConnection() {
    HttpURLConnection connection = activeConnection;
    if (connection != null) connection.disconnect();
  }

  private static final class DownloadCancelledException extends Exception {}

  private static final class ApkValidationException extends Exception {
    private final String code;

    private ApkValidationException(String code, String message) {
      super(message);
      this.code = code;
    }

    private ApkValidationException(String code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }
  }
}

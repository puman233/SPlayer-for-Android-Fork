package top.imsyy.splayer.android.update;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "AndroidAppUpdate")
public class AndroidAppUpdatePlugin extends Plugin {
  private static final int BUFFER_SIZE = 64 * 1024;
  private static final long PROGRESS_INTERVAL_MS = 250L;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

  @Override
  protected void handleOnDestroy() {
    cancelRequested.set(true);
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
  public void cancelDownload(PluginCall call) {
    cancelRequested.set(true);
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

    cancelRequested.set(false);
    executor.execute(() -> runDownload(call, url, fileName, expectedSha256));
  }

  private void runDownload(
      PluginCall call, String sourceUrl, String fileName, String expectedSha256) {
    File updateDir = new File(getContext().getCacheDir(), "updates");
    File partial = new File(updateDir, fileName + ".part");
    File target = new File(updateDir, fileName);
    HttpURLConnection connection = null;

    try {
      if (!updateDir.exists() && !updateDir.mkdirs()) {
        throw new IllegalStateException("Cannot create update cache directory");
      }
      if (partial.exists() && !partial.delete()) {
        throw new IllegalStateException("Cannot replace partial update");
      }

      connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
      connection.setConnectTimeout(15_000);
      connection.setReadTimeout(30_000);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
      connection.setRequestProperty("User-Agent", "SPlayer-Android-Updater");

      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) {
        throw new IllegalStateException("HTTP " + status);
      }

      long contentLength = connection.getContentLengthLong();
      long bytesRead = 0L;
      long lastProgressAt = 0L;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[BUFFER_SIZE];

      try (InputStream input = new BufferedInputStream(connection.getInputStream());
          FileOutputStream output = new FileOutputStream(partial)) {
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (cancelRequested.get()) throw new DownloadCancelledException();
          output.write(buffer, 0, count);
          digest.update(buffer, 0, count);
          bytesRead += count;

          long now = System.currentTimeMillis();
          if (now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
            notifyProgress(bytesRead, contentLength);
            lastProgressAt = now;
          }
        }
        output.getFD().sync();
      }

      String actualSha256 = toHex(digest.digest());
      if (!expectedSha256.isEmpty() && !actualSha256.equals(expectedSha256)) {
        throw new SecurityException("SHA-256 mismatch");
      }
      if (target.exists() && !target.delete()) {
        throw new IllegalStateException("Cannot replace downloaded update");
      }
      if (!partial.renameTo(target)) {
        throw new IllegalStateException("Cannot finalize downloaded update");
      }

      notifyProgress(bytesRead, contentLength);
      JSObject result = new JSObject();
      result.put("fileName", fileName);
      result.put("bytesRead", bytesRead);
      result.put("sha256", actualSha256);
      call.resolve(result);
    } catch (DownloadCancelledException error) {
      deleteQuietly(partial);
      call.reject("Download cancelled", "UPDATE_DOWNLOAD_CANCELLED");
    } catch (SecurityException error) {
      deleteQuietly(partial);
      deleteQuietly(target);
      call.reject(error.getMessage(), "UPDATE_CHECKSUM_MISMATCH", error);
    } catch (Exception error) {
      deleteQuietly(partial);
      call.reject(error.getMessage(), "UPDATE_DOWNLOAD_FAILED", error);
    } finally {
      if (connection != null) connection.disconnect();
      cancelRequested.set(false);
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
    getContext().startActivity(installIntent);

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
    Set<String> currentSigners = certificateDigests(currentInfo.signingInfo.getSigningCertificateHistory());
    Set<String> archiveSigners = certificateDigests(archiveInfo.signingInfo.getSigningCertificateHistory());
    currentSigners.retainAll(archiveSigners);
    return !currentSigners.isEmpty();
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

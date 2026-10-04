package top.imsyy.splayer.android.update;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** APK 下载配置；版本信息仍直接读取 GitHub。 */
final class UpdateDownloadSources {
  static final boolean PROXY_ENABLED = true;
  static final String PROXY_BASE_URL = "https://gh.llkk.cc";

  static List<String> resolve(String original) {
    URI uri = URI.create(original);
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || !"github.com".equalsIgnoreCase(uri.getHost())
        || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || !uri.getPath().matches("/[^/]+/[^/]+/releases/download/[^/]+/[^/]+\\.apk")) {
      throw new IllegalArgumentException("Only official GitHub Release APK URLs are allowed");
    }
    return PROXY_ENABLED
        ? Arrays.asList(PROXY_BASE_URL + "/" + original, original)
        : Collections.singletonList(original);
  }
}

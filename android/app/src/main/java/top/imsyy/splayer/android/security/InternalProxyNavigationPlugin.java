package top.imsyy.splayer.android.security;

import android.net.Uri;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Official navigation mitigation for GHSA-rvm3-566m-v7fv until the framework upgrade. */
@CapacitorPlugin(name = "InternalProxyNavigation")
public final class InternalProxyNavigationPlugin extends Plugin {
  @Override
  public Boolean shouldOverrideLoad(Uri url) {
    String path = url.getPath();
    return path != null && path.startsWith("/_capacitor_http_interceptor_") ? true : null;
  }
}

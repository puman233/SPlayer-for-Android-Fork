package top.imsyy.splayer.android.security;

import android.util.Log;
import android.webkit.ConsoleMessage;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;

/** Preserve application diagnostics without the SDK's credential-bearing packet tracing. */
public final class ApplicationConsoleClient extends BridgeWebChromeClient {
  public ApplicationConsoleClient(Bridge bridge) {
    super(bridge);
  }

  @Override
  public boolean onConsoleMessage(ConsoleMessage message) {
    if (!isValidMsg(message.message())) return true;
    int priority;
    switch (message.messageLevel()) {
      case ERROR: priority = Log.ERROR; break;
      case WARNING: priority = Log.WARN; break;
      case DEBUG: priority = Log.DEBUG; break;
      default: priority = Log.INFO;
    }
    // The application consumers enforce safe diagnostics; source URLs are omitted.
    Log.println(priority, "SPlayer/Console", message.message());
    return true;
  }
}

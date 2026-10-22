package top.imsyy.splayer.android.playback;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** Opaque test-APK surface for secondary displays that have no HOME Activity. */
public final class BackgroundCoverActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    View surface = new View(this);
    surface.setBackgroundColor(0xff202124);
    setContentView(surface);
  }
}

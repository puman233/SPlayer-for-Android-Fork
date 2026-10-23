package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercise the actual offline home and its library entry on both form factors. */
@RunWith(AndroidJUnit4.class)
public class LocalHomeDeviceTest {
  @Test public void offlineHomeOffersUsableLibraryInBothOrientations() throws Exception {
    try (DeviceWebFixture f = new DeviceWebFixture()) {
      String saved = f.js("JSON.stringify({online:fixtureSetting.useOnlineService,path:fixtureRouter.currentRoute.value.fullPath})");
      int orientation = f.activity.getRequestedOrientation();
      try {
        f.js("fixtureSetting.useOnlineService=false;fixtureRouter.replace('/')");
        f.await("fixtureRouter.currentRoute.value.path==='/'&&!!document.querySelector('.home-local')");
        assertFalse("Offline home must not be an unfinished placeholder",
            "true".equals(f.js("document.querySelector('.home-local').textContent.includes('还没开发完')")));
        for (int mode : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
          f.inst.runOnMainSync(() -> f.activity.setRequestedOrientation(mode));
          f.await(mode == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ? "innerHeight>innerWidth" : "innerWidth>innerHeight");
          f.await("!!document.querySelector('.home-local .open-local-library')");
          SystemClock.sleep(500);
          // First-run agreement/update imports can settle after the fixture's
          // initial dismissal. Close them through the real modal interface.
          f.js("window.$modal?.destroyAll()");
          f.await("!document.querySelector('.n-modal-mask')");
          f.capture("offline-home-" + mode);
          f.tapVisible("document.querySelector('.home-local .open-local-library')");
          f.await("fixtureRouter.currentRoute.value.name==='local-songs'&&!!document.querySelector('.local')");
          f.js("fixtureRouter.replace('/')");
          f.await("!!document.querySelector('.home-local .open-local-library')");
        }
      } finally {
        f.inst.runOnMainSync(() -> f.activity.setRequestedOrientation(orientation));
        f.js("(()=>{const saved=JSON.parse(" + saved + ");fixtureSetting.useOnlineService=saved.online;fixtureRouter.replace(saved.path)})()");
      }
    }
  }
}

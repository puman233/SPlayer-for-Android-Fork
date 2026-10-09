package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FloatingLyricResumeTest {
  @Test public void missingWindowRestoresOnResumeAndWebViewReload() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      String saved=f.js("fixtureStatus.showDesktopLyric");
      try {
        f.js("window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(true)");
        awaitVisible(manager,true);
        f.await("fixtureStatus.showDesktopLyric===true");
        f.inst.runOnMainSync(()->f.activity.stopService(new Intent(f.activity,FloatingLyricService.class)));
        awaitVisible(manager,false);
        assertEquals("保留已开启的偏好来模拟系统销毁服务","true",f.js("fixtureStatus.showDesktopLyric"));
        f.inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
        SystemClock.sleep(1000);
        f.activity.startActivity(new Intent(f.activity,top.imsyy.splayer.android.MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        awaitVisible(manager,true);
        f.await("fixtureStatus.showDesktopLyric===true");
        f.inst.runOnMainSync(()->f.activity.stopService(new Intent(f.activity,FloatingLyricService.class)));
        awaitVisible(manager,false);
        f.inst.runOnMainSync(f.web::reload);
        awaitVisible(manager,true);
        f.await("!!document.querySelector('#app')?.__vue_app__");
        assertEquals("重新初始化时按钮反映实际窗口","true",f.js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);return p._s.get('status').showDesktopLyric})()"));
      } finally {
        f.js("window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(false)");
        awaitVisible(manager,false);
        f.js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);p._s.get('status').showDesktopLyric="+saved+"})()");
      }
    }
  }
  private void awaitVisible(PlaybackManager manager,boolean expected) {
    long until=SystemClock.uptimeMillis()+15000;
    while(SystemClock.uptimeMillis()<until) {
      if(manager.isFloatingLyricVisible()==expected)return;
      SystemClock.sleep(100);
    }
    assertEquals("实际窗口状态必须恢复",expected,manager.isFloatingLyricVisible());
  }
}

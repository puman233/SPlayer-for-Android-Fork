package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FloatingLyricResumeTest {
  @Test public void failedCloseKeepsWindowEnabledAndReportsFailure() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      String saved=f.js("fixtureStatus.showDesktopLyric");
      try {
        f.js("window.closeReady=false;window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(true).then(()=>window.closeReady=true)");
        f.await("window.closeReady===true&&fixtureStatus.showDesktopLyric===true");
        awaitVisible(manager,true);
        // 只替换原生桥边界的关闭响应，其余调用和实际窗口保持真实。
        f.js("window.$message.destroyAll()");
        f.await("!document.querySelector('.n-message-wrapper')");
        f.js("window.savedNativePromise=Capacitor.nativePromise;window.closeFailureInjected=false;Capacitor.nativePromise=function(plugin,method,...args){if(plugin==='AndroidNativePlayback'&&method==='hideFloatingLyric'){window.closeFailureInjected=true;return Promise.reject(new Error('test native close failure'))}return savedNativePromise.call(this,plugin,method,...args)};window.closeDone=false;window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(false).then(()=>window.closeDone=true)");
        f.await("window.closeDone===true&&window.closeFailureInjected===true");
        SystemClock.sleep(200);
        assertTrue("关闭失败时实际窗口仍存在",manager.isFloatingLyricVisible());
        assertEquals("关闭失败不能错误熄灭按钮","true",f.js("fixtureStatus.showDesktopLyric"));
        assertEquals("关闭失败不得提示已关闭","false",f.js("document.body.textContent.includes('已关闭桌面歌词')"));
        f.capture("close-failure-feedback");
        assertEquals("用户应看到关闭失败反馈 "+f.js("JSON.stringify([...document.querySelectorAll('.n-message')].map(e=>e.textContent))"),"true",f.js("document.body.textContent.includes('关闭桌面歌词失败')"));
      } finally {
        f.js("if(window.savedNativePromise)Capacitor.nativePromise=savedNativePromise;window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(false)");
        awaitVisible(manager,false);
        f.js("fixtureStatus.showDesktopLyric="+saved);
      }
    }
  }

  @Test public void missingWindowRestoresOnResumeAndWebViewReload() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      String saved=f.js("fixtureStatus.showDesktopLyric");
      try {
        f.js("window.resumeEnabled=false;window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(true).then(()=>window.resumeEnabled=true)");
        f.await("window.resumeEnabled===true");
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

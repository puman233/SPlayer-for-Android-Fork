package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 使用隔离包实际 WebView 验证手机与平板旋转后的布局 */
@RunWith(AndroidJUnit4.class)
public class PlayerDeviceLayoutTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private String shell(String command) throws Exception {
    try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(command);
        FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
  }
  private String js(WebView web, String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    String[] result = new String[1];
    instrumentation.runOnMainSync(() -> web.evaluateJavascript(expression, value -> {
      result[0] = value; done.countDown();
    }));
    assertTrue("WebView 回调超时", done.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  @Test public void playerRemainsReachableAcrossRealRotations() throws Exception {
    String pkg = instrumentation.getTargetContext().getPackageName();
    assertTrue(pkg.endsWith(".lyricsverify") || pkg.endsWith(".phase1verify") || pkg.endsWith(".debug"));
    Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(intent);
    WebView[] holder = new WebView[1];
    instrumentation.runOnMainSync(() -> holder[0] = activity.getBridge().getWebView());
    WebView web = holder[0];
    instrumentation.runOnMainSync(() -> web.loadUrl(activity.getBridge().getServerUrl()));
    boolean mounted = false;
    for (int i=0; i<60; i++) {
      if ("true".equals(js(web, "!!document.querySelector('#app')?.__vue_app__"))) { mounted=true; break; }
      SystemClock.sleep(200);
    }
    assertTrue("实际 Vue 页面必须完成挂载", mounted);
    js(web, "(()=>{const a=document.querySelector('#app').__vue_app__;window.verifyPinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);const s=verifyPinia._s.get('setting'),t=verifyPinia._s.get('status'),m=verifyPinia._s.get('music');s.userAgreementVersion='v2.0';s.androidDeviceModeOverride='auto';window.$modal?.destroyAll();m.playSong={...m.playSong,id:12345,name:'测试歌曲🎵光に溢れて',artists:[{id:1,name:'测试歌手'}],album:{id:2,name:'测试专辑'}};t.showFullPlayer=true;t.playerMetaShow=true;t.playLoading=false;})()");
    int[] display = new int[1];
    instrumentation.runOnMainSync(() -> display[0] = activity.getDisplay().getDisplayId());
    String rotation = shell("wm user-rotation -d " + display[0]);
    String globalRotation = shell("settings get system user_rotation");
    try {
      int previousWidth = -1;
      for (int r : new int[]{0,1,0,1}) {
        shell("wm user-rotation -d " + display[0] + " lock " + r);
        SystemClock.sleep(1600);
        js(web, "verifyPinia._s.get('status').playerMetaShow=true;window.$modal?.destroyAll()");
        SystemClock.sleep(200);
        String raw = js(web, "JSON.stringify((()=>{const root=document.querySelector('.full-player'),l=document.querySelector('.full-player-mobile-landscape'),p=document.querySelector('.full-player-mobile');const rect=e=>{if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height}};return {width:innerWidth,height:innerHeight,screenWidth:screen.width,screenHeight:screen.height,ua:navigator.userAgent,mobile:!!p,landscape:!!l,cover:l?rect(l.querySelector('.landscape-cover')):null,info:l?rect(l.querySelector('.info')):null,control:rect([...(root?.querySelectorAll('.player-control,.mobile-player-bottom-controls')||[])].find(e=>e.getBoundingClientRect().height>0)),buttons:[...(root?.querySelectorAll('.btn-icon,.play-pause')||[])].map(rect)}})())");
        JSONObject g = new JSONObject((String)new JSONTokener(raw).nextValue());
        Log.i("PlayerDeviceVerify", g.toString());
        assertNotEquals("实际 WebView 应响应旋转", previousWidth, g.getInt("width"));
        previousWidth = g.getInt("width");
        JSONObject c = g.getJSONObject("control");
        assertTrue(c.getDouble("height")>0);
        assertTrue(c.getDouble("y")>=-1);
        assertTrue(c.getDouble("bottom")<=g.getDouble("height")+1);
        if (g.getBoolean("landscape")) {
          JSONObject cover=g.getJSONObject("cover"), info=g.getJSONObject("info");
          assertTrue(cover.getDouble("height")>0);
          assertTrue(cover.getDouble("bottom")<=info.getDouble("y")+1);
          assertTrue(info.getDouble("bottom")<=c.getDouble("y")+1);
        }
      }
    } finally {
      shell("wm user-rotation -d " + display[0] + " " + rotation);
      // free 恢复策略时仍会保留最后一次角度，另行恢复全局偏好
      shell("settings put system user_rotation " + globalRotation);
      instrumentation.runOnMainSync(activity::finish);
    }
  }
}

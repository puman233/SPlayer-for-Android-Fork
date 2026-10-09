package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 真实 Vue 专辑页面使用隔离数据检查按钮、文字和浮层边界。 */
@RunWith(AndroidJUnit4.class)
public class AlbumHeaderGeometryTest {
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    String[] result = new String[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
        web.evaluateJavascript(expression, value -> { result[0] = value; latch.countDown(); }));
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  private void capture(MainActivity activity, String name) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    android.graphics.Bitmap[] bitmap = new android.graphics.Bitmap[1];
    int[] status = new int[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      android.view.View decor = activity.getWindow().getDecorView();
      bitmap[0] = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
      android.view.PixelCopy.request(activity.getWindow(), bitmap[0], result -> { status[0]=result; latch.countDown(); }, new android.os.Handler(android.os.Looper.getMainLooper()));
    });
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(android.view.PixelCopy.SUCCESS, status[0]);
    java.io.File dir = new java.io.File(activity.getExternalFilesDir(null), "update-layout");
    assertTrue(dir.exists() || dir.mkdirs());
    try(java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
      bitmap[0].compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
    }
    bitmap[0].recycle();
  }
  @Test public void albumTextActionsAndFloatingButtonsFit() throws Exception {
    android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    assertTrue(inst.getTargetContext().getPackageName().endsWith(".phase1verify")
        || inst.getTargetContext().getPackageName().endsWith(".debug"));
    org.json.JSONObject detail = new org.json.JSONObject("{\"id\":999999999,\"name\":\"いますぐ輪廻 — 长标题显示边界测试\",\"cover\":\"/images/album.jpg\",\"artists\":[{\"id\":1,\"name\":\"なきそ / 初音ミク — 超长艺术家名称\"}],\"count\":100,\"description\":\"带简介的长文本专辑，点击可查看完整简介\"}");
    org.json.JSONArray songs = new org.json.JSONArray();
    for (int i=0;i<100;i++) songs.put(new org.json.JSONObject("{\"id\":"+(999+i)+",\"name\":\"测试歌曲\",\"artists\":[{\"id\":1,\"name\":\"测试歌手\"}],\"album\":{\"id\":999999999,\"name\":\"测试专辑\"},\"cover\":\"/images/album.jpg\",\"duration\":120000}"));
    org.json.JSONObject cache = new org.json.JSONObject().put("version",2).put("timestamp",System.currentTimeMillis()).put("complete",true).put("type","album").put("id",999999999).put("detail",detail).put("songs",songs);
    top.imsyy.splayer.android.cache.CacheStorage storage = top.imsyy.splayer.android.cache.CacheStorage.getInstance(inst.getTargetContext());
    assertTrue(storage.write("list-data", "album-999999999.json", cache.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    MainActivity activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> { web = activity.getBridge().getWebView(); activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); });
    try {
      for (int i=0;i<100&&!"true".equals(js("!!document.querySelector('#app')?.__vue_app__"));i++) SystemClock.sleep(200);
      js("(()=>{const a=document.querySelector('#app').__vue_app__;window.p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.s=p._s.get('setting');window.t=p._s.get('status');window.m=p._s.get('music');s.userAgreementVersion='v2.0';s.checkUpdateOnStart=false;s.playlistPageElements.description=true;t.showFullPlayer=false;t.showPlayBar=true;window.$modal?.destroyAll();a.config.globalProperties.$router.push('/album?id=999999999');})()");
      for (int i=0;i<60&&!"true".equals(js("!!document.querySelector('.album-list .list-detail')"));i++) SystemClock.sleep(200);
      assertEquals("true", js("!!document.querySelector('.album-list .list-detail')"));
      js("m.playSong={...m.playSong,id:999,name:'测试歌曲',artists:[{id:1,name:'测试歌手'}],album:{id:999999999,name:'测试专辑'},duration:120000}");
      for(int i=0;i<60&&!"true".equals(js("!!document.querySelector('.list-detail .menu .left .n-button')"));i++) SystemClock.sleep(200);
      for (int zoom : new int[]{100,130,160}) {
        inst.runOnMainSync(() -> web.getSettings().setTextZoom(zoom));
        SystemClock.sleep(900);
        for (boolean small : new boolean[]{false,true}) {
          js("document.querySelector('.song-list .n-scrollbar-container').scrollTop="+(small?100:0));
          SystemClock.sleep(400);
          capture(activity, "album-"+zoom+"-"+small);
          assertEquals("操作按钮同一横排且不越界: " + js("JSON.stringify([...document.querySelectorAll('.list-detail .menu .left .n-button')].map(e=>({text:e.textContent,r:e.getBoundingClientRect().toJSON()})))"), "true", js("(()=>{const buttons=[...document.querySelectorAll('.list-detail .menu .left .n-button')].map(e=>e.getBoundingClientRect());return buttons.length===3&&buttons.every(r=>Math.abs(r.y-buttons[0].y)<1&&r.right<=innerWidth&&r.width>0)})()"));
          if (!small) assertEquals("元信息完整行可见，不被操作行挤压 zoom="+zoom+" "+js("JSON.stringify(['.list-detail .meta','.list-detail .collapse','.list-detail .menu'].map(s=>document.querySelector(s).getBoundingClientRect().toJSON()))"), "true", js("(()=>{const m=document.querySelector('.list-detail .meta').getBoundingClientRect(),c=document.querySelector('.list-detail .collapse').getBoundingClientRect(),b=document.querySelector('.list-detail .menu').getBoundingClientRect();return m.top>=c.top&&m.bottom<=c.bottom+1&&m.bottom<=b.top+1})()"));
          assertEquals("首行歌曲不与头部重叠", "true", js("(()=>{const h=document.querySelector('.list-detail .detail').getBoundingClientRect(),r=document.querySelector('.song-card')?.getBoundingClientRect();return !!r&&r.top>=h.bottom})()"));
        }
      }
      assertEquals("定位按钮在迷你播放栏上方", "true", js("(()=>{const b=document.querySelector('.list-menu .n-float-button'),p=document.querySelector('.main-player.phone-floating.show');return !b||!p||b.getBoundingClientRect().bottom<p.getBoundingClientRect().top})()"));
    } finally {
      storage.remove("list-data", "album-999999999.json");
      inst.runOnMainSync(() -> {web.getSettings().setTextZoom(100); activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); activity.finish();});
    }
  }
}

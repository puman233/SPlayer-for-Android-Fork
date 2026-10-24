package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import org.json.JSONTokener;
import top.imsyy.splayer.android.MainActivity;

/** Synthetic image delays exercise rendering only; existing authenticated account and tracks stay intact. */
@RunWith(AndroidJUnit4.class)
public class HomeCoverRefreshTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private String js(String code) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] out = {null};
    inst.runOnMainSync(() -> web.evaluateJavascript(code, value -> { out[0] = value; done.countDown(); }));
    assertTrue(done.await(10, TimeUnit.SECONDS)); return out[0];
  }
  private void await(String code) throws Exception {
    long until = SystemClock.elapsedRealtime() + 30000;
    while (SystemClock.elapsedRealtime() < until) {
      if ("true".equals(js(code))) return;
      SystemClock.sleep(100);
    }
    fail("Home cover condition: " + code + "; diagnostic=" + js("JSON.stringify((()=>{const q=window.coverRefresh;if(!q?.music)return {};const card=[...document.querySelectorAll('.home-online .song-data-card')].find(c=>c.textContent.includes('每日推荐'));return {listLength:q.music.dailySongsData.list.length,fixtureSource:q.music.dailySongsData.list[0]?.cover?.startsWith(q.url),images:[...(card?.querySelectorAll('img')||[])].map(i=>({width:i.naturalWidth,complete:i.complete,opacity:getComputedStyle(i).opacity,pending:!!i.closest('.pending')}))}})())"));
  }
  private byte[] png(int size, int color) {
    Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    image.eraseColor(color); ByteArrayOutputStream out = new ByteArrayOutputStream();
    image.compress(Bitmap.CompressFormat.PNG, 100, out); image.recycle(); return out.toByteArray();
  }
  private String diagnostic() throws Exception {
    return js("JSON.stringify((()=>{const q=coverRefresh,card=[...document.querySelectorAll('.home-online .song-data-card')].find(c=>c.textContent.includes('每日推荐'));return {listLength:q.music.dailySongsData.list.length,fixtureSource:q.music.dailySongsData.list[0]?.cover?.startsWith(q.url),visibility:document.visibilityState,images:[...(card?.querySelectorAll('img')||[])].map(i=>({width:i.naturalWidth,complete:i.complete,opacity:getComputedStyle(i).opacity,pending:!!i.closest('.pending')}))}})())");
  }
  private void assertPaintedColor(MainActivity activity, int size, int expected) throws Exception {
    JSONObject point = new JSONObject((String) new JSONTokener(js("JSON.stringify((()=>{const card=[...document.querySelectorAll('.home-online .song-data-card')].find(c=>c.textContent.includes('每日推荐'));const image=[...card.querySelectorAll('img')].find(i=>i.complete&&i.naturalWidth==="+size+");const r=image.getBoundingClientRect();return {x:r.x+r.width/4,y:r.y+r.height/4,width:innerWidth}})())")).nextValue());
    CountDownLatch copied = new CountDownLatch(1); int[] pixel = {0}, status = {-1};
    inst.runOnMainSync(() -> {
      var decor = activity.getWindow().getDecorView();
      Bitmap bitmap = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
      int[] location = new int[2]; web.getLocationInWindow(location);
      double scale = web.getWidth() / point.optDouble("width");
      int x = location[0] + (int) Math.round(point.optDouble("x") * scale);
      int y = location[1] + (int) Math.round(point.optDouble("y") * scale);
      PixelCopy.request(activity.getWindow(), bitmap, result -> {
        status[0] = result;
        if (result == PixelCopy.SUCCESS) pixel[0] = bitmap.getPixel(x, y);
        bitmap.recycle(); copied.countDown();
      }, new Handler(Looper.getMainLooper()));
    });
    assertTrue(copied.await(10, TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS, status[0]);
    for (int shift : new int[]{0, 8, 16}) assertTrue("The compositor still paints the decoded artwork", Math.abs(((expected >> shift) & 255) - ((pixel[0] >> shift) & 255)) < 5);
  }
  @Test public void refreshKeepsOldArtworkThroughSlowAndFailedImages() throws Exception {
    MainActivity activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> web = activity.getBridge().getWebView());
    await("!!document.querySelector('#app')?.__vue_app__");
    await("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);if(!p?._s.has('music')||!p?._s.has('status')||!p?._s.has('data'))return false;window.coverRefresh={a,p};return true})()");
    assertEquals("Use existing online account", "true", js("!!(coverRefresh.p._s.get('data').userLoginStatus&&coverRefresh.p._s.get('setting').useOnlineService)"));
    // Initial account-cache hydration writes dailySongsData asynchronously. Do not race it with fixture input.
    await("coverRefresh.p._s.get('data').userData.userId>0&&coverRefresh.p._s.get('music').dailySongsData.list.length>1&&!!coverRefresh.p._s.get('music').dailySongsData.timestamp");
    String host = "home-cover-" + UUID.randomUUID() + ".invalid";
    byte[] first = png(48, 0xff357c6e), second = png(64, 0xff865c9e);
    CountDownLatch requested = new CountDownLatch(1), release = new CountDownLatch(1);
    android.webkit.WebViewClient[] saved = {null};
    inst.runOnMainSync(() -> { saved[0] = web.getWebViewClient(); web.setWebViewClient(new com.getcapacitor.BridgeWebViewClient(activity.getBridge()) {
      @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
        if (!host.equals(request.getUrl().getHost())) return super.shouldInterceptRequest(view, request);
        String path = request.getUrl().getPath();
        if ("/b.png".equals(path)) { requested.countDown(); try { release.await(20, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        byte[] bytes = "/a.png".equals(path) ? first : "/b.png".equals(path) ? second : new byte[]{1, 2, 3};
        return new android.webkit.WebResourceResponse("image/png", null, 200, "OK", Map.of("Access-Control-Allow-Origin", "*", "Cache-Control", "max-age=3600"), new ByteArrayInputStream(bytes));
      }
    }); });
    js("(()=>{const q=coverRefresh;q.music=q.p._s.get('music');q.saved=q.music.dailySongsData;q.route=q.a.config.globalProperties.$router.currentRoute.value.fullPath;q.full=q.p._s.get('status').showFullPlayer;q.p._s.get('status').showFullPlayer=false;q.a.config.globalProperties.$router.push('/');q.url='https://"+host+"/';q.show=path=>q.music.dailySongsData={timestamp:Date.now(),list:[{...(q.saved.list[0]||{}),id:99001,cover:q.url+path,coverSize:{m:q.url+path}}]};q.visible=size=>{const card=[...document.querySelectorAll('.home-online .song-data-card')].find(c=>c.textContent.includes('每日推荐'));return !!card&&[...card.querySelectorAll('img')].some(i=>{if(!i.complete||i.naturalWidth!==size)return false;for(let e=i;e&&e!==card.parentElement;e=e.parentElement){const s=getComputedStyle(e);if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)<0.99)return false}return true})};q.show('a.png')})()");
    try {
      await("coverRefresh.visible(48)");
      js("coverRefresh.show('b.png')");
      assertTrue("New image request started", requested.await(10, TimeUnit.SECONDS));
      SystemClock.sleep(700);
      assertEquals("Old artwork stays fully visible while the replacement is stalled; " + diagnostic(), "true", js("coverRefresh.visible(48)"));
      assertPaintedColor(activity, 48, 0xff357c6e);
      release.countDown();
      await("coverRefresh.visible(64)");
      js("coverRefresh.show('broken.png')");
      SystemClock.sleep(1200);
      assertEquals("An invalid replacement keeps the last decoded artwork", "true", js("coverRefresh.visible(64)"));
      assertPaintedColor(activity, 64, 0xff865c9e);
    } finally {
      release.countDown();
      js("(()=>{const q=coverRefresh;q.music.dailySongsData=q.saved;q.a.config.globalProperties.$router.push(q.route);q.p._s.get('status').showFullPlayer=q.full;delete window.coverRefresh})()");
      inst.runOnMainSync(() -> web.setWebViewClient(saved[0]));
    }
  }
}

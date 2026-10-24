package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Existing account only. No cookie changes, fixture identity, list mutation or data clearing. */
@RunWith(AndroidJUnit4.class)
public class LikedHomeCoverTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    String[] result = {null};
    inst.runOnMainSync(() -> web.evaluateJavascript(expression, value -> {
      result[0] = value; done.countDown();
    }));
    assertTrue("WebView observation completed", done.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  private void await(String expression) throws Exception {
    long until = SystemClock.elapsedRealtime() + 60000;
    while (SystemClock.elapsedRealtime() < until) {
      if ("true".equals(js(expression))) return;
      SystemClock.sleep(200);
    }
    fail("Liked home cover condition failed: " + expression);
  }
  @Test public void loggedInLikedCoverDecodesOnHome() throws Exception {
    MainActivity activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(),
        MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> web = activity.getBridge().getWebView());
    await("!!document.querySelector('#app')?.__vue_app__");
    await("(()=>{const a=document.querySelector('#app').__vue_app__;const p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);if(!p?._s.has('data')||!p?._s.has('setting')||!p?._s.has('status'))return false;window.likedCoverProbe={a,p};return true})()");
    assertEquals("Use the already authenticated online account", "true", js("(()=>{const {p}=likedCoverProbe;const d=p._s.get('data'),s=p._s.get('setting');return !!(d.userLoginStatus&&d.userData.userId&&s.useOnlineService&&localStorage.getItem('cookie-MUSIC_U')&&!s.hiddenCovers.home)})()"));
    js("(()=>{const q=likedCoverProbe;q.token=q.p._s.get('data').getAccountToken();q.full=q.p._s.get('status').showFullPlayer;q.route=q.a.config.globalProperties.$router.currentRoute.value.fullPath;q.p._s.get('status').showFullPlayer=false;q.a.config.globalProperties.$router.push('/');})()");
    try {
      await("!!document.querySelector('.home-online')");
      await("!!likedCoverProbe.p._s.get('data').userLikeData.playlists[0]?.cover");
      // The selected album image must differ from the server heart composite and decode successfully.
      await("(()=>{const cards=[...document.querySelectorAll('.home-online .song-data-card')];const card=cards.find(c=>c.textContent.includes('我喜欢的音乐'));if(!card)return false;const seen=new Set(),stack=[document.querySelector('#app')._vnode];let c;while(stack.length){const v=stack.pop();if(!v||typeof v!=='object'||seen.has(v))continue;seen.add(v);if(v.props?.title==='我喜欢的音乐'&&v.component){c=v;break}if(v.component?.subTree)stack.push(v.component.subTree);if(Array.isArray(v.children))stack.push(...v.children);if(v.suspense?.activeBranch)stack.push(v.suspense.activeBranch)}const pl=likedCoverProbe.p._s.get('data').userLikeData.playlists[0];const expected=c?.props.cover;const images=[...card.querySelectorAll('img')];return !!expected&&expected!==pl.cover&&expected!==pl.coverSize?.m&&images.some(i=>i.complete&&i.naturalWidth>1&&i.naturalHeight>1&&!i.src.includes('/images/')&&(i.classList.contains('loaded')||!card.classList.contains('home-rec-card')))})()");
      assertEquals("The account stayed unchanged", "true", js("likedCoverProbe.token===likedCoverProbe.p._s.get('data').getAccountToken()"));
      // Decoding can complete before Chromium submits its next frame and the image fade finishes.
      js("likedCoverProbe.painted=false;requestAnimationFrame(()=>requestAnimationFrame(()=>likedCoverProbe.painted=true))");
      await("likedCoverProbe.painted===true");
      SystemClock.sleep(500);
      // Copy this activity's window: UiAutomation captures display 0, which may be MuMu's launcher.
      Bitmap[] captured = {null};
      int[] captureStatus = {-1};
      CountDownLatch captureDone = new CountDownLatch(1);
      inst.runOnMainSync(() -> {
        var decor = activity.getWindow().getDecorView();
        assertTrue("Visible app window has dimensions", decor.getWidth() > 0 && decor.getHeight() > 0);
        captured[0] = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(activity.getWindow(), captured[0], status -> {
          captureStatus[0] = status; captureDone.countDown();
        }, new Handler(Looper.getMainLooper()));
      });
      assertTrue("App-window screenshot completed", captureDone.await(10, TimeUnit.SECONDS));
      assertEquals("App-window screenshot succeeded", PixelCopy.SUCCESS, captureStatus[0]);
      Bitmap screenshot = captured[0];
      try (FileOutputStream output = new FileOutputStream(new File(inst.getTargetContext().getExternalFilesDir(null), "liked-home-cover.png"))) {
        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
      } finally { screenshot.recycle(); }
      js("(()=>{const q=likedCoverProbe;q.playback=()=>JSON.stringify({song:q.p._s.get('music').playSong.id,queue:q.p._s.get('data').playList.map(s=>s.id),mode:q.p._s.get('status').shuffleMode});q.beforeClick=q.playback();[...document.querySelectorAll('.home-online .song-data-card')].find(c=>c.textContent.includes('我喜欢的音乐')).click()})()");
      await("likedCoverProbe.a.config.globalProperties.$router.currentRoute.value.name==='like-songs'");
      assertEquals("Clicking the artwork keeps the account", "true", js("likedCoverProbe.token===likedCoverProbe.p._s.get('data').getAccountToken()"));
      assertEquals("Artwork navigation preserves the song, queue and playback mode", "true", js("likedCoverProbe.beforeClick===likedCoverProbe.playback()"));
    } finally {
      js("(()=>{const q=likedCoverProbe;q.a.config.globalProperties.$router.push(q.route);q.p._s.get('status').showFullPlayer=q.full;delete window.likedCoverProbe;})()");
    }
  }
}

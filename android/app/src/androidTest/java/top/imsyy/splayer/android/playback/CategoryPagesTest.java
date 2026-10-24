package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 使用真实登录和接口；可临时挂载待验收 Web 资源，结束后恢复。 */
@RunWith(AndroidJUnit4.class)
public class CategoryPagesTest {
  final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  WebView web;
  MainActivity activity;
  @Test public void installedPageIdentity() throws Exception {
    assertEquals("true", InstrumentationRegistry.getArguments().getString("verifyInstalled"));
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView();
    await("!!document.querySelector('#app')?.__vue_app__");
    await("(()=>{const d=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('data');return !!d?.userLoginStatus&&!!d?.userData?.userId})()");
    assertFalse("必须验收APK内置资源", new File(activity.getBridge().getServerBasePath()).isAbsolute());
    js("window.cv={router:document.querySelector('#app').__vue_app__.config.globalProperties.$router};void 0");
    String account = js("document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('data').userData.userId");
    StringBuilder fingerprint = new StringBuilder();
    for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(account.getBytes(StandardCharsets.UTF_8))) fingerprint.append(String.format("%02x", value & 255));
    JSONObject evidence = new JSONObject();
    evidence.put("accountFingerprint", fingerprint.toString());
    evidence.put("version", inst.getTargetContext().getPackageManager().getPackageInfo(inst.getTargetContext().getPackageName(), 0).versionName);
    evidence.put("base", activity.getBridge().getServerBasePath());
    String stage = InstrumentationRegistry.getArguments().getString("stage", "installed");
    assertTrue(stage.matches("[a-z0-9-]+"));
    File dir = new File(inst.getTargetContext().getExternalFilesDir(null), "category-verification");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    try (var out = new java.io.FileOutputStream(new File(dir, stage + "-identity.json"))) { out.write(evidence.toString().getBytes(StandardCharsets.UTF_8)); }
    route("/discover/playlists");
    await("!!document.querySelector('.discover-playlists')");
    route("/discover/toplists");
    assertEquals("APK内置页面必须切换为排行榜，不能保留歌单广场", "true", js("!!document.querySelector('.discover-toplists')&&!document.querySelector('.discover-playlists')"));
    capture(stage + "-installed-toplists");
  }
  String js(String script) throws Exception {
    String[] result = new String[1]; CountDownLatch done = new CountDownLatch(1);
    inst.runOnMainSync(() -> web.evaluateJavascript(script, value -> { result[0] = value; done.countDown(); }));
    assertTrue("JS timeout: " + script.substring(0, Math.min(160, script.length())) + "; activityDestroyed=" + activity.isDestroyed(), done.await(15, TimeUnit.SECONDS));
    return result[0];
  }
  void await(String expression) throws Exception {
    long end = SystemClock.elapsedRealtime() + 60000;
    while (SystemClock.elapsedRealtime() < end) {
      if ("true".equals(js(expression))) return;
      SystemClock.sleep(150);
    }
    capture("failure");
    fail("等待失败: " + expression + "; " + js("JSON.stringify({route:location.hash,error:document.querySelector('.n-alert')?.textContent,requests:window.cv?.requests,login:window.cv?.data?.userLoginStatus,hasAccount:!!window.cv?.data?.userData?.userId,loginType:window.cv?.data?.loginType,savedAccounts:window.cv?.data?.userList?.length,body:document.body.innerText.slice(0,160)})"));
  }
  void route(String path) throws Exception {
    js("window.cv.routed=false;cv.router.push(" + JSONObject.quote(path) + ").then(()=>cv.routed=true)");
    await("cv.routed && !document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity)");
  }
  void capture(String name) throws Exception {
    boolean settled = false;
    for (int attempt = 0; attempt < 100; attempt++) {
      if ("true".equals(js("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity)"))) { settled = true; break; }
      SystemClock.sleep(100);
    }
    assertTrue("截图前过渡动画必须结束", settled);
    File dir = new File(inst.getTargetContext().getExternalFilesDir(null), "category-verification");
    assertTrue(dir.isDirectory() || dir.mkdirs());
    CountDownLatch visual = new CountDownLatch(1);
    inst.runOnMainSync(() -> web.postVisualStateCallback(SystemClock.uptimeMillis(), new WebView.VisualStateCallback() {
      @Override public void onComplete(long id) { web.invalidate(); web.postOnAnimation(() -> web.postOnAnimation(visual::countDown)); }
    }));
    assertTrue(visual.await(10, TimeUnit.SECONDS));
    CountDownLatch copied = new CountDownLatch(1);
    android.graphics.Bitmap[] pixels = new android.graphics.Bitmap[1];
    int[] status = new int[1];
    inst.runOnMainSync(() -> {
      android.view.View decor = activity.getWindow().getDecorView();
      pixels[0] = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
      android.view.PixelCopy.request(activity.getWindow(), pixels[0], result -> { status[0] = result; copied.countDown(); }, new android.os.Handler(android.os.Looper.getMainLooper()));
    });
    assertTrue(copied.await(10, TimeUnit.SECONDS));
    assertEquals(android.view.PixelCopy.SUCCESS, status[0]);
    android.graphics.Bitmap bitmap = pixels[0];
    try (var out = new java.io.FileOutputStream(new File(dir, name + ".png"))) {
      assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
    }
    bitmap.recycle();
  }
  @Test public void realAccountCategoriesAndNavigation() throws Exception {
    String assets = InstrumentationRegistry.getArguments().getString("webAssets");
    String pkg = inst.getTargetContext().getPackageName();
    assertTrue("主App验收需要显式指定", pkg.endsWith(".debug") || assets != null || "true".equals(InstrumentationRegistry.getArguments().getString("verifyInstalled")));
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView();
    await("!!document.querySelector('#app')?.__vue_app__");
    await("(()=>{const d=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('data');return !!d?.userLoginStatus&&!!d?.userData?.userId})()");
    System.out.println("INSTALLED_ACCOUNT=" + js("JSON.stringify((()=>{const d=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('data');return {login:d?.userLoginStatus,hasAccount:!!d?.userData?.userId,savedAccounts:d?.userList?.length}})())"));
    String originalBase = activity.getBridge().getServerBasePath();
    boolean completed = false;
    try {
      if (assets != null) {
        assertTrue(new File(assets, "index.html").isFile());
        String html = new String(java.nio.file.Files.readAllBytes(new File(assets, "index.html").toPath()), StandardCharsets.UTF_8);
        java.util.regex.Matcher script = java.util.regex.Pattern.compile("src=\"([^\"]*/assets/index-[^\"]+\\.js)\"").matcher(html);
        assertTrue("缺少构建入口", script.find());
        String bundle = script.group(1).substring(script.group(1).lastIndexOf('/') + 1);
        inst.runOnMainSync(() -> activity.getBridge().setServerBasePath(assets));
        await("!!document.querySelector('#app')?.__vue_app__ && [...document.scripts].some(s=>s.src.endsWith(" + JSONObject.quote(bundle) + "))");
      }
      js("window.cv={router:document.querySelector('#app').__vue_app__.config.globalProperties.$router,pinia:document.querySelector('#app').__vue_app__.config.globalProperties.$pinia,requests:[],rows:[]};void 0");
      await("(cv.data=cv.pinia._s.get('data'))?.userLoginStatus && cv.data.userData.userId>0");
      js("cv.setting=cv.pinia._s.get('setting');cv.savedKeep=cv.setting.useKeepAlive;cv.savedRoute=cv.router.currentRoute.value.fullPath;cv.setting.useKeepAlive=true;cv.account=cv.data.userData.userId;cv.savedOpen=XMLHttpRequest.prototype.open;cv.savedSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u,...args){this.cvPath=String(u).split('?')[0];return cv.savedOpen.call(this,m,u,...args)};XMLHttpRequest.prototype.send=function(...args){this.addEventListener('loadend',()=>{let body;try{body=JSON.parse(this.responseText)}catch{};cv.requests.push({path:this.cvPath,status:this.status,code:body?.code,counts:Object.fromEntries(['list','artists','albums','data','djRadios','playlist'].filter(k=>Array.isArray(body?.[k])).map(k=>[k,body[k].length]))})},{once:true});return cv.savedSend.apply(this,args)}");
      await("cv.data.userLoginStatus && cv.data.userData.userId>0");
      js("cv.account=cv.data.userData.userId");
      String[][] pages = {
        {"/discover/playlists", "/top/playlist", ".discover-playlists", ".cover-list.playlist .cover-item", "playlist"},
        {"/discover/toplists", "/toplist/detail", ".discover-toplists", ".official-list .n-card", "playlist"},
        {"/discover/artists", "/artist/list", ".discover-artists", ".artist-list .artist-item", "artist"},
        {"/discover/new", "/album/new", ".discover-new", ".cover-list.album .cover-item", "album"},
        {"/like/playlists", "", ".like", ".cover-list.playlist .cover-item", "playlist"},
        {"/like/albums", "/album/sublist", ".like", ".cover-list.album .cover-item", "album"},
        {"/like/artists", "/artist/sublist", ".like", ".artist-list .artist-item", "artist"},
        {"/like/videos", "/mv/sublist", ".like", ".cover-list.video .cover-item", "video"},
        {"/like/radios", "/dj/sublist", ".like", ".cover-list.radio .cover-item", "radio"}
      };
      for (String[] page : pages) {
        js("cv.requests=[]"); route(page[0]); await("!!document.querySelector(" + JSONObject.quote(page[2]) + ")");
        if (!page[1].isEmpty()) await("cv.requests.some(r=>r.path.endsWith(" + JSONObject.quote(page[1]) + ")&&r.status===200&&r.code===200)");
        await("!document.querySelector(" + JSONObject.quote(page[2] + " .n-alert," + page[2] + " .n-skeleton") + ") && (!!document.querySelector(" + JSONObject.quote(page[3]) + ")||!!document.querySelector(" + JSONObject.quote(page[2] + " .n-empty") + "))");
        String row = "{route:" + JSONObject.quote(page[0]) + ",requests:cv.requests,count:document.querySelectorAll(" + JSONObject.quote(page[3]) + ").length,empty:!!document.querySelector(" + JSONObject.quote(page[2] + " .n-empty") + ")}";
        js("cv.rows.push(JSON.parse(JSON.stringify(" + row + ")))"); capture(page[0].substring(1).replace('/', '-'));
        if ("true".equals(js("!!document.querySelector(" + JSONObject.quote(page[3]) + ")"))) {
          js("document.querySelector(" + JSONObject.quote(page[3]) + ").click()");
          await("cv.router.currentRoute.value.path.startsWith('/" + page[4] + "')");
          js("cv.router.back()"); await("cv.router.currentRoute.value.path===" + JSONObject.quote(page[0]));
          await("!!document.querySelector(" + JSONObject.quote(page[3]) + ")");
        }
      }
      route("/discover/artists");
      await("!!document.querySelector('.discover-artists .artist-item') && !document.querySelector('.discover-artists .n-skeleton')");
      js("cv.artistBefore=document.querySelectorAll('.discover-artists .artist-item').length;cv.pagination=false;const more=document.querySelector('.discover-artists .load-more button');if(more){more.click();cv.pagination=true}");
      if ("true".equals(js("cv.pagination"))) await("document.querySelectorAll('.discover-artists .artist-item').length>cv.artistBefore");
      js("cv.artistAfter=document.querySelectorAll('.discover-artists .artist-item').length");
      route("/like/albums");
      await("!document.querySelector('.like .n-skeleton,.like .n-alert')");
      js("cv.refreshCount=cv.requests.filter(r=>r.path.endsWith('/album/sublist')).length;cv.longPress=!!document.querySelector('.mobile-bottom-nav__item.active');if(cv.longPress){const button=document.querySelector('.mobile-bottom-nav__item.active');button.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}));setTimeout(()=>button.dispatchEvent(new PointerEvent('pointerup',{bubbles:true})),800)}");
      if ("true".equals(js("cv.longPress"))) {
        await("cv.requests.filter(r=>r.path.endsWith('/album/sublist')).length>cv.refreshCount && !document.querySelector('.like .n-skeleton,.like .n-alert')");
      }
      // 只模拟发现页网络错误；收藏数据始终使用真实接口。
      js("cv.recordingSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(...args){if(this.cvPath?.endsWith('/toplist/detail')){setTimeout(()=>this.onerror?.(new ProgressEvent('error')),0);return;}return cv.recordingSend.apply(this,args)}");
      route("/"); route("/discover/toplists");
      await("!!document.querySelector('.discover-toplists .n-alert') && !document.querySelector('.discover-toplists .cover-list')");
      capture("discover-network-error");
      js("XMLHttpRequest.prototype.send=cv.recordingSend;document.querySelector('.discover-toplists .n-alert button').click()");
      await("!document.querySelector('.discover-toplists .n-alert,.discover-toplists .n-skeleton') && !!document.querySelector('.discover-toplists .official-list .n-card')");
      js("cv.networkRetry=true");
      route("/discover/new?type=1");
      await("cv.requests.some(r=>r.path.endsWith('/top/song')&&r.status===200&&r.code===200) && !!document.querySelector('.discover-new .song-list') && !document.querySelector('.discover-new .n-skeleton')");
      js("cv.latestSongs={response:cv.requests.filter(r=>r.path.endsWith('/top/song')),rendered:document.querySelectorAll('.discover-new .song-list .song-card:not(.list-header)').length}");
      capture("discover-new-songs");
      // 连续快速切换，最终必须显示收藏专辑，不能残留其他页面。
      js("cv.router.push('/discover/toplists');cv.router.push('/like/artists');cv.router.push('/discover/new');cv.router.push('/like/albums')");
      await("cv.router.currentRoute.value.path==='/like/albums' && !!document.querySelector('.like-type') && !document.querySelector('.discover-new,.discover-toplists,.cover-list.playlist')");
      assertEquals("true", js("cv.data.userData.userId===cv.account && cv.data.userLoginStatus"));
      File report = new File(inst.getTargetContext().getExternalFilesDir(null), "category-verification/results.json");
      String result = (String) new JSONTokener(js("JSON.stringify({rows:cv.rows,latestSongs:cv.latestSongs,rapidSwitch:true,pagination:{before:cv.artistBefore,after:cv.artistAfter},longPress:cv.longPress,networkErrorAndRetry:cv.networkRetry,accountPreserved:cv.data.userData.userId===cv.account})")).nextValue();
      try (var out = new java.io.FileOutputStream(report)) { out.write(result.getBytes(StandardCharsets.UTF_8)); }
      completed = true;
    } finally {
      AssertionError cleanupFailure = null;
      try {
        js("if(window.cv?.savedOpen){XMLHttpRequest.prototype.open=cv.savedOpen;XMLHttpRequest.prototype.send=cv.savedSend;cv.setting.useKeepAlive=cv.savedKeep;cv.router.push(cv.savedRoute)}");
      } catch (AssertionError error) {
        cleanupFailure = error;
      } finally {
        if (assets != null) inst.runOnMainSync(() -> {
          if (new File(originalBase).isAbsolute()) activity.getBridge().setServerBasePath(originalBase);
          else activity.getBridge().setServerAssetPath(originalBase);
        });
      }
      if (completed && cleanupFailure != null) throw cleanupFailure;
    }
  }
}

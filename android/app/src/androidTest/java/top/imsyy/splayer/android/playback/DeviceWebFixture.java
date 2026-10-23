package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import top.imsyy.splayer.android.MainActivity;

/** 只在隔离包准备 Vue 页面，不修改真实账号或清空数据。 */
final class DeviceWebFixture implements AutoCloseable {
  final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  final MainActivity activity;
  final WebView web;
  final String savedSettings;
  final Object[] savedNativeApiContext=new Object[5];
  DeviceWebFixture() throws Exception {
    String pkg = inst.getTargetContext().getPackageName();
    assertTrue(pkg.endsWith(".phase1verify") || pkg.endsWith(".debug"));
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView();
    await("!!document.querySelector('#app')?.__vue_app__");
    // Vue 挂载早于异步数据加载和启动恢复；先等恢复播放任务收尾。
    try {
      String ready="(window.__SPLAYER_PLAYER_CONTROLLER__?.currentRequestToken>0||!!window.__SPLAYER_PLAYER_CONTROLLER__?.currentAudioSource?.url)&&!Reflect.ownKeys(document.querySelector('#app').__vue_app__._context.provides).map(k=>document.querySelector('#app').__vue_app__._context.provides[k]).find(v=>v?._s instanceof Map)._s.get('status').playLoading";
      for(int i=0;i<300&&!"true".equals(js(ready));i++) SystemClock.sleep(100);
      assertEquals("启动播放恢复必须完成", "true", js(ready));
    } catch(AssertionError error) {
      error.addSuppressed(new AssertionError("startup state="+js("JSON.stringify((()=>{const p=Reflect.ownKeys(document.querySelector('#app').__vue_app__._context.provides).map(k=>document.querySelector('#app').__vue_app__._context.provides[k]).find(v=>v?._s instanceof Map);return {token:window.__SPLAYER_PLAYER_CONTROLLER__?.currentRequestToken,loading:p._s.get('status').playLoading,nativeRestoredSource:!!window.__SPLAYER_PLAYER_CONTROLLER__?.currentAudioSource?.url,hasSong:!!p._s.get('music').playSong?.id}})())")));
      inst.runOnMainSync(()->activity.finish());
      throw error;
    }
    js("(()=>{const a=document.querySelector('#app').__vue_app__;window.fixtureRouter=a.config.globalProperties.$router;window.fixturePinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.fixtureStatus=fixturePinia._s.get('status');window.fixtureSetting=fixturePinia._s.get('setting');window.fixtureSaved={agreement:fixtureSetting.userAgreementVersion,update:fixtureSetting.checkUpdateOnStart,show:fixtureStatus.showFullPlayer,hiddenCovers:JSON.parse(JSON.stringify(fixtureSetting.hiddenCovers)),songUnlockServer:JSON.parse(JSON.stringify(fixtureSetting.songUnlockServer)),useSongUnlock:fixtureSetting.useSongUnlock};fixtureSetting.userAgreementVersion='v2.0';fixtureSetting.checkUpdateOnStart=false;fixtureStatus.showFullPlayer=false;window.$modal?.destroyAll();})()");
    savedSettings = (String) new org.json.JSONTokener(js("JSON.stringify(fixtureSaved)")).nextValue();
    inst.runOnMainSync(()->{
      try {
        PlaybackManager manager=PlaybackManager.getInstance(activity);
        String[] names={"apiBaseUrl","cookie","songLevel","disableAiAudio","playSongDemo"};
        for(int i=0;i<names.length;i++) {
          var field=PlaybackManager.class.getDeclaredField(names[i]);field.setAccessible(true);
          savedNativeApiContext[i]=field.get(manager);
        }
      }catch(Exception error){throw new AssertionError(error);}
    });
  }
  void restoreNativeApiContext() {
    inst.runOnMainSync(()->PlaybackManager.getInstance(activity).syncApiContext(
        (String)savedNativeApiContext[0],(String)savedNativeApiContext[1],(String)savedNativeApiContext[2],
        (Boolean)savedNativeApiContext[3],(Boolean)savedNativeApiContext[4]));
  }
  String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    String[] result = new String[1];
    inst.runOnMainSync(() -> web.evaluateJavascript(expression, value -> { result[0]=value; done.countDown(); }));
    assertTrue("WebView 回调超时", done.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  void await(String expression) throws Exception {
    for (int i=0;i<100;i++) {
      if ("true".equals(js(expression))) return;
      SystemClock.sleep(100);
    }
    fail("页面条件未满足: " + expression + "; result=" + js(expression)
        + "; layout=" + js("JSON.stringify({viewport:[innerWidth,innerHeight],screen:[screen.width,screen.height],dpr:devicePixelRatio,deviceMode:window.fixtureSetting?.androidDeviceModeOverride,pageZoom:window.fixtureSetting?.pageZoom,portrait:!!document.querySelector('.full-player-mobile'),landscape:!!document.querySelector('.full-player-mobile-landscape'),desktop:!!document.querySelector('.full-player.pad-desktop'),playerVisible:window.fixtureStatus?.showFullPlayer,textZoom:document.querySelector('.info-page .meta-actions-row')?.getBoundingClientRect().toJSON()})")
        + "; nativeDisplay=" + activity.getDisplay().getDisplayId()
        + "; nativeSize=" + web.getWidth() + "x" + web.getHeight());
  }
  Stage moveToBackground() throws Exception {
    return moveToBackground(inst,activity);
  }
  static Stage moveToBackground(Instrumentation inst,MainActivity activity) throws Exception {
    inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
    Stage[] stage = {null};
    long deadline = SystemClock.elapsedRealtime() + 500;
    do {
      inst.runOnMainSync(() -> stage[0] = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity));
      if (stage[0] == Stage.STOPPED) return stage[0];
      SystemClock.sleep(50);
    } while (SystemClock.elapsedRealtime() < deadline);
    // HOME may target the default display while this Activity remains resumed
    // on a MuMu secondary display. Move the actual task, then prove it stopped.
    boolean[] moved = {false};
    inst.runOnMainSync(() -> moved[0] = activity.moveTaskToBack(true));
    assertTrue("The target task must move behind its display's foreground", moved[0]);
    deadline = SystemClock.elapsedRealtime() + 500;
    do {
      inst.runOnMainSync(() -> stage[0] = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity));
      if (stage[0] == Stage.STOPPED) return stage[0];
      SystemClock.sleep(100);
    } while (SystemClock.elapsedRealtime() < deadline);
    // MuMu can keep the sole Activity resumed on a display with no secondary
    // launcher. A real opaque Activity in the test APK represents switching apps.
    Intent cover = new Intent(inst.getContext(), BackgroundCoverActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    var options = android.app.ActivityOptions.makeBasic();
    options.setLaunchDisplayId(activity.getDisplay().getDisplayId());
    inst.getContext().startActivity(cover, options.toBundle());
    deadline = SystemClock.elapsedRealtime() + 5000;
    do {
      inst.runOnMainSync(() -> stage[0] = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity));
      if (stage[0] == Stage.STOPPED) return stage[0];
      SystemClock.sleep(100);
    } while (SystemClock.elapsedRealtime() < deadline);
    assertEquals("The actual target Activity must stop before background observation", Stage.STOPPED, stage[0]);
    return stage[0];
  }
  void tapVisible(String expression) throws Exception { tapVisible(expression, 0.5, 0.5); }
  void tapVisible(String expression, double fractionX, double fractionY) throws Exception {
    // Prepare the hit target and listener atomically; no extra WebView round trips
    // between the valid sample and native input (controls may auto-hide meanwhile).
    String point="(()=>{if(document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&(document.querySelector('.full-player')?.contains(a.effect?.target)||a.effect?.target?.closest?.('.n-popover,.quick-actions-panel'))))return null;const e="+expression+";if(!e)return null;const r=e.getBoundingClientRect();const x=r.x+r.width*"+fractionX+",y=r.y+r.height*"+fractionY+";if(!(r.width>0&&r.height>0&&e.contains(document.elementFromPoint(x,y))))return null;window.fixtureClickSeen=false;window.fixtureInputTrace=[];window.fixtureInputProbe=e=>fixtureInputTrace.push({type:e.type,target:String(e.target?.className),time:e.timeStamp,x:e.clientX??e.changedTouches?.[0]?.clientX,y:e.clientY??e.changedTouches?.[0]?.clientY});window.fixtureInputTypes=['pointerdown','pointerup','pointercancel','touchstart','touchend','click','contextmenu'];fixtureInputTypes.forEach(t=>document.addEventListener(t,fixtureInputProbe,true));e.addEventListener('click',()=>window.fixtureClickSeen=true,{capture:true,once:true});return {x,y,viewportWidth:innerWidth}})()";
    String sampledPoint = "null";
    try {
      for (int attempt=0;attempt<100;attempt++) {
        Object decodedPoint=new org.json.JSONTokener(js("JSON.stringify("+point+")")).nextValue();
        sampledPoint=decodedPoint instanceof String ? (String)decodedPoint : "null";
        if (!"null".equals(sampledPoint)) break;
        SystemClock.sleep(100);
      }
      assertNotEquals("触摸坐标必须来自同一次有效命中采样", "null", sampledPoint);
    }
    catch(AssertionError error) {
      capture("touch-target-unreachable");
      throw new AssertionError("触摸目标="+js("JSON.stringify((()=>{const e="+expression+";if(!e)return {missing:true};const r=e.getBoundingClientRect();return {rect:r.toJSON(),viewport:[innerWidth,innerHeight],controlsHidden:document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden'),inert:!!e.closest('[inert]'),visibility:getComputedStyle(e).visibility,opacity:getComputedStyle(e).opacity,stack:document.elementsFromPoint(r.x+r.width/2,r.y+r.height/2).slice(0,7).map(n=>({cls:String(n.className),tag:n.tagName,rect:n.getBoundingClientRect().toJSON()}))}})())"),error);
    }
    var p=new org.json.JSONObject(sampledPoint);
    float scale=(float)(web.getWidth()/p.getDouble("viewportWidth"));
    float x=(float)p.getDouble("x")*scale,y=(float)p.getDouble("y")*scale;
    // Keep a native tap in one main-thread turn: queueing must not age DOWN into a long press.
    inst.runOnMainSync(() -> {
      long down=SystemClock.uptimeMillis();
      for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
        var event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(event);event.recycle();
      }
    });
    try { await("window.fixtureClickSeen===true&&!fixtureInputTrace.some(e=>e.type==='contextmenu')"); }
    catch(AssertionError error) {throw new AssertionError("输入事件轨迹="+js("JSON.stringify(fixtureInputTrace)"),error);}
    finally {js("fixtureInputTypes.forEach(t=>document.removeEventListener(t,fixtureInputProbe,true))");}
  }
  private void awaitVisualState() throws Exception {
    CountDownLatch visual = new CountDownLatch(1);
    inst.runOnMainSync(() -> web.postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback() {
      @Override public void onComplete(long id) {
        web.invalidate();web.postOnAnimation(()->web.postOnAnimation(visual::countDown));
      }
    }));
    assertTrue("截图前WebView绘制状态必须就绪",visual.await(10,TimeUnit.SECONDS));
  }
  void capture(String name) throws Exception {
    awaitVisualState();
    CountDownLatch done = new CountDownLatch(1);
    android.graphics.Bitmap[] bitmap = new android.graphics.Bitmap[1];
    int[] status = new int[1];
    inst.runOnMainSync(() -> {
      android.view.View decor=activity.getWindow().getDecorView();
      bitmap[0]=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
      android.view.PixelCopy.request(activity.getWindow(),bitmap[0], result->{status[0]=result;done.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
    });
    assertTrue(done.await(10,TimeUnit.SECONDS));
    assertEquals(android.view.PixelCopy.SUCCESS,status[0]);
    java.io.File dir=new java.io.File(activity.getExternalFilesDir(null),"detail-regression");
    assertTrue(dir.exists()||dir.mkdirs());
    try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
      assertTrue(bitmap[0].compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream));
    } finally {bitmap[0].recycle();}
  }
  @Override public void close() throws Exception {
    try { js("window.fixtureRestoreComplete=false;Promise.resolve(window.p7RestorePromise).finally(()=>window.fixtureRestoreComplete=true)");
    await("window.fixtureRestoreComplete===true"); } finally {
    try {
      js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map),s=p._s.get('setting'),t=p._s.get('status'),saved="+savedSettings+";s.userAgreementVersion=saved.agreement;s.checkUpdateOnStart=saved.update;s.hiddenCovers=saved.hiddenCovers;s.songUnlockServer=saved.songUnlockServer;s.useSongUnlock=saved.useSongUnlock;t.showFullPlayer=saved.show;window.fixturePinia=p;window.fixtureStatus=t;window.fixtureSetting=s;s.$persist?.();})()");
      // music-store uses idle/1s persistence; finishing an Activity is not a reliable pagehide.
      js("(()=>{fixturePinia._s.get('music').$persist?.();fixturePinia._s.get('status').$persist?.();window.dispatchEvent(new Event('pagehide'))})()");
      assertEquals("结束Activity前必须完成原歌曲持久化恢复", "true",js("JSON.parse(localStorage.getItem('music-store')||'null')?.playSong?.id===fixturePinia._s.get('music').playSong.id"));
      assertEquals("测试结束后音源配置必须完整持久化恢复", "true", js("JSON.stringify(JSON.parse(localStorage.getItem('setting-store')).songUnlockServer)===JSON.stringify("+new org.json.JSONObject(savedSettings).getJSONArray("songUnlockServer")+")"));
    } finally { restoreNativeApiContext();inst.runOnMainSync(() -> {web.getSettings().setTextZoom(100); activity.finish();}); }
    }
  }
}

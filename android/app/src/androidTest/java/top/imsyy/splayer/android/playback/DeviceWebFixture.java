package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import top.imsyy.splayer.android.MainActivity;

/** 只在隔离包准备 Vue 页面，不修改真实账号或清空数据。 */
final class DeviceWebFixture implements AutoCloseable {
  final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  final MainActivity activity;
  final WebView web;
  final String savedSettings;
  DeviceWebFixture() throws Exception {
    String pkg = inst.getTargetContext().getPackageName();
    assertTrue(pkg.endsWith(".phase1verify") || pkg.endsWith(".debug"));
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView();
    await("!!document.querySelector('#app')?.__vue_app__");
    // Vue 挂载早于异步数据加载和启动恢复；先等恢复播放任务收尾。
    await("window.__SPLAYER_PLAYER_CONTROLLER__?.currentRequestToken>0&&!Reflect.ownKeys(document.querySelector('#app').__vue_app__._context.provides).map(k=>document.querySelector('#app').__vue_app__._context.provides[k]).find(v=>v?._s instanceof Map)._s.get('status').playLoading");
    js("(()=>{const a=document.querySelector('#app').__vue_app__;window.fixtureRouter=a.config.globalProperties.$router;window.fixturePinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.fixtureStatus=fixturePinia._s.get('status');window.fixtureSetting=fixturePinia._s.get('setting');window.fixtureSaved={agreement:fixtureSetting.userAgreementVersion,update:fixtureSetting.checkUpdateOnStart,show:fixtureStatus.showFullPlayer,hiddenCovers:JSON.parse(JSON.stringify(fixtureSetting.hiddenCovers))};fixtureSetting.userAgreementVersion='v2.0';fixtureSetting.checkUpdateOnStart=false;fixtureStatus.showFullPlayer=false;window.$modal?.destroyAll();})()");
    savedSettings = (String) new org.json.JSONTokener(js("JSON.stringify(fixtureSaved)")).nextValue();
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
    fail("页面条件未满足: " + expression + "; result=" + js(expression));
  }
  void capture(String name) throws Exception {
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
    js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map),s=p._s.get('setting'),t=p._s.get('status'),saved="+savedSettings+";s.userAgreementVersion=saved.agreement;s.checkUpdateOnStart=saved.update;s.hiddenCovers=saved.hiddenCovers;t.showFullPlayer=saved.show;})()");
    inst.runOnMainSync(() -> {web.getSettings().setTextZoom(100); activity.finish();});
  }
}

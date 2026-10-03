package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
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

/** 用实际 WebView 测量系统字体与可变窗口，正式包数据不参与测试。 */
@RunWith(AndroidJUnit4.class)
public class AdaptiveWindowDeviceTest {
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
      result[0] = value;
      done.countDown();
    }));
    assertTrue("WebView 回调超时", done.await(10, TimeUnit.SECONDS));
    return result[0];
  }

  @Test public void realFontsAndNarrowWindowUseSharedConfiguration() throws Exception {
    assertTrue(instrumentation.getTargetContext().getPackageName().endsWith(".lyricsverify"));
    String originalScale = shell("settings get system font_scale");
    MainActivity activity = null;
    WindowManager.LayoutParams originalWindow = new WindowManager.LayoutParams();
    boolean windowSaved = false;
    try {
      Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity = (MainActivity) instrumentation.startActivitySync(intent);
      MainActivity current = activity;
      WebView[] holder = new WebView[1];
      instrumentation.runOnMainSync(() -> {
        holder[0] = current.getBridge().getWebView();
        originalWindow.copyFrom(current.getWindow().getAttributes());
      });
      windowSaved = true;
      WebView web = holder[0];
      boolean mounted = false;
      for (int i = 0; i < 100; i++) {
        if ("true".equals(js(web, "!!document.querySelector('.app-shell')"))) {
          mounted = true;
          break;
        }
        SystemClock.sleep(200);
      }
      assertTrue("实际 Vue 页面必须挂载", mounted);
      assertEquals("根节点 token 必须覆盖 body 弹层", "\"48px\"", js(web, "getComputedStyle(document.documentElement).getPropertyValue('--adaptive-touch-target').trim()"));
      js(web, "(()=>{const e=document.createElement('span');e.id='adaptive-font-probe';e.textContent='M汉あ';e.style.cssText='position:fixed;top:0;left:0;font-size:var(--adaptive-font-title);line-height:normal;white-space:nowrap;pointer-events:none';document.body.appendChild(e)})()");
      double baseHeight = 0;
      for (double scale : new double[]{1, 1.15, 1.3, 1.5, 2}) {
        shell("settings put system font_scale " + scale);
        JSONObject g = null;
        for (int i = 0; i < 50; i++) {
          String raw = js(web, "JSON.stringify((()=>{const s=document.querySelector('.app-shell'),p=document.querySelector('#adaptive-font-probe');return {scale:Number(s?.dataset.systemFontScale),height:p?.getBoundingClientRect().height,font:getComputedStyle(p).fontSize,width:innerWidth,widthClass:s?.dataset.windowWidthClass,heightClass:s?.dataset.windowHeightClass,shell:s?.className}})())");
          g = new JSONObject((String) new JSONTokener(raw).nextValue());
          if (Math.abs(g.getDouble("scale") - scale) < 0.02) break;
          SystemClock.sleep(200);
        }
        assertNotNull(g);
        assertEquals("前端必须接收到真实字号变化", scale, g.getDouble("scale"), 0.02);
        int[] zoom = new int[1];
        instrumentation.runOnMainSync(() -> zoom[0] = web.getSettings().getTextZoom());
        assertEquals("WebView 只执行一次系统缩放", (int) Math.round(scale * 100), zoom[0]);
        SystemClock.sleep(300);
        double actualHeight = Double.parseDouble(js(web, "document.querySelector('#adaptive-font-probe').getBoundingClientRect().height"));
        if (scale == 1) baseHeight = actualHeight;
        else assertTrue("网页文字必须实际增大", actualHeight > baseHeight * (scale - 0.12));
        g.put("measuredHeight", actualHeight);
        g.put("nativeTextZoom", zoom[0]);
        Log.i("AdaptiveLayoutVerify", g.toString());
        Bundle measurement = new Bundle();
        measurement.putString("adaptiveMeasurement", g.toString());
        instrumentation.sendStatus(0, measurement);
      }
      // 缩小真实 Activity 窗口；无需伪造 UA 或物理屏幕身份
      double initialWidth = Double.parseDouble(js(web, "innerWidth"));
      instrumentation.runOnMainSync(() -> current.getWindow().setLayout(
          (int) Math.round(Math.min(400, initialWidth * 0.65) * current.getResources().getDisplayMetrics().density),
          WindowManager.LayoutParams.MATCH_PARENT));
      boolean compact = false;
      for (int i = 0; i < 50; i++) {
        compact = "true".equals(js(web, "innerWidth<600 && document.querySelector('.app-shell')?.dataset.windowWidthClass==='compact' && document.querySelector('.app-shell')?.classList.contains('app-shell--phone')"));
        if (compact) break;
        SystemClock.sleep(200);
      }
      assertTrue("真实窄窗口必须回退紧凑单栏", compact);
      double narrowedWidth = Double.parseDouble(js(web, "innerWidth"));
      assertTrue("实际窗口宽度必须缩小", narrowedWidth < initialWidth - 1);
      String narrowResult = js(web, "JSON.stringify({narrowWidth:innerWidth,height:innerHeight,shell:document.querySelector('.app-shell').className})");
      Log.i("AdaptiveLayoutVerify", narrowResult);
      Bundle measurement = new Bundle();
      measurement.putString("adaptiveNarrowWindow", narrowResult);
      instrumentation.sendStatus(0, measurement);
    } finally {
      if (originalScale.equals("null")) shell("settings delete system font_scale");
      else shell("settings put system font_scale " + originalScale);
      if (activity != null) {
        MainActivity current = activity;
        boolean restoreWindow = windowSaved;
        instrumentation.runOnMainSync(() -> {
          if (restoreWindow) current.getWindow().setAttributes(originalWindow);
          current.finish();
        });
      }
    }
  }
}

package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;

/** 不依赖 WebView 进度回传，使用真实 ExoPlayer 验证后台歌词时钟。 */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricBackgroundTest {
  private final android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private void main(Runnable action) { inst.runOnMainSync(action); }
  private Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
  private void awaitPlaying(FloatingLyricService service, boolean expected) throws Exception {
    long deadline = SystemClock.uptimeMillis() + 5000;
    boolean[] value = new boolean[1];
    do {
      main(() -> value[0] = service.playing);
      if (value[0] == expected) return;
      SystemClock.sleep(50);
    } while (SystemClock.uptimeMillis() < deadline);
    assertEquals("原生播放状态必须直接同步到歌词", expected, value[0]);
  }
  @Test public void backgroundClockIgnoresStaleWebViewAndSurvivesServiceRestart() throws Exception {
    Context context = inst.getTargetContext();
    assertTrue("只能运行独立测试包", context.getPackageName().endsWith(".lyricsverify")
        || context.getPackageName().endsWith(".phase1verify") || context.getPackageName().endsWith(".debug"));
    Intent launch = new Intent(context, top.imsyy.splayer.android.MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    top.imsyy.splayer.android.MainActivity activity =
        (top.imsyy.splayer.android.MainActivity) inst.startActivitySync(launch);
    // 此测试只测原生后台链路，移除 JS 页面以防启动恢复操作与测试音频竞争。
    main(() -> { activity.getBridge().getWebView().stopLoading();
      activity.getBridge().getWebView().loadUrl("about:blank"); });
    SystemClock.sleep(500);
    File audio = new File(context.getCacheDir(), "background-clock.wav");
    int bytes = 60 * 16000 * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(bytes + 36);
    header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1);
    header.putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
    header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
    try (FileOutputStream stream = new FileOutputStream(audio)) {
      stream.write(header.array());
      stream.write(new byte[bytes]);
    }
    PlaybackManager manager = PlaybackManager.getInstance(context);
    try {
      main(() -> { manager.load(audio.toURI().toString(), 0, true); manager.showFloatingLyric(); });
      FloatingLyricService service = null;
      for (int i = 0; i < 50 && service == null; i++) {
        SystemClock.sleep(100);
        service = (FloatingLyricService) field(manager, "floatingLyricService");
      }
      assertNotNull("悬浮窗必须创建", service);
      final FloatingLyricService overlay = service;
      JSONArray lines = new JSONArray();
      for (int i = 0; i < 60; i++) {
        lines.put(new JSONObject().put("startTime", i * 1000).put("endTime", (i + 1) * 1000)
            .put("words", new JSONArray().put(new JSONObject().put("word", "后台歌词 " + i)
                .put("startTime", i * 1000).put("endTime", (i + 1) * 1000))));
      }
      main(() -> manager.updateFloatingLyricData(lines.toString(), "[]"));
      awaitPlaying(overlay, true);
      // Home 后不再发送任何 JS 进度，模拟 WebView 被暂停。
      inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
      long[] first = new long[1];
      main(() -> first[0] = overlay.baseMs);
      SystemClock.sleep(2000);
      main(() -> assertTrue("后台原生位置必须持续更新", overlay.baseMs > first[0] + 1000));
      android.view.View lyricView = (android.view.View) field(overlay, "view");
      assertTrue("真实悬浮窗绘制必须推进当前歌词行", (int) field(lyricView, "lastIndex") >= 1);
      File evidence = new File(context.getExternalFilesDir(null), "background-lyrics");
      assertTrue(evidence.exists() || evidence.mkdirs());
      main(() -> {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(lyricView.getWidth(),
            lyricView.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        lyricView.draw(new android.graphics.Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(evidence, "background-overlay.png"))) {
          assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
      });
      main(() -> manager.updateFloatingLyricProgress(0, false));
      main(() -> { assertTrue("旧 JS 暂停消息不得冻结歌词", overlay.playing); assertTrue(overlay.baseMs > 1000); });
      main(manager::pause);
      awaitPlaying(overlay, false);
      // 等音频线程提交最终暂停位置，再验证冻结；不把最后几毫秒提交当成播放。
      SystemClock.sleep(300);
      main(() -> first[0] = overlay.seekMs());
      SystemClock.sleep(600);
      main(() -> assertEquals("暂停时歌词停止", first[0], overlay.seekMs()));
      main(() -> manager.seek(20000));
      main(() -> assertEquals("跳转即时更新歌词", 20300L, overlay.seekMs(), 300L));
      main(manager::play);
      awaitPlaying(overlay, true);
      main(() -> context.stopService(new Intent(context, FloatingLyricService.class)));
      for (int i = 0; i < 50 && field(manager, "floatingLyricService") != null; i++) SystemClock.sleep(50);
      assertNull("旧悬浮服务必须销毁", field(manager, "floatingLyricService"));
      main(manager::showFloatingLyric);
      FloatingLyricService restored = null;
      for (int i = 0; i < 50 && restored == null; i++) {
        SystemClock.sleep(50);
        restored = (FloatingLyricService) field(manager, "floatingLyricService");
      }
      assertNotNull("服务重建必须成功", restored);
      final FloatingLyricService recovered = restored;
      awaitPlaying(recovered, true);
      main(() -> { assertTrue(recovered.baseMs >= 20000); assertEquals(60, recovered.lrcLines.size()); });
    } finally {
      main(() -> { manager.cleanup(); manager.hideFloatingLyric(); });
      assertTrue(audio.delete());
    }
  }
}

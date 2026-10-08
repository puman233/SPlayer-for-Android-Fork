package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 只允许独立验证包运行，避免改动已有应用的设置 */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricOverlayTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private Context context;
  private FloatingLyricService service;

  private String shell(String command) throws Exception {
    try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(command);
        FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
  }

  private Object field(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(object);
  }

  private void main(Runnable action) { instrumentation.runOnMainSync(action); }

  private void awaitControlsAlpha(float expected) throws Exception {
    long deadline = SystemClock.uptimeMillis() + 2000;
    do {
      // 等主线程处理当前帧，避免模拟器负载造成固定 sleep 的误判
      main(() -> {});
      if (Math.abs((float) field(service, "controlsAlpha") - expected) < 0.001f) return;
      SystemClock.sleep(30);
    } while (SystemClock.uptimeMillis() < deadline);
    assertEquals(expected, (float) field(service, "controlsAlpha"), 0.001f);
  }

  private void tap(View view, float x, float y) throws Exception {
    int[] location = new int[2];
    int[] display = new int[1];
    main(() -> {
      view.getLocationOnScreen(location);
      display[0] = view.getDisplay().getDisplayId();
    });
    // 通过系统输入路由，验证悬浮窗真实接收触碰
    shell("input -d " + display[0] + " tap " + Math.round(location[0] + x)
        + " " + Math.round(location[1] + y));
    main(() -> {});
  }

  private void assertAutoFade() throws Exception {
    assertEquals(2500L, field(service, "HIDE_DELAY_MS"));
    SystemClock.sleep(1700);
    assertEquals("超时前不能提前隐藏", 1f, (float) field(service, "controlsAlpha"), 0.001f);
    boolean fading = false;
    long deadline = SystemClock.uptimeMillis() + 1400;
    while (SystemClock.uptimeMillis() < deadline) {
      main(() -> {});
      float alpha = (float) field(service, "controlsAlpha");
      if (alpha > 0 && alpha < 1) fading = true;
      if (alpha == 0) break;
      SystemClock.sleep(20);
    }
    assertTrue("应观察到渐隐动画而非直接消失", fading);
    assertEquals("2.5 秒超时与动画结束后完全透明", 0f,
        (float) field(service, "controlsAlpha"), 0.001f);
  }

  private Bitmap capture(View view) {
    Bitmap[] result = new Bitmap[1];
    main(() -> {
      result[0] = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
      view.draw(new Canvas(result[0]));
    });
    return result[0];
  }
  private void draw(View view) { capture(view).recycle(); }

  private void saveScreen(String name, View view) throws Exception {
    File directory = new File(context.getExternalFilesDir(null), "phase3");
    assertTrue(directory.exists() || directory.mkdirs());
    String label = InstrumentationRegistry.getArguments().getString("label", "device");
    int[] display = new int[1];
    main(() -> display[0] = view.getDisplay().getDisplayId());
    java.util.regex.Matcher physical = java.util.regex.Pattern.compile(
        "DisplayViewport\\{[^}]*displayId=" + display[0] + ", uniqueId='local:([0-9]+)'")
        .matcher(shell("dumpsys display"));
    Bitmap screen;
    if (physical.find()) {
      try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(
          "screencap -p -d " + physical.group(1)); FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
        byte[] png = stream.readAllBytes();
        screen = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
      }
    } else {
      assertEquals("无法确定非默认显示面的实际截图来源", 0, display[0]);
      screen = instrumentation.getUiAutomation().takeScreenshot();
    }
    assertNotNull("必须取得实际窗口截图", screen);
    try (FileOutputStream output = new FileOutputStream(new File(directory, label + "-" + name + ".png"))) {
      assertTrue(screen.compress(Bitmap.CompressFormat.PNG, 100, output));
    } finally { screen.recycle(); }
    Bitmap overlay = capture(view);
    try (FileOutputStream output = new FileOutputStream(new File(directory, label + "-" + name + "-overlay.png"))) {
      assertTrue(overlay.compress(Bitmap.CompressFormat.PNG, 100, output));
    } finally { overlay.recycle(); }
  }

  private void assertBackground(View view, boolean visible) {
    Bitmap bitmap = capture(view);
    int alpha = android.graphics.Color.alpha(bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() - 2));
    bitmap.recycle();
    if (visible) assertTrue("展开后背景可见", alpha > 0);
    else assertEquals("待机和锁定背景必须完全透明", 0, alpha);
  }

  @Test public void overlayKeepsFontLocksCleanlyAndRestoresPreferences() throws Exception {
    context = instrumentation.getTargetContext();
    assertTrue("必须使用隔离验证包", context.getPackageName().endsWith(".lyricsverify")
        || context.getPackageName().endsWith(".phase1verify") || context.getPackageName().endsWith(".debug"));
    Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    SystemClock.sleep(2000);
    android.content.SharedPreferences preferences = context.getSharedPreferences("floating_lyric_prefs", Context.MODE_PRIVATE);
    java.util.Map<String, ?> savedPreferences = new java.util.HashMap<>(preferences.getAll());
    main(() -> preferences.edit().clear().commit());
    PlaybackManager manager = PlaybackManager.getInstance(context);
    try {
      main(manager::showFloatingLyric);
      for (int i = 0; i < 40; i++) {
        service = (FloatingLyricService) field(manager, "floatingLyricService");
        if (service != null) break;
        SystemClock.sleep(100);
      }
      assertNotNull(service);
      assertTrue((boolean) field(service, "attached"));
      assertEquals(FloatingLyricPolicy.DEFAULT_COLOR, service.colorPlayed);
      View view = (View) field(service, "view");
      JSONObject auto = new JSONObject().put("fontSizeMode", "AUTO_DEFAULT").put("isDoubleLine", true)
          .put("textBackgroundMask", true);
      main(() -> manager.updateFloatingLyricConfig(auto));
      String sampleJson = new org.json.JSONArray().put(new JSONObject().put("startTime", 0).put("endTime", 20000)
          .put("translatedLyric", "如此 永不改变").put("words", new org.json.JSONArray().put(
              new JSONObject().put("word", "変わらない このままだよ").put("startTime", 0).put("endTime", 20000)))).toString();
      main(() -> { service.pushLyrics(sampleJson, "[]"); service.pushProgress(0, false); });
      assertBackground(view, false);
      saveScreen("idle", view);
      tap(view, view.getWidth() / 2f, view.getHeight() / 2f);
      awaitControlsAlpha(1f);
      assertBackground(view, true);
      saveScreen("controls", view);
      tap(view, view.getWidth() / 2f, view.getHeight() - 10);
      awaitControlsAlpha(0f);
      assertBackground(view, false);
      saveScreen("touch-hidden", view);
      tap(view, view.getWidth() / 2f, view.getHeight() / 2f);
      awaitControlsAlpha(1f);
      assertBackground(view, true);
      draw(view);
      android.graphics.RectF play = (android.graphics.RectF) field(service, "rPlay");
      tap(view, play.centerX(), play.centerY());
      assertTrue("播放按钮不能被普通触碰切换误隐藏", ((FloatingLyricInteraction) field(service, "interaction")).controls());
      assertAutoFade();
      assertBackground(view, false);
      saveScreen("timeout", view);
      String[] samples = {"光", "光に溢れて　陰に居場所がない",
          "这是一条非常非常非常非常非常非常非常非常非常长的歌词，用于测试桌面歌词横向滚动是否可以稳定完整显示",
          "This is an intentionally very long lyric line used for verifying smooth marquee scrolling without dynamically shrinking the font size.",
          "光に溢れて世界はまだ続いている何度も何度も同じ空の下で歌い続ける長い長い歌詞の表示を確認します",
          "🎵 光に溢れて 世界はまだ続いている ✨", "مرحبا بالعالم هذه كلمات طويلة لاختبار الاتجاه"};
      float size = (float) field(service, "fontPx");
      float autoSp = size / service.getResources().getDisplayMetrics().scaledDensity;
      assertTrue("自动字号应在范围内", autoSp >= 16 && autoSp <= 32);
      Log.i("FloatingLyricVerify", "autoSp=" + autoSp + ", fontScale="
          + service.getResources().getConfiguration().fontScale + ", smallestWidth="
          + service.getResources().getConfiguration().smallestScreenWidthDp);
      for (String sample : samples) {
        String json = new org.json.JSONArray().put(new JSONObject().put("startTime", 0).put("endTime", 20000)
            .put("translatedLyric", sample).put("words", new org.json.JSONArray().put(
                new JSONObject().put("word", sample).put("startTime", 0).put("endTime", 20000)))).toString();
        main(() -> { service.pushLyrics(json, json); service.pushProgress(0, false); });
        draw(view);
        assertEquals(size, (float) field(service, "fontPx"), 0.001f);
        main(() -> service.pushProgress(8000, false));
        draw(view);
      }
      String scrollingJson = new org.json.JSONArray().put(new JSONObject().put("startTime", 0)
          .put("endTime", 20000).put("words", new org.json.JSONArray().put(
              new JSONObject().put("word", samples[3]).put("startTime", 0).put("endTime", 20000)))).toString();
      main(() -> { service.pushLyrics(scrollingJson, "[]"); service.pushProgress(0, true); });
      Bitmap initial = capture(view);
      SystemClock.sleep(2200);
      Bitmap scrolled = capture(view);
      assertFalse("长句应保持字号并改变内容偏移", initial.sameAs(scrolled));
      main(() -> service.pushProgress(service.seekMs() - 300, false));
      Bitmap paused = capture(view);
      SystemClock.sleep(300);
      Bitmap pausedAgain = capture(view);
      assertTrue("暂停后滚动应冻结", paused.sameAs(pausedAgain));
      main(() -> service.pushProgress(0, false));
      Bitmap reset = capture(view);
      assertTrue("seek 后从起点重置", initial.sameAs(reset));
      initial.recycle(); scrolled.recycle(); paused.recycle(); pausedAgain.recycle(); reset.recycle();
      tap(view, view.getWidth() / 2f, view.getHeight() / 2f);
      awaitControlsAlpha(1f);
      draw(view);
      android.graphics.RectF lock = (android.graphics.RectF) field(service, "rLock");
      if (service.tabletMode) {
        main(() -> service.pushSongInfo("平板歌名🎵", "平板歌手"));
        Bitmap titleA = capture(view);
        main(() -> service.pushSongInfo("另一首歌🎵", "另一位歌手"));
        Bitmap titleB = capture(view);
        Bitmap headerA = Bitmap.createBitmap(titleA, 0, 0, titleA.getWidth(), Math.round(lock.top));
        Bitmap headerB = Bitmap.createBitmap(titleB, 0, 0, titleB.getWidth(), Math.round(lock.top));
        assertFalse("平板控制栏必须保留歌曲信息", headerA.sameAs(headerB));
        assertTrue("信息行不能覆盖锁定按钮", lock.top >= 32 * service.getResources().getDisplayMetrics().density);
        titleA.recycle(); titleB.recycle();
        headerA.recycle(); headerB.recycle();
      }
      tap(view, lock.centerX(), lock.centerY());
      awaitControlsAlpha(0f);
      FloatingLyricInteraction interaction = (FloatingLyricInteraction) field(service, "interaction");
      assertTrue(interaction.locked());
      assertTrue((boolean) field(service, "unlockAttached"));
      assertBackground(view, false);
      WindowManager.LayoutParams lockedLayout = (WindowManager.LayoutParams) field(service, "lp");
      int lockedX = lockedLayout.x, lockedY = lockedLayout.y;
      tap(view, view.getWidth() / 2f, view.getHeight() / 2f);
      assertTrue("锁定时普通触摸不能展开", interaction.locked());
      assertEquals(lockedX, lockedLayout.x);
      assertEquals(lockedY, lockedLayout.y);
      saveScreen("locked", view);
      View unlock = (View) field(service, "unlockBtnView");
      tap(unlock, unlock.getWidth() / 2f, unlock.getHeight() / 2f);
      awaitControlsAlpha(1f);
      assertFalse(interaction.locked());
      assertFalse((boolean) field(service, "unlockAttached"));
      assertAutoFade();
      assertFalse(interaction.controls());
      assertBackground(view, false);
      JSONObject user = new JSONObject().put("fontSizeMode", "USER_DEFINED").put("fontSize", 30)
          .put("playedColor", "#123456");
      main(() -> manager.updateFloatingLyricConfig(user));
      String oldRotation = shell("settings get system user_rotation");
      String oldAutoRotation = shell("settings get system accelerometer_rotation");
      try {
        shell("settings put system accelerometer_rotation 0");
        Rect previousSafe = null;
        for (int rotation : new int[] {1, 0, 1, 0}) {
          shell("settings put system user_rotation " + rotation);
          SystemClock.sleep(900);
          WindowManager.LayoutParams rotated = (WindowManager.LayoutParams) field(service, "lp");
          Rect rotatedSafe = (Rect) field(service, "safeArea");
          if (previousSafe != null) assertFalse("旋转必须改变实际安全区域，不能只检查命令成功",
              previousSafe.equals(rotatedSafe));
          previousSafe = new Rect(rotatedSafe);
          int[] actual = new int[2];
          main(() -> view.getLocationOnScreen(actual));
          assertTrue("实际窗口左边界", actual[0] >= rotatedSafe.left);
          assertTrue("实际窗口上边界", actual[1] >= rotatedSafe.top);
          assertTrue("实际窗口右边界", actual[0] + view.getWidth() <= rotatedSafe.right);
          assertTrue("实际窗口下边界", actual[1] + view.getHeight() <= rotatedSafe.bottom);
          Log.i("FloatingLyricVerify", "rotation=" + rotation + ", safe=" + rotatedSafe
              + ", actual=" + actual[0] + "," + actual[1] + ", size="
              + view.getWidth() + "x" + view.getHeight());
          assertTrue(rotated.x >= rotatedSafe.left && rotated.x + rotated.width <= rotatedSafe.right);
          assertTrue(rotated.y >= rotatedSafe.top && rotated.y + rotated.height <= rotatedSafe.bottom);
          assertEquals("USER_DEFINED", service.fontSizeMode);
          assertEquals(30f, service.fontSizeSp, 0);
          assertSame(service, field(manager, "floatingLyricService"));
          saveScreen("rotation-" + rotation, view);
        }
      } finally {
        shell("settings put system user_rotation " + oldRotation);
        shell("settings put system accelerometer_rotation " + oldAutoRotation);
      }
      main(manager::showFloatingLyric);
      assertSame(service, field(manager, "floatingLyricService"));
      WindowManager.LayoutParams lp = (WindowManager.LayoutParams) field(service, "lp");
      Rect safe = (Rect) field(service, "safeArea");
      assertTrue(lp.x >= safe.left && lp.x + lp.width <= safe.right);
      assertTrue(lp.y >= safe.top && lp.y + lp.height <= safe.bottom);
      main(manager::hideFloatingLyric);
      SystemClock.sleep(350);
      assertFalse((boolean) field(service, "attached"));
      main(manager::showFloatingLyric);
      SystemClock.sleep(350);
      service = (FloatingLyricService) field(manager, "floatingLyricService");
      assertNotNull(service);
      assertEquals("USER_DEFINED", service.fontSizeMode);
      assertEquals(30f, service.fontSizeSp, 0);
      assertEquals(0xFF123456, service.colorPlayed);
    } finally {
      main(manager::hideFloatingLyric);
      main(() -> {
        android.content.SharedPreferences.Editor restore = preferences.edit().clear();
        for (java.util.Map.Entry<String, ?> entry : savedPreferences.entrySet()) {
          String key = entry.getKey(); Object value = entry.getValue();
          if (value instanceof String) restore.putString(key, (String) value);
          else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
          else if (value instanceof Integer) restore.putInt(key, (Integer) value);
          else if (value instanceof Long) restore.putLong(key, (Long) value);
          else if (value instanceof Float) restore.putFloat(key, (Float) value);
          else if (value instanceof java.util.Set) {
            java.util.Set<String> strings = new java.util.HashSet<>();
            for (Object item : (java.util.Set<?>) value) strings.add((String) item);
            restore.putStringSet(key, strings);
          }
        }
        assertTrue("恢复验证前的桌面歌词偏好", restore.commit());
      });
    }
  }
}

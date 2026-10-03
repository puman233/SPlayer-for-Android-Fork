package top.imsyy.splayer.android.playback;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import androidx.core.content.res.ResourcesCompat;
import java.util.Locale;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import top.imsyy.splayer.android.R;

/** 原生悬浮歌词窗口，不修改播放器的歌词时序 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
// 窗口固定屏幕左上原点，文本方向独立处理
@SuppressLint("RtlHardcoded")
public class FloatingLyricService extends Service {
  private static final String TAG = "FloatingLyric";
  private static final String PREFS = "floating_lyric_prefs";
  private static final long HIDE_DELAY_MS = 4000;
  private WindowManager wm;
  private LyricView view;
  private WindowManager.LayoutParams lp, unlockLp;
  private View unlockBtnView;
  private SharedPreferences prefs;
  private boolean attached, unlockAttached, destroyed, positionReady;
  private final Rect safeArea = new Rect();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final FloatingLyricInteraction interaction = new FloatingLyricInteraction();
  private ValueAnimator controlsAnimator;
  private float controlsAlpha;
  private float normalizedX = 0.5f, normalizedY = 0.3f;
  private int idleHeight, controlHeight;
  private float fontPx;
  private final Runnable autoHide = () -> {
    interaction.hide();
    animateControls();
  };

  List<Line> lrcLines = new ArrayList<>(), yrcLines = new ArrayList<>();
  String songName = "", artistName = "";
  long baseMs, anchorNano = System.nanoTime();
  boolean playing;
  int colorPlayed = FloatingLyricPolicy.DEFAULT_COLOR;
  int colorUnplayed = 0xFFCCCCCC, colorShadow = 0x80000000;
  float fontSizeSp = 24f;
  String fontSizeMode = "AUTO_DEFAULT";
  int fontWeight = 400;
  boolean wordMode = true, showTran = true, doubleLine = true, animation = true;
  boolean textBackgroundMask;
  int backgroundMaskColor = 0x80000000;
  String alignPosition = "both";
  int windowWidthPercent = 84, windowHeightDp = 72;
  boolean tabletMode;
  private float tX0, tY0;
  private int wX0, wY0;
  private boolean controlsAtDown;
  private final RectF rLock = new RectF(), rPrev = new RectF(), rPlay = new RectF(),
      rNext = new RectF(), rClose = new RectF(), rFavorite = new RectF();
  private static final int ICON_MUSIC = 1, ICON_PREV = 2, ICON_PLAY = 3, ICON_PAUSE = 4,
      ICON_NEXT = 5, ICON_HEART = 6, ICON_HEART_FILLED = 7, ICON_LOCK = 8, ICON_UNLOCK = 9,
      ICON_CLOSE = 10;
  private static final int COLOR_HEART_LIKED = 0xFFE0446A;
  private final Map<Integer, Drawable> iconCache = new HashMap<>();

  @Override
  public void onCreate() {
    super.onCreate();
    prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    restoreConfigFromPrefs();
    if (prefs.getBoolean("locked", false)) interaction.lock();
    wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    view = new LyricView(this);
    lp = new WindowManager.LayoutParams(1, 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.LEFT;
    if (interaction.locked()) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    view.setOnApplyWindowInsetsListener((v, insets) -> {
      refreshViewport(insets);
      return insets;
    });
    refreshViewport(null);
    try {
      wm.addView(view, lp);
      attached = true;
      view.requestApplyInsets();
      Log.i(TAG, "overlay create");
    } catch (SecurityException | IllegalArgumentException | WindowManager.BadTokenException e) {
      Log.e(TAG, "悬浮歌词窗口创建失败", e);
      stopSelf();
      return;
    }
    if (interaction.locked()) showUnlockBtn();
    PlaybackManager.getInstance(this).attachFloatingLyricService(this);
  }

  private void restoreConfigFromPrefs() {
    colorPlayed = prefs.getInt("colorPlayed", colorPlayed);
    colorUnplayed = prefs.getInt("colorUnplayed", colorUnplayed);
    colorShadow = prefs.getInt("colorShadow", colorShadow);
    backgroundMaskColor = prefs.getInt("backgroundMaskColor", backgroundMaskColor);
    fontSizeSp = prefs.getFloat("fontSizeSp", fontSizeSp);
    fontSizeMode = prefs.getString("fontSizeMode", prefs.contains("fontSizeSp") ? "USER_DEFINED" : "AUTO_DEFAULT");
    fontWeight = prefs.getInt("fontWeight", fontWeight);
    wordMode = prefs.getBoolean("wordMode", wordMode);
    showTran = prefs.getBoolean("showTran", showTran);
    doubleLine = prefs.getBoolean("doubleLine", doubleLine);
    animation = prefs.getBoolean("animation", animation);
    textBackgroundMask = prefs.getBoolean("textBackgroundMask", textBackgroundMask);
    windowWidthPercent = prefs.getInt("windowWidthPercent", windowWidthPercent);
    windowHeightDp = prefs.getInt("windowHeightDp", windowHeightDp);
    alignPosition = prefs.getString("alignPosition", alignPosition);
  }

  private void persistConfigToPrefs() {
    prefs.edit().putInt("colorPlayed", colorPlayed).putInt("colorUnplayed", colorUnplayed)
        .putInt("colorShadow", colorShadow).putInt("backgroundMaskColor", backgroundMaskColor)
        .putFloat("fontSizeSp", fontSizeSp).putString("fontSizeMode", fontSizeMode)
        .putInt("fontWeight", fontWeight).putBoolean("wordMode", wordMode)
        .putBoolean("showTran", showTran).putBoolean("doubleLine", doubleLine)
        .putBoolean("animation", animation).putBoolean("textBackgroundMask", textBackgroundMask)
        .putInt("windowWidthPercent", windowWidthPercent).putInt("windowHeightDp", windowHeightDp)
        .putString("alignPosition", alignPosition).apply();
  }

  @Override public int onStartCommand(Intent intent, int flags, int id) { return START_STICKY; }
  @Nullable @Override public IBinder onBind(Intent intent) { return null; }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    refreshViewport(null);
    if (view != null) view.requestApplyInsets();
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    handler.removeCallbacks(autoHide);
    if (controlsAnimator != null) controlsAnimator.cancel();
    PlaybackManager.getInstance(this).detachFloatingLyricService(this);
    removeUnlockBtn();
    if (attached) {
      try { wm.removeView(view); }
      catch (IllegalArgumentException e) { Log.w(TAG, "歌词窗口已移除", e); }
      attached = false;
    }
    view = null;
    iconCache.clear();
    Log.i(TAG, "overlay destroy");
    super.onDestroy();
  }

  /** 全部位置使用屏幕原点，安全区与主窗口、解锁窗口共用 */
  private void refreshViewport(@Nullable WindowInsets suppliedInsets) {
    if (destroyed || wm == null || lp == null) return;
    Rect bounds = new Rect();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowMetrics metrics = wm.getCurrentWindowMetrics();
      bounds.set(metrics.getBounds());
      android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
          WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
      bounds.left += insets.left;
      bounds.top += insets.top;
      bounds.right -= insets.right;
      bounds.bottom -= insets.bottom;
    } else {
      DisplayMetrics real = new DisplayMetrics();
      wm.getDefaultDisplay().getRealMetrics(real);
      bounds.set(0, 0, real.widthPixels, real.heightPixels);
      if (suppliedInsets != null) {
        bounds.left += suppliedInsets.getSystemWindowInsetLeft();
        bounds.top += suppliedInsets.getSystemWindowInsetTop();
        bounds.right -= suppliedInsets.getSystemWindowInsetRight();
        bounds.bottom -= suppliedInsets.getSystemWindowInsetBottom();
        if (suppliedInsets.getDisplayCutout() != null) {
          android.view.DisplayCutout cutout = suppliedInsets.getDisplayCutout();
          bounds.left = Math.max(bounds.left, cutout.getSafeInsetLeft());
          bounds.top = Math.max(bounds.top, cutout.getSafeInsetTop());
          bounds.right = Math.min(bounds.right, real.widthPixels - cutout.getSafeInsetRight());
          bounds.bottom = Math.min(bounds.bottom, real.heightPixels - cutout.getSafeInsetBottom());
        }
      }
    }
    if (bounds.width() <= 0 || bounds.height() <= 0) {
      Log.w(TAG, "歌词安全区域无效");
      return;
    }
    boolean changed = !safeArea.equals(bounds);
    safeArea.set(bounds);
    DisplayMetrics dm = getResources().getDisplayMetrics();
    float d = dm.density;
    tabletMode = getResources().getConfiguration().smallestScreenWidthDp >= 600;
    int width = Math.max(1, Math.round(bounds.width()
        * FloatingLyricPolicy.clamp(windowWidthPercent, 30, 100) / 100f));
    float fontScale = getResources().getConfiguration().fontScale;
    float requestedHeightDp = FloatingLyricPolicy.clamp(windowHeightDp, 48, 240);
    float effectiveSp = "USER_DEFINED".equals(fontSizeMode) ? fontSizeSp
        : FloatingLyricPolicy.defaultFont(bounds.width() / d, bounds.height() / d,
            width / d - 28, requestedHeightDp, fontScale, doubleLine);
    float newFontPx = effectiveSp * dm.scaledDensity;
    int buttonCount = tabletMode ? 6 : 5;
    int controlColumns = Math.max(1, width / Math.round(48 * d));
    int requestedControlHeight = (int) Math.ceil(buttonCount / (double) controlColumns) * Math.round(48 * d);
    int newIdleHeight = Math.min(Math.max(1, bounds.height() - requestedControlHeight), Math.round(Math.max(requestedHeightDp * d,
        newFontPx * (doubleLine ? 2.8f : 1.6f) + 16 * d)));
    int newControlHeight = Math.min(requestedControlHeight, Math.max(0, bounds.height() - newIdleHeight));
    changed |= lp.width != width || idleHeight != newIdleHeight || fontPx != newFontPx;
    fontPx = newFontPx;
    idleHeight = newIdleHeight;
    controlHeight = newControlHeight;
    lp.width = width;
    lp.height = Math.min(bounds.height(), idleHeight + Math.round(controlHeight * controlsAlpha));
    if (!positionReady) {
      normalizedX = prefs.contains("normalizedX") ? prefs.getFloat("normalizedX", 0.5f)
          : FloatingLyricPolicy.normalized(prefs.getInt("x", bounds.left + (bounds.width() - width) / 2),
              bounds.left, bounds.width(), width);
      normalizedY = prefs.contains("normalizedY") ? prefs.getFloat("normalizedY", 0.3f)
          : FloatingLyricPolicy.normalized(prefs.getInt("y", bounds.top + Math.round(bounds.height() * (tabletMode ? 0.7f : 0.3f))),
              bounds.top, bounds.height(), lp.height);
      positionReady = true;
      persistPosition();
    }
    projectPosition();
    if (changed && view != null) view.resetLayout();
    updateWindow();
    updateUnlockPosition();
  }

  private void projectPosition() {
    lp.x = FloatingLyricPolicy.project(normalizedX, safeArea.left, safeArea.width(), lp.width);
    lp.y = FloatingLyricPolicy.project(normalizedY, safeArea.top, safeArea.height(), idleHeight);
    lp.y = Math.min(lp.y, safeArea.bottom - lp.height);
  }

  private void persistPosition() {
    prefs.edit().putFloat("normalizedX", normalizedX).putFloat("normalizedY", normalizedY).apply();
  }

  private void updateWindow() {
    if (!attached || destroyed) return;
    try { wm.updateViewLayout(view, lp); }
    catch (IllegalArgumentException | SecurityException | WindowManager.BadTokenException e) { Log.w(TAG, "歌词窗口更新失败", e); }
  }

  public void pushLyrics(String lrcJson, String yrcJson) {
    lrcLines = parseLines(lrcJson);
    yrcLines = parseLines(yrcJson);
    if (view != null) view.resetLayout();
    postRedraw();
  }

  public void pushProgress(long ms, boolean isPlaying) {
    long previous = seekMs();
    baseMs = ms;
    anchorNano = System.nanoTime();
    playing = isPlaying;
    if (Math.abs(ms + 300 - previous) > 1200 && view != null) view.resetScroll();
    postRedraw();
  }

  public void pushSongInfo(String name, String artist) {
    String nextName = name == null ? "" : name, nextArtist = artist == null ? "" : artist;
    if ((!songName.equals(nextName) || !artistName.equals(nextArtist)) && view != null) view.resetLayout();
    songName = nextName;
    artistName = nextArtist;
    postRedraw();
  }

  public void applyConfig(JSONObject config) {
    if (config == null) return;
    Integer parsed = parseColor(config.opt("playedColor"));
    if (parsed != null) colorPlayed = parsed;
    parsed = parseColor(config.opt("unplayedColor")); if (parsed != null) colorUnplayed = parsed;
    parsed = parseColor(config.opt("shadowColor")); if (parsed != null) colorShadow = parsed;
    parsed = parseColor(config.opt("backgroundMaskColor")); if (parsed != null) backgroundMaskColor = parsed;
    if (config.has("fontSize")) {
      double value = config.optDouble("fontSize", fontSizeSp);
      if (Double.isFinite(value) && value > 0) fontSizeSp = FloatingLyricPolicy.clamp((float) value, 10, 96);
    }
    if (config.has("fontSizeMode")) {
      String mode = config.optString("fontSizeMode");
      if ("AUTO_DEFAULT".equals(mode) || "USER_DEFINED".equals(mode)) fontSizeMode = mode;
    } else if (config.has("fontSize")) fontSizeMode = "USER_DEFINED";
    fontWeight = (int) FloatingLyricPolicy.clamp(config.optInt("fontWeight", fontWeight), 100, 900);
    wordMode = config.optBoolean("showWordLyrics", wordMode);
    showTran = config.optBoolean("showTran", showTran);
    doubleLine = config.optBoolean("isDoubleLine", doubleLine);
    animation = config.optBoolean("animation", animation);
    textBackgroundMask = config.optBoolean("textBackgroundMask", textBackgroundMask);
    String position = config.optString("position", alignPosition);
    if ("left".equals(position) || "center".equals(position) || "right".equals(position) || "both".equals(position)) alignPosition = position;
    windowWidthPercent = (int) FloatingLyricPolicy.clamp(config.optInt("windowWidthPercent", windowWidthPercent), 30, 100);
    windowHeightDp = (int) FloatingLyricPolicy.clamp(config.optInt("windowHeightDp", windowHeightDp), 48, 240);
    persistConfigToPrefs();
    if (view != null) view.resetLayout();
    refreshViewport(view == null ? null : view.getRootWindowInsets());
    postRedraw();
  }

  private static Integer parseColor(Object raw) {
    if (!(raw instanceof String)) return null;
    String v = ((String) raw).trim();
    if (v.isEmpty()) return null;
    try {
      if (v.startsWith("#")) {
        return Color.parseColor(v);
      }
      String lower = v.toLowerCase(Locale.ROOT);
      if (lower.startsWith("rgba(") || lower.startsWith("rgb(")) {
        int lp = v.indexOf('('), rp = v.indexOf(')');
        if (lp < 0 || rp < 0) return null;
        String inner = v.substring(lp + 1, rp);
        String[] parts = inner.split(",");
        if (parts.length < 3) return null;
        int r = clamp255((int) Math.round(Double.parseDouble(parts[0].trim())));
        int g = clamp255((int) Math.round(Double.parseDouble(parts[1].trim())));
        int b = clamp255((int) Math.round(Double.parseDouble(parts[2].trim())));
        int a = 255;
        if (parts.length >= 4) {
          double af = Double.parseDouble(parts[3].trim());
          // 支持 0-1 的浮点透明度 或 0-255 的整型
          a = af <= 1.0 ? (int) Math.round(af * 255) : clamp255((int) Math.round(af));
        }
        return Color.argb(a, r, g, b);
      }
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "歌词颜色格式无效", e);
    }
    return null;
  }

  private static int clamp255(int v) {
    return Math.max(0, Math.min(255, v));
  }


  private void showControls() {
    if (interaction.locked() || destroyed) return;
    handler.removeCallbacks(autoHide);
    interaction.show();
    animateControls();
  }

  private void scheduleHide() {
    handler.removeCallbacks(autoHide);
    if (interaction.state() == FloatingLyricInteraction.State.CONTROLS_VISIBLE) {
      handler.postDelayed(autoHide, HIDE_DELAY_MS);
    }
  }

  private void animateControls() {
    if (destroyed || lp == null) return;
    float target = interaction.controls() ? 1f : 0f;
    if (controlsAnimator != null) controlsAnimator.cancel();
    if (controlsAlpha == target) return;
    controlsAnimator = ValueAnimator.ofFloat(controlsAlpha, target);
    controlsAnimator.setDuration(interaction.locked() ? 160 : 240);
    controlsAnimator.addUpdateListener(animator -> {
      controlsAlpha = (float) animator.getAnimatedValue();
      lp.height = Math.min(safeArea.height(), idleHeight + Math.round(controlHeight * controlsAlpha));
      projectPosition();
      updateWindow();
      updateUnlockPosition();
      postRedraw();
    });
    controlsAnimator.start();
  }

  public void setLocked(boolean locked) {
    handler.removeCallbacks(autoHide);
    if (locked) interaction.lock(); else interaction.unlock();
    prefs.edit().putBoolean("locked", locked).apply();
    if (lp == null) return;
    if (locked) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    else lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    updateWindow();
    if (locked) showUnlockBtn(); else removeUnlockBtn();
    animateControls();
    if (!locked) scheduleHide();
    postRedraw();
  }

  private void showUnlockBtn() {
    if (unlockBtnView != null || !attached) return;
    int size = Math.round(48 * getResources().getDisplayMetrics().density);
    unlockBtnView = new View(this) {
      private Drawable icon;
      @Override protected void onDraw(Canvas canvas) {
        if (icon == null) {
          icon = ResourcesCompat.getDrawable(getResources(), R.drawable.lyric_lock, null);
          if (icon != null) icon = icon.mutate();
        }
        if (icon == null) return;
        icon.setTint(Color.WHITE);
        int inset = Math.round(getWidth() * 0.21f);
        icon.setBounds(inset, inset, getWidth() - inset, getHeight() - inset);
        icon.draw(canvas);
      }
    };
    unlockBtnView.setContentDescription("解锁桌面歌词");
    unlockBtnView.setOnClickListener(v -> setLocked(false));
    unlockLp = new WindowManager.LayoutParams(size, size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
    unlockLp.gravity = Gravity.TOP | Gravity.LEFT;
    updateUnlockPosition();
    try {
      wm.addView(unlockBtnView, unlockLp);
      unlockAttached = true;
    } catch (SecurityException | IllegalArgumentException | WindowManager.BadTokenException e) {
      Log.e(TAG, "解锁窗口创建失败，恢复歌词触摸", e);
      unlockBtnView = null;
      setLocked(false);
    }
  }

  private void updateUnlockPosition() {
    if (unlockBtnView == null || unlockLp == null) return;
    unlockLp.x = Math.max(safeArea.left, Math.min(lp.x + lp.width - unlockLp.width,
        safeArea.right - unlockLp.width));
    unlockLp.y = Math.max(safeArea.top, Math.min(lp.y, safeArea.bottom - unlockLp.height));
    if (unlockAttached) {
      try { wm.updateViewLayout(unlockBtnView, unlockLp); }
      catch (IllegalArgumentException | SecurityException | WindowManager.BadTokenException e) { Log.w(TAG, "解锁窗口更新失败", e); }
    }
  }

  private void removeUnlockBtn() {
    if (unlockAttached) {
      try { wm.removeView(unlockBtnView); }
      catch (IllegalArgumentException e) { Log.w(TAG, "解锁窗口已移除", e); }
    }
    unlockAttached = false;
    unlockBtnView = null;
  }

  private void postRedraw() { if (!destroyed && view != null) view.postInvalidateOnAnimation(); }
  long seekMs() { return baseMs + 300 + (playing ? (System.nanoTime() - anchorNano) / 1_000_000L : 0); }
  List<Line> activeLines() { return wordMode && !yrcLines.isEmpty() ? yrcLines : lrcLines; }
  int findIndex(List<Line> lines, long ms) {
    int low = 0, high = lines.size() - 1, result = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (lines.get(mid).start <= ms) { result = mid; low = mid + 1; }
      else high = mid - 1;
    }
    return result;
  }

  private void onBtnTap(float x, float y) {
    if (interaction.locked()) return;
    PlaybackManager manager = PlaybackManager.getInstance(this);
    if (rLock.contains(x, y)) { setLocked(true); return; }
    if (rClose.contains(x, y)) { manager.hideFloatingLyric(); manager.emitDesktopLyricClosed(); return; }
    if (rFavorite.contains(x, y)) { manager.handleNotificationAction(PlaybackConstants.ACTION_FAVORITE); return; }
    if (rPrev.contains(x, y)) manager.handleNotificationAction(PlaybackConstants.ACTION_PREVIOUS);
    else if (rPlay.contains(x, y)) manager.handleNotificationAction(PlaybackConstants.ACTION_TOGGLE_PLAYBACK);
    else if (rNext.contains(x, y)) manager.handleNotificationAction(PlaybackConstants.ACTION_NEXT);
  }

  private static List<Line> parseLines(String json) {
    List<Line> r = new ArrayList<>();
    if (json == null || json.isEmpty()) return r;
    try {
      JSONArray a = new JSONArray(json);
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        Line l = new Line();
        l.start = o.optLong("startTime", 0);
        l.end = o.optLong("endTime", 0);
        l.tran = o.optString("translatedLyric", "");
        JSONArray wa = o.optJSONArray("words");
        if (wa != null)
          for (int j = 0; j < wa.length(); j++) {
            JSONObject wo = wa.getJSONObject(j);
            l.words.add(
                new Word(
                    wo.optString("word", ""),
                    wo.optLong("startTime", 0),
                    wo.optLong("endTime", 0)));
          }
        r.add(l);
      }
    } catch (Exception e) {
      Log.w(TAG, "parse", e);
    }
    return r;
  }


  private class LyricView extends View {
    private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinkedHashMap<String, FloatingLyricTextLayout> runs = new LinkedHashMap<String, FloatingLyricTextLayout>(24, 0.75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<String, FloatingLyricTextLayout> entry) { return size() > 24; }
    };
    private int lastIndex = -2;
    private long scrollStartMs;
    private long lineDurationMs;
    private float lineAlpha = 1f;
    private long lineSwitchNano;
    private final int dragSlop;

    LyricView(Context context) {
      super(context);
      dragSlop = ViewConfiguration.get(context).getScaledTouchSlop();
      setContentDescription("桌面歌词，点击显示控制，拖动调整位置");
    }
    void resetScroll() { lastIndex = -2; lineSwitchNano = 0; }
    void resetLayout() { runs.clear(); resetScroll(); }
    @Override public boolean performClick() { super.performClick(); showControls(); scheduleHide(); return true; }

    @Override public boolean onTouchEvent(MotionEvent event) {
      if (interaction.locked()) return false;
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          handler.removeCallbacks(autoHide);
          controlsAtDown = interaction.controls() && controlsAlpha > 0.95f;
          tX0 = event.getRawX(); tY0 = event.getRawY(); wX0 = lp.x; wY0 = lp.y;
          return true;
        case MotionEvent.ACTION_MOVE:
          float dx = event.getRawX() - tX0, dy = event.getRawY() - tY0;
          if (Math.hypot(dx, dy) > dragSlop) {
            interaction.drag();
            int x = Math.max(safeArea.left, Math.min(wX0 + Math.round(dx), safeArea.right - lp.width));
            int y = Math.max(safeArea.top, Math.min(wY0 + Math.round(dy), safeArea.bottom - lp.height));
            lp.x = x; lp.y = y;
            normalizedX = FloatingLyricPolicy.normalized(x, safeArea.left, safeArea.width(), lp.width);
            normalizedY = FloatingLyricPolicy.normalized(y, safeArea.top, safeArea.height(), idleHeight);
            updateWindow();
          }
          return true;
        case MotionEvent.ACTION_UP:
          if (interaction.state() == FloatingLyricInteraction.State.DRAGGING) {
            interaction.endDrag(); persistPosition(); animateControls();
          } else {
            if (controlsAtDown) onBtnTap(event.getX(), event.getY());
            // 按钮可能已经锁定或关闭，不能再反转状态
            if (!interaction.locked() && !destroyed) performClick();
          }
          scheduleHide();
          return true;
        case MotionEvent.ACTION_CANCEL:
          interaction.endDrag(); persistPosition(); animateControls(); scheduleHide();
          return true;
        default: return true;
      }
    }

    @Override protected void onDraw(Canvas canvas) {
      float d = getResources().getDisplayMetrics().density;
      int w = getWidth(), h = getHeight();
      if (textBackgroundMask) {
        bp.setColor(backgroundMaskColor);
        canvas.drawRoundRect(0, 0, w, h, 14 * d, 14 * d, bp);
      }
      if (controlsAlpha > 0) {
        bp.setColor(Color.argb(Math.round(190 * controlsAlpha), 30, 30, 46));
        canvas.drawRoundRect(0, 0, w, h, 14 * d, 14 * d, bp);
      }
      float header = Math.max(0, h - idleHeight);
      int lyricSave = canvas.save();
      canvas.clipRect(0, header, w, h);
      paintLyrics(canvas, w, header, h - header, d);
      canvas.restoreToCount(lyricSave);
      if (controlsAlpha > 0 && controlHeight > 0) {
        int save = canvas.saveLayerAlpha(0, 0, w, controlHeight, Math.round(255 * controlsAlpha));
        paintControls(canvas, w, controlHeight, d);
        canvas.restoreToCount(save);
      }
      boolean transition = animation && lineSwitchNano > 0
          && System.nanoTime() - lineSwitchNano < 240_000_000L;
      if (playing || transition) postInvalidateOnAnimation();
    }

    private void paintLyrics(Canvas canvas, int width, float top, float height, float d) {
      List<Line> lines = activeLines();
      long progress = seekMs();
      int index = findIndex(lines, progress);
      if (index != lastIndex) {
        lineSwitchNano = lastIndex >= 0 && animation ? System.nanoTime() : 0;
        lastIndex = index;
        scrollStartMs = progress;
        if (index >= 0) {
          long end = index + 1 < lines.size() ? lines.get(index + 1).start : lines.get(index).end;
          lineDurationMs = Math.max(0, end - progress);
        } else lineDurationMs = 0;
      }
      lineAlpha = lineSwitchNano == 0 || !animation ? 1f
          : Math.min(1f, (System.nanoTime() - lineSwitchNano) / 240_000_000f);
      if (index < 0) {
        drawRun(canvas, run(songName.isEmpty() ? "SPlayer" : songName + " - " + artistName, fontPx),
            width, top + height / 2, d, colorPlayed, null, progress, true);
        return;
      }
      Line line = lines.get(index);
      boolean translated = showTran && !line.tran.isEmpty();
      boolean hasSub = doubleLine && (translated || index + 1 < lines.size());
      FloatingLyricTextLayout main = run(lineText(line), fontPx);
      drawRun(canvas, main, width, top + height * (doubleLine ? 0.34f : 0.5f), d,
          colorPlayed, wordMode && !yrcLines.isEmpty() ? line : null, progress, true);
      if (hasSub) {
        String sub = translated ? line.tran : lineText(lines.get(index + 1));
        drawRun(canvas, run(sub, fontPx * 0.7f), width, top + height * 0.73f, d,
            colorUnplayed, null, progress, true);
      }
    }

    private FloatingLyricTextLayout run(String text, float size) {
      String key = text + "\u0000" + size + ":" + fontWeight;
      FloatingLyricTextLayout result = runs.get(key);
      if (result == null) { result = new FloatingLyricTextLayout(text, size, fontWeight); runs.put(key, result); }
      return result;
    }

    private void drawRun(Canvas canvas, FloatingLyricTextLayout run, int width, float cy, float d,
        int color, @Nullable Line wordLine, long progress, boolean scroll) {
      float pad = 14 * d, available = Math.max(1, width - 2 * pad);
      float overflow = Math.max(0, run.width - available);
      float distance = scroll ? FloatingLyricPolicy.scroll(overflow, d,
          Math.max(0, progress - scrollStartMs), lineDurationMs) : 0;
      float x;
      if (overflow > 0) x = run.rtl ? width - pad - run.width + distance : pad - distance;
      else if ("left".equals(alignPosition)) x = pad;
      else if ("right".equals(alignPosition)) x = width - pad - run.width;
      else x = (width - run.width) / 2;
      run.paint.setColor((Math.round(255 * lineAlpha) << 24) | (color & 0xFFFFFF));
      run.paint.setShader(null);
      run.paint.setShadowLayer(2 * d, 0, 0, colorShadow);
      if (wordLine != null && !wordLine.words.isEmpty()) {
        run.prepareWords(wordLine);
        float playedWidth = 0;
        for (int i = 0; i < wordLine.words.size(); i++) {
          Word word = wordLine.words.get(i);
          float fraction = FloatingLyricPolicy.clamp((progress - word.start)
              / (float) Math.max(1, word.end - word.start), 0, 1);
          playedWidth += run.wordWidths[i] * fraction;
        }
        int alpha = Math.round(255 * lineAlpha);
        int playedColor = (alpha << 24) | (colorPlayed & 0xFFFFFF);
        int unplayedColor = (alpha << 24) | (colorUnplayed & 0xFFFFFF);
        float boundary = run.rtl ? run.width - playedWidth : playedWidth;
        run.highlight(boundary, d, playedColor, unplayedColor);
      }
      int save = canvas.save();
      canvas.clipRect(pad, 0, width - pad, getHeight());
      canvas.translate(x, cy - run.layout.getHeight() / 2f);
      run.layout.draw(canvas);
      canvas.restoreToCount(save);
      run.paint.setShader(null);
    }

    private void paintControls(Canvas canvas, int width, float height, float d) {
      int count = tabletMode ? 6 : 5;
      int columns = Math.min(count, Math.max(1, width / Math.round(48 * d)));
      float size = Math.min(48 * d, width / (float) columns);
      float gap = Math.max(0, (width - columns * size) / (columns + 1));
      RectF[] rects = tabletMode ? new RectF[] {rLock, rPrev, rPlay, rNext, rFavorite, rClose}
          : new RectF[] {rLock, rPrev, rPlay, rNext, rClose};
      int[] icons = tabletMode ? new int[] {ICON_UNLOCK, ICON_PREV, playing ? ICON_PAUSE : ICON_PLAY,
          ICON_NEXT, PlaybackManager.getInstance(FloatingLyricService.this).isCurrentLiked()
              ? ICON_HEART_FILLED : ICON_HEART, ICON_CLOSE}
          : new int[] {ICON_UNLOCK, ICON_PREV, playing ? ICON_PAUSE : ICON_PLAY, ICON_NEXT, ICON_CLOSE};
      rFavorite.setEmpty();
      for (int i = 0; i < count; i++) {
        drawBtn(canvas, rects[i], gap + (i % columns) * (size + gap),
            (i / columns) * size + size / 2, size, icons[i]);
      }
    }

    private void drawBtn(Canvas canvas, RectF rect, float left, float cy, float size, int icon) {
      rect.set(left, cy - size / 2, left + size, cy + size / 2);
      drawResIcon(canvas, icon, rect.centerX(), rect.centerY(), size * 0.58f);
    }

    private void drawResIcon(Canvas c, int iconType, float cx, float cy, float sizePx) {
      int resId;
      int tint;
      switch (iconType) {
        case ICON_PLAY:
          resId = R.drawable.lyric_play;
          tint = 0xFFFFFFFF;
          break;
        case ICON_PAUSE:
          resId = R.drawable.lyric_pause;
          tint = 0xFFFFFFFF;
          break;
        case ICON_PREV:
          resId = R.drawable.lyric_prev;
          tint = 0xFFFFFFFF;
          break;
        case ICON_NEXT:
          resId = R.drawable.lyric_next;
          tint = 0xFFFFFFFF;
          break;
        case ICON_CLOSE:
          resId = R.drawable.lyric_close;
          tint = 0xFFFFFFFF;
          break;
        case ICON_LOCK:
          resId = R.drawable.lyric_lock;
          tint = 0xFFFFFFFF;
          break;
        case ICON_UNLOCK:
          resId = R.drawable.lyric_unlock;
          tint = 0xFFFFFFFF;
          break;
        case ICON_HEART:
          resId = R.drawable.lyric_heart;
          tint = COLOR_HEART_LIKED;
          break;
        case ICON_HEART_FILLED:
          resId = R.drawable.lyric_heart_filled;
          tint = COLOR_HEART_LIKED;
          break;
        case ICON_MUSIC:
          resId = R.drawable.lyric_music;
          tint = 0xFFFFFFFF;
          break;
        default:
          return;
      }
      Drawable d = cachedIcon(resId);
      if (d == null) return;
      d.setTint(tint);
      float half = sizePx / 2f;
      d.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
      d.draw(c);
    }

    /** 缓存 VectorDrawable，避免每帧重复加载。 */
    private Drawable cachedIcon(int resId) {
      Drawable d = iconCache.get(resId);
      if (d == null) {
        try {
          d = ResourcesCompat.getDrawable(getResources(), resId, null);
          if (d != null) d = d.mutate();
        } catch (Exception e) {
          Log.w(TAG, "歌词图标读取失败", e);
          d = null;
        }
        iconCache.put(resId, d);
      }
      return d;
    }


  }

  static String lineText(Line l) {
    if (l == null || l.words.isEmpty()) return "";
    if (l.text != null) return l.text;
    StringBuilder sb = new StringBuilder();
    for (Word w : l.words) sb.append(w.text);
    l.text = sb.toString();
    return l.text;
  }

  static class Line {
    long start, end;
    String tran = "";
    List<Word> words = new ArrayList<>();
    String text;
  }

  static class Word {
    final String text;
    final long start, end;

    Word(String t, long s, long e) {
      text = t;
      start = s;
      end = e;
    }
  }
}

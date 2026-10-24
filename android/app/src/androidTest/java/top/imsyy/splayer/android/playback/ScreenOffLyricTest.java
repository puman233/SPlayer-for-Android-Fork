package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Genuine playlist, 1x natural end, actual screen off. Optional bridge suppression models a frozen WebView. */
@RunWith(AndroidJUnit4.class)
public class ScreenOffLyricTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private String js(String code) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] out = {null};
    inst.runOnMainSync(() -> web.evaluateJavascript(code, value -> { out[0] = value; done.countDown(); }));
    assertTrue(done.await(10, TimeUnit.SECONDS)); return out[0];
  }
  private void await(String code) throws Exception {
    long end = SystemClock.elapsedRealtime() + 90000;
    while (SystemClock.elapsedRealtime() < end) {
      if ("true".equals(js(code))) return;
      SystemClock.sleep(200);
    }
    fail("Screen-off preparation failed: " + code);
  }
  private Object field(Object object, String name) throws Exception {
    var property = object.getClass().getDeclaredField(name); property.setAccessible(true); return property.get(object);
  }
  private JSONObject sample(MainActivity activity, PlaybackManager manager) throws Exception {
    JSONObject[] result = {null};
    inst.runOnMainSync(() -> {
      try {
        JSONObject state = manager.buildState();
        FloatingLyricService overlay = (FloatingLyricService) field(manager, "floatingLyricService");
        JSONArray texts = new JSONArray();
        if (overlay != null) for (var line : overlay.lrcLines) {
          StringBuilder text = new StringBuilder(); for (var word : line.words) text.append(word.text);
          texts.put(text.toString());
        }
        result[0] = new JSONObject().put("songId", state.optLong("songId")).put("playing", state.optBoolean("playing"))
            .put("position", state.optLong("positionMs")).put("duration", state.optLong("durationMs"))
            .put("lyrics", texts).put("stopped", ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity) == Stage.STOPPED)
            .put("interactive", ((PowerManager) activity.getSystemService(Context.POWER_SERVICE)).isInteractive());
      } catch (Exception e) { throw new AssertionError(e); }
    });
    return result[0];
  }
  @Test public void realPlaylistNextLyricsArriveBeforeOpeningApp() throws Exception {
    MainActivity activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> web = activity.getBridge().getWebView());
    await("!!document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.get('data')");
    assertTrue("Overlay permission required before playback changes", android.provider.Settings.canDrawOverlays(activity));
    js("(()=>{const p=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia,d=p._s.get('data'),uid=d.userData.userId,type=d.loginType,cookie=localStorage.getItem('cookie-MUSIC_U');window.realAccountPinia=p;window.realNaturalMatrix=true;window.realAccountWitness=()=>({loggedIn:!!d.userLoginStatus&&d.userData.userId>0,authenticated:type!=='uid'&&!!cookie,sameAccount:d.userData.userId===uid&&d.loginType===type,credentialsUnchanged:localStorage.getItem('cookie-MUSIC_U')===cookie})})()");
    assertEquals("Authenticated real account", "true", js("realAccountWitness().loggedIn&&realAccountWitness().authenticated"));
    PlaybackManager manager = PlaybackManager.getInstance(activity);
    AndroidNativePlaybackPlugin plugin = null;
    boolean suppressed = "true".equals(InstrumentationRegistry.getArguments().getString("suppress_web_events"));
    boolean passed = false;
    JSONArray samples = new JSONArray();
    try {
      try (var input = inst.getContext().getAssets().open("real-account-mode.js")) { js(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
      js("prepareRealAccountMode().then(()=>realAccountRestartCase()).then(async()=>{const next=await realAccountCheckNextSource();if(!next.available)throw Error('Next real track unavailable');window.screenNext=next.id;const lyric=await realAccountRequest({url:'/lyric/new',params:{id:next.id}});window.screenExpected=(lyric.lrc?.lyric||'').split(/\\r?\\n/).filter(line=>/\\[\\d+:\\d+/.test(line)).map(line=>line.replace(/\\[[^\\]]*\\]/g,'').trim()).filter(Boolean);if(!screenExpected.length)throw Error('Next real track has no official lyric');window.screenPrepared=true}).catch(e=>window.screenPrepareError=e.name+':'+e.message)");
      await("window.screenPrepared||!!window.screenPrepareError");
      assertEquals("Real source and independent lyric preflight", "null", js("window.screenPrepareError||null"));
      long nextId = ((Number) new JSONTokener(js("screenNext")).nextValue()).longValue();
      JSONArray expected = new JSONArray((String) new JSONTokener(js("JSON.stringify(screenExpected)")).nextValue());
      JSONObject before = sample(activity, manager);
      long readyDeadline = SystemClock.elapsedRealtime() + 30000;
      while ((!before.optBoolean("playing") || before.optLong("duration") < 1000) && SystemClock.elapsedRealtime() < readyDeadline) {
        SystemClock.sleep(100); before = sample(activity, manager);
      }
      long initialId = before.getLong("songId");
      assertTrue("Actual 1x playback is running", before.getBoolean("playing"));
      if (suppressed) {
        plugin = (AndroidNativePlaybackPlugin) field(manager, "plugin");
        assertNotNull(plugin);
        AndroidNativePlaybackPlugin savedPlugin = plugin;
        inst.runOnMainSync(() -> manager.detachPlugin(savedPlugin));
      }
      Intent cover = new Intent(inst.getContext(), BackgroundCoverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
      var options = android.app.ActivityOptions.makeBasic(); options.setLaunchDisplayId(activity.getDisplay().getDisplayId());
      inst.getContext().startActivity(cover, options.toBundle());
      long backgroundDeadline = SystemClock.elapsedRealtime() + 5000;
      while (!sample(activity, manager).getBoolean("stopped") && SystemClock.elapsedRealtime() < backgroundDeadline) SystemClock.sleep(100);
      assertTrue("Foreground transition must finish before sleeping", sample(activity, manager).getBoolean("stopped"));
      try (var command = new android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand("input keyevent 223"))) { command.readAllBytes(); }
      long sleepDeadline = SystemClock.elapsedRealtime() + 5000;
      while (SystemClock.elapsedRealtime() < sleepDeadline) {
        JSONObject sleeping = sample(activity, manager);
        if (sleeping.getBoolean("stopped") && !sleeping.getBoolean("interactive")) break;
        SystemClock.sleep(100);
      }
      long deadline = SystemClock.elapsedRealtime() + Math.max(60000, before.optLong("duration") - before.optLong("position") + 45000);
      boolean changed = false, lyricMatched = false;
      while (SystemClock.elapsedRealtime() < deadline) {
        JSONObject state = sample(activity, manager); samples.put(state);
        assertTrue("Activity must remain stopped while checking new lyrics", state.getBoolean("stopped"));
        assertFalse("Actual screen must remain off", state.getBoolean("interactive"));
        if (state.getLong("songId") != initialId) {
          changed = true; assertEquals("Natural next track follows the real playlist", nextId, state.getLong("songId"));
          JSONArray actual = state.getJSONArray("lyrics");
          for (int i = 0; i < actual.length(); i++) for (int j = 0; j < expected.length(); j++) {
            if (actual.optString(i).equals(expected.optString(j))) lyricMatched = true;
          }
          if (lyricMatched) break;
        }
        SystemClock.sleep(500);
      }
      assertTrue("The real song ended naturally while screen off", changed);
      assertTrue("Next-song lyrics must load before waking or opening the WebView", lyricMatched);
      passed = true;
    } finally {
      inst.getUiAutomation().executeShellCommand("input keyevent 224").close();
      if (plugin != null) { AndroidNativePlaybackPlugin savedPlugin = plugin; inst.runOnMainSync(() -> manager.attachPlugin(savedPlugin)); }
      activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      js("window.screenRestored=false;restoreRealAccountPlayback().then(()=>screenRestored=true).catch(e=>window.screenRestoreError=e.name)");
      await("window.screenRestored||!!window.screenRestoreError");
      File report = new File(activity.getExternalFilesDir(null), "screen-off-lyric.json");
      try (PrintWriter out = new PrintWriter(report, "UTF-8")) { out.print(new JSONObject().put("passed", passed).put("suppressedWebEvents", suppressed).put("samples", samples)); }
      assertEquals("Existing account survives testing", "true", js("realAccountWitness().sameAccount&&realAccountWitness().credentialsUnchanged"));
      assertEquals("Playback restored", "null", js("window.screenRestoreError||null"));
    }
  }
}

package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;

import android.os.SystemClock;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real HTTP and Media3, with bounded server faults; never changes device networking. */
@RunWith(AndroidJUnit4.class)
public class WeakNetworkPlaybackTest {
  private static Object field(Object object, String name) throws Exception {
    var declared = object.getClass().getDeclaredField(name);
    declared.setAccessible(true);
    return declared.get(object);
  }

  private static final class Scenario implements AutoCloseable {
    final DeviceWebFixture fixture = new DeviceWebFixture();
    final PlaybackTransitionTest.AudioServer server = new PlaybackTransitionTest.AudioServer(true, 180);
    final PlaybackManager manager = PlaybackManager.getInstance(fixture.activity);
    final JSONArray samples = new JSONArray(), errors = new JSONArray(), loadErrors = new JSONArray();
    final JSONObject metrics = new JSONObject();
    final AtomicInteger errorCount = new AtomicInteger();
    final AtomicInteger readTimeouts = new AtomicInteger();
    final String name;
    final Object[] savedApiContext = new Object[5]; // Kept in memory only; may include credentials.
    final long started = SystemClock.elapsedRealtime();
    int maxAttempts;
    long maxMainRoundTripMs;
    boolean passed;
    final Player.Listener listener = new Player.Listener() {
      @Override public void onPlayerError(PlaybackException error) {
        errorCount.incrementAndGet();
        try {
          errors.put(new JSONObject().put("atMs", SystemClock.elapsedRealtime() - started)
              .put("errorCode", error.errorCode).put("attempt", field(manager, "nativeRecoveryAttempts")));
        } catch (Exception failure) { throw new AssertionError(failure); }
      }
    };
    final AnalyticsListener analytics = new AnalyticsListener() {
      @Override public void onLoadError(EventTime eventTime, LoadEventInfo info, MediaLoadData data,
          IOException error, boolean wasCanceled) {
        // Media3 may deliver a late load event from the restored preceding item.
        // Observe only this scenario's unique HTTP endpoint, without recording its URL.
        if (info.uri.getPort() != server.server.getLocalPort()
            || info.uri.getPath() == null || !info.uri.getPath().startsWith("/" + server.prefix + "/")) return;
        boolean timeout = false;
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause())
          timeout |= cause instanceof java.net.SocketTimeoutException;
        if (timeout) readTimeouts.incrementAndGet();
        try {
          loadErrors.put(new JSONObject().put("atMs", SystemClock.elapsedRealtime() - started)
              .put("type", error.getClass().getSimpleName()).put("timeout", timeout).put("cancelled", wasCanceled));
        } catch (Exception failure) { throw new AssertionError(failure); }
      }
    };

    Scenario(String name) throws Exception {
      this.name = name;
      for (String asset : new String[]{"r23-player-fixture.js", "http-lyric-sequence.js"})
        try (var stream = fixture.inst.getContext().getAssets().open(asset)) {
          fixture.js(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
      fixture.inst.runOnMainSync(() -> {
        try {
          manager.getSession().getPlayer().addListener(listener);
          ((ExoPlayer) field(manager, "player")).addAnalyticsListener(analytics);
          String[] names = {"apiBaseUrl", "cookie", "songLevel", "disableAiAudio", "playSongDemo"};
          for (int index = 0; index < names.length; index++) savedApiContext[index] = field(manager, names[index]);
        } catch (Exception failure) { throw new AssertionError(failure); }
      });
    }

    void start() throws Exception {
      fixture.js("prepareHttpLyricSequence(" + JSONObject.quote("http://127.0.0.1:" + server.server.getLocalPort())
          + ",1,180000).catch(e=>window.httpSequenceError=e.name)");
      fixture.await("window.httpSequenceReady||!!window.httpSequenceError");
      assertEquals("null", fixture.js("window.httpSequenceError||null"));
      // JS context deduplication can skip an otherwise identical sync call. Explicitly
      // bind the real native resolver too, then restore its exact prior context in close().
      fixture.inst.runOnMainSync(() -> manager.syncApiContext(
          "http://127.0.0.1:" + server.server.getLocalPort(), "", "standard", true, false));
      fixture.inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
    }

    JSONObject sample() throws Exception {
      JSONObject[] result = {null};
      long before = SystemClock.elapsedRealtime();
      fixture.inst.runOnMainSync(() -> {
        try {
          var state = manager.buildState();
          int attempt = (Integer) field(manager, "nativeRecoveryAttempts");
          maxAttempts = Math.max(maxAttempts, attempt);
          result[0] = new JSONObject().put("atMs", SystemClock.elapsedRealtime() - started)
              .put("songId", state.optLong("songId")).put("positionMs", state.optLong("positionMs"))
              .put("playing", state.optBoolean("playing")).put("buffering", state.optBoolean("buffering"))
              .put("attempt", attempt).put("pending", field(manager, "pendingRecoveryRunnable") != null)
              .put("requested", field(manager, "playbackRequested"));
        } catch (Exception failure) { throw new AssertionError(failure); }
      });
      maxMainRoundTripMs = Math.max(maxMainRoundTripMs, SystemClock.elapsedRealtime() - before);
      samples.put(result[0]);
      assertEquals("The selected song must not change during recovery", 991700, result[0].getLong("songId"));
      return result[0];
    }

    JSONObject awaitPlaying(long timeoutMs) throws Exception {
      long deadline = SystemClock.elapsedRealtime() + timeoutMs;
      JSONObject state;
      do {
        state = sample();
        if (state.getBoolean("playing") && state.getLong("positionMs") > 100) return state;
        SystemClock.sleep(100);
      } while (SystemClock.elapsedRealtime() < deadline);
      fail("Actual playback did not advance: " + state);
      return state;
    }

    void awaitPendingRecovery() throws Exception {
      long deadline = SystemClock.elapsedRealtime() + 30000;
      while (SystemClock.elapsedRealtime() < deadline) {
        if (sample().getBoolean("pending")) return;
        SystemClock.sleep(50);
      }
      fail("The real HTTP fault must reach application recovery");
    }

    @Override public void close() throws Exception {
      fixture.inst.runOnMainSync(() -> {
        try {
          manager.getSession().getPlayer().removeListener(listener);
          ((ExoPlayer) field(manager, "player")).removeAnalyticsListener(analytics);
        } catch (Exception failure) { throw new AssertionError(failure); }
      });
      try {
        metrics.put("scenario", name).put("playbackAssertionsPassed", passed).put("maxAttempts", maxAttempts)
            .put("audioRequests", server.audioRequests.get()).put("urlRequests", server.urlRequests.get())
            .put("http503", server.failures.get()).put("tcpResets", server.interruptedResponses.get())
            .put("readStalls", server.stalledResponses.get()).put("readTimeouts", readTimeouts.get())
            .put("bytesSent", server.sent.get()).put("maxMainRoundTripMs", maxMainRoundTripMs)
            .put("errors", errors).put("loadErrors", loadErrors).put("samples", samples);
        File directory = new File(fixture.activity.getExternalFilesDir(null), "detail-regression");
        assertTrue(directory.exists() || directory.mkdirs());
        String filename = "network-" + name + "-" + System.currentTimeMillis() + ".json";
        try (PrintWriter output = new PrintWriter(new File(directory, filename), "UTF-8")) { output.print(metrics); }
        System.out.println("NETWORK_METRICS " + name + " passed=" + passed + " attempts=" + maxAttempts
            + " audioRequests=" + server.audioRequests.get() + " urlRequests=" + server.urlRequests.get());
      } finally {
        try {
          fixture.inst.runOnMainSync(() -> {
            manager.pause();
            manager.syncApiContext((String) savedApiContext[0], (String) savedApiContext[1],
                (String) savedApiContext[2], (Boolean) savedApiContext[3], (Boolean) savedApiContext[4]);
          });
          fixture.activity.startActivity(new android.content.Intent(fixture.activity,
              top.imsyy.splayer.android.MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
          fixture.js("window.networkRestored=false;window.networkRestorePhase='stores';window.networkSavedPlaying=p7Saved.status.playStatus;"
              + "p7Saved.status.playStatus=false;restoreHttpLyricSequence().then(async()=>{"
              + "window.networkRestorePhase='overlay';"
              + "await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(Boolean(p7Saved.status.showDesktopLyric));"
              + "window.networkRestorePhase='transport';"
              + "if(p7Saved.music.playSong.id)await window.__SPLAYER_PLAYER_CONTROLLER__.playSong({song:p7Saved.music.playSong,"
              + "autoPlay:networkSavedPlaying,seek:p7Saved.status.currentTime});window.networkRestored=true})"
              + ".catch(e=>window.networkRestoreError={phase:networkRestorePhase,type:e.name})");
          for (int attempt = 0; attempt < 600 && !"true".equals(fixture.js("window.networkRestored||!!window.networkRestoreError")); attempt++) SystemClock.sleep(100);
          assertEquals("Restore original stores and transport: " + fixture.js("window.networkRestoreError||window.networkRestorePhase"),
              "true", fixture.js("window.networkRestored===true&&!window.networkRestoreError"));
        } finally { server.close(); fixture.close(); }
      }
    }
  }

  private JSONObject sustainableProfile(String name, boolean delayed) throws Exception {
    try (Scenario scenario = new Scenario(name)) {
      scenario.server.chunkDelayMs = 100; // 80 KiB/s, comfortably above 31.25 KiB/s PCM.
      if (delayed) {
        scenario.server.headerDelayMs = 1200;
        scenario.server.jitterEveryChunks = 8;
        scenario.server.jitterDelayMs = 600; // Average remains about 46 KiB/s.
      }
      scenario.start();
      JSONObject first = scenario.awaitPlaying(15000);
      long selectedAt = (long) Double.parseDouble(scenario.fixture.js("window.httpSequenceSelectedAt"));
      long startupMs = System.currentTimeMillis() - selectedAt;
      long bufferingMs = 0, last = SystemClock.elapsedRealtime(), end = last + 20000;
      int stalls = 0;
      boolean wasBuffering = false;
      JSONObject state = first;
      while (SystemClock.elapsedRealtime() < end) {
        SystemClock.sleep(100);
        state = scenario.sample();
        long now = SystemClock.elapsedRealtime();
        if (state.getBoolean("buffering")) {
          bufferingMs += now - last;
          if (!wasBuffering) stalls++;
        }
        wasBuffering = state.getBoolean("buffering"); last = now;
      }
      scenario.metrics.put("startupMs", startupMs).put("bufferingMs", bufferingMs).put("stalls", stalls)
          .put("positionAdvanceMs", state.getLong("positionMs") - first.getLong("positionMs"))
          .put("headerDelayMs", scenario.server.headerDelayMs).put("jitterDelayMs", scenario.server.jitterDelayMs);
      assertTrue("Sustainable bandwidth must keep the audio timeline advancing", state.getLong("positionMs") - first.getLong("positionMs") > 17000);
      assertTrue("Sustainable throughput must not cause prolonged rebuffering: " + bufferingMs, bufferingMs < 2000);
      assertTrue("Startup must remain bounded with the injected delay", startupMs < 12000);
      assertEquals("No application recovery should be needed", 0, scenario.maxAttempts);
      assertEquals(0, scenario.errorCount.get());
      scenario.passed = true;
      return new JSONObject().put("profile", name).put("startupMs", startupMs).put("bufferingMs", bufferingMs).put("stalls", stalls);
    }
  }

  @Test public void sustainableLatencyAndJitterHaveMeasuredBaseline() throws Exception {
    JSONObject baseline = sustainableProfile("baseline", false);
    JSONObject delayed = sustainableProfile("latency-jitter", true);
    System.out.println("NETWORK_PROFILE_COMPARISON " + new JSONArray().put(baseline).put(delayed));
  }

  @Test public void realTcpResetRecoversWithoutChangingTrack() throws Exception {
    try (Scenario scenario = new Scenario("tcp-reset")) {
      scenario.server.chunkDelayMs = 100;
      scenario.server.interruptAfterBytes = 65536;
      scenario.start();
      long deadline = SystemClock.elapsedRealtime() + 20000;
      while (scenario.server.interruptedResponses.get() == 0 && SystemClock.elapsedRealtime() < deadline) {
        scenario.sample(); SystemClock.sleep(100);
      }
      assertEquals("The server must actually reset a TCP media connection", 1, scenario.server.interruptedResponses.get());
      JSONObject first = scenario.awaitPlaying(20000);
      SystemClock.sleep(5000);
      JSONObject later = scenario.sample();
      assertTrue("The audio must progress after the real TCP reset", later.getLong("positionMs") - first.getLong("positionMs") > 3500);
      assertTrue("Media must actually be requested again", scenario.server.audioRequests.get() >= 2);
      scenario.passed = true;
    }
  }

  @Test public void actualReadTimeoutReopensHttpAndKeepsTimelineAdvancing() throws Exception {
    try (Scenario scenario = new Scenario("read-timeout")) {
      scenario.server.chunkDelayMs = 100;
      scenario.server.firstReadStallMs = 17000; // Exceeds the production 15-second HTTP read timeout.
      scenario.start();
      long deadline = SystemClock.elapsedRealtime() + 30000;
      while (scenario.readTimeouts.get() == 0 && SystemClock.elapsedRealtime() < deadline) {
        scenario.sample(); SystemClock.sleep(100);
      }
      assertEquals("The media server must stall an actual response", 1, scenario.server.stalledResponses.get());
      assertTrue("The production HTTP reader must report SocketTimeoutException", scenario.readTimeouts.get() > 0);
      JSONObject first = scenario.awaitPlaying(20000);
      SystemClock.sleep(5000);
      JSONObject later = scenario.sample();
      assertTrue("Audio must progress after the real read timeout", later.getLong("positionMs") - first.getLong("positionMs") > 3500);
      assertTrue(scenario.server.audioRequests.get() >= 2);
      scenario.passed = true;
    }
  }

  @Test public void nativeRecoveryResumesAndResetsBudgetOnlyAfterStablePlayback() throws Exception {
    try (Scenario scenario = new Scenario("recover-503")) {
      scenario.server.audioOffline = true;
      scenario.start(); scenario.awaitPendingRecovery();
      assertEquals(1, scenario.maxAttempts);
      scenario.server.audioOffline = false;
      scenario.awaitPlaying(20000);
      assertTrue("Recovery must resolve a fresh actual source", scenario.server.urlRequests.get() >= 2);
      assertTrue("Brief playback must retain its recovery budget", scenario.sample().getInt("attempt") > 0);
      long deadline = SystemClock.elapsedRealtime() + 16000;
      JSONObject stable;
      long resetPositionMs = -1;
      do {
        stable = scenario.sample();
        if (stable.getInt("attempt") == 0 && resetPositionMs < 0) resetPositionMs = stable.getLong("positionMs");
        // The main-looper timer and Media3 audio position need not cross 10s in
        // the same sample. Wait for both, retaining the actual progress assertion.
        if (stable.getInt("attempt") == 0 && stable.getLong("positionMs") >= 10000) break;
        SystemClock.sleep(200);
      } while (SystemClock.elapsedRealtime() < deadline);
      scenario.metrics.put("budgetResetPositionMs", resetPositionMs);
      assertTrue("Recovery must not reset before stable playback (100ms observation tolerance)", resetPositionMs >= 9900);
      assertEquals("Ten seconds of actual steady playback must reset recovery", 0, stable.getInt("attempt"));
      assertTrue(stable.getBoolean("playing"));
      assertTrue(stable.getLong("positionMs") >= 10000);
      scenario.passed = true;
    }
  }

  @Test public void pauseCancelsPendingRecoveryEvenWhenNetworkReturns() throws Exception {
    try (Scenario scenario = new Scenario("pause-recovery")) {
      scenario.server.audioOffline = true;
      scenario.start(); scenario.awaitPendingRecovery();
      scenario.fixture.inst.runOnMainSync(scenario.manager::pause);
      int requestsAtPause = scenario.server.urlRequests.get();
      scenario.server.audioOffline = false;
      long deadline = SystemClock.elapsedRealtime() + 9000;
      while (SystemClock.elapsedRealtime() < deadline) {
        JSONObject state = scenario.sample();
        assertFalse("Network return must not resume a paused track", state.getBoolean("playing"));
        assertFalse(state.getBoolean("pending"));
        assertFalse(state.getBoolean("requested"));
        SystemClock.sleep(100);
      }
      assertEquals("A cancelled recovery must not resolve another source", requestsAtPause, scenario.server.urlRequests.get());
      scenario.passed = true;
    }
  }

  @Test public void persistent503ExhaustsThreeRecoveriesAndDoesNotStartWebRetryLoop() throws Exception {
    try (Scenario scenario = new Scenario("exhaust-503")) {
      scenario.server.audioOffline = true;
      scenario.start();
      long deadline = SystemClock.elapsedRealtime() + 90000;
      while (scenario.errorCount.get() < 4 && SystemClock.elapsedRealtime() < deadline) {
        scenario.sample(); SystemClock.sleep(100);
      }
      assertEquals("One initial failure and three native reload failures", 4, scenario.errorCount.get());
      assertEquals(3, scenario.maxAttempts);
      int finalAudioRequests = scenario.server.audioRequests.get(), finalUrlRequests = scenario.server.urlRequests.get();
      long quietEnd = SystemClock.elapsedRealtime() + 9000;
      while (SystemClock.elapsedRealtime() < quietEnd) {
        JSONObject state = scenario.sample();
        assertFalse(state.getBoolean("playing"));
        assertFalse("No recovery task may remain after exhaustion", state.getBoolean("pending"));
        SystemClock.sleep(100);
      }
      assertEquals("The Web layer must not start a second audio retry loop", finalAudioRequests, scenario.server.audioRequests.get());
      assertEquals(finalUrlRequests, scenario.server.urlRequests.get());
      assertTrue("Resolve only initial source and three native recoveries", finalUrlRequests <= 4);
      assertTrue("Media3's five retries per load remain bounded", scenario.loadErrors.length() <= 24);
      // The existing 10-second cache-promotion task can issue one separate request.
      // Count it too, without confusing a cache request with an extra playback retry.
      assertTrue("Playback retries plus one cache request must remain bounded", finalAudioRequests <= 25);
      assertEquals("Terminal error must settle the real UI without changing songs", "true",
          scenario.fixture.js("!fixtureStatus.playStatus&&!fixtureStatus.playLoading&&fixturePinia._s.get('music').playSong.id===991700"));
      scenario.passed = true;
    }
  }
}

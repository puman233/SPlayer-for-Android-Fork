package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import java.io.File;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real HTMLAudioElement, public engine selection and actual HTTP; isolated package only. */
@RunWith(AndroidJUnit4.class)
public class WebViewNetworkPlaybackTest {
  private static Object field(Object object, String name) throws Exception {
    var declared = object.getClass().getDeclaredField(name);
    declared.setAccessible(true);
    return declared.get(object);
  }

  private static final class Scenario implements AutoCloseable {
    final DeviceWebFixture fixture = new DeviceWebFixture();
    final PlaybackTransitionTest.AudioServer server = new PlaybackTransitionTest.AudioServer(true, 180);
    final PlaybackManager manager = PlaybackManager.getInstance(fixture.activity);
    final Object[] apiContext = new Object[5]; // Restore in memory; never serialize credentials.
    final JSONArray samples = new JSONArray();
    final JSONObject metrics = new JSONObject();
    final String name;
    int maxWebRetries, maxNativeRetries;
    boolean nativePlayed, passed;
    Scenario(String name) throws Exception {
      this.name = name;
      server.chunkDelayMs = 100;
      for (String asset : new String[]{"r23-player-fixture.js", "http-lyric-sequence.js", "webview-network.js"})
        try (var stream = fixture.inst.getContext().getAssets().open(asset)) {
          fixture.js(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
      fixture.inst.runOnMainSync(() -> {
        try {
          String[] names = {"apiBaseUrl", "cookie", "songLevel", "disableAiAudio", "playSongDemo"};
          for (int index = 0; index < names.length; index++) apiContext[index] = field(manager, names[index]);
        } catch (Exception error) { throw new AssertionError(error); }
      });
    }
    void begin() throws Exception {
      fixture.js("prepareWebNetwork(" + JSONObject.quote("http://127.0.0.1:" + server.server.getLocalPort())
          + ").catch(e=>window.webNetError={phase:window.webNetPhase,type:e.name})");
    }
    void start() throws Exception {
      begin();
      for (int attempt = 0; attempt < 600 && !"true".equals(fixture.js("window.webNetPrepared||!!window.webNetError")); attempt++) SystemClock.sleep(100);
      assertEquals("Real element setup: " + fixture.js("window.webNetError"), "true", fixture.js("window.webNetPrepared===true"));
    }
    JSONObject json(String expression) throws Exception {
      return new JSONObject((String) new JSONTokener(fixture.js("JSON.stringify(" + expression + ")")).nextValue());
    }
    JSONObject sample() throws Exception {
      JSONObject state = json("window.webNetSnapshot()");
      fixture.inst.runOnMainSync(() -> {
        try {
          ExoPlayer player = (ExoPlayer) field(manager, "player");
          nativePlayed |= player != null && player.isPlaying();
          maxNativeRetries = Math.max(maxNativeRetries, (Integer) field(manager, "nativeRecoveryAttempts"));
        } catch (Exception error) { throw new AssertionError(error); }
      });
      maxWebRetries = Math.max(maxWebRetries, state.getInt("retryCount"));
      samples.put(state);
      assertEquals("element", state.getString("engine"));
      assertTrue(state.getBoolean("realElement"));
      assertEquals(991700, state.getLong("songId"));
      assertFalse("ExoPlayer must not produce a second audio stream", nativePlayed);
      assertEquals("Native recovery must remain inactive in element mode", 0, maxNativeRetries);
      return state;
    }
    JSONObject awaitPlaying(long timeoutMs) throws Exception {
      long deadline = SystemClock.elapsedRealtime() + timeoutMs;
      JSONObject state;
      do {
        state = sample();
        if (!state.getBoolean("paused") && state.getLong("positionMs") > 200 && state.getInt("readyState") >= 3) {
          assertTrue("Actual media must come from this scenario's server", state.getBoolean("sourceBound"));
          return state;
        }
        SystemClock.sleep(100);
      } while (SystemClock.elapsedRealtime() < deadline);
      fail("Real HTML audio did not advance: " + state);
      return state;
    }
    void failActiveStream() throws Exception {
      awaitPlaying(20000);
      server.audioOffline = true;
      for (Socket socket : server.sockets) { socket.setSoLinger(true, 0); socket.close(); }
    }
    JSONObject awaitWebRecovery() throws Exception {
      long deadline = SystemClock.elapsedRealtime() + 30000;
      JSONObject state;
      do {
        state = sample();
        if (state.getInt("retryCount") > 0) return state;
        SystemClock.sleep(50);
      } while (SystemClock.elapsedRealtime() < deadline);
      fail("Midstream HTTP fault must reach real Web recovery: " + state);
      return state;
    }
    @Override public void close() throws Exception {
      try {
        metrics.put("scenario", name).put("playbackAssertionsPassed", passed).put("samples", samples)
            .put("maxWebRetries", maxWebRetries).put("maxNativeRetries", maxNativeRetries)
            .put("nativePlayed", nativePlayed).put("audioRequests", server.audioRequests.get())
            .put("statusProbeRequests",server.statusProbeRequests.get())
            .put("urlRequestFacts",server.urlRequestFacts)
            .put("urlRequests", server.urlRequests.get()).put("tcpResets", server.interruptedResponses.get())
            .put("trace", json("({events:window.webNetEvents||[],sources:window.webNetSources||[]})"));
        File directory = new File(fixture.activity.getExternalFilesDir(null), "detail-regression");
        assertTrue(directory.exists() || directory.mkdirs());
        try (PrintWriter output = new PrintWriter(new File(directory, "web-network-" + name + "-" + System.currentTimeMillis() + ".json"), "UTF-8")) { output.print(metrics); }
      } finally {
        try {
          fixture.activity.startActivity(new Intent(fixture.activity, top.imsyy.splayer.android.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
          fixture.inst.runOnMainSync(() -> {
            manager.pause();
            manager.syncApiContext((String) apiContext[0], (String) apiContext[1], (String) apiContext[2], (Boolean) apiContext[3], (Boolean) apiContext[4]);
          });
          if ("true".equals(fixture.js("!!window.p7Saved"))) {
            fixture.js("restoreWebNetwork().catch(e=>window.webNetRestoreError=e.name)");
            for (int attempt = 0; attempt < 600 && !"true".equals(fixture.js("window.webNetRestored||!!window.webNetRestoreError")); attempt++) SystemClock.sleep(100);
            assertEquals("Restore original engine and transport: " + fixture.js("window.webNetRestoreError"), "true", fixture.js("window.webNetRestored===true&&!window.webNetRestoreError"));
          }
        } finally { server.close(); fixture.close(); }
      }
    }
  }

  private void sustainable(String name, boolean delayed) throws Exception {
    try (Scenario scenario = new Scenario(name)) {
      if (delayed) {
        scenario.server.headerDelayMs = 1200;
        scenario.server.jitterEveryChunks = 8;
        scenario.server.jitterDelayMs = 600;
      }
      scenario.start();
      JSONObject first = scenario.awaitPlaying(20000);
      long selectedAt = (long) Double.parseDouble(scenario.fixture.js("window.httpSequenceSelectedAt"));
      scenario.metrics.put("startupMs", System.currentTimeMillis() - selectedAt);
      long end = SystemClock.elapsedRealtime() + 15000;
      JSONObject last = first;
      while (SystemClock.elapsedRealtime() < end) { SystemClock.sleep(200); last = scenario.sample(); }
      long advance = last.getLong("positionMs") - first.getLong("positionMs");
      scenario.metrics.put("positionAdvanceMs", advance);
      assertTrue("Sustainable HTTP must keep actual HTML media progressing", advance > 13000);
      assertEquals(0, scenario.maxWebRetries);
      assertTrue(scenario.server.audioRequests.get() > 0);
      scenario.passed = true;
    }
  }
  @Test public void elementActuallyPlaysNormalAndDelayedHttpWithoutNativeRecovery() throws Exception {
    sustainable("baseline", false);
    sustainable("latency-jitter", true);
  }
  @Test public void initial503RecoversWithoutWrappingSelectionOrNativeRetry() throws Exception {
    try (Scenario scenario = new Scenario("initial-503")) {
      scenario.server.audioOffline = true;
      scenario.start();
      scenario.awaitWebRecovery();
      scenario.server.audioOffline = false;
      scenario.awaitPlaying(20000);
      assertTrue("Recovery must fetch a fresh source",scenario.server.urlRequests.get()>=2);
      assertTrue("Web recovery remains bounded",scenario.maxWebRetries<=3);
      scenario.passed = true;
    }
  }
  @Test public void permanentAndUnobservableFailuresNeverRetryAs503() throws Exception {
    for(String profile:new String[]{"missing-404","denied-403","invalid-format","cors-denied"})try (Scenario scenario = new Scenario(profile)) {
      scenario.server.audioStatus = profile.equals("missing-404")?404:profile.equals("denied-403")?403:200;
      scenario.server.invalidAudio = profile.equals("invalid-format");
      scenario.server.audioCorsEnabled = !profile.equals("cors-denied");
      scenario.metrics.put("responseStatus",scenario.server.audioStatus).put("corsEnabled",scenario.server.audioCorsEnabled);
      scenario.start();
      assertTerminalFailure(scenario);
      scenario.passed = true;
    }
  }
  @Test public void initial503ProbeTimesOutWithoutAssumingARecoverableStatus() throws Exception {
    try(Scenario scenario=new Scenario("probe-timeout")) {
      scenario.server.audioOffline=true;
      scenario.server.statusProbeDelayMs=5000;
      scenario.start();
      assertTerminalFailure(scenario);
      assertEquals(1,scenario.server.statusProbeRequests.get());
      assertEquals(1,scenario.server.urlRequests.get());
      scenario.passed=true;
    }
  }
  @Test public void pausingDuringInitialStatusProbeCancelsTheRequestAndRecovery() throws Exception {
    try(Scenario scenario=new Scenario("pause-status-probe")) {
      scenario.server.audioOffline=true;
      scenario.server.statusProbeDelayMs=1500;
      // Observe and forward the real HTTP boundary, then issue the public pause
      // on the browser event loop. Native polling can exceed the two-second probe
      // deadline on a loaded tablet and miss the pending state entirely.
      scenario.fixture.js("observeWebProbeAndPause()");
      scenario.start();
      scenario.fixture.await("window.webNetProbePauseComplete===true");
      assertEquals(1,scenario.server.statusProbeRequests.get());
      assertEquals("A real HTTP request must be pending before pause","true",
          scenario.fixture.js("window.webNetProbePendingAtPause===true&&window.webNetObservedProbeSignal.aborted"));
      scenario.metrics.put("probePendingAtPause",true).put("probeAbortVerified",true);
      scenario.fixture.await("!window.__SPLAYER_PLAYER_CONTROLLER__.mediaFailureProbe");
      scenario.server.audioOffline=false;
      SystemClock.sleep(5500);
      JSONObject state=scenario.sample();
      assertTrue(state.getBoolean("paused"));
      assertFalse(state.getBoolean("playing"));
      assertEquals(0,state.getInt("retryCount"));
      assertEquals(1,scenario.server.urlRequests.get());
      assertEquals(1,state.getInt("sourceCalls"));
      scenario.passed=true;
    }
  }
  @Test public void persistentInitial503ExhaustsOneBoundedWebBudget() throws Exception {
    try(Scenario scenario=new Scenario("exhaust-initial-503")) {
      scenario.server.audioOffline=true;
      scenario.start();
      assertExhausted(scenario);
      assertEquals("Each failed media attempt has one bounded status request",8,scenario.server.audioRequests.get());
      assertEquals(4,scenario.server.statusProbeRequests.get());
      scenario.metrics.put("exhaustedAudioRequests",scenario.server.audioRequests.get()).put("exhaustedUrlRequests",scenario.server.urlRequests.get());
      scenario.server.audioOffline=false;
      scenario.fixture.js("window.__SPLAYER_PLAYER_CONTROLLER__.play()");
      JSONObject replay=scenario.awaitPlaying(20000);
      assertEquals("Only the explicit user replay may start a new request",5,replay.getInt("sourceCalls"));
      scenario.metrics.put("manualReplayVerified",true);
      scenario.passed=true;
    }
  }
  @Test public void midstreamFailureAndPersistent503ShareOneWebRecoveryBudget() throws Exception {
    try(Scenario scenario=new Scenario("exhaust-midstream")) {
      scenario.start();
      scenario.awaitPlaying(20000);
      SystemClock.sleep(1200);
      scenario.failActiveStream();
      scenario.awaitWebRecovery();
      assertExhausted(scenario);
      assertEquals("true",scenario.fixture.js("window.webNetEvents.some(event=>event.mediaCode===2)"));
      assertTrue("At most one status probe per remaining source attempt",scenario.server.statusProbeRequests.get()<=3);
      scenario.passed=true;
    }
  }
  @Test public void confirmed503CanRecoverWhileTheActualActivityStaysStopped() throws Exception {
    try(Scenario scenario=new Scenario("recover-background-503")) {
      scenario.server.audioOffline=true;
      scenario.server.firstMediaHeaderDelayMs=10000;
      scenario.begin();
      scenario.fixture.await("window.webNetElement instanceof HTMLAudioElement&&window.webNetElement.src.includes('127.0.0.1:')");
      Stage stage=scenario.fixture.moveToBackground();
      assertEquals("true",scenario.fixture.js("document.hidden"));
      assertEquals("No HTTP error may occur before actual background entry",0,scenario.server.failures.get());
      scenario.metrics.put("backgroundLifecycle",stage.name());
      long deadline=SystemClock.elapsedRealtime()+20000;
      while(scenario.server.failures.get()<2&&SystemClock.elapsedRealtime()<deadline) {
        SystemClock.sleep(50);
        scenario.fixture.inst.runOnMainSync(()->assertEquals(Stage.STOPPED,
          ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(scenario.fixture.activity)));
      }
      assertEquals("Both initial media and real status probe must receive503",2,scenario.server.failures.get());
      assertEquals(1,scenario.server.statusProbeRequests.get());
      scenario.server.audioOffline=false;
      for(int index=0;index<3;index++) {
        SystemClock.sleep(5000);
        scenario.fixture.inst.runOnMainSync(()->assertEquals(Stage.STOPPED,
          ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(scenario.fixture.activity)));
      }
      scenario.fixture.activity.startActivity(new Intent(scenario.fixture.activity,top.imsyy.splayer.android.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      JSONObject after=scenario.sample();
      assertEquals(2,after.getInt("sourceCalls"));
      assertFalse(after.getBoolean("paused"));
      assertTrue("Actual HTML audio must resume without foreground polling",after.getLong("positionMs")>=5000);
      scenario.passed=true;
    }
  }
  private void assertExhausted(Scenario scenario) throws Exception {
    long deadline=SystemClock.elapsedRealtime()+40000;
    JSONObject state;
    do {
      state=scenario.sample();
      if(state.getInt("sourceCalls")==4&&state.getInt("retryCount")==0
          &&state.getBoolean("paused")&&!state.getBoolean("playing")&&!state.getBoolean("loading")&&!state.getBoolean("mediaFailurePending"))break;
      SystemClock.sleep(100);
    }while(SystemClock.elapsedRealtime()<deadline);
    assertEquals("Only the initial source plus three retries are allowed",4,state.getInt("sourceCalls"));
    assertEquals(4,scenario.server.urlRequests.get());
    assertEquals(0,state.getInt("retryCount"));
    assertTrue(state.getBoolean("paused"));
    assertFalse(state.getBoolean("playing"));
    assertFalse(state.getBoolean("loading"));
    assertTrue(scenario.maxWebRetries>0&&scenario.maxWebRetries<=3);
    int requests=scenario.server.audioRequests.get();
    SystemClock.sleep(8000);
    state=scenario.sample();
    assertEquals(4,state.getInt("sourceCalls"));
    assertEquals(requests,scenario.server.audioRequests.get());
    assertTrue(state.getBoolean("paused"));
  }
  private void assertTerminalFailure(Scenario scenario) throws Exception {
      long deadline = SystemClock.elapsedRealtime() + 30000;
      JSONObject state;
      do {
        state = scenario.sample();
        if (state.getInt("mediaCode") > 0 && !state.getBoolean("playing") && !state.getBoolean("loading")) break;
        SystemClock.sleep(100);
      } while (SystemClock.elapsedRealtime() < deadline);
      assertTrue("The real media resource must fail", state.getInt("mediaCode") > 0);
      assertFalse(state.getBoolean("playing")); assertFalse(state.getBoolean("loading"));
      int urls = scenario.server.urlRequests.get();
      SystemClock.sleep(5500);
      state = scenario.sample();
      assertTrue(state.getBoolean("paused"));
      assertEquals("Terminal source failure must stop resolving", urls, scenario.server.urlRequests.get());
      assertTrue(scenario.server.urlRequests.get() <= 4);
      assertEquals(0,scenario.maxWebRetries);
  }
  @Test public void webMediaRecoversFromActualTcpResetWithoutChangingSong() throws Exception {
    try (Scenario scenario = new Scenario("tcp-reset")) {
      scenario.server.interruptAfterBytes = 65536;
      scenario.start();
      JSONObject first = scenario.awaitPlaying(30000);
      long deadline = SystemClock.elapsedRealtime() + 15000;
      while (scenario.server.interruptedResponses.get() == 0 && SystemClock.elapsedRealtime() < deadline) { scenario.sample(); SystemClock.sleep(100); }
      assertEquals(1, scenario.server.interruptedResponses.get());
      first = scenario.awaitPlaying(30000);
      SystemClock.sleep(5000);
      JSONObject last = scenario.sample();
      assertTrue("Actual HTML audio must advance after TCP reset", last.getLong("positionMs") - first.getLong("positionMs") > 3500);
      assertTrue(scenario.server.audioRequests.get() >= 2);
      assertTrue(scenario.maxWebRetries <= 3);
      scenario.passed = true;
    }
  }
  @Test public void pausingRealPendingWebRecoveryPreventsRestartWhenNetworkReturns() throws Exception {
    try (Scenario scenario = new Scenario("pause-recovery")) {
      // Issue pause from the real media-error turn, after the production error
      // listener schedules recovery. A slow native round trip can otherwise
      // arrive after the one-second recovery has already started its HTTP call.
      scenario.fixture.js("window.webNetPauseOnMediaError=true");
      scenario.start(); scenario.awaitPlaying(20000);
      SystemClock.sleep(1200);
      scenario.failActiveStream();
      long pauseAt = SystemClock.elapsedRealtime();
      scenario.fixture.await("window.webNetRecoveryPauseComplete===true");
      assertEquals("The real network error must schedule recovery before public pause","true",
          scenario.fixture.js("window.webNetRecoveryAtPause>0&&window.webNetErrorCodeAtPause===2"));
      int urls = scenario.server.urlRequests.get();
      assertEquals("Pause must precede another source request",1,urls);
      // The production pause honors the saved fade duration. Network return is
      // tested after the real DOM transport finishes that pause, not one task earlier.
      scenario.fixture.await("window.webNetElement.paused&&!window.fixtureStatus.playStatus");
      scenario.metrics.put("pauseCompletionMs", SystemClock.elapsedRealtime() - pauseAt);
      scenario.metrics.put("realNetworkErrorBeforePause",true);
      scenario.server.audioOffline = false;
      long end = SystemClock.elapsedRealtime() + 8000;
      while (SystemClock.elapsedRealtime() < end) {
        JSONObject state = scenario.sample();
        assertTrue("Network return must not restart paused HTML media", state.getBoolean("paused"));
        assertFalse(state.getBoolean("playing"));
        assertEquals(0, state.getInt("retryCount"));
        assertEquals("Paused recovery must not start source preparation",1,state.getInt("sourceCalls"));
        SystemClock.sleep(200);
      }
      assertEquals("Cancelled web recovery must not fetch another source", urls, scenario.server.urlRequests.get());
      scenario.passed = true;
    }
  }
  @Test public void realWebRecoveryRetainsBudgetUntilTenSecondsOfActualProgress() throws Exception {
    recoveredStablePlayback("stable-recovery", false);
  }
  @Test public void realSeekCannotEarnANewWebRecoveryBudget() throws Exception {
    recoveredStablePlayback("seek-recovery", true);
  }
  @Test public void actualElementContinuesInBackgroundWithoutWebViewPolling() throws Exception {
    try (Scenario scenario = new Scenario("background")) {
      scenario.start();
      JSONObject first = scenario.awaitPlaying(20000);
      Stage stage = scenario.fixture.moveToBackground();
      assertEquals("The media document must really be hidden before the unpolled interval", "true", scenario.fixture.js("document.hidden"));
      scenario.metrics.put("backgroundLifecycle", stage.name());
      long started = SystemClock.elapsedRealtime();
      for (int index = 0; index < 6; index++) {
        SystemClock.sleep(5000);
        // No evaluateJavascript while backgrounded. Only verify the native
        // decoder does not become a second transport behind the HTML player.
        scenario.fixture.inst.runOnMainSync(() -> {
          try {
            ExoPlayer player = (ExoPlayer) field(scenario.manager, "player");
            assertFalse(player != null && player.isPlaying());
            assertEquals(0, (int) (Integer) field(scenario.manager, "nativeRecoveryAttempts"));
          } catch (Exception error) { throw new AssertionError(error); }
        });
      }
      scenario.fixture.activity.startActivity(new Intent(scenario.fixture.activity, top.imsyy.splayer.android.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      JSONObject last = scenario.sample();
      long advance = last.getLong("positionMs") - first.getLong("positionMs");
      scenario.metrics.put("backgroundWindowMs", SystemClock.elapsedRealtime() - started).put("backgroundActualAdvanceMs", advance);
      assertTrue("Actual DOM audio must progress during the unpolled background interval", advance >= 25000);
      assertFalse(last.getBoolean("paused"));
      assertTrue(last.getBoolean("sourceBound"));
      scenario.passed = true;
    }
  }
  private void recoveredStablePlayback(String name, boolean seek) throws Exception {
    try (Scenario scenario = new Scenario(name)) {
      scenario.start(); scenario.awaitPlaying(20000);
      SystemClock.sleep(1200);
      scenario.failActiveStream(); scenario.awaitWebRecovery();
      scenario.server.audioOffline = false;
      long deadline = SystemClock.elapsedRealtime() + 15000;
      while (scenario.sample().getInt("sourceCalls") < 2 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
      assertTrue("Actual production retry must prepare another source", scenario.sample().getInt("sourceCalls") >= 2);
      JSONObject first = scenario.awaitPlaying(20000);
      assertTrue("A short play must retain its retry budget", first.getInt("retryCount") > 0);
      if (seek) {
        scenario.fixture.js("window.__SPLAYER_PLAYER_CONTROLLER__.setSeek(60000)");
        deadline = SystemClock.elapsedRealtime() + 15000;
        do {
          first = scenario.sample();
          if (first.getLong("positionMs") >= 60000 && first.getInt("readyState") >= 3 && !first.getBoolean("paused")) break;
          SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue("Actual HTML media must seek to the requested position", first.getLong("positionMs") >= 60000);
        assertEquals("true", scenario.fixture.js("window.webNetEvents.some(event=>event.type==='seeking')"));
        assertTrue("A seek jump must not count as stable playback", first.getInt("retryCount") > 0);
      }
      double progressStart = -1;
      JSONObject state;
      deadline = SystemClock.elapsedRealtime() + 16000;
      do {
        state = scenario.sample();
        if (progressStart < 0 && !state.isNull("progressStart")) progressStart = state.getDouble("progressStart");
        if (state.getInt("retryCount") == 0) break;
        SystemClock.sleep(100);
      } while (SystemClock.elapsedRealtime() < deadline);
      scenario.metrics.put("progressStartMs", progressStart).put("resetStorePositionMs", state.getDouble("storePositionMs"))
          .put("actualProgressMs", state.getLong("positionMs") - first.getLong("positionMs"));
      assertEquals(0, state.getInt("retryCount"));
      assertTrue("The real DOM media timeline must have advanced", state.getLong("positionMs") - first.getLong("positionMs") >= 9000);
      assertTrue("Reset must follow ten seconds on the production progress clock", progressStart >= 0 && state.getDouble("storePositionMs") - progressStart >= 10000);
      assertFalse(state.getBoolean("paused"));
      scenario.passed = true;
    }
  }
}

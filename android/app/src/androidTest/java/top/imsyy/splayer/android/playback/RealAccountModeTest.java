package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import android.net.Uri;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Real account only: no fixture authentication, credential replacement or logout. */
@RunWith(AndroidJUnit4.class)
public class RealAccountModeTest {
  private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private MainActivity activity;
  private final JSONArray observations=new JSONArray();
  private final JSONArray naturalCases=new JSONArray();
  private JSONObject json(String expression) throws Exception {
    return new JSONObject((String)new JSONTokener(js("JSON.stringify("+expression+")")).nextValue());
  }
  private void await(String expression,long timeout) throws Exception {
    long until=SystemClock.elapsedRealtime()+timeout;
    while(SystemClock.elapsedRealtime()<until) {
      if("true".equals(js(expression)))return;
      SystemClock.sleep(100);
    }
    fail("Real-account condition did not complete: "+expression+"; preparation="+js("window.realAccountPreparationStage||null"));
  }
  private static Object field(Object object,String name) throws Exception {
    var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
  }
  private JSONObject nativeSample(PlaybackManager manager,boolean background) throws Exception {
    JSONObject[] result={null};
    inst.runOnMainSync(()->{
      try {
        if(background)assertEquals("Real account observation must remain STOPPED",Stage.STOPPED,
            ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity));
        var state=manager.buildState();
        PlaybackQueue queue=(PlaybackQueue)field(manager,"playbackQueue");
        FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
        JSONArray heads=new JSONArray();
        if(overlay!=null)for(int i=0;i<Math.min(3,overlay.lrcLines.size());i++) {
          StringBuilder text=new StringBuilder();for(var word:overlay.lrcLines.get(i).words)text.append(word.text);
          heads.put(text.toString());
        }
        var item=manager.getSession().getPlayer().getCurrentMediaItem();
        result[0]=new JSONObject().put("atMs",SystemClock.elapsedRealtime()).put("songId",state.optLong("songId"))
            .put("positionMs",state.optLong("positionMs")).put("durationMs",state.optLong("durationMs"))
            .put("rate",state.optDouble("playbackRate")).put("playing",state.optBoolean("playing"))
            .put("queueSongId",queue.current()==null?0:queue.current().songId)
            .put("queueIndex",queue.current()==null?-1:queue.current().playListIndex)
            .put("nextSongId",queue.peekNextTrack()==null?0:queue.peekNextTrack().songId)
            .put("overlayShown",manager.isFloatingLyricRunning()).put("lyrics",heads)
            .put("mediaUriMatchesLoadedSource",item!=null&&item.localConfiguration!=null
                &&item.localConfiguration.uri.equals(Uri.parse(state.optString("src"))));
      }catch(Exception error){throw new AssertionError(error);}
    });
    observations.put(result[0]);return result[0];
  }
  private JSONObject snapshot() throws Exception {
    JSONObject state=json("realAccountSnapshot()");
    JSONObject account=state.getJSONObject("account");
    assertTrue("Existing account must stay logged in",account.getBoolean("loggedIn"));
    assertTrue("Account must never change",account.getBoolean("sameAccount"));
    assertTrue("The test must never replace credentials",account.getBoolean("credentialsUnchanged"));
    JSONArray list=state.getJSONArray("list");
    assertEquals("Visible queue position must identify current track",state.getLong("songId"),list.getLong(state.getInt("index")));
    return state;
  }
  private JSONObject mode(PlaybackManager manager,String value) throws Exception {
    js("window.realModeDone=false;window.realModeError=null;realAccountSetMode("+JSONObject.quote(value)+").then(()=>realModeDone=true).catch(e=>realModeError=e.name)");
    await("window.realModeDone||!!window.realModeError",45000);
    assertEquals("null",js("window.realModeError||null"));
    JSONObject state=snapshot();assertEquals(value,state.getString("mode"));
    JSONObject actual=nativeSample(manager,false);
    assertEquals(state.getLong("songId"),actual.getLong("songId"));
    assertEquals(state.getLong("songId"),actual.getLong("queueSongId"));
    assertEquals(state.getInt("index"),actual.getInt("queueIndex"));
    if(state.getInt("index")+1<state.getJSONArray("list").length())
      assertEquals(state.getJSONArray("list").getLong(state.getInt("index")+1),actual.getLong("nextSongId"));
    if(value.equals("heartbeat"))assertEquals("Real recommendation response must own committed queue", "true",
        js("(()=>{const response=realAccountHeartResponses.filter(r=>r.code===200&&r.ids.length).at(-1);return !!response&&realAccountSnapshot().list.slice(1).every(id=>response.ids.includes(id))})()"));
    return state;
  }
  private JSONObject awaitIdentity(PlaybackManager manager,long expected,long before,String official,boolean background,long timeout) throws Exception {
    long until=SystemClock.elapsedRealtime()+timeout;JSONObject last=null;
    while(SystemClock.elapsedRealtime()<until) {
      last=nativeSample(manager,background);long id=last.getLong("songId");
      if(background)assertTrue("Natural next must follow the real committed queue",id==before||id==expected);
      if(id==expected&&last.getBoolean("playing")&&last.getLong("positionMs")>300
          &&last.getJSONArray("lyrics").length()>0) {
        assertTrue("Actual Media3 item must match loaded source",last.getBoolean("mediaUriMatchesLoadedSource"));
        assertTrue("Desktop lyrics must remain attached",last.getBoolean("overlayShown"));
        if(official!=null) {
          String head=last.getJSONArray("lyrics").getString(0).replaceAll("\\s+","");
          assertTrue("Current native lyric must belong to the independently fetched real song",!head.isEmpty()&&official.replaceAll("\\s+","").contains(head));
        }
        return last;
      }
      SystemClock.sleep(250);
    }
    fail("Real next audio/lyric identity did not settle: "+last);return last;
  }
  private String js(String expression) throws Exception {
    CountDownLatch done=new CountDownLatch(1);String[] result={null};
    inst.runOnMainSync(()->web.evaluateJavascript(expression,value->{result[0]=value;done.countDown();}));
    assertTrue("Account-safe WebView observation must complete",done.await(10,TimeUnit.SECONDS));
    return result[0];
  }
  private void background() throws Exception {
    if(!"true".equals(InstrumentationRegistry.getArguments().getString("cover_background"))) {
      DeviceWebFixture.moveToBackground(inst,activity);return;
    }
    // Keep another actual app surface in front on MuMu displays whose launcher
    // can bring the music task back after idle. Never relax the STOPPED check.
    Intent cover=new Intent(inst.getContext(),BackgroundCoverActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    var options=android.app.ActivityOptions.makeBasic();
    inst.runOnMainSync(()->options.setLaunchDisplayId(activity.getDisplay().getDisplayId()));
    inst.getContext().startActivity(cover,options.toBundle());
    Stage[] stage={null};long until=SystemClock.elapsedRealtime()+5000;
    do {
      inst.runOnMainSync(()->stage[0]=ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity));
      if(stage[0]==Stage.STOPPED)return;
      SystemClock.sleep(100);
    }while(SystemClock.elapsedRealtime()<until);
    assertEquals("Opaque foreground app must stop the player Activity",Stage.STOPPED,stage[0]);
  }
  @Test public void currentRealAccountModeSequence() throws Exception {
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web=activity.getBridge().getWebView();
    long deadline=SystemClock.elapsedRealtime()+30000;
    while(!"true".equals(js("!!document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.get('data')"))
        &&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
    // Account identity and credential remain inside this closure. Only booleans leave it.
    assertEquals("true",js("(()=>{const p=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia;const d=p._s.get('data');const uid=d.userData.userId;const type=d.loginType;const cookie=localStorage.getItem('cookie-MUSIC_U');window.realAccountWitness=()=>({loggedIn:!!d.userLoginStatus&&d.userData.userId>0,authenticated:type!=='uid'&&!!cookie,sameAccount:d.userData.userId===uid&&d.loginType===type,credentialsUnchanged:localStorage.getItem('cookie-MUSIC_U')===cookie});window.realAccountPinia=p;return true})()"));
    String facts=js("JSON.stringify(realAccountWitness())");
    System.out.println("REAL_ACCOUNT_SAFE_PROBE "+facts);
    boolean matrix="true".equals(InstrumentationRegistry.getArguments().getString("natural_matrix"));
    js("window.realNaturalMatrix="+matrix);
    assertEquals("The test must use the existing authenticated account", "true",
        js("realAccountWitness().loggedIn&&realAccountWitness().authenticated&&realAccountWitness().sameAccount&&realAccountWitness().credentialsUnchanged"));
    if("true".equals(InstrumentationRegistry.getArguments().getString("proxy_guard"))) {
      // Query the navigation decision only: never load the URL or execute HTML.
      // The inert destination is not contacted and no credential is inspected.
      Uri app=Uri.parse(activity.getBridge().getAppUrl());
      Uri proxy=app.buildUpon().path("/_capacitor_http_interceptor_")
          .clearQuery().appendQueryParameter("u","http://127.0.0.1:1/inert-review-marker").build();
      boolean[] blocked={false};
      inst.runOnMainSync(()->blocked[0]=activity.getBridge().launchIntent(proxy));
      for (String path : new String[] {"/_capacitor_http_interceptor_/suffix", "/%5Fcapacitor_http_interceptor_"}) {
        Uri variant=proxy.buildUpon().encodedPath(path).build();
        inst.runOnMainSync(()->assertTrue("Proxy path variants must be blocked",activity.getBridge().launchIntent(variant)));
      }
      inst.runOnMainSync(()->assertFalse("Ordinary app navigation must keep normal handling",
          activity.getBridge().launchIntent(app.buildUpon().path("/ordinary-app-page").build())));
      assertEquals("true",js("realAccountWitness().loggedIn&&realAccountWitness().sameAccount&&realAccountWitness().credentialsUnchanged"));
      System.out.println("REAL_ACCOUNT_PROXY_GUARD blocked="+blocked[0]+" navigated=false accountUnchanged=true");
      js("delete window.realAccountWitness");
      assertTrue("Frame navigation to the internal HTTP proxy must be blocked",blocked[0]);
      return;
    }
    if("true".equals(InstrumentationRegistry.getArguments().getString("probe")))return;
    assertTrue("Desktop lyric test requires overlay permission before changing playback: "
        +inst.getTargetContext().getPackageName(),android.provider.Settings.canDrawOverlays(activity));
    PlaybackManager manager=PlaybackManager.getInstance(activity);
    boolean passed=false;
    try {
      try(var input=inst.getContext().getAssets().open("real-account-mode.js")) {
        js(new String(input.readAllBytes(),StandardCharsets.UTF_8));
      }
      js("prepareRealAccountMode().catch(e=>window.realAccountModeError=e.message)");
      await("window.realAccountModeReady||!!window.realAccountModeError",90000);
      assertEquals("Real account playlist preparation", "null",js("window.realAccountModeError||null"));
      JSONObject state=snapshot();
      awaitIdentity(manager,state.getLong("songId"),state.getLong("songId"),null,false,30000);
      if("true".equals(InstrumentationRegistry.getArguments().getString("prepare_only"))) {
        passed=true;return;
      }
      if(!matrix) {
      for(int cycle=0;cycle<2;cycle++)for(String value:new String[]{"on","heartbeat","on","heartbeat","off"})mode(manager,value);
      js("window.realRapidDone=false;Promise.all(['on','heartbeat','off','on','heartbeat','off'].map(value=>window.__SPLAYER_PLAYER_CONTROLLER__.toggleShuffle(value))).then(()=>realRapidDone=true)");
      await("window.realRapidDone===true",45000);assertEquals("off",snapshot().getString("mode"));
      state=mode(manager,"heartbeat");
      long first=state.getLong("songId"),next=state.getJSONArray("list").getLong(state.getInt("index")+1);
      for(String command:new String[]{"next","prev"}) {
        js("window.realManualDone=false;window.__SPLAYER_PLAYER_CONTROLLER__.nextOrPrev("+JSONObject.quote(command)+").then(()=>realManualDone=true)");
        await("window.realManualDone===true",45000);
        awaitIdentity(manager,command.equals("next")?next:first,first,null,false,30000);snapshot();
      }
      inst.runOnMainSync(()->manager.handleNotificationAction(PlaybackConstants.ACTION_NEXT));
      awaitIdentity(manager,next,first,null,false,30000);
      }
      String[][] paths=matrix ? new String[][]{{"off","heartbeat"},{"heartbeat","off"},{"off","on"},{"on","off"},{"heartbeat","on"}}
          :new String[][]{{"on","heartbeat"},{"on","heartbeat"}};
      int start=Integer.parseInt(InstrumentationRegistry.getArguments().getString("case_start","0"));
      assertTrue("At least one configured natural-next path must run",start>=0&&start<paths.length);
      boolean restart="true".equals(InstrumentationRegistry.getArguments().getString("restart_each_case"));
      for(int caseIndex=start;caseIndex<paths.length;caseIndex++) {
        String[] path=paths[caseIndex];
        if(restart) {
          js("window.realRestartDone=false;window.realRestartError=null;realAccountRestartCase().then(()=>realRestartDone=true).catch(e=>realRestartError=e.name)");
          await("window.realRestartDone||!!window.realRestartError",60000);
          assertEquals("null",js("window.realRestartError||null"));
        }
        for(String value:path)state=mode(manager,value);
        JSONObject preflight=null;
        if(restart) {
          JSONArray rejected=new JSONArray();
          for(int attempt=0;;attempt++) {
          js("window.realSourceCheck=null;window.realSourceError=null;realAccountCheckNextSource().then(value=>realSourceCheck=value).catch(e=>realSourceError=e.name)");
          await("!!window.realSourceCheck||!!window.realSourceError",45000);
          assertEquals("null",js("window.realSourceError||null"));
          preflight=json("realSourceCheck");
          if(preflight.getBoolean("available"))break;
          rejected.put(preflight);
          assertTrue("Real next sources unavailable under current trial policy: "+rejected,attempt<4);
          mode(manager,"off");
          for(String value:path)state=mode(manager,value);
          }
          preflight.put("rejectedCandidates",rejected);
        }
        long before=state.getLong("songId"),expected=state.getJSONArray("list").getLong(state.getInt("index")+1);
        JSONObject actual=awaitIdentity(manager,before,before,null,false,30000);
        js("window.realExpectedLyric=null;window.realExpectedError=null;realAccountRequest({url:'/lyric/new',params:{id:"+expected+",noCookie:true}}).then(r=>realExpectedLyric=(r.lrc?.lyric||'')+(r.yrc?.lyric||'')).catch(e=>realExpectedError=e.name)");
        await("window.realExpectedLyric!==null||!!window.realExpectedError",25000);
        assertEquals("null",js("window.realExpectedError||null"));
        String official=(String)new JSONTokener(js("realExpectedLyric")).nextValue();
        assertTrue("Expected real track must have independently available lyrics",official.length()>20);
        background();
        long remaining=Math.max(0,actual.getLong("durationMs")-actual.getLong("positionMs"));
        JSONObject nextActual=awaitIdentity(manager,expected,before,official,true,Math.min(600000,(long)(remaining/actual.optDouble("rate",1))+45000));
        if(preflight!=null&&preflight.optLong("durationMs")>0)
          assertTrue("Actual audio duration must match the independent real song URL response",
              Math.abs(nextActual.getLong("durationMs")-preflight.getLong("durationMs"))<2000);
        activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        await("realAccountSnapshot().songId==="+expected+"&&!realAccountSnapshot().lyricLoading",30000);
        JSONObject visible=snapshot();
        assertEquals("Natural next must keep the selected playback mode",path[path.length-1],visible.getString("mode"));
        JSONArray lines=visible.getJSONArray("lyrics");
        assertTrue("Foreground lyrics must be present after natural next",lines.length()>0);
        String head=lines.getString(0).replaceAll("\\s+","");
        assertTrue("Foreground lyrics must belong to the independently fetched next song",!head.isEmpty()&&official.replaceAll("\\s+","").contains(head));
        JSONObject result=new JSONObject().put("path",new JSONArray(path)).put("before",before).put("expected",expected)
            .put("nativeAfter",nextActual).put("visibleAfter",visible).put("rate",actual.optDouble("rate",1));
        if(preflight!=null)result.put("sourcePreflight",preflight);
        naturalCases.put(result);
        System.out.println("NATURAL_MODE_CASE "+result);
        js("window.realOverlayDone=false;(async()=>{await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(false);await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(true);realOverlayDone=true})()");
        await("window.realOverlayDone===true",15000);
      }
      assertEquals("Every configured natural-next path must complete",paths.length-start,naturalCases.length());
      if(start==0)assertEquals("true",js("realAccountHeartResponses.filter(r=>r.code===200&&r.ids.length).length>="+(matrix?3:4)));
      passed=true;
    } finally {
      activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      File dir=new File(activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
      JSONObject report=new JSONObject().put("passed",passed).put("naturalCases",naturalCases).put("nativeSamples",observations)
          .put("preparationOnly","true".equals(InstrumentationRegistry.getArguments().getString("prepare_only")))
          .put("preparationStage",new JSONTokener(js("window.realAccountPreparationStage||null")).nextValue())
          .put("modeTrace",new JSONTokener((String)new JSONTokener(js("JSON.stringify(window.realAccountModeTrace||[])")).nextValue()).nextValue())
          .put("realHeartResponses",new JSONTokener((String)new JSONTokener(js("JSON.stringify(window.realAccountHeartResponses||[])")).nextValue()).nextValue());
      js("window.realAccountPlaybackRestored=false;restoreRealAccountPlayback().catch(e=>window.realRestoreError=e.name)");
      await("window.realAccountPlaybackRestored||!!window.realRestoreError",90000);
      report.put("accountAfter",json("realAccountWitness()"));
      report.put("restoreSucceeded","true".equals(js("window.realAccountPlaybackRestored===true")));
      try(PrintWriter out=new PrintWriter(new File(dir,"real-account-mode-"+System.currentTimeMillis()+".json"),"UTF-8")){out.print(report);}
      assertEquals("Playback settings must be restored without touching the account","null",js("window.realRestoreError||null"));
      assertEquals("Existing real account and credentials must survive testing","true",js("realAccountWitness().loggedIn&&realAccountWitness().sameAccount&&realAccountWitness().credentialsUnchanged"));
      System.out.println("REAL_ACCOUNT_SAFE_FINAL "+js("JSON.stringify(realAccountWitness())"));
      js("delete window.realAccountWitness");
    }
  }
}

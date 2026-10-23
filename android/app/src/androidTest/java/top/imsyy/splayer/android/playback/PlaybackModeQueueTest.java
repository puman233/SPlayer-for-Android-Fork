package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual native queue, delayed HTTP replies and production lyric synchronization. */
@RunWith(AndroidJUnit4.class)
public class PlaybackModeQueueTest {
  private static Object field(Object object,String name) throws Exception {
    var declared=object.getClass().getDeclaredField(name);declared.setAccessible(true);return declared.get(object);
  }

  private static final class ModeScenario implements AutoCloseable {
    final DeviceWebFixture f=new DeviceWebFixture();
    final PlaybackTransitionTest.AudioServer server=new PlaybackTransitionTest.AudioServer(true,30);
    final PlaybackManager manager=PlaybackManager.getInstance(f.activity);
    float savedRate;
    long expectedBeforeAdvance;
    long expectedIntermediate;
    boolean sawIntermediate;
    long identityDeadlineMs=20000;
    final JSONArray nativeSamples=new JSONArray();
    ModeScenario() throws Exception {
      f.inst.runOnMainSync(()->savedRate=(float)manager.buildState().optDouble("playbackRate",1));
      for(String asset:new String[]{"r23-player-fixture.js","http-lyric-sequence.js","play-mode-sequence.js"})
        try(var input=f.inst.getContext().getAssets().open(asset)){f.js(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("prepareModeSequence("+JSONObject.quote("http://127.0.0.1:"+server.server.getLocalPort())+",30000).catch(e=>window.modeError=e.name)");
      f.await("window.modeReady||!!window.modeError");
      assertEquals("null",f.js("window.modeError||null"));
      f.await("fixtureStatus.playStatus&&fixtureStatus.currentTime>0");
    }
    JSONObject mode(String value) throws Exception {
      f.js("window.modeApplied=false;modeSet("+JSONObject.quote(value)+").then(state=>{window.modeLast=state;window.modeApplied=true}).catch(e=>window.modeError=e.name)");
      f.await("window.modeApplied||!!window.modeError");assertEquals("null",f.js("window.modeError||null"));
      JSONObject state=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(window.modeLast)")).nextValue());
      assertEquals(value,state.getString("mode"));
      assertQueue(state);
      return state;
    }
    void assertQueue(JSONObject state) throws Exception {
      JSONArray list=state.getJSONArray("list");int index=state.getInt("index");long song=state.getLong("songId");
      assertEquals("Visible index must identify the playing song",song,list.getLong(index));
      f.inst.runOnMainSync(()->{
        try {
          PlaybackQueue queue=(PlaybackQueue)field(manager,"playbackQueue");
          assertNotNull(queue.current());
          assertEquals("Actual native media must match the visible current track",song,manager.buildState().optLong("songId"));
          assertEquals("Native cursor must match the committed mode",song,queue.current().songId);
          assertEquals(index,queue.current().playListIndex);
          PlaybackQueue.Track next=queue.peekNextTrack();
          if(index+1<list.length()) {
            assertNotNull("Native next candidate must exist",next);
            assertEquals("Native next candidate must follow the displayed mode order",list.getLong(index+1),next.songId);
          } else assertNull("Repeat-off list end must not wrap",next);
        }catch(Exception error){throw new AssertionError(error);}
      });
    }
    void awaitIdentity(long song,boolean background) throws Exception {
      boolean[] matched={false};long deadline=SystemClock.elapsedRealtime()+identityDeadlineMs;
      while(!matched[0]&&SystemClock.elapsedRealtime()<deadline) {
        f.inst.runOnMainSync(()->{
          try {
            if(background)assertEquals(androidx.test.runner.lifecycle.Stage.STOPPED,
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(f.activity));
            var state=manager.buildState();
            PlaybackQueue queue=(PlaybackQueue)field(manager,"playbackQueue");
            nativeSamples.put(new JSONObject().put("atMs",SystemClock.elapsedRealtime()).put("songId",state.optLong("songId"))
                .put("positionMs",state.optLong("positionMs")).put("durationMs",state.optLong("durationMs"))
                .put("playing",state.optBoolean("playing")).put("rate",state.optDouble("playbackRate"))
                .put("queueSongId",queue.current()==null?0:queue.current().songId)
                .put("pendingRefill",field(manager,"pendingResumeAfterRefill")));
            if(background)assertTrue("Natural completion must not start an unrelated candidate",
                state.optLong("songId")==expectedBeforeAdvance||state.optLong("songId")==expectedIntermediate||state.optLong("songId")==song);
            if(expectedIntermediate>0&&state.optLong("songId")==expectedIntermediate)sawIntermediate=true;
            FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
            String lyric=overlay==null||overlay.lrcLines.isEmpty()?"":overlay.lrcLines.get(0).words.get(0).text;
            if(!lyric.isEmpty())assertEquals("Lyrics must follow confirmed native identity","HTTP lyric "+state.optLong("songId"),lyric);
            matched[0]=state.optLong("songId")==song&&state.optBoolean("playing")&&lyric.equals("HTTP lyric "+song);
            if(matched[0]) {
              var item=manager.getSession().getPlayer().getCurrentMediaItem();assertNotNull(item);assertNotNull(item.localConfiguration);
              assertTrue("Actual audio URI must belong to the expected next song",item.localConfiguration.uri.getPath().contains("song"+song+".wav"));
            }
          }catch(Exception error){throw new AssertionError(error);}
        });
        SystemClock.sleep(100);
      }
      assertTrue("Mode-selected native audio and lyrics must both match "+song+"; last="+
          (nativeSamples.length()==0?"none":nativeSamples.opt(nativeSamples.length()-1)),matched[0]);
    }
    void prepareLargeTail() throws Exception {
      f.js("window.modeLargeReady=false;const large=Array.from({length:202},(_,i)=>({...modeInitialSongs[0],id:991700+i,name:'HTTP sequence '+(991700+i)}));window.__SPLAYER_PLAYER_CONTROLLER__.toggleRepeat('list');window.__SPLAYER_PLAYER_CONTROLLER__.updatePlayList(large,large[200],991799,{play:true,showTip:false}).then(()=>window.modeLargeReady=true).catch(e=>window.modeError=e.name)");
      f.await("window.modeLargeReady||!!window.modeError");assertEquals("null",f.js("window.modeError||null"));
      f.await("fixtureStatus.currentTime>0&&fixturePinia._s.get('music').playSong.id===991900");
      expectedBeforeAdvance=991900;expectedIntermediate=991901;
      f.inst.runOnMainSync(()->{
        manager.setRate(3);
        var state=manager.buildState();
        identityDeadlineMs=(long)Math.ceil(2*state.optLong("durationMs")/state.optDouble("playbackRate",1))+10000;
      });
    }
    @Override public void close() throws Exception {
      server.holdHeartResponses=false;
      f.inst.runOnMainSync(()->manager.setRate(savedRate));
      f.activity.startActivity(new Intent(f.activity,top.imsyy.splayer.android.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
      File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
      try(PrintWriter out=new PrintWriter(new File(dir,"mode-transitions-"+System.currentTimeMillis()+".json"),"UTF-8")) {
        out.print((String)new JSONTokener(f.js("JSON.stringify(window.modeTrace||[])")).nextValue());
      }
      try(PrintWriter out=new PrintWriter(new File(dir,"mode-native-samples-"+System.currentTimeMillis()+".json"),"UTF-8")){out.print(nativeSamples);}
      f.restoreNativeApiContext();
      try {f.js("restoreModeSequence().then(()=>window.modeRestored=true).catch(e=>window.modeRestoreError=e.name)");
        f.await("window.modeRestored||!!window.modeRestoreError");assertEquals("null",f.js("window.modeRestoreError||null"));
      }finally{server.close();f.close();}
    }
  }

  @Test public void repeatedShuffleHeartOffKeepsNativeOrderAndBackgroundNaturalLyrics() throws Exception {
    try(ModeScenario s=new ModeScenario()) {
      for(int cycle=0;cycle<3;cycle++)for(String mode:new String[]{"on","heartbeat","on","heartbeat","off"})s.mode(mode);
      JSONObject state=s.mode("heartbeat");
      long expected=state.getJSONArray("list").getLong(state.getInt("index")+1);
      s.expectedBeforeAdvance=state.getLong("songId");
      s.f.inst.runOnMainSync(()->s.manager.setRate(3));
      s.f.moveToBackground();
      assertEquals("true",s.f.js("document.hidden"));
      s.awaitIdentity(expected,true);
      s.f.inst.runOnMainSync(()->s.manager.setRate(1));
    }
  }

  @Test public void aLargeQueueNaturallyWrapsThroughRefillWithoutReplayingItsLastTrack() throws Exception {
    try(ModeScenario s=new ModeScenario()) {
      s.prepareLargeTail();
      s.f.moveToBackground();assertEquals("true",s.f.js("document.hidden"));
      s.awaitIdentity(991700,true);
      assertTrue("The original last track must play before wrapping",s.sawIntermediate);
      s.f.await("fixturePinia._s.get('music').playSong.id===991700&&fixtureStatus.playIndex===0");
    }
  }

  @Test public void nativePauseCancelsARealPendingWrapAndExplicitNextStillStartsTheFirstTrack() throws Exception {
    try(ModeScenario s=new ModeScenario()) {
      s.prepareLargeTail();s.server.heldUrlSongId=991700;s.server.holdUrlResponses=true;
      s.f.moveToBackground();
      boolean[] pending={false};long deadline=SystemClock.elapsedRealtime()+25000;
      while(!pending[0]&&SystemClock.elapsedRealtime()<deadline) {
        s.f.inst.runOnMainSync(()->{
          try {pending[0]=(Boolean)field(s.manager,"pendingResumeAfterRefill")
              &&s.manager.buildState().optLong("songId")==991901&&s.server.heldUrlRequests.get()>0;}
          catch(Exception error){throw new AssertionError(error);}
        });
        SystemClock.sleep(25);
      }
      assertTrue("A real wrap HTTP request must be pending",pending[0]);
      s.f.inst.runOnMainSync(()->s.manager.pause());
      s.server.holdUrlResponses=false;
      long quietUntil=SystemClock.elapsedRealtime()+8000;
      while(SystemClock.elapsedRealtime()<quietUntil) {
        s.f.inst.runOnMainSync(()->{
          try {
            assertFalse("Returning HTTP must not resume native pause",s.manager.buildState().optBoolean("playing"));
            assertEquals(991901,s.manager.buildState().optLong("songId"));
            assertEquals("Stale wrap must not move the paused cursor",991901,((PlaybackQueue)field(s.manager,"playbackQueue")).current().songId);
          }catch(Exception error){throw new AssertionError(error);}
        });
        SystemClock.sleep(100);
      }
      s.expectedBeforeAdvance=991901;s.expectedIntermediate=0;
      s.f.inst.runOnMainSync(()->s.manager.handleNotificationAction(PlaybackConstants.ACTION_NEXT));
      s.awaitIdentity(991700,true);
    }
  }

  @Test public void rapidModesCancelOldHeartReplyAndManualAndSessionNextUseTheCommittedOrder() throws Exception {
    try(ModeScenario s=new ModeScenario()) {
      s.mode("on");s.server.holdHeartResponses=true;
      s.f.js("window.modeHeartDone=false;modeSet('heartbeat').then(()=>window.modeHeartDone=true).catch(e=>window.modeError=e.name)");
      long deadline=SystemClock.elapsedRealtime()+10000;
      while(s.server.heartRequests.get()==0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(25);
      assertEquals("A real heart recommendation request must be pending",1,s.server.heartRequests.get());
      JSONObject state=s.mode("off");
      s.server.holdHeartResponses=false;s.f.await("window.modeHeartDone===true");
      s.f.js("window.modeRapidDone=false;Promise.all(['on','heartbeat','off','on','heartbeat','off'].map(mode=>window.__SPLAYER_PLAYER_CONTROLLER__.toggleShuffle(mode))).then(()=>window.modeRapidDone=true)");
      s.f.await("window.modeRapidDone===true");
      state=s.mode("heartbeat");
      long first=state.getLong("songId"),next=state.getJSONArray("list").getLong(state.getInt("index")+1);
      s.f.js("window.modeManualDone=false;window.__SPLAYER_PLAYER_CONTROLLER__.nextOrPrev('next').then(()=>window.modeManualDone=true)");
      s.f.await("window.modeManualDone===true");s.awaitIdentity(next,false);
      s.f.js("window.modeManualDone=false;window.__SPLAYER_PLAYER_CONTROLLER__.nextOrPrev('prev').then(()=>window.modeManualDone=true)");
      s.f.await("window.modeManualDone===true");s.awaitIdentity(first,false);
      s.f.inst.runOnMainSync(()->s.manager.handleNotificationAction(PlaybackConstants.ACTION_NEXT));
      s.awaitIdentity(next,false);
    }
  }

  @Test public void replacingPlaylistCancelsPendingHeartWithoutReloadingTheCurrentAudio() throws Exception {
    try(ModeScenario s=new ModeScenario()) {
      s.mode("on");s.server.holdHeartResponses=true;
      s.f.js("window.modeHeartDone=false;modeSet('heartbeat').then(()=>window.modeHeartDone=true).catch(e=>window.modeError=e.name)");
      long deadline=SystemClock.elapsedRealtime()+10000;
      while(s.server.heartRequests.get()==0&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(25);
      assertEquals(1,s.server.heartRequests.get());
      s.f.js("window.modeReplacementDone=false;window.__SPLAYER_PLAYER_CONTROLLER__.updatePlayList(modeInitialSongs.slice().reverse(),fixturePinia._s.get('music').playSong,991799,{play:true,showTip:false}).then(()=>window.modeReplacementDone=true).catch(e=>window.modeError=e.name)");
      s.f.await("window.modeReplacementDone||!!window.modeError");assertEquals("null",s.f.js("window.modeError||null"));
      s.server.holdHeartResponses=false;s.f.await("window.modeHeartDone===true");
      JSONObject state=new JSONObject((String)new JSONTokener(s.f.js("JSON.stringify({mode:fixtureStatus.shuffleMode,list:fixturePinia._s.get('data').playList.map(song=>song.id),songId:fixturePinia._s.get('music').playSong.id,index:fixtureStatus.playIndex})")).nextValue());
      assertEquals("on",state.getString("mode"));assertEquals(6,state.getJSONArray("list").length());
      for(int i=0;i<6;i++)assertTrue(state.getJSONArray("list").getLong(i)<991706);
      s.assertQueue(state);
      assertEquals("A same-song queue replacement must keep its real playback position","true",s.f.js("fixtureStatus.currentTime>0"));
    }
  }
  @Test public void replacingOrderWhileNextUrlResolvesCannotPlayTheObsoleteCandidate() throws Exception {
    assertPendingReorder(false);
  }
  @Test public void replacingOrderAtRepeatOffEndStopsWithoutWrappingOrOldReply() throws Exception {
    assertPendingReorder(true);
  }
  private void assertPendingReorder(boolean atEnd) throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture();
        PlaybackTransitionTest.AudioServer server=new PlaybackTransitionTest.AudioServer(true,4)) {
      for(String asset:new String[]{"r23-player-fixture.js","http-lyric-sequence.js"})
        try(var input=f.inst.getContext().getAssets().open(asset)){f.js(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      server.firstSongSeconds=1;
      server.holdAudioBody=true;server.heldUrlSongId=991701;server.holdUrlResponses=true;
      server.heldAudioSongId=991702;server.holdSelectedAudioBody=true;
      JSONArray samples=new JSONArray();
      try {
        f.js("prepareHttpLyricSequence("+JSONObject.quote("http://127.0.0.1:"+server.server.getLocalPort())+",3,4000,{beforePlay:()=>{fixturePinia._s.get('data').playList[0].duration=1000}}).catch(e=>window.httpSequenceError=e.name)");
        f.await("window.httpSequenceReady||!!window.httpSequenceError");
        assertEquals("null",f.js("window.httpSequenceError||null"));
        f.inst.runOnMainSync(()->{
          try {assertEquals("Actual native resolver must use this scenario's real HTTP server",
              server.server.getLocalPort(),android.net.Uri.parse((String)field(manager,"apiBaseUrl")).getPort());}
          catch(Exception error){throw new AssertionError(error);}
        });
        f.moveToBackground();server.holdAudioBody=false;
        boolean[] resolving={false};
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(!resolving[0]&&SystemClock.elapsedRealtime()<deadline) {
          f.inst.runOnMainSync(()->{
            try {
              ExoPlayer player=(ExoPlayer)field(manager,"player");
              PlaybackQueue queue=(PlaybackQueue)field(manager,"playbackQueue");
              resolving[0]=player.getPlaybackState()==Player.STATE_ENDED&&queue.current()!=null
                  &&queue.current().songId==991701&&manager.buildState().optLong("songId")==991700;
            }catch(Exception error){throw new AssertionError(error);}
          });
          SystemClock.sleep(25);
        }
        if(!resolving[0]) {
          final String[] diagnostic={""};
          f.inst.runOnMainSync(()->{
            try {
              var state=manager.buildState();var queue=(PlaybackQueue)field(manager,"playbackQueue");
              var player=(ExoPlayer)field(manager,"player");
              diagnostic[0]=new JSONObject().put("songId",state.optLong("songId")).put("durationMs",state.optLong("durationMs"))
                  .put("positionMs",state.optLong("positionMs")).put("playing",state.optBoolean("playing"))
                  .put("playerState",player.getPlaybackState()).put("queueSongId",queue.current()==null?0:queue.current().songId)
                  .put("sourcePath",android.net.Uri.parse(state.optString("src")).getPath()).put("urlRequests",server.urlRequests.get()).toString();
            }catch(Exception error){throw new AssertionError(error);}
          });
          fail("Actual end must be waiting for the original next URL: "+diagnostic[0]);
        }
        f.activity.startActivity(new Intent(f.activity,top.imsyy.splayer.android.MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        String reordered=atEnd?"[d.playList[1],d.playList[2],d.playList[0]]":"[d.playList[0],d.playList[2],d.playList[1]]";
        f.js("(async()=>{const d=fixturePinia._s.get('data');const list="+reordered+";fixtureStatus.playIndex="+(atEnd?2:0)+";await d.setPlayList(list);await window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext(fixturePinia._s.get('music').playSong);window.modeQueueReordered=true})().catch(e=>window.modeQueueError=e.name)");
        f.await("window.modeQueueReordered||!!window.modeQueueError");
        assertEquals("null",f.js("window.modeQueueError||null"));
        f.moveToBackground();
        server.holdUrlResponses=false;server.holdSelectedAudioBody=false;
        boolean[] correct={false};
        deadline=SystemClock.elapsedRealtime()+12000;
        while(!correct[0]&&SystemClock.elapsedRealtime()<deadline) {
          f.inst.runOnMainSync(()->{
            try {
              var state=manager.buildState();
              assertNotEquals("An obsolete resolved candidate must never start",991701,state.optLong("songId"));
              FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
              String lyric=overlay==null||overlay.lrcLines.isEmpty()?"":overlay.lrcLines.get(0).words.get(0).text;
              samples.put(new JSONObject().put("songId",state.optLong("songId")).put("playing",state.optBoolean("playing")).put("lyric",lyric));
              correct[0]=atEnd ? state.optLong("songId")==991700&&!state.optBoolean("playing")&&state.optLong("positionMs")>=server.firstSongSeconds*1000-500
                  : state.optLong("songId")==991702&&state.optBoolean("playing")&&lyric.equals("HTTP lyric 991702");
              if(correct[0]&&!atEnd) {
                var item=manager.getSession().getPlayer().getCurrentMediaItem();assertNotNull(item);assertNotNull(item.localConfiguration);
                assertTrue(item.localConfiguration.uri.getPath().contains("song991702.wav"));
              }
            }catch(Exception error){throw new AssertionError(error);}
          });
          SystemClock.sleep(100);
        }
        assertTrue("Committed order must play C, or stop at repeat-off end, without the obsolete B callback",correct[0]);
        long quietUntil=SystemClock.elapsedRealtime()+(atEnd?8000:1500);
        while(SystemClock.elapsedRealtime()<quietUntil) {
          f.inst.runOnMainSync(()->{
            assertEquals("A late reply must not replace the committed outcome",atEnd?991700:991702,manager.buildState().optLong("songId"));
            if(atEnd)assertFalse(manager.buildState().optBoolean("playing"));
          });
          SystemClock.sleep(100);
        }
        if(atEnd) {
          f.await("fixturePinia._s.get('music').playSong.id===991700&&fixtureStatus.playIndex===2&&!fixtureStatus.playStatus&&!fixtureStatus.playLoading");
        }
      } finally {
        server.holdUrlResponses=false;server.holdAudioBody=false;server.holdSelectedAudioBody=false;
        File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        try(PrintWriter out=new PrintWriter(new File(dir,"mode-pending-url-reorder"+(atEnd?"-end":"")+".json"),"UTF-8")){out.print(samples);}
        f.activity.startActivity(new Intent(f.activity,top.imsyy.splayer.android.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        f.restoreNativeApiContext();
        f.js("restoreHttpLyricSequence().then(()=>window.httpSequenceRestored=true)");f.await("window.httpSequenceRestored===true");
        f.inst.runOnMainSync(manager::hideFloatingLyric);
      }
    }
  }
}

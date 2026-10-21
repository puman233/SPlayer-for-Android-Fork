package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.net.Uri;
import android.os.SystemClock;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.cache.AudioCacheProvider;

/** Real queue commands and HTTP/cache playback, without deleting existing user cache. */
@RunWith(AndroidJUnit4.class)
public class PlaybackTransitionTest {
  private static final class AudioServer implements AutoCloseable {
    final ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
    final ExecutorService clients=Executors.newCachedThreadPool();
    final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    final AtomicInteger sent=new AtomicInteger(),slowRequests=new AtomicInteger();
    final Thread accept;
    final String prefix="transition-"+UUID.randomUUID();
    final boolean tones;
    AudioServer() throws Exception {this(false);}
    AudioServer(boolean tones) throws Exception {
      this.tones=tones;
      accept=new Thread(()->{while(!server.isClosed())try {
        Socket socket=server.accept();sockets.add(socket);clients.execute(()->serve(socket));
      }catch(IOException error){if(!server.isClosed())throw new AssertionError(error);}},"transition-test-http");
      accept.setDaemon(true);accept.start();
    }
    void serve(Socket socket) {
      try(socket) {
        socket.setSoTimeout(3000);
        BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
        String request=in.readLine(),line;long start=0;
        if(request==null)return;
        while((line=in.readLine())!=null&&!line.isEmpty()) {
          if(line.toLowerCase(Locale.ROOT).startsWith("range: bytes=")) start=Long.parseLong(line.substring(13).split("-")[0]);
        }
        if(tones&&request.contains("/slow.wav")){slowRequests.incrementAndGet();SystemClock.sleep(1800);}
        byte[] wave=tones?wave():null;
        int total=tones?wave.length:3*1024*1024;
        OutputStream out=socket.getOutputStream();
        String header=start>0?"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes "+start+"-"+(total-1)+"/"+total+"\r\n":"HTTP/1.1 200 OK\r\n";
        out.write((header+"Content-Length: "+(total-start)+"\r\nContent-Type: audio/wav\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        byte[] chunk=new byte[8192];Arrays.fill(chunk,(byte)(request.contains("/next.wav")?71:93));
        for(long position=start;position<total;position+=chunk.length) {
          if(tones)out.write(wave,(int)position,(int)Math.min(chunk.length,total-position));
          else out.write(chunk,0,(int)Math.min(chunk.length,total-position));
          out.flush();
          sent.addAndGet(chunk.length);SystemClock.sleep(4);
        }
      }catch(IOException expectedCancellation) {
        // Production cancellation and the intentional offline phase close these sockets.
      }finally{sockets.remove(socket);}
    }
    byte[] wave() {
      int samples=32000,bytes=samples*2;
      java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(44+bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
      b.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
      b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
      for(int i=0;i<samples;i++)b.putShort((short)(300*Math.sin(i*2*Math.PI*220/16000)));
      return b.array();
    }
    String url(String name){return "http://127.0.0.1:"+server.getLocalPort()+"/"+prefix+"/"+name+".wav";}
    public void close() throws Exception {
      server.close();for(Socket socket:sockets)socket.close();accept.join(1000);clients.shutdownNow();clients.awaitTermination(3,TimeUnit.SECONDS);
    }
  }
  private static PlaybackQueue.Track track(long id,String url) {
    PlaybackQueue.Track t=new PlaybackQueue.Track();t.songId=id;t.url=url;t.playListIndex=(int)(id-991550);t.title="Transition test "+id;return t;
  }
  @Test public void fartherResolvedSongCannotCancelImmediateNextAudioWarmup() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture();AudioServer server=new AudioServer()) {
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureStatus.showFullPlayer=false;window.transitionReady=true})");f.await("window.transitionReady===true&&!document.querySelector('.full-player')");
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      String next=server.url("next");
      try {
        f.inst.runOnMainSync(()->manager.updateQueueContext(false,true,false,true,false,false,
          Arrays.asList(track(991550,server.url("current")),track(991551,next),track(991552,server.url("farther"))),0,"off",false,false,false,false));
        long started=SystemClock.elapsedRealtime();
        while(server.sent.get()<2*1024*1024&&SystemClock.elapsedRealtime()-started<5000)SystemClock.sleep(40);
        assertTrue("Actual HTTP preloading must run",server.sent.get()>=2*1024*1024);
        SystemClock.sleep(500);server.close();
        DataSource source=AudioCacheProvider.buildCachedDataSourceFactory(f.activity).createDataSource();
        try {
          source.open(new DataSpec.Builder().setUri(Uri.parse(next)).setLength(512*1024).build());
          byte[] buffer=new byte[8192];int count,total=0;
          while((count=source.read(buffer,0,buffer.length))!=-1) {for(int i=0;i<count;i++)assertEquals(71,buffer[i]);total+=count;}
          assertEquals("The immediate next song must be ready offline, not displaced by a farther song",512*1024,total);
        }finally{source.close();}
      } finally {
        f.inst.runOnMainSync(()->manager.updateQueueContext(false,false,false,true,false,false,Collections.emptyList(),-1,"off",false,false,false,false));
        f.js("p7Restore().then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext())");f.await("window.p7Restored===true");
      }
    }
  }
  @Test public void naturalHttpAudioEndAdvancesAndRecordsTheFirstPlayingEvent() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture();AudioServer server=new AudioServer(true)) {
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureStatus.showFullPlayer=false;window.transitionReady=true})");f.await("window.transitionReady===true&&!document.querySelector('.full-player')");
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      org.json.JSONArray events=new org.json.JSONArray();
      long[] ended={0},nextPlaying={0};
      androidx.media3.common.Player.Listener listener=new androidx.media3.common.Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
          long now=SystemClock.elapsedRealtime();
          events.put(new com.getcapacitor.JSObject().put("atMs",now).put("state",state));
          if(state==androidx.media3.common.Player.STATE_ENDED&&ended[0]==0)ended[0]=now;
        }
        @Override public void onIsPlayingChanged(boolean playing) {
          var state=manager.buildState();long now=SystemClock.elapsedRealtime();
          events.put(new com.getcapacitor.JSObject().put("atMs",now).put("playing",playing).put("songId",state.optLong("songId")));
          if(playing&&state.optLong("songId")==991551&&nextPlaying[0]==0)nextPlaying[0]=now;
        }
      };
      f.inst.runOnMainSync(()->{manager.buildState();manager.getSession().getPlayer().addListener(listener);});
      f.js("window.transitionData=fixturePinia._s.get('data');window.transitionSaved={list:transitionData.playList,original:transitionData.originalPlayList};window.transitionHistory=transitionData.setHistory;transitionData.setHistory=async()=>{};transitionData.playList=[0,1].map((i)=>({...p7Songs[0],id:991550+i,type:'streaming',path:undefined,name:'HTTP transition '+i,duration:2000,streamUrl:i===0?"+org.json.JSONObject.quote(server.url("current"))+":"+org.json.JSONObject.quote(server.url("next"))+"}));fixtureStatus.playIndex=0;fixtureStatus.repeatMode='off';fixtureStatus.personalFmMode=false;fixtureSetting.useNextPrefetch=true;window.__SPLAYER_PLAYER_CONTROLLER__.playSong({song:transitionData.playList[0],autoPlay:true}).then(()=>window.httpStarted=true)");
      try {
        f.await("window.httpStarted===true");
        long started=SystemClock.elapsedRealtime();
        while(SystemClock.elapsedRealtime()-started<12000) {
          boolean[] done={false};f.inst.runOnMainSync(()->done[0]=nextPlaying[0]>0&&ended[0]>0);
          if(done[0])break;SystemClock.sleep(40);
        }
        long[] result=new long[2];f.inst.runOnMainSync(()->{result[0]=ended[0];result[1]=nextPlaying[0];});
        java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        try(var out=new PrintWriter(new File(dir,"natural-http-transition.json"),"UTF-8")){out.print(new org.json.JSONObject().put("events",events).put("eventGapMs",result[1]-result[0]).put("measurement","Media3 ENDED to next isPlaying; not acoustic sample gap"));}
        assertTrue("Actual first audio must end naturally",result[0]>0);
        assertTrue("Actual second audio must start playing",result[1]>=result[0]);
        assertTrue("Prepared HTTP next track must avoid multi-second stalls: "+(result[1]-result[0]),result[1]-result[0]<1200);
      } finally {
        f.inst.runOnMainSync(()->{manager.getSession().getPlayer().removeListener(listener);manager.pause();});
        f.js("transitionData.playList=transitionSaved.list;transitionData.originalPlayList=transitionSaved.original;transitionData.setHistory=transitionHistory;p7Restore().then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext())");f.await("window.p7Restored===true");
      }
    }
  }

  @Test public void rapidSelectionsDoNotWaitForOrReturnToTheOldHttpAudio() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture();AudioServer server=new AudioServer(true);
        MainThreadTrace trace=new MainThreadTrace(f.activity,"rapid-main-thread")) {
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureStatus.showFullPlayer=false;window.rapidReady=true})");f.await("window.rapidReady===true&&!document.querySelector('.full-player')");
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      String urls=new org.json.JSONArray(Arrays.asList(server.url("slow"),server.url("middle"),server.url("final"))).toString();
      long[] nativeFinalPlayingAt={0},nativeFinalEndedAt={0};
      androidx.media3.common.Player.Listener nativeTiming=new androidx.media3.common.Player.Listener() {
        @Override public void onPlaybackStateChanged(int state) {
          if(state!=androidx.media3.common.Player.STATE_ENDED)return;
          var snapshot=manager.buildState();
          if(snapshot.optLong("songId")==991562&&server.url("final").equals(snapshot.optString("src")))
            nativeFinalEndedAt[0]=SystemClock.elapsedRealtime();
        }
        @Override public void onIsPlayingChanged(boolean playing) {
          var state=manager.buildState();
          if(playing&&state.optLong("songId")==991562&&server.url("final").equals(state.optString("src"))&&nativeFinalPlayingAt[0]==0)
            nativeFinalPlayingAt[0]=System.currentTimeMillis();
        }
      };
      // Cached final audio can become ready before metadata IPC; exercise that real timing too.
      DataSource warm=AudioCacheProvider.buildCachedDataSourceFactory(f.activity).createDataSource();
      try {
        warm.open(new DataSpec.Builder().setUri(Uri.parse(server.url("final"))).build());
        byte[] bytes=new byte[8192];while(warm.read(bytes,0,bytes.length)!=-1){}
      } finally {warm.close();}
      f.inst.runOnMainSync(()->manager.getSession().getPlayer().addListener(nativeTiming));
      try(var in=f.inst.getContext().getAssets().open("rapid-playback-diagnostic.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("window.rapidData=fixturePinia._s.get('data');window.rapidSaved={list:rapidData.playList,original:rapidData.originalPlayList};window.rapidHistory=rapidData.setHistory;rapidData.setHistory=async()=>{};rapidData.playList="+urls+".map((u,i)=>({...p7Songs[0],id:991560+i,type:'streaming',path:undefined,streamUrl:u,name:'Rapid HTTP '+i,duration:2000}));fixtureStatus.personalFmMode=false;fixtureStatus.repeatMode='off';fixtureStatus.playIndex=0;fixtureSetting.useNextPrefetch=false;window.rapidEvents=[];window.rapidIdentityErrors=[];window.rapidListener=Capacitor.addListener('AndroidNativePlayback','playbackStateChanged',e=>{const file=e.src?.startsWith('http://127.0.0.1:')?new URL(e.src).pathname.split('/').pop():'other';const expected={'slow.wav':991560,'middle.wav':991561,'final.wav':991562}[file];if(expected&&e.songId!==expected)rapidIdentityErrors.push({id:e.songId,source:file,playing:e.playing});if(e.playing)rapidEvents.push({id:e.songId,source:e.src?.startsWith('http://127.0.0.1:')?new URL(e.src).pathname.split('/').pop():'other',at:Date.now()})});window.__SPLAYER_PLAYER_CONTROLLER__.playSong({song:rapidData.playList[0],autoPlay:true})");
      try {
        long started=SystemClock.elapsedRealtime();
        while(server.slowRequests.get()==0&&SystemClock.elapsedRealtime()-started<5000)SystemClock.sleep(30);
        assertTrue("First track must actually be loading over HTTP",server.slowRequests.get()>0);
        f.js("window.rapidSelectedAt=Date.now();rapidTrace.push({mark:'selection',start:rapidSelectedAt-rapidTraceStart});fixtureStatus.playIndex=1;window.__SPLAYER_PLAYER_CONTROLLER__.playSong({song:rapidData.playList[1],autoPlay:true});rapidTrace.push({mark:'middle-returned',start:Date.now()-rapidTraceStart});fixtureStatus.playIndex=2;window.__SPLAYER_PLAYER_CONTROLLER__.playSong({song:rapidData.playList[2],autoPlay:true});rapidTrace.push({mark:'final-returned',start:Date.now()-rapidTraceStart})");
        f.await("rapidEvents.some(e=>e.id===991562)");
        java.io.File timingDir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(timingDir.exists()||timingDir.mkdirs());
        long[] nativeAt={0};f.inst.runOnMainSync(()->nativeAt[0]=nativeFinalPlayingAt[0]);
        long selectedAt=(long)Double.parseDouble(f.js("rapidSelectedAt"));
        String timing=f.js("JSON.stringify({bridge:rapidTrace,longTasks:rapidLongTasks,events:rapidEvents,finalDelayMs:rapidEvents.find(e=>e.id===991562).at-rapidSelectedAt,nativeFirstPlayingDelayMs:"+(nativeAt[0]-selectedAt)+"})");
        try(var out=new PrintWriter(new File(timingDir,"rapid-http-first-playing.json"),"UTF-8")){out.print(timing);}
        assertTrue("Native final playback must meet the same latency limit: "+timing,nativeAt[0]>=selectedAt&&nativeAt[0]-selectedAt<1500);
        assertEquals("Slow old request must not hold final selection for seconds: "+timing,"true",f.js("rapidEvents.find(e=>e.id===991562).at-rapidSelectedAt<1500"));
        long completionDeadline=SystemClock.elapsedRealtime()+5000;
        long[] completedAt={0};
        do {
          f.inst.runOnMainSync(()->completedAt[0]=nativeFinalEndedAt[0]);
          if(completedAt[0]>0)break;
          SystemClock.sleep(50);
        }while(SystemClock.elapsedRealtime()<completionDeadline);
        assertTrue("Final HTTP audio must actually reach its natural end",completedAt[0]>0);
        // Keep the original late-response observation after the actual terminal event.
        SystemClock.sleep(2000);
        java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        try(var out=new PrintWriter(new File(dir,"rapid-http-selections.json"),"UTF-8")){out.print(f.js("JSON.stringify({events:rapidEvents,finalDelayMs:rapidEvents.find(e=>e.id===991562).at-rapidSelectedAt})"));}
        assertEquals("Every native resource event must carry its matching identity: "+f.js("JSON.stringify(rapidIdentityErrors)"),"true",f.js("rapidIdentityErrors.length===0"));
        assertEquals("Old and intermediate audio must never play after final selection: "+f.js("JSON.stringify(rapidEvents)"),"true",f.js("rapidEvents.filter(e=>e.at>=rapidSelectedAt).every(e=>e.id===991562&&e.source==='final.wav')"));
        assertEquals("Repeat off must stop at the final song without leaving loading active","true",f.js("!fixtureStatus.playStatus&&!fixtureStatus.playLoading&&fixturePinia._s.get('music').playSong.id===991562"));
      } finally {
        f.js("window.rapidObserver?.disconnect();if(window.rapidNativePromise)window.Capacitor.nativePromise=rapidNativePromise;if(window.rapidStorageSetItem)Storage.prototype.setItem=rapidStorageSetItem");
        f.inst.runOnMainSync(()->{manager.getSession().getPlayer().removeListener(nativeTiming);manager.pause();});
        f.js("Promise.resolve(rapidListener).then(h=>h.remove());rapidData.playList=rapidSaved.list;rapidData.originalPlayList=rapidSaved.original;rapidData.setHistory=rapidHistory;p7Restore().then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.syncAndroidPlaybackContext())");f.await("window.p7Restored===true");
      }
    }
  }

}

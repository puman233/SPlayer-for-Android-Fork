package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Public built playback commands and real Media3 audio; dedicated synthetic package only. */
@RunWith(AndroidJUnit4.class)
public class PlaybackStabilityDeviceTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private Server server;
  private String savedSettings;
  private String savedPlaylist;
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] value = {null};
    String guarded = "(()=>{try{return eval(" + JSONObject.quote(expression) + ")}catch(e){return 'EVAL_ERROR: '+e.message}})()";
    inst.runOnMainSync(() -> web.evaluateJavascript(guarded, v -> { value[0] = v; done.countDown(); }));
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertFalse(value[0], value[0] != null && value[0].contains("EVAL_ERROR:")); return value[0];
  }
  private void await(String expression) throws Exception {
    for (int i = 0; i < 250; i++) { if ("true".equals(js(expression))) return; SystemClock.sleep(100); }
    fail("Timed out: " + expression + "; " + js("JSON.stringify({error:window.psError,source:window.__SPLAYER_AUDIO_MANAGER__?.src,requests:window.psRequests?.length})"));
  }
  private JSONObject nativeState() throws Exception {
    JSONObject[] state = {null};
    inst.runOnMainSync(() -> state[0] = PlaybackManager.getInstance(inst.getTargetContext()).buildState());
    return state[0];
  }
  @Before public void setup() throws Exception {
    assertTrue(java.util.Arrays.asList("top.imsyy.splayer.android.debug", "top.imsyy.splayer.android.debug.sec17").contains(inst.getTargetContext().getPackageName()));
    server = new Server();
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView(); await("!!window.$message && !!window.__SPLAYER_PLAYER_CONTROLLER__");
    js("window.psReady=false;window.psError=null;window.psDone=false;window.psRequests=[];window.psLyrics=[];window.psHold=false");
    String base = JSONObject.quote(server.url());
    js("(async()=>{try{const resource=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name));const exps=Object.values(await import(resource.name));const pinia=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia;window.psSetting=pinia._s.get('setting');window.psStatus=pinia._s.get('status');window.psMusic=pinia._s.get('music');window.psData=pinia._s.get('data');window.psSavedPlaylist={playList:psData.playList,originalPlayList:psData.originalPlayList,playIndex:psStatus.playIndex,personalFmMode:psStatus.personalFmMode};window.psSaved={androidAllowMixWithOthers:psSetting.androidAllowMixWithOthers,enableAutomix:psSetting.enableAutomix,enableQQMusicLyric:psSetting.enableQQMusicLyric,enableOnlineTTMLLyric:psSetting.enableOnlineTTMLLyric,cacheEnabled:psSetting.cacheEnabled,lyricPriority:psSetting.lyricPriority,useNextPrefetch:psSetting.useNextPrefetch};Object.assign(psSetting,{androidAllowMixWithOthers:true,enableAutomix:false,enableQQMusicLyric:false,enableOnlineTTMLLyric:false,cacheEnabled:false,lyricPriority:'official',useNextPrefetch:false});const get=exps.find(v=>typeof v==='function'&&String(v).includes('playbackEngine')&&String(v).includes('audioEngine')&&String(v).includes('new '));if(!get)throw Error('Public audio getter unavailable');window.__SPLAYER_AUDIO_MANAGER__.destroy();delete window.__SPLAYER_AUDIO_MANAGER__;window.psAudio=get();window.psPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;psPlayer.rebindAudioEvents();if(psAudio.engineType!=='android-native')throw Error('Native engine not selected');window.psBase="+base+";window.psOpen=XMLHttpRequest.prototype.open;window.psSend=XMLHttpRequest.prototype.send;const requests=new WeakMap();XMLHttpRequest.prototype.open=function(m,u,...args){requests.set(this,String(u));return psOpen.call(this,m,u,...args)};XMLHttpRequest.prototype.send=function(body){const url=requests.get(this)||'';if(!/\\/(song\\/url|lyric)/.test(url))return psSend.call(this,body);const params=new URL(url,location.href).searchParams;const id=Number(params.get('id')||params.get('ids')?.replace(/[\\[\\]]/g,'')||99007001);const xhr=this;const request={url,id,sent:false,complete:()=>{if(request.sent)return;request.sent=true;const data=url.includes('/lyric')?{code:200,lrc:{lyric:'[00:01.000]fixture song '+id},tlyric:{lyric:'[00:01.000]'+(window.psTranslation||'translation one')},romalrc:{lyric:'[00:01.000]'+(window.psRoman||'roman')},yrc:{lyric:'[1000,1000](1000,'+(window.psWordTiming||500)+',0)fixture'}}:{code:200,data:[{id,url:psBase+'audio'+id+'.wav',type:'wav',level:'standard',time:40000,fee:0,flag:0,size:640044}]};const text=JSON.stringify(data);for(const[k,v]of Object.entries({status:200,statusText:'OK',readyState:4,responseText:text,response:text,responseURL:url}))Object.defineProperty(xhr,k,{configurable:true,value:v});xhr.getAllResponseHeaders=()=> 'content-type: application/json';xhr.dispatchEvent(new Event('readystatechange'));xhr.dispatchEvent(new Event('load'));xhr.dispatchEvent(new Event('loadend'))}};psRequests.push(request);if(!window.psHold||url.includes('/lyric'))setTimeout(request.complete,0)};window.psSong=id=>({id,name:'Synthetic song '+id,type:'song',duration:40000,artists:[{id:99007,name:'Synthetic artist'}],album:{id:99008,name:'Synthetic album'}});window.psReady=true}catch(e){window.psError=e.message}})()");
    await("psReady||!!psError"); assertEquals("null", js("psError||null"));
    savedSettings = (String) new JSONTokener(js("JSON.stringify(psSaved)")).nextValue();
    savedPlaylist = (String) new JSONTokener(js("JSON.stringify(psSavedPlaylist)")).nextValue();
    System.out.println("PLAYBACK_STABILITY viewport=" + js("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"));
  }
  private void play(int id, boolean autoPlay) throws Exception {
    js("window.psDone=false;(async()=>{await psData.setPlayList([psSong("+id+")]);await psData.setOriginalPlayList([psSong("+id+")]);psStatus.playIndex=0;psStatus.personalFmMode=false;await psPlayer.playSong({song:psSong("+id+"),autoPlay:"+autoPlay+"})})().then(()=>psDone=true,e=>{psError=e.message;psDone=true})");
    await("psDone"); assertEquals("null", js("psError||null"));
  }
  private void awaitPlaying() throws Exception {
    for (int i=0;i<200;i++) { if (nativeState().optBoolean("playing") && nativeState().optLong("positionMs")>200) return; SystemClock.sleep(100); }
    fail("Actual native playback did not advance: " + nativeState());
  }
  @After public void cleanup() throws Exception {
    if (web!=null) js("(()=>{XMLHttpRequest.prototype.open=window.psOpen||XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.send=window.psSend||XMLHttpRequest.prototype.send;window.__SPLAYER_AUDIO_MANAGER__?.stop();const setting=document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.get('setting');if(setting&&"+(savedSettings!=null)+")Object.assign(setting,"+(savedSettings==null?"{}":savedSettings)+")})()");
    if(web!=null&&savedPlaylist!=null){
      js("window.psCleaned=false;(async()=>{const pinia=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia;const data=pinia._s.get('data'),status=pinia._s.get('status');const saved="+savedPlaylist+";await data.setPlayList(saved.playList);await data.setOriginalPlayList(saved.originalPlayList);status.playIndex=saved.playIndex;status.personalFmMode=saved.personalFmMode;window.psCleaned=true})()");await("psCleaned");
    }
    if (activity!=null) inst.runOnMainSync(activity::finish);
    if (server!=null) server.close();
  }
  @Test public void lateQualityResponseCannotReplaceActuallyPlayingNextSong() throws Exception {
    play(99007001,true); awaitPlaying();
    js("window.psHold=true;window.psOldDone=false;psPlayer.switchQuality(1000).then(()=>psOldDone=true)");
    await("psRequests.some(r=>r.url.includes('/song/url')&&!r.sent)");
    js("window.psHold=false"); play(99007002,true); awaitPlaying();
    js("psRequests.filter(r=>!r.sent).forEach(r=>r.complete())"); await("psOldDone"); SystemClock.sleep(300);
    assertTrue(nativeState().getString("src").contains("audio99007002.wav"));
    assertTrue(nativeState().getBoolean("playing")); assertEquals("99007002", js("psMusic.playSong.id"));
    assertEquals("false", js("psStatus.playLoading"));
  }
  @Test public void pausedSwitchRetainsActualNativePauseAndSeek() throws Exception {
    play(99007001,true); awaitPlaying(); js("psPlayer.pause();psPlayer.setSeek(5000)");
    await("psAudio.paused"); js("window.psDone=false;psPlayer.switchQuality(5000).then(()=>psDone=true)"); await("psDone");
    SystemClock.sleep(600); JSONObject nativeResult=nativeState();
    assertFalse(nativeResult.getBoolean("playing")); assertTrue(nativeResult.getBoolean("paused"));
    assertTrue(nativeResult.optLong("positionMs")>=4500); assertEquals("false", js("psStatus.playStatus"));
  }
  @Test public void lifecycleRebindingKeepsNativeClockAndLyricsAfterRotation() throws Exception {
    play(99007001,true); awaitPlaying(); long before=nativeState().getLong("positionMs");
    js("psAudio.destroy();psAudio.init();psAudio.init();document.dispatchEvent(new Event('visibilitychange'))");
    SystemClock.sleep(700); assertTrue(nativeState().getLong("positionMs")>before);
    await("!psAudio.paused"); assertTrue("true".equals(js("psAudio.currentTime>0")));
    int original=activity.getRequestedOrientation();
    inst.runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    await("innerWidth>innerHeight");
    System.out.println("PLAYBACK_STABILITY landscape="+js("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"));
    inst.runOnMainSync(()->activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)); await("innerHeight>innerWidth");
    inst.runOnMainSync(()->activity.setRequestedOrientation(original));
    assertTrue(nativeState().getBoolean("playing"));
    assertNotEquals("null",js("psMusic.songLyric.lrcData[0]?.words[0]?.word||null"));
  }
  @Test public void translationOnlyUpdateReachesTheBuiltLyricStore() throws Exception {
    play(99007001,false); await("psMusic.songLyric.lrcData.length>0");
    js("window.psTranslation='translation two';psPlayer.setupSongUI(psSong(99007001),0)");
    await("psMusic.songLyric.lrcData[0]?.translatedLyric==='translation two'");
    assertEquals("\"translation two\"", js("psMusic.songLyric.lrcData[0].translatedLyric"));
    assertEquals("false", js("psStatus.lyricLoading"));
    js("window.psRoman='roman two';psPlayer.setupSongUI(psSong(99007001),0)");
    await("psMusic.songLyric.lrcData[0]?.romanLyric==='roman two'");
    js("window.psWordTiming=700;psPlayer.setupSongUI(psSong(99007001),0)");
    await("psMusic.songLyric.yrcData[0]?.words[0]?.endTime===1700");
    assertEquals("false",js("psStatus.lyricLoading"));
    psSeekAndResume();
  }
  private void psSeekAndResume() throws Exception {
    js("psPlayer.setSeek(2500);window.psDone=false;psPlayer.play().then(()=>psDone=true)");await("psDone");awaitPlaying();
    assertEquals("true",js("psMusic.songLyric.lrcData[0]?.romanLyric==='roman two'"));
  }
  @Test public void rapidNextPreviousAndSourceSwitchKeepTheFinalNativeTrack() throws Exception {
    play(99007001,true);awaitPlaying();
    js("window.psDone=false;(async()=>{const list=[psSong(99007001),psSong(99007002),psSong(99007003)];await psData.setPlayList(list);await psData.setOriginalPlayList(list);psStatus.playIndex=0;await Promise.all([psPlayer.nextOrPrev('next'),psPlayer.nextOrPrev('next'),psPlayer.nextOrPrev('prev')]);psDone=true})().catch(e=>{psError=e.message;psDone=true})");
    await("psDone");assertEquals("null",js("psError||null"));awaitPlaying();
    js("window.psDone=false;psPlayer.switchAudioSource('auto').then(()=>psDone=true)");await("psDone");awaitPlaying();
    assertEquals("99007002",js("psMusic.playSong.id")); assertTrue(nativeState().getString("src").contains("audio99007002.wav"));
    assertEquals("false",js("psStatus.playLoading"));
  }
  @Test public void backgroundAndActivityRecreationRetainRealNativePlayback() throws Exception {
    play(99007001,true); awaitPlaying(); long before=nativeState().getLong("positionMs");
    inst.runOnMainSync(()->activity.moveTaskToBack(true)); SystemClock.sleep(800);
    assertTrue(nativeState().getBoolean("playing")); assertTrue(nativeState().getLong("positionMs")>before);
    inst.runOnMainSync(()->activity.startActivity(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
    await("document.visibilityState==='visible'");
    MainActivity previous=activity; inst.runOnMainSync(previous::recreate);
    for(int i=0;i<100;i++){
      MainActivity[] resumed={null};
      inst.runOnMainSync(()->{for(Activity candidate:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(candidate instanceof MainActivity&&candidate!=previous)resumed[0]=(MainActivity)candidate;});
      if(resumed[0]!=null){activity=resumed[0];break;}SystemClock.sleep(100);
    }
    assertNotSame("A new Activity must actually be created",previous,activity);
    web=activity.getBridge().getWebView(); await("!!window.$message&&!!window.__SPLAYER_PLAYER_CONTROLLER__");
    js("(async()=>{const resource=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name));const exps=Object.values(await import(resource.name));const get=exps.find(v=>typeof v==='function'&&String(v).includes('playbackEngine')&&String(v).includes('audioEngine')&&String(v).includes('new '));window.psAudio=get();psAudio.init()})()");
    await("!!window.psAudio&&!psAudio.paused&&psAudio.currentTime>0");
    await("window.__SPLAYER_PLAYER_CONTROLLER__.currentAudioSource?.url?.includes('audio99007001.wav')&&document.querySelector('#app').__vue_app__.config.globalProperties.$pinia._s.get('status').playLoading===false");
    assertTrue(nativeState().getString("src").contains("audio99007001.wav")); assertTrue(nativeState().getBoolean("playing"));
    System.out.println("PLAYBACK_STABILITY recreated="+nativeState());
  }
  static final class Server implements AutoCloseable {
    private final ServerSocket socket = new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final byte[] audio;
    private volatile boolean closed;
    Server() throws IOException {
      int size=8000*40*2; ByteBuffer wav=ByteBuffer.allocate(size+44).order(ByteOrder.LITTLE_ENDIAN);
      wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(size+36).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size);
      for(int i=0;i<size/2;i++)wav.putShort((short)(700*Math.sin(2*Math.PI*220*i/8000)));audio=wav.array();
      workers.execute(()->{while(!closed){try{Socket connection=socket.accept();workers.execute(()->serve(connection));}catch(IOException e){if(!closed)throw new RuntimeException(e);}}});
    }
    // URI schemes are case-insensitive. Keep the fixture's cleartext loopback
    // transport instead of the online provider's lowercase http-to-https rewrite.
    String url(){return "HTTP://127.0.0.1:"+socket.getLocalPort()+"/";}
    private void serve(Socket connection){try(Socket peer=connection){BufferedReader reader=new BufferedReader(new InputStreamReader(peer.getInputStream(),StandardCharsets.US_ASCII));reader.readLine();String header;while((header=reader.readLine())!=null&&!header.isEmpty()){}OutputStream out=peer.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: "+audio.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(audio);out.flush();}catch(IOException ignored){/* Stopping a track may disconnect the synthetic response. */}}
    @Override public void close() throws IOException {closed=true;socket.close();workers.shutdownNow();}
  }
}

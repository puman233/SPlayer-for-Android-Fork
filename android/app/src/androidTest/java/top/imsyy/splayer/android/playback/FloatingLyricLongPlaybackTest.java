package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 真实 Native queue → JS trackChanged → LyricManager → 悬浮窗，使用真实同目录 LRC 文件。 */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricLongPlaybackTest {
  private Object field(Object target,String name) throws Exception {
    Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
  }
  private enum BackgroundAdvance { NEXT, PREPARED_NEXT, SEEK_END, NATURAL_END, FAST_NATURAL_END }
  // Independent upstream probe after observation; never persist media URLs or credentials.
  private JSONObject probeObservedAudio(DeviceWebFixture f,long songId) throws Exception {
    JSONObject result=new JSONObject().put("songId",songId);
    Object source=new JSONTokener(f.js("onlineObservedSources["+songId+"]||null")).nextValue();
    if(!(source instanceof String))return result.put("observed",false);
    var uri=android.net.Uri.parse((String)source);
    result.put("observed",true).put("scheme",uri.getScheme());
    result.put("loopbackHost","127.0.0.1".equals(uri.getHost())||"localhost".equals(uri.getHost())||"::1".equals(uri.getHost()));
    result.put("knownEmptyAudioPath",uri.getPath()!=null&&uri.getPath().matches("(?i).*/(?:404|error|empty|blank|silence)\\.mp3"));
    var cache=top.imsyy.splayer.android.cache.AudioCacheProvider.getOrCreate(f.activity);
    String key=top.imsyy.splayer.android.cache.AudioCacheProvider.resolveCacheKey(uri);
    result.put("cachedBytes",cache.getCachedBytes(key,0,Long.MAX_VALUE));
    result.put("cachedLength",androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(key)));
    JSONArray spans=new JSONArray();
    for(var span:cache.getCachedSpans(key)) {
      JSONObject info=new JSONObject().put("position",span.position).put("length",span.length);
      if(span.position==0&&span.file!=null) {
        try(var input=new java.io.FileInputStream(span.file)) {
          byte[] header=input.readNBytes(16);StringBuilder hex=new StringBuilder();for(byte value:header)hex.append(String.format("%02x",value&255));info.put("headerHex",hex.toString());
        }
        var retriever=new android.media.MediaMetadataRetriever();
        try {retriever.setDataSource(span.file.getAbsolutePath());info.put("fileDurationMs",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));}
        catch(Exception error){info.put("metadataFailure",error.getClass().getSimpleName());}
        finally {retriever.release();}
      }
      spans.put(info);
    }
    result.put("spans",spans);
    if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))return result;
    File sample=File.createTempFile("upstream-audio-probe-",".bin",f.activity.getCacheDir());
    java.net.HttpURLConnection connection=null;
    try {
      connection=(java.net.HttpURLConnection)new java.net.URL((String)source).openConnection();
      connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
      connection.setRequestProperty("Range","bytes=0-2097151");
      result.put("httpStatus",connection.getResponseCode()).put("contentLength",connection.getContentLengthLong()).put("contentType",connection.getContentType());
      result.put("contentRange",connection.getHeaderField("Content-Range")).put("acceptRanges",connection.getHeaderField("Accept-Ranges"));
      result.put("redirectedToKnownEmptyAudio",connection.getURL().getPath().matches("(?i).*/(?:404|error|empty|blank|silence)\\.mp3"));
      int total=0;long deadline=SystemClock.uptimeMillis()+25000;
      try(var input=connection.getInputStream();var output=new FileOutputStream(sample)) {
        byte[] buffer=new byte[8192];int count;
        while(total<2097152&&SystemClock.uptimeMillis()<deadline&&(count=input.read(buffer,0,Math.min(buffer.length,2097152-total)))!=-1){output.write(buffer,0,count);total+=count;}
      }
      result.put("sampleBytes",total);
      byte[] sampleData=java.nio.file.Files.readAllBytes(sample.toPath());
      StringBuilder digest=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(sampleData))digest.append(String.format("%02x",value&255));
      result.put("sampleSha256",digest.toString());
      var retriever=new android.media.MediaMetadataRetriever();
      try {retriever.setDataSource(sample.getAbsolutePath());result.put("directDurationMs",retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));}
      finally {retriever.release();}
    } catch(Exception error) {result.put("probeFailure",error.getClass().getSimpleName());}
    finally {if(connection!=null)connection.disconnect();sample.delete();}
    return result;
  }
  private JSONObject nativeSnapshot(PlaybackManager manager) throws Exception {
    var metadata=(PlaybackManager.TrackMetadata)field(manager,"currentMetadata");
    var player=(androidx.media3.exoplayer.ExoPlayer)field(manager,"player");
    var queue=(PlaybackQueue)field(manager,"playbackQueue");
    var current=queue.current();
    var overlay=(FloatingLyricService)field(manager,"floatingLyricService");
    android.view.View overlayView=overlay==null?null:(android.view.View)field(overlay,"view");
    ArrayList<String> lines=new ArrayList<>();
    if(overlay!=null)for(int i=0;i<Math.min(3,overlay.lrcLines.size());i++) {
      StringBuilder text=new StringBuilder();for(var word:overlay.lrcLines.get(i).words)text.append(word.text);
      lines.add(text.toString());
    }
    return new JSONObject().put("songId",metadata==null?0:metadata.songId)
        .put("remoteMode",field(manager,"remoteMode"))
        .put("queueSongId",current==null?0:current.songId).put("queueIndex",current==null?-1:current.playListIndex)
        .put("positionMs",player==null?-1:player.getCurrentPosition()).put("durationMs",player==null?-1:player.getDuration())
        .put("playing",player!=null&&player.isPlaying()).put("rate",player==null?0:player.getPlaybackParameters().speed)
        .put("overlayShown",overlayView!=null&&overlayView.isShown()&&overlayView.getDisplay()!=null&&overlayView.getDisplay().isValid())
        .put("overlayDisplayId",overlayView==null||overlayView.getDisplay()==null?-1:overlayView.getDisplay().getDisplayId())
        .put("lyrics",String.join("|",lines));
  }
  @Test public void backgroundRealOnlineNextUpdatesLyricsWithoutOpeningApp() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.NEXT);
  }
  @Test public void backgroundPreparedOnlineNextUpdatesLyricsWithoutOpeningApp() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.PREPARED_NEXT);
  }
  @Test public void backgroundOnlineTrackEndUpdatesNextLyricsWithoutOpeningApp() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.SEEK_END);
  }
  @Test public void fiveMinutesBackgroundRealOnlineSongEndsAndLoadsNextLyrics() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.NATURAL_END);
  }
  @Test public void acceleratedBackgroundRealOnlineNaturalEndLoadsNextLyrics() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.FAST_NATURAL_END);
  }
  @Test public void acceleratedBackgroundAlternateOnlineNaturalEndLoadsNextLyrics() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.FAST_NATURAL_END,28941714L);
  }
  @Test public void fiveMinutesBackgroundAlternateOnlineSongEndsAndLoadsNextLyrics() throws Exception {
    assertBackgroundOnlineNext(BackgroundAdvance.NATURAL_END,28941714L);
  }
  private void assertBackgroundOnlineNext(BackgroundAdvance mode) throws Exception {
    assertBackgroundOnlineNext(mode,186016L);
  }
  private void assertBackgroundOnlineNext(BackgroundAdvance mode,long targetSongId) throws Exception {
    boolean prepareAudio=mode==BackgroundAdvance.PREPARED_NEXT;
    boolean automatic=mode==BackgroundAdvance.SEEK_END;
    boolean naturalEnd=mode==BackgroundAdvance.NATURAL_END||mode==BackgroundAdvance.FAST_NATURAL_END;
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      for(String asset:new String[]{"r23-player-fixture.js","online-lyric-transition.js"})
        try(var stream=f.inst.getContext().getAssets().open(asset)){f.js(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      Float[] savedRate={null};
      try {
        f.js("window.onlineTargetSongId="+targetSongId+";prepareOnlineTransition().catch(e=>window.onlineError={phase:onlinePhase,type:e.name})");
        for(int i=0;i<600&&!"true".equals(f.js("window.onlinePrepared===true||!!window.onlineError"));i++)SystemClock.sleep(100);
        assertEquals("真实在线首曲准备成功: "+f.js("window.onlineError"),"true",f.js("window.onlinePrepared===true"));
        f.await("!fixtureStatus.lyricLoading&&fixtureStatus.playStatus&&p7Stores.music.songLyric.lrcData.length>0");
        // 独立的公开歌词响应仅作预期值，不写入播放器/歌词缓存。
        f.js("fetch('http://127.0.0.1:1145/api/netease/lyric/new?id='+onlineSongs[1].id+'&noCookie=true').then(r=>r.json()).then(r=>window.backgroundExpected=r.lrc?.lyric||'').catch(e=>window.backgroundExpectedError=e.name)");
        f.await("!!window.backgroundExpected||!!window.backgroundExpectedError");
        String raw=(String)new JSONTokener(f.js("backgroundExpected")).nextValue();
        assertTrue("目标在线歌曲必须有歌词",raw.length()>20);
        long target=Long.parseLong(f.js("onlineSongs[1].id"));
        if(prepareAudio) {
        // 对照组通过公开补窗准备音频；常规组不增加任何补窗操作。
        f.js("window.backgroundQueueReady=false;window.__SPLAYER_PLAYER_CONTROLLER__.refreshAndroidQueueWindow().then(()=>window.backgroundQueueReady=true).catch(e=>window.backgroundQueueError=e.name)");
        for(int i=0;i<600&&!"true".equals(f.js("window.backgroundQueueReady||!!window.backgroundQueueError"));i++)SystemClock.sleep(100);
        assertEquals("补窗必须完成","true",f.js("window.backgroundQueueReady===true"));
        boolean[] ready={false};
        f.inst.runOnMainSync(()->{try {PlaybackQueue queue=(PlaybackQueue)field(manager,"playbackQueue");ready[0]=queue.findUrlBySongId(target)!=null;}catch(Exception e){throw new AssertionError(e);}});
        assertTrue("后台歌词测试前原生队列必须已有目标音频地址",ready[0]);
        }
        if(mode==BackgroundAdvance.FAST_NATURAL_END)f.inst.runOnMainSync(()->{
          try {savedRate[0]=((androidx.media3.exoplayer.ExoPlayer)field(manager,"player")).getPlaybackParameters().speed;manager.setRate(3f);}catch(Exception e){throw new AssertionError(e);}
        });
        f.inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
        JSONArray timeline=new JSONArray();
        for(int i=0;i<(mode==BackgroundAdvance.FAST_NATURAL_END?22:naturalEnd?60:automatic?14:4);i++) {
          SystemClock.sleep(5000);JSONObject[] sample={null};
          f.inst.runOnMainSync(()->{try {sample[0]=nativeSnapshot(manager);}catch(Exception e){throw new AssertionError(e);}});
          timeline.put(sample[0].put("elapsedMs",(i+1)*5000));
          assertTrue("后台歌词窗口必须保持实际显示且显示器有效: "+sample[0],sample[0].getBoolean("overlayShown"));
        }
        f.inst.runOnMainSync(()->{
          if(naturalEnd)return; // 五分钟组完全由真实音频自然结束推进，不seek、不发下一首。
          if(automatic) {
            try {
              androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)field(manager,"player");
              assertTrue("真实音频时长必须可用",player.getDuration()>5000);
              manager.seek(player.getDuration()-1500);
            }catch(Exception e){throw new AssertionError(e);}
          } else manager.handleNotificationAction(PlaybackConstants.ACTION_NEXT);
        });
        JSONArray samples=new JSONArray();boolean matched=false;
        // 此循环绝不调用 WebView，避免打开页面掩盖后台同步错误。
        for(int attempt=0;attempt<60;attempt++) {
          SystemClock.sleep(1000);
          JSONObject sample=new JSONObject();StringBuilder text=new StringBuilder();long[] id={0};
          f.inst.runOnMainSync(()->{
            try {
              PlaybackManager.TrackMetadata metadata=(PlaybackManager.TrackMetadata)field(manager,"currentMetadata");
              id[0]=metadata==null?0:metadata.songId;
              FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
              if(overlay!=null)for(int i=0;i<Math.min(3,overlay.lrcLines.size());i++) {
                if(i>0)text.append('|');for(var word:overlay.lrcLines.get(i).words)text.append(word.text);
              }
              androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)field(manager,"player");
              sample.put("songId",id[0]).put("positionMs",player==null?-1:player.getCurrentPosition()).put("playing",player!=null&&player.isPlaying()).put("lyrics",text.toString());
            }catch(Exception e){throw new AssertionError(e);}
          });
          samples.put(sample.put("elapsedMs",(attempt+1)*1000));
          String[] lines=text.toString().split("\\|");
          matched=id[0]==target&&lines.length>=2;
          for(String line:lines)matched&=!line.trim().isEmpty()&&raw.contains(line.trim());
          if(matched&&sample.optBoolean("playing")&&sample.optLong("positionMs")>1000)break;
          matched=false;
        }
        File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        String evidence=mode==BackgroundAdvance.FAST_NATURAL_END?"online-background-fast-natural.json":naturalEnd?"online-background-five-minutes.json":automatic?"online-background-ended-transition.json":prepareAudio?"online-background-prepared-transition.json":"online-background-lyric-transition.json";
        if(targetSongId!=186016L)evidence=evidence.replace(".json","-"+targetSongId+".json");
        android.graphics.Bitmap screen=f.inst.getUiAutomation().takeScreenshot();
        if(screen!=null)try(var out=new FileOutputStream(new File(dir,evidence.replace(".json","-screen.png")))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}
        try(var out=new java.io.PrintWriter(new File(dir,evidence),"UTF-8")){out.print(samples);}
        try(var out=new java.io.PrintWriter(new File(dir,evidence.replace(".json","-diagnostic.json")),"UTF-8")) {
          out.print(new JSONObject().put("timeline",timeline).put("upstreamAudio",probeObservedAudio(f,target)).put("uiAfterObservation",new JSONTokener((String)new JSONTokener(f.js("JSON.stringify({songId:p7Stores.music.playSong.id,list:fixturePinia._s.get('data').playList.map(s=>s.id),index:fixtureStatus.playIndex,repeat:fixtureStatus.repeatMode,mix:fixtureSetting.androidAllowMixWithOthers,automix:fixtureSetting.enableAutomix,abLoop:fixtureStatus.abLoop,trace:onlineTrace,actions:onlineActions,nativeEvents:onlineNativeEvents})")).nextValue()).nextValue()));
        }
        assertTrue("后台原生下一首必须播放目标曲并显示该曲歌词，无需返回App。末态="+samples.opt(samples.length()-1),matched);
      } finally {
        if(savedRate[0]!=null)f.inst.runOnMainSync(()->manager.setRate(savedRate[0]));
        f.activity.startActivity(new android.content.Intent(f.activity,top.imsyy.splayer.android.MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        f.js("restoreOnlineTransition().catch(e=>{window.onlineRestoreError=e.name;window.onlineRestored=true})");
        for(int i=0;i<600&&!"true".equals(f.js("window.onlineRestored===true"));i++)SystemClock.sleep(100);
        assertEquals("恢复原队列和歌曲","true",f.js("window.onlineRestored===true&&!window.onlineRestoreError"));
      }
    }
  }
  @Test public void realOnlineNextLoadsCurrentTrackIntoFloatingLyrics() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      for(String asset:new String[]{"r23-player-fixture.js","online-lyric-transition.js"})
        try(var stream=f.inst.getContext().getAssets().open(asset)){f.js(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
      try {
        f.js("prepareOnlineTransition().catch(e=>window.onlineError={phase:window.onlinePhase,type:e.name,message:String(e.message).replace(/https?:\\S+/g,'<url>').slice(0,160)})");
        for(int i=0;i<600&&!"true".equals(f.js("window.onlinePrepared===true||!!window.onlineError"));i++)SystemClock.sleep(100);
        assertEquals("真实在线歌曲必须解析并开始准备，错误类型="+f.js("window.onlineError"),"true",f.js("window.onlinePrepared===true"));
        PlaybackManager manager=PlaybackManager.getInstance(f.activity);
        JSONArray samples=new JSONArray();
        for(int track=0;track<2;track++) {
          if(track==1)f.js("window.onlineNextStarted=Date.now();window.__SPLAYER_PLAYER_CONTROLLER__.nextOrPrev('next')");
          final int index=track;
          try {f.await("p7Stores.music.playSong.id===onlineSongs["+track+"].id&&!fixtureStatus.lyricLoading&&p7Stores.music.songLyric.lrcData.length>0&&fixtureStatus.playStatus");}
          catch(AssertionError error){throw new AssertionError("真实切歌状态="+f.js("JSON.stringify({expected:onlineSongs["+track+"].id,current:p7Stores.music.playSong.id,index:fixtureStatus.playIndex,playing:fixtureStatus.playStatus,playLoading:fixtureStatus.playLoading,lyricLoading:fixtureStatus.lyricLoading,lrc:p7Stores.music.songLyric.lrcData.length,yrc:p7Stores.music.songLyric.yrcData.length,source:fixtureStatus.audioSource,trace:onlineTrace})"),error);}
          String expected=(String)new JSONTokener(f.js("p7Stores.music.songLyric.lrcData.slice(0,3).map(l=>l.words.map(w=>w.word).join('').trim()).join('|')")).nextValue();
          String[] rendered={""};long[] nativeId={0};
          for(int attempt=0;attempt<60;attempt++) {
            f.inst.runOnMainSync(()->{
              try {
                FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
                PlaybackManager.TrackMetadata metadata=(PlaybackManager.TrackMetadata)field(manager,"currentMetadata");
                nativeId[0]=metadata==null?0:metadata.songId;
                ArrayList<String> lines=new ArrayList<>();
                if(overlay!=null)for(int line=0;line<Math.min(3,overlay.lrcLines.size());line++) {
                  StringBuilder words=new StringBuilder();for(var word:overlay.lrcLines.get(line).words)words.append(word.text);
                  lines.add(words.toString().trim());
                }
                rendered[0]=String.join("|",lines);
              }catch(Exception error){throw new AssertionError(error);}
            });
            if(expected.equals(rendered[0]))break;
            SystemClock.sleep(250);
          }
          long expectedId=Long.parseLong(f.js("onlineSongs["+index+"].id"));
          assertEquals("原生播放必须切到目标真实歌曲",expectedId,nativeId[0]);
          assertEquals("桌面歌词必须加载当前真实歌曲而非上一首/空内容",expected,rendered[0]);
          samples.put(new JSONObject().put("songId",nativeId[0]).put("nativeMatchesCurrentLyrics",true).put("elapsedMs",Long.parseLong(f.js("Date.now()-"+(track==0?"onlineFirstStarted":"onlineNextStarted")))));
        }
        File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        try(var out=new java.io.PrintWriter(new File(dir,"online-lyric-transition.json"),"UTF-8")){out.print(new JSONObject().put("samples",samples).put("upstreamAudio",probeObservedAudio(f,Long.parseLong(f.js("onlineSongs[1].id")))).put("trace",new JSONTokener((String)new JSONTokener(f.js("JSON.stringify(onlineTrace)")).nextValue()).nextValue()).put("actions",new JSONTokener((String)new JSONTokener(f.js("JSON.stringify(onlineActions)")).nextValue()).nextValue()).put("nativeEvents",new JSONTokener((String)new JSONTokener(f.js("JSON.stringify(onlineNativeEvents)")).nextValue()).nextValue()).put("apiTimings",new JSONTokener((String)new JSONTokener(f.js("JSON.stringify(performance.getEntriesByType('resource').filter(e=>e.name.startsWith('http://127.0.0.1:1145/api/netease/')).map(e=>({path:new URL(e.name).pathname,start:e.startTime,duration:e.duration})))")).nextValue()).nextValue()));}
      } finally {
        f.js("restoreOnlineTransition().catch(e=>{window.onlineRestoreError=e.name;window.onlineRestored=true})");
        for(int i=0;i<600&&!"true".equals(f.js("window.onlineRestored===true"));i++)SystemClock.sleep(100);
        assertEquals("真实播放回归必须完成原队列/歌曲恢复", "true",f.js("window.onlineRestored===true&&!window.onlineRestoreError"));
      }
    }
  }
  @Test public void fiveMinutesBackgroundAdvancesThreeTracksAndTheirLyrics() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      ArrayList<File> files=new ArrayList<>();
      PlaybackManager manager=PlaybackManager.getInstance(f.activity);
      JSONArray songs=new JSONArray();
      for(int i=0;i<3;i++) {
        File audio=new File(f.activity.getCacheDir(),"long-background-"+i+".wav");
        files.add(audio);
        int bytes=100*16000*2;
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(bytes+36)
            .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1)
            .putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16)
            .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
        try(FileOutputStream out=new FileOutputStream(audio)) { out.write(header.array());out.write(new byte[bytes]); }
        File sidecar=new File(f.activity.getCacheDir(),"long-background-"+i+".lrc");
        files.add(sidecar);
        try(java.io.PrintWriter lyric=new java.io.PrintWriter(sidecar,"UTF-8")) {
          for(int line=0;line<100;line++) lyric.printf("[%02d:%02d.00]曲%d 行%d%n",line/60,line%60,i,line);
        }
        songs.put(new JSONObject().put("id",991000+i).put("type","song").put("path",android.net.Uri.fromFile(audio).toString())
            .put("name","后台测试曲 "+i).put("duration",100000).put("cover","/images/album.jpg")
            .put("artists",new JSONArray().put(new JSONObject().put("id",1).put("name","测试歌手")))
            .put("album",new JSONObject().put("id",1).put("name","后台测试专辑")));
      }
      f.js("window.longData=fixturePinia._s.get('data');window.longMusic=fixturePinia._s.get('music');window.longController=window.__SPLAYER_PLAYER_CONTROLLER__;window.longSaved={list:longData.playList,song:longMusic.playSong,index:fixtureStatus.playIndex,repeat:fixtureStatus.repeatMode,prefetch:fixtureSetting.useNextPrefetch,overlay:fixtureStatus.showDesktopLyric};window.longHistory=longData.setHistory;longData.setHistory=async()=>{};fixtureSetting.useNextPrefetch=false;fixtureStatus.repeatMode='off';fixtureStatus.playIndex=0;longData.playList="+songs+";longController.setDesktopLyricShow(true).then(async()=>{longController.setupSongUI(longData.playList[0],0);await longController.loadAndPlay(longData.playList[0].path,true,0);await longController.afterPlaySetup(longData.playList[0]);window.longReady=true}).catch(e=>window.longError=String(e));");
      try {
        // 只记录歌词提交；后台循环中仍不执行 JS，也不改变提交内容或调用顺序。
        f.js("window.longLyricTrace=[];window.longSetLyric=longMusic.setSongLyric;longMusic.setSongLyric=function(data,...rest){longLyricTrace.push({at:Date.now(),id:this.playSong?.id,lrc:data.lrcData?.length,yrc:data.yrcData?.length,stack:new Error().stack});return longSetLyric.call(this,data,...rest)}");
        f.await("window.longReady===true");
        f.await("longMusic.songLyric.lrcData.length>=90");
        f.await("fixtureStatus.playStatus===true&&fixtureStatus.currentTime>0");
        f.inst.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
        File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");
        assertTrue(dir.exists()||dir.mkdirs());
        long previousPosition=-1,previousSong=-1;
        boolean second=false,third=false;
        // 完整保留后台五分钟，绝不执行 JS 求值或打开页面来刷新歌词。
        try(java.io.PrintWriter log=new java.io.PrintWriter(new File(dir,"long-background.csv"),"UTF-8")) {
          log.println("elapsedMs,songId,positionMs,lyric,nativePositionMs,nativePlaying,playWhenReady,playbackState,suppressionReason");
          long started=SystemClock.uptimeMillis();
          while(SystemClock.uptimeMillis()-started<300000) {
            SystemClock.sleep(5000);
            long[] position={0},song={0}; String[] lyric={""},nativeState={""};
            f.inst.runOnMainSync(()->{
              try {
                FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
                assertNotNull("后台窗口不得丢失",overlay);
                position[0]=overlay.baseMs;
                PlaybackManager.TrackMetadata metadata=(PlaybackManager.TrackMetadata)field(manager,"currentMetadata");
                song[0]=metadata.songId;
                androidx.media3.exoplayer.ExoPlayer player=(androidx.media3.exoplayer.ExoPlayer)field(manager,"player");
                nativeState[0]=player==null?"missing,false,false,-1,-1":player.getCurrentPosition()+","+player.isPlaying()+","+player.getPlayWhenReady()+","+player.getPlaybackState()+","+player.getPlaybackSuppressionReason();
                if(!overlay.lrcLines.isEmpty()&&!overlay.lrcLines.get(0).words.isEmpty())
                  lyric[0]=overlay.lrcLines.get(0).words.get(0).text;
              } catch(Exception e) { throw new AssertionError(e); }
            });
            long elapsed=SystemClock.uptimeMillis()-started;
            log.println(elapsed+","+song[0]+","+position[0]+","+lyric[0]+","+nativeState[0]); log.flush();
            if(elapsed<290000 && song[0]==previousSong) assertTrue("后台歌词时钟必须继续推进",position[0]>previousPosition+2500);
            if(position[0]>5000) assertTrue("切歌后必须加载当前歌曲歌词，无需打开 App",lyric[0].startsWith("曲"+(song[0]-991000)));
            second|=song[0]==991001; third|=song[0]==991002;
            previousSong=song[0]; previousPosition=position[0];
          }
        }
        assertTrue("自动切至第二首",second); assertTrue("自动切至第三首",third);
      } finally {
        File diagnosticDir=new File(f.activity.getExternalFilesDir(null),"detail-regression");
        assertTrue(diagnosticDir.exists()||diagnosticDir.mkdirs());
        // 只在后台验证结束后取诊断，不能在测试期间唤醒 WebView。
        try(java.io.PrintWriter diagnostic=new java.io.PrintWriter(new File(diagnosticDir,"long-background-final.json"),"UTF-8")) {
          diagnostic.print(f.js("JSON.stringify({songId:longMusic.playSong?.id,index:fixtureStatus.playIndex,fm:fixtureStatus.personalFmMode,lrc:longMusic.songLyric.lrcData.length,yrc:longMusic.songLyric.yrcData.length,trace:longLyricTrace})"));
        }
        f.js("longMusic.setSongLyric=longSetLyric");
        f.inst.runOnMainSync(()->{manager.cleanup();manager.hideFloatingLyric();});
        f.activity.startActivity(new android.content.Intent(f.activity,top.imsyy.splayer.android.MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        f.js("longData.setHistory=longHistory;longData.playList=longSaved.list;longMusic.playSong=longSaved.song;fixtureStatus.playIndex=longSaved.index;fixtureStatus.repeatMode=longSaved.repeat;fixtureStatus.showDesktopLyric=longSaved.overlay;fixtureStatus.playStatus=false;fixtureSetting.useNextPrefetch=longSaved.prefetch;");
        for(File audio:files) assertTrue(audio.delete());
      }
    }
  }
}

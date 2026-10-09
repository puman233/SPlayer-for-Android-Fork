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
import org.junit.Test;
import org.junit.runner.RunWith;

/** 真实 Native queue → JS trackChanged → LyricManager → 悬浮窗，使用真实同目录 LRC 文件。 */
@RunWith(AndroidJUnit4.class)
public class FloatingLyricLongPlaybackTest {
  private Object field(Object target,String name) throws Exception {
    Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
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
          log.println("elapsedMs,songId,positionMs,lyric");
          long started=SystemClock.uptimeMillis();
          while(SystemClock.uptimeMillis()-started<300000) {
            SystemClock.sleep(5000);
            long[] position={0},song={0}; String[] lyric={""};
            f.inst.runOnMainSync(()->{
              try {
                FloatingLyricService overlay=(FloatingLyricService)field(manager,"floatingLyricService");
                assertNotNull("后台窗口不得丢失",overlay);
                position[0]=overlay.baseMs;
                PlaybackManager.TrackMetadata metadata=(PlaybackManager.TrackMetadata)field(manager,"currentMetadata");
                song[0]=metadata.songId;
                if(!overlay.lrcLines.isEmpty()&&!overlay.lrcLines.get(0).words.isEmpty())
                  lyric[0]=overlay.lrcLines.get(0).words.get(0).text;
              } catch(Exception e) { throw new AssertionError(e); }
            });
            long elapsed=SystemClock.uptimeMillis()-started;
            log.println(elapsed+","+song[0]+","+position[0]+","+lyric[0]); log.flush();
            if(elapsed<290000 && song[0]==previousSong) assertTrue("后台歌词时钟必须继续推进",position[0]>previousPosition+2500);
            if(position[0]>5000) assertTrue("切歌后必须加载当前歌曲歌词，无需打开 App",lyric[0].startsWith("曲"+(song[0]-991000)));
            second|=song[0]==991001; third|=song[0]==991002;
            previousSong=song[0]; previousPosition=position[0];
          }
        }
        assertTrue("自动切至第二首",second); assertTrue("自动切至第三首",third);
      } finally {
        f.inst.runOnMainSync(()->{manager.cleanup();manager.hideFloatingLyric();});
        f.activity.startActivity(new android.content.Intent(f.activity,top.imsyy.splayer.android.MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        f.js("longData.setHistory=longHistory;longData.playList=longSaved.list;longMusic.playSong=longSaved.song;fixtureStatus.playIndex=longSaved.index;fixtureStatus.repeatMode=longSaved.repeat;fixtureStatus.showDesktopLyric=longSaved.overlay;fixtureStatus.playStatus=false;fixtureSetting.useNextPrefetch=longSaved.prefetch;");
        for(File audio:files) assertTrue(audio.delete());
      }
    }
  }
}

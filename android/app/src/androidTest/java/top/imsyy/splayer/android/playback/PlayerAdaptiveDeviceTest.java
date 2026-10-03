package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 隔离包验证播放器几何与真实悬浮歌词权限流程。 */
@RunWith(AndroidJUnit4.class)
public class PlayerAdaptiveDeviceTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private String shell(String command) throws Exception {
    try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(command);
        FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
  }
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    String[] result = new String[1];
    instrumentation.runOnMainSync(() -> web.evaluateJavascript(expression, value -> {
      result[0] = value; done.countDown();
    }));
    assertTrue(done.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  private void start() throws Exception {
    assertTrue(instrumentation.getTargetContext().getPackageName().endsWith(".lyricsverify"));
    Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity = (MainActivity) instrumentation.startActivitySync(intent);
    instrumentation.runOnMainSync(() -> web = activity.getBridge().getWebView());
    for (int i=0;i<100;i++) {
      if ("true".equals(js("!!document.querySelector('#app')?.__vue_app__"))) break;
      SystemClock.sleep(200);
    }
    assertEquals("true", js("!!document.querySelector('#app')?.__vue_app__"));
    js("(()=>{const a=document.querySelector('#app').__vue_app__;window.verifyPinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);const s=verifyPinia._s.get('setting'),t=verifyPinia._s.get('status'),m=verifyPinia._s.get('music');s.userAgreementVersion='v2.0';s.androidDeviceModeOverride='auto';s.phonePortraitPageZoom=s.padPageZoom=s.padPortraitPageZoom=100;s.useAMLyrics=false;s.playerType='cover';s.fullscreenPlayerElements.desktopLyric=true;window.$modal?.destroyAll();m.playSong={...m.playSong,id:12345,type:'song',name:'长歌曲标题 Desktop Lyrics テスト 🎵'.repeat(3),artists:[{id:1,name:'长歌手名称 Artist 日本語'.repeat(5)}],album:{id:2,name:'长专辑 Album 日本語'.repeat(5)}};m.songLyric={lrcData:[{time:0,endTime:60000,content:'歌词 Lyrics 歌詞'}],yrcData:[]};t.showFullPlayer=true;t.playerMetaShow=true;t.playLoading=false;})()");
    SystemClock.sleep(800);
  }
  private void report(String key, String value) {
    Bundle data = new Bundle(); data.putString(key, value); instrumentation.sendStatus(0,data);
  }
  private void captureWindow(int rotation) throws Exception {
    captureWindow("player-adaptive-"+rotation);
  }
  private void captureWindow(String name) throws Exception {
    CountDownLatch done=new CountDownLatch(1);
    int[] result=new int[1];
    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(web.getWidth(),web.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
    instrumentation.runOnMainSync(() -> android.view.PixelCopy.request(activity.getWindow(),bitmap,r -> {result[0]=r;done.countDown();},new android.os.Handler(android.os.Looper.getMainLooper())));
    assertTrue(done.await(5,TimeUnit.SECONDS));
    assertEquals(android.view.PixelCopy.SUCCESS,result[0]);
    java.io.File file=new java.io.File(activity.getExternalFilesDir(null),name+".png");
    try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}
    bitmap.recycle();
    report("playerScreenshot",file.getAbsolutePath());
  }
  private void awaitCondition(String expression) throws Exception {
    for (int i=0;i<80;i++) {
      if ("true".equals(js(expression))) return;
      SystemClock.sleep(100);
    }
    fail("等待实际页面条件失败: " + expression);
  }

  @Test public void playerFitsRealFontsAndRotations() throws Exception {
    String originalScale=shell("settings get system font_scale");
    String originalGlobal=shell("settings get system user_rotation");
    String rotation=null;
    int[] display=new int[1];
    try {
      start();
      instrumentation.runOnMainSync(() -> display[0]=activity.getDisplay().getDisplayId());
      rotation=shell("wm user-rotation -d "+display[0]);
      for (double scale:new double[]{1,1.15,1.3,1.5,2}) {
        shell("settings put system font_scale "+scale);
        awaitCondition("Math.abs(Number(document.querySelector('.app-shell').dataset.systemFontScale)-"+scale+")<0.02");
        for (int r:new int[]{0,1}) {
          shell("wm user-rotation -d "+display[0]+" lock "+r);
          SystemClock.sleep(1300);
          for (String mode:new String[]{"cover","record","fullscreen"}) {
            js("verifyPinia._s.get('setting').playerType='"+mode+"';verifyPinia._s.get('status').playerMetaShow=true;window.$modal?.destroyAll()");
            SystemClock.sleep(400);
            String raw=js("JSON.stringify((()=>{const root=document.querySelector('.full-player');const rect=e=>{if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height}};const c=root.querySelector('.player-control,.mobile-player-bottom-controls');const art=root.querySelector('.wide-cover-space,.landscape-cover,.info-page .cover-section');const info=root.querySelector('.wide-metadata,.left-section .info,.info-page .info-group');const b=root.querySelector('.desktop-lyrics-button');return {width:innerWidth,height:innerHeight,control:rect(c),art:rect(art),info:rect(info),desktop:rect(b),buttons:[...c.querySelectorAll('.btn-icon,.play-pause,.ctrl-btn,.play-btn')].map(rect),overlap:[...c.querySelectorAll('.btn-icon,.play-pause,.ctrl-btn,.play-btn')].some(e=>{const i=e.querySelector('svg');return i&&i.getBoundingClientRect().width>e.getBoundingClientRect().width+1})}})())");
            JSONObject g=new JSONObject((String)new JSONTokener(raw).nextValue());
            g.put("scale",scale); g.put("rotation",r); g.put("mode",mode); report("playerAdaptive",g.toString());
            JSONObject c=g.getJSONObject("control");
            assertTrue("控制栏必须在安全窗口内: "+g,c.getDouble("y")>=-1&&c.getDouble("bottom")<=g.getDouble("height")+1);
            assertTrue("控制栏不能溢出横向窗口: "+g,c.getDouble("x")>=-1&&c.getDouble("right")<=g.getDouble("width")+1);
            JSONObject b=g.getJSONObject("desktop");
            assertTrue("独立歌词入口可见: "+g,b.getDouble("height")>=47&&b.getDouble("width")>=47&&b.getDouble("y")>=-1&&b.getDouble("right")<=g.getDouble("width")+1);
            JSONArray buttons=g.getJSONArray("buttons"); assertTrue(buttons.length()>=3);
            for(int i=0;i<buttons.length();i++) {
              JSONObject k=buttons.getJSONObject(i);
              assertTrue("核心触控区域不足: "+g,k.getDouble("width")>=47&&k.getDouble("height")>=47);
              assertTrue("核心操作不能超出控制栏: "+g,k.getDouble("x")>=c.getDouble("x")-1&&k.getDouble("right")<=c.getDouble("right")+1&&k.getDouble("bottom")<=c.getDouble("bottom")+1);
            }
            assertFalse("图标不得比按钮盒更宽",g.getBoolean("overlap"));
            if(scale==2 && mode.equals("cover")) captureWindow(r);
            if(!g.isNull("art")&&!g.isNull("info")) {
              JSONObject art=g.getJSONObject("art"),info=g.getJSONObject("info");
              assertTrue("封面与信息区域不能重叠: "+g,art.getDouble("bottom")<=info.getDouble("y")+1);
              assertTrue("信息区域不能压入控件: "+g,info.getDouble("bottom")<=c.getDouble("y")+1);
            }
          }
        }
      }
    } finally {
      shell(originalScale.equals("null")?"settings delete system font_scale":"settings put system font_scale "+originalScale);
      if(rotation!=null) shell("wm user-rotation -d "+display[0]+" "+rotation);
      shell("settings put system user_rotation "+originalGlobal);
      if(activity!=null) instrumentation.runOnMainSync(activity::finish);
    }
  }

  @Test public void desktopButtonTracksNativeWindowAndContinuesAfterPermission() throws Exception {
    String pkg=instrumentation.getTargetContext().getPackageName();
    try {
      start();
      awaitCondition("!!document.querySelector('.desktop-lyrics-button:not([disabled])')");
      js("document.querySelector('.desktop-lyrics-button').click()");
      awaitCondition("document.querySelector('.desktop-lyrics-button').dataset.desktopLyricsState==='ON'");
      assertTrue(PlaybackManager.getInstance(instrumentation.getTargetContext()).isFloatingLyricRunning());
      instrumentation.runOnMainSync(() -> activity.stopService(new Intent(activity,FloatingLyricService.class)));
      awaitCondition("document.querySelector('.desktop-lyrics-button').dataset.desktopLyricsState==='OFF'");
      shell("appops set "+pkg+" SYSTEM_ALERT_WINDOW deny");
      instrumentation.runOnMainSync(activity::onResume);
      awaitCondition("document.querySelector('.desktop-lyrics-button').dataset.desktopLyricsState==='PERMISSION_REQUIRED'");
      js("document.querySelector('.desktop-lyrics-button').click()");
      awaitCondition("!!document.querySelector('.n-dialog')");
      js("[...document.querySelectorAll('.n-dialog .n-button')].find(b=>b.textContent.includes('去授权')).click()");
      SystemClock.sleep(800);
      String settingsPackage="";
      for(int i=0;i<50;i++) {
        android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
        if(root!=null) { settingsPackage=String.valueOf(root.getPackageName());root.recycle(); }
        if(settingsPackage.contains("settings")) break;
        SystemClock.sleep(100);
      }
      report("permissionSettings",settingsPackage);
      assertTrue("必须实际进入系统设置页: "+settingsPackage,settingsPackage.contains("settings"));
      shell("appops set "+pkg+" SYSTEM_ALERT_WINDOW allow");
      shell("input keyevent KEYCODE_BACK");
      awaitCondition("document.querySelector('.desktop-lyrics-button').dataset.desktopLyricsState==='ON'");
      assertTrue(PlaybackManager.getInstance(instrumentation.getTargetContext()).isFloatingLyricRunning());
      js("document.querySelector('.desktop-lyrics-button').click()");
      awaitCondition("document.querySelector('.desktop-lyrics-button').dataset.desktopLyricsState==='OFF'");
      report("desktopButton","原生开关、服务停止、缺权限状态与系统设置返回自动继续通过");
    } finally {
      shell("appops set "+pkg+" SYSTEM_ALERT_WINDOW allow");
      if(activity!=null) instrumentation.runOnMainSync(() -> { activity.stopService(new Intent(activity,FloatingLyricService.class));activity.finish(); });
    }
  }

  /** 检查共享控件、真实列表测量及跳转末尾，不使用正式用户数据。 */
  @Test public void sharedComponentsTrackFontAndMeasuredRows() throws Exception {
    String originalScale=shell("settings get system font_scale");
    String originalGlobal=shell("settings get system user_rotation");
    String rotation=null;
    int[] display=new int[1];
    try {
      start();
      instrumentation.runOnMainSync(() -> display[0]=activity.getDisplay().getDisplayId());
      rotation=shell("wm user-rotation -d "+display[0]);
      js("verifyPinia._s.get('status').showFullPlayer=false;verifyPinia._s.get('status').showPlayBar=true;document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/history')");
      awaitCondition("document.querySelector('#app').__vue_app__.config.globalProperties.$router.currentRoute.value.path==='/history'");
      SystemClock.sleep(1500);
      js("(()=>{const s=verifyPinia._s.get('setting'),t=verifyPinia._s.get('status'),m=verifyPinia._s.get('music');s.showLocalCover=true;s.showSongArtist=true;s.showSongQuality=true;s.showSongOriginalTag=true;s.showSongPrivilegeTag=true;s.showSongOperations=true;s.showSongAlbum=true;window.sharedSongs=Array.from({length:200},(_,i)=>({...m.playSong,id:2001+i,name:'歌曲 '+i+' 長いタイトル English 中文 🎵'.repeat(3),path:'/isolated-test/song-'+i+'.mp3',quality:'Hi-Res',free:1,originCoverType:2,mv:123,artists:[{id:1,name:'歌手 Artist 日本語'.repeat(4)}]}));verifyPinia._s.get('data').historyList=sharedSongs;verifyPinia._s.get('data').playList=sharedSongs.slice(0,100);m.playSong=sharedSongs[50];t.playIndex=50;t.playRate=1.25;t.playStatus=false;})()");
      awaitCondition("document.querySelectorAll('.song-list .song-card').length>2");
      for(double scale:new double[]{1,1.15,1.3,1.5,2}) {
        shell("settings put system font_scale "+scale);
        awaitCondition("Math.abs(Number(document.querySelector('.app-shell').dataset.systemFontScale)-"+scale+")<0.02");
        for(int r:new int[]{0,1}) {
          shell("wm user-rotation -d "+display[0]+" lock "+r);
          SystemClock.sleep(1200);
          js("(()=>{const e=document.querySelector('.song-list .n-scrollbar-container');e.scrollTop=0;e.dispatchEvent(new Event('scroll'));})()");
          SystemClock.sleep(700);
          String raw=js("JSON.stringify((()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height}};const mini=document.querySelector('.main-player'),rows=[...document.querySelectorAll('.song-list .virtual-item')];return {width:innerWidth,height:innerHeight,mini:rect(mini),miniButtons:[...mini.querySelectorAll('.adaptive-button,.play-menu .menu-icon')].filter(e=>e.getClientRects().length).map(rect),lyric:rect(mini.querySelector('.lyric-container')),title:rect(mini.querySelector('.name')),rows:rows.map(e=>({index:Number(e.dataset.index),...rect(e)})),tags:[...document.querySelectorAll('.song-list .adaptive-tag')].map(e=>({box:rect(e),content:rect(e.querySelector('.n-tag__content'))}))}})())");
          JSONObject g=new JSONObject((String)new JSONTokener(raw).nextValue());g.put("scale",scale);g.put("rotation",r);report("sharedAdaptive",g.toString());
          JSONObject mini=g.getJSONObject("mini");
          assertTrue("迷你栏必须在窗口内: "+g,mini.getDouble("x")>=-1&&mini.getDouble("right")<=g.getDouble("width")+1&&mini.getDouble("y")>=-1&&mini.getDouble("bottom")<=g.getDouble("height")+1);
          assertTrue("标题至少保留可读空间: "+g,g.getJSONObject("title").getDouble("width")>=30);
          JSONArray buttons=g.getJSONArray("miniButtons");assertTrue(buttons.length()>=5);
          for(int i=0;i<buttons.length();i++) {JSONObject b=buttons.getJSONObject(i);assertTrue("迷你栏按钮不足48或越界: "+g,b.getDouble("width")>=47&&b.getDouble("height")>=47&&b.getDouble("right")<=mini.getDouble("right")+1&&b.getDouble("bottom")<=mini.getDouble("bottom")+1);}
          JSONArray rows=g.getJSONArray("rows");assertTrue("虚拟列表不能渲染全部数据",rows.length()>2&&rows.length()<40);
          for(int i=0;i<rows.length()-1;i++) {JSONObject a=rows.getJSONObject(i),b=rows.getJSONObject(i+1);assertTrue("实际行高必须与下一行定位一致: "+g,Math.abs(a.getDouble("bottom")-b.getDouble("y"))<1.5);}
          JSONArray tags=g.getJSONArray("tags");assertTrue(tags.length()>0);
          for(int i=0;i<tags.length();i++) {JSONObject tag=tags.getJSONObject(i),box=tag.getJSONObject("box"),content=tag.getJSONObject("content");assertTrue("标签文字不能被固定高度裁切",content.getDouble("height")<=box.getDouble("height")+1);}
          assertEquals("队列数量不得被菜单滚动容器裁切","true",js("(()=>{const menu=document.querySelector('.main-player .play-menu').getBoundingClientRect();return [...document.querySelectorAll('.main-player .play-menu .n-badge-sup')].every(e=>{const r=e.getBoundingClientRect();return r.top>=menu.top-1&&r.bottom<=menu.bottom+1&&r.right<=menu.right+1})})()"));
          assertEquals("徽标数字本体必须容纳大字号","true",js("(()=>{const e=document.querySelector('.main-player .playlist-count');if(!e||e.textContent.trim()!=='100')return false;const r=e.getBoundingClientRect(),b=e.closest('.n-badge-sup').getBoundingClientRect();return r.width<=b.width+1&&r.top>=b.top-1&&r.bottom<=b.bottom+1})()"));
          for(double fraction:new double[]{0.45,1}) {
            js("(()=>{const e=document.querySelector('.song-list .n-scrollbar-container');e.scrollTop=(e.scrollHeight-e.clientHeight)*"+fraction+";e.dispatchEvent(new Event('scroll'));})()");SystemClock.sleep(800);
            report("sharedScroll",js("JSON.stringify([...document.querySelectorAll('.song-list .virtual-item')].map(e=>({index:e.dataset.index,y:e.getBoundingClientRect().y,height:e.getBoundingClientRect().height,transform:e.style.transform})))"));
            assertEquals("滚动后实际行位置一致","true",js("(()=>{const rows=[...document.querySelectorAll('.song-list .virtual-item')];return rows.length<40&&rows.every((e,i)=>i===0||Math.abs(rows[i-1].getBoundingClientRect().bottom-e.getBoundingClientRect().top)<1.5)})()"));
            if(fraction==1) assertEquals("必须能滚动到最后一首","true",js("[...document.querySelectorAll('.song-list .virtual-item')].some(e=>Number(e.dataset.index)===199)"));
          }
          js("verifyPinia._s.get('status').playListShow=true");awaitCondition("!!document.querySelector('#main-playlist .song-item')");SystemClock.sleep(500);
          js("document.querySelector('#main-playlist .playlist-menu .n-button__content').textContent='定位当前歌曲 Locate 日本語 ボタン'");
          report("sharedQueueButtons",js("JSON.stringify([...document.querySelectorAll('#main-playlist .playlist-menu .adaptive-button')].map(e=>{const r=e.getBoundingClientRect(),c=e.querySelector('.n-button__content').getBoundingClientRect();return {width:r.width,height:r.height,right:r.right,bottom:r.bottom,contentWidth:c.width,contentHeight:c.height,windowWidth:innerWidth,windowHeight:innerHeight}}))"));
          SystemClock.sleep(200);
          captureWindow("shared-queue-check-"+scale+"-"+r);
          assertEquals("队列底栏长文案与48px触控区域必须容纳","true",js("[...document.querySelectorAll('#main-playlist .playlist-menu .adaptive-button')].every(e=>{const r=e.getBoundingClientRect(),c=e.querySelector('.n-button__content').getBoundingClientRect();return r.width>=47&&r.height>=47&&c.height<=r.height&&c.width<=r.width&&r.right<=innerWidth+1&&r.bottom<=innerHeight+1})"));
          assertEquals("播放队列行不能重叠","true",js("(()=>{const rows=[...document.querySelectorAll('#main-playlist .virtual-item')];return rows.length>1&&rows.every((e,i)=>i===0||Math.abs(rows[i-1].getBoundingClientRect().bottom-e.getBoundingClientRect().top)<1.5)})()"));
          if(scale==2) captureWindow("shared-components-queue-"+r);
          js("verifyPinia._s.get('status').playListShow=false");SystemClock.sleep(400);
          if(scale==2) captureWindow("shared-components-"+r);
        }
      }
      js("verifyPinia._s.get('status').playListShow=true");awaitCondition("!!document.querySelector('#main-playlist .remove')");
      js("document.querySelector('#main-playlist .remove').click()");awaitCondition("verifyPinia._s.get('data').playList.length===99");
      report("sharedComponents","五档字号、双方向、迷你栏、标签、列表中部/末尾、队列与移除按钮通过");
    } finally {
      if(web!=null) js("verifyPinia._s.get('status').playListShow=false");
      shell(originalScale.equals("null")?"settings delete system font_scale":"settings put system font_scale "+originalScale);
      if(rotation!=null) shell("wm user-rotation -d "+display[0]+" "+rotation);
      shell("settings put system user_rotation "+originalGlobal);
      if(activity!=null) instrumentation.runOnMainSync(activity::finish);
    }
  }
}

package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** User gestures and rendered overlay pixels, exclusively in the existing isolated debug package. */
@RunWith(AndroidJUnit4.class)
public class NextStageInteractionTest {
  @Test public void amllBlurRemainsVisibleAfterRealTouch() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
      f.js("p7Init().then(async()=>{await window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(false);fixtureSetting.useAMLyrics=true;fixtureSetting.lyricsBlur=true;fixtureSetting.autoHidePlayerMeta=false;window.blurReady=true})");
      try {
        f.await("window.blurReady===true&&!!document.querySelector('.am-lyric div[class*=lyricLineWrapper]')");
        f.js("[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)?.click()");
        SystemClock.sleep(700);
        swipe(f,".am-lyric",-1);SystemClock.sleep(6500);
        f.await("[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].some(e=>parseFloat(e.style.filter.match(/blur\\(([^p]+)/)?.[1]||0)>0)");
        f.capture("amll-blur-after-touch");
        assertEquals("开启模糊后真实触摸不能让CSS覆盖引擎模糊 "+f.js("JSON.stringify({hover:document.querySelector('.amll-lyric-player').matches(':hover'),lines:[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].map(e=>({inline:e.style.filter,actual:getComputedStyle(e).filter}))})"),"true",f.js("[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].some(e=>parseFloat(getComputedStyle(e).filter.match(/blur\\(([^p]+)/)?.[1]||0)>0)"));
        JSONObject hover=json(f,"document.querySelector('.amll-lyric-player').getBoundingClientRect().toJSON()");
        f.js("window.hoverEvents=[];window.hoverRecord=e=>hoverEvents.push({type:e.type,x:e.clientX,y:e.clientY,target:String(e.target.className)});for(const t of ['pointerover','pointermove','mouseover','mousemove'])document.addEventListener(t,hoverRecord,true)");
        float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
        for(int action:new int[]{MotionEvent.ACTION_HOVER_ENTER,MotionEvent.ACTION_HOVER_MOVE}) {
          MotionEvent.PointerProperties pointer=new MotionEvent.PointerProperties();pointer.id=0;pointer.toolType=MotionEvent.TOOL_TYPE_MOUSE;
          MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();
          point.x=(float)(hover.getDouble("x")+hover.getDouble("width")/2)*scale;
          point.y=(float)(hover.getDouble("y")+hover.getDouble("height")/2)*scale;
          MotionEvent event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{pointer},new MotionEvent.PointerCoords[]{point},0,0,1,1,-1,0,android.view.InputDevice.SOURCE_MOUSE,0);
          // WebView公开悬停入口将原生鼠标事件交给渲染器，不依赖MuMu显示器输入路由。
          try {f.inst.runOnMainSync(()->f.web.onHoverEvent(event));}
          finally {event.recycle();}
        }
        try {f.await("document.querySelector('.amll-lyric-player').matches(':hover')");}
        catch(AssertionError error){
          f.capture("amll-hover-precondition-failure");
          Bitmap screen=f.inst.getUiAutomation().takeScreenshot();
          if(screen!=null)try(var out=new java.io.FileOutputStream(new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression/amll-hover-global-failure.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}
          throw new AssertionError("鼠标输入诊断="+f.js("JSON.stringify({events:hoverEvents,visible:document.visibilityState,focus:document.hasFocus(),bounds:document.querySelector('.amll-lyric-player').getBoundingClientRect().toJSON(),hit:(()=>{const r=document.querySelector('.amll-lyric-player').getBoundingClientRect();return document.elementsFromPoint(r.x+r.width/2,r.y+r.height/2).slice(0,5).map(e=>String(e.className))})()})"),error);
        }
        SystemClock.sleep(500);
        assertEquals("鼠标停留也不能覆盖用户开启的模糊选项","true",f.js("[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].some(e=>parseFloat(getComputedStyle(e).filter.match(/blur\\(([^p]+)/)?.[1]||0)>0)"));
        f.js("fixtureSetting.lyricsBlur=false");SystemClock.sleep(700);
        assertEquals("关闭模糊必须清除每行模糊","true",f.js("[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].every(e=>!parseFloat(getComputedStyle(e).filter.match(/blur\\(([^p]+)/)?.[1]||0))"));
      } finally {f.js("if(window.hoverRecord)for(const t of ['pointerover','pointermove','mouseover','mousemove'])document.removeEventListener(t,hoverRecord,true);window.__SPLAYER_PLAYER_CONTROLLER__.setDesktopLyricShow(Boolean(p7Saved.status.showDesktopLyric)).then(()=>p7Restore())");f.await("window.p7Restored===true");}
    }
  }
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(var fd=f.inst.getUiAutomation().executeShellCommand(command);var in=new java.io.FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();}
  }
  @Test public void lyricsPageRemainsAvailableWhileLoadingAndAcrossSongChanges() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.useAMLyrics=false;fixtureSetting.autoHidePlayerMeta=false;p7Stores.music.setSongLyric({lrcData:[],yrcData:[]},true);fixtureStatus.lyricLoading=true;window.lyricPageReady=true})");
      try {
        f.await("window.lyricPageReady===true");SystemClock.sleep(500);
        assertEquals("歌词尚未返回时不能移除整个歌词页面", "true", f.js("!!document.querySelector('.full-player-mobile .lyric-page')"));
        JSONObject r=new JSONObject((String)new JSONTokener(f.js("JSON.stringify([...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1).getBoundingClientRect().toJSON())")).nextValue());
        float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
        float x=(float)(r.getDouble("x")+r.getDouble("width")/2)*scale,y=(float)(r.getDouble("y")+r.getDouble("height")/2)*scale;long down=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(80);}
        f.await("document.querySelector('.full-player-mobile').classList.contains('lyric-active')&&!!document.querySelector('.lyric-page .lyric-loading')");
        f.js("fixtureStatus.lyricLoading=false");f.await("document.querySelector('.lyric-page .lyric-empty')?.textContent.includes('暂无歌词')");
        f.js("p7SetLyrics(true)");f.await("!!document.querySelector('.lyric-page .yrc-word')&&document.querySelector('.full-player-mobile').classList.contains('lyric-active')");
        f.js("p7Stores.music.playSong={...p7Songs[1]};p7Stores.music.setSongLyric({lrcData:[],yrcData:[]},true);fixtureStatus.lyricLoading=true");
        f.await("document.querySelector('.full-player-mobile').classList.contains('lyric-active')&&!!document.querySelector('.lyric-page .lyric-loading')");
        f.capture("lyric-page-preserved-on-track-change");
      } finally {f.js("p7Restore()");f.await("window.p7Restored===true");}
    }
  }
  @Test public void betaChannelIsOptInAndShowsExplicitLabels() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      // 默认值由全新 Pinia 实例单测验证；已安装包可能有用户主动保存的 true。
      String savedBeta=f.js("fixtureSetting.checkBetaUpdates");
      try {
      f.js("fixtureSetting.checkBetaUpdates=false");
      f.js("window.nextCache=sessionStorage.getItem('androidUpdateLogV2');sessionStorage.setItem('androidUpdateLogV2',JSON.stringify({expiry:Date.now()+600000,value:[{tag_name:'v3.0.17-beta.2',prerelease:true,draft:false,body:'Beta release',published_at:'2026-10-16T00:00:00Z',html_url:'https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.17-beta.2'},{tag_name:'v3.0.15',prerelease:false,draft:false,body:'Stable release',published_at:'2026-10-05T00:00:00Z',html_url:'https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v3.0.15'}]}));document.querySelector('.nav button[title=\"设置\"]').click()");
      f.await("[...document.querySelectorAll('.n-dropdown-option')].some(e=>e.textContent.includes('设置'))");
      f.js("[...document.querySelectorAll('.n-dropdown-option-body')].find(e=>e.textContent.includes('全局设置')).click()");
      f.await("!!document.querySelector('.setting')");
      f.js("document.querySelector('.setting .mobile-title button')?.click();[...document.querySelectorAll('.setting .n-menu-item-content')].find(e=>e.textContent.includes('关于')).click()");
      f.await("!!document.querySelector('[aria-label=\"检查 Beta 版本\"]')");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&a.effect?.target?.closest?.('.n-modal,.n-modal-container'))");
        assertEquals("Beta开关应保持右侧独立48px触控区，说明在左侧完整换行","true",f.js("(()=>{const d=document.querySelector('.beta-description').getBoundingClientRect(),s=document.querySelector('[aria-label=\"检查 Beta 版本\"]').getBoundingClientRect();return s.width>=48&&s.height>=48&&s.left>=d.right&&s.right<=innerWidth&&d.width>0})()"));
        f.await("document.querySelector('.update-data')?.textContent.includes('v3.0.15')");
        assertEquals("默认不展示 Beta", "false", f.js("document.querySelector('.update-data')?.textContent.includes('beta.2')"));
        SystemClock.sleep(700);f.capture("next-update-stable");
        f.js("document.querySelector('[aria-label=\"检查 Beta 版本\"]').click()");
        f.await("fixtureSetting.checkBetaUpdates===true&&document.querySelector('.update-data')?.textContent.includes('beta.2')");
        assertEquals("Beta 必须明确标识", "true", f.js("document.querySelector('.update-data')?.textContent.includes('Beta')"));
        SystemClock.sleep(700);f.capture("next-update-beta");
        f.js("document.querySelector('[aria-label=\"检查 Beta 版本\"]').click()");
        f.await("fixtureSetting.checkBetaUpdates===false&&document.querySelector('.update-data')?.textContent.includes('v3.0.15')");
      } finally {f.js("fixtureSetting.checkBetaUpdates="+savedBeta+";window.$modal?.destroyAll();if(typeof nextCache!=='undefined'){if(nextCache===null)sessionStorage.removeItem('androidUpdateLogV2');else sessionStorage.setItem('androidUpdateLogV2',nextCache)}");}
    }
  }
  private static Object field(Object owner, String name) throws Exception {
    Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
  }
  private static JSONObject json(DeviceWebFixture f, String expression) throws Exception {
    return new JSONObject((String) new JSONTokener(f.js("JSON.stringify("+expression+")")).nextValue());
  }
  private void swipe(DeviceWebFixture f, String selector, float direction) throws Exception {
    JSONObject r = json(f, "document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect().toJSON()");
    float scale = (float)(f.web.getWidth() / Double.parseDouble(f.js("innerWidth")));
    float x = (float)(r.getDouble("x") + r.getDouble("width")*.5)*scale;
    float y = (float)(r.getDouble("y") + r.getDouble("height")*.65)*scale;
    float distance = (float)r.getDouble("height")*.4f*scale*direction;
    long down = SystemClock.uptimeMillis();
    for (int i=0; i<=20; i++) {
      int action = i==0?MotionEvent.ACTION_DOWN:i==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
      MotionEvent e = MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y+distance*i/20,0);
      f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e)); e.recycle(); SystemClock.sleep(25);
      if(i==12 && Math.abs(direction)>=.5f && selector.equals(".lyric-scroll-container")) {
        // 原生触摸滚动在 compositor 提交，先等待最后一段 MOVE 的滚动位置落地。
        SystemClock.sleep(200);
        double held=Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop"));
        f.js("nm.songLyric={...nm.songLyric,lrcData:nm.songLyric.lrcData.map(l=>({...l}))}");
        SystemClock.sleep(3400);
        assertEquals("歌词动态刷新且手指仍按住时不得回拉",held,Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop")),5);
      }
    }
  }

  @Test public void amllTouchBrowsingReturnsThroughExistingEngine() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int originalOrientation=f.activity.getRequestedOrientation();
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")) {
        f.js(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
      }
      f.js("window.followPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;window.followOriginalSeek=followPlayer.getSeek;p7Init().then(()=>{followPlayer.getSeek=()=>35000;fixtureSetting.androidDeviceModeOverride='phone';p7Stores.music.playSong={...p7Stores.music.playSong,id:99007101};p7Stores.setting.useAMLyrics=true;window.nextAmllReady=true})");
      try {
        f.await("window.nextAmllReady===true&&!!document.querySelector('.am-lyric div[class*=lyricMainLine]')");
        f.js("[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)?.click()");
        // Compare the same idle-control state and settled font/layout on both sides.
        f.await("document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden')");
        SystemClock.sleep(700);
        String geometry="JSON.stringify(['.am-lyric','.amll-lyric-player','.full-player-mobile'].map(s=>{const e=document.querySelector(s);return {s,c:e?.className,r:e?.getBoundingClientRect().toJSON()}}))";
        String beforeGeometry=f.js(geometry);
        String beforeLines=f.js("JSON.stringify([...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].map(e=>({text:e.textContent,y:e.getBoundingClientRect().y,height:e.getBoundingClientRect().height})))");
        // AMLL aligns the line wrapper; its inner word box has independent presentation offsets.
        String position="document.querySelector('.am-lyric div[class*=lyricLineWrapper]').getBoundingClientRect().y";
        double before=Double.parseDouble(f.js(position));
        swipe(f,".am-lyric",-1);SystemClock.sleep(600);
        double browsed=Double.parseDouble(f.js(position));
        assertTrue("AMLL真实触摸须能自由浏览",Math.abs(before-browsed)>20);
        SystemClock.sleep(6000);
        f.await("document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden')");
        SystemClock.sleep(700);
        f.capture("next-amll-idle");
        String state=f.js("JSON.stringify({seek:window.__SPLAYER_PLAYER_CONTROLLER__.getSeek(),lines:[...document.querySelectorAll('.am-lyric div[class*=lyricLineWrapper]')].map(e=>({text:e.textContent,y:e.getBoundingClientRect().y,height:e.getBoundingClientRect().height}))})");
        assertEquals("AMLL既有引擎在操作结束后恢复默认位置 "+state+" beforeLines="+beforeLines+" before="+beforeGeometry+" after="+f.js(geometry),before,Double.parseDouble(f.js(position)),5);
      } finally {f.js("followPlayer.getSeek=followOriginalSeek;p7Restore().then(()=>window.nextAmllRestored=true)");f.await("window.p7Restored===true");f.await("window.nextAmllRestored===true");f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(originalOrientation));}
    }
  }
  @Test public void touchBrowsingReturnsToDefaultLyricAnchorAfterIdle() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{window.nextOriginalSeek=window.__SPLAYER_PLAYER_CONTROLLER__.getSeek;window.__SPLAYER_PLAYER_CONTROLLER__.getSeek=()=>35000;Object.assign(fixtureSetting,{useAMLyrics:false,countDownShow:false,lyricsBlur:false,lyricFontSizeMode:'fixed',lyricFontSize:32,lyricTranFontSize:18,lyricRomaFontSize:18,lyricsScrollOffset:.45,autoHidePlayerMeta:false});window.nm=fixturePinia._s.get('music');nm.playSong={...nm.playSong,id:998877,name:'超长歌曲名 — multilingual lyrics 123456789 日本語',artists:[{id:1,name:'歌手'}],album:{id:2,name:'专辑'}};nm.songLyric={lrcData:Array.from({length:50},(_,i)=>({startTime:i*10000,endTime:(i+1)*10000,words:[{word:'第 '+i+' 行 长歌词 English 日本語 123456789',startTime:i*10000,endTime:(i+1)*10000}],translatedLyric:'翻译文本 translated line',romanLyric:'romaji line'})),yrcData:[]};window.defaultFollowReady=true})");
      f.await("window.defaultFollowReady===true");
      try {
        f.await("!!document.querySelector('.lyric-scroll-container .lrc-line.on')");
        // Phone player has separate cover/lyric pages; select the existing lyric page through its native UI.
        f.js("fixtureStatus.playerMetaShow=true;[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)?.click()");
        f.await("document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden')");
        SystemClock.sleep(700);
        String selector=".lyric-scroll-container";
        f.capture("next-lyric-before");
        f.js("window.defaultAnchorSnapshot=()=>{const c=document.querySelector('.lyric-scroll-container'),l=c.querySelector('.on');return {container:c.getBoundingClientRect().toJSON(),scroll:c.scrollTop,line:l?{id:l.id,offset:l.offsetTop,height:l.offsetHeight,rect:l.getBoundingClientRect().toJSON(),font:getComputedStyle(l.querySelector('.content')).fontSize}:null,mode:fixtureSetting.lyricFontSizeMode,offset:fixtureSetting.lyricsScrollOffset,meta:fixtureStatus.playerMetaShow,seek:window.__SPLAYER_PLAYER_CONTROLLER__.getSeek()}}");
        String anchorBefore=f.js("JSON.stringify(defaultAnchorSnapshot())");
        assertEquals("稳定视窗应使用项目默认37%空隙锚点，而非控件收起前的旧位置："+anchorBefore,.37,Double.parseDouble(f.js("(()=>{const s=defaultAnchorSnapshot();return (s.line.offset-s.scroll)/(s.container.height-s.line.height)})()")),.01);
        double before=Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop"));
        swipe(f,selector,-1);
        SystemClock.sleep(600);
        double browsed=Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop"));
        assertTrue("真实手指浏览必须改变歌词滚动位置",Math.abs(before-browsed)>30);
        f.capture("next-lyric-browsing");
        SystemClock.sleep(4200);
        f.capture("next-lyric-idle");
        double returned=Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop"));
        assertEquals("停止手指与惯性操作后回到项目原默认歌词位置，before="+anchorBefore+", after="+f.js("JSON.stringify(defaultAnchorSnapshot())"),before,returned,5);
        f.js("window.smallGestureTrace=[];window.smallGestureRecord=e=>smallGestureTrace.push({type:e.type,y:e.touches?.[0]?.clientY,scroll:document.querySelector('.lyric-scroll-container')?.scrollTop,seek:window.__SPLAYER_PLAYER_CONTROLLER__.getSeek()});for(const t of ['touchstart','touchmove','touchend','click'])document.querySelector('.lyric-scroll-container').addEventListener(t,smallGestureRecord,{capture:true,passive:true})");
        String seekBefore=f.js("window.__SPLAYER_PLAYER_CONTROLLER__.getSeek()");
        swipe(f,selector,-0.2f);SystemClock.sleep(600);
        double smallOffset=Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop"))-before;
        String trace=f.js("JSON.stringify(smallGestureTrace)");
        f.js("for(const t of ['touchstart','touchmove','touchend','click'])document.querySelector('.lyric-scroll-container').removeEventListener(t,smallGestureRecord,true)");
        assertTrue("小幅真实浏览须改变位置，且落在旧12%安全带内，实际偏移="+smallOffset+", trace="+trace,smallOffset>1&&smallOffset<Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').clientHeight"))*.12);
        SystemClock.sleep(4200);
        assertEquals("小幅浏览不能被误判成歌词点击跳转，trace="+trace,seekBefore,f.js("window.__SPLAYER_PLAYER_CONTROLLER__.getSeek()"));
        assertEquals("小幅偏离也应在操作结束后恢复原锚点，trace="+trace,before,Double.parseDouble(f.js("document.querySelector('.lyric-scroll-container').scrollTop")),1);
      } finally {
        f.js("window.__SPLAYER_PLAYER_CONTROLLER__.getSeek=nextOriginalSeek;p7Restore()");f.await("window.p7Restored===true");
      }
    }
  }

  @Test public void playbackLineAdvancePreservesHeldManualBrowsePosition() throws Exception {
    boolean interlude=Boolean.parseBoolean(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("interlude","false"));
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int orientation=f.activity.getRequestedOrientation();
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
      try {
        for(boolean amll:interlude?new boolean[]{true}:new boolean[]{false,true}) {
          f.js("p7Init(()=>{window.clockPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;window.clockOriginalSeek=clockPlayer.getSeek;window.playbackClock=41700;clockPlayer.getSeek=()=>playbackClock}).then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.useAMLyrics="+amll+";fixtureSetting.useAMSpring=true;p7Stores.music.playSong={...p7Songs[0],id:99007201};p7SetLyrics(true);"+(interlude?"const shift=lines=>lines.map((l,i)=>i<6?l:{...l,startTime:l.startTime+7000,endTime:l.endTime+7000,words:l.words.map(w=>({...w,startTime:w.startTime+7000,endTime:w.endTime+7000}))});p7Stores.music.setSongLyric({lrcData:shift(p7Stores.music.songLyric.lrcData),yrcData:shift(p7Stores.music.songLyric.yrcData)},true);":"")+"window.holdReady=true})");
          boolean held=false;float x=0,y=0;long down=0;
          try {
            f.await("window.holdReady===true");
            f.js("[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)?.click()");
            f.await("document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden')");SystemClock.sleep(700);
            f.js("fixtureStatus.playStatus=true;window.heldLineNode=document.querySelector('.am-lyric div[class*=lyricMainLine][class*=active]')?.closest('[class*=lyricLineWrapper]')");
            String selector=amll?".am-lyric":".lyric-scroll-container";
            String position=amll?"heldLineNode.getBoundingClientRect().y":"document.querySelector('.lyric-scroll-container').scrollTop";
            JSONObject r=json(f,"document.querySelector('"+selector+"').getBoundingClientRect().toJSON()");
            float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
            x=(float)(r.getDouble("x")+r.getDouble("width")*.5)*scale;
            float startY=(float)(r.getDouble("y")+r.getDouble("height")*.65)*scale;
            double initialAnchor=Double.parseDouble(f.js(amll?position:"document.querySelector('#lrc-5').getBoundingClientRect().y"));
            down=SystemClock.uptimeMillis();
            for(int step=0;step<=4;step++) {
              y=startY-55*scale*step/4;
              MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),step==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
              try{f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));}finally{event.recycle();}held=true;SystemClock.sleep(25);
            }
            SystemClock.sleep(120);
            double before=Double.parseDouble(f.js(position));
            for(int step=1;step<=20;step++){f.js("playbackClock="+(41700+step*20));SystemClock.sleep(20);}
            if(interlude)SystemClock.sleep(300);
            else f.await(amll?"[...document.querySelectorAll('.am-lyric div[class*=lyricMainLine][class*=active]')].some(e=>e.textContent.includes('远方 6'))":"!!document.querySelector('#lrc-6.on')");
            double after=Double.parseDouble(f.js(position));
            assertEquals((amll?"AMLL":"默认歌词")+"正常播放进入下一句不能移动手指按住时的浏览位置",before,after,5);
            for(int step=1;step<=4;step++) {
              y=startY-(55+35*step/4f)*scale;
              MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
              try{f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));}finally{event.recycle();}SystemClock.sleep(25);
            }
            SystemClock.sleep(120);
            assertEquals((amll?"AMLL":"默认歌词")+"跨句后继续拖动应跟随手指而非跳回旧偏移",amll?-35:35,Double.parseDouble(f.js(position))-after,5);
            if(interlude) {
              double heldInGap=Double.parseDouble(f.js(position));
              for(int step=1;step<=70;step++){f.js("playbackClock="+(42100+step*100));SystemClock.sleep(20);}
              f.await("[...document.querySelectorAll('.am-lyric div[class*=lyricMainLine][class*=active]')].some(e=>e.textContent.includes('远方 6'))");
              assertEquals("间奏结束不能移动按住时的浏览位置",heldInGap,Double.parseDouble(f.js(position)),5);
            }
            MotionEvent release=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);release.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try{f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(release));}finally{release.recycle();}held=false;
            SystemClock.sleep(6500);
            String recovered=amll?"document.querySelector('.am-lyric div[class*=lyricMainLine][class*=active]').closest('[class*=lyricLineWrapper]').getBoundingClientRect().y":"document.querySelector('#lrc-6').getBoundingClientRect().y";
            assertEquals((amll?"AMLL":"默认歌词")+"松手后应将当前播放句恢复到原默认锚点",initialAnchor,Double.parseDouble(f.js(recovered)),5);
          } finally {
            if(held){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);try{f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));}finally{event.recycle();}}
            f.js("clockPlayer.getSeek=clockOriginalSeek;window.holdReady=false;p7Restore()");f.await("window.p7Restored===true");
          }
        }
      } finally {f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));}
    }
  }

  @Test public void lockedControlDoesNotCoverAnyRenderedLyricPixels() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      PlaybackManager manager=PlaybackManager.getInstance(f.inst.getTargetContext());
      f.inst.runOnMainSync(manager::showFloatingLyric);
      FloatingLyricService service=null;
      for(int i=0;i<40&&service==null;i++){service=(FloatingLyricService)field(manager,"floatingLyricService");SystemClock.sleep(100);}
      assertNotNull(service);
      final FloatingLyricService overlay=service;
      View lyric=(View)field(overlay,"view");
      assertNotNull("悬浮歌词应已附着到真实显示器",lyric.getDisplay());
      int displayId=lyric.getDisplay().getDisplayId();
      assertTrue("桌面歌词所在显示器必须有效",lyric.getDisplay().isValid());
      String originalSize=shell(f,"wm size -d "+displayId);
      int originalWidth=(int)field(overlay,"windowWidthPercent"),originalHeight=(int)field(overlay,"windowHeightDp");
      try {
        JSONObject leftPlacement=new JSONObject().put("windowWidthPercent",60);
        f.inst.runOnMainSync(()->manager.updateFloatingLyricConfig(leftPlacement));
        f.inst.runOnMainSync(()->overlay.setLocked(false));SystemClock.sleep(400);
        android.graphics.RectF unlockedLock=new android.graphics.RectF((android.graphics.RectF)field(overlay,"rLock"));
        int toolbarHeight=(int)field(overlay,"controlHeight");
        assertTrue("未锁定工具栏必须实际绘制锁按钮",unlockedLock.width()>0);
        SystemClock.sleep(2800);
        Bitmap[] unlocked=new Bitmap[1];int[] originalPosition=new int[2];
        f.inst.runOnMainSync(()->{
          overlay.pushSongInfo("长歌词 LongLyrics0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ 日本語 日本語 日本語", "歌手");
          overlay.pushLyrics("[]","[]"); overlay.pushProgress(0,false);
          lyric.getLocationOnScreen(originalPosition);
          unlocked[0]=Bitmap.createBitmap(lyric.getWidth(),lyric.getHeight(),Bitmap.Config.ARGB_8888);lyric.draw(new Canvas(unlocked[0]));
          overlay.setLocked(true);
        });
        SystemClock.sleep(800);
        View control=(View)field(overlay,"unlockBtnView"); assertNotNull(control);
        assertEquals("正文与独立解锁入口必须位于同一有效显示器",displayId,control.getDisplay().getDisplayId());
        int[] a=new int[2], b=new int[2]; Bitmap[] bitmap=new Bitmap[1];
        f.inst.runOnMainSync(()->{lyric.getLocationOnScreen(a);control.getLocationOnScreen(b);bitmap[0]=Bitmap.createBitmap(lyric.getWidth(),lyric.getHeight(),Bitmap.Config.ARGB_8888);lyric.draw(new Canvas(bitmap[0]));});
        assertArrayEquals("锁定不能移动歌词窗口",originalPosition,a);
        assertTrue("锁定不能缩小正文显示范围或改变排版",unlocked[0].sameAs(bitmap[0]));unlocked[0].recycle();
        float d=control.getResources().getDisplayMetrics().density;
        assertTrue("解锁触控宽度至少48dp",control.getWidth()>=Math.round(48*d));
        android.graphics.Rect safe=(android.graphics.Rect)field(overlay,"safeArea");
        assertTrue("完整锁定触控区不得越过系统安全边界",b[0]>=safe.left&&b[1]>=safe.top
            &&b[0]+control.getWidth()<=safe.right&&b[1]+control.getHeight()<=safe.bottom);
        int gap=Math.round(6*d),size=control.getHeight();
        int referenceX=a[0]-size-gap;
        int referenceY=Math.max(safe.top,Math.min(a[1]+(lyric.getHeight()-size)/2,safe.bottom-size));
        if(referenceX>=safe.left) {
          assertEquals("有足够空间时锁入口必须在锁定歌词窗口正左侧",referenceX,b[0],1);
          assertEquals("左侧入口应与歌词正文区域垂直居中",referenceY,b[1],1);
        }
        int overlap=0;
        for(int y=Math.max(0,b[1]-a[1]);y<Math.min(bitmap[0].getHeight(),b[1]-a[1]+control.getHeight());y++)
          for(int x=Math.max(0,b[0]-a[0]);x<Math.min(bitmap[0].getWidth(),b[0]-a[0]+control.getWidth());x++)
            if(android.graphics.Color.alpha(bitmap[0].getPixel(x,y))>0) overlap++;
        java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"next-locked-lyric.png"))){bitmap[0].compress(Bitmap.CompressFormat.PNG,100,out);}
        if(control.getDisplay().getDisplayId()==android.view.Display.DEFAULT_DISPLAY) {
          Bitmap screen=f.inst.getUiAutomation().takeScreenshot();assertNotNull("原生悬浮窗截图应可获取",screen);
          try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"next-locked-lyric-screen.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}
        }
        bitmap[0].recycle();
        assertEquals("独立解锁控制的屏幕区域内不得绘制歌词正文或阴影",0,overlap);
        // Window receives actual system input; accessibility name must remain available.
        assertEquals("解锁桌面歌词",control.getContentDescription());
        int display=control.getDisplay().getDisplayId();
        try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand("input -d "+display+" tap "+(b[0]+control.getWidth()/2)+" "+(b[1]+control.getHeight()/2));java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
        SystemClock.sleep(500);assertNull("真实点击后解锁控制窗口应被移除",field(overlay,"unlockBtnView"));
        // A full-width lyric has no left-hand space, even when above/below is available.
        JSONObject fullWidth=new JSONObject().put("windowWidthPercent",100);
        f.inst.runOnMainSync(()->manager.updateFloatingLyricConfig(fullWidth));SystemClock.sleep(2800);
        f.inst.runOnMainSync(()->overlay.setLocked(true));SystemClock.sleep(500);
        View noLeftSpace=(View)field(overlay,"unlockBtnView");assertNotNull(noLeftSpace);
        assertEquals("左侧不足时不得把锁按钮移到歌词上方或下方",View.INVISIBLE,noLeftSpace.getVisibility());
        android.view.WindowManager.LayoutParams noLeftParams=(android.view.WindowManager.LayoutParams)field(overlay,"unlockLp");
        assertTrue("隐藏入口不能吞掉触摸",(noLeftParams.flags&android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
        String savedRoute=f.js("fixtureRouter.currentRoute.value.fullPath");
        try {
          f.js("fixtureRouter.push('/');document.querySelector('.nav button[title=\"设置\"]').click()");
          f.await("!!document.querySelector('.n-dropdown-option-body')");
          f.js("[...document.querySelectorAll('.n-dropdown-option-body')].find(e=>e.textContent.includes('全局设置')).click()");
          f.await("!!document.querySelector('.setting')");
          f.js("(()=>{const n=document.querySelector('.setting .set-left');if(n&&getComputedStyle(n).display==='none')document.querySelector('.setting .mobile-title button').click();[...document.querySelectorAll('.setting .n-menu-item-content')].find(e=>e.textContent.includes('歌词')).click()})()");
          f.await("!!document.querySelector('#setting-desktopLyricUnlock button')");
          f.js("document.querySelector('#setting-desktopLyricUnlock button').scrollIntoView({block:'center'})");SystemClock.sleep(400);
          f.tapVisible("document.querySelector('#setting-desktopLyricUnlock button')");
          for(int i=0;i<30&&field(overlay,"unlockBtnView")!=null;i++)SystemClock.sleep(100);
          assertNull("锁图标隐藏后仍可从真实设置按钮解锁",field(overlay,"unlockBtnView"));
        } finally {f.js("window.$modal?.destroyAll();fixtureRouter.push("+savedRoute+")");}
        float density=lyric.getResources().getDisplayMetrics().density;
        String resizeReply=shell(f,"wm size "+Math.round(260*density)+"x"+Math.round(260*density)+" -d "+displayId);
        for(int i=0;i<40&&((android.graphics.Rect)field(overlay,"safeArea")).height()>260*density+1;i++)SystemClock.sleep(100);
        assertTrue("低高度安全区必须实际生效: display="+displayId+", reply="+resizeReply+", wm="+shell(f,"wm size -d "+displayId)+", safe="+field(overlay,"safeArea"),((android.graphics.Rect)field(overlay,"safeArea")).height()<=260*density+1);
        JSONObject compact=new JSONObject().put("windowWidthPercent",100).put("windowHeightDp",240);
        f.inst.runOnMainSync(()->manager.updateFloatingLyricConfig(compact));SystemClock.sleep(3200);
        f.inst.runOnMainSync(()->overlay.setLocked(true));SystemClock.sleep(600);
        View hidden=(View)field(overlay,"unlockBtnView");
        assertNotNull(hidden);
        assertEquals("无48dp安全区时不能把图标放回正文",View.INVISIBLE,hidden.getVisibility());
        android.view.WindowManager.LayoutParams hiddenParams=(android.view.WindowManager.LayoutParams)field(overlay,"unlockLp");
        assertTrue("隐藏控制不能拦截正文触摸",(hiddenParams.flags&android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
        f.js("window.fallbackUnlocked=false;Capacitor.nativePromise('AndroidNativePlayback','unlockFloatingLyric',{}).then(()=>window.fallbackUnlocked=true)");
        f.await("window.fallbackUnlocked===true");
        assertNull("原生应用入口在极低高度仍可解锁",field(overlay,"unlockBtnView"));
        f.inst.runOnMainSync(()->overlay.setLocked(true));
        f.inst.runOnMainSync(manager::hideFloatingLyric);
        for(int i=0;i<40&&lyric.isAttachedToWindow();i++)SystemClock.sleep(100);
        f.inst.runOnMainSync(manager::showFloatingLyric);
        FloatingLyricService reopened=null;
        for(int i=0;i<40&&reopened==null;i++){reopened=(FloatingLyricService)field(manager,"floatingLyricService");if(reopened==overlay)reopened=null;SystemClock.sleep(100);}
        assertNotNull("关闭后应可重新打开桌面歌词",reopened);
        assertNull("关闭桌面歌词必须清除锁定，重新开启时不能仍锁定",field(reopened,"unlockBtnView"));

      } finally {
        f.inst.runOnMainSync(()->{overlay.setLocked(false);manager.hideFloatingLyric();});
        String previous=originalSize.contains("Override size:")?originalSize.substring(originalSize.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+displayId);
        JSONObject restore=new JSONObject().put("windowWidthPercent",originalWidth).put("windowHeightDp",originalHeight);
        f.inst.runOnMainSync(()->manager.updateFloatingLyricConfig(restore));
      }
    }
  }
}

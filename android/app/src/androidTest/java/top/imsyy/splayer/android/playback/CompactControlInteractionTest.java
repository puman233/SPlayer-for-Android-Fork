package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/** Real touch through public UI; audio resolution is isolated at the controller boundary. */
@RunWith(AndroidJUnit4.class)
public class CompactControlInteractionTest {
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())) {return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();}
  }
  private void tap(DeviceWebFixture f,String expression) throws Exception {
    try {f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&(document.querySelector('.full-player')?.contains(a.effect?.target)||a.effect?.target?.closest?.('.n-popover,.n-base-select-menu,.quick-actions-panel')))");}
    catch(AssertionError error) {
      throw new AssertionError("点击前动画诊断="+f.js("JSON.stringify(document.getAnimations().filter(a=>a.playState==='running').map(a=>({name:a.animationName,type:a.constructor.name,target:String(a.effect?.target?.className),timing:a.effect?.getComputedTiming(),time:a.currentTime})))"),error);
    }
    if(expression.contains(".info-page .meta-item")&&"true".equals(f.js("!!document.querySelector('.info-page .meta-settings-trigger')"))) {
      if(!"true".equals(f.js("!!document.querySelector('.merged-meta-panel')")))tap(f,"document.querySelector('.info-page .meta-settings-trigger')");
      expression=expression.replace(".info-page .meta-item",".merged-meta-panel .meta-item");
    }
    String point="(()=>{const e="+expression+";if(!e)return null;const r=e.getBoundingClientRect();for(const fy of [.5,.25,.75])for(const fx of [.5,.25,.75]){const x=r.x+r.width*fx,y=r.y+r.height*fy;if(e.contains(document.elementFromPoint(x,y)))return {x,y}}return null})()";
    try {f.await("!!"+point);}
    catch(AssertionError error) {
      f.capture("control-unreachable-option");
      throw new AssertionError("触摸目标诊断="+f.js("JSON.stringify((()=>{const e="+expression+";if(!e)return {missing:true};const r=e.getBoundingClientRect();return {text:e.textContent,rect:r.toJSON(),viewport:{width:innerWidth,height:innerHeight},stack:document.elementsFromPoint(Math.max(0,Math.min(innerWidth-1,r.x+r.width/2)),Math.max(0,Math.min(innerHeight-1,r.y+r.height/2))).slice(0,8).map(n=>({tag:n.tagName,cls:n.className,z:getComputedStyle(n).zIndex})),parents:[e,...(function*(n){while(n.parentElement){n=n.parentElement;yield n}})(e)].slice(0,7).map(n=>({cls:n.className,rect:n.getBoundingClientRect().toJSON(),overflow:getComputedStyle(n).overflow,z:getComputedStyle(n).zIndex}))}})())"),error);
    }
    JSONObject rect=new JSONObject((String)new JSONTokener(f.js("JSON.stringify("+point+")")).nextValue());
    float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
    float x=(float)rect.getDouble("x")*scale,y=(float)rect.getDouble("y")*scale;long down=SystemClock.uptimeMillis();
    f.js("window.controlTouchObserved=false;("+expression+").addEventListener('click',()=>window.controlTouchObserved=true,{once:true,capture:true})");
    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(80);}
    f.await("window.controlTouchObserved===true");
  }
  private String option(String label) {return "[...document.querySelectorAll('.n-base-select-option')].find(e=>e.textContent.includes("+JSONObject.quote(label)+"))";}
  @Test public void stalledTTMLPriorityFallsBackAndStopsLoading() throws Exception {
    boolean amll="true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("amll"));
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      for(String asset:new String[]{"r23-player-fixture.js","ttml-toggle-fixture.js"})
        try(var in=f.inst.getContext().getAssets().open(asset)){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(prepareTTML).catch(e=>window.ttmlError=e.name)");
      try {
        f.await("window.ttmlReady===true");
        f.js("fixtureSetting.useAMLyrics="+amll+";fixtureSetting.useAMSpring="+amll);
        f.js("window.savedTTMLFetch=window.fetch;window.fetch=(url,options,...args)=>String(url).startsWith('https://stalled-ttml.invalid/')?new Promise((resolve,reject)=>{window.stalledTTMLRequested=true;options?.signal?.addEventListener('abort',()=>{window.stalledTTMLAborted=true;reject(new DOMException('Aborted','AbortError'))},{once:true})}):savedTTMLFetch(url,options,...args);Object.assign(fixtureSetting,{lyricPriority:'ttml',enableOnlineTTMLLyric:true,showWordLyrics:true,amllDbServer:'https://stalled-ttml.invalid/%s'});window.Capacitor.nativePromise('AndroidCache','remove',{type:'lyrics',key:ttmlKeys[0]}).then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.setupSongUI(p7Stores.music.playSong,0))");
        f.await("window.stalledTTMLRequested===true");
        f.await("!fixtureStatus.lyricLoading&&p7Stores.music.songLyric.lrcData[0]?.words[0].word==='Fallback line'");
        assertEquals("超时TTML必须实际取消，回退官方歌词","true",f.js("window.stalledTTMLAborted===true&&!fixtureStatus.usingTTMLLyric"));
        fixtureOpenLyrics(f);
        f.await("(()=>{const e=document.querySelector('.player-lyric'),r=e?.getBoundingClientRect();return e?.textContent.includes('Fallback line')&&r.width>0&&r.height>0&&r.left>=-1&&r.right<=innerWidth+1})()");
        f.capture("stalled-ttml-fallback-visible"+(amll?"-amll":""));
      } finally {
        f.js("if(window.savedTTMLFetch)window.fetch=savedTTMLFetch;restoreTTML().then(p7Restore)");f.await("window.ttmlRestored&&window.p7Restored===true");
      }
    }
  }
  private void fixtureOpenLyrics(DeviceWebFixture f) throws Exception {
    f.js("fixtureStatus.showFullPlayer=true;fixtureSetting.autoHidePlayerMeta=false");
    if("true".equals(f.js("!!document.querySelector('.full-player-mobile .pagination .dot:last-child')"))) {
      tap(f,"document.querySelector('.full-player-mobile .pagination .dot:last-child')");
    }
  }
  @Test public void autoModeRendersReadyLyricsBeforeTTMLNetworkCompletes() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      for(String asset:new String[]{"r23-player-fixture.js","ttml-toggle-fixture.js"})
        try(var in=f.inst.getContext().getAssets().open(asset)){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(prepareTTML).catch(e=>window.ttmlError=e.name)");
      try {
        f.await("window.ttmlReady===true");
        f.js("window.savedTTMLFetch=window.fetch;window.fetch=(url,...args)=>String(url).startsWith('https://next-stage.invalid/')?new Promise(resolve=>{window.finishSlowTTML=()=>resolve(new Response(window.ttmlFixtureXml,{status:200}));window.slowTTMLRequested=true}):savedTTMLFetch(url,...args);Object.assign(fixtureSetting,{lyricPriority:'auto',enableOnlineTTMLLyric:true,showWordLyrics:true,amllDbServer:'https://next-stage.invalid/%s'});window.Capacitor.nativePromise('AndroidCache','remove',{type:'lyrics',key:ttmlKeys[0]}).then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.setupSongUI(p7Stores.music.playSong,0))");
        f.await("window.slowTTMLRequested===true");
        f.await("!fixtureStatus.lyricLoading&&p7Stores.music.songLyric.lrcData[0]?.words[0].word==='Fallback line'");
        assertEquals("未完成TTML不能阻止LRC先显示","false",f.js("fixtureStatus.usingTTMLLyric"));
        if("true".equals(f.js("!!document.querySelector('.pagination .dot')")))
          tap(f,"[...document.querySelectorAll('.pagination .dot')].at(-1)");
        f.await("(()=>{const e=document.querySelector('.player-lyric'),r=e?.getBoundingClientRect();return e?.textContent.includes('Fallback line')&&r.width>0&&r.height>0&&r.left>=-1&&r.right<=innerWidth+1})()");
        f.capture("auto-lyric-before-ttml");
        f.js("finishSlowTTML()");
        f.await("fixtureStatus.usingTTMLLyric&&p7Stores.music.songLyric.yrcData[0]?.words.length===2&&document.querySelectorAll('.player-lyric .yrc-word').length>=2");
        f.await("document.querySelector('.player-lyric')?.innerText.includes('hello')&&!document.querySelector('.player-lyric')?.innerText.includes('Fallback line')");
        f.js("window.ttmlFrameReady=false;requestAnimationFrame(()=>requestAnimationFrame(()=>window.ttmlFrameReady=true))");
        f.await("window.ttmlFrameReady===true");
        f.capture("auto-lyric-after-ttml");
      } finally {
        f.js("window.finishSlowTTML?.();if(window.savedTTMLFetch)window.fetch=savedTTMLFetch");
        f.await("!fixtureStatus.lyricLoading");
        f.js("restoreTTML()");f.await("window.ttmlRestored===true");
        f.js("p7Restore()");f.await("window.p7Restored===true");
      }
    }
  }
  @Test public void ttmlToggleRefreshesCurrentSongAndRendersTimedWords() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int orientation=f.activity.getRequestedOrientation();
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      try(var in=f.inst.getContext().getAssets().open("ttml-toggle-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';return prepareTTML()}).catch(e=>window.ttmlError=e.message)");
      try {
        f.await("window.ttmlReady===true");
        tap(f,"document.querySelector('.info-page .qa-trigger')");
        f.await("!!document.querySelector('.quick-actions-panel')");
        f.capture("ttml-more-menu-reachable");
        tap(f,"[...document.querySelectorAll('.quick-actions-panel .qa-item')].find(e=>e.textContent.includes('在线 TTML')).querySelector('.n-switch')");
        try { f.await("fixtureStatus.usingTTMLLyric&&fixtureSetting.showWordLyrics&&p7Stores.music.songLyric.yrcData[0]?.words.length===2"); }
        catch(AssertionError error) { f.capture("ttml-failure");throw new AssertionError("TTML state="+f.js("JSON.stringify({songId:p7Stores.music.playSong.id,songType:p7Stores.music.playSong.type,cacheEnabled:fixtureSetting.cacheEnabled,firstLine:p7Stores.music.songLyric.lrcData[0]?.words[0]?.word,enabled:fixtureSetting.enableOnlineTTMLLyric,word:fixtureSetting.showWordLyrics,priority:fixtureSetting.lyricPriority,ttml:fixtureStatus.usingTTMLLyric,loading:fixtureStatus.lyricLoading,yrc:p7Stores.music.songLyric.yrcData.length,words:p7Stores.music.songLyric.yrcData[0]?.words.length,lrc:p7Stores.music.songLyric.lrcData.length})"),error); }

        assertEquals("逐词时间必须来自真实TTML解析", "2000",f.js("p7Stores.music.songLyric.yrcData[0].words[1].startTime"));
        tap(f,"document.querySelector('.mobile-player-bottom-controls .time')");
        tap(f,"[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)");
        f.await("[...document.querySelectorAll('.lyric-page .yrc-word')].some(e=>e.textContent.trim()==='world')");
        f.await("[...document.querySelectorAll('.lyric-page .yrc-word')].some(e=>{const r=e.getBoundingClientRect();return e.textContent.trim()==='world'&&r.left>=0&&r.right<=innerWidth&&r.top>=0&&r.bottom<=innerHeight})");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&document.querySelector('.full-player')?.contains(a.effect?.target))");
        f.capture("ttml-real-word-render");
      } finally { f.js("restoreTTML()");f.await("window.ttmlRestored===true");f.js("p7Restore()");f.await("window.p7Restored===true");f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation)); }
    }
  }
  @Test public void modeButtonCyclesShuffleAndHeartbeat() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int orientation=f.activity.getRequestedOrientation();
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.autoHidePlayerMeta=false;fixtureStatus.shuffleMode='off';window.heartPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;window.heartOriginal=heartPlayer.toggleShuffle;window.heartCalls=[];heartPlayer.toggleShuffle=async(mode)=>{heartCalls.push(mode??'cycle');fixtureStatus.shuffleMode=mode??({off:'on',on:'heartbeat',heartbeat:'off'})[fixtureStatus.shuffleMode]};window.heartReady=true})");
      try {
        f.await("window.heartReady===true");
        assertEquals("心动应复用底部模式位置，不另占信息操作区", "true", f.js("!document.querySelector('.heartbeat-mode-button')"));
        for(String mode:new String[]{"on","heartbeat","off","on","heartbeat"}) {
          tap(f,"document.querySelector('.mobile-player-bottom-controls .mode-btn')");
          try { f.await("fixtureStatus.shuffleMode==="+JSONObject.quote(mode)); }
          catch(AssertionError error) { throw new AssertionError("heart touch state="+f.js("JSON.stringify({calls:heartCalls,mode:fixtureStatus.shuffleMode,labels:[...document.querySelectorAll('.heartbeat-mode-button')].map(e=>e.getAttribute('aria-label'))})"),error); }

        }
        assertEquals("心动命令应可重复进入", "true",f.js("JSON.stringify(heartCalls)===JSON.stringify(['cycle','cycle','cycle','cycle','cycle'])"));
        f.capture("heartbeat-cycle-button");
      } finally { f.js("heartPlayer.toggleShuffle=heartOriginal;p7Restore()");f.await("window.p7Restored===true");f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation)); }
    }
  }
  @Test public void playlistBatchSelectionAndActionsFitCompactWindow() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();String size=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;shell(f,"wm size "+Math.round(320*density)+"x"+Math.round(640*density)+" -d "+display);
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));f.await("Math.abs(innerWidth-320)<=1");f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(100));
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureStatus.showFullPlayer=false;p7Stores.music.dailySongsData={...p7Stores.music.dailySongsData,list:p7Songs,timestamp:Date.now()};fixtureRouter.push({name:'daily-songs'});window.batchReady=true})");
      try {
        f.await("window.batchReady===true&&!!document.querySelector('.daily-songs .more')");f.js("document.querySelector('.daily-songs .more').scrollIntoView({block:'center'})");SystemClock.sleep(400);
        f.capture("batch-entry-before");tap(f,"document.querySelector('.daily-songs .more')");f.await("[...document.querySelectorAll('.n-dropdown-option')].some(e=>e.textContent.includes('批量操作'))");tap(f,"[...document.querySelectorAll('.n-dropdown-option')].find(e=>e.textContent.includes('批量操作'))");f.await("!!document.querySelector('.batch-list')");f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(200));SystemClock.sleep(500);f.capture("batch-compact");
        assertEquals("窄屏批量弹窗应利用可用窗口而非70vw","true",f.js("(()=>{const r=document.querySelector('.batch-list').closest('.n-card').getBoundingClientRect();return r.width>=innerWidth-32&&r.left>=-1&&r.right<=innerWidth+1})()"));
        tap(f,"document.querySelector('.batch-list tbody .n-checkbox')");f.await("document.querySelector('.batch-footer .count').textContent.includes('1')");
        f.js("document.querySelector('.batch-footer').scrollIntoView({block:'center'})");SystemClock.sleep(300);
        assertEquals("批量按钮应完整可达且保留48dp目标","true",f.js("[...document.querySelectorAll('.batch-footer button')].every(e=>{const r=e.getBoundingClientRect();return r.width>=48&&r.height>=48&&r.left>=-1&&r.right<=innerWidth+1&&e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})"));
        tap(f,"[...document.querySelectorAll('.batch-footer button')].find(e=>e.textContent.includes('高级筛选'))");f.await("document.querySelectorAll('.range-input').length===2");
        assertEquals("范围筛选浮层内容应适配窄屏","true",f.js("[...document.querySelectorAll('.range-input')].every(e=>{const r=e.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth})"));
      } finally {
        f.js("window.$modal.destroyAll();p7Restore()");f.await("window.p7Restored===true");String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
  @Test public void landscapeIdleKeepsTagsOperableAndAnimatesControls() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();String size=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;shell(f,"wm size "+Math.round(360*density)+"x"+Math.round(720*density)+" -d "+display);
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));f.await("Math.abs(innerWidth-720)<=1");
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.lyricPriority='auto';window.landscapeReady=true})");f.await("window.landscapeReady===true&&!!document.querySelector('.full-player-mobile-landscape .meta-item')");
      try {
        tap(f,"document.querySelector('.landscape-header')");
        f.js("window.collapseHeights=[];window.collapseObserver=new ResizeObserver(entries=>collapseHeights.push(entries[0].target.getBoundingClientRect().height));collapseObserver.observe(document.querySelector('.landscape-controls'))");
        f.await("document.querySelector('.full-player-mobile-landscape').dataset.controlsVisible==='false'");SystemClock.sleep(350);f.capture("landscape-idle-tags");
        assertEquals("静置后标签必须保持显示和可操作","true",f.js("(()=>{const e=document.querySelector('.full-player-mobile-landscape .meta-actions-row'),r=e.getBoundingClientRect();return r.width>0&&r.height>0&&!e.closest('[inert]')})()"));
        assertEquals("控件应经过中间高度平滑收起","true",f.js("collapseHeights.some(h=>h>1&&h<80)"));
        assertTrue("横屏封面需保持明显圆角",Double.parseDouble(f.js("parseFloat(getComputedStyle(document.querySelector('.landscape-cover')).borderTopLeftRadius)"))>=24);
        tap(f,"[...document.querySelectorAll('.full-player-mobile-landscape .meta-item')].find(e=>/YRC|LRC|TTML|QRC/.test(e.textContent))");f.await("!!document.querySelector('.n-base-select-menu')");SystemClock.sleep(2300);
        assertEquals("菜单打开时不应被静置隐藏", "true",f.js("document.querySelector('.full-player-mobile-landscape').dataset.controlsVisible==='true'"));
        tap(f,option("官方优先"));f.await("fixtureSetting.lyricPriority==='official'");
      } finally {
        f.js("window.collapseObserver?.disconnect();p7Restore()");f.await("window.p7Restored===true");String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
  @Test public void firstTouchOpensAndAppliesPlayerTagsAndQuickActions() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();String size=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;shell(f,"wm size "+Math.round(360*density)+"x"+Math.round(800*density)+" -d "+display);
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));f.await("Math.abs(innerWidth-360)<=1");
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.autoHidePlayerMeta=false;fixtureSetting.lyricPriority='auto';window.controlReady=true})");f.await("window.controlReady===true&&!!document.querySelector('.info-page .qa-trigger')");SystemClock.sleep(700);
      try {
        tap(f,"document.querySelector('.info-page .qa-trigger')");
        f.await("!!document.querySelector('.quick-actions-panel')");
        boolean original=Boolean.parseBoolean(f.js("fixtureSetting.showWordLyrics"));
        tap(f,"[...document.querySelectorAll('.quick-actions-panel .qa-item')].find(e=>e.textContent.includes('逐词效果')).querySelector('.n-switch')");
        assertEquals("快捷菜单选择须立即更新设置",!original,Boolean.parseBoolean(f.js("fixtureSetting.showWordLyrics")));
        tap(f,"document.querySelector('.mobile-player-bottom-controls .time')");
        f.await("!document.querySelector('.quick-actions-panel')");
        tap(f,"[...document.querySelectorAll('.info-page .meta-item')].find(e=>/YRC|LRC|TTML|QRC/.test(e.textContent))");
        f.await("!!document.querySelector('.n-base-select-menu')");
        assertEquals("歌词格式菜单不重复提供逐词开关","true",f.js("!document.querySelector('.n-base-select-menu [aria-label=\"逐词显示\"]')"));
        tap(f,option("官方优先"));f.await("fixtureSetting.lyricPriority==='official'");
        f.js("window.controlPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;window.controlOriginal={quality:controlPlayer.switchQuality,source:controlPlayer.switchAudioSource};window.controlCalls=[];controlPlayer.switchQuality=async()=>{controlCalls.push('quality');fixtureStatus.songQuality='HQ'};controlPlayer.switchAudioSource=async(source)=>{controlCalls.push(source);fixtureStatus.audioSource=source};fixtureSetting.songLevel='standard';p7Stores.music.playSong={...p7Songs[0],path:undefined};fixtureStatus.audioSource='official';fixtureStatus.songQuality='LQ';fixtureStatus.availableQualities=[{level:'standard',name:'标准',size:1024},{level:'higher',name:'较高',size:2048}]");
        try {f.await("!!document.querySelector('.info-page button[aria-busy] .meta-item')");}
        catch(AssertionError error) {throw new AssertionError("音质入口状态="+f.js("JSON.stringify({song:{id:p7Stores.music.playSong.id,type:p7Stores.music.playSong.type,path:p7Stores.music.playSong.path,pc:p7Stores.music.playSong.pc},unlocked:fixtureStatus.isUnlocked,meta:fixtureSetting.showPlayMeta,quality:fixtureSetting.showPlayerQuality,width:innerWidth,height:innerHeight,html:document.querySelector('.info-page .meta-actions-row')?.innerHTML})"),error);}
        tap(f,"[...document.querySelectorAll('.info-page .meta-item.clickable')].find(e=>e.closest('button[aria-busy]'))");f.await("[...document.querySelectorAll('.n-base-select-option')].some(e=>e.textContent.includes('较高'))");tap(f,option("较高"));f.await("fixtureSetting.songLevel==='higher'&&controlCalls.includes('quality')&&fixtureStatus.songQuality==='HQ'");
        tap(f,"[...document.querySelectorAll('.info-page .meta-item')].at(-1)");f.await("[...document.querySelectorAll('.n-base-select-option')].some(e=>e.textContent.includes('Kuwo'))");tap(f,option("Kuwo"));f.await("controlCalls.includes('kuwo')&&fixtureStatus.audioSource==='kuwo'");
        f.capture("control-tags-and-more");
        shell(f,"wm size "+Math.round(320*density)+"x"+Math.round(640*density)+" -d "+display);
        f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(200));
        f.await("Math.abs(innerWidth-320)<=1&&!!document.querySelector('.info-page .meta-settings-trigger')");
        f.js("document.querySelector('.info-page .info-group').scrollTop=100000");SystemClock.sleep(400);
        tap(f,"[...document.querySelectorAll('.info-page .meta-item')].find(e=>/YRC|LRC|TTML|QRC/.test(e.textContent))");
        f.await("!!document.querySelector('.n-base-select-menu')");tap(f,option("自动"));f.await("fixtureSetting.lyricPriority==='auto'");
        f.capture("control-merged-tags-320-200");
      } finally {
        f.js("if(window.controlOriginal){controlPlayer.switchQuality=controlOriginal.quality;controlPlayer.switchAudioSource=controlOriginal.source}p7Restore()");f.await("window.p7Restored===true");
        String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
  @Test public void availableSpaceShowsQueueBeforeCollapsingBothActions() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();String size=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;
      f.inst.runOnMainSync(()->{f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);f.web.getSettings().setTextZoom(100);});
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.autoHidePlayerMeta=false;fixtureSetting.showPlayMeta=true;fixtureSetting.showPlayerQuality=true;fixtureStatus.personalFmMode=false;p7Stores.music.playSong={...p7Songs[0],path:undefined};fixtureStatus.songQuality='LQ';fixtureStatus.audioSource='official';window.actionSpaceReady=true})");
      try {
        f.await("window.actionSpaceReady===true");
        for(int width:new int[]{360,412}) {
          shell(f,"wm size "+Math.round(width*density)+"x"+Math.round(800*density)+" -d "+display);
          f.await("Math.abs(innerWidth-"+width+")<=1");SystemClock.sleep(700);
          f.capture("player-actions-space-"+width);
          assertEquals("360dp有一个按钮空位时应优先保留队列，不应同时收纳两个操作","true",f.js("!!document.querySelector('.info-page .info-actions .n-badge .action-btn')"));
          if(width==412)assertEquals("412dp应显示桌面歌词与队列","true",f.js("!!document.querySelector('.info-page .portrait-desktop-lyric')"));
          assertEquals("同排所有按钮保持48dp且不相交、不越界","true",f.js("(()=>{const es=[...document.querySelectorAll('.info-page .info-actions .action-btn,.info-page .info-actions .qa-trigger')].map(e=>e.getBoundingClientRect()).filter(r=>r.width>0);return es.every((r,i)=>r.width>=48&&r.height>=48&&r.left>=0&&r.right<=innerWidth&&es.every((q,j)=>j===i||r.right<=q.left+.5||q.right<=r.left+.5))})()"));
        }
        tap(f,"document.querySelector('.info-page .info-actions .n-badge .action-btn')");f.await("fixtureStatus.playListShow===true");
        f.js("fixtureStatus.playListShow=false;fixtureStatus.showDesktopLyric=true");
        tap(f,"document.querySelector('.info-page .qa-trigger')");
        f.await("!!document.querySelector('.quick-actions-panel')");
        assertEquals("更多菜单不再提供解锁桌面歌词","true",f.js("!document.querySelector('.quick-actions-panel').textContent.includes('解锁桌面歌词')"));
      } finally {
        f.js("window.$modal?.destroyAll();fixtureStatus.playListShow=false;p7Restore()");f.await("window.p7Restored===true");
        String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
}

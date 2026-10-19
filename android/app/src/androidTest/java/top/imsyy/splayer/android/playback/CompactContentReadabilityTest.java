package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real settings and authenticated empty-list rendering; no account or setting writes. */
@RunWith(AndroidJUnit4.class)
public class CompactContentReadabilityTest {
  private static String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);
        java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())) {
      return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();
    }
  }
  private static JSONObject json(DeviceWebFixture f,String expression) throws Exception {
    return new JSONObject((String)new JSONTokener(f.js("JSON.stringify("+expression+")")).nextValue());
  }
  private static final class Compact implements AutoCloseable {
    final DeviceWebFixture f=new DeviceWebFixture();
    final int display=f.activity.getDisplay().getDisplayId();
    final String original=shell(f,"wm size -d "+display);
    final int orientation=f.activity.getRequestedOrientation();
    Compact() throws Exception {
      try {
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      f.await("innerHeight>innerWidth");
      float density=f.activity.getResources().getDisplayMetrics().density;
      shell(f,"wm size "+Math.round(320*density)+"x"+Math.round(640*density)+" -d "+display);
      f.await("Math.abs(innerWidth-320)<=1");
      f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(200));
      } catch(Exception|AssertionError error) {
        try {close();} catch(Exception|AssertionError restore) {error.addSuppressed(restore);}
        throw error;
      }
    }
    public void close() throws Exception {
      try {
        f.js("window.$modal?.destroyAll()");
        String previous=original.contains("Override size:")?original.substring(original.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);
      } finally { f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));f.close(); }
    }
  }
  private void openSettings(DeviceWebFixture f,String group) throws Exception {
    f.js("fixtureRouter.push('/');document.querySelector('.nav button[title=\"设置\"]').click()");
    f.await("!!document.querySelector('.n-dropdown-option-body')");
    f.js("[...document.querySelectorAll('.n-dropdown-option-body')].find(e=>e.textContent.includes('全局设置')).click()");
    f.await("!!document.querySelector('.setting')");
    f.js("(()=>{const n=document.querySelector('.setting .set-left');if(n&&getComputedStyle(n).display==='none')document.querySelector('.setting .mobile-title button').click();[...document.querySelectorAll('.setting .n-menu-item-content')].find(e=>e.textContent.includes("+JSONObject.quote(group)+")).click()})()");
  }
  private void openOverlay(DeviceWebFixture f,String key) throws Exception {
    f.await("(()=>{const e=document.querySelector('#setting-"+key+" button');return !!e&&e.getBoundingClientRect().width>0})()");
    SystemClock.sleep(300);
    int before=Integer.parseInt(f.js("document.querySelectorAll('.n-modal').length"));
    f.js("(()=>{const b=document.querySelector('#setting-"+key+" button');b.scrollIntoView({block:'center'});b.click()})()");
    f.await("document.querySelectorAll('.n-modal').length>"+before);
    SystemClock.sleep(700);
  }
  private void assertReadableLabel(DeviceWebFixture f,String root,String label,int maxLines) throws Exception {
    JSONObject geometry=json(f,"(()=>{const e=[...document.querySelectorAll('"+root+" .name')].find(e=>e.textContent.trim()==="+JSONObject.quote(label)+");const r=document.createRange();r.selectNodeContents(e);return {lines:r.getClientRects().length,rect:e.getBoundingClientRect().toJSON()}})()");
    assertTrue(label+"不能被操作控件挤成逐字竖排: "+geometry,geometry.getInt("lines")<=maxLines);
  }
  @Test public void dailyRecommendationHeaderReflowsAndMoreRemainsReachable() throws Exception {
    verifyDailyRecommendation(200);
  }
  @Test public void dailyRecommendationDefaultTextKeepsMenuReachable() throws Exception {
    verifyDailyRecommendation(100);
  }
  private void verifyDailyRecommendation(int textZoom) throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(textZoom));
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureStatus.showFullPlayer=false;fixtureSetting.useOnlineService=true;fixtureSetting.showSongOperations=true;fixtureSetting.hiddenCovers.home=false;p7Stores.music.dailySongsData={timestamp:new Date().setHours(12,0,0,0),list:[...p7Songs.map(s=>({...s,name:p7Long,path:undefined})),{...p7Saved.music.playSong,name:p7Long}]};return fixtureRouter.push('/')}).then(()=>window.dailyReady=true)");
      try {
        f.await("window.dailyReady===true&&!!document.querySelector('.home-rec-card[aria-label=\"每日推荐\"]')");
        f.js("document.querySelector('.home-rec-card[aria-label=\"每日推荐\"]').scrollIntoView({block:'center'})");
        f.tapVisible("document.querySelector('.home-rec-card[aria-label=\"每日推荐\"]')");
        f.await("!!document.querySelector('.daily-songs .song-list')");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity)");
        f.js("document.querySelector('.daily-songs').scrollIntoView({block:'start'})");
        SystemClock.sleep(200);
        f.capture("daily-recommendation-compact-header-"+textZoom);
        JSONObject g=json(f,"(()=>{const h=document.querySelector('.daily-songs .title'),n=h.querySelector('.name'),t=h.querySelector('.tip'),m=h.querySelector('.menu'),list=document.querySelector('.daily-songs .song-list');return {header:h.getBoundingClientRect().toJSON(),name:n.getBoundingClientRect().toJSON(),tip:t.getBoundingClientRect().toJSON(),menu:m.getBoundingClientRect().toJSON(),list:list.getBoundingClientRect().toJSON(),width:innerWidth,scrollWidth:document.documentElement.scrollWidth}})()");
        JSONObject h=g.getJSONObject("header"),n=g.getJSONObject("name"),m=g.getJSONObject("menu"),tip=g.getJSONObject("tip");
        JSONObject columns=json(f,"(()=>{const h=document.querySelector('.daily-songs .list-header'),e=h.querySelector('.actions'),range=document.createRange();range.selectNodeContents(e);return {lines:range.getClientRects().length,text:range.getBoundingClientRect().toJSON(),header:h.getBoundingClientRect().toJSON()}})()");
        assertEquals("操作列表头应保持横排: "+columns,1,columns.getInt("lines"));
        assertTrue("放大后的表头文字不能溢出标题行: "+columns,columns.getJSONObject("text").getDouble("top")>=columns.getJSONObject("header").getDouble("top")-1&&columns.getJSONObject("text").getDouble("bottom")<=columns.getJSONObject("header").getDouble("bottom")+1);
        assertTrue("标题不能因固定高度越出容器: "+g,n.getDouble("top")>=h.getDouble("top")-1&&m.getDouble("bottom")<=h.getDouble("bottom")+1);
        assertTrue("文字与菜单不能相交: "+g,n.getDouble("bottom")<=tip.getDouble("top")+1&&tip.getDouble("bottom")<=m.getDouble("top")+1);
        assertTrue("列表必须排在菜单下方: "+g,g.getJSONObject("list").getDouble("top")>=m.getDouble("bottom")+8);
        assertTrue("推荐标题不能横向出界: "+g,n.getDouble("left")>=-1&&n.getDouble("right")<=g.getDouble("width")+1&&g.getDouble("scrollWidth")<=g.getDouble("width")+1);
        f.js("document.querySelector('.daily-songs .title button.more').scrollIntoView({block:'center'})");
        f.tapVisible("document.querySelector('.daily-songs .title button.more')");
        f.await("[...document.querySelectorAll('.n-dropdown-option-body')].some(e=>e.textContent.includes('批量操作'))");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity)");
        assertEquals("更多按钮保持48dp触控目标","true",f.js("(()=>{const r=document.querySelector('.daily-songs .title button.more').getBoundingClientRect();return r.width>=48&&r.height>=48})()"));
        f.capture("daily-recommendation-more-menu-"+textZoom);
        assertEquals("菜单文字必须位于自身选项内且触控高度至少48dp","true",f.js("[...document.querySelectorAll('.n-dropdown-option-body')].filter(e=>/更新日推|批量操作/.test(e.textContent)).every(e=>{const r=e.getBoundingClientRect(),label=e.querySelector('.n-dropdown-option-body__label'),range=document.createRange();range.selectNodeContents(label);const t=range.getBoundingClientRect();return r.height>=48&&t.top>=r.top-1&&t.bottom<=r.bottom+1})"));
        f.tapVisible("[...document.querySelectorAll('.n-dropdown-option-body')].find(e=>e.textContent.includes('批量操作'))");
        f.await("!!document.querySelector('.batch-list')");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity)");
        f.tapVisible("document.querySelector('.batch-list').closest('.n-modal').querySelector('.n-base-close')");
        f.await("!document.querySelector('.batch-list')");
        // 定位按钮仅在当前曲属于列表时存在；账号初始化可能替换先前的合成日推。
        f.js("p7Stores.music.playSong={...p7Stores.music.dailySongsData.list[0]};document.querySelector('.daily-songs .song-list').scrollIntoView({block:'start'})");
        f.await("p7Stores.music.dailySongsData.list.some(s=>s.id===p7Stores.music.playSong.id)");
        try {f.await("!!document.querySelector('.list-menu:not(.outside-list) .n-float-button:not(.hidden)')");}
        catch(AssertionError error) {throw new AssertionError("浮动工具几何="+f.js("JSON.stringify({lists:[...document.querySelectorAll('.song-list')].map(e=>({rect:e.getBoundingClientRect().toJSON(),scroll:e.scrollTop})),tools:[...document.querySelectorAll('.list-menu')].map(e=>({cls:e.className,rect:e.getBoundingClientRect().toJSON(),buttons:[...e.querySelectorAll('button')].map(b=>({cls:b.className,rect:b.getBoundingClientRect().toJSON()}))}))})"),error);}
        f.capture("daily-recommendation-list-columns-"+textZoom);
        JSONObject rows=json(f,"(()=>{const rows=[...document.querySelectorAll('.daily-songs .song-node .song-card')].slice(0,3);return {rows:rows.map(e=>{const range=document.createRange();range.selectNodeContents(e.querySelector('.title .info .artists'));const n=e.querySelector('.title .info .name').getBoundingClientRect(),a=range.getBoundingClientRect();return {card:e.getBoundingClientRect().toJSON(),text:{top:Math.min(n.top,a.top),bottom:Math.max(n.bottom,a.bottom)},title:n.toJSON(),artist:a.toJSON()}})}})()");
        for(int i=0;i<rows.getJSONArray("rows").length();i++) {
          JSONObject row=rows.getJSONArray("rows").getJSONObject(i),card=row.getJSONObject("card"),text=row.getJSONObject("text");
          assertTrue("大字号标题和歌手不能越出当前歌曲行: "+rows,text.getDouble("top")>=card.getDouble("top")-1&&text.getDouble("bottom")<=card.getDouble("bottom")+1);
          if(i>0)assertTrue("相邻虚拟歌曲行不得交叠: "+rows,card.getDouble("top")>=rows.getJSONArray("rows").getJSONObject(i-1).getJSONObject("card").getDouble("bottom")-1);
        }

        assertEquals("进入列表区域后工具恢复且保持48dp","true",f.js("(()=>{const e=document.querySelector('.list-menu:not(.outside-list) .n-float-button:not(.hidden)'),r=e.getBoundingClientRect();return getComputedStyle(e).visibility==='visible'&&r.width>=48&&r.height>=48})()"));
        f.tapVisible("document.querySelector('.list-menu:not(.outside-list) .n-float-button:not(.hidden)')");
      } finally {f.js("window.$modal?.destroyAll();p7Restore()");f.await("window.p7Restored===true");}
    }
  }
  @Test public void settingsOverlaysKeepLabelsReadableAndControlsReachable() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      openSettings(f,"外观");openOverlay(f,"fontConfig");
      f.capture("readability-font-before-or-after");
      assertReadableLabel(f,".font-manager","全局字体",2);
      f.js("document.querySelector('.font-manager input').scrollIntoView({block:'center'})");
      SystemClock.sleep(300);
      // Naive Input 的触控区域由外层容器拥有，文字编辑行本身不代表整个触控目标。
      JSONObject input=json(f,"(()=>{const e=document.querySelector('.font-manager .n-input'),r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.bottom-2);return {rect:r.toJSON(),hit:e.contains(hit)}})()");
      JSONObject ir=input.getJSONObject("rect");
      assertTrue("字体输入触控区域必须至少48px且可命中: "+input,ir.getDouble("width")>=48&&ir.getDouble("height")>=48&&input.getBoolean("hit"));
      float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
      float x=(float)(ir.getDouble("x")+ir.getDouble("width")/2)*scale;
      float y=(float)(ir.getDouble("bottom")-2)*scale;
      long down=SystemClock.uptimeMillis();
      for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();SystemClock.sleep(50);
      }
      f.await("document.activeElement===document.querySelector('.font-manager input')");
      f.inst.runOnMainSync(()->((android.view.inputmethod.InputMethodManager)f.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.web.getWindowToken(),0));
      f.js("document.activeElement?.blur()");SystemClock.sleep(300);
      f.js("[...document.querySelectorAll('.n-modal')].at(-1).querySelector('.n-base-close').click()");
      f.await("!document.querySelector('.font-manager')");
      f.js("document.querySelector('.setting .mobile-title button').click();[...document.querySelectorAll('.setting .n-menu-item-content')].find(e=>e.textContent.includes('歌词')).click()");
      SystemClock.sleep(600);
      openOverlay(f,"configExcludeLyric");f.capture("readability-exclude-before-or-after");
      assertReadableLabel(f,".exclude-lyrics-modal","TTML 歌词排除",3);
      f.js("[...document.querySelectorAll('.exclude-lyrics-modal button')].find(e=>e.textContent.trim()==='取消').scrollIntoView({block:'center'})");
      SystemClock.sleep(300);
      assertEquals("取消操作必须滚动可达且保持触控高度","true",f.js("(()=>{const e=[...document.querySelectorAll('.exclude-lyrics-modal button')].find(e=>e.textContent.trim()==='取消'),r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return r.height>=48&&(hit===e||e.contains(hit))})()"));
    }
  }
  @Test public void cloudEmptyIconDoesNotIntersectMultilineDescription() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      f.js("fixtureRouter.push('/cloud')");
      f.await("!!document.querySelector('.cloud .song-list.empty .n-empty__description')");
      f.await("!!document.querySelector('.cloud .song-list.empty .n-empty__icon svg')&&!document.querySelector('.cloud .n-skeleton,.cloud .n-button--loading')");
      SystemClock.sleep(700);f.capture("readability-cloud-before-or-after");
      JSONObject r=json(f,"(()=>{const icon=document.querySelector('.cloud .song-list.empty .n-empty__icon svg'),text=document.querySelector('.cloud .song-list.empty .n-empty__description'),range=document.createRange();range.selectNodeContents(text);return {icon:icon.getBoundingClientRect().toJSON(),text:range.getBoundingClientRect().toJSON()}})()");
      assertTrue("空态图标与正文不能相交: "+r,r.getJSONObject("icon").getDouble("bottom")<=r.getJSONObject("text").getDouble("top"));
    }
  }
  @Test public void emptyLocalCategoriesStopLoadingAfterScanCompletes() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      assertEquals("仅使用未配置目录的隔离测试库，不清空已有歌曲", "true",f.js("fixtureSetting.androidLocalMusicDirectories.length===0"));
      f.js("window.emptyLibrarySaved={route:fixtureRouter.currentRoute.value.fullPath,dirs:JSON.parse(JSON.stringify(fixtureSetting.androidLocalMusicDirectories))};window.emptyLibraryNative=Capacitor.nativePromise;window.emptyScanPending=false;Capacitor.nativePromise=function(plugin,method,options){if(plugin==='AndroidDownload'&&method==='scanLocalMusic'){window.emptyScanPending=true;return new Promise(resolve=>window.finishEmptyScan=()=>{window.emptyScanPending=false;resolve({songs:[]})})}return emptyLibraryNative.apply(this,arguments)};fixtureSetting.androidLocalMusicDirectories=[{uri:'content://next-stage.invalid/empty-regression',name:'隔离空库'}]");
      try {
        for(String category:new String[]{"artists","albums","folders"}) {
          f.js("fixtureRouter.push('/local/"+category+"')");
          f.await("!!document.querySelector('.local-"+category+"')");
          if(!"true".equals(f.js("window.emptyScanPending")))f.tapVisible("document.querySelector('.local .menu .left .more')");
          f.await("window.emptyScanPending&&!!document.querySelector('.local-"+category+" .song-list.loading .n-skeleton')");
          f.js("finishEmptyScan()");
          f.await("!document.querySelector('.local .menu .n-button--loading')");
          f.capture("empty-local-"+category);
          assertEquals("扫描已完成的空分类应显示空态，而非无限加载："+category,"true",f.js("!!document.querySelector('.local-"+category+" .song-list.empty')&&!document.querySelector('.local-"+category+" .n-skeleton')"));
        }
      } finally {
        f.js("window.finishEmptyScan?.();fixtureSetting.androidLocalMusicDirectories=emptyLibrarySaved.dirs;fixtureRouter.push(emptyLibrarySaved.route)");
        SystemClock.sleep(1200);
        f.js("Capacitor.nativePromise=emptyLibraryNative");
      }
    }
  }
  @Test public void emptyStreamingCategoriesShowEmptyInsteadOfSkeletons() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      String route=f.js("fixtureRouter.currentRoute.value.fullPath");
      try {
        for(String category:new String[]{"artists","albums"}) {
          f.js("fixtureRouter.push('/streaming/"+category+"')");
          f.await("!!document.querySelector('.streaming-"+category+"')");
          f.await("!document.querySelector('.streaming .menu .n-button--loading')");
          SystemClock.sleep(400);f.capture("empty-streaming-"+category);
          assertEquals("未连接且无请求的空分类应显示空态："+category,"true",f.js("!!document.querySelector('.streaming-"+category+" .song-list.empty')&&!document.querySelector('.streaming-"+category+" .n-skeleton')"));
        }
      } finally {f.js("fixtureRouter.push("+route+")");}
    }
  }
}

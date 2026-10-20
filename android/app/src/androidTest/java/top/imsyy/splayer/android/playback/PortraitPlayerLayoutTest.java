package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PortraitPlayerLayoutTest {
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())) {
      return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();
    }
  }
  private JSONObject geometry(DeviceWebFixture f) throws Exception {
    return new JSONObject((String)new JSONTokener(f.js("JSON.stringify((()=>{const root=document.querySelector('.full-player-mobile'),rect=s=>root.querySelector(s).getBoundingClientRect().toJSON();return {cover:rect('.player-cover'),info:rect('.info-group'),footer:rect('.mobile-player-bottom-controls'),page:rect('.info-page')}})())")).nextValue());
  }
  private void openPlayer(DeviceWebFixture f) throws Exception {
    f.await("(()=>{const e=document.querySelector('.main-player .play-data .cover');if(!e)return false;const r=e.getBoundingClientRect();return r.width>0&&e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})()");
    JSONObject rect=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelector('.main-player .play-data .cover').getBoundingClientRect().toJSON())")).nextValue());
    float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
    float x=(float)(rect.getDouble("x")+rect.getDouble("width")/2)*scale,y=(float)(rect.getDouble("y")+rect.getDouble("height")/2)*scale;
    long down=SystemClock.uptimeMillis();
    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
      MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();SystemClock.sleep(80);
    }
  }
  private void swipe(DeviceWebFixture f) throws Exception {
    JSONObject rect=geometry(f).getJSONObject("info");
    float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
    float x=(float)(rect.getDouble("x")+rect.getDouble("width")*.5)*scale;
    float y=(float)(rect.getDouble("y")+Math.min(30,rect.getDouble("height")*.25))*scale;
    long down=SystemClock.uptimeMillis();
    for(int i=0;i<=13;i++) {
      int action=i==0?MotionEvent.ACTION_DOWN:i==13?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
      MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y-Math.min(i,12)*3*scale,0);
      event.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();SystemClock.sleep(25);
    }
  }
  private void verifyCompactMeta(DeviceWebFixture f) throws Exception {
    String selector=".info-page .meta-settings-trigger,.info-page .play-meta";
    // Short windows allow vertical browsing of long metadata, never horizontal tag scrolling.
    for(int attempt=0;attempt<8;attempt++) {
      if("true".equals(f.js("(()=>{const e=document.querySelector('"+selector+"'),r=e.getBoundingClientRect();return e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})()")))break;
      JSONObject r=geometry(f).getJSONObject("info");
      float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
      float x=(float)(r.getDouble("x")+r.getDouble("width")*.4)*scale;
      float start=(float)(r.getDouble("bottom")-12)*scale, distance=(float)Math.min(150,r.getDouble("height")*.7)*scale;
      long down=SystemClock.uptimeMillis();
      for(int i=0;i<=13;i++) {
        int action=i==0?MotionEvent.ACTION_DOWN:i==13?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,start-distance*Math.min(i,12)/12,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(25);
      }
      SystemClock.sleep(200);
    }
    assertEquals("标签不能缩窄、变形、横滑或被播放控件遮挡","true",f.js("(()=>{const e=document.querySelector('"+selector+"'),r=e.getBoundingClientRect();return e.scrollWidth<=e.clientWidth+1&&e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))&&[...e.querySelectorAll('.meta-item')].every(t=>t.scrollWidth<=t.clientWidth+1)})()"));
    if("true".equals(f.js("!!document.querySelector('.info-page .meta-settings-trigger')"))) {
      tapMetaSettings(f);
      f.await("!!document.querySelector('.merged-meta-panel')");
      f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&a.effect?.target?.closest?.('.n-popover'))");
      assertEquals("合并入口应完整展示三个标签，保留点击高度且无横向裁切 "+f.js("JSON.stringify([...document.querySelectorAll('.merged-meta-panel .meta-item')].map(e=>({text:e.textContent,rect:e.getBoundingClientRect().toJSON(),target:e.closest('.meta-target')?.getBoundingClientRect().toJSON(),scroll:e.scrollWidth,client:e.clientWidth})))"),"true",f.js("(()=>{const items=[...document.querySelectorAll('.merged-meta-panel .meta-item')];return items.length===3&&items.every(e=>{const r=e.getBoundingClientRect();return r.height>0&&r.left>=0&&r.right<=innerWidth&&e.scrollWidth<=e.clientWidth+1&&(e.closest('.meta-target')?.getBoundingClientRect().height??48)>=48})})()"));
      tapMetaSettings(f);f.await("!document.querySelector('.merged-meta-panel')");
    }
  }
  private void tapMetaSettings(DeviceWebFixture f) throws Exception {
    JSONObject r=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelector('.info-page .meta-settings-trigger').getBoundingClientRect().toJSON())")).nextValue());
    float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
    float x=(float)(r.getDouble("x")+r.getDouble("width")/2)*scale,y=(float)(r.getDouble("y")+r.getDouble("height")/2)*scale;
    long down=SystemClock.uptimeMillis();
    for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
      MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(80);
    }
  }
  @Test public void metadataSwipeMovesTheWholePageWithoutLocalDrift() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int orientation=f.activity.getRequestedOrientation();
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.autoHidePlayerMeta=false;fixtureStatus.showFullPlayer=false;window.dragReady=true})");
      f.await("window.dragReady===true");
      try {
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        f.js("p7Stores.music.playSong={...p7Songs[0],name:p7Short};p7SetLyrics(true);fixtureSetting.hiddenCovers.player=false");
        openPlayer(f);
        f.await("!!document.querySelector('.full-player-mobile .info-group')");
        SystemClock.sleep(800);
        for(String target:new String[]{"title","icon"}) for(int direction:new int[]{-1,1}) {
          JSONObject beforeAll=geometry(f),before=beforeAll.getJSONObject("info");
          boolean boundary="true".equals(f.js("(()=>{const dots=[...document.querySelectorAll('.full-player-mobile .pagination .dot')],i=dots.findIndex(e=>e.getAttribute('aria-current')==='page');return "+direction+">0?i===0:i===dots.length-1})()"));
          JSONObject touch=target.equals("title")?before:new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelector('.info-page .info-actions .action-btn svg').getBoundingClientRect().toJSON())")).nextValue());
          float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
          float x=(float)(touch.getDouble("x")+touch.getDouble("width")*.5)*scale;
          float y=(float)(touch.getDouble("y")+(target.equals("title")?20:touch.getDouble("height")*.5))*scale;
          long down=SystemClock.uptimeMillis();
          try {
            for(int i=0;i<=8;i++) {
              MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,x+direction*i*4*scale,y,0);
              e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(40);
            }
            f.capture("metadata-held-horizontal-"+target+"-"+direction);
            JSONObject heldAll=geometry(f),held=heldAll.getJSONObject("info");
            assertEquals("信息区必须随封面整页移动，不能独立侧移: "+target,before.getDouble("left")-beforeAll.getJSONObject("cover").getDouble("left"),held.getDouble("left")-heldAll.getJSONObject("cover").getDouble("left"),1.0);
            assertEquals("整页跟手且保留原有页边界阻尼: "+target,direction*(boundary?9.6:32),held.getDouble("left")-before.getDouble("left"),1.0);
            assertEquals("信息区自身不能水平滚动","0",f.js("document.querySelector('.info-group').scrollLeft"));
          } finally {
            MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,x+direction*32*scale,y,0);
            e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(350);
          }
        }
        JSONObject info=geometry(f).getJSONObject("info");
        float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
        float x=(float)(info.getDouble("x")+info.getDouble("width")*.7)*scale,y=(float)(info.getDouble("y")+20)*scale;
        long down=SystemClock.uptimeMillis();
        for(int i=0;i<=16;i++) {
          MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x-i*10*scale,y,0);
          event.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();SystemClock.sleep(20);
        }
        f.await("document.querySelector('.full-player-mobile').classList.contains('lyric-active')");
        assertEquals("从信息区横滑必须切换到歌词页","true",f.js("!!document.querySelector('.lyric-page .player-lyric')"));
        // Exercise the real idle state, then wake controls with touch before using them.
        f.await("document.querySelector('.full-player-mobile').classList.contains('controls-hidden')");
        f.tapVisible("document.querySelector('.lyric-page')", 0.02, 0.5);
        f.tapVisible("document.querySelectorAll('.mobile-player-bottom-controls .pagination .dot')[0]");
        f.await("!document.querySelector('.full-player-mobile').classList.contains('lyric-active')");
        f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&document.querySelector('.full-player')?.contains(a.effect?.target))");
        assertEquals("下拉捕获区必须覆盖封面并止于信息区之前，不能保留进场动画坐标", "true", f.js("(()=>{const h=document.querySelector('.full-player-mobile .drag-handle').getBoundingClientRect(),c=document.querySelector('.cover-section').getBoundingClientRect(),i=document.querySelector('.info-group').getBoundingClientRect();return h.bottom>=c.bottom-1&&h.bottom<=i.top+1})()"));
        f.tapVisible("document.querySelector('.info-page .qa-trigger')");
        f.await("!!document.querySelector('.quick-actions-panel')");
        f.tapVisible("document.querySelector('.info-page .qa-trigger')");
        f.await("!document.querySelector('.quick-actions-panel')");
        JSONObject cover=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelector('.cover-section').getBoundingClientRect().toJSON())")).nextValue());
        float closeX=(float)(cover.getDouble("x")+cover.getDouble("width")*.5)*scale;
        float closeY=(float)(cover.getDouble("y")+Math.min(40,cover.getDouble("height")*.25))*scale;
        long closeDown=SystemClock.uptimeMillis();
        for(int i=0;i<=16;i++) {
          MotionEvent e=MotionEvent.obtain(closeDown,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,closeX,closeY+i*10*scale,0);
          e.setSource(InputDevice.SOURCE_TOUCHSCREEN);f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));e.recycle();SystemClock.sleep(25);
        }
        f.await("!document.querySelector('.full-player')");
      } finally {
        f.js("p7Restore()");f.await("window.p7Restored===true");
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
  @Test public void portraitLayoutIsStableAndMetadataStaysAboveControls() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();
      String original=shell(f,"wm size -d "+display);
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(stream.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.autoHidePlayerMeta=false;fixtureStatus.personalFmMode=false;fixtureStatus.showFullPlayer=false;window.portraitReady=true})");
      f.await("window.portraitReady===true");
      try {
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        for(int[] size:new int[][]{{390,800},{360,800},{320,640},{412,1024}}) for(int zoom:new int[]{100,200}) {
          f.js("fixtureStatus.showFullPlayer=false");SystemClock.sleep(450);
          float density=f.activity.getResources().getDisplayMetrics().density;
          shell(f,"wm size "+Math.round(size[0]*density)+"x"+Math.round(size[1]*density)+" -d "+display);
          f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(zoom));
          f.await("Math.abs(innerWidth-"+size[0]+")<=1");
          f.js("p7Stores.music.playSong={...p7Songs[0],name:"+(zoom==100?"p7Short":"p7Long")+"};fixtureSetting.hiddenCovers.player=false;"+(zoom==100?"p7Stores.music.setSongLyric({lrcData:[],yrcData:[]},true)":"p7SetLyrics(true)"));
          openPlayer(f);
          f.await("!!document.querySelector('.full-player-mobile .info-group')");
          f.await("!document.getAnimations().some(a=>a.playState==='running'&&a.effect?.getComputedTiming().iterations!==Infinity&&document.querySelector('.full-player')?.contains(a.effect?.target))");
          SystemClock.sleep(700);
          if(size[0]>=390&&zoom==100)assertEquals("空间足够时应保留桌面歌词和歌曲列表按钮","true",f.js("!!document.querySelector('.info-page .portrait-desktop-lyric')&&!!document.querySelector('.info-page .info-actions > .n-badge')"));
          assertEquals("分页几何="+f.js("JSON.stringify({size:[innerWidth,innerHeight],pagination:document.querySelector('.full-player-mobile .pagination').getBoundingClientRect().toJSON(),dots:[...document.querySelectorAll('.full-player-mobile .pagination .dot')].map(e=>{const r=e.getBoundingClientRect();return {rect:r.toJSON(),top:document.elementFromPoint(r.x+r.width/2,r.y+2)?.className,bottom:document.elementFromPoint(r.x+r.width/2,r.bottom-2)?.className}})})"),"true",f.js("(()=>{const e=document.querySelector('.full-player-mobile .pagination');return e.getBoundingClientRect().height<=29&&[...e.querySelectorAll('.dot')].every(b=>{const r=b.getBoundingClientRect();return r.width>=48&&r.height>=48&&b.contains(document.elementFromPoint(r.x+r.width/2,r.y+2))&&b.contains(document.elementFromPoint(r.x+r.width/2,r.bottom-2))})})()"));
          assertEquals("标签和图标须保持同一横排且图标不被挤压", "true",f.js("(()=>{const t=document.querySelector('.info-page .meta-settings-trigger,.info-page .play-meta').getBoundingClientRect(),a=document.querySelector('.info-page .info-actions').getBoundingClientRect();return Math.abs(t.y+t.height/2-a.y-a.height/2)<=2&&t.right<=a.left+1&&[...document.querySelectorAll('.info-page .info-actions .action-btn,.info-page .qa-trigger--mobile')].every(e=>e.getBoundingClientRect().width>=48)})()"));
          verifyCompactMeta(f);
          JSONObject before=geometry(f);String label=size[0]+"-"+size[1]+"-"+zoom;
          f.capture("portrait-before-"+label);swipe(f);SystemClock.sleep(600);
          JSONObject after=geometry(f);f.capture("portrait-after-"+label);
          java.nio.file.Files.write(new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression/portrait-geometry-"+label+".json").toPath(),new JSONObject().put("before",before).put("after",after).toString().getBytes(StandardCharsets.UTF_8));
          for(String name:new String[]{"cover","info","footer"}) for(String dimension:new String[]{"top","height"}) {
            assertEquals("首次进入与触摸后布局必须稳定 "+label+" "+name+"/"+dimension+" before="+before+" after="+after,before.getJSONObject(name).getDouble(dimension),after.getJSONObject(name).getDouble(dimension),2.0);
          }
          for(JSONObject state:new JSONObject[]{before,after}) {
            double gap=state.getJSONObject("footer").getDouble("top")-state.getJSONObject("info").getDouble("bottom");
            assertTrue("信息区应衔接播放控件上方，不能留大块空隙 "+label+" gap="+gap+" "+state,gap>=-1&&gap<=36);
            assertTrue("手机封面应有300dp上限 "+label,state.getJSONObject("cover").getDouble("width")<=301);
          }
          assertEquals("标签及动作不允许互相覆盖或越过页面边界 "+label,"true",f.js("(()=>{const root=document.querySelector('.full-player-mobile'),page=root.querySelector('.info-page').getBoundingClientRect(),a=[...root.querySelectorAll('.info-page .meta-settings-trigger,.info-page .play-meta,.info-page .info-actions .action-btn,.info-page .qa-trigger--mobile')].filter(e=>e.getBoundingClientRect().width>0).map(e=>e.getBoundingClientRect());return a.every((r,i)=>r.left>=page.left-1&&r.right<=page.right+1&&a.slice(i+1).every(s=>Math.min(r.right,s.right)-Math.max(r.left,s.left)<=1||Math.min(r.bottom,s.bottom)-Math.max(r.top,s.top)<=1))})()"));
        }
      } finally {
        f.js("p7Restore()");f.await("window.p7Restored===true");
        String previous=original.contains("Override size:")?original.substring(original.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
}

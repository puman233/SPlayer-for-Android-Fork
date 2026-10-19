package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.nio.charset.StandardCharsets;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompactHomeLayoutTest {
  private void verifyCoverCorners(DeviceWebFixture f,int zoom) throws Exception {
    f.capture("home-corners-"+zoom);
    java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");
    android.graphics.Bitmap actual=android.graphics.BitmapFactory.decodeFile(new java.io.File(dir,"home-corners-"+zoom+".png").getPath());
    try {
      org.json.JSONArray covers=new org.json.JSONArray((String)new JSONTokener(f.js("JSON.stringify([...document.querySelectorAll('.home-rec-card .art')].map(e=>e.getBoundingClientRect().toJSON()))")).nextValue());
      float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
      int[] origin=new int[2];f.inst.runOnMainSync(()->f.web.getLocationInWindow(origin));
      for(int i=0;i<covers.length();i++)for(boolean right:new boolean[]{false,true}) {
        JSONObject rect=covers.getJSONObject(i);
        int x=origin[0]+Math.round((float)(right?rect.getDouble("right")-.5:rect.getDouble("left")+.5)*scale);
        int y=origin[1]+Math.round((float)(rect.getDouble("bottom")-.5)*scale);
        int outsideX=origin[0]+Math.round((float)(rect.getDouble("left")-2)*scale);
        int a=actual.getPixel(x,y),b=actual.getPixel(outsideX,y);
        assertTrue("封面底角必须透出卡片底色，不能呈矩形 "+zoom+"/"+i+"/"+right,Math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))<=8&&Math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))<=8&&Math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))<=8);
      }
    } finally {actual.recycle();}
  }
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();}
  }
  @Test public void coveredHomeBackgroundsStopRenderingAndResumeAfterPlayerCloses() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId(),orientation=f.activity.getRequestedOrientation();
      String size=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      shell(f,"wm size "+Math.round(360*density)+"x"+Math.round(720*density)+" -d "+display);
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("window.homeGpuSaved=[];window.homeGpuDraws=0;for(const ctor of [window.WebGLRenderingContext,window.WebGL2RenderingContext]){if(!ctor)continue;for(const name of ['drawArrays','drawElements','drawArraysInstanced','drawElementsInstanced']){const p=ctor.prototype,original=p[name];if(typeof original!=='function')continue;homeGpuSaved.push({p,name,descriptor:Object.getOwnPropertyDescriptor(p,name)});p[name]=function(...args){if(this.canvas?.closest?.('.home-rec-card'))homeGpuDraws++;return original.apply(this,args)}}}");
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureSetting.useOnlineService=true;fixtureSetting.hiddenCovers.home=false;fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;p7Stores.music.dailySongsData={timestamp:new Date().setHours(12,0,0,0),list:p7Songs};return fixtureRouter.push('/')}).then(()=>window.homeGpuReady=true)");
      try {
        f.await("window.homeGpuReady&&!!document.querySelector('.home-rec-card .caption-material canvas')");
        f.js("document.querySelector('.home-rec-card').scrollIntoView({block:'center'});homeGpuDraws=0");
        f.await("homeGpuDraws>1");
        f.tapVisible("document.querySelector('.main-player.phone-floating .cover')");
        f.await("fixtureStatus.showFullPlayer===true&&!!document.querySelector('.full-player-mobile')");
        SystemClock.sleep(700);f.js("homeGpuDraws=0");SystemClock.sleep(750);
        assertEquals("全屏播放器覆盖首页后，首页动态背景不应继续消耗绘制帧",0,Integer.parseInt(f.js("homeGpuDraws")));
        f.tapVisible("document.querySelector('.full-player-mobile .top-bar .btn:last-child')");
        f.await("fixtureStatus.showFullPlayer===false");f.js("homeGpuDraws=0");
        f.await("homeGpuDraws>1");
      } finally {
        f.js("for(const s of homeGpuSaved.reverse()){if(s.descriptor)Object.defineProperty(s.p,s.name,s.descriptor);else delete s.p[s.name]}p7Restore()");f.await("window.p7Restored===true");
        String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
  @Test public void compactHomeKeepsLongTextAndTouchTargetsAccessible() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId();String size=shell(f,"wm size -d "+display);
      int originalOrientation=f.activity.getRequestedOrientation();
      f.js("window.homeSaved={setting:JSON.parse(JSON.stringify(fixtureSetting.$state)),music:JSON.parse(JSON.stringify(fixturePinia._s.get('music').$state)),data:JSON.parse(JSON.stringify({userLoginStatus:fixturePinia._s.get('data').userLoginStatus,loginType:fixturePinia._s.get('data').loginType,userData:fixturePinia._s.get('data').userData,likeSongsList:fixturePinia._s.get('data').likeSongsList}))};window.hm=fixturePinia._s.get('music');window.hd=fixturePinia._s.get('data');fixtureSetting.lastfm.enabled=false;hd.$patch({userLoginStatus:true,loginType:'uid',userData:{userId:99009900,name:'Synthetic home fixture'}});hm.personalFM={playIndex:0,list:[{id:99009901,name:'超长歌曲名ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789日本語',artists:[{id:1,name:'超长歌手名ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789日本語'}],album:{id:2,name:'超长专辑名ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789'},duration:180000,cover:'/images/song.jpg'}]};fixtureStatus.showFullPlayer=false;fixtureRouter.push('/')");
      try {
        f.js("fixtureSetting.useOnlineService=true;fixtureSetting.showHomeGreeting=true;fixtureSetting.hiddenCovers.home=false;window.homeArtwork=(colors)=>{const c=document.createElement('canvas');c.width=c.height=128;const x=c.getContext('2d'),g=x.createLinearGradient(0,0,128,128);colors.forEach((v,i)=>g.addColorStop(i/(colors.length-1),v));x.fillStyle=g;x.fillRect(0,0,128,128);return c.toDataURL()};hm.dailySongsData.list=[{...hm.personalFM.list[0],cover:homeArtwork(['#fff4d6','#c36e44','#365c68'])}];hd.likeSongsList.data=[{...hm.personalFM.list[0],cover:homeArtwork(['#d4e6f6','#507c98','#c7a8c3'])}]");
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        double previousGreetingSize=0;
        for(int width:new int[]{320,360,390,412}) {
          float density=f.activity.getResources().getDisplayMetrics().density;
          shell(f,"wm size "+Math.round(width*density)+"x"+Math.round(800*density)+" -d "+display);
          f.await("Math.abs(innerWidth-"+width+")<=1");
          f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(100));
          f.js("window.fmLongSong=hm.personalFM.list[0];hm.personalFM.list=[{...fmLongSong,name:'夏夜微风',artists:[{id:1,name:'歌手'}]}]");
          f.await("document.querySelector('.personal-fm .name')?.textContent.trim()==='夏夜微风'");
          SystemClock.sleep(700);
          assertEquals("常规短文本FM保持紧凑且不裁文字 "+width,"true",f.js("(()=>{const e=document.querySelector('.personal-fm'),c=e.querySelector('.cover').getBoundingClientRect(),m=e.querySelector('.fm-metadata'),b=e.querySelector('.menu').getBoundingClientRect();return e.getBoundingClientRect().height-c.height<=50&&m.scrollHeight<=m.clientHeight+1&&b.top>=m.getBoundingClientRect().bottom-1})()"));
          f.capture("next-home-fm-short-"+width);
          f.js("hm.personalFM.list=[fmLongSong]");
          f.await("document.querySelector('.personal-fm .name')?.textContent.includes('超长歌曲名')");
          for(int zoom:new int[]{100,200}) {
            f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(zoom));
            f.js("hm.personalFM.list=[{...fmLongSong,name:'夏夜微风',artists:[{id:1,name:'歌手'}],album:{id:2,name:'专辑'}}]");SystemClock.sleep(350);
            double shortFmHeight=Double.parseDouble(f.js("document.querySelector('.personal-fm').getBoundingClientRect().height"));
            f.js("hm.personalFM.list=[fmLongSong]");SystemClock.sleep(350);
            assertEquals("FM卡片不能因歌名、歌手或专辑文本变长而增高 "+width+"/"+zoom,shortFmHeight,Double.parseDouble(f.js("document.querySelector('.personal-fm').getBoundingClientRect().height")),1);
            f.await("document.querySelectorAll('.main-rec .song-data-card').length===2&&!!document.querySelector('.personal-fm')");
            assertEquals("窄屏推荐卡按设计只保留主标题", "true", f.js("!document.querySelector('.home-rec-card .desc')"));
            if(width==320&&zoom==100) {
              f.js("window.homeRandomReady=false;window.homeRandomPrevious=Math.random;window.homeAlternate=homeArtwork(['#d99720','#254530']);Math.random=()=>.999;hm.dailySongsData.list=[hm.dailySongsData.list[0],{...hm.dailySongsData.list[0],id:99009902,cover:homeAlternate}];hd.likeSongsList.data=[hd.likeSongsList.data[0],{...hd.likeSongsList.data[0],id:99009903,cover:homeAlternate}];queueMicrotask(()=>{Math.random=homeRandomPrevious;window.homeRandomReady=true})");
              f.await("window.homeRandomReady===true");SystemClock.sleep(300);
              assertEquals("两张卡片必须从整个列表随机取封面，不能固定第一首","true",f.js("[...document.querySelectorAll('.home-rec-card .art img.cover')].length===2&&[...document.querySelectorAll('.home-rec-card .art img.cover')].every(e=>e.getAttribute('src')===homeAlternate)"));
            }
            SystemClock.sleep(700);f.capture("next-home-"+width+"-"+zoom);
            if(width==320)verifyCoverCorners(f,zoom);
            assertEquals("标题底部保留4至14dp留白，不预留空白行 "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.home-rec-card .caption')].every(e=>{const r=document.createRange();r.selectNodeContents(e.querySelector('.name'));const lines=[...r.getClientRects()],gap=e.getBoundingClientRect().bottom-lines.at(-1).bottom;return gap>=4&&gap<=14})"));
            if(zoom==100) {
              double greetingSize=Double.parseDouble(f.js("parseFloat(getComputedStyle(document.querySelector('.welcome .n-h1')).fontSize)"));
              assertTrue("窄屏问候语应保持可读的最小字号",greetingSize>=22);
              if(width==320)assertTrue("320dp问候语应比原固定26px更紧凑",greetingSize<26);
              else assertTrue("界面变宽时问候语字号应增大",greetingSize>previousGreetingSize);
              previousGreetingSize=greetingSize;
            }
            String geometry=f.js("JSON.stringify([...document.querySelectorAll('.main-rec-grid,.rec-list,.main-rec .song-data-card,.personal-fm')].map(e=>({c:e.className,r:e.getBoundingClientRect().toJSON()})))");
            assertEquals("首页卡片不允许越界 "+width+"/"+zoom+" "+geometry,"true",f.js("[...document.querySelectorAll('.main-rec-grid,.rec-list,.main-rec .song-data-card,.personal-fm')].every(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.left>=-1&&r.right<=innerWidth+1})"));
            assertEquals("每日推荐与我喜欢的音乐必须保持同一水平行 "+width+"/"+zoom,"true",f.js("(()=>{const a=[...document.querySelectorAll('.main-rec .song-data-card')].map(e=>e.getBoundingClientRect());return a.length===2&&Math.abs(a[0].top-a[1].top)<=1&&a[0].right<=a[1].left})()"));
            assertEquals("两推荐卡间距应不超过8dp "+width+"/"+zoom,"true",f.js("(()=>{const a=[...document.querySelectorAll('.main-rec .song-data-card')].map(e=>e.getBoundingClientRect());return a[1].left-a[0].right<=8.1})()"));
            assertEquals("两卡主标题首行须水平对齐 "+width+"/"+zoom,"true",f.js("(()=>{const a=[...document.querySelectorAll('.home-rec-card .name')].map(e=>{const r=document.createRange();r.selectNodeContents(e);return r.getClientRects()[0]?.top});return a.length===2&&Math.abs(a[0]-a[1])<=1})()"));
            assertEquals("FM长文本按最新需求单行省略且布局不越界 "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.personal-fm .name,.personal-fm .artists')].every(e=>{const c=getComputedStyle(e),r=e.getBoundingClientRect(),p=e.closest('.personal-fm').getBoundingClientRect();return c.whiteSpace==='nowrap'&&c.textOverflow==='ellipsis'&&c.overflowX==='hidden'&&r.width>0&&r.right<=p.right+1})"));
            assertEquals("FM信息不能被固定高度裁切或遮挡 "+width+"/"+zoom,"true",f.js("(()=>{const e=document.querySelector('.personal-fm .fm-metadata');return e.scrollHeight<=e.clientHeight+1})()"));
            assertEquals("FM文字与封面上沿对齐，操作位于完整文字下方且不挤压封面 "+width+"/"+zoom,"true",f.js("(()=>{const e=document.querySelector('.personal-fm'),c=e.querySelector('.cover').getBoundingClientRect(),t=e.querySelector('.fm-metadata').getBoundingClientRect(),b=e.querySelector('.menu').getBoundingClientRect();return Math.abs(c.top-t.top)<=1&&b.bottom>=c.bottom-1&&b.top>=t.bottom-1})()"));
            assertEquals("内嵌封面应占卡片大部分并保留边界 "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.main-rec .song-data-card')].every(e=>{const a=e.querySelector('.cover')?.getBoundingClientRect(),b=e.getBoundingClientRect();return a&&a.width/b.width>=0.8&&a.width/b.width<=0.94&&a.left>b.left&&a.right<b.right})"));
            assertEquals("标题矩形及每行文字均须在封面区域内 "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.home-rec-card .caption')].every(e=>{const c=e.getBoundingClientRect(),m=e.closest('.cover').getBoundingClientRect(),r=document.createRange();r.selectNodeContents(e.querySelector('.name'));return c.left>=m.left-1&&c.right<=m.right+1&&c.bottom<=m.bottom+1&&[...r.getClientRects()].every(t=>t.left>=c.left-1&&t.right<=c.right+1&&t.top>=c.top-1&&t.bottom<=c.bottom+1)})"));
            assertEquals("矩形应与封面同宽，高度仅为文字加8dp "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.home-rec-card .caption')].every(e=>{const c=e.getBoundingClientRect(),m=e.closest('.cover').getBoundingClientRect(),t=e.querySelector('.name').getBoundingClientRect();return Math.abs(c.width-m.width)<=1&&Math.abs(c.height-t.height-8)<=1})"));
            assertEquals("长文本须完整换行，不以裁切掩盖 "+width+"/"+zoom,"true",f.js("[...document.querySelectorAll('.main-rec .info .name,.main-rec .info .desc')].filter(e=>!e.closest('.personal-fm')).every(e=>e.scrollWidth<=e.clientWidth+1&&e.scrollHeight<=e.clientHeight+1)"));
            assertEquals("三个 FM 操作必须为具名原生按钮并有48dp目标","true",f.js("(()=>{const b=[...document.querySelectorAll('.personal-fm .menu button')];return b.length===3&&b.every(e=>{const r=e.getBoundingClientRect();return !!e.getAttribute('aria-label')&&r.width>=48&&r.height>=48})})()"));
            f.js("document.querySelector('.personal-fm .menu').scrollIntoView({block:'center'})");
            SystemClock.sleep(400);f.capture("next-home-"+width+"-"+zoom+"-fm-actions");
            assertEquals("滚动后全部FM操作均可访问，不被固定播放条遮住","true",f.js("[...document.querySelectorAll('.personal-fm .menu button')].every(e=>{const r=e.getBoundingClientRect();return e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})"));
            f.js("document.querySelector('.home').scrollIntoView({block:'start'})");
          }
        }
        f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(100));
        f.js("document.querySelector('.home').scrollIntoView({block:'start'})");
        f.await("document.querySelectorAll('.home-rec-card .caption-material canvas').length===2");
        for(int index=0;index<2;index++) {
          String destination=index==0?"daily-songs":"like-songs";
          f.js("document.querySelector('.home-rec-card').scrollIntoView({block:'center'})");
          SystemClock.sleep(400);
          JSONObject rect=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelectorAll('.home-rec-card')["+index+"].getBoundingClientRect().toJSON())")).nextValue());
          float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
          float x=(float)(rect.getDouble("x")+rect.getDouble("width")/2)*scale,y=(float)(rect.getDouble("y")+rect.getDouble("height")/2)*scale;
          long down=SystemClock.uptimeMillis();
          for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();SystemClock.sleep(80);
          }
          f.await("fixtureRouter.currentRoute.value.name==='"+destination+"'");
          f.js("fixtureRouter.push('/')");
          f.await("document.querySelectorAll('.home-rec-card').length===2");
        }
      } finally {
        f.js("if(window.homeRandomPrevious)Math.random=homeRandomPrevious;fixtureSetting.$patch(homeSaved.setting);hm.$patch(homeSaved.music);hd.$patch(homeSaved.data)");
        String previous=size.contains("Override size:")?size.substring(size.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(originalOrientation));
      }
    }
  }
}

package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import androidx.test.platform.app.InstrumentationRegistry;

/** Measures rendered lyric motion during native touch, rather than after the recovery timer. */
public class LyricDragResponseTest {
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(var fd=f.inst.getUiAutomation().executeShellCommand(command);var in=new java.io.FileInputStream(fd.getFileDescriptor())) {return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();}
  }
  @Test public void bothLyricEnginesTrackFingerDuringDrag() throws Exception {
    String engineOnly=InstrumentationRegistry.getArguments().getString("engine","both");
    String inputMode=InstrumentationRegistry.getArguments().getString("inputMode","system");
    int textZoom=Integer.parseInt(InstrumentationRegistry.getArguments().getString("textZoom","100"));
    boolean spring=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("spring","false"));
    boolean word=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("word","false"));
    boolean blur=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("blur","true"));
    boolean profile=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("profile","false"));
    boolean rasterProbe=Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("rasterProbe","false"));
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int orientation=f.activity.getRequestedOrientation(),display=f.activity.getDisplay().getDisplayId();
      String originalSize=shell(f,"wm size -d "+display);
      float density=f.activity.getResources().getDisplayMetrics().density;
      shell(f,"wm size "+Math.round(360*density)+"x"+Math.round(720*density)+" -d "+display);
      int[] originalTextZoom={100};
      f.inst.runOnMainSync(()->originalTextZoom[0]=f.web.getSettings().getTextZoom());
      f.inst.runOnMainSync(()->{f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);f.web.getSettings().setTextZoom(textZoom);});
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      JSONArray reports=new JSONArray();
      java.util.List<AssertionError> profileFailures=new java.util.ArrayList<>();
      try {
        if(profile){f.inst.runOnMainSync(()->android.webkit.WebView.setWebContentsDebuggingEnabled(true));android.os.Bundle phase=new android.os.Bundle();phase.putString("profilePhase","amll-ready");f.inst.sendStatus(2,phase);SystemClock.sleep(20000);}
        for(boolean amll:new boolean[]{false,true}) {
          if(("amll".equals(engineOnly)&&!amll)||("default".equals(engineOnly)&&amll))continue;
          f.js("p7Init(()=>{window.dragPlayer=window.__SPLAYER_PLAYER_CONTROLLER__;window.dragOriginalSeek=dragPlayer.getSeek;dragPlayer.getSeek=()=>35000}).then(()=>{fixtureSetting.androidDeviceModeOverride='phone';Object.assign(fixtureSetting,{lyricFontSizeMode:'fixed',lyricFontSize:32,lyricTranFontSize:18,lyricRomaFontSize:18});fixtureSetting.useAMLyrics="+amll+";fixtureSetting.useAMSpring="+spring+";fixtureSetting.lyricsBlur="+blur+";fixtureStatus.pureLyricMode=false;p7Stores.music.playSong={...p7Stores.music.playSong,id:99007102};const lines=Array.from({length:100},(_,i)=>{const text='真实触摸与长歌词跟手验证 English 日本語 '+i;return {startTime:i*7000,endTime:(i+1)*7000,words:"+word+"?[...text].map((word,j,a)=>({word,startTime:i*7000+j*7000/a.length,endTime:i*7000+(j+1)*7000/a.length})):[{startTime:i*7000,endTime:(i+1)*7000,word:text}],translatedLyric:'A translated line stays readable during a real drag.',romanLyric:'Zhen shi chu mo yu chang ge ci'}});p7Stores.music.setSongLyric({lrcData:lines,yrcData:"+word+"?lines:[]},true);window.dragReady=true})");
          try {
            f.await("window.dragReady&&!!document.querySelector('"+(amll?".am-lyric div[class*=lyricLineWrapper]":".lyric-scroll-container #lrc-5")+"')");
            f.js("[...document.querySelectorAll('.mobile-player-bottom-controls .dot')].at(-1)?.click()");
            f.await("document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden')");SystemClock.sleep(700);
            String selector=amll?".am-lyric":".lyric-scroll-container";
            if(amll&&rasterProbe)f.js("window.dragRasterStyle=document.createElement('style');dragRasterStyle.textContent='.am-lyric [class*=lyricMainLine] span {will-change:auto!important}';document.head.appendChild(dragRasterStyle)");
            f.js("window.dragLongTasks=[];window.dragObserver=new PerformanceObserver(list=>dragLongTasks.push(...list.getEntries().map(e=>({start:e.startTime,duration:e.duration}))));dragObserver.observe({type:'longtask'});window.dragSamples=[];window.dragTracked=document.querySelector('"+(amll?".am-lyric div[class*=lyricLineWrapper]":".lyric-scroll-container #lrc-5")+"');window.dragRecord=e=>{const y=e.touches[0]?.clientY,received=performance.now(),stamp=e.timeStamp;requestAnimationFrame(()=>{if(dragTracked?.isConnected)dragSamples.push({finger:y,line:dragTracked.getBoundingClientRect().y,delay:performance.now()-stamp,handler:received-stamp})})};document.querySelector('"+selector+"').addEventListener('touchmove',dragRecord,{passive:true})");
            String beforeLines=f.js("JSON.stringify({active:[...document.querySelectorAll('.am-lyric [class*=active],.lyric-scroll-container .on')].map(e=>({text:e.textContent,cls:e.className})),song:p7Stores.music.playSong.id,lines:p7Stores.music.songLyric.lrcData.length,background:fixtureSetting.playerBackgroundType,backgroundPause:fixtureSetting.playerBackgroundPause,homeCards:document.querySelectorAll('.home-rec-card').length,canvases:[...document.querySelectorAll('canvas')].map(e=>({parent:String(e.parentElement?.className),width:e.width,height:e.height}))})");
            JSONObject r=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(document.querySelector('"+selector+"').getBoundingClientRect().toJSON())")).nextValue());
            float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
            float x=(float)(r.getDouble("x")+r.getDouble("width")*.5)*scale;
            float y=(float)(r.getDouble("y")+r.getDouble("height")*.7)*scale;
            float distance=(float)r.getDouble("height")*.3f*scale;
            if("instrumentation".equals(inputMode)) {
              assertEquals("此定频系统输入诊断仅用于已核实的手机主显示器",0,display);
              int[] location=new int[2];f.inst.runOnMainSync(()->f.web.getLocationOnScreen(location));
              long down=SystemClock.uptimeMillis();
              for(int i=0;i<=20;i++) {
                int action=i==0?MotionEvent.ACTION_DOWN:i==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
                MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+location[0],y+location[1]-distance*i/20,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try {f.inst.sendPointerSync(e);}finally{e.recycle();}
                SystemClock.sleep(16);
              }
            } else if("system".equals(inputMode)) {
              int[] location=new int[2];f.inst.runOnMainSync(()->f.web.getLocationOnScreen(location));
              String result=shell(f,"input -d "+display+" swipe "+Math.round(x+location[0])+" "+Math.round(y+location[1])+" "+Math.round(x+location[0])+" "+Math.round(y-distance+location[1])+" 320");
              assertFalse("系统触摸命令必须成功: "+result,result.contains("Error")||result.contains("Exception"));
            } else {
            long down=SystemClock.uptimeMillis();
            for(int i=0;i<=20;i++) {
              int action=i==0?MotionEvent.ACTION_DOWN:i==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
              MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y-distance*i/20,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
              try {f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(e));}finally{e.recycle();}
              SystemClock.sleep(16);
            }
            }
            SystemClock.sleep(200);
            JSONArray samples=new JSONArray((String)new JSONTokener(f.js("JSON.stringify(dragSamples)")).nextValue());
            JSONObject result=new JSONObject().put("engine",amll?"amll":"default").put("inputMode",inputMode).put("textZoom",textZoom).put("spring",spring).put("word",word).put("blur",blur).put("rasterProbe",rasterProbe).put("beforeLines",new JSONTokener(beforeLines).nextValue()).put("samples",samples).put("longTasks",new JSONArray((String)new JSONTokener(f.js("JSON.stringify(dragLongTasks)")).nextValue()));reports.put(result);
            java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
            try(var out=new java.io.FileOutputStream(new java.io.File(dir,"lyric-drag-response.json"))){out.write(reports.toString(2).getBytes(StandardCharsets.UTF_8));}
            if(amll&&profile){android.os.Bundle phase=new android.os.Bundle();phase.putString("profilePhase","amll-recorded");f.inst.sendStatus(2,phase);SystemClock.sleep(15000);}
            assertTrue("拖动必须产生可观测的真实帧："+result,samples.length()>=3);
            JSONObject first=samples.getJSONObject(0),last=samples.getJSONObject(samples.length()-1);
            double finger=Math.abs(last.getDouble("finger")-first.getDouble("finger"));
            double content=Math.abs(last.getDouble("line")-first.getDouble("line"));
            double[] delays=new double[samples.length()];for(int i=0;i<delays.length;i++)delays[i]=samples.getJSONObject(i).getDouble("delay");java.util.Arrays.sort(delays);
            assertTrue("手指需要真实移动足够距离："+result,finger>50);
            assertTrue("歌词应在手指按住期间同步移动，而非等待松手："+result,content/finger>=.65&&content/finger<=1.35);
            assertTrue("95%输入到可见帧延迟应小于100ms："+result,delays[(int)Math.ceil(delays.length*.95)-1]<100);
            SystemClock.sleep(6000);
            f.js("window.dragOriginalSetSeek=dragPlayer.setSeek;window.dragOriginalPlay=dragPlayer.play;window.dragSeekCalls=[];dragPlayer.setSeek=time=>dragSeekCalls.push(time);dragPlayer.play=()=>Promise.resolve()");
            String active=amll?"document.querySelector('.am-lyric div[class*=lyricMainLine][class*=active]')":"document.querySelector('.lyric-scroll-container #lrc-5 .content')";
            try {f.await("(()=>{const e="+active+";if(!e)return false;const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))})()");}
            catch(AssertionError error) {throw new AssertionError("歌词目标几何="+f.js("JSON.stringify((()=>{const e="+active+",r=e?.getBoundingClientRect(),c=document.querySelector('.lyric-scroll-container');return {seek:dragPlayer.getSeek(),offset:fixtureStatus.getSongOffset(p7Stores.music.playSong.id),active:[...document.querySelectorAll('.lyric-scroll-container .on,.am-lyric [class*=active]')].map(e=>({id:e.id,text:e.textContent,cls:e.className})),song:p7Stores.music.playSong.id,lines:p7Stores.music.songLyric.lrcData.length,amllRows:[...document.querySelectorAll('.am-lyric div[class*=lyricMainLine]')].slice(0,10).map(e=>({text:e.textContent,cls:e.className,y:e.getBoundingClientRect().y})),rect:r?.toJSON(),scrollTop:c?.scrollTop,container:c?.getBoundingClientRect().toJSON(),stack:r?document.elementsFromPoint(r.x+r.width/2,r.y+r.height/2).map(e=>String(e.className)):[]}})())"),error);}
            JSONObject target=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(("+active+").getBoundingClientRect().toJSON())")).nextValue());
            float tx=(float)(target.getDouble("x")+target.getDouble("width")*.5)*scale;
            float ty=(float)(target.getDouble("y")+target.getDouble("height")*.5)*scale;
            long tappedDown=SystemClock.uptimeMillis();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
              MotionEvent tap=MotionEvent.obtain(tappedDown,SystemClock.uptimeMillis(),action,tx,ty,0);tap.setSource(InputDevice.SOURCE_TOUCHSCREEN);
              try {f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(tap));}finally{tap.recycle();}SystemClock.sleep(80);
            }
            // AMLL阻止浏览器click并发出自身line-click，验证实际定位命令而非DOM click。
            f.await("dragSeekCalls.length===1");
            // 点击会唤出控件并改变歌词裁切区，长按需重新命中当前可见文字。
            String pressPoint="(()=>{const e="+active+",r=e.getBoundingClientRect();for(const fy of [.5,.75,.25,.9,.1])for(const fx of [.5,.25,.75]){const x=r.x+r.width*fx,y=r.y+r.height*fy;if(e.contains(document.elementFromPoint(x,y)))return {x,y}}return null})()";
            f.await("!!"+pressPoint);
            JSONObject visiblePress=new JSONObject((String)new JSONTokener(f.js("JSON.stringify("+pressPoint+")")).nextValue());
            tx=(float)visiblePress.getDouble("x")*scale;ty=(float)visiblePress.getDouble("y")*scale;
            f.js("window.dragPressEvents=[];window.dragPressRecord=e=>dragPressEvents.push({type:e.type,target:String(e.target.className),line:e.target.closest?.('.lrc-line')?.id,touch:e.touches?.[0]?{x:e.touches[0].clientX,y:e.touches[0].clientY}:null});['touchstart','touchend','touchcancel'].forEach(t=>document.addEventListener(t,dragPressRecord,{capture:true,passive:true}));window.dragPressBefore={active:("+active+").getBoundingClientRect().toJSON(),point:{x:"+(tx/scale)+",y:"+(ty/scale)+"},top:String(document.elementFromPoint("+(tx/scale)+","+(ty/scale)+")?.className)}");
            long heldDown=SystemClock.uptimeMillis();
            MotionEvent press=MotionEvent.obtain(heldDown,heldDown,MotionEvent.ACTION_DOWN,tx,ty,0);press.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(press));}finally{press.recycle();}
            SystemClock.sleep(2750);
            MotionEvent release=MotionEvent.obtain(heldDown,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,tx,ty,0);release.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(release));}finally{release.recycle();}
            try {f.await("!!document.querySelector('.copy-lyrics')");}
            catch(AssertionError error){f.capture("drag-large-text-long-press");throw new AssertionError("长按触摸诊断="+f.js("JSON.stringify({before:dragPressBefore,events:dragPressEvents,active:("+active+").getBoundingClientRect().toJSON(),seekCalls:dragSeekCalls.length})"),error);}
            assertEquals("长按打开歌词菜单不能误触进度跳转","1",f.js("dragSeekCalls.length"));
            f.js("window.$modal.destroyAll();['touchstart','touchend','touchcancel'].forEach(t=>document.removeEventListener(t,dragPressRecord,true))");
          } catch(AssertionError error) {
            if(!profile)throw error;
            profileFailures.add(error);
          } finally {
            f.js("['touchstart','touchend','touchcancel'].forEach(t=>document.removeEventListener(t,window.dragPressRecord,true));window.dragObserver?.disconnect();window.dragRasterStyle?.remove();document.querySelector('"+(amll?".am-lyric":".lyric-scroll-container")+"')?.removeEventListener('touchmove',window.dragRecord);if(window.dragOriginalSetSeek){dragPlayer.setSeek=dragOriginalSetSeek;dragPlayer.play=dragOriginalPlay;delete window.dragOriginalSetSeek;delete window.dragOriginalPlay}dragPlayer.getSeek=dragOriginalSeek;window.dragReady=false;p7Restore()");f.await("window.p7Restored===true");
          }
        }
        if(!profileFailures.isEmpty()){AssertionError error=profileFailures.get(0);for(int i=1;i<profileFailures.size();i++)error.addSuppressed(profileFailures.get(i));throw error;}
      } finally {
        if(profile){android.os.Bundle phase=new android.os.Bundle();phase.putString("profilePhase","profile-finishing");f.inst.sendStatus(2,phase);SystemClock.sleep(5000);f.inst.runOnMainSync(()->android.webkit.WebView.setWebContentsDebuggingEnabled(false));}
        String previous=originalSize.contains("Override size:")?originalSize.substring(originalSize.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);f.inst.runOnMainSync(()->{f.activity.setRequestedOrientation(orientation);f.web.getSettings().setTextZoom(originalTextZoom[0]);});
      }
    }
  }
}

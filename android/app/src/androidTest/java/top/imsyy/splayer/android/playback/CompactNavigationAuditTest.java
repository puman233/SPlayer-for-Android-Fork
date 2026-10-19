package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Observational inventory from the live router; geometry outliers remain evidence, not automatic bugs. */
@RunWith(AndroidJUnit4.class)
public class CompactNavigationAuditTest {
  private String label;
  private JSONArray records=new JSONArray();
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);
      java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();}
  }
  private JSONObject inspect(DeviceWebFixture f,String page) throws Exception {
    String script="JSON.stringify((()=>{const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&r.bottom>0&&r.top<innerHeight};return {page:"+JSONObject.quote(page)+",url:location.hash,width:innerWidth,height:innerHeight,dpr:devicePixelRatio,zoom:fixtureSetting.pageZoom,outliers:[...document.querySelectorAll('button,input,.n-dialog,.n-modal,.n-card,.set-item,.song-card')].filter(visible).filter(e=>{const r=e.getBoundingClientRect();return r.left < -1||r.right>innerWidth+1}).slice(0,30).map(e=>({tag:e.tagName,cls:String(e.className),text:e.textContent.slice(0,160),rect:e.getBoundingClientRect().toJSON()}))}})())";
    JSONObject row=new JSONObject((String)new JSONTokener(f.js(script)).nextValue());
    records.put(row);f.capture("next-audit-"+label+"-"+page.replaceAll("[^a-zA-Z0-9_-]","_"));return row;
  }
  private void inspectSettingOverlay(DeviceWebFixture f,String key) throws Exception {
    String selector="#setting-"+key+" button";
    if(!"true".equals(f.js("!!document.querySelector("+JSONObject.quote(selector)+")"))) {
      records.put(new JSONObject().put("page","overlay-"+key).put("status","NOT_AVAILABLE"));return;
    }
    int before=Integer.parseInt(f.js("document.querySelectorAll('.n-modal').length"));
    f.js("(()=>{const b=document.querySelector("+JSONObject.quote(selector)+");b.scrollIntoView({block:'center'});b.click()})()");
    f.await("document.querySelectorAll('.n-modal').length>"+before);
    SystemClock.sleep(700);
    JSONObject row=inspect(f,"overlay-"+key);
    row.put("activeModal",new JSONObject((String)new JSONTokener(f.js("JSON.stringify((()=>{const m=[...document.querySelectorAll('.n-modal')].at(-1);return {rect:m.getBoundingClientRect().toJSON(),horizontalOutliers:[...m.querySelectorAll('button,input,.n-card')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&(r.left< -1||r.right>innerWidth+1)}).map(e=>({tag:e.tagName,rect:e.getBoundingClientRect().toJSON()}))}})())")).nextValue()));
    // 只关闭最上层浮层，不点击保存、重置、删除或开关。
    f.js("[...document.querySelectorAll('.n-modal')].at(-1).querySelector('.n-base-close').click()");
    f.await("document.querySelectorAll('.n-modal').length==="+before);
  }
  @Test public void inventoryActualNavigationAtCompactAndLargeText() throws Exception {
    label=InstrumentationRegistry.getArguments().getString("label","compact");
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId();
      int originalOrientation=f.activity.getRequestedOrientation();
      String originalSize=shell(f,"wm size -d "+display);
      int width=Integer.parseInt(InstrumentationRegistry.getArguments().getString("width","0"));
      int height=Integer.parseInt(InstrumentationRegistry.getArguments().getString("height","640"));
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      if(width>0){float density=f.activity.getResources().getDisplayMetrics().density;shell(f,"wm size "+Math.round(width*density)+"x"+Math.round(height*density)+" -d "+display);f.await("Math.abs(innerWidth-"+width+")<=1");}
      try {
      JSONObject account=new JSONObject((String)new JSONTokener(f.js("JSON.stringify((()=>{const d=fixturePinia._s.get('data'),cookiePresent=!!(localStorage.getItem('cookie-MUSIC_U')||document.cookie.split(';').some(c=>c.trim().startsWith('MUSIC_U=')));return {loginFlag:!!d.userLoginStatus,loginType:d.loginType,cookiePresent,syntheticProfile:d.userData.userId===99009900||d.userData.name==='Synthetic home fixture',authenticated:!!d.userLoginStatus&&(d.loginType==='uid'||cookiePresent)}})())")).nextValue());
      android.os.Bundle accountProgress=new android.os.Bundle();accountProgress.putString("auditAccount",account.toString());f.inst.sendStatus(2,accountProgress);
      f.js("window.auditVisibleLoading=()=>[...document.querySelectorAll('.n-skeleton,.n-spin-body')].some(e=>{const r=e.getBoundingClientRect();if(r.width<=0||r.height<=0||r.bottom<=0||r.top>=innerHeight)return false;for(let n=e;n;n=n.parentElement){const s=getComputedStyle(n);if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)===0)return false}return true})");
      f.js("window.auditReferenceReady=false;(async()=>{for(let attempt=0;attempt<60;attempt++){try{if((await fetch('http://127.0.0.1:1145/api')).ok)break}catch{}if(attempt===59){window.auditReference={error:'LOCAL_API_NOT_READY'};window.auditReferenceReady=true;return}await new Promise(resolve=>setTimeout(resolve,500))}const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),12000);try{const [song,charts,mvs,radios,categories]=await Promise.allSettled([fetch('http://127.0.0.1:1145/api/netease/song/detail?ids=28941713&noCookie=true',{signal:abort.signal}).then(r=>r.json()),fetch('http://127.0.0.1:1145/api/netease/toplist?noCookie=true',{signal:abort.signal}).then(r=>r.json()),fetch('http://127.0.0.1:1145/api/netease/mv/all?limit=1&noCookie=true',{signal:abort.signal}).then(r=>r.json()),fetch('http://127.0.0.1:1145/api/netease/dj/recommend?noCookie=true',{signal:abort.signal}).then(r=>r.json()),fetch('http://127.0.0.1:1145/api/netease/dj/catelist?noCookie=true',{signal:abort.signal}).then(r=>r.json())]);const s=song.status==='fulfilled'?song.value.songs?.[0]:null;window.auditReference={song:s?.id,album:s?.al?.id,artist:s?.ar?.[0]?.id,playlist:charts.status==='fulfilled'?charts.value.list?.[0]?.id:null,video:mvs.status==='fulfilled'?mvs.value.data?.[0]?.id:null,radio:radios.status==='fulfilled'?radios.value.djRadios?.[0]?.id:null,radioCategory:categories.status==='fulfilled'?categories.value.categories?.[0]?.id:null,radioCategoryName:categories.status==='fulfilled'?categories.value.categories?.[0]?.name:null}}finally{clearTimeout(timer);window.auditReferenceReady=true}})()");
      for(int wait=0;wait<450&&!"true".equals(f.js("window.auditReferenceReady"));wait++)SystemClock.sleep(100);
      JSONObject reference=new JSONObject((String)new JSONTokener(f.js("JSON.stringify(window.auditReference||{})")).nextValue());
      // 可传入本次设备外公开API核实的ID；不更改用户配置，也不伪造远程内容。
      for(String key:new String[]{"video","radio","radioCategory"}) {
        String value=InstrumentationRegistry.getArguments().getString("public-"+key,"");
        if(!value.isEmpty())reference.put(key,Long.parseLong(value));
      }
      String categoryName=InstrumentationRegistry.getArguments().getString("public-radioCategoryName","");
      if(!categoryName.isEmpty())reference.put("radioCategoryName",categoryName);
      String raw=(String)new JSONTokener(f.js("JSON.stringify(fixtureRouter.getRoutes().filter(r=>r.name&&!r.path.includes(':')).map(r=>({name:r.name,path:r.path,auth:!!r.meta?.needLogin})))")).nextValue();
      JSONArray routes=new JSONArray(raw);
      boolean accountOnly="account".equals(InstrumentationRegistry.getArguments().getString("scope","all"));
      boolean mediaOnly="media".equals(InstrumentationRegistry.getArguments().getString("scope","all"));
      int textZoom=Integer.parseInt(InstrumentationRegistry.getArguments().getString("textZoom","100"));
      f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(textZoom));
      for(int i=0;i<routes.length();i++) {
        JSONObject route=routes.getJSONObject(i);String path=route.getString("path");
        if(mediaOnly&&!path.matches("/(video|radio|radio-type)"))continue;
        if(accountOnly&&!path.matches("/(like-songs|cloud|daily-songs|like/(playlists|albums|artists|videos|radios))"))continue;
        if(route.optBoolean("auth")&&!account.optBoolean("authenticated")) {
          records.put(new JSONObject().put("page",route.getString("name")).put("status","BLOCKED_AUTH_REQUIRED"));continue;
        }
        String target=path;
        if(path.contains("search"))target+="?keyword=No%20title";
        String input=path.startsWith("/artist")?"artist":path.equals("/album")?"album":path.equals("/playlist")?"playlist":path.equals("/comment")||path.equals("/song/wiki")?"song":path.equals("/video")?"video":path.equals("/radio")?"radio":path.equals("/radio-type")?"radioCategory":null;
        if(input!=null) {
          if(reference.optLong(input)<=0){records.put(new JSONObject().put("page",route.getString("name")).put("status","BLOCKED_PUBLIC_INPUT_UNAVAILABLE"));continue;}
          target+="?id="+reference.getLong(input);
          if(path.equals("/radio-type"))target+="&name="+java.net.URLEncoder.encode(reference.optString("radioCategoryName"),StandardCharsets.UTF_8);
        } else if(path.equals("/streaming-playlist")) {
          records.put(new JSONObject().put("page",route.getString("name")).put("status","BLOCKED_VALID_MEDIA_INPUT_REQUIRED"));continue;
        }
        f.js("window.$modal?.destroyAll();fixtureStatus.showFullPlayer=false;fixtureRouter.push("+JSONObject.quote(target)+").catch(()=>{})");
        SystemClock.sleep(500);
        {
          // 账号巡检等待真实数据状态；超时仍记录为 Loading，不能把骨架图当作内容通过。
          for(int wait=0;wait<24&&"true".equals(f.js("auditVisibleLoading()"));wait++)SystemClock.sleep(500);
        }
        JSONObject row=inspect(f,route.getString("name"));row.put("requested",target).put("actual",(String)new JSONTokener(f.js("fixtureRouter.currentRoute.value.fullPath")).nextValue());
        row.put("loadingAtCapture","true".equals(f.js("auditVisibleLoading()")));
        android.os.Bundle progress=new android.os.Bundle();progress.putString("auditPage",route.getString("name"));f.inst.sendStatus(2,progress);
      }
      f.js("fixtureRouter.push('/');document.querySelector('.nav button[title=\"设置\"]')?.click()");
      f.await("[...document.querySelectorAll('.n-dropdown-option')].some(e=>e.textContent.includes('设置'))");
      f.js("[...document.querySelectorAll('.n-dropdown-option-body')].find(e=>e.textContent.includes('全局设置')).click()");
      f.await("!!document.querySelector('.setting')");
      String[] keys={"general","appearance","play","lyric","local","keyboard","network","about"};
      int index=0;
      boolean overlays="true".equals(InstrumentationRegistry.getArguments().getString("overlays","false"));
      for(String group:new String[]{"常规","外观","播放","歌词","本地","快捷键","网络","关于"}) {
        f.js("(()=>{const nav=document.querySelector('.setting .set-left');if(nav&&getComputedStyle(nav).display==='none')document.querySelector('.setting .mobile-title button')?.click();[...document.querySelectorAll('.setting .n-menu-item-content')].find(e=>e.textContent.includes("+JSONObject.quote(group)+"))?.click()})()");
        SystemClock.sleep(600);inspect(f,"setting-"+keys[index++]);
        if(overlays&&group.equals("外观"))for(String key:new String[]{"fontConfig","sidebarHide","homePageSection","playlistPageElements","fullscreenPlayer"})inspectSettingOverlay(f,key);
        if(overlays&&group.equals("歌词"))inspectSettingOverlay(f,"configExcludeLyric");
      }
      File dir=new File(f.activity.getExternalFilesDir(null),"detail-regression");assertTrue(dir.exists()||dir.mkdirs());
      JSONObject report=new JSONObject().put("label",label).put("textZoom",textZoom).put("account",account).put("reference",reference).put("routes",routes).put("observations",records)
          .put("limitations","导航快照及可见几何采样；鉴权重定向、未加载内容、滚动外内容和未打开浮层不算通过。textZoom 不等同系统fontScale。");
      try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(dir,"next-audit-"+label+".json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
      f.js("window.$modal?.destroyAll()");
      } finally {
        if(width>0){String previous=originalSize.contains("Override size:")?originalSize.substring(originalSize.indexOf("Override size:")+14).trim():"reset";shell(f,"wm size "+previous+" -d "+display);}
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(originalOrientation));
      }
    }
  }
}

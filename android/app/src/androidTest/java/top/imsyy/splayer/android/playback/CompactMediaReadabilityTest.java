package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Compact persistent player and real public video detail, through visible UI. */
@RunWith(AndroidJUnit4.class)
public class CompactMediaReadabilityTest {
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
  @Test public void miniPlayerTextFitsAndCoverOpensPlayerAtLargeText() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;window.miniReady=true})");
      try {
        f.await("window.miniReady&&!!document.querySelector('.main-player.phone-floating.show')");
        for(String mode:new String[]{"phone","auto"}) {
          f.js("fixtureSetting.androidDeviceModeOverride="+JSONObject.quote(mode));
        for(int zoom:new int[]{100,200}) {
          f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(zoom));SystemClock.sleep(600);
          f.capture("mini-player-text-"+mode+"-"+zoom);
          JSONObject g=json(f,"(()=>{const p=document.querySelector('.main-player.phone-floating'),a=p.querySelector('.artists .ar-item'),n=p.querySelector('.data .name'),r=document.createRange();r.selectNodeContents(a);return {bar:p.getBoundingClientRect().toJSON(),artist:r.getBoundingClientRect().toJSON(),artistBox:a.closest('.lyric-container').getBoundingClientRect().toJSON(),title:n.getBoundingClientRect().toJSON()}})()");
          JSONObject bar=g.getJSONObject("bar"),artist=g.getJSONObject("artist"),box=g.getJSONObject("artistBox");
          assertTrue("自动设备模式也必须保留可见歌曲信息: "+mode+g,g.getJSONObject("title").getDouble("width")>1&&box.getDouble("width")>1);
          assertTrue("歌手文字不能越出迷你播放器或被固定副标题高度裁切: "+g,artist.getDouble("top")>=bar.getDouble("top")-1&&artist.getDouble("bottom")<=bar.getDouble("bottom")+1&&artist.getDouble("top")>=box.getDouble("top")-1&&artist.getDouble("bottom")<=box.getDouble("bottom")+1);
          assertTrue("标题和歌手不能互相覆盖: "+g,g.getJSONObject("title").getDouble("bottom")<=artist.getDouble("top")+1);
          f.tapVisible("document.querySelector('.main-player.phone-floating .cover')");
          f.await("fixtureStatus.showFullPlayer===true&&!!document.querySelector('.full-player-mobile')");
          f.js("fixtureStatus.showFullPlayer=false");SystemClock.sleep(400);
        }
        }
      } finally {f.js("p7Restore()");f.await("window.p7Restored===true");}
    }
  }
  @Test public void miniPlayerRecoversCoverAndKeepsHeightAcrossSongAndLyricChanges() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      f.js("p7Init().then(()=>{fixtureSetting.androidDeviceModeOverride='phone';fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;fixtureSetting.barLyricShow=true;window.coverBroken=URL.createObjectURL(new Blob(['not an image'],{type:'image/png'}));window.coverGood=URL.createObjectURL(new Blob(['<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"48\" height=\"48\"><rect width=\"48\" height=\"48\" fill=\"#428899\"/></svg>'],{type:'image/svg+xml'}));p7Stores.music.playSong={...p7Stores.music.playSong,cover:coverGood,coverSize:{s:coverBroken}};window.coverTestReady=true})");
      try {
        f.await("window.coverTestReady&&!!document.querySelector('.main-player.phone-floating.show')");
        f.await("[...document.querySelectorAll('.main-player .cover img')].some(i=>i.dataset.error==='true'||(i.src===coverGood&&i.complete&&i.naturalWidth===48))");
        SystemClock.sleep(600);
        String loaded="(()=>{const i=[...document.querySelectorAll('.main-player .cover img')].find(i=>i.src===coverGood);return !!i&&i.complete&&i.naturalWidth===48&&getComputedStyle(i).visibility!=='hidden'&&parseFloat(getComputedStyle(i).opacity)>0.9})()";
        assertEquals("缩略图失败但原封面可用时，底栏应回退到真实封面而非空白", "true",f.js(loaded));
        f.js("p7Stores.music.playSong.coverSize.s=coverGood");SystemClock.sleep(400);
        assertEquals("小封面地址更新后必须显示新封面", "true",f.js(loaded));
        for(int zoom:new int[]{100,200})for(String effect:new String[]{"fade","slide"}) {
          f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(zoom));
          f.js("fixtureSetting.lyricTransition="+JSONObject.quote(effect)+";fixtureStatus.playStatus=true;fixtureStatus.lyricLoading=false;fixtureStatus.lyricIndex=1");SystemClock.sleep(500);
          f.js("window.miniHeights=[];window.miniSampling=true;window.miniSample=()=>{if(!miniSampling)return;miniHeights.push(document.querySelector('.main-player').getBoundingClientRect().height);requestAnimationFrame(miniSample)};requestAnimationFrame(miniSample)");
          for(int n=0;n<3;n++) {
            f.js("fixtureStatus.lyricIndex="+(n+2)+";p7Stores.music.playSong={...p7Stores.music.playSong,id:"+(930000+n)+",name:'切换歌曲 Long song '+"+n+"}");SystemClock.sleep(360);
            f.js("fixtureStatus.playStatus=false");SystemClock.sleep(360);
            f.js("fixtureStatus.playStatus=true");SystemClock.sleep(360);
          }
          JSONObject heights=json(f,"(()=>{miniSampling=false;return {min:Math.min(...miniHeights),max:Math.max(...miniHeights),samples:miniHeights.length}})()");
          assertTrue("切歌和歌词/歌手交替不能让底栏高度跳变: "+zoom+"/"+effect+heights,heights.getInt("samples")>10&&heights.getDouble("max")-heights.getDouble("min")<=1);
        }
        f.capture("mini-player-cover-and-stable-height");
      } finally {f.js("miniSampling=false;URL.revokeObjectURL(coverBroken);URL.revokeObjectURL(coverGood);p7Restore()");f.await("window.p7Restored===true");}
    }
  }
  @Test public void miniPlayerUsesNativeWarmCoverWhenImageNetworkIsUnavailable() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888);
      image.eraseColor(android.graphics.Color.rgb(45,139,155));java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);image.recycle();
      String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
      f.js("p7Init().then(async()=>{fixtureSetting.cacheEnabled=true;fixtureSetting.androidDeviceModeOverride='phone';fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;window.warmCoverUrl='https://cover-test.invalid/"+java.util.UUID.randomUUID()+".png';let h=5381;for(let i=0;i<warmCoverUrl.length;i++)h=((h<<5)+h+warmCoverUrl.charCodeAt(i))|0;window.warmCoverKey=new URL(warmCoverUrl).pathname.split('/').pop().slice(-32)+'_'+(h>>>0).toString(16);const seed=await Capacitor.nativePromise('AndroidCache','write',{type:'covers',key:warmCoverKey,data:"+JSONObject.quote(encoded)+"});if(!seed.success)throw Error('Could not seed test-owned native cover');p7Stores.music.playSong={...p7Stores.music.playSong,cover:warmCoverUrl,coverSize:{s:warmCoverUrl,l:warmCoverUrl}};window.warmCoverReady=true})");
      try {
        f.await("window.warmCoverReady===true");long start=SystemClock.elapsedRealtime();
        f.await("[...document.querySelectorAll('.main-player .cover img')].some(i=>i.src.startsWith('blob:')&&i.complete&&i.naturalWidth===48&&parseFloat(getComputedStyle(i).opacity)>.9)");
        long elapsed=SystemClock.elapsedRealtime()-start;
        assertTrue("本地封面无需等待远程图片网络: "+elapsed,elapsed<1500);
        f.capture("mini-player-warm-cover-offline");
        java.io.File dir=new java.io.File(f.activity.getExternalFilesDir(null),"detail-regression");
        try(var out=new java.io.PrintWriter(new java.io.File(dir,"mini-cover-cache.json"),"UTF-8")){out.print(new JSONObject().put("elapsedMs",elapsed).put("source","native-cache-blob").put("textZoom",200));}
      } finally {
        f.js("Capacitor.nativePromise('AndroidCache','remove',{type:'covers',key:warmCoverKey});p7Restore()");f.await("window.p7Restored===true");
      }
    }
  }
  @Test public void coldCoverUsesDownloadedBytesWithoutWaitingForSlowerImageRequest() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(48,48,android.graphics.Bitmap.Config.ARGB_8888);
      bitmap.eraseColor(android.graphics.Color.rgb(91,149,106));
      java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();
      byte[] png=output.toByteArray();
      java.util.concurrent.atomic.AtomicInteger downloaded=new java.util.concurrent.atomic.AtomicInteger(),images=new java.util.concurrent.atomic.AtomicInteger();
      android.webkit.WebViewClient[] original=new android.webkit.WebViewClient[1];
      f.inst.runOnMainSync(()->original[0]=f.web.getWebViewClient());
      f.inst.runOnMainSync(()->f.web.setWebViewClient(new com.getcapacitor.BridgeWebViewClient(f.activity.getBridge()) {
        @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest request) {
          if(!"cold-cover-test.invalid".equals(request.getUrl().getHost()))return super.shouldInterceptRequest(view,request);
          boolean image=request.getRequestHeaders().values().stream().anyMatch(v->v.contains("image/"));
          if(image){images.incrementAndGet();SystemClock.sleep(4000);}else {SystemClock.sleep(120);downloaded.incrementAndGet();}
          return new android.webkit.WebResourceResponse("image/png",null,200,"OK",java.util.Map.of("Access-Control-Allow-Origin","*","Cache-Control","no-store"),new java.io.ByteArrayInputStream(png));
        }
      }));
      f.js("p7Init().then(()=>{fixtureSetting.cacheEnabled=true;fixtureSetting.androidDeviceModeOverride='phone';fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;window.coldCoverUrl='https://cold-cover-test.invalid/"+java.util.UUID.randomUUID()+".png';p7Stores.music.playSong={...p7Stores.music.playSong,cover:coldCoverUrl,coverSize:{s:coldCoverUrl,l:coldCoverUrl}};window.coldCoverReady=true})");
      try {
        f.await("window.coldCoverReady===true");
        long start=SystemClock.elapsedRealtime();
        while(downloaded.get()==0&&SystemClock.elapsedRealtime()-start<2500)SystemClock.sleep(30);
        assertTrue("受控后台下载必须实际返回图片",downloaded.get()>0);
        String visible="(()=>{const i=document.querySelector('.main-player .cover img.cover');return !!i&&i.complete&&i.naturalWidth===48&&parseFloat(getComputedStyle(i).opacity)>.9})()";
        long available=SystemClock.elapsedRealtime();
        while(!"true".equals(f.js(visible))&&SystemClock.elapsedRealtime()-available<1200)SystemClock.sleep(40);
        f.capture("cold-cover-download-complete");
        assertEquals("封面字节已经下载后，应立即显示，不能继续等待较慢的重复图片请求 images="+images.get(),"true",f.js(visible));
        f.tapVisible("document.querySelector('.main-player .cover')");
        f.await("!!document.querySelector('.full-player-mobile .player-cover img.cover.loaded')");
        assertEquals("播放页也应显示同一真实冷封面","true",f.js("document.querySelector('.full-player-mobile .player-cover img.cover.loaded').naturalWidth===48"));
      } finally {
        f.js("p7Restore()");f.await("window.p7Restored===true");
        f.inst.runOnMainSync(()->f.web.setWebViewClient(original[0]));
      }
    }
  }
  @Test public void newSongPreparesFullSizeArtworkBeforeOpeningPlayer() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      try(var in=f.inst.getContext().getAssets().open("r23-player-fixture.js")){f.js(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
      android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
      bitmap.eraseColor(android.graphics.Color.rgb(106,121,168));java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
      byte[] png=bytes.toByteArray();
      java.util.concurrent.atomic.AtomicInteger largeRequests=new java.util.concurrent.atomic.AtomicInteger();
      android.webkit.WebViewClient[] original=new android.webkit.WebViewClient[1];
      f.inst.runOnMainSync(()->{original[0]=f.web.getWebViewClient();f.web.setWebViewClient(new com.getcapacitor.BridgeWebViewClient(f.activity.getBridge()) {
        @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest request) {
          if(!"new-artwork-test.invalid".equals(request.getUrl().getHost()))return super.shouldInterceptRequest(view,request);
          SystemClock.sleep(160);
          if(request.getUrl().getPath().endsWith("/large.png"))largeRequests.incrementAndGet();
          return new android.webkit.WebResourceResponse("image/png",null,200,"OK",java.util.Map.of("Access-Control-Allow-Origin","*","Cache-Control","no-store"),new java.io.ByteArrayInputStream(png));
        }
      });});
      try {
        f.js("p7Init().then(()=>{fixtureSetting.cacheEnabled=true;fixtureSetting.androidDeviceModeOverride='phone';fixtureStatus.showFullPlayer=false;fixtureStatus.showPlayBar=true;window.artworkBase='https://new-artwork-test.invalid/"+java.util.UUID.randomUUID()+"/';window.__SPLAYER_PLAYER_CONTROLLER__.setupSongUI({...p7Songs[0],type:'streaming',path:undefined,cover:artworkBase+'large.png',coverSize:{s:artworkBase+'small.png',l:artworkBase+'large.png'}},0);window.artworkReady=true})");
        f.await("window.artworkReady===true");
        long started=SystemClock.elapsedRealtime();
        while(largeRequests.get()==0&&SystemClock.elapsedRealtime()-started<1500)SystemClock.sleep(30);
        assertTrue("选中新歌时就应提前请求大封面，不能等打开播放页才开始",largeRequests.get()>0);
        f.tapVisible("document.querySelector('.main-player .cover')");
        f.await("!!document.querySelector('.full-player-mobile .player-cover img.cover.loaded')");
        assertEquals("播放页必须解码真实大封面","true",f.js("document.querySelector('.full-player-mobile .player-cover img.cover.loaded').naturalWidth===64"));
        f.capture("new-song-full-artwork-prepared");
      } finally {
        f.js("p7Restore()");f.await("window.p7Restored===true");
        f.inst.runOnMainSync(()->f.web.setWebViewClient(original[0]));
      }
    }
  }
  @Test public void loadedVideoAuthorActionsAndDescriptionDoNotOverlap() throws Exception {
    try(Compact c=new Compact()) {
      DeviceWebFixture f=c.f;
      String route=(String)new JSONTokener(f.js("fixtureRouter.currentRoute.value.fullPath")).nextValue();
      try {
        f.js("fixtureStatus.showFullPlayer=false;fixtureRouter.push('/video?id=10928968&type=mv')");
        for(int i=0;i<60&&!"true".equals(f.js("!!document.querySelector('.video .info .name')"));i++)SystemClock.sleep(500);
        f.await("!!document.querySelector('.video .info .name')");
        f.capture("video-detail-320-200");
        JSONObject g=json(f,"(()=>{const m=document.querySelector('.video .menu'),a=m.querySelector('.artist'),c=m.querySelector('.control'),d=document.querySelector('.video .desc');return {menu:m.getBoundingClientRect().toJSON(),artist:a.getBoundingClientRect().toJSON(),controls:c.getBoundingClientRect().toJSON(),description:d.getBoundingClientRect().toJSON()}})()");
        JSONObject m=g.getJSONObject("menu"),a=g.getJSONObject("artist"),b=g.getJSONObject("controls"),d=g.getJSONObject("description");
        assertTrue("作者和操作区必须完整位于菜单内: "+g,a.getDouble("top")>=m.getDouble("top")-1&&a.getDouble("bottom")<=m.getDouble("bottom")+1&&b.getDouble("top")>=m.getDouble("top")-1&&b.getDouble("bottom")<=m.getDouble("bottom")+1);
        assertTrue("视频操作区不能和简介重叠: "+g,d.getDouble("top")>=Math.max(a.getDouble("bottom"),b.getDouble("bottom"))-1);
      } finally {f.js("fixtureRouter.replace("+JSONObject.quote(route)+")");}
    }
  }
}

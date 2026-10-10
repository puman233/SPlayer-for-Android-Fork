package top.imsyy.splayer.android.security;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Executes real built consumers; only synthetic credentials in a dedicated package. */
@RunWith(AndroidJUnit4.class)
public class CredentialLogDeviceTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] result = {null};
    String guarded = "(()=>{try{return eval(" + JSONObject.quote(expression) + ")}catch(e){return 'EVAL_ERROR: '+e.message}})()";
    inst.runOnMainSync(() -> web.evaluateJavascript(guarded, v -> {result[0] = v; done.countDown();}));
    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertFalse(result[0], result[0] != null && result[0].contains("EVAL_ERROR:")); return result[0];
  }
  private void await(String expression) throws Exception {
    for (int i = 0; i < 200; i++) { if ("true".equals(js(expression))) return; SystemClock.sleep(100); }
    fail("Timed out: " + expression + "; " + js("JSON.stringify({error:window.secError,requests:window.secRequests?.length,logs:window.secLogs?.map(l=>l.args[0]),ready:window.secReady})"));
  }
  @Before public void setup() throws Exception {
    assertEquals("top.imsyy.splayer.android.debug.sec17", inst.getTargetContext().getPackageName());
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView(); await("!!window.$message && !!window.__SPLAYER_PLAYER_CONTROLLER__");
    js("window.secDone=false;window.secError=null;window.secLogs=[];window.secRequests=[];window.secRestore=null;window.secNotices=[];window.secReflectErrors=false");
    System.out.println("SEC_LOG_AUDIT pid=" + android.os.Process.myPid() + " viewport=" + js("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"));
    js("(async()=>{try{const url=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name)).name;window.secExports=Object.values(await import(url));window.secStores=secExports.find(v=>v?.useDataStore&&v?.useSettingStore);if(!secStores)throw Error('Public stores unavailable');const pinia=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia;window.secStores={useSettingStore:()=>pinia._s.get('setting'),useDataStore:()=>pinia._s.get('data'),useMusicStore:()=>pinia._s.get('music')};if(!secStores.useSettingStore().lastfm)throw Error('Setting store unavailable: '+Object.keys(secStores.useSettingStore()).join(','));const originals={};for(const name of ['log','info','warn','error','debug']){originals[name]=console[name];console[name]=(...args)=>{secLogs.push({name,args,rendered:args.map(v=>typeof v==='string'?v:JSON.stringify(v)).join(' ')});originals[name](...args)}}const originalNotice=window.$message.error;window.$message.error=(message,...args)=>{secNotices.push(String(message));return originalNotice(message,...args)};const NativeXHR=window.XMLHttpRequest,requests=new WeakMap();window.XMLHttpRequest=function(...args){const xhr=new NativeXHR(...args),open=xhr.open,send=xhr.send;xhr.open=function(method,url,...rest){requests.set(this,{method,url:String(url)});return open.call(this,method,url,...rest)};xhr.send=function(data){const req=requests.get(this);if(req&&(/playlist\\/catlist|playlist\\/highquality\\/tags|artist\\/(?:songs|detail)|comment\\/(?:new|hot)|song\\/detail|ws\\.audioscrobbler\\.com/.test(req.url))){secRequests.push({...req,data:String(data||'')});if(/playlist\\/|artist\\/|comment\\/|song\\/detail/.test(req.url)||window.secReflectErrors){Object.defineProperties(this,{status:{get:()=>window.secReflectErrors&&req.url.includes('audioscrobbler')?403:400},statusText:{get:()=>''},responseText:{get:()=>JSON.stringify({error:6,message:'fixture_token_device'})},readyState:{get:()=>4}});queueMicrotask(()=>this.dispatchEvent(new ProgressEvent('loadend')))}else queueMicrotask(()=>this.dispatchEvent(new ProgressEvent('timeout')));return;}return send.call(this,data)};return xhr};window.XMLHttpRequest.prototype=NativeXHR.prototype;window.secRestore=()=>{window.$message.error=originalNotice;window.$modal.destroyAll();window.XMLHttpRequest=NativeXHR;for(const name of Object.keys(originals))console[name]=originals[name];document.cookie='MUSIC_U=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';localStorage.removeItem('cookie-MUSIC_U');secStores.useSettingStore().lastfm.enabled=false};window.secReady=true}catch(e){window.secError=e.message}})()");
    await("!!window.secReady||!!window.secError"); assertEquals("null", js("secError||null"));
  }
  @After public void cleanup() throws Exception {
    if (web != null) js("window.secRestore?.();window.secReady=false");
    if (activity != null) inst.runOnMainSync(activity::finish);
  }
  private void assertSafe() throws Exception {
    assertEquals("false", js("secLogs.some(l=>/fixture_(cookie|token|session|secret)_device/.test(l.rendered))||secNotices.some(n=>/fixture_(cookie|token|session|secret)_device/.test(n))"));
    assertEquals("true", js("secLogs.filter(l=>l.name==='error'&&/playlist cat list|Last.fm|artist|comment/.test(l.rendered)).every(l=>l.args.every(a=>typeof a==='string'))"));
  }
  @Test public void playlistConsumerRejectsWithoutCredentialLog() throws Exception {
    js("document.cookie='MUSIC_U=fixture_cookie_device; path=/';localStorage.setItem('cookie-MUSIC_U','fixture_cookie_device');secStores.useDataStore().getPlaylistCatList().then(()=>{secError='Unexpected success';secDone=true},e=>{window.secRejected=e.response?.status===400;secDone=true})");
    await("secDone"); assertEquals("true", js("secRejected"));
    assertEquals("true", js("secRequests.some(r=>r.url.includes('playlist/catlist'))"));
    assertEquals("true", js("secLogs.some(l=>l.args[0]==='Error getting playlist cat list:'&&l.args[1]==='http')")); assertSafe();
  }
  @Test public void lastfmAuthorizationRejectsWithoutTokenLog() throws Exception {
    js("Object.assign(secStores.useSettingStore().lastfm,{apiKey:'fixture_key_device',apiSecret:'fixture_secret_device'});const get=secExports.find(v=>typeof v==='function'&&String(v).includes('auth.getSession'));if(!get){secError='Public getSession unavailable';secDone=true}else get('fixture_token_device').then(()=>{secError='Unexpected success';secDone=true},e=>{window.secRejected=e.code==='ECONNABORTED';secDone=true})");
    await("secDone"); assertEquals("null", js("secError||null")); assertEquals("true", js("secRejected"));
    assertEquals("true", js("secRequests.some(r=>r.method==='GET'&&r.url.includes('fixture_token_device'))"));
    assertEquals("true", js("secLogs.some(l=>l.args[0]==='Last.fm API 错误:'&&l.args[1]==='timeout')")); assertSafe();
  }
  @Test public void playerNowPlayingAndScheduledScrobbleDoNotRelogSession() throws Exception {
    js("Object.assign(secStores.useSettingStore().lastfm,{enabled:true,apiKey:'fixture_key_device',apiSecret:'fixture_secret_device',sessionKey:'fixture_session_device',nowPlayingEnabled:true,scrobbleEnabled:true});secStores.useSettingStore().useNextPrefetch=false;const song={id:990001,name:'Synthetic security song',type:'radio',duration:2000,artists:[{id:990002,name:'Synthetic artist'}],album:{id:990003,name:'Synthetic album'}};secStores.useMusicStore().playSong=song;window.__SPLAYER_PLAYER_CONTROLLER__.afterPlaySetup(song).catch(e=>{secError=e.message})");
    await("secLogs.some(l=>l.args[0]==='Last.fm: Scrobble 失败')||!!secError");
    assertEquals("null", js("secError||null"));
    assertEquals("2", js("secRequests.filter(r=>r.method==='POST'&&r.data.includes('sk=fixture_session_device')).length"));
    assertEquals("true", js("['Last.fm: 更新正在播放状态失败','Last.fm: Scrobble 失败'].every(label=>secLogs.some(l=>l.args[0]===label&&l.args[1]==='timeout'))")); assertSafe();
  }
  @Test public void actualArtistAndCommentPagesDoNotLeakHttpFailures() throws Exception {
    js("document.cookie='MUSIC_U=fixture_cookie_device; path=/';localStorage.setItem('cookie-MUSIC_U','fixture_cookie_device');const router=document.querySelector('#app').__vue_app__.config.globalProperties.$router;router.push('/artist/songs?id=990011').catch(()=>{})");
    await("secLogs.some(l=>l.args[0]==='Error getting artist all songs:')");
    assertEquals("true", js("secLogs.some(l=>l.args[0]==='Error getting artist all songs:'&&l.args[1]==='http')"));
    js("document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/comment?id=990012&type=0').catch(()=>{})");
    await("secLogs.some(l=>l.args[0]==='Error getting comment data:')");
    assertEquals("true", js("secRequests.some(r=>r.url.includes('comment/hot'))&&secRequests.some(r=>r.url.includes('comment/new'))"));
    assertEquals("true", js("secNotices.includes('获取评论数据失败')&&!!document.querySelector('.comment-page .list-comment')"));
    assertSafe();
  }
  @Test public void actualLastfmSettingsFailureHasSafeNoticeAndRestoresButton() throws Exception {
    js("window.secReflectErrors=true;Object.assign(secStores.useSettingStore().lastfm,{enabled:true,apiKey:'fixture_key_device',apiSecret:'fixture_secret_device',sessionKey:'',username:''});const open=secExports.find(v=>typeof v==='function'&&String(v).includes('设置页面已打开'));if(!open){secError='Public setting action unavailable'}else open('network').catch(e=>{secError='Settings failed'})");
    await("!!document.querySelector('.main-setting')||!!secError"); assertEquals("null", js("secError||null"));
    await("Array.from(document.querySelectorAll('.main-setting button')).some(b=>b.textContent.trim()==='连接账号')");
    js("Array.from(document.querySelectorAll('.main-setting button')).find(b=>b.textContent.trim()==='连接账号').click()");
    await("secLogs.some(l=>l.args[0]==='Last.fm 连接失败:')");
    assertEquals("true", js("secNotices.includes('连接 Last.fm 失败，请检查网络和 API 配置')"));
    assertEquals("true", js("Array.from(document.querySelectorAll('.main-setting button')).some(b=>b.textContent.trim()==='连接账号'&&!b.disabled&&!b.classList.contains('n-button--loading'))"));
    assertSafe();
  }
  @Test public void actualBridgePacketAndUnhandledRejectionDiagnosticsStaySafe() throws Exception {
    js("window.Capacitor.logToNative({options:{cookie:'fixture_cookie_device',url:'https://example.invalid/?token=fixture_token_device'}});window.Capacitor.logFromNative({data:{session:'fixture_session_device'}});const error=Object.assign(new Error('fixture_token_device'),{cause:{config:{data:'fixture_session_device'}}});const event=new PromiseRejectionEvent('unhandledrejection',{promise:Promise.resolve(),reason:error,cancelable:true});window.dispatchEvent(event);window.secPrevented=event.defaultPrevented");
    assertEquals("true", js("secPrevented"));
    assertEquals("true", js("secLogs.some(l=>l.args[0]==='[Native bridge] request')&&secLogs.some(l=>l.args[0]==='[Unhandled rejection]'&&l.args[1]==='unknown')"));
    assertSafe();
  }
  @Test public void sdkOrphanCallbackAndWindowErrorCannotLeakReflectedCredentials() throws Exception {
    js("Capacitor.fromNative({callbackId:'missing-security-fixture',pluginId:'SecurityFixture',success:false,error:{message:'fixture_token_device',cause:{config:{data:'fixture_session_device'}}}});window.onerror('fixture_token_device','https://example.invalid/?token=fixture_token_device',1,2,new Error('fixture_cookie_device'))");
    js("window.secDone=false;Capacitor.nativeCallback('App','getInfo',{},()=>{window.secDone=true;throw 'fixture_token_device'})");
    await("secDone");
    assertSafe();
  }
  @Test public void nativeResolverMalformedResponsesDoNotExposeCookieOrReflectedBody() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (java.net.ServerSocket server = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(10000);
      Future<Integer> responses = executor.submit(() -> {
        int count = 0;
        for (int i = 0; i < 5; i++) {
          try (java.net.Socket socket = server.accept()) {
            socket.setSoTimeout(10000);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder request = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) request.append(line).append('\n');
            assertTrue("Synthetic credential reached isolated HTTP server", request.toString().contains("fixture_cookie_device"));
            byte[] body = "{fixture_token_device".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            socket.getOutputStream().write(body); socket.getOutputStream().flush(); count++;
          }
        }
        return count;
      });
      top.imsyy.splayer.android.playback.PlaybackUrlResolver resolver = new top.imsyy.splayer.android.playback.PlaybackUrlResolver();
      resolver.updateContext("http://127.0.0.1:" + server.getLocalPort(), "fixture_cookie_device", "dolby", false, false);
      assertNull(resolver.resolveSync(990101));
      assertEquals(Integer.valueOf(5), responses.get(15, TimeUnit.SECONDS));
      System.out.println("SEC_NATIVE_REQUESTS=5; malformed responses safely rejected");
    } finally { executor.shutdownNow(); }
  }
  @Test public void normalEmbeddedApiStartsWithFreshBuiltResources() throws Exception {
    js("window.secHealth=null;(async()=>{try{const response=await fetch('http://127.0.0.1:1145/api');const body=await response.json();window.secHealth=response.status===200&&body.name==='SPlayer API'}catch{window.secHealth=false}})()");
    await("window.secHealth!==null"); assertEquals("true", js("secHealth"));
  }
  @Test public void sdkPacketLoggingIsDisabledWhileApplicationDiagnosticsRemainAvailable() throws Exception {
    assertFalse("SDK packet/cookie logging must not serialize credentials", com.getcapacitor.Logger.shouldLog());
    js("console.info('[Application diagnostic] fixture boundary active')");
    assertEquals("true", js("secLogs.some(l=>l.args[0]==='[Application diagnostic] fixture boundary active')"));
  }

}

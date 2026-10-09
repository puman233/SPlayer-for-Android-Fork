package top.imsyy.splayer.android.download;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Real WebView -> Capacitor -> HTTP -> SAF integration, restricted to the isolated Debug APK. */
@RunWith(AndroidJUnit4.class)
public class DownloadDeviceRegressionTest {
  @Rule public final org.junit.rules.TestName testName = new org.junit.rules.TestName();
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private Uri tree;
  private String root;
  private Server server;
  private final JSONObject evidence = new JSONObject();
  private final Uri provider = Uri.parse("content://" + DownloadFixtureProvider.AUTHORITY);
  private String viewport() throws Exception {
    return (String)value("JSON.stringify({innerWidth,innerHeight,devicePixelRatio,visualViewport:{width:visualViewport.width,height:visualViewport.height,scale:visualViewport.scale},screen:{width:screen.width,height:screen.height},userAgent:navigator.userAgent})");
  }
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] result = new String[1];
    inst.runOnMainSync(() -> web.evaluateJavascript(expression, v -> {result[0]=v;done.countDown();}));
    assertTrue("WebView callback", done.await(10, TimeUnit.SECONDS)); return result[0];
  }
  private Object value(String expression) throws Exception { return new JSONTokener(js(expression)).nextValue(); }
  private void await(String expression) throws Exception {
    for (int i=0;i<200;i++) { if ("true".equals(js(expression))) return; SystemClock.sleep(100); }
    fail("Condition timed out: " + expression + "; " + js("JSON.stringify({results:window.drResults,setup:window.drError,requests:window.drRequests,songs:window.drData?.downloadingSongs})"));
  }
  private Bundle configure(boolean failWrite, int delay) {
    Bundle b=new Bundle();b.putBoolean("failWrite",failWrite);b.putInt("deleteDelay",delay);
    return inst.getTargetContext().getContentResolver().call(tree!=null?tree:provider,"fixture:configure",null,b);
  }
  @Before public void setup() throws Exception {
    assertEquals("top.imsyy.splayer.android.debug",inst.getTargetContext().getPackageName());
    root=InstrumentationRegistry.getArguments().getString("safetyRoot","run-"+UUID.randomUUID());
    assertTrue("Only isolated UUID fixture roots",root.matches("run-[a-f0-9-]{36}"));
    evidence.put("test",testName.getMethodName());
    inst.getUiAutomation().adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
    try { inst.getTargetContext().getContentResolver().call(provider,InstrumentationRegistry.getArguments().containsKey("safetyRoot")?"fixture:grant":"fixture:root",root,new Bundle()); }
    finally { inst.getUiAutomation().dropShellPermissionIdentity(); }
    tree=DocumentsContract.buildTreeDocumentUri(DownloadFixtureProvider.AUTHORITY,root);
    configure(false,0); server=new Server();
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web=activity.getBridge().getWebView(); await("!!document.querySelector('#app')?.__vue_app__");
    // Mount precedes asynchronous persistence restoration. Do not let startup replace fixture state.
    await("window.__SPLAYER_PLAYER_CONTROLLER__?.currentRequestToken>0&&!Reflect.ownKeys(document.querySelector('#app').__vue_app__._context.provides).map(k=>document.querySelector('#app').__vue_app__._context.provides[k]).find(v=>v?._s instanceof Map)._s.get('status').playLoading");
    js("(()=>{const a=document.querySelector('#app').__vue_app__;window.drPinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.drSetting=drPinia._s.get('setting');window.drData=drPinia._s.get('data');window.drSaved={};const options={androidDownloadDirectoryUri:"+JSONObject.quote(tree.toString())+",downloadPath:'DeviceFixture',downloadMeta:false,downloadLyric:false,downloadMakeYrc:false,downloadSaveAsAss:false,folderStrategy:'none',fileNameFormat:'title',usePlaybackForDownload:false,useUnlockForDownload:false,userAgreementVersion:'v2.0',checkUpdateOnStart:false};for(const[k,v]of Object.entries(options)){drSaved[k]=drSetting[k];drSetting[k]=v;}window.$modal?.destroyAll();window.drResults={};window.drRequests=[];window.drNative=window.Capacitor.nativePromise;window.Capacitor.nativePromise=function(p,m,o){if(p==='AndroidDownload'&&m==='downloadFile'){const r={...o,start:performance.now(),settled:false};drRequests.push(r);return drNative.call(this,p,m,o).then(v=>{r.settled=true;r.result=v;r.end=performance.now();return v},e=>{r.settled=true;r.error=e.message;r.end=performance.now();throw e});}return drNative.call(this,p,m,o)};})()");
    String viewport=(String)value("JSON.stringify({innerWidth,innerHeight,devicePixelRatio,visualViewport:{width:visualViewport.width,height:visualViewport.height,scale:visualViewport.scale},screen:{width:screen.width,height:screen.height},userAgent:navigator.userAgent})");
    evidence.put("viewport",new JSONObject(viewport)); evidence.put("tree",tree.toString());
  }
  @After public void teardown() throws Exception {
    if (web!=null) {
      js("window.drNative.call(window.Capacitor,'AndroidDownload','resetDownloads',{}).then(()=>window.drReset=true)");
      await("window.drReset===true");
      if ("true".equals(js("!!window.drManager"))) {
        js("drManager.removeDownload(99000001);drManager.removeDownload(99000002)");
      }
      evidence.put("requests",new JSONArray((String)value("JSON.stringify(drRequests)")));
      evidence.put("results",new JSONObject((String)value("JSON.stringify(drResults)")));
      File dir=new File(activity.getExternalFilesDir(null),"r01-r02-device"); assertTrue(dir.exists()||dir.mkdirs());
      try(FileOutputStream out=new FileOutputStream(new File(dir,root+".json"))){out.write(evidence.toString(2).getBytes(StandardCharsets.UTF_8));}
      js("(()=>{if(window.drXhrOpen)XMLHttpRequest.prototype.open=drXhrOpen;if(window.drXhrSend)XMLHttpRequest.prototype.send=drXhrSend;window.Capacitor.nativePromise=drNative;for(const[k,v]of Object.entries(drSaved))drSetting[k]=v;})()");
      inst.runOnMainSync(activity::finish);
    }
    if(server!=null)server.close(); configure(false,0);
  }
  private String download(String key,String mode,String name,String sub) throws Exception {
    JSONObject opts=new JSONObject().put("taskId","device-"+UUID.randomUUID()).put("url",server.url(mode)).put("fileName",name).put("directoryUri",tree).put("subPath",sub);
    js("window.Capacitor.nativePromise('AndroidDownload','downloadFile',"+opts+").then(v=>drResults["+JSONObject.quote(key)+"]={ok:true,...v},e=>drResults["+JSONObject.quote(key)+"]={ok:false,message:e.message})");
    return opts.getString("taskId");
  }
  private JSONObject result(String key) throws Exception {
    await("!!drResults["+JSONObject.quote(key)+"]");return new JSONObject((String)value("JSON.stringify(drResults["+JSONObject.quote(key)+"] )"));
  }
  private Uri doc(String name) { return DocumentsContract.buildDocumentUriUsingTree(tree,root+"/"+name); }
  private byte[] read(String name) throws Exception {
    try(InputStream in=inst.getTargetContext().getContentResolver().openInputStream(doc(name));ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
    }
  }
  private List<String> names() {
    List<String> list=new ArrayList<>();
    try(android.database.Cursor c=inst.getTargetContext().getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,root),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
      while(c.moveToNext())list.add(c.getString(0));
    } return list;
  }
  private void put(String name,byte[] bytes) {
    Bundle b=new Bundle();b.putByteArray("bytes",bytes);
    inst.getTargetContext().getContentResolver().call(tree,"fixture:put",root+"/"+name,b);
  }
  @Test public void nativeCancellationStopsSocketAndWritesAndIsIdempotent() throws Exception {
    String task=download("cancel","slow","cancel.wav",""); await("drRequests.length===1");
    assertTrue(server.started.await(10,TimeUnit.SECONDS)); SystemClock.sleep(250);
    js("window.Capacitor.nativePromise('AndroidDownload','cancelDownload',{taskId:"+JSONObject.quote(task)+"}).then(v=>drResults.cancelAck=v)");
    assertEquals("cancelled",result("cancel").getString("status"));
    assertTrue("Actual server socket must stop",server.stopped.await(5,TimeUnit.SECONDS));
    int written=server.sent.get();SystemClock.sleep(250);assertEquals(written,server.sent.get());assertTrue(names().isEmpty());
    js("window.Capacitor.nativePromise('AndroidDownload','cancelDownload',{taskId:"+JSONObject.quote(task)+"}).then(v=>drResults.secondCancel=v)");
    assertEquals("finished",result("secondCancel").getString("status"));
    evidence.put("serverBytesAfterCancel",written);evidence.put("filesAfterCancel",new JSONArray(names()));
  }
  @Test public void managerCancellationRetainsSlotAndReaddedSongGetsNewIdentity() throws Exception {
    configure(false,600);
    // Import the actual already-built shared module; discover exports by the public manager API.
    js("(async()=>{try{const u=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name)).name;const m=await import(u);window.drManager=Object.values(m).flatMap(v=>typeof v==='function'&&/^\\(\\)=>[\\w$]+$/.test(Function.prototype.toString.call(v))?[v()]:[v]).find(v=>v&&Object.prototype.hasOwnProperty.call(v,'queue')&&typeof v.addDownload==='function'&&typeof v.removeDownload==='function');if(!drManager)throw Error('Manager export not found');}catch(e){window.drError=e.message}})()");
    await("!!window.drManager || !!window.drError");assertEquals("null",js("window.drError||null"));
    // Replace only the song-URL HTTP boundary; all queue, native, IO and progress code is real.
    js("(()=>{try{window.drXhrOpen=XMLHttpRequest.prototype.open;window.drXhrSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u,...a){this.drUrl=u;return drXhrOpen.call(this,m,u,...a)};XMLHttpRequest.prototype.send=function(...a){if(String(this.drUrl).includes('/song/download/url')){const response=JSON.stringify({code:200,data:{url:"+JSONObject.quote(server.url("slow"))+",type:'wav'}});for(const[k,v]of Object.entries({readyState:4,status:200,responseText:response,response})){Object.defineProperty(this,k,{configurable:true,value:v})}setTimeout(()=>{this.onload?.(new Event('load'));this.onloadend?.(new Event('loadend'))},0);return}return drXhrSend.apply(this,a)};window.drSong={id:99000001,name:'DeviceCancel',artists:'FixtureArtist',album:'FixtureAlbum',duration:2000};drManager.addDownload(drSong,'h').catch(e=>window.drError=e.stack);window.drData=drPinia._s.get('data');}catch(e){window.drError=e.stack}})()");
    await("drRequests.length===1 || !!window.drError");assertEquals("null",js("window.drError||null"));assertTrue(server.started.await(10,TimeUnit.SECONDS)); SystemClock.sleep(250);
    js("drManager.removeDownload(drSong.id);drManager.addDownload(drSong,'h');drManager.addDownload({...drSong,id:99000002,name:'QueuedDelete'},'h');drManager.removeDownload(99000002)");
    SystemClock.sleep(150);assertEquals("1",js("drRequests.length"));
    await("drRequests.length===2");
    JSONObject first=new JSONObject((String)value("JSON.stringify(drRequests[0])"));JSONObject second=new JSONObject((String)value("JSON.stringify(drRequests[1])"));
    assertNotEquals(first.getString("taskId"),second.getString("taskId"));
    assertTrue(first.getBoolean("settled"));assertTrue(second.getDouble("start")>=first.getDouble("end"));
    assertEquals("true",js("drData.downloadingSongs.some(t=>t.song.id===99000001&&t.status==='downloading')"));
    js("drManager.removeDownload(99000001)"); await("drRequests.every(r=>r.settled)");SystemClock.sleep(250);
    assertEquals("2",js("drRequests.length"));assertTrue(names().isEmpty());
    evidence.put("slotReleasedOnlyAfterSettlement",true);evidence.put("queuedTaskNeverRequested",true);
  }
  @Test public void recreatedActivityWaitsForOldNativeCleanupBeforeNewSession() throws Exception {
    configure(false,5000);download("oldSession","slow","old.wav","");
    assertTrue(server.started.await(10,TimeUnit.SECONDS));SystemClock.sleep(250);
    assertFalse(names().isEmpty());
    String saved=(String)value("JSON.stringify(drSaved)");
    evidence.put("destroyedSessionRequests",new JSONArray((String)value("JSON.stringify(drRequests)")));
    MainActivity previous=activity;inst.runOnMainSync(previous::finish);inst.waitForIdleSync();
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web=activity.getBridge().getWebView();await("!!document.querySelector('#app')?.__vue_app__");
    js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.drSetting=p._s.get('setting');window.drSaved="+saved+";window.drNative=window.Capacitor.nativePromise;window.drResults={};window.drRequests=[];window.Capacitor.nativePromise('AndroidDownload','resetDownloads',{}).then(()=>drResults.newSessionReset={ok:true});})()");
    result("newSessionReset");assertTrue("New plugin must wait for old provider IO cleanup",names().isEmpty());
    assertTrue(server.stopped.await(5,TimeUnit.SECONDS));evidence.put("oldActivityCleanupCompleted",true);
  }
  @Test public void safIntegrityFailuresRetryAndUnknownLengthFraming() throws Exception {
    download("short","short","retry.wav","");assertFalse(result("short").getBoolean("ok"));assertTrue(names().isEmpty());
    download("retry","normal","retry.wav","");assertEquals("success",result("retry").getString("status"));assertArrayEquals(Server.wav,read("retry.wav"));
    download("chunked","chunked","chunked.wav","");assertEquals("success",result("chunked").getString("status"));assertArrayEquals(Server.wav,read("chunked.wav"));
    download("truncatedChunk","truncatedChunk","broken.wav","");assertFalse(result("truncatedChunk").getBoolean("ok"));assertFalse(names().contains("broken.wav"));
    download("unknown","unknown","unknown.wav","");assertFalse(result("unknown").getBoolean("ok"));assertFalse(names().contains("unknown.wav"));
    configure(true,0);download("writeFail","normal","write.wav","");assertFalse(result("writeFail").getBoolean("ok"));assertFalse(names().contains("write.wav"));
    configure(false,0);download("writeRetry","normal","write.wav","");assertEquals("success",result("writeRetry").getString("status"));assertArrayEquals(Server.wav,read("write.wav"));
    assertEquals(0,configure(false,0).getInt("renameCalls"));
    evidence.put("safRenameUnsupported",true);evidence.put("remainingFiles",new JSONArray(names()));
  }
  @Test public void existingFilesAreVerifiedPreservedAndPlayableAndScanWorks() throws Exception {
    byte[] fragment=new byte[]{1,2,3};put("fragment.wav",fragment);
    download("fragment","normal","fragment.wav","");assertFalse(result("fragment").getBoolean("ok"));assertArrayEquals(fragment,read("fragment.wav"));
    put("valid.wav",Server.wav);download("valid","normal","valid.wav","");assertEquals("skipped",result("valid").getString("status"));assertArrayEquals(Server.wav,read("valid.wav"));
    download("folders","normal","Artist - Title.wav","Artist/Album");assertEquals("success",result("folders").getString("status"));
    js("window.Capacitor.nativePromise('AndroidDownload','listDownloadedSongs',{directoryUri:"+JSONObject.quote(tree.toString())+"}).then(v=>drResults.scan=v)");
    assertTrue(result("scan").getJSONArray("songs").length()>=2);
    android.media.MediaPlayer player=new android.media.MediaPlayer();
    try { player.setDataSource(inst.getTargetContext(),doc("valid.wav"));player.setVolume(0,0);player.prepare();player.start();SystemClock.sleep(300);assertTrue(player.getCurrentPosition()>0);evidence.put("wavPlaybackPositionMs",player.getCurrentPosition()); } finally {player.release();}
    js("window.Capacitor.nativePromise('AndroidDownload','writeTextFile',{directoryUri:"+JSONObject.quote(tree.toString())+",fileName:'valid.lrc',content:'[00:00.00]Device lyric fixture'}).then(v=>drResults.lyric=v)");
    assertEquals("success",result("lyric").getString("status"));assertEquals("[00:00.00]Device lyric fixture",new String(read("valid.lrc"),StandardCharsets.UTF_8));
    // A durable unfinished marker must also hide an audio document from both scanner entry points.
    put("pending.wav",new byte[]{7,8});
    byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest("pending.wav".getBytes(StandardCharsets.UTF_8));
    StringBuilder marker=new StringBuilder(".splayer-");for(byte b:digest)marker.append(String.format(Locale.ROOT,"%02x",b&255));marker.append(".pending");
    put(marker.toString(),doc("pending.wav").toString().getBytes(StandardCharsets.UTF_8));
    js("window.Capacitor.nativePromise('AndroidDownload','scanLocalMusic',{directories:[{uri:"+JSONObject.quote(tree.toString())+"}]}).then(v=>drResults.localScan=v)");
    JSONArray scanned=result("localScan").getJSONArray("songs");
    for(int i=0;i<scanned.length();i++)assertNotEquals("pending.wav",scanned.getJSONObject(i).getString("fileName"));
    download("pendingRetry","normal","pending.wav","");assertFalse(result("pendingRetry").getBoolean("ok"));assertArrayEquals(new byte[]{7,8},read("pending.wav"));
    String secondRoot="run-"+UUID.randomUUID();
    inst.getUiAutomation().adoptShellPermissionIdentity("android.permission.MANAGE_DOCUMENTS");
    try{inst.getTargetContext().getContentResolver().call(provider,"fixture:root",secondRoot,new Bundle());}finally{inst.getUiAutomation().dropShellPermissionIdentity();}
    String savedRoot=root;Uri savedTree=tree;
    try{root=secondRoot;tree=DocumentsContract.buildTreeDocumentUri(DownloadFixtureProvider.AUTHORITY,root);download("directorySwitch","normal","switched.wav","");assertEquals("success",result("directorySwitch").getString("status"));assertArrayEquals(Server.wav,read("switched.wav"));}
    finally{root=savedRoot;tree=savedTree;}
    evidence.put("userFragmentPreserved",true);
  }
  private void restartFixtureActivity() throws Exception {
    String saved=(String)value("JSON.stringify(drSaved)");
    MainActivity old=activity;inst.runOnMainSync(old::finish);inst.waitForIdleSync();
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web=activity.getBridge().getWebView();await("!!document.querySelector('#app')?.__vue_app__");
    await("window.__SPLAYER_PLAYER_CONTROLLER__?.currentRequestToken>0&&!Reflect.ownKeys(document.querySelector('#app').__vue_app__._context.provides).map(k=>document.querySelector('#app').__vue_app__._context.provides[k]).find(v=>v?._s instanceof Map)._s.get('status').playLoading");
    js("(()=>{const a=document.querySelector('#app').__vue_app__,p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.drSetting=p._s.get('setting');window.drSaved="+saved+";window.drNative=window.Capacitor.nativePromise;window.drRequests=[];window.drResults={};})()");
  }
  private void assertHidden(String file,String key) throws Exception {
    js("window.Capacitor.nativePromise('AndroidDownload','scanLocalMusic',{directories:[{uri:"+JSONObject.quote(tree.toString())+"}]}).then(v=>drResults["+JSONObject.quote(key)+"]=v)");
    JSONArray songs=result(key).getJSONArray("songs");
    for(int i=0;i<songs.length();i++)assertNotEquals(file,songs.getJSONObject(i).getString("fileName"));
    evidence.put(key,songs);
  }
  @Test public void replacedSameUriSurvivesRestartAndFailedRetry() throws Exception {
    String phase=InstrumentationRegistry.getArguments().getString("safetyPhase","");
    if(!phase.equals("verify")) {
      put("replaced.wav",new byte[]{1,2,3});
      put(DownloadTransfer.pendingName("replaced.wav"),doc("replaced.wav").toString().getBytes(StandardCharsets.UTF_8));
      Bundle b=new Bundle();b.putByteArray("bytes",Server.wav);
      inst.getTargetContext().getContentResolver().call(tree,"fixture:replace",root+"/replaced.wav",b);
    }
    if(phase.equals("prepare")){System.out.println("SAF_SAFETY_ROOT="+root);evidence.put("preparedRoot",root);return;}
    restartFixtureActivity();
    download("offlineRetry","short","replaced.wav","");assertFalse(result("offlineRetry").getBoolean("ok"));
    assertArrayEquals(Server.wav,read("replaced.wav"));assertHidden("replaced.wav","hiddenAfterFailedRetry");
    download("verifiedRetry","normal","replaced.wav","");assertEquals("skipped",result("verifiedRetry").getString("status"));
    assertArrayEquals(Server.wav,read("replaced.wav"));
    evidence.put("sameUriUserBytesPreserved",true);
  }
  @Test public void failedMetadataAndDeleteKeepFragmentHiddenAfterRestart() throws Exception {
    String phase=InstrumentationRegistry.getArguments().getString("safetyPhase","");
    if(!phase.equals("verify")) {
      put("unrelated.wav",Server.wav);
      Bundle fault=new Bundle();fault.putBoolean("failAudioName",true);fault.putBoolean("failAudioDelete",true);
      inst.getTargetContext().getContentResolver().call(tree,"fixture:configure",null,fault);
      download("metadataFail","normal","fragment.wav","");assertFalse(result("metadataFail").getBoolean("ok"));
      configure(false,0);
    }
    assertTrue(names().contains("fragment.wav"));assertTrue(names().contains(DownloadTransfer.pendingName("fragment.wav")));
    assertEquals(doc("fragment.wav").toString(),journalUri(read(DownloadTransfer.pendingName("fragment.wav"))));
    if(phase.equals("prepare")){System.out.println("SAF_SAFETY_ROOT="+root);evidence.put("preparedRoot",root);return;}
    restartFixtureActivity();assertHidden("fragment.wav","hiddenAfterRestart");
    download("fragmentRetry","normal","fragment.wav","");assertFalse(result("fragmentRetry").getBoolean("ok"));
    assertEquals(0,read("fragment.wav").length);assertArrayEquals(Server.wav,read("unrelated.wav"));
    assertHidden("fragment.wav","hiddenAfterRetry");evidence.put("metadataAndDeleteFaultProtection",true);
  }
  @Test public void providerRenamedAudioRemainsProtectedByUriAfterRestart() throws Exception {
    Bundle fault=new Bundle();fault.putBoolean("renameAudioOnCreate",true);
    inst.getTargetContext().getContentResolver().call(tree,"fixture:configure",null,fault);
    download("renamedFail","normal","requested.wav","");assertFalse(result("renamedFail").getBoolean("ok"));
    configure(false,0);assertTrue(names().contains("renamed.wav"));
    restartFixtureActivity();assertHidden("renamed.wav","renamedHiddenAfterRestart");
    download("renamedRetry","normal","requested.wav","");assertFalse(result("renamedRetry").getBoolean("ok"));
    assertEquals(0,read("renamed.wav").length);assertFalse(names().contains("requested.wav"));
    evidence.put("renamedUriProtected",true);
  }
  private String journalUri(byte[] bytes) throws Exception {
    String[] frame=new String(bytes,StandardCharsets.UTF_8).split("\n",-1);
    assertEquals(4,frame.length);assertEquals("SPLAYER-PENDING-1",frame[0]);
    byte[] payload=java.util.Base64.getDecoder().decode(frame[1]);
    StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(payload))hash.append(String.format(Locale.ROOT,"%02x",b&255));
    assertEquals(hash.toString(),frame[2]);return new String(payload,StandardCharsets.UTF_8);
  }
  @Test public void corruptJournalQuarantinesAudioWithoutChangingItsBytes() throws Exception {
    put("renamed.wav",new byte[]{8,9});put("user.wav",Server.wav);
    put(DownloadTransfer.pendingName("requested.wav"),"SPLAYER-PENDING-1\nY29udGVudDovLw==\nbroken\n".getBytes(StandardCharsets.UTF_8));
    assertHidden("renamed.wav","corruptRenamedHidden");assertHidden("user.wav","corruptDirectoryQuarantine");
    download("corruptRetry","short","requested.wav","");assertFalse(result("corruptRetry").getBoolean("ok"));
    assertArrayEquals(Server.wav,read("user.wav"));assertArrayEquals(new byte[]{8,9},read("renamed.wav"));
    evidence.put("corruptJournalPreservesBytes",true);
  }
  @Test public void recordRealPortraitAndLandscapeViewportWithoutUiChanges() throws Exception {
    int orientation=activity.getRequestedOrientation();
    try {
      inst.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      await("innerHeight>innerWidth");SystemClock.sleep(300);evidence.put("portraitViewport",new JSONObject(viewport()));capture("portrait");
      inst.runOnMainSync(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      await("innerWidth>innerHeight");SystemClock.sleep(300);evidence.put("landscapeViewport",new JSONObject(viewport()));capture("landscape");
    } finally {inst.runOnMainSync(()->activity.setRequestedOrientation(orientation));}
  }
  private void capture(String suffix) throws Exception {
    CountDownLatch done=new CountDownLatch(1);android.graphics.Bitmap[] b=new android.graphics.Bitmap[1];int[] status=new int[1];
    inst.runOnMainSync(()->{android.view.View v=activity.getWindow().getDecorView();b[0]=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);android.view.PixelCopy.request(activity.getWindow(),b[0],r->{status[0]=r;done.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));});
    assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,status[0]);
    File dir=new File(activity.getExternalFilesDir(null),"r01-r02-device");assertTrue(dir.exists()||dir.mkdirs());
    try(FileOutputStream out=new FileOutputStream(new File(dir,root+"-"+suffix+".png"))){assertTrue(b[0].compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b[0].recycle();}
  }
  static final class Server implements AutoCloseable {
    static final byte[] wav=wave();
    final ServerSocket socket;
    final ExecutorService workers=Executors.newCachedThreadPool();
    final CountDownLatch started=new CountDownLatch(1),stopped=new CountDownLatch(1);
    final AtomicInteger sent=new AtomicInteger();
    volatile boolean closed;
    Server() throws IOException {socket=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));workers.execute(()->{while(!closed){try{Socket s=socket.accept();workers.execute(()->serve(s));}catch(IOException e){if(!closed)throw new RuntimeException(e);}}});}
    String url(String mode){return "http://127.0.0.1:"+socket.getLocalPort()+"/"+mode;}
    void serve(Socket s) {
      boolean slow=false;
      try(Socket connection=s){
        BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));String request=in.readLine();String h;while((h=in.readLine())!=null&&!h.isEmpty()){}
        String mode=request.split(" ")[1].substring(1);slow=mode.equals("slow");
        OutputStream out=s.getOutputStream();
        boolean chunk=mode.equals("chunked")||mode.equals("truncatedChunk");
        String framing=chunk?"Transfer-Encoding: chunked\r\n":mode.equals("unknown")?"":"Content-Length: "+(slow?10485760:mode.equals("short")?wav.length+1000:wav.length)+"\r\n";
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\n"+framing+"Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();
        if(slow){started.countDown();byte[] b=new byte[8192];for(int i=0;i<1280&&!closed;i++){out.write(b);out.flush();sent.addAndGet(b.length);SystemClock.sleep(20);}}
        else if(chunk){out.write((Integer.toHexString(wav.length)+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(wav);if(mode.equals("chunked"))out.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));}
        else out.write(wav);
        out.flush();
      }catch(IOException ignored){/* A real peer disconnect is expected for cancellation. */}finally{if(slow)stopped.countDown();}
    }
    @Override public void close() throws IOException {closed=true;socket.close();workers.shutdownNow();}
    static byte[] wave(){
      java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(32044).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(32036).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(32000);
      while(b.hasRemaining())b.putShort((short)0);return b.array();
    }
  }
}

package top.imsyy.splayer.android.account;

import static org.junit.Assert.*;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Instrumentation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Synthetic IDs in the isolated debug app only; never reads or logs real credentials. */
@RunWith(AndroidJUnit4.class)
public class AccountIsolationDeviceTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch done = new CountDownLatch(1); String[] value = {null};
    inst.runOnMainSync(() -> web.evaluateJavascript(expression, result -> { value[0] = result; done.countDown(); }));
    assertTrue(done.await(10, TimeUnit.SECONDS)); return value[0];
  }
  private void await(String expression) throws Exception {
    for (int i=0;i<200;i++) { if ("true".equals(js(expression))) return; SystemClock.sleep(100); }
    fail("Timed out: " + expression);
  }
  @Before public void setup() throws Exception {
    assertEquals("top.imsyy.splayer.android.debug.sec17", inst.getTargetContext().getPackageName());
    activity = (MainActivity) inst.startActivitySync(new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web = activity.getBridge().getWebView(); await("!!document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.get('data')");
    js("window.qPinia=document.querySelector('#app').__vue_app__.config.globalProperties.$pinia;window.qData=qPinia._s.get('data');window.qOriginals=['data','music','status'].map(id=>[id,JSON.parse(JSON.stringify(qPinia._s.get(id).$state))]);window.qReady=false;window.qFailed=false;window.qRestore=()=>qOriginals.forEach(([id,state])=>Object.assign(qPinia._s.get(id).$state,state));window.qAccountA=990000000000+Date.now()%100000000;window.qAccountB=qAccountA+1");
    System.out.println("ACCOUNT_ISOLATION viewport=" + js("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"));
  }
  @After public void cleanup() throws Exception {
    try {
      if (web!=null) {
        js("window.qClean=false;(async()=>{try{await window.qStreamingRestore?.()}finally{window.qRestore?.();window.qClean=true}})()");
        await("qClean");
      }
    } finally {
      if (activity!=null) inst.runOnMainSync(activity::finish);
    }
  }
  @Test public void actualIndexedDbKeepsAccountLikesSeparateAndRestoresOffline() throws Exception {
    js("(async()=>{try{await qData.clearUserData();qData.userLoginStatus=true;qData.userData={userId:qAccountA,name:'Synthetic A'};await qData.setUserLikeData('songs',[99001311]);await qData.setCloudPlayList([{id:99001312,name:'Synthetic A cloud'}]);await qData.clearUserData();qData.userLoginStatus=true;qData.userData={userId:qAccountB,name:'Synthetic B'};await qData.loadAccountData();window.qEmpty=qData.userLikeData.songs.length===0&&qData.cloudPlayList.length===0;await qData.setUserLikeData('songs',[99001321]);await qData.clearUserData();qData.userLoginStatus=true;qData.userData={userId:qAccountA,name:'Synthetic A'};await qData.loadAccountData();window.qOwn=qData.userLikeData.songs[0]===99001311&&qData.cloudPlayList[0].id===99001312;qReady=true}catch(e){window.qFailure={stage:window.qStage||'account',type:['TypeError','ReferenceError','Error'].includes(e?.name)?e.name:'unknown'};qFailed=true}})()");
    await("qReady||qFailed"); assertEquals(js("JSON.stringify(window.qFailure||null)"),"false",js("qFailed")); assertEquals("true",js("qEmpty&&qOwn"));
  }
  @Test public void staleAccountWriteAndLocalLogoutPreserveNonAccountData() throws Exception {
    js("(async()=>{try{await qData.clearUserData();qData.userLoginStatus=true;qData.userData={userId:qAccountA,name:'Synthetic A'};qData.localPlayList=[{id:99001399}];qData.downloadingSongs=[{song:{id:99001398}}];qData.userList=[{userId:99001397,name:'Synthetic saved account'}];const old=qData.getAccountToken();await qData.clearUserData();qData.userLoginStatus=true;qData.userData={userId:qAccountB,name:'Synthetic B'};await qData.setUserLikeData('songs',[99001321]);await qData.setUserLikeData('songs',[99001311],old);window.qLate=qData.userLikeData.songs[0]===99001321;await qData.clearUserData();window.qKept=qData.localPlayList[0].id===99001399&&qData.downloadingSongs[0].song.id===99001398&&qData.userList[0].userId===99001397&&!qData.userLoginStatus;qReady=true}catch(e){window.qFailure={stage:window.qStage||'account',type:['TypeError','ReferenceError','Error'].includes(e?.name)?e.name:'unknown'};qFailed=true}})()");
    await("qReady||qFailed"); assertEquals(js("JSON.stringify(window.qFailure||null)"),"false",js("qFailed")); assertEquals("true",js("qLate&&qKept"));
  }
  @Test public void builtStreamingStoreRejectsLateResponsesFromAnotherServer() throws Exception {
    js("(async()=>{try{window.qStage='module';const resource=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name));const exports=Object.values(await import(resource.name));const publicStores=exports.find(v=>v&&Object.prototype.hasOwnProperty.call(v,'useStreamingStore')&&typeof v.useStreamingStore==='function');if(!publicStores)throw Error('Public stores unavailable');window.qStage='store';const store=publicStores.useStreamingStore();window.qStage='snapshot';const original={servers:JSON.parse(JSON.stringify(store.servers.value)),active:store.activeServerId.value,status:store.connectionStatus.value,songs:store.songs.value,artists:store.artists.value,albums:store.albums.value,playlists:store.playlists.value};const fetchOriginal=window.fetch;window.qStreamingRestore=async()=>{store.disconnect();window.fetch=fetchOriginal;store.servers.value=original.servers;store.activeServerId.value=original.active;store.connectionStatus.value=original.status;store.songs.value=original.songs;store.artists.value=original.artists;store.albums.value=original.albums;store.playlists.value=original.playlists;await store.saveServers()};let finishOld;window.fetch=(input,options)=>{const url=new URL(typeof input==='string'?input:input.url);if(!['fixture-a.invalid','fixture-b.invalid'].includes(url.hostname))return fetchOriginal(input,options);const response=body=>new Response(JSON.stringify({'subsonic-response':{status:'ok',version:'1.16.1',...body}}),{status:200,headers:{'Content-Type':'application/json'}});if(url.pathname.endsWith('/ping'))return Promise.resolve(response({}));if(url.pathname.endsWith('/getPlaylist')){const body={playlist:{id:'same',name:'Synthetic',entry:[{id:url.hostname==='fixture-a.invalid'?'A-song':'B-song',title:'Synthetic song'}]}};if(url.hostname==='fixture-a.invalid')return new Promise(resolve=>{finishOld=()=>resolve(response(body))});return Promise.resolve(response(body))}return Promise.reject(Error('Unexpected fixture request'))};window.qStage='create';const a=await store.addServer({name:'Synthetic A',type:'subsonic',url:'https://fixture-a.invalid',username:'fixture',password:'fixture-password'});const b=await store.addServer({name:'Synthetic B',type:'subsonic',url:'https://fixture-b.invalid',username:'fixture',password:'fixture-password'});window.qStage='connect-A';await store.connectToServer(a.id);const scopeA=store.getCacheScope();window.qStage='late-request';const late=store.fetchPlaylistSongs('same');window.qStage='connect-B';await store.connectToServer(b.id);const scopeB=store.getCacheScope();const own=await store.fetchPlaylistSongs('same');window.qStage='resolve-old';finishOld();const stale=await late;window.qServers=store.activeServerId.value===b.id&&store.isConnected.value&&scopeA!==scopeB&&own[0].originalId==='B-song'&&stale.length===0;await store.connectToServer(a.id);window.qRevisit=store.getCacheScope()===scopeA;qReady=true}catch(e){window.qFailure={stage:window.qStage||'account',type:['TypeError','ReferenceError','Error'].includes(e?.name)?e.name:'unknown'};qFailed=true}})()");
    await("qReady||qFailed"); assertEquals(js("JSON.stringify(window.qFailure||null)"),"false",js("qFailed")); assertEquals("true",js("qServers&&qRevisit"));
  }
}

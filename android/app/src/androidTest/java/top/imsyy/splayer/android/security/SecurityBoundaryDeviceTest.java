package top.imsyy.splayer.android.security;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** Only synthetic content in the isolated Debug application; no real account cookies. */
@RunWith(AndroidJUnit4.class)
public class SecurityBoundaryDeviceTest {
  private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
  private MainActivity activity;
  private WebView web;
  private String js(String expression)throws Exception{
    CountDownLatch done=new CountDownLatch(1);String[] result={null};
    inst.runOnMainSync(()->web.evaluateJavascript(expression,v->{result[0]=v;done.countDown();}));
    assertTrue(done.await(10,TimeUnit.SECONDS));return result[0];
  }
  private void await(String expression)throws Exception{
    for(int i=0;i<200;i++){if("true".equals(js(expression)))return;SystemClock.sleep(100);}
    fail("Timed out: "+expression+"; "+js("window.sbError||null"));
  }
  @Before public void setup()throws Exception{
    assertTrue(java.util.Arrays.asList("top.imsyy.splayer.android.debug", "top.imsyy.splayer.android.debug.sec17").contains(inst.getTargetContext().getPackageName()));
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    web=activity.getBridge().getWebView();await("!!document.querySelector('#app')?.__vue_app__ && !!window.$modal");
    js("window.sbReady=false;window.sbError=null;window.sbRestore=null;window.sbModal=null");
    System.out.println("SECURITY_AUDIT pid="+android.os.Process.myPid()+" viewport="+js("JSON.stringify({width:innerWidth,height:innerHeight,dpr:devicePixelRatio})"));
  }
  @After public void cleanup()throws Exception{
    if(web!=null)js("window.sbModal?.destroy();document.querySelector('#security-fixture')?.remove();window.sbRestore?.()");
    if(activity!=null)inst.runOnMainSync(activity::finish);
  }
  @Test public void syntheticCookieIsSavedRestoredAndRemovedWithoutLogging()throws Exception{
    js("(async()=>{try{const url=performance.getEntriesByType('resource').find(e=>/\\/stores-[^/]+\\.js/.test(e.name)).name;const exports=Object.values(await import(url));const set=exports.find(v=>typeof v==='function'&&String(v).includes('decodeURIComponent')&&String(v).includes('expires='));const get=key=>localStorage.getItem('cookie-'+key);const remove=key=>{document.cookie=key+'=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';localStorage.removeItem('cookie-'+key)};if(!set)throw Error('Cookie public exports unavailable');const key='fixture_security_'+Date.now();const value='synthetic_security_value';const originals={};window.sbLogs=[];for(const name of ['log','info','warn','error','debug']){originals[name]=console[name];console[name]=(...args)=>{window.sbLogs.push(args.map(String).join(' '));originals[name](...args)}}window.sbRestore=()=>{remove(key);for(const name of Object.keys(originals))console[name]=originals[name]};set(key+'='+value);window.sbCookieSaved=get(key)===value;document.cookie=key+'=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';window.sbCookieRestored=get(key)===value;remove(key);window.sbCookieRemoved=get(key)===null;const request=exports.find(v=>typeof v==='function'&&String(v).length<150&&String(v).includes('.request(')&&String(v).includes('data:'));if(!request)throw Error('Request public export unavailable');for(const status of [undefined,500]){const error=Object.assign(new Error(value),{response:status?{status,statusText:value}:undefined});await request({url:'https://example.invalid/fixture',params:{noCookie:true},adapter:()=>Promise.reject(error)}).catch(()=>{});}window.sbLeak=sbLogs.some(line=>line.includes(value)||line.includes(key));window.sbReady=true;}catch(e){window.sbError=e.message}})()");
    await("!!window.sbReady||!!window.sbError");assertEquals("null",js("window.sbError||null"));
    assertEquals("true",js("sbCookieSaved&&sbCookieRestored&&sbCookieRemoved"));
    assertEquals("false",js("sbLeak"));
  }
  @Test public void releaseMarkdownCannotExecuteInActualUpdateModal()throws Exception{
    // Discover the already-built public module exports, without replacing any renderer.
    String attack="## Fixture release\n\n- visible list\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n![normal](https://example.invalid/fixture.png)\n\n[normal link](https://example.invalid/notes)\n\n<img src='data:invalid' onerror='window.__securityFixtureExecuted=1'><svg onload='window.__securityFixtureExecuted=2'></svg><a href='javascript:window.__securityFixtureExecuted=3'>bad</a><a href='java&#x09;script:alert(1)'>encoded</a><a href='intent://fixture'>intent</a><a href='https://user:password@example.invalid'>credentials</a><iframe srcdoc='<script>alert(1)</script>'></iframe><object data='data:text/html,test'></object>";
    js("window.__securityFixtureExecuted=0;window.sbBody="+JSONObject.quote(attack)+";(async()=>{try{const entries=performance.getEntriesByType('resource');const helperUrl=entries.find(e=>/\\/stores-[^/]+\\.js/.test(e.name)).name;const helper=await import(helperUrl);const getLog=Object.values(helper).find(v=>typeof v==='function'&&String(v).includes('androidUpdateLog'));if(!getLog)throw Error('getUpdateLog export unavailable');window.sbCache=sessionStorage.getItem('androidUpdateLog');sessionStorage.setItem('androidUpdateLog',JSON.stringify({value:[{tag_name:'v99.0.0',html_url:'https://github.com/puman233/SPlayer-for-Android-Fork/releases/tag/v99.0.0',published_at:'2026-01-01T00:00:00Z',body:sbBody,draft:false,prerelease:false}],expiry:Date.now()+60000}));window.sbRestore=()=>{if(sbCache===null)sessionStorage.removeItem('androidUpdateLog');else sessionStorage.setItem('androidUpdateLog',sbCache)};const logs=await getLog();window.sbHtml=logs[0].changelog;const openModal=Object.values(helper).find(v=>typeof v==='function'&&String(v).includes('发现 Android 新版本'));if(!openModal)throw Error('Update modal export unavailable');await openModal(logs[0]);window.sbModal={destroy:()=>window.$modal.destroyAll()};window.sbReady=true;}catch(e){window.sbError=e.message}})()");
    await("!!window.sbReady||!!window.sbError");assertEquals("null",js("window.sbError||null"));
    await("!!document.querySelector('.android-update-app .markdown-body')");SystemClock.sleep(500);
    assertEquals("0",js("window.__securityFixtureExecuted"));
    assertEquals("false",js("!!document.querySelector('.android-update-app .markdown-body [onerror],.android-update-app .markdown-body [onload],.android-update-app .markdown-body svg,.android-update-app .markdown-body a[href^=\"javascript:\"]')"));
    assertEquals("true",js("!!document.querySelector('.android-update-app .markdown-body table')&&!!document.querySelector('.android-update-app .markdown-body ul')"));
    assertEquals("true",js("(()=>{const root=document.querySelector('.android-update-app .markdown-body');const link=root.querySelector('a[href=\"https://example.invalid/notes\"]');return !!root.querySelector('img[src=\"https://example.invalid/fixture.png\"]')&&link?.target==='_blank'&&link?.rel==='noopener noreferrer'&&!root.querySelector('iframe,object')&&Array.from(root.querySelectorAll('a[href]')).every(a=>a.href.startsWith('https://example.invalid/'));})()"));
  }
  @Test public void backupRulesUseRealWebviewDomainAndPreserveNativePreferences()throws Exception{
    assertTrue(new java.io.File(inst.getTargetContext().getApplicationInfo().dataDir,"app_webview").isDirectory());
    for(String resource:new String[]{"backup_rules","data_extraction_rules"}){
      int id=inst.getTargetContext().getResources().getIdentifier(resource,"xml",inst.getTargetContext().getPackageName());
      android.content.res.XmlResourceParser xml=inst.getTargetContext().getResources().getXml(id);
      int webviewRules=0;
      try{while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(xml.getEventType()!=org.xmlpull.v1.XmlPullParser.START_TAG||!xml.getName().equals("exclude"))continue;String domain=xml.getAttributeValue(null,"domain"),path=xml.getAttributeValue(null,"path");assertTrue("Android supported backup domain",java.util.Arrays.asList("root","file","database","sharedpref","external","device_root","device_file","device_database","device_sharedpref").contains(domain));assertNotNull(path);if("app_webview/".equals(path)){assertEquals("root",domain);webviewRules++;}assertFalse("Do not exclude all native preferences","sharedpref".equals(domain)&&(".".equals(path)||"./".equals(path)));}}finally{xml.close();}
      assertEquals(resource.equals("backup_rules")?1:2,webviewRules);
    }
  }
  @Test public void ordinaryPreferencesSurviveActivityRecreation()throws Exception{
    String key="security_preference_"+java.util.UUID.randomUUID();
    android.content.SharedPreferences preferences=inst.getTargetContext().getSharedPreferences("security_fixture",0);
    try{
      assertTrue(preferences.edit().putString(key,"synthetic_preference").commit());
      js("localStorage.setItem("+JSONObject.quote(key)+",'synthetic_preference')");
      inst.runOnMainSync(activity::finish);
      activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      web=activity.getBridge().getWebView();await("!!window.$modal");
      assertEquals("synthetic_preference",preferences.getString(key,null));
      assertEquals("\"synthetic_preference\"",js("localStorage.getItem("+JSONObject.quote(key)+")"));
    }finally{
      preferences.edit().remove(key).commit();
      js("localStorage.removeItem("+JSONObject.quote(key)+")");
    }
  }
}

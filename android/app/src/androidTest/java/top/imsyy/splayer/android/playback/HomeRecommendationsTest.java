package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 真实 WebView 中模拟不同身份的推荐响应；不使用真实账号或清空应用数据。 */
@RunWith(AndroidJUnit4.class)
public class HomeRecommendationsTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private String js(String expression) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    String[] result = new String[1];
    inst.runOnMainSync(() -> web.evaluateJavascript("(()=>{try{return eval("+org.json.JSONObject.quote(expression)+");}catch(e){return String(e.stack||e);}})()", value -> { result[0]=value; latch.countDown(); }));
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    return result[0];
  }
  private void awaitText(String text) throws Exception {
    for(int i=0;i<50;i++) {
      if("true".equals(js("document.querySelector('.home-online')?.textContent.includes('"+text+"')"))) return;
      SystemClock.sleep(100);
    }
    fail("首页未刷新为："+text+"; state="+js("JSON.stringify({calls:window.recCalls,saved:!!window.recSaved,cookie:document.cookie,local:localStorage.getItem('cookie-MUSIC_U'),login:window.recData?.userLoginStatus,id:window.recData?.userData.userId,home:document.querySelector('.home-online')?.textContent.slice(0,500)})"));
  }
  @Test public void refreshOnLoginSwitchLogoutAndRejectLateGuest() throws Exception {
    assertTrue(inst.getTargetContext().getPackageName().endsWith(".phase1verify"));
    MainActivity activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    inst.runOnMainSync(() -> web=activity.getBridge().getWebView());
    for(int i=0;i<90&&!"true".equals(js("!!(()=>{const a=document.querySelector('#app')?.__vue_app__;if(!a)return false;const p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);return p?._s.has('data')&&p?._s.has('setting')&&p?._s.has('status')})()"));i++) SystemClock.sleep(200);
    assertEquals("推荐测试初始化", "true", js("(()=>{const a=document.querySelector('#app').__vue_app__;window.recPinia=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.recData=recPinia._s.get('data');window.recSetting=recPinia._s.get('setting');window.recSaved={online:recSetting.useOnlineService,login:recData.userLoginStatus,type:recData.loginType,id:recData.userData.userId,cookie:localStorage.getItem('cookie-MUSIC_U')};recSetting.userAgreementVersion='v2.0';recSetting.useOnlineService=false;recPinia._s.get('status').showFullPlayer=false;a.config.globalProperties.$router.push('/');return true;})()"));
    SystemClock.sleep(300);
    try {
      js("(()=>{window.recCalls=[];window.recGuestDelay=1400;window.recOpen=XMLHttpRequest.prototype.open;window.recSend=XMLHttpRequest.prototype.send;window.recHeader=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.open=function(method,url,...args){this.recUrl=String(url);this.recHeaders={};return recOpen.call(this,method,url,...args)};XMLHttpRequest.prototype.setRequestHeader=function(k,v){this.recHeaders[k.toLowerCase()]=v;return recHeader.call(this,k,v)};XMLHttpRequest.prototype.send=function(...args){if(this.recUrl.includes('/login/status'))return;if(!/\\/personalized(?:\\?|$)/.test(this.recUrl))return recSend.apply(this,args);const cookie=this.recHeaders['x-splayer-cookie']||'',who=cookie.includes('fixture-a')?'账号A推荐':cookie.includes('fixture-b')?'账号B推荐':'游客推荐';recCalls.push({url:this.recUrl,who,cookie});setTimeout(()=>{const body=JSON.stringify({code:200,result:[{id:who==='游客推荐'?101:who==='账号A推荐'?102:103,name:who,picUrl:'/images/album.jpg',playcount:1}]});Object.defineProperties(this,{readyState:{value:4,configurable:true},status:{value:200,configurable:true},responseText:{value:body,configurable:true},response:{value:body,configurable:true}});this.getAllResponseHeaders=()=> 'content-type: application/json';this.onloadend?.call(this,new Event('loadend'));},who==='游客推荐'?recGuestDelay:80);};for(const k of Object.keys(sessionStorage))if(k.startsWith('playlistRec'))sessionStorage.removeItem(k);sessionStorage.setItem('playlistRec',JSON.stringify({expiry:Date.now()+600000,value:{result:[{id:99,name:'旧游客缓存',picUrl:'/images/album.jpg'}]}}));document.cookie='MUSIC_U=; Max-Age=0; path=/';localStorage.removeItem('cookie-MUSIC_U');document.cookie='MUSIC_U=; Max-Age=0; path=/; Secure';recData.userLoginStatus=false;recData.userData.userId=0;recSetting.useOnlineService=true;})()");
      for(int i=0;i<30&&"0".equals(js("recCalls.length"));i++) SystemClock.sleep(100);
      assertEquals("游客旧通用缓存不得显示", "false", js("document.querySelector('.home-online').textContent.includes('旧游客缓存')"));
      js("localStorage.setItem('cookie-MUSIC_U','fixture-a');document.cookie='MUSIC_U=fixture-a; path=/';recData.loginType='qr';recData.userData.userId=901;recData.userLoginStatus=true");
      awaitText("账号A推荐");
      SystemClock.sleep(1600);
      assertEquals("迟到游客响应不得覆盖登录推荐", "false", js("document.querySelector('.home-online').textContent.includes('游客推荐')"));
      assertEquals("登录请求带新凭据及缓存穿透时间戳", "true", js("recCalls.some(c=>c.who==='账号A推荐'&&c.cookie.includes('fixture-a')&&c.url.includes('timestamp='))"));
      js("localStorage.setItem('cookie-MUSIC_U','fixture-b');document.cookie='MUSIC_U=fixture-b; path=/';recData.userData.userId=902");
      awaitText("账号B推荐");
      js("recSetting.useOnlineService=false"); SystemClock.sleep(200);
      js("recSetting.useOnlineService=true");
      awaitText("账号B推荐");
      assertEquals("组件重建不得复用其他账号推荐", "false", js("document.querySelector('.home-online').textContent.includes('账号A推荐')"));
      js("recGuestDelay=0;document.cookie='MUSIC_U=; Max-Age=0; path=/';localStorage.removeItem('cookie-MUSIC_U');document.cookie='MUSIC_U=; Max-Age=0; path=/; Secure';recData.userLoginStatus=false;recData.userData.userId=0");
      awaitText("游客推荐");
      assertEquals("退出后不显示账号推荐", "false", js("document.querySelector('.home-online').textContent.includes('账号B推荐')"));
    } finally {
      js("(()=>{if(!window.recSaved)return;XMLHttpRequest.prototype.open=recOpen;XMLHttpRequest.prototype.send=recSend;XMLHttpRequest.prototype.setRequestHeader=recHeader;recSetting.useOnlineService=false;recData.userLoginStatus=recSaved.login;recData.loginType=recSaved.type;recData.userData.userId=recSaved.id;if(recSaved.cookie===null)localStorage.removeItem('cookie-MUSIC_U');else localStorage.setItem('cookie-MUSIC_U',recSaved.cookie);for(const k of Object.keys(sessionStorage))if(k.startsWith('playlistRec'))sessionStorage.removeItem(k);recSetting.useOnlineService=recSaved.online;})()");
    }
  }
}

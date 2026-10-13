package top.imsyy.splayer.android.phase7;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** AndroidTest-only real WebView measurements with synthetic isolated-package data. */
@RunWith(AndroidJUnit4.class)
public class R23TouchTargetsDeviceTest {
  final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
  MainActivity activity; WebView web; File output; String label; int originalOrientation;
  JSONArray measurements=new JSONArray();
  String js(String code) throws Exception {
    CountDownLatch latch=new CountDownLatch(1);String[] value={null};
    String guarded="(()=>{try{return eval("+JSONObject.quote(code)+")}catch(e){return 'AUDIT_ERROR: '+e.message}})()";
    inst.runOnMainSync(()->web.evaluateJavascript(guarded,v->{value[0]=v;latch.countDown();}));
    assertTrue(latch.await(15,TimeUnit.SECONDS));assertFalse(value[0],value[0]!=null&&value[0].contains("AUDIT_ERROR:"));return value[0];
  }
  void await(String expression) throws Exception {
    for(int i=0;i<200;i++){if("true".equals(js(expression)))return;SystemClock.sleep(100);}
    fail("Audit condition unavailable: "+expression);
  }
  void write(String name,String contents) throws Exception {try(FileOutputStream out=new FileOutputStream(new File(output,name))){out.write(contents.getBytes(StandardCharsets.UTF_8));}}
  void orientation(boolean landscape) throws Exception {
    inst.runOnMainSync(()->activity.setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
    await(landscape?"innerWidth>innerHeight":"innerHeight>innerWidth");SystemClock.sleep(800);
  }
  void show() throws Exception {js("(document.querySelector('.full-player-mobile-landscape')||document.querySelector('.full-player-mobile')||document.querySelector('.full-player'))?.dispatchEvent(new MouseEvent('click',{bubbles:true}))");SystemClock.sleep(250);}
  void nativeTouchRestore() throws Exception {
    int[] pos={0,0};inst.runOnMainSync(()->web.getLocationOnScreen(pos));
    int x=pos[0]+Math.max(4,web.getWidth()/20),y=pos[1]+web.getHeight()/2;
    try(android.os.ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand("input -d "+activity.getDisplay().getDisplayId()+" tap "+x+" "+y)){try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}}
    SystemClock.sleep(350);
  }
  void capture(String name) throws Exception {
    Object raw=new JSONTokener(js("JSON.stringify(p7Measure("+JSONObject.quote(name)+"))")).nextValue();
    JSONObject measurement=new JSONObject(String.valueOf(raw));
    int[] loc={0,0};inst.runOnMainSync(()->web.getLocationOnScreen(loc));
    measurement.put("nativeWebView",new JSONObject().put("left",loc[0]).put("top",loc[1]).put("width",web.getWidth()).put("height",web.getHeight()));
    measurement.put("screenshotMethod","UiAutomation.takeScreenshot(Activity.Window)");measurement.put("displayId",activity.getDisplay().getDisplayId());measurements.put(measurement);write(name+".json",measurement.toString(2));
    Bitmap screenshot=inst.getUiAutomation().takeScreenshot(activity.getWindow());assertNotNull(screenshot);
    try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,stream));}finally{screenshot.recycle();}
    System.out.println("UI_AUDIT "+name+" viewport="+measurement.getJSONObject("viewport")+" visible="+measurement.opt("controlsVisible"));
  }


  void key(String args) throws Exception {
    String[] parts=args.split(" ");int code=Integer.parseInt(parts[parts.length-1]);int meta=args.startsWith("keycombination")?android.view.KeyEvent.META_SHIFT_ON:0;
    inst.runOnMainSync(()->{long now=SystemClock.uptimeMillis();activity.dispatchKeyEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,code,0,meta,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,android.view.InputDevice.SOURCE_KEYBOARD));activity.dispatchKeyEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,code,0,meta,android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,android.view.InputDevice.SOURCE_KEYBOARD));});
    SystemClock.sleep(160);
  }

  android.view.ViewGroup.LayoutParams savedLayout;
  int savedWidth,savedHeight;
  String key = "unknown";
  JSONObject geometry(String name) throws Exception {
    JSONObject result=new JSONObject(String.valueOf(new JSONTokener(js("JSON.stringify(r23Geometry())")).nextValue()));write(label+"_"+name+"_geometry.json",result.toString(2));return result;
  }
  @org.junit.Before public void setup() throws Exception {
    assertEquals("top.imsyy.splayer.android.debug.r6",inst.getTargetContext().getPackageName());label=InstrumentationRegistry.getArguments().getString("deviceLabel","unknown");key=InstrumentationRegistry.getArguments().getString("evidencePhase","current");
    output=new File(inst.getTargetContext().getExternalFilesDir(null),"phase7-r23-"+key);assertTrue(output.exists()||output.mkdirs());
    activity=(MainActivity)inst.startActivitySync(new Intent(inst.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));web=activity.getBridge().getWebView();originalOrientation=activity.getRequestedOrientation();savedLayout=web.getLayoutParams();savedWidth=savedLayout.width;savedHeight=savedLayout.height;
    await("!!window.__SPLAYER_PLAYER_CONTROLLER__&&!!document.querySelector('#app')?.__vue_app__?.config.globalProperties.$pinia?._s.get('music')");
    try(InputStream in=inst.getContext().getAssets().open("r23-player-fixture.js")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);js(bytes.toString("UTF-8"));}
    js("window.r23Ready=false;p24Prepare().then(()=>r23Ready=true).catch(e=>window.r23Error=e.message)");await("window.r23Error?(()=>{throw Error(r23Error)})():r23Ready");js("p7Stores.setting.androidDeviceModeOverride='phone'");orientation(false);await("!!document.querySelector('.mobile-player-bottom-controls')");show();
  }
  @org.junit.After public void cleanup() throws Exception {
    if(web!=null){js("r23CommentsOff();window.r23Restored=false;p24Restore().then(()=>r23Restored=true)");await("r23Restored");inst.runOnMainSync(()->{savedLayout.width=savedWidth;savedLayout.height=savedHeight;web.setLayoutParams(savedLayout);activity.setRequestedOrientation(originalOrientation);});}
  }
  void minTarget(JSONObject t) throws Exception {JSONObject r=t.getJSONObject("rect");assertTrue(t.toString(),r.getDouble("width")>=47.99&&r.getDouble("height")>=47.99);}
  @Test public void shuffleTargetIsAtLeast48CssPixels() throws Exception {JSONObject g=geometry("T01_shuffle");capture(label+"_T01_red_or_green");minTarget(g.getJSONArray("controls").getJSONObject(0));}
  @Test public void repeatTargetIsAtLeast48CssPixels() throws Exception {JSONObject g=geometry("T02_repeat");minTarget(g.getJSONArray("controls").getJSONObject(4));}
  @Test public void pageTargetsAreIndependent48PixelNativeButtons() throws Exception {JSONObject g=geometry("T04_pages");for(int i=0;i<g.getJSONArray("pages").length();i++){JSONObject page=g.getJSONArray("pages").getJSONObject(i);assertEquals("BUTTON",page.getString("tag"));minTarget(page);}}
  void layout(String name) throws Exception {
    show();SystemClock.sleep(250);JSONObject g=geometry(name);capture(label+"_"+name);if(g.isNull("footer"))return;
    assertFalse(g.toString(),g.getBoolean("overflow"));JSONArray controls=g.getJSONArray("controls"),pages=g.getJSONArray("pages");
    for(int i=0;i<controls.length();i++)minTarget(controls.getJSONObject(i));
    for(int i:new int[]{0,4}){JSONArray hits=controls.getJSONObject(i).getJSONArray("hits");for(int k=0;k<hits.length();k++)assertTrue(hits.getBoolean(k));}
    double end=-1;for(int i=0;i<pages.length();i++){JSONObject p=pages.getJSONObject(i);minTarget(p);assertEquals("BUTTON",p.getString("tag"));JSONObject r=p.getJSONObject("rect");assertTrue(r.getDouble("left")>=end);end=r.getDouble("right");assertTrue(r.getDouble("top")>=controls.getJSONObject(2).getJSONObject("rect").getDouble("bottom"));}
    assertEquals(g.getJSONObject("footer").getDouble("height"),Double.parseDouble(g.getString("footerObserved").replace("px","")),1);
  }

  void touch(double x,double y,boolean swipe) throws Exception {
    double ratio=web.getWidth()/Double.parseDouble(js("innerWidth"));
    inst.runOnMainSync(()->{long now=SystemClock.uptimeMillis();for(int i=0;i<(swipe?3:2);i++){int action=i==0?android.view.MotionEvent.ACTION_DOWN:i==(swipe?2:1)?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now+i*60,action,(float)((x-(swipe&&i>0?150:0))*ratio),(float)(y*ratio),0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);web.dispatchTouchEvent(event);event.recycle();}});SystemClock.sleep(350);
  }
  @Test public void touchEdgesThreePagesAndHiddenFooterRemainUsable() throws Exception {
    JSONObject g=geometry("touch_before");
    for(int i=0;i<g.getJSONArray("pages").length();i++){js("r23PageObserve()");JSONObject r=g.getJSONArray("pages").getJSONObject(i).getJSONObject("rect");touch(r.getDouble("left")+1,r.getDouble("top")+1,false);assertEquals("1",js("r23Events.length"));}
    js("r23CommentsOn()");SystemClock.sleep(300);await("r23Pages().length===3");layout("three_pages");
    js("r23Pages()[1].click()");SystemClock.sleep(300);g=geometry("three_pages_info");
    for(int i=0;i<3;i++){JSONArray hits=g.getJSONArray("pages").getJSONObject(i).getJSONArray("hits");for(int k=0;k<hits.length();k++)assertTrue(hits.getBoolean(k));}
    inst.runOnMainSync(()->web.requestFocus());key("keyevent 61");js("r23Pages()[0].focus()");key("keyevent 61");assertEquals("true",js("document.activeElement===r23Pages()[1]"));key("keyevent 61");assertEquals("true",js("document.activeElement===r23Pages()[2]"));key("keycombination 59 61");assertEquals("true",js("document.activeElement===r23Pages()[1]"));key("keycombination 59 61");assertEquals("true",js("document.activeElement===r23Pages()[0]"));
    JSONObject r=g.getJSONArray("pages").getJSONObject(2).getJSONObject("rect");String before=js("document.querySelector('.pagination').getAttribute('aria-label')");touch(r.getDouble("left")+24,r.getDouble("top")+24,true);assertEquals(before,js("document.querySelector('.pagination').getAttribute('aria-label')"));
    js("r23Pages()[2].click()");SystemClock.sleep(300);js("r23Pages()[0].focus()");SystemClock.sleep(4500);g=geometry("portrait_hidden");capture(label+"_portrait_hidden");assertFalse(g.getBoolean("visible"));assertEquals("false",js("!!document.activeElement.closest('.mobile-player-bottom-controls')"));
    assertEquals("true",js("r23Pages().every(e=>{const r=e.getBoundingClientRect(),hit=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return !hit||!e.contains(hit)})"));
    show();await("r23Geometry().visible");layout("portrait_restored");js("r23Pages()[0].focus()");key("keyevent 61");assertEquals("true",js("document.activeElement===r23Pages()[1]"));
    js("document.querySelector('.full-player-mobile').style.setProperty('--mobile-safe-bottom','48px')");SystemClock.sleep(300);g=geometry("simulated_safe_bottom_48");capture(label+"_simulated_safe_bottom_48");for(int i=0;i<3;i++){assertTrue(g.getJSONArray("pages").getJSONObject(i).getJSONObject("rect").getDouble("bottom")<=g.getJSONObject("viewport").getDouble("height")-48);}
    js("document.querySelector('.full-player-mobile').style.removeProperty('--mobile-safe-bottom')");
  }
  @Test public void responsivePageControlsAndSafeAreaMatrix() throws Exception {
    if(label.equals("A")){
      inst.runOnMainSync(()->{android.view.ViewGroup.LayoutParams p=web.getLayoutParams();float density=web.getResources().getDisplayMetrics().density;p.width=Math.round(320*density);p.height=Math.round(568*density);web.setLayoutParams(p);});await("innerWidth===320&&innerHeight===568");layout("simulated_320x568_info");inst.runOnMainSync(()->{savedLayout.width=savedWidth;savedLayout.height=savedHeight;web.setLayoutParams(savedLayout);});await("innerWidth===360");
    }
    layout("phone_portrait_info");js("document.querySelector('.pagination .dot:last-child').click()");SystemClock.sleep(400);layout("phone_portrait_lyric");
    js("r23PageObserve();r23Pages()[0].focus()");key("keyevent 66");assertEquals("1",js("r23Events.length"));
    js("r23PageObserve();r23Pages()[r23Pages().length-1].focus()");key("keyevent 62");assertEquals("1",js("r23Events.length"));
    js("p7Stores.music.setSongLyric({lrcData:[],yrcData:[]},true)");SystemClock.sleep(150);assertEquals("1",js("r23Pages().length"));layout("no_lyric_single_page");js("p7SetLyrics(true)");SystemClock.sleep(150);assertEquals("2",js("r23Pages().length"));
    js("p7Stores.music.playSong={...p7Songs[0],name:p7Long}");layout("long_title");
    orientation(true);show();capture(label+"_phone_landscape_visible");SystemClock.sleep(2500);capture(label+"_phone_landscape_hidden");assertEquals("true",js("Array.from(document.querySelectorAll('[inert]')).every(e=>!e.contains(document.activeElement))"));show();capture(label+"_phone_landscape_restored");orientation(false);
    if(label.equals("B")){js("p7Stores.setting.androidDeviceModeOverride='auto'");SystemClock.sleep(500);layout("auto_pad_portrait");orientation(true);show();capture(label+"_auto_pad_landscape");}
  }
}

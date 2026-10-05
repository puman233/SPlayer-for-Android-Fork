package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.imsyy.splayer.android.MainActivity;

/** 隔离测试包的真实触摸、旋转与控制层验收。 */
@RunWith(AndroidJUnit4.class)
public class PhoneLandscapePlayerTest {
  private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
  private WebView web;
  private MainActivity activity;
  private File evidence;
  private String label;
  private long touchDown;
  private int[] origin = new int[2];
  private float scale;

  private String js(String expression) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    String[] result = new String[1];
    inst.runOnMainSync(() -> web.evaluateJavascript(expression, value -> {
      result[0] = value;
      latch.countDown();
    }));
    assertTrue("WebView 回调超时", latch.await(10, TimeUnit.SECONDS));
    return result[0];
  }

  private JSONObject json(String expression) throws Exception {
    return new JSONObject((String) new JSONTokener(js("JSON.stringify(" + expression + ")")).nextValue());
  }

  private boolean visible() throws Exception {
    return "true".equals(js("document.querySelector('.full-player-mobile-landscape')?.dataset.controlsVisible==='true'"));
  }

  private JSONObject bounds(String selector) throws Exception {
    return json("(()=>{const e=document.querySelector(" + JSONObject.quote(selector) + ");const r=e.getBoundingClientRect();return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height}})()");
  }

  private void event(int action, float x, float y) {
    if (action == MotionEvent.ACTION_DOWN) touchDown = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(touchDown, SystemClock.uptimeMillis(), action,
        x * scale, y * scale, 0);
    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    // 将真实 MotionEvent 交给目标 WebView，避免多显示面默认注入到显示面零。
    inst.runOnMainSync(() -> web.dispatchTouchEvent(event));
    event.recycle();
  }

  private void tap(float x, float y) {
    event(MotionEvent.ACTION_DOWN, x, y);
    SystemClock.sleep(100);
    event(MotionEvent.ACTION_UP, x, y);
    SystemClock.sleep(400);
  }

  private void reveal() throws Exception {
    // 顶栏空白区域，不触发歌词跳转或封面点击。
    JSONObject header = bounds(".landscape-header");
    tap((float) (header.getDouble("x") + header.getDouble("width") / 2),
        (float) (header.getDouble("y") + header.getDouble("height") / 2));
    assertTrue("真实单击应展开控制层", visible());
  }

  private void capture(String name) throws Exception {
    Bitmap[] buffer = new Bitmap[1];
    CountDownLatch captured = new CountDownLatch(1);
    int[] status = new int[1];
    inst.runOnMainSync(() -> {
      android.view.View decor = activity.getWindow().getDecorView();
      buffer[0] = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
      PixelCopy.request(activity.getWindow(), buffer[0], result -> {
        status[0] = result;
        captured.countDown();
      }, new Handler(Looper.getMainLooper()));
    });
    assertTrue(captured.await(10, TimeUnit.SECONDS));
    assertEquals("实际应用窗口截图必须成功", PixelCopy.SUCCESS, status[0]);
    Bitmap bitmap = buffer[0];
    try (FileOutputStream output = new FileOutputStream(new File(evidence, label + "-" + name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
    }
    bitmap.recycle();
  }

  private void save(String name, JSONObject data) throws Exception {
    try (FileOutputStream output = new FileOutputStream(new File(evidence, label + "-" + name + ".json"))) {
      output.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
    }
  }

  private void rotate(int orientation) throws Exception {
    inst.runOnMainSync(() -> activity.setRequestedOrientation(orientation));
    SystemClock.sleep(1800);
    inst.runOnMainSync(() -> web.getLocationOnScreen(origin));
    int[] width = new int[1];
    inst.runOnMainSync(() -> width[0] = web.getWidth());
    scale = (float) (width[0] / json("({width:innerWidth})").getDouble("width"));
  }

  private void assertMainBounds() throws Exception {
    JSONObject cover = bounds(".landscape-cover"), info = bounds(".left-section .info");
    JSONObject lyrics = bounds(".right-section"), root = bounds(".full-player-mobile-landscape");
    assertTrue("封面应有正尺寸", cover.getDouble("height") > 0);
    assertEquals("封面保持正方形", cover.getDouble("width"), cover.getDouble("height"), 1);
    assertTrue("封面与歌曲信息不得重叠", cover.getDouble("bottom") <= info.getDouble("y") + 1);
    assertTrue("信息不得越界", info.getDouble("bottom") <= root.getDouble("bottom") + 1);
    assertTrue("信息与歌词不得重叠", info.getDouble("right") <= lyrics.getDouble("x") + 1);
    assertTrue("歌词应占主要宽度", lyrics.getDouble("width") > info.getDouble("width"));
    assertTrue("歌词不得越界", lyrics.getDouble("right") <= root.getDouble("right") + 1);
  }

  private void assertStableLyrics(String engine) throws Exception {
    // 引擎首次排版完成后，再测试显隐本身的位移。
    SystemClock.sleep(2500);
    for (int cycle = 0; cycle < 2; cycle++) {
      tap(5, 40);
      SystemClock.sleep(800);
      assertEquals("控件应恢复", "false", js("document.querySelector('.full-player-mobile').classList.contains('controls-hidden')"));
      String anchor = "(()=>{const e=Array.from(document.querySelectorAll('.lyric-page [lang], .lyric-page [class*=lyricMainLine]')).find(e=>e.textContent.includes('決めつけばかり'));if(!e)throw Error('缺少测试歌词行');const r=e.getBoundingClientRect(),v=document.querySelector('.lyric-main').getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,viewportY:v.y,viewportHeight:v.height,hidden:document.querySelector('.full-player-mobile').classList.contains('controls-hidden'),pure:t.pureLyricMode,playing:t.playStatus,time:t.currentTime,spring:s.useAMSpring}})()";
      JSONObject before = json(anchor);
      capture("stable-" + engine + "-visible-" + cycle);
      org.json.JSONArray samples = new org.json.JSONArray();
      for (int frame = 0; frame < 12; frame++) {
        SystemClock.sleep(180);
        JSONObject position = json(anchor);
        samples.put(position);
        save("stable-" + engine + "-" + cycle, new JSONObject().put("before", before).put("frames", samples));
        assertEquals("显隐全过程当前歌词行不跳动：" + engine, before.getDouble("y"), position.getDouble("y"), 1);
        assertEquals("封面、顶栏、播放控件同步渐隐", "true", js("(()=>{const opacity=s=>Number(getComputedStyle(document.querySelector(s)).opacity);const header=opacity('.lyric-header');return Math.abs(header-opacity('.top-bar'))<.02&&Math.abs(header-opacity('.mobile-player-bottom-controls'))<.02})()"));
      }
      assertEquals("两秒后控件隐藏", "true", js("document.querySelector('.full-player-mobile').classList.contains('controls-hidden')"));
      save("stable-" + engine + "-" + cycle, new JSONObject().put("before", before).put("frames", samples));
      capture("stable-" + engine + "-hidden-" + cycle);
    }
  }

  private void assertCompactActionGroups() throws Exception {
    for (String side : new String[] {"left", "right"}) {
      JSONObject geometry = json("(()=>{const e=document.querySelector('.landscape-controls ." + side + "'),r=e.getBoundingClientRect();const widths=Array.from(e.children).map(c=>c.getBoundingClientRect().width).reduce((a,b)=>a+b,0);return {width:r.width,content:widths}})()");
      assertTrue("侧组不得在按钮旁边保留大量空白：" + side,
          geometry.getDouble("width") - geometry.getDouble("content") <= 14);
    }
    JSONObject slider = bounds(".landscape-controls .slider"), root = bounds(".full-player-mobile-landscape");
    assertTrue("进度条下方仅保留系统安全区和小间距", root.getDouble("bottom") - slider.getDouble("bottom") <= 12);
  }

  private File audio() throws Exception {
    File file = new File(inst.getTargetContext().getFilesDir(), "phase1-silence.wav");
    int bytes = 120 * 16000 * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(bytes + 36);
    header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1);
    header.putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
    header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(header.array());
      byte[] silence = new byte[32000];
      for (int i = 0; i < 120; i++) out.write(silence);
    }
    return file;
  }

  private void assertPortraitMetadata() throws Exception {
    assertEquals("未传入横屏可见性参数时标签操作必须可交互", "false", js("document.querySelector('.info-page .meta-actions-row').inert"));
    if (!"phone".equals(label)) {
      assertEquals("平板竖屏不得开启手机专属布局", "false", js("!!document.querySelector('.phone-portrait-meta')"));
      return;
    }
    JSONObject title = bounds(".info-page .mobile-data > .name"), artists = bounds(".info-page .artists");
    JSONObject album = bounds(".info-page .album"), row = bounds(".info-page .meta-actions-row");
    JSONObject tags = bounds(".info-page .play-meta"), actions = bounds(".info-page .info-actions");
    assertTrue("标题在歌手上方", title.getDouble("bottom") <= artists.getDouble("y") + 1);
    assertTrue("歌手在专辑上方", artists.getDouble("bottom") <= album.getDouble("y") + 1);
    assertTrue("标签操作在专辑下方", album.getDouble("bottom") <= row.getDouble("y") + 1);
    assertTrue("标签不能与操作重叠", tags.getDouble("right") <= actions.getDouble("x") + 1 || tags.getDouble("bottom") <= actions.getDouble("y") + 1);
    assertEquals("手机操作顺序必须为收藏、桌面歌词、队列、更多", "true", js("(()=>{const row=document.querySelector('.info-page .info-actions');const d=row.querySelector('.portrait-desktop-lyric'),q=row.querySelector('.n-badge');return !!d&&!!q&&d.previousElementSibling?.classList.contains('action-btn')&&d.nextElementSibling===q&&q.nextElementSibling?.classList.contains('qa-trigger')})()"));
    assertTrue("操作不能越界", actions.getDouble("right") <= row.getDouble("right") + 1);
    assertTrue("手机操作触控热区至少48px", bounds(".info-page .qa-trigger").getDouble("height") >= 48);
    save("portrait-geometry", json("({title:" + title + ",artists:" + artists + ",album:" + album + ",tags:" + tags + ",actions:" + actions + "})"));
  }

  @Test public void landscapeContentAndTemporaryControls() throws Exception {
    String pkg = inst.getTargetContext().getPackageName();
    assertTrue("禁止对正式应用执行测试", pkg.endsWith(".phase1verify"));
    label = InstrumentationRegistry.getArguments().getString("label", "device");
    evidence = new File(inst.getTargetContext().getExternalFilesDir(null), "phase1");
    assertTrue(evidence.isDirectory() || evidence.mkdirs());
    Intent intent = new Intent(inst.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity = (MainActivity) inst.startActivitySync(intent);
    inst.runOnMainSync(() -> web = activity.getBridge().getWebView());
    for (int i = 0; i < 90; i++) {
      if ("true".equals(js("!!document.querySelector('#app')?.__vue_app__"))) break;
      SystemClock.sleep(200);
    }
    assertEquals("true", js("!!document.querySelector('#app')?.__vue_app__"));
    File wav = audio();
    try {
      rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      js("(()=>{const a=document.querySelector('#app').__vue_app__;window.p=Reflect.ownKeys(a._context.provides).map(k=>a._context.provides[k]).find(v=>v?._s instanceof Map);window.s=p._s.get('setting');window.t=p._s.get('status');window.m=p._s.get('music');s.userAgreementVersion='v2.0';s.androidDeviceModeOverride='auto';s.useAMLyrics=false;s.showTran=true;s.showRoma=true;s.dynamicCover=false;s.hideBracketedContent=true;s.playerType='cover';window.$modal?.destroyAll();m.playSong={...m.playSong,alia:'',path:undefined,id:12345,name:'ツギハギスタッカート',cover:'/images/album.jpg',coverSize:{s:'/images/album.jpg',m:'/images/album.jpg',l:'/images/album.jpg',xl:'/images/album.jpg'},artists:[{id:1,name:'とあ'}],album:{id:2,name:'拼凑的断音'},duration:120000};m.songLyric={lrcData:[{startTime:0,endTime:6000,words:[{word:'変わらない このままだよ',startTime:0,endTime:6000}],translatedLyric:'如此 永不改变',romanLyric:'ka wa ra na i ko no ma ma da yo'},{startTime:6000,endTime:12000,words:[{word:'どんな言葉も どんな未来も',startTime:6000,endTime:12000}],translatedLyric:'无论怎样的言语',romanLyric:'do n na ko to ba mo'},{startTime:12000,endTime:120000,words:[{word:'光の中で',startTime:12000,endTime:120000}],translatedLyric:'在光芒之中'}],yrcData:[]};t.showFullPlayer=true;t.playLoading=false;t.lyricLoading=false;t.lyricIndex=0;t.duration=120000;t.currentTime=0;t.playStatus=false;t.mainColor='239,239,239';t.repeatMode='off';t.shuffleMode='off';})()");
      SystemClock.sleep(1800);
      assertEquals("true", js("!!document.querySelector('.full-player-mobile')"));
      capture("portrait");
      assertPortraitMetadata();
      if ("phone".equals(label)) {
        int[] currentWidth = new int[1];
        inst.runOnMainSync(() -> currentWidth[0] = web.getWidth());
        scale = (float)(currentWidth[0] / json("({width:innerWidth})").getDouble("width"));
        JSONObject more = bounds(".info-page .qa-trigger");
        save("portrait-touch", json("(()=>{const e=document.querySelector('.info-page .qa-trigger'),r=e.getBoundingClientRect();return {scale:" + scale + ",width:innerWidth,hit:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.outerHTML}})()"));
        tap((float)(more.getDouble("x") + more.getDouble("width") / 2), (float)(more.getDouble("y") + more.getDouble("height") / 2));
        capture("portrait-more-attempt");
        assertEquals("窄屏次要操作应可在更多菜单访问", "true", js("!!document.querySelector('.portrait-overflow-actions')"));
        capture("portrait-more");
        JSONObject menu = bounds(".quick-actions-popover");
        assertTrue("竖屏收纳菜单顶部不得裁切", menu.getDouble("y") >= 0);
        assertTrue("竖屏收纳菜单底部不得越界", menu.getDouble("bottom") <= json("({height:innerHeight})").getDouble("height") + 1);
        assertEquals("只有添加歌单可收纳", "true", js("document.querySelector('.portrait-overflow-actions').textContent.trim()==='添加到歌单'"));
        tap(12, 100);
        JSONObject queue = bounds(".info-page .info-actions .n-badge .action-btn");
        tap((float)(queue.getDouble("x") + queue.getDouble("width") / 2), (float)(queue.getDouble("y") + queue.getDouble("height") / 2));
        assertEquals("真实点击常驻队列入口应打开队列", "true", js("t.playListShow"));
        js("t.playListShow=false");
        tap(12, 100);
        JSONObject desktop = bounds(".portrait-desktop-lyric");
        tap((float)(desktop.getDouble("x") + desktop.getDouble("width") / 2), (float)(desktop.getDouble("y") + desktop.getDouble("height") / 2));
        SystemClock.sleep(1000);
        assertEquals("桌面歌词按钮应调用现有原生开关", "true", js("t.showDesktopLyric"));
        tap((float)(desktop.getDouble("x") + desktop.getDouble("width") / 2), (float)(desktop.getDouble("y") + desktop.getDouble("height") / 2));
        SystemClock.sleep(500);
        assertEquals("桌面歌词应可关闭", "false", js("t.showDesktopLyric"));

        JSONObject lyricDot = bounds(".pagination .dot:last-child");
        js("m.songLyric={lrcData:Array.from({length:20},(_,i)=>({startTime:(i-8)*5000,endTime:(i-7)*5000,romanLyric:'ki me tsu ke ba ka ri u nu bo re wo ki ta chi pu na ho ko ri de',translatedLyric:'一味的固执己见，充满着傲慢，就算是自负且虚假的自尊',words:[{word:i===8?'決めつけばかり 自惚れを着た チープなhokoriで 音荒げても':'Lyric line '+(i+1)+' — keep the music playing',startTime:(i-8)*5000,endTime:(i-7)*5000}]})),yrcData:[]}");
        tap((float)(lyricDot.getDouble("x") + lyricDot.getDouble("width") / 2), (float)(lyricDot.getDouble("y") + lyricDot.getDouble("height") / 2));
        capture("portrait-lyric-controls");
        JSONObject visibleLyric = bounds(".lyric-page .lyric-main");
        SystemClock.sleep(2100);
        assertEquals("手机歌词页两秒隐藏", "true", js("document.querySelector('.full-player-mobile').classList.contains('controls-hidden')"));
        assertEquals("隐藏控件禁用触摸", "true", js("document.querySelector('.mobile-player-bottom-controls').inert"));
        capture("portrait-lyric-idle");
        JSONObject hiddenLyric = bounds(".lyric-page .lyric-main");
        assertEquals("隐藏时歌词视窗顶部不移位", visibleLyric.getDouble("y"), hiddenLyric.getDouble("y"), 1);
        assertEquals("隐藏时歌词视窗底部不移位", visibleLyric.getDouble("bottom"), hiddenLyric.getDouble("bottom"), 1);
        assertTrue("隐藏后歌词接近完整可用高度", hiddenLyric.getDouble("height") > json("({height:innerHeight})").getDouble("height") * 0.85);
        assertStableLyrics("default");
        js("s.useAMLyrics=true"); SystemClock.sleep(500);
        assertEquals("扩展状态第二歌词引擎可达", "true", js("!!document.querySelector('.lyric-page .am-lyric')"));
        capture("portrait-lyric-expanded-amll");
        assertStableLyrics("amll");
        js("s.useAMLyrics=false"); SystemClock.sleep(500);
        tap(5, 40);
        assertEquals("空白点击恢复歌词页控件", "false", js("document.querySelector('.full-player-mobile').classList.contains('controls-hidden')"));
        assertEquals("进度与播放按钮恢复后可见且可命中", "true", js("(()=>{const b=document.querySelector('.mobile-player-bottom-controls'),p=b.querySelector('.play-btn'),r=p.getBoundingClientRect();return !b.inert&&getComputedStyle(b).visibility==='visible'&&r.height>=48&&r.bottom<=innerHeight&&!!document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('.play-btn')})()"));
        capture("portrait-lyric-restored");
        assertEquals("歌词页裁剪自身内容避免横滑溢出", "true", js("getComputedStyle(document.querySelector('.lyric-page')).overflow==='hidden'"));
        JSONObject lyricPage = bounds(".lyric-page");
        float swipeY = (float)(lyricPage.getDouble("y") + lyricPage.getDouble("height") * 0.45);
        event(MotionEvent.ACTION_DOWN, 60, swipeY);
        for (int i=1;i<=12;i++) {
          event(MotionEvent.ACTION_MOVE, 60 + i * 18, swipeY);
          SystemClock.sleep(20);
        }
        capture("portrait-swipe-mid");
        assertEquals("横滑期间底部控件隐藏且不可点击", "true", js("(()=>{const e=document.querySelector('.mobile-player-bottom-controls');return e.inert&&getComputedStyle(e).visibility==='hidden'&&Number(getComputedStyle(e).opacity)===0})()"));
        event(MotionEvent.ACTION_UP, 276, swipeY);
        SystemClock.sleep(80);
        assertEquals("切页回弹期间底部控件仍隐藏", "true", js("getComputedStyle(document.querySelector('.mobile-player-bottom-controls')).visibility==='hidden'"));
        SystemClock.sleep(450);
        assertEquals("真实横滑应返回播放页", "false", js("document.querySelector('.full-player-mobile').classList.contains('lyric-active')"));
        assertPortraitMetadata();
        capture("portrait-swipe-info");
        double lyricY = bounds(".lyric-main").getDouble("y");
        double lyricHeight = bounds(".lyric-main").getDouble("height");
        String linePosition = "Array.from(document.querySelectorAll('.lyric-page [lang]')).find(e=>e.textContent.includes('決めつけばかり')).getBoundingClientRect().y";
        double lineY = Double.parseDouble(js(linePosition));
        event(MotionEvent.ACTION_DOWN, 290, swipeY);
        for (int i=1;i<=12;i++) {
          event(MotionEvent.ACTION_MOVE, 290 - i * 18, swipeY);
          assertEquals("进入歌词页时歌词区域不得上下移动", lyricY, bounds(".lyric-main").getDouble("y"), 1);
          assertEquals("进入歌词页时当前歌词不得下移动画", lineY, Double.parseDouble(js(linePosition)), 1);
        }
        event(MotionEvent.ACTION_UP, 74, swipeY);
        for (int i=0;i<12;i++) {
          SystemClock.sleep(45);
          assertEquals("进入歌词页时歌词区域不得上下移动", lyricY, bounds(".lyric-main").getDouble("y"), 1);
          assertEquals("进入歌词页时歌词区域高度不变", lyricHeight, bounds(".lyric-main").getDouble("height"), 1);
          assertEquals("切页完成期间当前歌词不得下移动画", lineY, Double.parseDouble(js(linePosition)), 1);
        }
        capture("portrait-swipe-lyric");
        JSONObject returnDot = bounds(".pagination .dot:last-child");
        tap((float)(returnDot.getDouble("x")+returnDot.getDouble("width")/2), (float)(returnDot.getDouble("y")+returnDot.getDouble("height")/2));
        save("portrait-lyric-dom", json("(()=>{const q=s=>{const e=document.querySelector(s),r=e.getBoundingClientRect(),c=getComputedStyle(e);return {x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom,display:c.display,opacity:c.opacity,zIndex:c.zIndex,hit:document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.className}};return {height:innerHeight,footer:q('.mobile-player-bottom-controls'),progress:q('.mobile-player-bottom-controls .progress-section'),controls:q('.mobile-player-bottom-controls .control-section'),play:q('.mobile-player-bottom-controls .play-btn'),html:document.querySelector('.mobile-player-bottom-controls').outerHTML}})()"));
        js("window.testGeneration=null;fetch('http://127.0.0.1:1145/api').then(r=>r.json()).then(x=>window.testGeneration=x.generation)");
        for(int i=0;i<40&&"null".equals(js("window.testGeneration"));i++) SystemClock.sleep(200);
        assertFalse("本地服务应就绪", "null".equals(js("window.testGeneration")));
        js("window.nodejs.channel.send('embedded-api-reload')");
        js("window.reloadGeneration=null");
        for(int i=0;i<40&&!"true".equals(js("window.reloadGeneration>window.testGeneration"));i++) {
          js("fetch('http://127.0.0.1:1145/api',{cache:'no-store'}).then(r=>r.json()).then(x=>window.reloadGeneration=x.generation).catch(()=>{})");
          SystemClock.sleep(200);
        }
        assertEquals("真实桥接热重载必须创建新的HTTP服务", "true", js("window.reloadGeneration>window.testGeneration"));
        save("api-reload", json("({before:window.testGeneration,after:window.reloadGeneration,songId:m.playSong.id,playStatus:t.playStatus})"));
        tap(180, 40);
        JSONObject infoDot = bounds(".pagination .dot:nth-last-child(2)");
        tap((float)(infoDot.getDouble("x") + infoDot.getDouble("width") / 2), (float)(infoDot.getDouble("y") + infoDot.getDouble("height") / 2));
      }
      rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      if ("tablet".equals(label)) {
        assertEquals("平板不得采用手机横屏布局", "false", js("!!document.querySelector('.full-player-mobile-landscape')"));
        assertEquals("平板保留原左右内容布局", "true", js("!!document.querySelector('.player-content .content-left')"));
        capture("landscape-original");
        save("result", new JSONObject().put("status", "VERIFIED").put("layout", "original-tablet"));
        return;
      }
      assertEquals("true", js("!!document.querySelector('.full-player-mobile-landscape')"));
      js("(()=>{window.hotfixOpen=XMLHttpRequest.prototype.open;window.hotfixSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(method,url,...rest){this.hotfixComment=String(url).includes('/comment/');return window.hotfixOpen.call(this,method,url,...rest)};XMLHttpRequest.prototype.send=function(body){if(!this.hotfixComment)return window.hotfixSend.call(this,body);const comment={commentId:1,content:'横屏评论应该有充分的阅读空间，完整显示较长的文字与回复内容，不再挤在狭窄的一列中。',time:Date.now(),likedCount:12,liked:false,user:{userId:1,nickname:'布局测试用户',avatarUrl:''},beReplied:[]};const payload=JSON.stringify({code:200,hotComments:[comment],data:{comments:[comment],totalCount:1,hasMore:false}});setTimeout(()=>{for(const [key,value] of Object.entries({status:200,readyState:4,responseText:payload,response:this.responseType==='json'?JSON.parse(payload):payload}))Object.defineProperty(this,key,{configurable:true,value});this.dispatchEvent(new Event('load'));this.dispatchEvent(new Event('loadend'))},50)}})()");
      js("m.playSong.path=undefined;t.showPlayerComment=true");
      SystemClock.sleep(500);
      assertTrue("横屏评论使用整行宽度", bounds(".landscape-comment").getDouble("width") >= bounds(".landscape-content").getDouble("width") * 0.85);
      capture("landscape-comment-full-width");
      assertEquals("长评论内容实际渲染", "true", js("document.querySelector('.landscape-comment').textContent.includes('充分的阅读空间')"));
      js("t.showPlayerComment=false");
      js("XMLHttpRequest.prototype.open=window.hotfixOpen;XMLHttpRequest.prototype.send=window.hotfixSend");
      SystemClock.sleep(150);
      SystemClock.sleep(2400);
      assertFalse("横屏进入时默认隐藏控件", visible());
      assertMainBounds();
      capture("idle-1.0");
      JSONObject before = bounds(".landscape-content");
      reveal();
      js("t.personalFmMode=false");
      SystemClock.sleep(150);
      assertEquals("普通播放保留两个模式按钮", "2", js("document.querySelectorAll('.landscape-controls .mode-icon').length"));
      assertMainBounds();
      JSONObject after = bounds(".landscape-content");
      assertTrue("隐藏控制层应释放底部歌词空间", before.getDouble("height") >= after.getDouble("height") + 80);
      JSONObject overlay = bounds(".landscape-controls"), root = bounds(".full-player-mobile-landscape");
      save("debug-layout", json("(()=>{const r=document.querySelector('.full-player-mobile-landscape'),l=r.querySelector('.left-section'),i=l.querySelector('.info');return {root:" + root + ",left:" + bounds(".left-section") + ",cover:" + bounds(".landscape-cover") + ",info:" + bounds(".left-section .info") + ",play:" + bounds(".landscape-controls .play-pause") + ",padding:getComputedStyle(r).padding,infoScroll:i.scrollHeight,infoOffset:i.offsetHeight,leftHeight:l.clientHeight,coverSize:getComputedStyle(r).getPropertyValue('--landscape-cover-size'),zoom:getComputedStyle(document.body).zoom}})()"));
      capture("controls-1.0");
      assertCompactActionGroups();
      js("t.personalFmMode=true");
      SystemClock.sleep(150);
      assertEquals("私人FM不显示模式按钮", "0", js("document.querySelectorAll('.landscape-controls .mode-icon').length"));
      assertCompactActionGroups();
      capture("controls-fm-compact");
      js("t.personalFmMode=false");
      assertTrue(overlay.getDouble("y") >= root.getDouble("y"));
      assertTrue(overlay.getDouble("bottom") <= root.getDouble("bottom") + 1);
      assertTrue(overlay.getDouble("right") <= root.getDouble("right") + 1);
      assertTrue("播放按钮可见", bounds(".landscape-controls .play-pause").getDouble("height") >= 48);
      assertTrue("播放按钮不得在控制层外", bounds(".landscape-controls .play-pause").getDouble("bottom") <= overlay.getDouble("bottom") + 1);
      save("geometry-1.0", json("({width:innerWidth,height:innerHeight,cover:" + bounds(".landscape-cover") + ",info:" + bounds(".left-section .info") + ",overlay:" + overlay + "})"));
      capture("controls-1.0");
      SystemClock.sleep(2400);
      assertFalse("无操作后控制层隐藏", visible());
      assertEquals("true", js("document.querySelector('.landscape-controls').inert"));
      assertEquals("\"hidden\"", js("getComputedStyle(document.querySelector('.landscape-controls')).visibility"));

      reveal();
      JSONObject tools = bounds(".header-actions [aria-label='歌词工具']");
      tap((float)(tools.getDouble("x") + tools.getDouble("width") / 2),
          (float)(tools.getDouble("y") + tools.getDouble("height") / 2));
      SystemClock.sleep(2400);
      assertTrue("顶部歌词工具弹层开启时保持显示", visible());
      assertEquals("true", js("!!document.querySelector('.landscape-lyric-tools .lyric-menu')"));
      capture("lyric-tools");
      reveal();
      SystemClock.sleep(2400);
      assertFalse("歌词工具关闭后恢复两秒隐藏", visible());

      reveal();
      JSONObject more = bounds(".landscape-controls .qa-trigger");
      tap((float) (more.getDouble("x") + more.getDouble("width") / 2), (float) (more.getDouble("y") + more.getDouble("height") / 2));
      SystemClock.sleep(2400);
      assertTrue("弹出菜单期间保持控制层", visible());
      js("window.$modal?.destroyAll()");
      JSONObject header = bounds(".landscape-header");
      tap((float) (header.getDouble("right") - 10), (float) (header.getDouble("y") + header.getDouble("height") / 2));
      SystemClock.sleep(2400);

      // 真实原生音频，进度拖动必须改变播放位置。
      js("window.Capacitor.nativePromise('AndroidNativePlayback','load',{url:" + JSONObject.quote(wav.toURI().toString()) + ",autoPlay:false}).then(()=>window.audioLoaded=true)");
      SystemClock.sleep(1200);
      assertEquals("true", js("!!window.audioLoaded"));
      reveal();
      JSONObject slider = bounds(".landscape-controls .n-slider");
      float x = (float) (slider.getDouble("x") + slider.getDouble("width") * 0.3);
      float y = (float) (slider.getDouble("y") + slider.getDouble("height") / 2);
      event(MotionEvent.ACTION_DOWN, x, y);
      for (int i = 0; i < 8; i++) {
        SystemClock.sleep(500);
        event(MotionEvent.ACTION_MOVE, x + i, y);
      }
      assertTrue("拖动超过隐藏时限仍显示", visible());
      capture("seek-hold");
      event(MotionEvent.ACTION_UP, x + 7, y);
      SystemClock.sleep(800);
      js("window.Capacitor.nativePromise('AndroidNativePlayback','getState',{}).then(v=>window.nativeState=v)");
      SystemClock.sleep(300);
      JSONObject nativeState = json("window.nativeState");
      assertTrue("真实 seek 改变原生播放位置", nativeState.getDouble("positionMs") > 1000);
      save("native-seek", nativeState);
      js("window.nativeReloadState=null;window.nodejs.channel.send('embedded-api-reload');");
      SystemClock.sleep(700);
      js("window.Capacitor.nativePromise('AndroidNativePlayback','getState',{}).then(v=>window.nativeReloadState=v)");
      SystemClock.sleep(300);
      JSONObject nativeReloadState = json("window.nativeReloadState");
      assertEquals("服务热重载不得重置原生播放进度", nativeState.getDouble("positionMs"), nativeReloadState.getDouble("positionMs"), 1000);
      save("native-after-reload", nativeReloadState);
      SystemClock.sleep(2400);
      assertFalse("松手后恢复隐藏计时", visible());

      inst.runOnMainSync(() -> web.getSettings().setTextZoom(130));
      js("m.playSong.name='非常长的歌曲标题・ツギハギスタッカート・Adaptive Player';m.playSong.artists=[{id:1,name:'非常长的歌手名称'},{id:2,name:'第二位歌手'}];m.playSong.album={id:2,name:'这是一张名称很长的专辑・長いアルバム'}");
      SystemClock.sleep(900);
      assertMainBounds();
      capture("idle-1.3-long");
      reveal();
      assertMainBounds();
      capture("controls-1.3-long");
      save("geometry-1.3", json("({width:innerWidth,height:innerHeight,textZoom:130,cover:" + bounds(".landscape-cover") + ",info:" + bounds(".left-section .info") + ",overlay:" + bounds(".landscape-controls") + "})"));
      js("s.useAMLyrics=true");
      SystemClock.sleep(1500);
      assertEquals("true", js("!!document.querySelector('.am-lyric')"));
      assertMainBounds();
      capture("amll-1.3");
      js("m.songLyric={lrcData:[],yrcData:[]}");
      SystemClock.sleep(400);
      assertEquals("true", js("!!document.querySelector('.no-lrc')"));
      capture("no-lyric");
      js("t.shuffleMode='on';t.repeatMode='one';window.savedSongId=m.playSong.id");
      rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      assertEquals("true", js("!!document.querySelector('.full-player-mobile')"));
      assertEquals("true", js("m.playSong.id===window.savedSongId&&t.shuffleMode==='on'&&t.repeatMode==='one'"));
      capture("portrait-1.3-return");
      assertPortraitMetadata();
      rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      SystemClock.sleep(2400); assertFalse("再进入横屏应自动隐藏", visible());
      save("result", new JSONObject().put("status", "VERIFIED").put("textZoom", "100/130").put("seekPositionMs", nativeState.getDouble("positionMs")));
      rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      js("(()=>{window.hotfixData=p._s.get('data');window.hotfixLogin=hotfixData.userLoginStatus;window.hotfixLoginType=hotfixData.loginType;hotfixData.userLoginStatus=true;hotfixData.loginType='uid';t.showFullPlayer=false;document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/')})()");
      SystemClock.sleep(1200);
      assertEquals("主页恢复原有双列卡片", "true", js("(()=>{const e=document.querySelector('.home-online .rec-list');if(!e)return false;const a=e.children[0].getBoundingClientRect(),b=e.children[1].getBoundingClientRect();return Math.abs(a.y-b.y)<1&&a.right<=b.x+1})()"));
      capture("home-readable-cards");
      js("hotfixData.userLoginStatus=window.hotfixLogin;hotfixData.loginType=window.hotfixLoginType");
    } finally {
      js("if(window.hotfixOpen){XMLHttpRequest.prototype.open=window.hotfixOpen;XMLHttpRequest.prototype.send=window.hotfixSend}if(window.hotfixData){hotfixData.userLoginStatus=window.hotfixLogin;hotfixData.loginType=window.hotfixLoginType}");
      capture("last-state");
      js("window.Capacitor.nativePromise('AndroidNativePlayback','stop',{})");
      inst.runOnMainSync(() -> {
        web.getSettings().setTextZoom(100);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        activity.finish();
      });
      wav.delete();
    }
  }
}

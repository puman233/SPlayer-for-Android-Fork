package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VolumeTouchInteractionTest {
  private String shell(DeviceWebFixture f,String command) throws Exception {
    try(android.os.ParcelFileDescriptor fd=f.inst.getUiAutomation().executeShellCommand(command);
        java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())) {
      return new String(in.readAllBytes(),StandardCharsets.UTF_8).trim();
    }
  }
  private JSONObject json(DeviceWebFixture f,String expression) throws Exception {
    return new JSONObject((String)new JSONTokener(f.js("JSON.stringify("+expression+")")).nextValue());
  }
  private void touch(DeviceWebFixture f,float x,float y,int action,long down) {
    MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    f.inst.runOnMainSync(()->f.web.dispatchTouchEvent(event));event.recycle();
  }
  private void tap(DeviceWebFixture f,String selector,int index) throws Exception {
    JSONObject r=json(f,"document.querySelectorAll("+JSONObject.quote(selector)+")["+index+"].getBoundingClientRect().toJSON()");
    float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
    float x=(float)(r.getDouble("x")+r.getDouble("width")/2)*scale;
    float y=(float)(r.getDouble("y")+r.getDouble("height")/2)*scale;
    long down=SystemClock.uptimeMillis();touch(f,x,y,MotionEvent.ACTION_DOWN,down);
    SystemClock.sleep(80);touch(f,x,y,MotionEvent.ACTION_UP,down);SystemClock.sleep(350);
  }
  @Test public void touchOpensVolumeWithoutMutingAndSliderChangesVolume() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      int display=f.activity.getDisplay().getDisplayId();
      int orientation=f.activity.getRequestedOrientation();String original=shell(f,"wm size -d "+display);
      var args=InstrumentationRegistry.getArguments();
      int width=Integer.parseInt(args.getString("width","1024")),height=Integer.parseInt(args.getString("height","600"));
      float density=f.activity.getResources().getDisplayMetrics().density;
      shell(f,"wm size "+Math.round(Math.min(width,height)*density)+"x"+Math.round(Math.max(width,height)*density)+" -d "+display);
      f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
      f.await("Math.abs(innerWidth-"+width+")<=1");
      try(var stream=f.inst.getContext().getAssets().open("r23-player-fixture.js")) {
        f.js(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
      }
      f.js("p7Init().then(()=>{p7Stores.setting.autoHidePlayerMeta=false;p7Stores.status.playerMetaShow=true;window.__SPLAYER_PLAYER_CONTROLLER__.setVolume(0.63);window.volumeReady=true})");
      try {
        f.await("window.volumeReady===true&&!!document.querySelector('.player-control .right-menu button[aria-label=\"调节音量\"]')");
        SystemClock.sleep(700);tap(f,".player-control .right-menu button[aria-label=\"调节音量\"]",0);
        f.await("!!document.querySelector('.volume-change')");
        f.capture("volume-open-"+width);
        assertEquals("首次触屏点击只展开音量条，不得改变音量",0.63,Double.parseDouble(f.js("fixtureStatus.playVolume")),0.001);
        JSONObject rail=json(f,"document.querySelector('.volume-change .n-slider-rail').getBoundingClientRect().toJSON()");
        float scale=(float)(f.web.getWidth()/Double.parseDouble(f.js("innerWidth")));
        float x=(float)(rail.getDouble("x")+rail.getDouble("width")/2)*scale;
        float y=(float)(rail.getDouble("top")+rail.getDouble("height")*.37)*scale;
        float end=(float)(rail.getDouble("top")+rail.getDouble("height")*.2)*scale;
        long down=SystemClock.uptimeMillis();
        touch(f,x,y,MotionEvent.ACTION_DOWN,down);
        for(int i=1;i<=12;i++){touch(f,x,y+(end-y)*i/12,MotionEvent.ACTION_MOVE,down);SystemClock.sleep(25);}
        touch(f,x,end,MotionEvent.ACTION_UP,down);
        f.await("Math.abs(fixtureStatus.playVolume-0.8)<0.06");
        double changed=Double.parseDouble(f.js("fixtureStatus.playVolume"));
        tap(f,".volume-change button[aria-label='静音']",0);
        f.await("fixtureStatus.playVolume===0");
        tap(f,".volume-change button[aria-label='取消静音']",0);
        assertEquals("独立静音操作应恢复此前音量",changed,Double.parseDouble(f.js("fixtureStatus.playVolume")),0.001);
      } finally {
        f.js("p7Restore().then(()=>window.__SPLAYER_PLAYER_CONTROLLER__.setVolume(fixtureStatus.playVolume))");f.await("window.p7Restored===true");
        String previous=original.contains("Override size:")?original.substring(original.indexOf("Override size:")+14).trim():"reset";
        shell(f,"wm size "+previous+" -d "+display);
        f.inst.runOnMainSync(()->f.activity.setRequestedOrientation(orientation));
      }
    }
  }
}

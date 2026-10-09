package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ArtistHeaderGeometryTest {
  @Test public void artistSongsAndAlbumsKeepHeaderAndRowsSeparate() throws Exception {
    try(DeviceWebFixture f=new DeviceWebFixture()) {
      f.inst.runOnMainSync(() -> f.activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
      f.await("innerHeight>innerWidth");
      JSONArray songs=new JSONArray(),albums=new JSONArray();
      for(int i=0;i<80;i++) {
        songs.put(new JSONObject().put("id",990100+i).put("name","长歌曲名称 — 测试曲目 "+i)
            .put("ar",new JSONArray().put(new JSONObject().put("id",990001).put("name","测试歌手")))
            .put("al",new JSONObject().put("id",990200+i).put("name","测试专辑").put("picUrl","/images/album.jpg")).put("dt",90000));
        albums.put(new JSONObject().put("id",990200+i).put("name","非常非常长的专辑标题 / Long album title "+i)
            .put("picUrl","/images/album.jpg").put("publishTime",1700000000000L).put("size",10)
            .put("artists",new JSONArray().put(new JSONObject().put("id",990001).put("name","很长很长的歌手名字 / Long Artist"))));
      }
      JSONObject artist=new JSONObject().put("id",990001).put("name","超长歌手名字 / 初音ミク Artist")
          .put("picUrl","/images/artist.jpg").put("briefDesc","这是一段长歌手简介，文字和按钮必须保持独立布局")
          .put("musicSize",12345).put("albumSize",1234).put("mvSize",123);
      JSONObject responses=new JSONObject()
          .put("/artist/detail",new JSONObject().put("code",200).put("data",new JSONObject().put("artist",artist)
              .put("identify",new JSONObject().put("imageDesc","歌手、制作人、作词人"))))
          .put("/artist/songs",new JSONObject().put("code",200).put("songs",songs).put("more",false))
          .put("/song/detail",new JSONObject().put("code",200).put("songs",songs))
          .put("/artist/album",new JSONObject().put("code",200).put("hotAlbums",albums).put("more",false));
      f.js("window.fixtureResponses="+responses+";window.fixtureOpen=XMLHttpRequest.prototype.open;window.fixtureSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(method,url,...args){this.fixtureUrl=String(url);return fixtureOpen.call(this,method,url,...args)};XMLHttpRequest.prototype.send=function(...args){const key=Object.keys(fixtureResponses).find(k=>this.fixtureUrl.split('?')[0].endsWith(k));if(!key)return fixtureSend.apply(this,args);setTimeout(()=>{const body=JSON.stringify(fixtureResponses[key]);Object.defineProperties(this,{readyState:{value:4,configurable:true},status:{value:200,configurable:true},responseText:{value:body,configurable:true},response:{value:body,configurable:true}});this.getAllResponseHeaders=()=> 'content-type: application/json';this.onloadend?.call(this,new Event('loadend'));},30)};");
      try {
        for(String route:new String[]{"artist-songs","artist-albums"}) {
          f.js("fixtureRouter.push({name:'"+route+"',query:{id:990001}})");
          f.await("!!document.querySelector('.artist .menu .left .n-button')");
          f.await(route.equals("artist-songs")?"!!document.querySelector('.artist .song-card')":"document.querySelectorAll('.artist .cover-item').length>10");
          for(int zoom:new int[]{100,130,160}) {
            f.inst.runOnMainSync(()->f.web.getSettings().setTextZoom(zoom));
            SystemClock.sleep(500);
            for(boolean hidden:new boolean[]{false,true}) {
              f.js("fixtureSetting.hiddenCovers.artistDetail="+hidden+";fixtureSetting.hiddenCovers.album="+hidden);
              SystemClock.sleep(300);
            for(int scroll:new int[]{0,300,0}) {
              f.js("(document.querySelector('.artist .n-scrollbar-container')||document.querySelector('.artist .router-view.artist-type')).scrollTop="+scroll);
              SystemClock.sleep(500);
              f.capture(route+"-"+zoom+"-"+hidden+"-"+scroll);
              assertEquals("按钮不重叠、不越界 "+route+" zoom="+zoom,"true",f.js("(()=>{const a=[...document.querySelectorAll('.artist .menu .n-button')].map(e=>e.getBoundingClientRect());return a.length===3&&a.every((r,i)=>r.width>0&&r.right<=innerWidth+1&&a.every((q,j)=>i===j||r.right<=q.left+1||q.right<=r.left+1||r.bottom<=q.top+1||q.bottom<=r.top+1))})()"));
              assertEquals("操作在标题及元数据下方","true",f.js("(()=>{const h=document.querySelector('.artist .detail'),m=h.querySelector('.menu').getBoundingClientRect(),n=h.querySelector('.name').getBoundingClientRect(),c=h.querySelector('.collapse')?.getBoundingClientRect();return m.top>=n.bottom-1&&(!c||c.height<1||m.top>=c.bottom-1)})()"));
              assertEquals("列表不与头部及 tabs 重叠","true",f.js("(()=>{const tabs=document.querySelector('.artist > .tabs').getBoundingClientRect(),row=(document.querySelector('.artist .router-view .n-scrollbar-container')||document.querySelector('.artist .router-view.artist-type')).getBoundingClientRect();return row.top>=tabs.bottom-1})()"));
              if(scroll>0) assertEquals("内容必须真的向下滚动","true",f.js("(document.querySelector('.artist .n-scrollbar-container')||document.querySelector('.artist .router-view.artist-type')).scrollTop>0"));
              if(route.equals("artist-albums")) assertEquals("专辑卡片不能横向溢出","true",f.js("[...document.querySelectorAll('.artist .cover-item')].every(e=>e.getBoundingClientRect().right<=innerWidth+1)"));
            }
            }
          }
        }
      } finally { f.js("XMLHttpRequest.prototype.open=fixtureOpen;XMLHttpRequest.prototype.send=fixtureSend"); }
    }
  }
}

// External runtime inspection only. Real application DOM; synthetic isolated-package data.
window.p7Init = async (beforeRender) => {
  const app=document.querySelector('#app').__vue_app__;
  window.p7App=app.config.globalProperties;
  window.p7Stores={};window.p7Saved={};
  for(const key of ['music','setting','status']){
    window.p7Stores[key]=window.p7App.$pinia._s.get(key);
    if(!window.p7Stores[key])throw Error('Missing isolated store '+key);
    window.p7Saved[key]=JSON.parse(JSON.stringify(window.p7Stores[key].$state));
  }
  window.p7Route=window.p7App.$router.currentRoute.value.fullPath;
  // Freeze the real transport while synthetic UI data is installed, not only its UI flag.
  await window.__SPLAYER_PLAYER_CONTROLLER__.pause();
  for(let attempt=0;attempt<40;attempt++) {
    const state=await window.Capacitor.nativePromise('AndroidNativePlayback','getState',{});
    if(!state.playing)break;
    if(attempt===39)throw Error('Isolated UI fixture could not pause native playback');
    await new Promise(resolve=>setTimeout(resolve,50));
  }

  beforeRender?.();
  window.p7Stores.setting.lastfm.enabled=false;
  Object.assign(window.p7Stores.setting,{playerType:'cover',dynamicCover:false,showSpectrums:false,showTran:true,showRoma:true,showWordLyrics:true,lyricFontSizeMode:'adaptive',androidDeviceModeOverride:'auto'});
  Object.assign(window.p7Stores.status,{playStatus:false,playLoading:false,lyricLoading:false,showPlayerComment:false,pureLyricMode:false,playerMetaShow:true,listSortField:'default'});
  window.p7Short='晚风与星光';
  window.p7Long='晚风与星光——直到夏日的最后一颗流星划过海面 / A Gentle Song with a Very Long Multilingual Title';
  window.p7Songs=Array.from({length:18},(_,i)=>({id:99007100+i,name:i?'隔离曲目 '+(i+1):window.p7Short,type:'song',duration:180000,cover:'/images/song.jpg?asset',artists:[{id:990071,name:'合成歌手 / Synthetic artist'}],album:{id:990072,name:'柔和的夏夜'},path:'/phase7-isolated/'+i+'.wav'}));
  // 通过公开歌曲初始化边界使旧歌词请求失效；无流媒体标识的占位曲不发网络请求。
  window.__SPLAYER_PLAYER_CONTROLLER__.setupSongUI({...window.p7Songs[0],type:'streaming'},0);
  for(let attempt=0;attempt<100;attempt++) {
    if(!window.p7Stores.status.lyricLoading)break;
    if(attempt===99)throw Error('Isolated lyric initialization did not settle');
    await new Promise(resolve=>setTimeout(resolve,50));
  }
  window.p7Stores.music.playSong=window.p7Songs[0];
  window.p7SetLyrics(true);
  window.p7Stores.status.showFullPlayer=true;
};
window.p7SetLyrics=(word=false)=>{
  const lines=Array.from({length:18},(_,i)=>({startTime:i*7000,endTime:(i+1)*7000,words:[{startTime:i*7000,endTime:(i+1)*7000,word:i===0?'晚风轻轻吹过夏夜的海岸':i===1?'如果这一行歌词很长很长很长，温柔的星光仍然落在你的肩膀上':'我们把细小的愿望交给远方 '+i}],translatedLyric:i===1?'A long translation remains readable without covering playback controls.':'The summer breeze carries a gentle wish.',romanLyric:'Wan feng qing qing chui guo xia ye de hai an',isBG:false,isDuet:false}));
  const yrc=lines.map(line=>({...line,words:Array.from(line.words[0].word).map((word,j,a)=>({word,startTime:line.startTime+j*(7000/a.length),endTime:line.startTime+(j+1)*(7000/a.length)}))}));
  window.p7Stores.music.setSongLyric({lrcData:lines,yrcData:word?yrc:[]},true);
};
window.p7Measure=(name)=>{
  const selectors=['.full-player','.full-player-mobile','.top-bar','.mobile-content','.info-page','.cover-section','.info-group','.lyric-page','.lyric-main','.mobile-player-bottom-controls','.control-section','.pagination','.full-player-mobile-landscape','.landscape-header','.landscape-content','.left-section','.landscape-cover','.info','.right-section','.landscape-controls','.player-cover','.player-data','.name-text','.meta-actions-row','.player-lyric','.lyric-scroll-container','.player-control','.tap-restore-overlay','.orientation-backdrop','.orientation-hero','.drag-label','.drop-line'];
  const rect=r=>({x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom});
  const identity=e=>e?{tag:e.tagName,class:String(e.className||'').slice(0,160),label:e.getAttribute('aria-label'),role:e.getAttribute('role')}:null;
  const details=e=>{const r=e.getBoundingClientRect(),c=getComputedStyle(e);return {...identity(e),rect:rect(r),client:[e.clientWidth,e.clientHeight],scroll:[e.scrollWidth,e.scrollHeight],tabIndex:e.tabIndex,inert:e.inert||!!e.closest('[inert]'),ariaHidden:e.getAttribute('aria-hidden'),ariaPressed:e.getAttribute('aria-pressed'),computed:{display:c.display,position:c.position,overflowX:c.overflowX,overflowY:c.overflowY,zIndex:c.zIndex,pointerEvents:c.pointerEvents,visibility:c.visibility,opacity:c.opacity,transform:c.transform,fontSize:c.fontSize,padding:c.padding,gridTemplateRows:c.gridTemplateRows,gridTemplateColumns:c.gridTemplateColumns},centerHit:identity(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))};};
  const elements=[];for(const selector of selectors)document.querySelectorAll(selector).forEach((e,i)=>elements.push({selector,index:i,...details(e)}));
  const controls=Array.from(document.querySelectorAll('.full-player button,.full-player [role=button],.full-player [aria-label],.full-player .btn-icon,.full-player .menu-icon,.full-player .top-bar .btn')).filter(e=>e.getBoundingClientRect().width>0).map(details);
  const targets=Array.from(document.querySelectorAll('.mobile-player-bottom-controls .mode-btn,.mobile-player-bottom-controls .ctrl-btn,.pagination .dot')).map(e=>{const r=e.getBoundingClientRect();return {...details(e),expanded48Probe:[[-24,0],[24,0],[0,-24],[0,24]].map(([dx,dy])=>({dx,dy,hit:identity(document.elementFromPoint(r.x+r.width/2+dx,r.y+r.height/2+dy))}))};});
  const root=document.querySelector('.full-player');const style=getComputedStyle(root||document.documentElement);
  return {name,timestamp:Date.now(),fixture:{song:'synthetic isolated music',paused:true,mode:window.p7Stores.setting.androidDeviceModeOverride,word:window.p7Stores.music.isHasYrc,translation:window.p7Stores.setting.showTran,roman:window.p7Stores.setting.showRoma},viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio,visual:{width:visualViewport.width,height:visualViewport.height,scale:visualViewport.scale},screen:{width:screen.width,height:screen.height},ua:navigator.userAgent},safeArea:Object.fromEntries(['top','right','bottom','left'].map(s=>[s,style.getPropertyValue('--safe-area-'+s).trim()])),scrollRoot:{clientWidth:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth},controlsVisible:document.querySelector('.full-player-mobile-landscape')?.getAttribute('data-controls-visible')||!document.querySelector('.full-player-mobile')?.classList.contains('controls-hidden'),orientationPhase:root?.getAttribute('data-orientation-phase'),elements,controls,targets};
};
window.p7Restore=()=>{window.p7Restored=false;return window.p7RestorePromise=(async()=>{
  window.p7Stores.status.showFullPlayer=false;await window.p7App.$router.replace(window.p7Route);
  for(const key of ['music','setting','status'])Object.assign(window.p7Stores[key].$state,window.p7Saved[key]);
  window.p7Stores.setting.$persist?.();
  window.$modal?.destroyAll();
  if(window.p7Saved.status.playStatus) {
    window.p7Stores.status.playStatus=false;
    await window.__SPLAYER_PLAYER_CONTROLLER__.play();
  }
  window.p7Restored=true;
})();};

window.p24Controls=()=>Array.from(document.querySelectorAll('.mobile-player-bottom-controls .control-section > button,.mobile-player-bottom-controls .control-section > .mode-btn,.mobile-player-bottom-controls .control-section > .ctrl-btn,.player-control .btn > button,.player-control .btn > .btn-icon')).filter(e=>e.getBoundingClientRect().width>0&&!e.closest('[inert]')&&getComputedStyle(e).visibility!=='hidden');
window.p24State=()=>({viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},calls:window.p24Calls.slice(),controls:window.p24Controls().map(e=>({tag:e.tagName,label:e.getAttribute('aria-label'),pressed:e.getAttribute('aria-pressed'),tabIndex:e.tabIndex,focused:document.activeElement===e,focusVisible:e.matches(':focus-visible'),outline:getComputedStyle(e).outline,rect:e.getBoundingClientRect().toJSON(),icon:e.querySelector('.n-icon')?.getBoundingClientRect().toJSON()})),active:{tag:document.activeElement.tagName,label:document.activeElement.getAttribute('aria-label'),inert:!!document.activeElement.closest('[inert]')},hidden:Array.from(document.querySelectorAll('.full-player [inert]')).map(e=>({class:e.className,containsFocus:e.contains(document.activeElement)}))});
window.p24Prepare=async()=>{
 await window.p7Init();window.p24Calls=[];window.p24Original={};
 const player=window.__SPLAYER_PLAYER_CONTROLLER__;
 // Isolated UI command boundary: count commands; real native playback is tested separately.
 for(const name of ['toggleShuffle','toggleRepeat','nextOrPrev','playOrPause']){
  window.p24Original[name]=player[name];player[name]=(...args)=>{
   window.p24Calls.push({name,args});
   if(name==='toggleShuffle')window.p7Stores.status.shuffleMode=window.p7Stores.status.shuffleMode==='off'?'on':'off';
   if(name==='toggleRepeat')window.p7Stores.status.repeatMode={off:'list',list:'one',one:'off'}[window.p7Stores.status.repeatMode];
   if(name==='playOrPause')window.p7Stores.status.playStatus=!window.p7Stores.status.playStatus;
  };
 }
 window.p7Stores.status.shuffleMode='off';window.p7Stores.status.repeatMode='off';
};
window.p24Restore=async()=>{Object.assign(window.__SPLAYER_PLAYER_CONTROLLER__,window.p24Original);await window.p7Restore()};

window.r23Pages=()=>Array.from(document.querySelectorAll('.mobile-player-bottom-controls .pagination .dot'));
window.r23Targets=()=>Array.from(document.querySelectorAll('.mobile-player-bottom-controls .control-section > button,.mobile-player-bottom-controls .control-section > .mode-btn,.mobile-player-bottom-controls .control-section > .ctrl-btn'));
window.r23Geometry=()=>{
 const rect=e=>e.getBoundingClientRect().toJSON();
 const detail=e=>({tag:e.tagName,label:e.getAttribute('aria-label'),rect:rect(e),marker:rect(e.querySelector('.dot-marker')||e),active:e.getAttribute('aria-current'),inert:!!e.closest('[inert]'),hits:[[1,1],[-1,1],[1,-1],[-1,-1]].map(([x,y])=>{const r=e.getBoundingClientRect();const hit=document.elementFromPoint(x>0?r.left+x:r.right+x,y>0?r.top+y:r.bottom+y);return hit===e||e.contains(hit)})});
 const footer=document.querySelector('.mobile-player-bottom-controls'),lyric=document.querySelector('.lyric-main'),root=document.querySelector('.full-player-mobile');
 return {viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},footer:footer?rect(footer):null,footerObserved:root?getComputedStyle(root).getPropertyValue('--lyric-footer-height'):null,lyric:lyric?{rect:rect(lyric),clip:getComputedStyle(lyric).clipPath}:null,controls:window.r23Targets().map(detail),pages:window.r23Pages().map(detail),overflow:document.documentElement.scrollWidth>document.documentElement.clientWidth,visible:footer?!footer.inert&&getComputedStyle(footer).visibility!=='hidden':false,safeBottom:root?getComputedStyle(root).getPropertyValue('--mobile-safe-bottom'):null};
};
window.r23PageObserve=()=>{
 const seen=new Set();const find=v=>{if(!v||typeof v!=='object'||seen.has(v))return null;seen.add(v);if(v.component?.type?.__name==='MobilePlayerBottomControls')return v.component;return find(v.component?.subTree)||find(v.suspense?.activeBranch)||(Array.isArray(v.children)?v.children.map(find).find(Boolean):null)};
 const component=find(document.querySelector('#app')._vnode);if(!component)throw Error('Actual component unavailable');
 window.r23Events=[];const props=component.vnode.props;const current=props['onUpdate:pageIndex'];window.r23Handlers ||= new WeakMap();const original=window.r23Handlers.get(current)||current;const wrapper=(value)=>{window.r23Events.push(value);return original(value)};window.r23Handlers.set(wrapper,original);props['onUpdate:pageIndex']=wrapper;
};

window.r23CommentsOn=()=>{
 window.r23Open=XMLHttpRequest.prototype.open;window.r23Send=XMLHttpRequest.prototype.send;const urls=new WeakMap();
 XMLHttpRequest.prototype.open=function(method,url,...args){urls.set(this,String(url));return window.r23Open.call(this,method,url,...args)};
 XMLHttpRequest.prototype.send=function(body){const url=urls.get(this)||'';if(!url.includes('/comment'))return window.r23Send.call(this,body);setTimeout(()=>{const text=JSON.stringify({code:200,total:0,comments:[],hotComments:[],more:false,data:{comments:[],totalCount:0,hasMore:false}});for(const [name,value]of Object.entries({status:200,statusText:'OK',readyState:4,responseText:text,response:text,responseURL:url}))Object.defineProperty(this,name,{configurable:true,value});this.getAllResponseHeaders=()=> 'content-type: application/json';this.dispatchEvent(new Event('readystatechange'));this.dispatchEvent(new Event('load'));this.dispatchEvent(new Event('loadend'))},0)};
 window.p7Stores.setting.fullscreenPlayerElements={...window.p7Stores.setting.fullscreenPlayerElements,comments:true};window.p7Stores.music.playSong={...window.p7Songs[0],path:undefined};
};
window.r23CommentsOff=()=>{if(window.r23Open){XMLHttpRequest.prototype.open=window.r23Open;XMLHttpRequest.prototype.send=window.r23Send;delete window.r23Open;delete window.r23Send;}};

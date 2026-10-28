/* 页面只负责本地视图；采集会话与其他浏览器不会因筛选、暂停或清空而改变。 */
'use strict';
const $=id=>document.getElementById(id),auth=location.search, token=new URLSearchParams(auth).get('token')||'';
const endpoint=(path,params={})=>path+'?'+new URLSearchParams({token,...params});
const read=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))??fallback;}catch{return fallback;}};
let selected=new Set(read('packages',[])), apps=[],records=[],cacheBytes=0,cursor=0,session=null;
let groups=[],heights=[0],follow=true,paused=false,pauseCursor=Infinity,clearBefore=0,selectedId=0;
let worker=null,workerBusy=false,workerTimer=null,workerFailed=false,pending=[],job=0,revision=0,now=Date.now(),status={},lastUpdate=0;
let history=read('history',[]),favorites=read('favorites',[]),frozenGroups=null;
let pendingView=false,appSearchTimer,queryTimer,renderFrame=0;
let selectingLogs=false,horizontalScrolling=false,horizontalScrollTimer,renderDeferred=false,indexDeferred=false;
const horizontalOffsets=new Map();
function hasLogSelection(){const selection=window.getSelection();return !!selection&&!selection.isCollapsed&&selection.rangeCount>0&&selection.getRangeAt(0).intersectsNode($('viewport'));}
function selectionBlocksRender(){return selectingLogs||horizontalScrolling||hasLogSelection();}
function resumeDeferredRender(){
 if(selectionBlocksRender())return;
 if(indexDeferred){indexDeferred=false;renderDeferred=false;indexGroups();}
 else if(renderDeferred){renderDeferred=false;scheduleRender();}
}
function persist(key,value){try{localStorage.setItem(key,JSON.stringify(value));}catch{}}
function workerStart(){
 worker=new Worker(endpoint('/filter-worker.js'));pending=records.slice();
 worker.onmessage=event=>{
  clearTimeout(workerTimer);workerBusy=false;const data=event.data;
  if(data.id!==job)return;
  if(data.error){$('query-error').textContent=data.error;}
  else if(data.revision===undefined && workerRevision===revision){
   $('query-error').textContent='';
   if(!paused||pendingView){groups=data.groups;frozenGroups=paused?groups:null;pendingView=false;indexGroups();}
  }
  updateStats();if(pending.length||workerRevision!==revision)filter();
 };
 worker.onerror=()=>failWorker('查询线程异常，请修改条件后重试');
}
let workerRevision=0;
function failWorker(message){clearTimeout(workerTimer);worker?.terminate();worker=null;workerBusy=false;workerFailed=true;$('query-error').textContent=message;}
function filter(force=false){
 if(force){revision++;pendingView=true;if(workerFailed){workerFailed=false;workerStart();}}
 if(workerFailed||workerBusy)return;
 if(!worker)workerStart();workerBusy=true;workerRevision=revision;
 const min=$('level').value;const base=$('query').value.trim();
 const query=min?(base?'('+base+') & ':'')+'level:'+min:base;
 worker.postMessage({id:++job,append:pending.splice(0),query,packages:[...selected],caseSensitive:$('case-sensitive').checked,now,clearBefore,pauseBefore:paused?pauseCursor:null});
 workerTimer=setTimeout(()=>failWorker('过滤执行超时，已终止正则。请简化表达式后重试。'),1500);
}
function indexGroups(){if(selectionBlocksRender()){indexDeferred=true;return;}heights=[0];for(const row of groups)heights.push(heights[heights.length-1]+50+20*row.message.split('\n').length);$('virtual-space').style.height=heights[heights.length-1]+'px';$('empty').hidden=groups.length>0;
 if(!groups.length){$('empty').querySelector('h2').textContent=records.length?'没有匹配的日志':'等待日志';$('empty').querySelector('p').textContent=records.length?'调整查询、应用范围或清空条件。':'启动手机服务，并允许设备日志访问。';}
 if(follow&&!paused)$('viewport').scrollTop=heights[heights.length-1];scheduleRender();updateStats();}
function lowerBound(value){let left=0,right=groups.length;while(left<right){const mid=(left+right)>>1;if(heights[mid+1]<value)left=mid+1;else right=mid;}return left;}
function scheduleRender(){if(selectionBlocksRender()){renderDeferred=true;return;}if(renderFrame)return;renderFrame=requestAnimationFrame(()=>{renderFrame=0;if(selectionBlocksRender()){renderDeferred=true;return;}render();});}
function render(){
 const view=$('viewport'),start=Math.max(0,lowerBound(view.scrollTop)-5),end=Math.min(groups.length,lowerBound(view.scrollTop+view.clientHeight)+8);
 const fragment=document.createDocumentFragment();
 for(let i=start;i<end;i++){
  const e=groups[i],row=document.createElement('div');row.className='log-row'+(selectedId===e.id?' selected':'');row.style.height=(heights[i+1]-heights[i])+'px';row.dataset.id=e.id;row.title='点击选择日志，再查看上下文';
  const meta=document.createElement('div');meta.className='log-meta';
  const stamp=document.createElement('span');stamp.textContent=new Date(e.time).toLocaleTimeString('zh-CN',{hour12:false})+'.'+String(e.time%1000).padStart(3,'0');meta.append(stamp);
  const pkg=document.createElement('span');pkg.className='pkg';pkg.textContent=e.package||'归属未知';pkg.title=e.package||'无法从 UID 唯一确定包名';meta.append(pkg);
  const proc=document.createElement('span');proc.textContent=(e.process||'进程未知')+' · '+e.pid+':'+e.tid+' · uid '+e.uid;meta.append(proc);
  if(e.lines>1){const label=document.createElement('span');label.className='group-label';label.textContent='堆栈组 · '+e.lines+' 行';meta.append(label);}
  row.append(meta);const message=document.createElement('pre');message.className='log-message';
  const level=document.createElement('span');level.className='level level-'+e.level;level.textContent=e.level+' ';message.append(level);
  const tag=document.createElement('span');tag.className='tag';tag.textContent=e.tag+'  ';message.append(tag,document.createTextNode(e.message));row.append(message);
  row.onclick=()=>{if(hasLogSelection()||horizontalScrolling)return;selectedId=e.id;scheduleRender();};fragment.append(row);
 }
 $('rows').style.transform='translateY('+heights[start]+'px)';$('rows').replaceChildren(fragment);
 for(const message of $('rows').querySelectorAll('.log-message'))message.scrollLeft=horizontalOffsets.get(message.parentElement.dataset.id)||0;
}
function updateStats(){const count=groups.reduce((total,g)=>total+g.lines,0);$('stats').textContent='匹配 '+count.toLocaleString()+' 条 / '+groups.length.toLocaleString()+' 组 · 缓存 '+records.length.toLocaleString()+' 条 · '+(cacheBytes/1048576).toFixed(1)+' MB'+(status.dropped?' · 手机已淘汰 '+status.dropped+' 条':'')+(paused?' · 显示已暂停':'');}
function appList(){
 const search=$('app-search').value.toLocaleLowerCase(),showSystem=$('system-apps').checked;
 const visible=apps.filter(a=>(showSystem||!a.system||selected.has(a.package))&&(!search||(a.name+' '+a.package).toLocaleLowerCase().includes(search)));
 const fragment=document.createDocumentFragment();
 for(const app of visible){
  const label=document.createElement('label');label.className='app'+(selected.has(app.package)?' selected':'');
  const box=document.createElement('input');box.type='checkbox';box.checked=selected.has(app.package);box.setAttribute('aria-label',app.package);box.onchange=()=>{if(box.checked)selected.add(app.package);else selected.delete(app.package);selectionChanged();};
  const icon=document.createElement('img');icon.src=endpoint('/icon',{package:app.package});icon.loading='lazy';icon.alt='';icon.onerror=()=>{icon.style.visibility='hidden';};
  const text=document.createElement('div');text.className='app-text';const name=document.createElement('div');name.className='app-name';name.textContent=app.name;const pkg=document.createElement('div');pkg.className='app-package';pkg.textContent=app.package;text.append(name,pkg);label.append(box,icon,text);fragment.append(label);
 }
 $('apps').replaceChildren(fragment);$('selected-count').textContent=selected.size?'已选 '+selected.size+' 个应用':'全部日志';$('scope').textContent=selected.size?[...selected].join('、'):'全部应用';$('scope').title=$('scope').textContent;
 if(!visible.length){const text=document.createElement('p');text.className='app-summary';text.textContent='未找到应用';$('apps').append(text);}
}
function selectionChanged(){persist('packages',[...selected]);appList();filter(true);}
async function loadApps(refresh=false){
 $('refresh-apps').disabled=true;
 try{const response=await fetch(endpoint('/apps',refresh?{refresh:1}:{}));if(!response.ok)throw new Error('应用列表请求失败');const data=await response.json();if(!data.ready){setTimeout(()=>loadApps(),1000);return;}apps=data.apps;appList();}
 catch(error){$('query-error').textContent=error.message;}finally{$('refresh-apps').disabled=false;}
}
async function poll(){
 let delay=700;
 try{
  const response=await fetch(endpoint('/logs',{after:cursor}),{signal:AbortSignal.timeout(6000)});
  if(!response.ok)throw new Error(response.status===403?'链接授权失效，请重新扫码':'HTTP '+response.status);
  const data=await response.json();now=data.now;status=data;lastUpdate=Date.now();
  if(session!==null&&session!==data.session){records=[];cacheBytes=0;cursor=0;clearBefore=0;pending=[];horizontalOffsets.clear();worker?.terminate();worker=null;workerBusy=false;clearTimeout(workerTimer);groups=[];filter(true);session=data.session;delay=0;return;}
  session=data.session;
  if(data.cursor<cursor){cursor=0;delay=0;return;}
  if(cursor>0&&data.oldest>cursor+1)$('query-error').textContent='缓存已淘汰部分历史日志，已从可用位置继续。';
  for(const e of data.records){if(e.id<=cursor)continue;records.push(e);pending.push(e);cacheBytes+=e.bytes||256;}
  let remove=0;while(records.length-remove>50000||cacheBytes>32*1024*1024){cacheBytes-=records[remove++].bytes||256;}if(remove)records.splice(0,remove);
  cursor=data.cursor;filter();
  $('connection').textContent='● 已连接 · '+location.host;
  $('reader-status').textContent=data.error||(!data.readLogsGranted?'需通过 ADB 授予 READ_LOGS':!data.readerAlive?'采集进程已退出，请在手机重新请求':!data.lastForeignAt?'尚未确认读到跨 UID 日志，请检查手机授权':'正在采集 · UID '+data.uid);
  if(data.cursor<data.latest)delay=10;
 }catch(error){$('connection').textContent='○ 连接中断';$('reader-status').textContent=error.message+'，自动重连中';delay=2000;}
 finally{setTimeout(poll,delay);}
}
function remember(){const query=$('query').value.trim();if(!query)return;history=[query,...history.filter(q=>q!==query)].slice(0,30);persist('history',history);updateHistory();}
function queryLabel(q){try{return LogQuery.compile(q,{packages:[...selected]}).label||q;}catch{return q;}}
function updateHistory(){const list=$('history');list.replaceChildren(new Option('历史与收藏',''));for(const q of favorites)list.add(new Option('★ '+queryLabel(q),q));for(const q of history.filter(q=>!favorites.includes(q)))list.add(new Option(queryLabel(q),q));}
function suggestions(){
 const value=$('query').value,part=/^(.*?)(-?)(package|tag|process|level|is|age|pid|tid|uid):([^\s]*)$/.exec(value);
 let values;
 const quote=text=>/[^a-zA-Z0-9_.$:/-]/.test(text)?JSON.stringify(text):text;
 if(part){
  const field=part[3],needle=part[4].toLowerCase();let candidates=[];
  if(field==='package')candidates=['mine',...apps.map(a=>a.package)];
  else if(field==='level')candidates=['VERBOSE','DEBUG','INFO','WARN','ERROR','ASSERT'];
  else if(field==='is')candidates=['crash','stacktrace'];
  else if(field==='age')candidates=['30s','5m','1h','1d'];
  else candidates=[...new Set(records.slice(-5000).map(e=>String(e[field]??'')).filter(Boolean))];
  values=candidates.filter(v=>v.toLowerCase().includes(needle)).slice(0,30).map(v=>part[1]+part[2]+field+':'+quote(v));
 }else{
  const prefix=value.replace(/[^\s]*$/,'');
  values=['package:mine','tag:','-tag:','message:','line:','message~:""','level:WARN','age:5m','is:crash','is:stacktrace','pid:','tid:','uid:','process:','name:'].map(s=>prefix+s);
 }
 $('suggestions').replaceChildren(...values.map(value=>new Option('',value)));
 $('save-query').textContent=favorites.includes(value.trim())?'★':'☆';
}
$('query').value=read('query','');$('case-sensitive').checked=read('caseSensitive',true);$('system-apps').checked=read('systemApps',false);$('level').value=read('level','');
$('query').oninput=()=>{persist('query',$('query').value);suggestions();clearTimeout(queryTimer);queryTimer=setTimeout(()=>filter(true),150);};$('query').onchange=remember;$('query').onkeydown=event=>{if(event.key==='Enter'){remember();filter(true);}};
$('level').onchange=()=>{persist('level',$('level').value);filter(true);};$('case-sensitive').onchange=()=>{persist('caseSensitive',$('case-sensitive').checked);filter(true);};
$('history').onchange=()=>{if($('history').value){$('query').value=$('history').value;persist('query',$('query').value);filter(true);}};
$('save-query').onclick=()=>{const value=$('query').value.trim();if(!value)return;favorites=favorites.includes(value)?favorites.filter(q=>q!==value):[value,...favorites].slice(0,30);persist('favorites',favorites);$('save-query').textContent=favorites.includes(value)?'★':'☆';remember();updateHistory();};
$('app-search').oninput=()=>{clearTimeout(appSearchTimer);appSearchTimer=setTimeout(appList,100);};$('system-apps').onchange=()=>{persist('systemApps',$('system-apps').checked);appList();};$('refresh-apps').onclick=()=>loadApps(true);$('deselect').onclick=()=>{selected.clear();selectionChanged();};
$('pause').onclick=()=>{paused=!paused;pauseCursor=paused?cursor:Infinity;frozenGroups=paused?groups:null;$('pause').textContent=paused?'▶ 恢复显示':'Ⅱ 暂停显示';$('pause').classList.toggle('active',paused);filter(true);};
$('follow').onclick=()=>{if(hasLogSelection())window.getSelection().removeAllRanges();selectedId=0;follow=true;$('follow').classList.add('active');resumeDeferredRender();$('viewport').scrollTop=heights[heights.length-1];scheduleRender();};
$('viewport').addEventListener('wheel',()=>{follow=false;$('follow').classList.remove('active');},{passive:true});$('viewport').addEventListener('touchmove',()=>{follow=false;$('follow').classList.remove('active');},{passive:true});$('viewport').addEventListener('scroll',scheduleRender,{passive:true});
$('viewport').addEventListener('scroll',event=>{
 const message=event.target;if(!message.classList?.contains('log-message'))return;
 const id=message.parentElement.dataset.id,offset=message.scrollLeft;
 if((horizontalOffsets.get(id)||0)===offset)return;
 if(offset)horizontalOffsets.set(id,offset);else horizontalOffsets.delete(id);
 while(horizontalOffsets.size>50000)horizontalOffsets.delete(horizontalOffsets.keys().next().value);
 horizontalScrolling=true;follow=false;$('follow').classList.remove('active');
 clearTimeout(horizontalScrollTimer);horizontalScrollTimer=setTimeout(()=>{horizontalScrolling=false;resumeDeferredRender();},350);
},true);
$('viewport').addEventListener('pointerdown',()=>{selectingLogs=true;});
window.addEventListener('pointerup',()=>{if(selectingLogs){selectingLogs=false;setTimeout(resumeDeferredRender,0);}});
window.addEventListener('pointercancel',()=>{selectingLogs=false;setTimeout(resumeDeferredRender,0);});
document.addEventListener('selectionchange',()=>{if(hasLogSelection()){follow=false;$('follow').classList.remove('active');}else resumeDeferredRender();});
$('clear').onclick=()=>{clearBefore=cursor;selectedId=0;horizontalOffsets.clear();groups=[];indexGroups();filter(true);};
$('context').onclick=()=>{const chosen=selectedId||groups[groups.length-1]?.id,index=records.findIndex(e=>e.id===chosen);if(index<0){$('query-error').textContent='请选择缓存内的一条日志';return;}$('context-content').textContent=records.slice(Math.max(0,index-10),index+21).map(e=>e.line).join('\n');$('context-dialog').showModal();};$('close-context').onclick=()=>$('context-dialog').close();
let exportUrl='';
$('export').onclick=()=>{
 const all=$('export-scope').value==='all';
 const text=all?records.map(e=>e.line).join('\n'):groups.map(e=>'['+new Date(e.time).toISOString()+' '+(e.package||'归属未知')+' '+e.pid+':'+e.tid+' '+e.level+'/'+e.tag+']\n'+e.message).join('\n\n');
 if(exportUrl)URL.revokeObjectURL(exportUrl);
 const blob=new Blob([text],{type:'text/plain;charset=utf-8'});exportUrl=URL.createObjectURL(blob);
 $('download-export').href=exportUrl;$('download-export').download='weblogcat-'+(all?'all':'filtered')+'-'+Date.now()+'.log';
 $('export-preview').value=text;$('export-summary').textContent=(all?'全部缓存':'当前过滤结果')+' · '+blob.size.toLocaleString()+' 字节';
 $('copy-export').textContent='复制导出内容';$('export-dialog').showModal();
};
$('close-export').onclick=()=>$('export-dialog').close();
$('copy-export').onclick=async()=>{
 const preview=$('export-preview');
 try{if(navigator.clipboard&&window.isSecureContext)await navigator.clipboard.writeText(preview.value);else{preview.focus();preview.select();if(!document.execCommand('copy'))throw new Error();}$('copy-export').textContent='已复制';}
 catch{$('copy-export').textContent='请在内容框内全选并复制';preview.focus();preview.select();}
};
$('help-toggle').onclick=()=>$('help').hidden=!$('help').hidden;$('menu').onclick=()=>document.body.classList.toggle('apps-open');
function setLogFullscreen(active){document.body.classList.toggle('log-fullscreen',active);const button=$('log-fullscreen');button.textContent=active?'⛶ 退出全屏':'⛶ 日志全屏';button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));document.body.classList.remove('apps-open');scheduleRender();}
$('log-fullscreen').onclick=async()=>{
 if(document.body.classList.contains('log-fullscreen')){
  if(document.fullscreenElement&&document.exitFullscreen){try{await document.exitFullscreen();}catch(e){}}
  setLogFullscreen(false);return;
 }
 setLogFullscreen(true);
 if(document.documentElement.requestFullscreen){try{await document.documentElement.requestFullscreen();}catch(e){}}
};
document.addEventListener('fullscreenchange',()=>{if(!document.fullscreenElement&&document.body.classList.contains('log-fullscreen'))setLogFullscreen(false);});
$('theme').value=localStorage.getItem('theme')||'system';$('theme').onchange=()=>{document.documentElement.dataset.theme=$('theme').value;localStorage.setItem('theme',$('theme').value);};
window.addEventListener('resize',scheduleRender);setInterval(()=>{if(status.now){now=status.now+(Date.now()-lastUpdate);filter();}},1000);
updateHistory();suggestions();filter(true);loadApps();poll();

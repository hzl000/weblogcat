/* 正则只在工作线程执行，超时由页面终止，避免冻结操作界面。 */
importScripts('query.js'+self.location.search);
let records=[],bytes=0,offset=0,retained=[],retainedKey=null;
function stackline(message){return /(^\s*(at\s|Caused by:|Suppressed:|\.\.\. \d+ more)|Exception\b|Error\b)/m.test(message);}
function crashline(e){return /FATAL EXCEPTION|Fatal signal|Abort message:|>>> .* <<</.test(e.message)||(e.tag==='DEBUG'&&e.level==='F');}
self.onmessage=event=>{
 const input=event.data;
 if(input.reset){records=[];bytes=0;offset=0;retained=[];retainedKey=null;}
 for(const entry of input.append||[]){records.push(entry);bytes+=entry.bytes||256;}
 try{
  const query=LogQuery.compile(input.query,{caseSensitive:input.caseSensitive,packages:input.packages});
  // 筛选条件变化后只从当前原始缓存重新建立保留结果。
  const key=JSON.stringify([input.query,!!input.caseSensitive,[...(input.packages||[])].sort()]);
  if(key!==retainedKey){retained=[];retainedKey=key;}
  const active=!!input.query.trim()||!!input.packages?.length;
  const candidates=new Map(retained.map(entry=>[entry.id,entry]));
  for(let i=offset;i<records.length;i++)candidates.set(records[i].id,records[i]);
  const groups=[];let current=null;
  for(const entry of [...candidates.values()].sort((a,b)=>a.id-b.id)){
   entry.stacktrace=stackline(entry.message);entry.crash=crashline(entry);
   const continuation=current&&current.pid===entry.pid&&current.tid===entry.tid&&current.tag===entry.tag&&entry.time-current.lastTime<=2000&&(entry.stacktrace||current.crash&&['E','F'].includes(entry.level));
   if(continuation){current.entries.push(entry);current.lastTime=entry.time;current.crash ||= entry.crash;current.stacktrace ||=entry.stacktrace;}
   else{current={id:entry.id,pid:entry.pid,tid:entry.tid,tag:entry.tag,lastTime:entry.time,crash:entry.crash,stacktrace:entry.stacktrace,entries:[entry]};groups.push(current);}
  }
  const matched=[];
  for(const group of groups){
   if(group.entries[group.entries.length-1].id<=input.clearBefore)continue;
   const scoped=group.entries.filter(e=>!input.packages?.length||input.packages.includes(e.package));
   if(!scoped.length)continue;
   if(scoped.some(e=>query.test({...e,crash:group.crash,stacktrace:group.stacktrace},{now:input.now}))){
    matched.push(group);
   }
  }
  // 保留完整日志组，达到独立预算时淘汰最旧的组。
  let count=0,size=0,start=matched.length;
  while(start>0){
   const group=matched[start-1],groupSize=group.entries.reduce((sum,e)=>sum+(e.bytes||256),0);
   if(count+group.entries.length>50000||size+groupSize>32*1024*1024)break;
   count+=group.entries.length;size+=groupSize;start--;
  }
  const kept=matched.slice(start);
  retained=active?kept.flatMap(group=>group.entries):[];
  const result=kept.filter(group=>input.pauseBefore==null||group.id<=input.pauseBefore).map(group=>{
   const entries=input.pauseBefore==null?group.entries:group.entries.filter(e=>e.id<=input.pauseBefore);
   const {line,bytes,...display}=entries[0];
   return {...display,crash:group.crash,stacktrace:group.stacktrace,ids:entries.map(e=>e.id),message:entries.map(e=>e.message).join('\n'),lines:entries.length,context:false};
  });
  self.postMessage({id:input.id,groups:result,retainedCount:retained.length,retainedBytes:active?size:0,evicted:start>0,label:query.label});
 }catch(error){self.postMessage({id:input.id,error:error.message});}
 while(records.length-offset>50000||bytes>32*1024*1024){bytes-=records[offset++].bytes||256;}
 if(offset>2000){records=records.slice(offset);offset=0;}
};

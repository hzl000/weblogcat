/* 正则只在工作线程执行，超时由页面终止，避免冻结操作界面。 */
importScripts('query.js'+self.location.search);
let records=[],bytes=0,offset=0;
function stackline(message){return /(^\s*(at\s|Caused by:|Suppressed:|\.\.\. \d+ more)|Exception\b|Error\b)/m.test(message);}
function crashline(e){return /FATAL EXCEPTION|Fatal signal|Abort message:|>>> .* <<</.test(e.message)||(e.tag==='DEBUG'&&e.level==='F');}
self.onmessage=event=>{
 const input=event.data;
 if(input.reset){records=[];bytes=0;offset=0;}
 for(const entry of input.append||[]){records.push(entry);bytes+=entry.bytes||256;}
 while(records.length-offset>50000||bytes>32*1024*1024){bytes-=records[offset++].bytes||256;}
 if(offset>2000){records=records.slice(offset);offset=0;}
 try{
  const query=LogQuery.compile(input.query,{caseSensitive:input.caseSensitive,packages:input.packages});
  const groups=[];let current=null;
  for(let i=offset;i<records.length;i++){
   const entry=records[i];if(input.pauseBefore!=null && entry.id>input.pauseBefore)continue;entry.stacktrace=stackline(entry.message);entry.crash=crashline(entry);
   const continuation=current&&current.pid===entry.pid&&current.tid===entry.tid&&current.tag===entry.tag&&entry.time-current.lastTime<=2000&&(entry.stacktrace||current.crash&&['E','F'].includes(entry.level));
   if(continuation){current.entries.push(entry);current.lastTime=entry.time;current.crash ||= entry.crash;current.stacktrace ||=entry.stacktrace;}
   else{current={id:entry.id,pid:entry.pid,tid:entry.tid,tag:entry.tag,lastTime:entry.time,crash:entry.crash,stacktrace:entry.stacktrace,entries:[entry]};groups.push(current);}
  }
  const result=[];
  for(const group of groups){
   if(group.entries[group.entries.length-1].id<=input.clearBefore)continue;
   const scoped=group.entries.filter(e=>!input.packages?.length||input.packages.includes(e.package));
   if(!scoped.length)continue;
   if(scoped.some(e=>query.test({...e,crash:group.crash,stacktrace:group.stacktrace},{now:input.now}))){
    const first=group.entries[0];const {line,bytes,...display}=first;result.push({...display,crash:group.crash,stacktrace:group.stacktrace,ids:group.entries.map(e=>e.id),message:group.entries.map(e=>e.message).join('\n'),lines:group.entries.length,context:false});
   }
  }
  self.postMessage({id:input.id,groups:result,count:records.length-offset,bytes,label:query.label});
 }catch(error){self.postMessage({id:input.id,error:error.message});}
};

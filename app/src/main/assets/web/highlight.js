(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LogHighlight=api;
})(globalThis,function(){
 'use strict';
 const escapeRegExp=value=>value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
 function segments(value,terms){
  const active=terms.map((term,index)=>({text:term.text,index})).filter(term=>term.text).sort((a,b)=>b.text.length-a.text.length||a.index-b.index);
  if(!active.length)return [{text:value,index:-1}];
  const pattern=new RegExp(active.map(term=>escapeRegExp(term.text)).join('|'),'giu'),result=[];let cursor=0,match;
  while((match=pattern.exec(value))){
   if(match.index>cursor)result.push({text:value.slice(cursor,match.index),index:-1});
   const normalized=match[0].toLowerCase(),term=terms.findIndex(item=>item.text.toLowerCase()===normalized);
   result.push({text:match[0],index:term});cursor=pattern.lastIndex;
  }
  if(cursor<value.length)result.push({text:value.slice(cursor),index:-1});
  return result.length?result:[{text:value,index:-1}];
 }
 return {segments};
});

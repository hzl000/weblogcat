/* 查询语法独立于页面，供浏览器、工作线程和测试共用。 */
(function(root){
'use strict';
const keys=new Set(['package','process','tag','message','line','pid','tid','uid','level','age','is','name']);
const levels={V:0,VERBOSE:0,D:1,DEBUG:1,I:2,INFO:2,W:3,WARN:3,WARNING:3,E:4,ERROR:4,F:5,FATAL:5,ASSERT:5};
function tokenize(source){
 const tokens=[];let i=0;
 while(i<source.length){
  if(/\s/.test(source[i])){i++;continue;}
  if('()&|'.includes(source[i])){tokens.push({op:source[i++]});continue;}
  let value='',quote=null,position=i;
  while(i<source.length){
   const ch=source[i];
   if(ch==='\\'&&i+1<source.length){const next=source[i+1];if(next==='"'||next==="'"||next==='\\'){value+=next;i+=2;continue;}value+=ch;i++;continue;}
   if(quote){if(ch===quote)quote=null;else value+=ch;i++;continue;}
   if(ch==='"'||ch==="'"){quote=ch;i++;continue;}
   if(/\s/.test(ch)||'()&|'.includes(ch))break;
   value+=ch;i++;
  }
  if(quote)throw new Error('引号未闭合（位置 '+(position+1)+'）');
  if(/^-?[a-z]+~?:$/i.test(value)){
   while(i<source.length&&/\s/.test(source[i]))i++;
   const rest=tokenize(source.slice(i));if(!rest.length||rest[0].op)throw new Error('字段缺少值');
   tokens.push({value:value+rest[0].value});tokens.push(...rest.slice(1));return tokens;
  }
  if(value)tokens.push({value,literal:source[position]==='"'||source[position]==="'"});else throw new Error('无法解析查询');
 }
 return tokens;
}
function compile(source,options={}){
 const tokens=tokenize(source);let pos=0,label='';
 const sensitive=options.caseSensitive!==false;
 const textMatch=(value,needle)=>sensitive?String(value??'').includes(needle):String(value??'').toLocaleLowerCase().includes(needle.toLocaleLowerCase());
 function atom(token){
  const m=token.literal?null:/^(-?)([a-z]+)(~?):(.*)$/i.exec(token.value);
  if(!m)return {kind:'atom',test:e=>textMatch(e.message,token.value)};
  const neg=!!m[1],key=m[2].toLowerCase(),regex=!!m[3],value=m[4];
  if(!keys.has(key))throw new Error('不支持的字段：'+key);
  if(!value)throw new Error(key+' 缺少值');
  let test;
  if(key==='name'){label=value;test=()=>true;}
  else if(key==='level'){
   if(regex||neg)throw new Error('level 不支持排除或正则，请使用逻辑组合');
   const rank=levels[value.toUpperCase()];if(rank===undefined)throw new Error('无效日志等级：'+value);
   test=e=>levels[e.level]>=rank;
  }else if(key==='age'){
   const age=/^(\d+)([smhd])$/.exec(value);if(!age||regex)throw new Error('时间格式示例：age:5m');
   const duration=Number(age[1])*({s:1000,m:60000,h:3600000,d:86400000}[age[2]]);
   test=(e,ctx)=>e.time>=ctx.now-duration&&e.time<=ctx.now;
  }else if(key==='is'){
   if(!['crash','stacktrace'].includes(value)||regex)throw new Error('支持 is:crash、is:stacktrace');
   test=e=>value==='crash'?!!e.crash:!!e.stacktrace;
  }else if(['pid','tid','uid'].includes(key)){
   if(regex||!/^\d+$/.test(value))throw new Error(key+' 需要非负整数');test=e=>Number(e[key])===Number(value);
  }else if(key==='package'&&value==='mine'&&!regex){
   if(!options.packages?.length)throw new Error('package:mine 需要先选择应用');
   test=e=>options.packages.includes(e.package);
  }else if(regex){
   let re;try{re=new RegExp(value,sensitive?'':'i');}catch(error){throw new Error('正则表达式无效：'+error.message);}
   test=e=>re.test(String(e[key]??''));
  }else test=e=>textMatch(e[key],value);
  return {kind:'atom',implicitKey:!neg&&key!=='name'?key:null,test:neg?(e,c)=>!test(e,c):test};
 }
 function primary(){
  const token=tokens[pos++];if(!token)throw new Error('条件不完整');
  if(token.op==='('){const node=implicit();if(tokens[pos++]?.op!==')')throw new Error('缺少右括号');return {...node,kind:'group'};}
  if(token.op)throw new Error('运算符位置不正确');return atom(token);
 }
 function and(){let node=primary();while(tokens[pos]?.op==='&'){pos++;const left=node,right=primary();node={kind:'and',test:(e,c)=>left.test(e,c)&&right.test(e,c)};}return node;}
 function or(){let node=and();while(tokens[pos]?.op==='|'){pos++;const left=node,right=and();node={kind:'or',test:(e,c)=>left.test(e,c)||right.test(e,c)};}return node;}
 function implicit(){
  const nodes=[or()];while(pos<tokens.length&&tokens[pos].op!==')'){if(tokens[pos].op)throw new Error('运算符位置不正确');nodes.push(or());}
  const grouped=new Map(),other=[];
  for(const node of nodes){if(node.kind==='atom'&&node.implicitKey){if(!grouped.has(node.implicitKey))grouped.set(node.implicitKey,[]);grouped.get(node.implicitKey).push(node);}else other.push(node);}
  return {kind:'implicit',test:(e,c)=>other.every(n=>n.test(e,c))&&[...grouped.values()].every(group=>group.some(n=>n.test(e,c)))};
 }
 if(!tokens.length)return {test:()=>true,label:''};
 const node=implicit();if(pos!==tokens.length)throw new Error('存在多余右括号');
 return {test:(entry,context={now:Date.now()})=>node.test(entry,context),label};
}
const api={compile,tokenize,keys:[...keys]};if(typeof module!=='undefined')module.exports=api;else root.LogQuery=api;
})(typeof self!=='undefined'?self:globalThis);

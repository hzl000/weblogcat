// 覆盖过滤语义与错误输入，避免查询看起来可用但实际匹配范围错误。
const {test}=require('node:test');const assert=require('node:assert/strict');
const {compile}=require('../app/src/main/assets/web/query.js');
const row={message:'Network timeout failed 42',tag:'Network',package:'com.example.app',process:'com.example.app:remote',level:'W',pid:123,tid:456,uid:10001,time:9000,line:'raw Network timeout',crash:false,stacktrace:true};
const run=(q,r=row,options={})=>compile(q,options).test(r,{now:10000});
test('字段、普通文字、短语与精确数字',()=>{for(const q of ['timeout','"timeout failed"','tag:Network','package:com.example','process:remote','pid:123','tid:456','uid:10001','line:"raw Network"','message: timeout'])assert.equal(run(q),true,q);assert.equal(run('pid:12'),false);assert.equal(run('message:network'),false);assert.equal(run('message:network',row,{caseSensitive:false}),true);});
test('等级是最低阈值，不是相等',()=>{assert.equal(run('level:DEBUG'),true);assert.equal(run('level:WARN'),true);assert.equal(run('level:ERROR'),false);assert.equal(run('level:ASSERT',{...row,level:'F'}),true);});
test('相同键隐式 OR，不同键 AND，排除条件 AND',()=>{assert.equal(run('tag:Missing tag:Network package:example'),true);assert.equal(run('tag:Missing -tag:Network'),false);assert.equal(run('tag:Network -tag:Noise'),true);assert.equal(run('tag:Network package:absent'),false);});
test('显式组合优先级、括号和隐式低优先级',()=>{assert.equal(run('tag:Network | level:ERROR & package:absent'),true);assert.equal(run('(tag:Network | level:ERROR) & package:absent'),false);assert.equal(run('timeout tag:Network | tag:Other'),true);assert.equal(run('missing tag:Network | tag:Other'),false);});
test('排除正则、运算符转义和引号',()=>{assert.equal(run('message~:"timeout|failed"'),true);assert.equal(run('-message~:"success|passed"'),true);assert.equal(run('message~:"\\d+"'),true);assert.equal(run('message:"say \\"hello\\""',{...row,message:'say "hello"'}),true);assert.equal(run('tag~:"Net(work)"'),true);});
test('时间基于传入的手机时间，未来时间不匹配',()=>{assert.equal(run('age:1s'),true);assert.equal(run('age:0s'),false);assert.equal(run('age:5m',{...row,time:20000}),false);});
test('应用范围与异常条件',()=>{assert.equal(run('package:mine',row,{packages:['com.example.app']}),true);assert.equal(run('package:mine',row,{packages:['other']}),false);assert.equal(run('is:stacktrace'),true);assert.equal(run('is:crash'),false);assert.equal(compile('name:"网络错误"').label,'网络错误');});
test('非法表达式明确报错',()=>{for(const q of ['tag:','unknown:x','tag:"oops','(tag:x','tag:x)','tag:x &','& tag:x','tag~:"["','level:NOPE','age:2x','pid:x','is:nope','package:mine'])assert.throws(()=>compile(q),undefined,q);});

test('完整引号中的冒号属于普通文本',()=>assert.equal(run('\"http://example\"',{...row,message:'visit http://example'}),true));

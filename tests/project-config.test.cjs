const {test}=require('node:test');const assert=require('node:assert/strict');const projects=require('../app/src/main/assets/web/projects.json');const {compile}=require('../app/src/main/assets/web/query.js');
test('项目代号与包名映射唯一，Tag 可作为查询条件解析',()=>{
 assert.equal(projects.length,156);
 assert.equal(new Set(projects.map(project=>project.code)).size,projects.length);
 assert.equal(new Set(projects.map(project=>project.package)).size,153);
 const byCode=Object.fromEntries(projects.map(project=>[project.code,project]));
 assert.equal(byCode.PDF06.package,'com.smartpdf.viewer');
 assert.deepEqual(byCode.PDF06.tags.map(item=>item.tag),['notify_send','ad_manager','AdCallbackImp','all_report_pdf','report_tag']);
 assert.equal(byCode.PDF09.package,'com.pdfmanager.proreader');
 assert.deepEqual(byCode.PDF09.tags.map(item=>item.tag),['notify_send','ad_manager','AdCallbackImp','firebase_report']);
 for(const project of projects){
  assert.match(project.package,/^[a-zA-Z0-9_.]+$/);
  for(const item of project.tags){
   assert.ok(['广告','通知','埋点'].includes(item.type),project.code);
   assert.ok(item.tag.trim()===item.tag,project.code);
   assert.doesNotThrow(()=>compile('tag:'+item.tag),project.code+': '+item.tag);
  }
 }
});

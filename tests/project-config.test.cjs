const {test}=require('node:test');const assert=require('node:assert/strict');const projects=require('../app/src/main/assets/web/projects.json');const {compile}=require('../app/src/main/assets/web/query.js');
test('项目代号与包名映射唯一，Tag 可作为查询条件解析',()=>{
 assert.equal(projects.length,21);
 assert.equal(new Set(projects.map(project=>project.code)).size,projects.length);
 assert.equal(new Set(projects.map(project=>project.package)).size,projects.length);
 for(const project of projects){
  assert.match(project.package,/^[a-zA-Z0-9_.]+$/);
  assert.ok(project.tags.length,project.code);
  for(const item of project.tags){
   assert.ok(['广告','通知','埋点'].includes(item.type),project.code);
   assert.ok(item.tag.trim()===item.tag,project.code);
   assert.doesNotThrow(()=>compile('tag:'+item.tag),project.code+': '+item.tag);
  }
 }
});

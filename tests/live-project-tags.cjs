const assert=require('node:assert/strict');

function matchingPackages(projects,search){
 const query=search.trim().toLocaleLowerCase();
 const exact=projects.find(project=>project.code.toLocaleLowerCase()===query);
 return new Set((exact?[exact]:projects.filter(project=>project.code.toLocaleLowerCase().includes(query))).map(project=>project.package));
}
function hasTagsForPackages(projects,packages){
 return projects.some(project=>packages.includes(project.package)&&project.tags.length>0);
}

async function main(){
 const page=new URL(process.argv[2]||'');
 if(!page.hostname||!page.searchParams.has('token'))throw new Error('Usage: node tests/live-project-tags.cjs <authorized WebLogcat URL>');
 const endpoint=path=>{const url=new URL(path,page);for(const [key,value] of page.searchParams)if(!url.searchParams.has(key))url.searchParams.append(key,value);return url;};
 const [appsResponse,projectsResponse]=await Promise.all([
  fetch(endpoint('/apps?refresh=1')),
  fetch(endpoint('/projects.json'))
 ]);
 assert.equal(appsResponse.status,200,'读取真机应用列表失败');
 assert.equal(projectsResponse.status,200,'读取真机项目映射失败');
 const catalog=await appsResponse.json(),projects=await projectsResponse.json();
 assert.equal(catalog.ready,true,'真机应用目录尚未就绪');
 assert.ok(Array.isArray(catalog.apps)&&Array.isArray(projects),'接口返回格式异常');

 const installed=new Map(catalog.apps.map(app=>[app.package,app.name]));
 const matches=projects.filter(project=>installed.has(project.package));
 assert.ok(matches.length,'真机已安装应用中没有项目包名匹配');
 for(const project of matches){
  assert.ok(matchingPackages(projects,project.code).has(project.package),`${project.code} 搜索不到 ${project.package}`);
 }

 const packages=[...new Set(matches.map(project=>project.package))];
 for(const pkg of packages){
  const expected=projects.some(project=>project.package===pkg&&project.tags.length>0);
  assert.equal(hasTagsForPackages(projects,[pkg]),expected,`单选 ${pkg} 的 Tag 下拉状态错误`);
 }
 for(let left=0;left<packages.length;left++)for(let right=left+1;right<packages.length;right++){
  const selected=[packages[left],packages[right]];
  const expected=projects.some(project=>selected.includes(project.package)&&project.tags.length>0);
  assert.equal(hasTagsForPackages(projects,selected),expected,`多选 ${selected.join(', ')} 的 Tag 下拉状态错误`);
 }

 console.log(`通过：${matches.length} 个已安装项目映射、${packages.length} 种单选及 ${packages.length*(packages.length-1)/2} 种两两多选。`);
 console.log('真机已安装项目：');
 for(const project of matches)console.log(`${project.code}\t${project.package}\t${project.tags.length?'有 Tag':'空 Tag'}`);
}

main().catch(error=>{console.error(error.message);process.exitCode=1;});

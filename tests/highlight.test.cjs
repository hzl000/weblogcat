const {test}=require('node:test');const assert=require('node:assert/strict');const {segments}=require('../app/src/main/assets/web/highlight.js');
test('matches multiple keywords without interpreting regex characters',()=>{
 assert.deepEqual(segments('Ad+Banner and Notification',[{text:'Ad+',hue:10},{text:'Notification',hue:200}]),[
  {text:'Ad+',index:0},{text:'Banner and ',index:-1},{text:'Notification',index:1}
 ]);
});
test('uses longest match first and ignores letter case',()=>{
 assert.deepEqual(segments('FIREBASE_REPORT report',[{text:'report',hue:10},{text:'firebase_report',hue:200}]),[
  {text:'FIREBASE_REPORT',index:1},{text:' ',index:-1},{text:'report',index:0}
 ]);
});

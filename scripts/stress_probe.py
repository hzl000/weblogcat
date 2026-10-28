#!/usr/bin/env python3
"""使用独立测试源验证重启归属和真实缓存条数上限。"""
import json,subprocess,time,urllib.request
from pathlib import Path
from device_config import config
from urllib.parse import urlencode
settings=config();BASE=settings['base'];AUTH=urlencode({'token':settings['token']})
def get(path):
    with urllib.request.urlopen(BASE+path+('&' if '?' in path else '?')+AUTH,timeout=15) as response:return json.load(response)
def adb(*parts):return subprocess.check_output(['adb','-s','55271FDCR00D58',*parts],text=True)
initial=get('/logs?after=0');cursor=initial['latest']
old_pid=adb('shell','pidof','dev.weblogcat.fixture').strip()
adb('shell','am','force-stop','dev.weblogcat.fixture')
adb('shell','am','broadcast','-n','dev.weblogcat.fixture/.ProbeReceiver','--es','text','WEBLOGCAT_RESTART_TEST')
time.sleep(1)
rows=[]
while True:
    page=get('/logs?after='+str(cursor));rows+=page['records'];cursor=page['cursor']
    if cursor>=page['latest']:break
found=[r for r in rows if r['tag']=='WebLogcatProbe' and 'WEBLOGCAT_RESTART_TEST' in r['message']]
assert found and all(r['package']=='dev.weblogcat.fixture' and r['process']=='dev.weblogcat.fixture' for r in found)
assert all(str(r['pid'])!=old_pid for r in found)
print('应用重启后新的 PID 归属正确：'+str(found[0]['pid']),flush=True)
for index in range(20):
    adb('shell','am','broadcast','-n','dev.weblogcat.fixture/.ProbeReceiver','--es','text','WEBLOGCAT_STRESS_'+str(index),'--ei','count','5000')
    print('完成第 '+str(index+1)+' 批，每批请求 5000 条',flush=True)
    time.sleep(.5)
    if get('/logs?after='+str(cursor))['dropped']>0:break
deadline=time.monotonic()+90
while True:
    after=get('/logs?after=0')
    if after['dropped']>0 or time.monotonic()>deadline:break
    time.sleep(2)
result={k:after[k] for k in ['session','count','bytes','oldest','latest','dropped','readerAlive','error']}
result['restartedFixturePid']=found[0]['pid']
result['passed']=after['count']<=50000 and after['bytes']<=32*1024*1024 and after['dropped']>0 and after['readerAlive']
Path('evidence/v02-stress.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2),flush=True)
assert result['passed']

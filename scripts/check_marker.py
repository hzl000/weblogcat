#!/usr/bin/env python3
"""通过增量 HTTP 接口验证 shell UID 写入的唯一日志标记。"""
import argparse,json,subprocess,time,urllib.request,uuid
from pathlib import Path
from urllib.parse import urlencode
from device_config import config
parser=argparse.ArgumentParser()
parser.add_argument('--device',default='55271FDCR00D58')
parser.add_argument('--phase',required=True)
parser.add_argument('--expect',choices=['present','absent'],required=True)
parser.add_argument('--forwarded',action='store_true')
args=parser.parse_args();settings=config(args.device)
base='http://127.0.0.1:18765' if args.forwarded else settings['base']
def page(after=0):
    query=urlencode({'token':settings['token'],'after':after})
    with urllib.request.urlopen(base+'/logs?'+query,timeout=5) as response:return json.load(response)
initial=page();cursor=initial['latest']
marker='WEBLOGCAT_'+args.phase.upper()+'_'+uuid.uuid4().hex
subprocess.run(['adb','-s',args.device,'shell','log','-p','i','-t','WebLogcatProbe',marker],check=True)
matched=[];deadline=time.monotonic()+5
while time.monotonic()<deadline:
    data=page(cursor);cursor=data['cursor']
    matched.extend(e['line'] for e in data['records'] if e['tag']=='WebLogcatProbe' and marker in e['message'])
    if matched:break
    time.sleep(.2)
found=bool(matched)
result={'phase':args.phase,'transport':'adb-forward-http' if args.forwarded else 'direct-wifi-http','appUid':data['uid'],'shellUid':2000,'readLogsGranted':data['readLogsGranted'],'readerAlive':data['readerAlive'],'serviceStarted':data['started'],'marker':marker,'found':found,'expected':args.expect,'markerLines':matched,'error':data['error']}
result['passed']=found==(args.expect=='present') and data['readerAlive'] and data['uid']!=2000
Path('evidence').mkdir(exist_ok=True);Path('evidence/'+args.phase+'.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2));raise SystemExit(0 if result['passed'] else 1)

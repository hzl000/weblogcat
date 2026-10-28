#!/usr/bin/env python3
"""确认 USB 断开后，手机仍能采集并通过 Wi-Fi 返回延迟标记。"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--url', required=True)
args = parser.parse_args()
stimulus = json.loads(Path('evidence/unplug-stimulus.json').read_text())
devices = subprocess.check_output(['adb', 'devices'], text=True)
assert '55271FDCR00D58' not in devices, '目标手机仍连接 ADB，不能认定已拔线'
observed_detached = time.time()
assert observed_detached < stimulus['scheduledAt'] + stimulus['delaySeconds'], '确认断线太晚，需重新安排测试标记'
print('已确认目标设备不在 ADB 列表中；开始仅通过 Wi-Fi 等待标记。', flush=True)
with urllib.request.urlopen(args.url, timeout=5) as response:
    initial = json.load(response)
cursor = initial.get('latest', 0)
deadline = time.monotonic() + 110
while time.monotonic() < deadline:
    url = args.url + ('&' if '?' in args.url else '?') + 'after=' + str(cursor)
    with urllib.request.urlopen(url, timeout=5) as response:
        data = json.load(response)
    cursor = data.get('cursor',cursor)
    logs=data.get('logs','\n'.join(e['line'] for e in data.get('records',[])))
    matched = [line for line in logs.splitlines() if 'I WebLogcatProbe:' in line and stimulus['marker'] in line]
    if matched:
        result = {
            'phase': 'usb-unplugged', 'transport': 'direct-wifi-http',
            'adbDetached': True, 'detachedObservedAt': observed_detached,
            'scheduledAt': stimulus['scheduledAt'], 'delaySeconds': stimulus['delaySeconds'],
            'observedAt': time.time(), 'uid': data['uid'], 'readerAlive': data['readerAlive'],
            'readLogsGranted': data['readLogsGranted'], 'serviceStarted': data['started'],
            'markerLines': matched, 'passed': data['readerAlive'],
        }
        Path('evidence/usb-unplugged.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
        print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
        raise SystemExit(0 if result['passed'] else 1)
    time.sleep(.2)
raise SystemExit('未在时限内收到延迟标记；不能认定拔线采集验证通过')

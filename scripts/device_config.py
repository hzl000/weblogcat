#!/usr/bin/env python3
"""从调试设备读取当前服务配置，测试脚本不保存访问 token。"""
import re
import subprocess
import xml.etree.ElementTree as ET

def config(device='55271FDCR00D58'):
    def adb(*args):return subprocess.check_output(['adb','-s',device,*args],text=True)
    prefs=ET.fromstring(adb('shell','run-as','dev.weblogcat.probe','cat','shared_prefs/server.xml'))
    token=prefs.find("string[@name='token']").text
    value=prefs.find("int[@name='port']")
    port=int(value.get('value')) if value is not None else 8765
    ip=re.search(r'inet (\d+\.\d+\.\d+\.\d+)',adb('shell','ip','-4','addr','show','wlan0')).group(1)
    return {'base':f'http://{ip}:{port}','token':token,'device':device}

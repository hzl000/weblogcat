#!/usr/bin/env python3
"""根据最新界面节点定位原生控件，仅供本项目实机验证使用。"""
import re
import subprocess
import xml.etree.ElementTree as ET
DEVICE='55271FDCR00D58'
def adb(*parts):
    return subprocess.check_output(['adb','-s',DEVICE,*parts],text=True)
def nodes():
    adb('shell','uiautomator','dump','/data/local/tmp/weblogcat-ui.xml')
    return list(ET.fromstring(adb('shell','cat','/data/local/tmp/weblogcat-ui.xml')).iter('node'))
def click_node(node):
    values=list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
    adb('shell','input','tap',str((values[0]+values[2])//2),str((values[1]+values[3])//2))
def click_text(text):
    node=next(n for n in nodes() if n.get('package')=='dev.weblogcat.probe' and n.get('text')==text)
    click_node(node)
def set_port(port):
    click_text('设置')
    node=next(n for n in nodes() if n.get('package')=='dev.weblogcat.probe' and n.get('class')=='android.widget.EditText')
    click_node(node)
    adb('shell','input','keycombination','113','29')
    adb('shell','input','text',str(port))
    adb('shell','input','keyevent','KEYCODE_BACK')
    click_text('保存端口')
if __name__=='__main__':
    import sys
    adb('shell','am','start','-W','-n','dev.weblogcat.probe/.MainActivity')
    set_port(sys.argv[1])

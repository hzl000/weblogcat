#!/usr/bin/env python3
"""仅监听本机的界面调试服务器，日志请求转发到已授权手机。"""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import urllib.request
from urllib.parse import urlsplit
from device_config import config
PHONE=config()['base']
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path=urlsplit(self.path).path
        if path in ['/logs','/status','/apps','/icon']:
            try:
                with urllib.request.urlopen(PHONE+self.path,timeout=10) as response:
                    data=response.read();content_type=response.headers.get('Content-Type')
            except Exception:
                self.send_error(502);return
        else:
            name='index.html' if path=='/' else path.lstrip('/')
            if name not in ['index.html','query.js','app.js','filter-worker.js','style.css']:
                self.send_error(404);return
            data=Path('app/src/main/assets/web',name).read_bytes()
            content_type='text/javascript' if name.endswith('.js') else 'text/css' if name.endswith('.css') else 'text/html'
        self.send_response(200);self.send_header('Content-Type',content_type+'; charset=utf-8' if 'charset' not in content_type else content_type);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def log_message(self,*args):pass
ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()

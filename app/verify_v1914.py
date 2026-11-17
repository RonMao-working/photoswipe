#!/usr/bin/env python3
"""API34 real UI: cold-start candidate, photo tap vs info-row tap. No photo decision made."""
import regression_emulator as r
import json,re,time
from pathlib import Path
P=Path(__file__).resolve().parent;checks=[]
def check(b,s):
 if not b:raise AssertionError(s)
 checks.append(s);print('PASS',s,flush=True)
def tap_xy(x,y):r.adb('shell','input','tap',x,y);time.sleep(1.0)
def node(pred):
 for n in r.ui().iter('node'):
  if pred(n.get('text','')):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
 return None
def tap_pred(pred):
 b=node(pred)
 if not b:raise RuntimeError('node not found\n'+'\n'.join(r.texts()))
 tap_xy((b[0]+b[2])//2,(b[1]+b[3])//2)
r.start()
if '滑选' in r.texts():r.tap('滑选')
time.sleep(2)
check(any(t.startswith('文件') for t in r.texts()),'cold start reaches a readable photo')
check(not any('正在' in t for t in r.texts()),'cold start leaves no loading placeholder on the slide page')
check(r.photo() is not None,'cold start photo metadata already filled')
before=set(r.texts())
tap_xy(540,700)
check('照片信息' not in r.texts() and set(r.texts())==before,'single tap on the photo opens no info dialog')
r.screenshot('预览-滑选首屏-v1.9.14.png')
tap_pred(lambda t:'年' in t and ':' in t)
after=r.texts()
check('照片信息' in after,'tap on the time row opens the photo info dialog')
dialog=[t for t in after if '拍摄时间' in t or '文件  ' in t or t.startswith('设备')]
check(bool(dialog),'dialog carries time/place/device lines')
r.screenshot('预览-信息详情-v1.9.14.png')
r.tap('关闭')
check('照片信息' not in r.texts(),'info dialog closes back to the slide card')
r.tap('我的')
r.tap('设置')
check(any('v1.9.14' in t for t in r.texts()),'settings shows version 1.9.14')
if '关闭' in r.texts():r.tap('关闭')
time.sleep(.6)
if '取消' in r.texts():r.tap('取消')
time.sleep(.6)
if '滑选' in r.texts():r.tap('滑选')
time.sleep(1.0)
check('滑选' in r.texts() and any(t.startswith('文件') for t in r.texts()),'slide tab returns to the card without My tab round-trip')
(P/'测试结果-v1.9.14-ui.json').write_text(json.dumps({'checks':checks,'count':len(checks)},ensure_ascii=False,indent=2))
print('TOTAL',len(checks),'checks')

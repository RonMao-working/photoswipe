#!/usr/bin/env python3
"""Compile and run isolated v1.9.15 regression on emulator-5554 only."""
from pathlib import Path
import os,subprocess,shutil,sys,tempfile
P=Path(__file__).resolve().parent; SDK=Path.home()/'android-dev/sdk'; JDK=Path.home()/'android-dev/jdk/Contents/Home'; BT=SDK/'build-tools/34.0.0'; jar=SDK/'platforms/android-34/android.jar'
os.environ['JAVA_HOME']=str(JDK);os.environ['PATH']=str(JDK/'bin')+os.pathsep+os.environ['PATH']
name=sys.argv[1] if len(sys.argv)>1 else 'Regression1915Instrumentation'
if name not in ('Regression1915Instrumentation','Regression1911Instrumentation'):raise SystemExit('Unknown regression')
W=Path(tempfile.mkdtemp(prefix='photoswipe1915-'));(W/'classes').mkdir();(W/'dex').mkdir()
manifest=W/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.maodun.photoswipe.test"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="PhotoSwipe tests"/><instrumentation android:name="com.maodun.photoswipe.'+name+'" android:targetPackage="com.maodun.photoswipe"/></manifest>',encoding='utf-8')
def run(args,cwd=None):subprocess.run(list(map(str,args)),cwd=cwd,check=True)
run([BT/'aapt2','link','-I',jar,'--manifest',manifest,'-o',W/'base.apk'])
run([JDK/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',str(jar)+':'+str(P/'build/classes'),'-d',W/'classes',P/('tests/'+name+'.java')])
run([BT/'d8','--min-api','26','--lib',jar,'--output',W/'dex',*list((W/'classes').rglob('*.class'))])
shutil.copy2(W/'base.apk',W/'unsigned.apk');run(['zip','-q','-X',W/'unsigned.apk','classes.dex'],W/'dex');run([BT/'zipalign','-f','4',W/'unsigned.apk',W/'aligned.apk'])
run([BT/'apksigner','sign','--ks',Path.home()/'android-dev/debug.keystore','--ks-pass','pass:android','--out',W/'test.apk',W/'aligned.apk'])
run([SDK/'platform-tools/adb','-s','emulator-5554','install','-r',W/'test.apk'])
p=subprocess.run([str(SDK/'platform-tools/adb'),'-s','emulator-5554','shell','am','instrument','-w','com.maodun.photoswipe.test/com.maodun.photoswipe.'+name],capture_output=True,text=True,timeout=180)
(P/('测试结果-v1.9.15-'+name+'.txt')).write_text(p.stdout+p.stderr,encoding='utf-8');print(p.stdout,p.stderr)
if 'TOTAL ' not in p.stdout or 'AssertionError' in p.stdout or 'Exception' in p.stdout or 'INSTRUMENTATION_CODE: -1' not in p.stdout:raise SystemExit('Regression failed')

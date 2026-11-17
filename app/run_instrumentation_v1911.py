#!/usr/bin/env python3
"""Build isolated v1911 QA instrumentation against product classes. Emulator only."""
from pathlib import Path
import os,subprocess,shutil,sys
P=Path(__file__).resolve().parent;SDK=Path.home()/'android-dev/sdk';JDK=Path.home()/'android-dev/jdk/Contents/Home';BT=SDK/'build-tools/34.0.0';jar=SDK/'platforms/android-34/android.jar'
os.environ['JAVA_HOME']=str(JDK);os.environ['PATH']=str(JDK/'bin')+os.pathsep+os.environ['PATH']
baseline='--baseline' in sys.argv;name='Regression1911Instrumentation';W=Path('/tmp/photoswipe1911-test');W.mkdir(exist_ok=True);(W/'classes').mkdir(exist_ok=True);(W/'dex').mkdir(exist_ok=True)
manifest=W/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.maodun.photoswipe.test"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="PhotoSwipe tests"/><instrumentation android:name="com.maodun.photoswipe.'+name+'" android:targetPackage="com.maodun.photoswipe"/></manifest>')
def run(args,cwd=None):subprocess.run(list(map(str,args)),cwd=cwd,check=True)
run([BT/'aapt2','link','-I',jar,'--manifest',manifest,'-o',W/'base.apk']);run([JDK/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',str(jar)+':'+str(P/'build/classes'),'-d',W/'classes',P/('tests/'+name+'.java')]);run([BT/'d8','--min-api','26','--lib',jar,'--output',W/'dex',*list((W/'classes').rglob('*.class'))])
shutil.copy2(W/'base.apk',W/'unsigned.apk');run(['zip','-q','-X',W/'unsigned.apk','classes.dex'],W/'dex');run([BT/'zipalign','-f','4',W/'unsigned.apk',W/'aligned.apk']);run([BT/'apksigner','sign','--ks',Path.home()/'android-dev/debug.keystore','--ks-pass','pass:android','--out',W/'test.apk',W/'aligned.apk']);run([SDK/'platform-tools/adb','-s','emulator-5554','install','-r',W/'test.apk'])
cmd=[str(SDK/'platform-tools/adb'),'-s','emulator-5554','shell','am','instrument','-w'];cmd+=['-e','baseline','true'] if baseline else [];cmd+=['com.maodun.photoswipe.test/com.maodun.photoswipe.'+name];p=subprocess.run(cmd,capture_output=True,text=True)
suffix='baseline' if baseline else 'instrumentation';(P/('测试结果-v1.9.11-'+suffix+'.txt')).write_text(p.stdout+p.stderr);print(p.stdout,p.stderr)
expected=2 if baseline else 43
if ('TOTAL '+str(expected)+' checks') not in p.stdout or 'AssertionError' in p.stdout or 'Exception' in p.stdout:raise SystemExit('Instrumentation failed')

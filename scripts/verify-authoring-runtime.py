#!/usr/bin/env python3
"""Run native Android authoring pixel checks against this checkout's packaged APK."""
from pathlib import Path
import os,shutil,subprocess
ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'android-sdk')))
OUT=ROOT/'build/authoring-runtime'
OUT.mkdir(parents=True,exist_ok=True)
subprocess.run(['python3',str(ROOT/'scripts/generate-authoring-fixtures.py')],check=True)
android=SDK/'platforms/android-36/android.jar'
kotlin=next((Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.20').glob('*/*.jar'))
app=ROOT/'build/intermediates/compile_app_classes_jar/debug/bundleDebugClassesToCompileJar/classes.jar'
canvas=ROOT/'libraries/libinfinicanvas/canvas-android/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar'
stream=ROOT/'libraries/libinfinistream/stream-core/build/libs/stream-core-0.1.0.jar'
classpath=[android,kotlin,app,canvas,stream]
classes=OUT/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-classpath',':'.join(map(str,classpath)),'-d',str(classes),*[str(p) for p in (ROOT/'scripts/runtime').glob('*.java')]],check=True)
dex=OUT/'dex';dex.mkdir(exist_ok=True)
for p in dex.glob('*.dex'):p.chmod(0o644)
subprocess.run([str(SDK/'build-tools/36.0.0/d8'),'--min-api','26','--lib',str(android),'--output',str(dex)]+[item for p in classpath[1:] for item in ['--classpath',str(p)]]+[str(p) for p in classes.glob('*.class')],check=True)
for p in dex.glob('*.dex'):p.chmod(0o444)
apk=OUT/'runtime.apk'
if apk.exists():apk.chmod(0o644)
shutil.copyfile(ROOT/'build/outputs/apk/debug/InfiniCanvas-debug.apk',apk);apk.chmod(0o444)
env=os.environ.copy();env.pop('LD_LIBRARY_PATH',None);env.pop('LD_PRELOAD',None)
env['CLASSPATH']=str(dex/'classes.dex')+':'+str(apk)
result=subprocess.run(['/system/bin/app_process64','/system/bin','AuthoringRuntimeCheck',str(ROOT/'build/authoring-fixtures')],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=60)
(OUT/'result.log').write_text(result.stdout)
print(result.stdout,end='')
raise SystemExit(result.returncode)

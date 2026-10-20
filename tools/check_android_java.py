#!/usr/bin/env python3
"""Compile the real Android UI and resources before assembling a release APK."""
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ['ANDROID_SDK_ROOT'])
android=sdk/'platforms/android-35/android.jar'
aapt=sdk/'build-tools/35.0.0/aapt2'
source=root/'os/android/app/src/main'
out=root/'out/java-check';out.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
(out/'resources.zip').unlink(missing_ok=True)
run(aapt,'compile','--dir',source/'res','-o',out/'resources.zip')
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
tree=ET.parse(source/'AndroidManifest.xml');tree.getroot().set('package','com.libertyrecomp')
tree.write(out/'manifest.xml',encoding='utf-8',xml_declaration=True)
run(aapt,'link','--manifest',out/'manifest.xml','-I',android,'--min-sdk-version','29',
    '--target-sdk-version','35','--custom-package','com.libertyrecomp','--java',out/'generated',
    '-o',out/'resources.apk',out/'resources.zip')
files=[*source.joinpath('java').rglob('*.java'),
       *root.joinpath('glue/rexglue-sdk-main/thirdparty/sdl3/android-project/app/src/main/java').rglob('*.java'),
       *out.joinpath('generated').rglob('*.java')]
(out/'classes').mkdir(exist_ok=True)
(out/'sources.txt').write_text('\n'.join(str(p) for p in files)+'\n')
run('java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-classpath',android,
    '-d',out/'classes','@'+str(out/'sources.txt'))
print('Android UI/resource compile passed')

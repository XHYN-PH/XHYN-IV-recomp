#!/usr/bin/env python3
"""Compile Android resources and every launcher/SDL Java source without an APK.

Requires Android API 35 android.jar, aapt2 and JDK 17. Does not reuse an old
native binary or report a Java-only build as a complete playable APK.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar',type=Path,required=True)
    parser.add_argument('--aapt2',type=Path,required=True)
    args=parser.parse_args()
    out=ROOT/'out/android-ui-check'
    if out.exists():shutil.rmtree(out)
    out.mkdir(parents=True)
    source=ROOT/'os/android/app/src/main'
    tree=ET.parse(source/'AndroidManifest.xml');tree.getroot().set('package','com.libertyrecomp')
    tree.write(out/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    subprocess.run([str(args.aapt2),'compile','--dir',str(source/'res'),'-o',str(out/'resources.zip')],check=True)
    subprocess.run([str(args.aapt2),'link','--manifest',str(out/'AndroidManifest.xml'),'-I',str(args.android_jar),
                    '--min-sdk-version','29','--target-sdk-version','35','--java',str(out/'generated'),
                    '-o',str(out/'resources.apk'),str(out/'resources.zip')],check=True)
    sources=sorted((source/'java').rglob('*.java'))+sorted((ROOT/'glue/rexglue-sdk-main/thirdparty/sdl3/android-project/app/src/main/java').rglob('*.java'))+list((out/'generated').rglob('*.java'))
    (out/'sources.txt').write_text('\n'.join(map(str,sources))+'\n')
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-classpath',str(args.android_jar),
                    '-d',str(out/'classes'),'@'+str(out/'sources.txt')],check=True)
    print(f'Android resources and {len(sources)} Java files compiled')

if __name__=='__main__':main()

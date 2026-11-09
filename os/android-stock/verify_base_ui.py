#!/usr/bin/env python3
"""Check Test 2's payload, assembled English DEX and compiled manifest boundary."""
import argparse
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile

from build_base_ui import APP_ID, CYRILLIC, VERSION_CODE, VERSION_NAME, verify_candidate
from preflight import uleb


def manifest(path: Path, aapt2: Path) -> dict:
    text=subprocess.check_output([str(aapt2),'dump','xmltree',str(path),'--file','AndroidManifest.xml'],text=True)
    stack=[]; root=None
    for line in text.splitlines():
        match=re.match(r'(\s*)E: ([^ ]+)',line)
        if match:
            depth=len(match[1]); node={'tag':match[2],'attrs':{},'children':[]}
            while stack and stack[-1][0]>=depth:stack.pop()
            if stack:stack[-1][1]['children'].append(node)
            else:root=node
            stack.append((depth,node))
        else:
            match=re.match(r'\s*A: ([^(=]+)(?:\([^)]*\))?=(.*?)(?: \(Raw: .*)?$',line)
            if match and stack:stack[-1][1]['attrs'][match[1]]=match[2]
    if root is None:raise ValueError('No manifest')
    return root


def verify(base: Path, apk: Path, aapt2: Path) -> dict:
    report=verify_candidate(base,apk)
    old,new=manifest(base,aapt2),manifest(apk,aapt2)
    ns='http://schemas.android.com/apk/res/android:'
    assert new['attrs']['package']==json.dumps(APP_ID)
    assert new['attrs'][ns+'versionCode']==str(VERSION_CODE)
    assert new['attrs'][ns+'versionName']==json.dumps(VERSION_NAME)
    for key in ('package',ns+'versionCode',ns+'versionName'):
        old['attrs'].pop(key);new['attrs'].pop(key)
    old_app=next(n for n in old['children'] if n['tag']=='application')
    new_app=next(n for n in new['children'] if n['tag']=='application')
    assert new_app['attrs'][ns+'label']=='"XHYN IV"'
    assert new_app['attrs'].pop(ns+'debuggable')=='false'
    old_app['attrs'].pop(ns+'label');new_app['attrs'].pop(ns+'label')
    assert old==new,'Compiled stock manifest changed outside package/version/label/debugging'
    with zipfile.ZipFile(apk) as archive:
        data=archive.read('classes.dex')
        count,offset=struct.unpack_from('<II',data,56)
        strings=[]
        for i in range(count):
            start,=struct.unpack_from('<I',data,offset+4*i);_,start=uleb(data,start)
            strings.append(data[start:data.index(b'\0',start)].decode('utf-8','replace'))
        assert not any(CYRILLIC.search(s) for s in strings),'Russian text remains in assembled UI DEX'
        assert all(s in strings for s in ('Game disc image (.iso)','Title Update 8 (TU8)','Game data','Game language','xhynEnsureEnglish'))
    report['compiled_manifest']='original components, permissions, process and launch configuration preserved'
    report['android_ui_language']='no Russian strings in final DEX string table'
    report['version_code']=VERSION_CODE
    report['version_name']=VERSION_NAME
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ('base','apk','aapt2','out'):parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args(); report=verify(args.base,args.apk,args.aapt2)
    args.out.write_text(json.dumps(report,indent=2)+'\n')
    print('Payload, English UI and original compiled launch configuration verified')


if __name__=='__main__':main()

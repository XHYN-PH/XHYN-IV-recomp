#!/usr/bin/env python3
"""Verify the exact Test 3 package, native interfaces, imported artwork and update identity."""
from pathlib import Path
import argparse,hashlib,json,os,re,struct,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
 bt=Path(os.environ['ANDROID_SDK_ROOT'])/'build-tools/35.0.0';llvm=Path(os.environ['ANDROID_NDK_HOME'])/'toolchains/llvm/prebuilt/linux-x86_64/bin';staged=ROOT/'out/apk-release/lib/arm64-v8a'
 hashes={};asset_count=0
 with zipfile.ZipFile(a.apk) as z:
  names=z.namelist();assert len(names)==len(set(names));assert z.testzip() is None
  libs=[n for n in names if n.startswith('lib/')];assert len(libs)==11 and all(n.startswith('lib/arm64-v8a/') for n in libs)
  for n in libs:
   data=z.read(n);assert data==(staged/Path(n).name).read_bytes(),n
   assert data[:6]==b'\x7fELF\x02\x01' and struct.unpack_from('<H',data,18)[0]==183
   offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
   for i in range(count):
    kind,flags,off,addr,physical,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',data,offset+i*size)
    if kind==1:assert align>=16384 and off%16384==addr%16384,(n,align)
   hashes[Path(n).name]=hashlib.sha256(data).hexdigest()
  dex=b''.join(z.read(n) for n in names if re.fullmatch(r'classes[0-9]*\.dex',n))
  for cls in ['PhoneGyro','GyroMath','PerformanceHud','UiIcon','GameDataCheck','AchievementsUi']:
   assert ('Lcom/libertyrecomp/'+cls+';').encode() in dex,cls
  for cls in ['GyroMathTest','TouchIconAssetsTest','MobileOptionsTest']:assert cls.encode() not in dex
  for file in (ROOT/'os/android/app/src/main/assets/touch-icons').glob('*.png'):
   assert z.read('assets/touch-icons/'+file.name)==file.read_bytes();asset_count+=1
  assert 'assets/TOUCH-NOTICE.txt' in names and 'assets/COPYING' in names
 cert=subprocess.check_output(['java','-jar',str(bt/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(a.apk)],text=True)
 expected='106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49';assert re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',cert)[1]==expected
 (a.output/'signature.txt').write_text(cert)
 subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(a.apk)],check=True)
 badging=subprocess.check_output([str(bt/'aapt2'),'dump','badging',str(a.apk)],text=True);(a.output/'manifest-badging.txt').write_text(badging)
 for value in ["name='com.xhyn.ivrecomp'","versionCode='31'","versionName='0.5.9.3-xhyn-r2-test3'","minSdkVersion:'29'","targetSdkVersion:'35'"]:assert value in badging,value
 assert 'application-debuggable' not in badging
 symbols=subprocess.check_output([str(llvm/'llvm-nm'),'-D','--defined-only',str(staged/'libmain.so')],text=True)
 for entry in ['nativeGyroSample','nativeGyroReset','nativeGetSetting','nativeApplySettings','nativeQueueCheat','nativeHostMenu']:
  assert 'Java_com_libertyrecomp_LibertyActivity_'+entry in symbols,entry
 assert b'texture-reuse-stats:' in (staged/'librexgpu-gta4-native.so').read_bytes()
 assert b'touch-icon: name=' in (staged/'libmain.so').read_bytes()
 assert b'low_latency=' in (staged/'librexruntime.so').read_bytes()
 report=dict(version='0.5.9.3-xhyn-r2-test3',version_code=31,package='com.xhyn.ivrecomp',apk_sha256=hashlib.sha256(a.apk.read_bytes()).hexdigest(),apk_bytes=a.apk.stat().st_size,signer_sha256=expected,native_library_sha256=hashes,touch_pngs_verified=asset_count,crc_verified=True,signature_verified=True,alignment_16kb_verified=True,gyro_jni_verified=True,device_gameplay_verified=False)
 (a.output/'build.json').write_text(json.dumps(report,indent=2)+'\n')
 print('Verified: signed update identity, version 31, 11 exact ARM64 libraries, 16 KiB ELF/ZIP alignment, all artwork, new gyro JNI and no test fixtures.')
 print(a.apk.name,report['apk_bytes'],report['apk_sha256'])
if __name__=='__main__':main()

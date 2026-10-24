#!/usr/bin/env python3
"""Verify the exact Test 6 package, native interfaces, imported artwork and update identity."""
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
  for cls in ['PhoneGyro','GyroMath','PerformanceHud','UiIcon','GameDataCheck','AchievementsUi','GameStorage','PendingSettings','NextLaunchConfig','PreparationUi','TurnipSettingsUi','SessionRecorder','SessionResultsUi','SessionHistory','OverlayPlacement','OverlayPreview','TouchViewport','ViewportIdentity','SaveMetadata','DriverState','DriverPackages']:
   assert ('Lcom/libertyrecomp/'+cls+';').encode() in dex,cls
  for cls in ['GyroMathTest','TouchIconAssetsTest','MobileOptionsTest','SaveSlotsTest','DriverPolicyTest','SessionDiagnosticsTest','SaveMetadataTest','AndroidLogFilesTest','FakeLogcat']:assert cls.encode() not in dex
  for file in (ROOT/'os/android/app/src/main/assets/touch-icons').glob('*.png'):
   assert z.read('assets/touch-icons/'+file.name)==file.read_bytes();asset_count+=1
  drivers={}
  import io
  source=(ROOT/'os/android/app/src/main/java/com/libertyrecomp/BundledDrivers.java').read_text()
  blocks=re.findall(r'\{\s*Map<String,String> files=new LinkedHashMap<>\(\);(.*?)\n        \}',source,re.S)
  assert len(blocks)==7,'Expected all seven bundled driver definitions'
  for block in blocks:
   bundle=re.search(r'new Bundle\("([^"\n]+)","([^"\n]+)","([^"\n]+)",(\d+),"([^"\n]+)","([0-9a-f]{64})",files\)',block)
   assert bundle,'Unrecognized driver definition'
   mode,name,label,api,library,expected=bundle.groups()
   data=z.read('assets/drivers/'+name);assert hashlib.sha256(data).hexdigest()==expected,name
   assert data==(ROOT/'os/android/app/src/main/assets/drivers'/name).read_bytes(),name
   members=dict(re.findall(r'files.put\("([^"\n]+)","([0-9a-f]{64})"\)',block))
   with zipfile.ZipFile(io.BytesIO(data)) as archive:
    assert archive.testzip() is None
    assert len(archive.namelist())==len(set(archive.namelist()))
    assert set(archive.namelist())==set(members)
    for member,digest in members.items():assert hashlib.sha256(archive.read(member)).hexdigest()==digest,(name,member)
   drivers[name]=expected
  for file in ['touch-defaults.json','xbox-defaults.json','drivers/xhyn-bundled-provenance.json']:
   assert z.read('assets/'+file)==(ROOT/'os/android/app/src/main/assets'/file).read_bytes(),file
  assert 'assets/TOUCH-NOTICE.txt'  in names and 'assets/COPYING' in names
 cert=subprocess.check_output(['java','-jar',str(bt/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(a.apk)],text=True)
 expected='106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49';assert re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',cert)[1]==expected
 (a.output/'signature.txt').write_text(cert)
 subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(a.apk)],check=True)
 badging=subprocess.check_output([str(bt/'aapt2'),'dump','badging',str(a.apk)],text=True);(a.output/'manifest-badging.txt').write_text(badging)
 for value in ["name='com.xhyn.ivrecomp'","versionCode='34'","versionName='0.5.9.3-xhyn-r2-test6'","minSdkVersion:'29'","targetSdkVersion:'35'"]:assert value in badging,value
 assert 'application-debuggable' not in badging
 assert "launchable-activity: name='com.libertyrecomp.LauncherActivity'" in badging
 symbols=subprocess.check_output([str(llvm/'llvm-nm'),'-D','--defined-only',str(staged/'libmain.so')],text=True)
 for entry in ['nativeGyroSample','nativeGyroReset','nativeGetSetting','nativeApplySettings','nativeQueueCheat','nativeHostMenu','nativeSetInputTrace','nativeTouchViewport','nativeTouchViewportGeneration']:
  assert 'Java_com_libertyrecomp_LibertyActivity_'+entry in symbols,entry
 install_symbols=subprocess.check_output([str(llvm/'llvm-nm'),'-D','--defined-only',str(staged/'libliberty_install.so')],text=True)
 assert 'Java_com_libertyrecomp_Installer_nativePrepareAchievements' in install_symbols
 assert b'texture-reuse-stats:'  in (staged/'librexgpu-gta4-native.so').read_bytes()
 assert b'touch-icon: name=' in (staged/'libmain.so').read_bytes()
 assert b'low_latency=' in (staged/'librexruntime.so').read_bytes()
 assert b'data_file_mod_status' in (staged/'librexruntime.so').read_bytes()
 assert b'address fallback is incomplete' in (staged/'libvulkan.so').read_bytes()
 assert b'elapsed_ms' in (staged/'librexgpu-gta4-native.so').read_bytes()
 report=dict(version='0.5.9.3-xhyn-r2-test6',version_code=34,package='com.xhyn.ivrecomp',apk_sha256=hashlib.sha256(a.apk.read_bytes()).hexdigest(),apk_bytes=a.apk.stat().st_size,signer_sha256=expected,native_library_sha256=hashes,bundled_driver_zip_sha256=drivers,touch_pngs_verified=asset_count,crc_verified=True,signature_verified=True,alignment_16kb_verified=True,gyro_jni_verified=True,achievement_preparation_jni_verified=True,device_gameplay_verified=False)
 (a.output/'build.json').write_text(json.dumps(report,indent=2)+'\n')
 print('Verified: signed update identity, version 34, 11 exact ARM64 libraries, 16 KiB ELF/ZIP alignment, all artwork, new gyro JNI and no test fixtures.')
 print(a.apk.name,report['apk_bytes'],report['apk_sha256'])
if __name__=='__main__':main()

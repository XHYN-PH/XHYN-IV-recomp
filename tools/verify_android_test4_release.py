from pathlib import Path
import hashlib,os,re,struct,subprocess,zipfile,json
root=Path(__file__).resolve().parents[1]
apk=root.parent/'deliverables/XHYN-IV-0.5.9.3-test4-release.apk'
old=root.parent/'recovery/XHYN-IV-0.5.9.3-test3-release.apk'
staged=root/'out/apk-release/lib/arm64-v8a'
ndk=Path(os.environ['ANDROID_NDK_HOME'])
bt=Path(os.environ['ANDROID_SDK_ROOT'])/'build-tools/35.0.0'
llvm=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
checks=root.parent/'test4-checkpoint/checks'
report={}
with zipfile.ZipFile(apk) as z:
 names=z.namelist();assert len(names)==len(set(names))
 libraries=[n for n in names if n.startswith('lib/')]
 assert len(libraries)==11 and all(n.startswith('lib/arm64-v8a/') for n in libraries)
 hashes={}
 for name in libraries:
  data=z.read(name);local=staged/Path(name).name
  assert data==local.read_bytes(),name+' stale package copy'
  assert data[:6]==b'\x7fELF\x02\x01' and struct.unpack_from('<H',data,18)[0]==183
  phoff=struct.unpack_from('<Q',data,32)[0]
  ents,num=struct.unpack_from('<HH',data,54)
  for i in range(num):
   kind,flags,offset,vaddr,paddr,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',data,phoff+i*ents)
   if kind==1:assert align>=16384 and offset%16384==vaddr%16384,(name,align)
  hashes[Path(name).name]=hashlib.sha256(data).hexdigest()
 dex=b''.join(z.read(n) for n in names if re.fullmatch(r'classes[0-9]*\.dex',n))
 for cls in ['AndroidLogFilesTest','DriverPolicyTest','FakeLogcat','MobileOptionsTest']:assert cls.encode() not in dex
 for cls in ['Lcom/libertyrecomp/LibertyActivity;','Lcom/libertyrecomp/LauncherActivity;']:assert cls.encode() in dex
 assert any(n.startswith('assets/touch-icons/') for n in names)
 assert 'assets/COPYING' in names
 assert z.read('assets/licenses/SGSR1-LICENSE.txt')==(root/'docs/thirdparty/sgsr1/LICENSE').read_bytes()
 report['native_library_sha256']=hashes
signature=[]
for path in [old,apk]:
 output=subprocess.check_output(['java','-jar',str(bt/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(path)],text=True)
 signature.append(re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',output)[1])
 if path==apk:(checks/'apk-signature.txt').write_text(output)
assert signature[0]==signature[1]=='106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49'
subprocess.run([str(bt/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
badging=subprocess.check_output([str(bt/'aapt2'),'dump','badging',str(apk)],text=True)
assert "name='com.xhyn.ivrecomp'" in badging and "versionCode='22'" in badging and "versionName='0.5.9.3-xhyn-test4'" in badging
assert "minSdkVersion:'29'" in badging and "targetSdkVersion:'35'" in badging
assert 'application-debuggable' not in badging
(checks/'apk-badging.txt').write_text(badging)
manifest=subprocess.check_output([str(bt/'aapt2'),'dump','xmltree','--file','AndroidManifest.xml',str(apk)],text=True)
assert 'com.libertyrecomp.LibertyActivity' in manifest and 'com.libertyrecomp.LauncherActivity' in manifest
(checks/'apk-manifest.txt').write_text(manifest)
main_symbols=subprocess.check_output([str(llvm/'llvm-nm'),'-D','--defined-only',str(staged/'libmain.so')],text=True)
for entry in ['nativeSetTouchLayout','nativeGetSetting','nativeApplySettings','nativeHostMenu','nativeEditLayout']:
 assert 'Java_com_libertyrecomp_LibertyActivity_'+entry in main_symbols
runtime_symbols=subprocess.check_output([str(llvm/'llvm-nm'),'-D','--defined-only',str(staged/'librexruntime.so')],text=True)
assert 'CreateShaderModule' in runtime_symbols
assert b'SGSR1 shader creation failed' in (staged/'librexruntime.so').read_bytes()
assert b'gta4-frame-stats:' in (staged/'librexgpu-gta4-native.so').read_bytes()
assert b'Vulkan shader compatibility rejected module' in (staged/'librexruntime.so').read_bytes()
report.update(version='0.5.9.3-xhyn-test4',application_id='com.xhyn.ivrecomp',version_code=22,abi='arm64-v8a',min_android_api=29,
 source_checkout_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),base_commit='93a67c6e057b83c8932826961ff9a55c5834d1dc',
 apk_sha256=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest(),apk_bytes=apk.stat().st_size,signer_sha256=signature[1],
 signature_verified=True,same_signer_as_test3=True,native_libraries_exact_and_16kb_aligned=True,jni_controller_bridge_verified=True,
 native_shader_creation_export_verified=True,publication='Not published',device_gameplay_verified=False)
(root.parent/'test4-checkpoint/XHYN-IV-test4-build.json').write_text(json.dumps(report,indent=2)+'\n')
print('Verified: 11 exact ARM64/16 KiB libraries, controller JNI, runtime shader export, manifest, assets, no test fixtures, matching release signer.')
print(apk.name,report['apk_bytes'],report['apk_sha256'])

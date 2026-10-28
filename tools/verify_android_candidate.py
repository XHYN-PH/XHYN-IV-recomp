#!/usr/bin/env python3
"""Verify a built Android candidate against staged natives and source asset pins."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--version', required=True)
    p.add_argument('--code', type=int, required=True)
    p.add_argument('--certificate-sha256', help='Require a signed APK with this certificate')
    p.add_argument('--unsigned-baseline', type=Path, help='Compare every pre-sign ZIP entry byte for byte')
    p.add_argument('--report', type=Path, required=True)
    args = p.parse_args()
    bt = Path(os.environ['ANDROID_SDK_ROOT']) / 'build-tools/35.0.0'
    staged = ROOT / 'out/apk-release/lib/arm64-v8a'
    source = ROOT / 'os/android/app/src/main'
    hashes = {}
    driver_hashes = {}
    members = 0
    with zipfile.ZipFile(args.apk) as z:
        names = z.namelist()
        assert len(names) == len(set(names)) and z.testzip() is None
        assert not any('v38' in n.lower() for n in names)
        assert not any(n.lower().endswith(('.p12', '.jks', '.keystore')) or 'store-password' in n.lower() for n in names)
        libs = [n for n in names if n.startswith('lib/')]
        expected_libs = {'libmain.so','libliberty_install.so','librexruntime.so','librexgpu-gta4-native.so',
                         'librexgpu-xenos.so','libSDL3.so','libc++_shared.so','libvulkan.so',
                         'libmain_hook.so','libhook_impl.so','libc11_threads_compat.so'}
        assert len(libs) == 11 and {Path(n).name for n in libs} == expected_libs
        assert all(n.startswith('lib/arm64-v8a/') for n in libs)
        for n in libs:
            data = z.read(n)
            assert data == (staged / Path(n).name).read_bytes(), n
            assert data[:6] == b'\x7fELF\x02\x01' and struct.unpack_from('<H', data, 18)[0] == 183
            offset = struct.unpack_from('<Q', data, 32)[0]
            size, count = struct.unpack_from('<HH', data, 54)
            for i in range(count):
                kind, flags, off, addr, physical, filesz, memsz, align = struct.unpack_from('<IIQQQQQQ', data, offset+i*size)
                if kind == 1:
                    assert align >= 16384 and off % 16384 == addr % 16384, (n, align)
            hashes[Path(n).name] = sha(data)
        dex = b''.join(z.read(n) for n in names if re.fullmatch(r'classes[0-9]*\.dex', n))
        for cls in ['ThermalStatus', 'OverlayMetrics', 'ControllerNavigation', 'TouchEditorActivity', 'PerformanceHud', 'DebugLogArchive']:
            assert ('Lcom/libertyrecomp/'+cls+';').encode() in dex, cls
        for cls in ['RefinementRegressionTest', 'MenuRegressionTest', 'GyroMathTest', 'TouchIconAssetsTest']:
            assert cls.encode() not in dex, cls
        icons = list((source/'assets/touch-icons').glob('*.png'))
        assert len(icons) == 44
        for icon in icons:
            assert z.read('assets/touch-icons/'+icon.name) == icon.read_bytes(), icon.name
        # The Java selector is the runtime's hash authority; inspect every pin.
        java = (source/'java/com/libertyrecomp/BundledDrivers.java').read_text()
        bundles = re.findall(r'Map<String,String> files=new LinkedHashMap<>\(\);(.*?)bundles.put\("[^"]+",new Bundle\("[^"]+","([^"]+)","[^"]+",\d+,"[^"]+","([0-9a-f]{64})",files\)\);', java, re.S)
        assert len(bundles) == 6
        for body, filename, digest in bundles:
            data = z.read('assets/drivers/'+filename)
            assert sha(data) == digest and data == (source/'assets/drivers'/filename).read_bytes(), filename
            driver_hashes[filename] = digest
            with zipfile.ZipFile(io.BytesIO(data)) as driver:
                assert driver.testzip() is None
                pins = re.findall(r'files.put\("([^"]+)","([0-9a-f]{64})"\);', body)
                assert pins
                for member, expected in pins:
                    assert sha(driver.read(member)) == expected, (filename, member)
                    members += 1
        assert {n for n in names if n.startswith('assets/drivers/') and n.endswith('.zip')} == {'assets/drivers/'+n for n in driver_hashes}
        for file in ['touch-defaults.json', 'xbox-defaults.json', 'drivers/xhyn-bundled-provenance.json']:
            assert z.read('assets/'+file) == (source/'assets'/file).read_bytes(), file
        for n in ['assets/TOUCH-NOTICE.txt', 'assets/COPYING']:
            assert n in names
        for library, token in [('librexgpu-gta4-native.so',b'frame-hitch:'), ('librexgpu-gta4-native.so',b'idle_retained_bytes='),
                               ('librexruntime.so',b'empty_queue='), ('libmain.so',b'gta4_thermal_status')]:
            assert token in z.read('lib/arm64-v8a/'+library), (library, token)
        if args.unsigned_baseline:
            with zipfile.ZipFile(args.unsigned_baseline) as baseline:
                for n in baseline.namelist():
                    assert baseline.read(n) == z.read(n), ('changed while signing', n)
    subprocess.run([str(bt/'zipalign'), '-c', '-P', '16', '4', str(args.apk)], check=True)
    badging = subprocess.check_output([str(bt/'aapt2'), 'dump', 'badging', str(args.apk)], text=True)
    for text in ["name='com.xhyn.ivrecomp'", f"versionCode='{args.code}'", f"versionName='{args.version}'", "minSdkVersion:'29'", "targetSdkVersion:'35'"]:
        assert text in badging, text
    assert 'application-debuggable' not in badging
    certificate = None
    if args.certificate_sha256:
        result = subprocess.check_output(['java','-jar',str(bt/'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(args.apk)],text=True)
        certificate = re.search(r'Signer #1 certificate SHA-256 digest: (\S+)', result)[1]
        assert certificate == args.certificate_sha256
        args.report.with_suffix('.signature.txt').write_text(result)
    report = dict(version=args.version, version_code=args.code, apk_sha256=sha(args.apk.read_bytes()),
                  apk_bytes=args.apk.stat().st_size, signer_sha256=certificate, native_library_sha256=hashes,
                  bundled_driver_zip_sha256=driver_hashes, driver_member_pins_verified=members,
                  touch_pngs_verified=len(icons), zip_crc_verified=True, alignment_16kb_verified=True,
                  unsigned_entries_unchanged=bool(args.unsigned_baseline), device_gameplay_verified=False)
    args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(f'Candidate verified: version {args.code}, 11 exact ARM64 libraries, 16 KiB alignment, 44 icons, 6 drivers/{members} member pins; signed={bool(certificate)}')
    print(args.apk.name, report['apk_sha256'])


if __name__ == '__main__':
    main()

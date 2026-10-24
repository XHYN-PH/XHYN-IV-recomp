#!/usr/bin/env python3
"""Package freshly staged ARM64 libraries with Android SDK tools and the existing release key."""
from pathlib import Path
import argparse
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def run(*args):
    subprocess.run(list(map(str, args)), check=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--android-jar', type=Path, required=True)
    p.add_argument('--build-tools', type=Path, required=True)
    p.add_argument('--keystore', type=Path, required=True)
    p.add_argument('--store-password-file', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    config = (ROOT / 'os/android/app/build.gradle.kts').read_text()
    package = re.search(r'applicationId\s*=\s*"([^"]+)"', config)[1]
    version = re.search(r'versionName\s*=\s*"([^"]+)"', config)[1]
    code = re.search(r'versionCode\s*=\s*(\d+)', config)[1]
    source = ROOT / 'os/android/app/src/main'
    out = ROOT / 'out/apk-release'
    out.mkdir(parents=True, exist_ok=True)
    for name in ['classes', 'generated', 'dex']:
        directory = out / name
        if directory.exists(): shutil.rmtree(directory)
        directory.mkdir()
    ET.register_namespace('android', ANDROID[1:-1])
    manifest = ET.parse(source / 'AndroidManifest.xml')
    root = manifest.getroot()
    root.set('package', package)
    root.set(ANDROID + 'versionCode', code)
    root.set(ANDROID + 'versionName', version)
    for element in root.iter():
        if element.tag in ('application', 'activity', 'activity-alias', 'service', 'receiver', 'provider', 'instrumentation'):
            for attribute in ['name', 'targetActivity']:
                name = element.get(ANDROID + attribute, '')
                if name.startswith('.'):
                    element.set(ANDROID + attribute, 'com.libertyrecomp' + name)
        for key, value in list(element.attrib.items()):
            element.set(key, value.replace('${applicationId}', package))
    root.find('application').set(ANDROID + 'debuggable', 'false')
    manifest.write(out / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    resources = out / 'resources.zip'
    resources.unlink(missing_ok=True)
    run(a.build_tools / 'aapt2', 'compile', '--dir', source / 'res', '-o', resources)
    run(a.build_tools / 'aapt2', 'link', '--manifest', out / 'AndroidManifest.xml',
        '-I', a.android_jar, '--min-sdk-version', '29', '--target-sdk-version', '35',
        '--custom-package', 'com.libertyrecomp', '--java', out / 'generated',
        '-A', source / 'assets', '-o', out / 'resources.apk', resources)
    sources = sorted((source / 'java').rglob('*.java'))
    sources += sorted((ROOT / 'glue/rexglue-sdk-main/thirdparty/sdl3/android-project/app/src/main/java').rglob('*.java'))
    sources += sorted((out / 'generated').rglob('*.java'))
    (out / 'sources.txt').write_text('\n'.join(map(str, sources)) + '\n')
    run('java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17',
        '-classpath', a.android_jar, '-d', out / 'classes', '@' + str(out / 'sources.txt'))
    with zipfile.ZipFile(out / 'classes.jar', 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for file in sorted((out / 'classes').rglob('*.class')):
            jar.write(file, file.relative_to(out / 'classes').as_posix())
    run(a.build_tools / 'd8', '--release', '--min-api', '29', '--lib', a.android_jar,
        '--output', out / 'dex', out / 'classes.jar')
    libs = source / 'jniLibs/arm64-v8a'
    names = {file.name for file in libs.glob('*.so')}
    expected = {'libmain.so', 'libliberty_install.so', 'librexruntime.so', 'librexgpu-gta4-native.so',
                'librexgpu-xenos.so', 'libSDL3.so', 'libc++_shared.so', 'libvulkan.so',
                'libmain_hook.so', 'libhook_impl.so', 'libc11_threads_compat.so'}
    if names != expected:
        raise SystemExit('Missing or unexpected staged native libraries: ' + repr(names ^ expected))
    (out / 'lib/arm64-v8a').mkdir(parents=True, exist_ok=True)
    unsigned = out / 'unsigned.apk'
    shutil.copy2(out / 'resources.apk', unsigned)
    with zipfile.ZipFile(unsigned, 'a') as apk:
        for file in sorted((out / 'dex').glob('*.dex')):
            apk.write(file, file.name, compress_type=zipfile.ZIP_DEFLATED)
        for file in sorted(libs.glob('*.so')):
            shutil.copy2(file, out / 'lib/arm64-v8a' / file.name)
            apk.write(file, 'lib/arm64-v8a/' + file.name, compress_type=zipfile.ZIP_DEFLATED)
    aligned = out / 'aligned.apk'
    run(a.build_tools / 'zipalign', '-f', '-P', '16', '4', unsigned, aligned)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    run('java', '-jar', a.build_tools / 'lib/apksigner.jar', 'sign', '--ks', a.keystore,
        '--ks-pass', 'file:' + str(a.store_password_file.resolve()), '--out', a.output, aligned)
    print('Signed release package:', a.output.name, a.output.stat().st_size)


if __name__ == '__main__': main()

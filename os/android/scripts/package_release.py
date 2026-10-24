#!/usr/bin/env python3
"""Package ARM64 on Linux; optionally retain verified native code for Java/asset hotfixes."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


def run(*command):
    subprocess.run(list(map(str, command)), check=True)


def verify_apk(apk, build_tools, *, signed):
    """Check the actual output archive, not just the signing command's status."""
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("APK contains duplicate archive entries")
        required = {"AndroidManifest.xml", "resources.arsc", "classes.dex"}
        if not required.issubset(names):
            raise RuntimeError("APK is missing its manifest, resources or dex")
        damaged = archive.testzip()
        if damaged:
            raise RuntimeError(f"APK contains a damaged entry: {damaged}")
    # Android's reader must accept both the ZIP and the binary manifest.
    run(build_tools / "aapt2", "dump", "badging", apk)
    if signed:
        run("java", "-jar", build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk)
    run(build_tools / "zipalign", "-c", "-P", "16", "4", apk)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    signing = parser.add_mutually_exclusive_group(required=True)
    signing.add_argument("--keystore", type=Path)
    signing.add_argument("--unsigned", action="store_true", help="Prepare an aligned APK for later signing; it cannot be installed yet")
    parser.add_argument("--password-file", type=Path)
    parser.add_argument("--alias", default="xhyn")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--native-apk",type=Path,help="Reuse native code/assets from an exact signed baseline for Java/asset-only fixes")
    parser.add_argument("--native-apk-sha256",help="Required SHA-256 pin for --native-apk; never use after native source changes")
    args = parser.parse_args()
    if args.keystore and not args.password_file:
        parser.error("--password-file is required with --keystore")
    if args.unsigned and args.password_file:
        parser.error("--password-file cannot be used with --unsigned")
    if bool(args.native_apk)!=bool(args.native_apk_sha256):
        parser.error("--native-apk and --native-apk-sha256 must be supplied together")
    root = Path(__file__).resolve().parents[3]
    sdk = Path(os.environ["ANDROID_SDK_ROOT"])
    ndk = Path(os.environ.get("ANDROID_NDK_HOME","/unconfigured-android-ndk"))
    build_tools = sdk / "build-tools/35.0.0"
    android = sdk / "platforms/android-35/android.jar"
    llvm = Path(os.environ.get("ANDROID_LLVM_BIN", str(ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin")))
    source = root / "os/android/app/src/main"
    engine = root / "glue/rexglue-sdk-main"
    build = root / "out/build/android-arm64-release"
    proxy = root / "out/build/android-driver-proxy"
    out = root / "out/apk-release"
    out.mkdir(parents=True, exist_ok=True)
    gradle = (source.parents[1] / "build.gradle.kts").read_text()
    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)[1]
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1]
    code = re.search(r'versionCode\s*=\s*(\d+)', gradle)[1]
    staged = out / "lib/arm64-v8a"
    if staged.exists():
        shutil.rmtree(staged)
    staged.mkdir(parents=True, exist_ok=True)
    if args.native_apk:
        if hashlib.sha256(args.native_apk.read_bytes()).hexdigest()!=args.native_apk_sha256:
            raise RuntimeError("Baseline APK hash mismatch")
        verify_apk(args.native_apk,build_tools,signed=True)
        with zipfile.ZipFile(args.native_apk) as archive:
            native=[name for name in archive.namelist() if name.startswith("lib/")]
            expected={"libmain.so","libliberty_install.so","librexruntime.so","librexgpu-gta4-native.so","librexgpu-xenos.so","libSDL3.so","libc++_shared.so","libvulkan.so","libmain_hook.so","libhook_impl.so","libc11_threads_compat.so"}
            if len(native)!=11 or {Path(n).name for n in native}!=expected or any(not n.startswith("lib/arm64-v8a/") for n in native):
                raise RuntimeError("Unexpected baseline native library set")
            for name in native:(staged/Path(name).name).write_bytes(archive.read(name))
        print("Java/assets hotfix: all native libraries retained byte-for-byte from the pinned signed baseline.")
    else:
        libraries = [build / "glue/gta4-recomp/libmain.so", build / "glue/gta4-recomp/libliberty_install.so",
                 *[engine / "out/linux-arm64" / name for name in
                   ("librexruntime.so", "librexgpu-gta4-native.so", "librexgpu-xenos.so", "libSDL3.so")],
                 ndk / "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"]
        for name in ("libvulkan.so", "libmain_hook.so", "libhook_impl.so", "libc11_threads_compat.so"):
            matches = list(proxy.rglob(name))
            if len(matches) != 1:
                raise RuntimeError(f"Expected one built proxy library: {name}, got {matches}")
            libraries += matches
        for path in libraries:
            if not path.is_file():
                raise FileNotFoundError(path)
            run(llvm / "llvm-strip", "--strip-unneeded", "-o", staged / path.name, path)
    allowed = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libvulkan.so",
               "libEGL.so", "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libaaudio.so",
               "libz.so", "libmediandk.so", "libnativewindow.so", "libjnigraphics.so"}
    for library in staged.glob("*.so"):
        reader=shutil.which("readelf") if args.native_apk else str(llvm/"llvm-readelf")
        if not reader:raise RuntimeError("readelf is required to inspect native dependencies")
        needed = subprocess.check_output([reader, "-d", library], text=True)
        for name in re.findall(r'Shared library: \[([^]]+)\]', needed):
            if name not in allowed and not (staged / name).is_file():
                raise RuntimeError(f"{library.name} needs missing {name}")
    (out / "resources.zip").unlink(missing_ok=True)
    run(build_tools / "aapt2", "compile", "--dir", source / "res", "-o", out / "resources.zip")
    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    tree = ET.parse(source / "AndroidManifest.xml")
    manifest = tree.getroot()
    manifest.set("package", "com.libertyrecomp")
    attribute = "{http://schemas.android.com/apk/res/android}"
    for activity in manifest.findall("application/activity"):
        name = activity.get(attribute + "name", "")
        if name.startswith("."):
            activity.set(attribute + "name", "com.libertyrecomp" + name)
    manifest.find("application").set(attribute + "debuggable", "false")
    tree.write(out / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    run(build_tools / "aapt2", "link", "--manifest", out / "AndroidManifest.xml", "-I", android,
        "--min-sdk-version", "29", "--target-sdk-version", "35", "--version-code", code,
        "--version-name", version, "--rename-manifest-package", app_id, "--custom-package",
        "com.libertyrecomp", "--java", out / "generated", "-o", out / "base.apk", out / "resources.zip")
    java = [*source.joinpath("java").rglob("*.java"),
            *engine.joinpath("thirdparty/sdl3/android-project/app/src/main/java").rglob("*.java"),
            *out.joinpath("generated").rglob("*.java")]
    java = [path for path in java if not any(part.startswith(".") for part in path.relative_to(root).parts)]
    classes = out / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    (out / "java-sources.txt").write_text("\n".join(map(str, java)) + "\n")
    run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "17", "-classpath", android,
        "-d", classes, "@" + str(out / "java-sources.txt"))
    with zipfile.ZipFile(out / "classes.jar", "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob("*.class")):
            relative=path.relative_to(classes)
            if not any(part.startswith(".") for part in relative.parts):archive.write(path,relative)
    dex = out / "dex"
    if dex.exists():
        shutil.rmtree(dex)
    dex.mkdir()
    run("java", "-cp", build_tools / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "29",
        "--lib", android, "--output", dex, out / "classes.jar")
    unsigned = out / "unsigned.apk"
    shutil.copyfile(out / "base.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        written=set(archive.namelist())
        for path in sorted(source.joinpath("assets").rglob("*")):
            if path.is_file() and not any(part.startswith(".") for part in path.relative_to(source/"assets").parts):
                name="assets/"+str(path.relative_to(source/"assets"));archive.write(path,name);written.add(name)
        for folder, destination in ((root / "LibertyRecompLib/font_atlases", "font_atlases"),
                                    (root / "LibertyRecompLib/private/button_prompts", "button_prompts")):
            for path in sorted(folder.rglob("*")):
                if path.is_file() and not any(part.startswith(".") for part in path.relative_to(folder).parts):
                    name="assets/Resources/"+destination+"/"+str(path.relative_to(folder))
                    if name not in written:archive.write(path,name);written.add(name)
        key = root / "LibertyRecompLib/aes_key.bin"
        if key.is_file():
            name="assets/Resources/aes_key.bin"
            if name not in written:archive.write(key,name);written.add(name)
        if "assets/COPYING" not in written:archive.write(root / "COPYING", "assets/COPYING");written.add("assets/COPYING")
        if args.native_apk:
            with zipfile.ZipFile(args.native_apk) as previous:
                for name in previous.namelist():
                    if name.startswith("assets/") and not name.startswith("assets/touch-icons/") and name not in written:
                        archive.writestr(name,previous.read(name));written.add(name)
        for path in sorted(staged.glob("*.so")):
            archive.write(path, "lib/arm64-v8a/" + path.name)
        for path in sorted(dex.glob("*.dex")):
            archive.write(path, path.name)
    run(build_tools / "zipalign", "-P", "16", "-f", "4", unsigned, out / "aligned.apk")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Publish only a complete, checked file; a failed sign must not replace an
    # earlier deliverable with a partial archive.
    with tempfile.TemporaryDirectory(prefix="apk-package-", dir=args.output.parent) as temporary:
        candidate = Path(temporary) / "candidate.apk"
        if args.unsigned:
            shutil.copyfile(out / "aligned.apk", candidate)
        else:
            run("java", "-jar", build_tools / "lib/apksigner.jar", "sign", "--ks", args.keystore,
                "--ks-key-alias", args.alias, "--ks-pass", "file:" + str(args.password_file),
                "--v4-signing-enabled", "false", "--out", candidate, out / "aligned.apk")
        verify_apk(candidate, build_tools, signed=not args.unsigned)
        os.replace(candidate, args.output)
    verify_apk(args.output, build_tools, signed=not args.unsigned)
    if args.unsigned:
        print("Unsigned APK prepared; sign with the existing release key before installation.")
    print(args.output.name, hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()

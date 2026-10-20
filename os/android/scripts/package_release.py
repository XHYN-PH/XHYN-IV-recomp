#!/usr/bin/env python3
"""Package freshly built ARM64 libraries on Linux with a private release key."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile


def run(*command):
    subprocess.run(list(map(str, command)), check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--password-file", type=Path, required=True)
    parser.add_argument("--alias", default="xhyn")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    sdk = Path(os.environ["ANDROID_SDK_ROOT"])
    ndk = Path(os.environ["ANDROID_NDK_HOME"])
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
    libraries = [build / "glue/gta4-recomp/libmain.so", build / "glue/gta4-recomp/libliberty_install.so",
                 *[engine / "out/linux-arm64" / name for name in
                   ("librexruntime.so", "librexgpu-gta4-native.so", "librexgpu-xenos.so", "libSDL3.so")],
                 ndk / "toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"]
    for name in ("libvulkan.so", "libmain_hook.so", "libhook_impl.so", "libc11_threads_compat.so"):
        matches = list(proxy.rglob(name))
        if len(matches) != 1:
            raise RuntimeError(f"Expected one built proxy library: {name}, got {matches}")
        libraries += matches
    staged = out / "lib/arm64-v8a"
    staged.mkdir(parents=True, exist_ok=True)
    for path in libraries:
        if not path.is_file():
            raise FileNotFoundError(path)
        run(llvm / "llvm-strip", "--strip-unneeded", "-o", staged / path.name, path)
    allowed = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libvulkan.so",
               "libEGL.so", "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libaaudio.so",
               "libz.so", "libmediandk.so", "libnativewindow.so", "libjnigraphics.so"}
    for library in staged.glob("*.so"):
        needed = subprocess.check_output([llvm / "llvm-readelf", "-d", library], text=True)
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
    classes = out / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    (out / "java-sources.txt").write_text("\n".join(map(str, java)) + "\n")
    run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "17", "-classpath", android,
        "-d", classes, "@" + str(out / "java-sources.txt"))
    with zipfile.ZipFile(out / "classes.jar", "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes))
    dex = out / "dex"
    if dex.exists():
        shutil.rmtree(dex)
    dex.mkdir()
    run("java", "-cp", build_tools / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "29",
        "--lib", android, "--output", dex, out / "classes.jar")
    unsigned = out / "unsigned.apk"
    shutil.copyfile(out / "base.apk", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(source.joinpath("assets").rglob("*")):
            if path.is_file() and not any(part.startswith(".") for part in path.relative_to(source/"assets").parts):
                archive.write(path, "assets/" + str(path.relative_to(source / "assets")))
        for folder, destination in ((root / "LibertyRecompLib/font_atlases", "font_atlases"),
                                    (root / "LibertyRecompLib/private/button_prompts", "button_prompts")):
            for path in sorted(folder.rglob("*")):
                if path.is_file():
                    archive.write(path, "assets/Resources/" + destination + "/" + str(path.relative_to(folder)))
        key = root / "LibertyRecompLib/aes_key.bin"
        if key.is_file():
            archive.write(key, "assets/Resources/aes_key.bin")
        archive.write(root / "COPYING", "assets/COPYING")
        for path in sorted(staged.glob("*.so")):
            archive.write(path, "lib/arm64-v8a/" + path.name)
        for path in sorted(dex.glob("*.dex")):
            archive.write(path, path.name)
    run(build_tools / "zipalign", "-P", "16", "-f", "4", unsigned, out / "aligned.apk")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    run("java", "-jar", build_tools / "lib/apksigner.jar", "sign", "--ks", args.keystore,
        "--ks-key-alias", args.alias, "--ks-pass", "file:" + str(args.password_file),
        "--out", args.output, out / "aligned.apk")
    run("java", "-jar", build_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", args.output)
    run(build_tools / "zipalign", "-c", "-P", "16", "4", args.output)
    print(args.output.name, hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()

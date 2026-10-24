#!/usr/bin/env python3
"""Build the pinned ESMERINGHOY source candidate; does not build or publish an APK."""
import argparse
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
LOCK = json.loads((HERE / "source.lock.json").read_text())


def run(args, **kwargs):
    subprocess.run([str(x) for x in args], check=True, **kwargs)


def digest(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def verify_source(source, lock):
    commit = subprocess.check_output(["git", "-C", source, "rev-parse", "HEAD"], text=True).strip()
    if commit != lock["mesa_commit"]:
        raise SystemExit("Existing source uses a different revision. Choose a fresh --work folder.")
    expected_files = lock["patched_files_sha256"]
    status = subprocess.check_output(["git", "-C", source, "-c", "core.quotePath=false", "status",
                                      "--porcelain=v1", "--untracked-files=normal", "--no-renames"], text=True)
    for line in status.splitlines():
        if line[3:] not in expected_files:
            raise SystemExit("Unexpected source edit: " + line[3:] + "; no files were reset.")
    for relative, expected in expected_files.items():
        path = source / relative
        if path.is_symlink() or not path.is_file() or digest(path) != expected:
            raise SystemExit("Existing source differs at " + relative + "; no files were reset.")


def verify_tools(ndk):
    revision = dict(tuple(part.strip() for part in line.split("=", 1)) for line in (ndk / "source.properties").read_text().splitlines() if "=" in line)
    if revision.get("Pkg.Revision", "") != LOCK["ndk_revision"]:
        raise SystemExit("Use the pinned Android NDK " + LOCK["ndk_revision"])
    for package in ("meson", "ninja", "cmake", "mako", "ply"):
        try:
            actual = importlib.metadata.version(package)
        except importlib.metadata.PackageNotFoundError:
            actual = "missing"
        if actual != LOCK[package]:
            raise SystemExit("Install " + package + "==" + LOCK[package] + "; found " + actual)
    for program, args, expected in (("meson", ["--version"], LOCK["meson"]),
                                    ("ninja", ["--version"], LOCK["ninja"]),
                                    ("cmake", ["--version"], "cmake version " + LOCK["cmake"])):
        actual = subprocess.check_output([program, *args], text=True).splitlines()[0].strip()
        if actual != expected:
            raise SystemExit(program + " executable differs from the pinned Python package.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work", type=Path, required=True, help="Dedicated build folder")
    parser.add_argument("--ndk", type=Path, required=True, help="Android NDK r29 root")
    parser.add_argument("--jobs", type=int, default=4)
    args = parser.parse_args()
    work, ndk = args.work.resolve(), args.ndk.resolve()
    verify_tools(ndk)
    patch = HERE / "patches/android-and-game-policy.patch"
    if digest(patch) != LOCK["patch_sha256"]:
        raise SystemExit("Source patch differs from the lock.")
    work.mkdir(parents=True, exist_ok=True)
    source, build = work / "mesa", work / "build"
    if not source.exists():
        run(["git", "init", source])
        run(["git", "-C", source, "remote", "add", "origin", LOCK["mesa_repository"]])
        run(["git", "-C", source, "fetch", "--depth=1", "origin", LOCK["mesa_commit"]])
        run(["git", "-C", source, "checkout", "--detach", "FETCH_HEAD"])
        run(["git", "-C", source, "apply", "--check", patch])
        run(["git", "-C", source, "apply", patch])
    verify_source(source, LOCK)
    compiler = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    empty = work / "empty-pkgconfig"
    empty.mkdir(exist_ok=True)
    pkg = shutil.which("pkg-config")
    if not pkg:
        raise SystemExit("Install pkg-config, flex, bison, m4 and glslang tools.")
    cross = work / "android-aarch64-api29.ini"
    cross.write_text(f"""[binaries]
c = '{compiler}/aarch64-linux-android29-clang'
cpp = ['{compiler}/aarch64-linux-android29-clang++', '-fno-exceptions', '-fno-unwind-tables', '-fno-asynchronous-unwind-tables', '-static-libstdc++']
ar = '{compiler}/llvm-ar'
strip = '{compiler}/llvm-strip'
pkg-config = ['env', 'PKG_CONFIG_LIBDIR={empty}', '{pkg}']
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'
[built-in options]
c_args = ['-Wno-error', '-Wno-error=gnu-empty-initializer', '-Wno-c++11-narrowing']
cpp_args = ['-Wno-c++11-narrowing']
c_link_args = ['-Wl,-z,max-page-size=16384']
cpp_link_args = ['-Wl,-z,max-page-size=16384', '-static-libstdc++']
""")
    native = work / "native-linux.ini"
    native.write_text("""[binaries]
c = '/usr/bin/gcc'
cpp = '/usr/bin/g++'
ar = '/usr/bin/ar'
strip = '/usr/bin/strip'
pkg-config = 'pkg-config'
""")
    options = ["-Dbuildtype=release", "-Dstrip=true", "-Dplatforms=android", "-Dvideo-codecs=", "-Dplatform-sdk-version=29", "-Dandroid-stub=true", "-Dgallium-drivers=", "-Dvulkan-drivers=freedreno", "-Dvulkan-beta=true", "-Dfreedreno-kmds=kgsl", "-Degl=disabled", "-Dandroid-libbacktrace=disabled", "-Dandroid-libperfetto=disabled", "-Dllvm=disabled", "-Dbuild-tests=false"]
    setup = ["meson", "setup"]
    if (build / "build.ninja").is_file():
        setup.append("--reconfigure")
    run(setup + [build, source, "--cross-file", cross, "--native-file", native] + options)
    run(["ninja", "-C", build, "-j" + str(max(1, args.jobs)), "src/freedreno/vulkan/libvulkan_freedreno.so"])
    output = work / "libvulkan_freedreno.so"
    shutil.copy2(build / "src/freedreno/vulkan/libvulkan_freedreno.so", output)
    run([compiler / "llvm-strip", "--strip-unneeded", output])
    print(output)
    print("SHA-256: " + digest(output))
    print("Source candidate built. Device correctness and matched performance checks remain pending.")


if __name__ == "__main__":
    main()

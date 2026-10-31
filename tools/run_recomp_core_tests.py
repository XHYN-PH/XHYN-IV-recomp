#!/usr/bin/env python3
"""Run PPC bridge, stack, byte-order and XEXP record tests with ASan and UBSan.

Uses the repository's pinned Catch2 submodule; never downloads dependencies.
"""
from pathlib import Path
import argparse
import os
import platform
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "glue/rexglue-sdk-main"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cxx", default="g++")
    parser.add_argument("--jobs", type=int, default=min(4, os.cpu_count() or 1))
    args = parser.parse_args()
    catch = SDK / "thirdparty/catch2"
    if not (catch / "CMakeLists.txt").is_file():
        parser.error("Initialize pinned Catch2 first: git submodule update --init "
                     "glue/rexglue-sdk-main/thirdparty/catch2")
    output = ROOT / "out/host-tests"
    catch_build = output / "catch2"
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run(["cmake", "-S", str(catch), "-B", str(catch_build),
                    "-DCMAKE_BUILD_TYPE=Debug", "-DCATCH_BUILD_TESTING=OFF",
                    "-DCATCH_INSTALL_DOCS=OFF", "-DCATCH_INSTALL_EXTRAS=OFF"], check=True)
    subprocess.run(["cmake", "--build", str(catch_build), "--target", "Catch2WithMain",
                    "--parallel", str(max(1, args.jobs))], check=True)
    includes = [SDK / path for path in ("include", "src", "thirdparty/simde",
                "thirdparty/fmt/include", "thirdparty/spdlog/include", "thirdparty/catch2/src")]
    includes.append(catch_build / "generated-includes")
    sources = [SDK / "tests/unit" / path for path in (
        "ppc/import_function_test.cpp", "ppc/stack_test.cpp", "core/byte_order_test.cpp",
        "system/lzx_delta_patch_test.cpp")]
    flags = ["-std=c++23", "-O0", "-g1", "-pthread", "-fsanitize=address,undefined",
             "-fno-omit-frame-pointer", "-ffunction-sections", "-fdata-sections",
             "-DFMT_HEADER_ONLY", "-DSPDLOG_FMT_EXTERNAL"]
    if platform.machine().lower() in ("x86_64", "amd64"):
        flags.append("-msse4.1")
    executable = output / "recomp-core"
    subprocess.run([args.cxx, *flags, *["-I" + str(path) for path in includes],
                    *map(str, sources), str(ROOT / "tools/android_host_test_support.cpp"),
                    str(catch_build / "src/libCatch2Maind.a"),
                    str(catch_build / "src/libCatch2d.a"), "-Wl,--gc-sections",
                    "-o", str(executable)], check=True)
    subprocess.run([str(executable), "--reporter", "console"], check=True,
                    env={**os.environ, "ASAN_OPTIONS": "detect_leaks=1",
                         "UBSAN_OPTIONS": "halt_on_error=1"})


if __name__ == "__main__":
    main()

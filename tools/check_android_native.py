#!/usr/bin/env python3
"""Compile changed runtime surfaces with the real Android ARM64/API 29 headers.

This syntax check complements the host sanitizers and Java resource check; it
does not claim that the complete native libraries linked or ran on a phone.
"""
import argparse
from pathlib import Path
import platform
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / 'glue/rexglue-sdk-main'
SOURCES = (
    'src/core/crash_capture_posix.cpp',
    'src/core/exception_handler_posix.cpp',
    'src/system/xam/user_profile.cpp',
    'src/system/runtime.cpp',
    'src/filesystem/devices/host_path_device.cpp',
    'src/system/achievement_manager.cpp',
    'src/system/kernel_state.cpp',
    'gta4-recomp/src/gta4_quicksave_hooks.cpp',
    'gta4-recomp/src/gta4_input_hooks.cpp',
    'gta4-recomp/src/input/context_touch_overlay.cpp',
    'gta4-recomp/src/android/android_platform_stubs.cpp',
    'gta4-recomp/src/input/gamepad_touch_overlay.cpp',
    'gta4-recomp/src/input/liberty_touch_overlay.cpp',
    'gta4-recomp/src/input/context_touch_draw.cpp',
)
INCLUDES = ('src', 'include', 'gta4-recomp/src', 'gta4-recomp/generated',
    'thirdparty/fmt/include', 'thirdparty/spdlog/include',
    'thirdparty/tomlplusplus/include', 'thirdparty/imgui',
    'thirdparty/sdl3/include', 'thirdparty/xxHash', 'thirdparty/simde',
    'thirdparty/cli11/include', 'thirdparty/utfcpp/source',
    'thirdparty/vulkan-headers/include', 'thirdparty/volk')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    args = parser.parse_args()
    if platform.system() != 'Linux':
        parser.error('this checker currently supports Linux hosts')
    compiler = args.ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++'
    if not compiler.is_file():
        parser.error(f'Android compiler not found: {compiler}')
    flags = ['-std=c++23', '-fsyntax-only', '-DFMT_HEADER_ONLY',
        '-DFMT_USE_CONSTEVAL=0', '-DSPDLOG_FMT_EXTERNAL', '-DVK_NO_PROTOTYPES',
        '-DREXGLUE_USE_VULKAN=1', '-DGTA4_TOUCH_PRIMARY_PLAYER_ALIAS=1',
        *['-I' + str(SDK / path) for path in INCLUDES]]
    for source in SOURCES:
        subprocess.run([str(compiler), *flags, str(SDK / source)], check=True)
        print(f'Android ARM64 compiled: {source}', flush=True)
    print(f'{len(SOURCES)} Android ARM64 translation units passed syntax checks')


if __name__ == '__main__':
    main()

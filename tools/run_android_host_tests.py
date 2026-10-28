#!/usr/bin/env python3
"""Run the Android CPU/input regressions under ASan and UBSan on Linux."""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
SDK = ROOT / "glue/rexglue-sdk-main"
OUT = ROOT / "out/host-tests"
OUT.mkdir(parents=True, exist_ok=True)
includes = [SDK / p for p in ("src", "include", "gta4-recomp/src", "thirdparty/fmt/include",
    "thirdparty/spdlog/include", "thirdparty/simde", "thirdparty/xxHash",
    "thirdparty/cli11/include", "thirdparty/tomlplusplus/include", "thirdparty/imgui",
    "thirdparty/sdl3/include", "thirdparty/utfcpp/source")]
flags = ["-std=c++23", "-O0", "-g1", "-pthread", "-msse4.1", "-fsanitize=address,undefined",
    "-fno-omit-frame-pointer", "-ffunction-sections", "-fdata-sections", "-DFMT_HEADER_ONLY",
    "-DSPDLOG_FMT_EXTERNAL", *["-I" + str(p) for p in includes]]


def obj(path):
    path = ROOT / path
    output = OUT / (str(path.relative_to(ROOT)).replace("/", "_") + ".o")
    dep = output.with_suffix(".d")
    dependencies = [path]
    if dep.exists():
        dependencies += [Path(p) for p in dep.read_text().replace("\\\n", " ").split(":", 1)[1].split()]
    if not output.exists() or any(not p.exists() or p.stat().st_mtime > output.stat().st_mtime
                                  for p in dependencies):
        # The factory in this TU mentions physical backends not linked by
        # this host harness. The SDL fixture supplies a guest-memory boundary.
        # Keep ASan/UBSan, but avoid unused factory/guest-heap RTTI
        # relocations from vptr instrumentation preventing section collection.
        extra = ["-fno-sanitize=vptr"] if path.name in ("input_system.cpp","sdl_audio_driver.cpp") else []
        subprocess.run(["g++", *flags, *extra, "-MMD", "-MF", str(dep), "-c", str(path), "-o", str(output)], check=True)
    return str(output)


common = ["glue/rexglue-sdk-main/src/core/cvar.cpp", "glue/rexglue-sdk-main/src/core/platform/env_posix.cpp"]
native = ["tools/test_android_refinements.cpp", "tools/android_host_test_support.cpp", *common,
          "glue/rexglue-sdk-main/src/core/memory.cpp",
          "glue/rexglue-sdk-main/src/graphics/pipeline/texture/conversion.cpp"]
touch = ["tools/test_android_touch.cpp", *common,
    "glue/rexglue-sdk-main/src/input/absolute_pointer.cpp",
    "glue/rexglue-sdk-main/src/input/input_system.cpp",
    "glue/rexglue-sdk-main/gta4-recomp/src/input/liberty_touch_overlay.cpp",
    "glue/rexglue-sdk-main/gta4-recomp/src/input/gamepad_touch_overlay.cpp",
    *["glue/rexglue-sdk-main/thirdparty/imgui/" + name + ".cpp" for name in
      ("imgui", "imgui_draw", "imgui_tables", "imgui_widgets")]]
context_draw = ["glue/rexglue-sdk-main/tests/regression/controls/touch_activity_draw_runtime.cpp",
    *["glue/rexglue-sdk-main/gta4-recomp/src/input/" + name + ".cpp" for name in
      ("context_touch_activity", "context_touch_activity_layout", "context_touch_layout", "context_touch_draw")],
    *["glue/rexglue-sdk-main/thirdparty/imgui/" + name + ".cpp" for name in
      ("imgui", "imgui_draw", "imgui_tables", "imgui_widgets")]]
profile = ["tools/test_profile_setting_lifetime.cpp", "tools/android_host_test_support.cpp", *common,
    "glue/rexglue-sdk-main/src/core/byte_stream.cpp", "glue/rexglue-sdk-main/src/core/memory.cpp"]
mods = ["tools/test_data_mods.cpp", "tools/android_host_test_support.cpp", *common,
    *["glue/rexglue-sdk-main/src/" + path for path in (
      "filesystem/device.cpp", "filesystem/entry.cpp", "filesystem/devices/host_path_device.cpp",
      "filesystem/devices/host_path_entry.cpp", "filesystem/devices/host_path_file.cpp",
      "core/filesystem.cpp", "core/filesystem_posix.cpp", "core/mapped_memory_posix.cpp",
      "core/mutex.cpp", "core/utf8.cpp", "core/string_buffer.cpp", "core/filesystem_wildcard.cpp")]]
for name, files in (("android-audio", ["tools/test_android_audio.cpp","tools/android_host_test_support.cpp",*common,"glue/rexglue-sdk-main/src/audio/audio_driver.cpp","glue/rexglue-sdk-main/src/audio/sdl/sdl_audio_driver.cpp"]),
                    ("android-profile-lifetime", profile), ("android-data-mods", mods),
                    ("android-refinements", native), ("android-touch", touch),
                    ("android-context-draw", context_draw),
                    ("android-vertex-locations", ["tools/test_vertex_location_remap.cpp"]),
                    ("android-shader-compat", ["tools/test_android_shader_compat.cpp"]),
                    ("android-mobile", ["tools/test_android_mobile.cpp"]),
                    ("android-achievements",["tools/test_android_achievements.cpp","glue/rexglue-sdk-main/src/system/achievement_manager.cpp","glue/rexglue-sdk-main/src/core/diagnostics_policy.cpp",*common]),
                    ("android-launch", ["tools/test_android_launch.cpp","glue/rexglue-sdk-main/src/core/diagnostics_policy.cpp",*common])):
    objects = [obj(p) for p in files]
    executable = OUT / name
    subprocess.run(["g++", *flags, *objects, "-Wl,--gc-sections", "-o", str(executable)], check=True)
    data = "production-shaders" if name == "android-shader-compat" else "mobile-cache" if name=="android-mobile" else "touch-home"
    subprocess.run([str(executable), str(OUT / data)], check=True,
                   env={**os.environ, "ASAN_OPTIONS": "detect_leaks=" + os.environ.get("XHYN_HOST_DETECT_LEAKS", "1" if name in ("android-profile-lifetime", "android-data-mods") else "0")})

subprocess.run([sys.executable, str(ROOT / "tools/test_native_capture_png.py")], check=True)

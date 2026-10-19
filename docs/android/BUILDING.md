# Building the Android APK

The XHYN release APK is built on Linux. The original Windows/Git Bash scripts are also retained below. The Linux release packager requires a private signing key; keys and passwords must stay outside the repository.

The generated C++ for the game is checked in, as it is upstream, so building needs no game files.

## Linux release build

Use Android NDK r29 (`29.0.14206865`), SDK platform 35, build-tools 35.0.0, JDK 17, Python 3.10+, CMake 3.31.6 and Ninja. Install host builds of DXC and glslang, and preserve executable permissions and NDK symlinks when extracting tool archives. The release toolchain used DXC 1.9.2609 and glslang 16.6.0.

Set `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME`, `JAVA_HOME` and `PATH` for your installation. Set `DXC_BIN`, `DXC_LIB` and `GLSLANG_VALIDATOR` to your host shader tools. Run these commands from the repository root:

```bash
python3 tools/setup_repo.py

cmake -S . -B out/build/android-arm64-release -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared \
  -DLIBERTY_RECOMP_TARGET_PLATFORM=android \
  -DLIBERTY_RECOMP_ANDROID_RUNTIME_ASSETS=ON -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_CXX_SCAN_FOR_MODULES=OFF \
  '-DCMAKE_C_FLAGS=-march=armv8-a -moutline-atomics -fno-emulated-tls' \
  '-DCMAKE_CXX_FLAGS=-march=armv8-a -moutline-atomics -fno-emulated-tls' \
  -DREXGLUE_ENABLE_TRACY=OFF -DREXGLUE_ENABLE_PERF_COUNTERS=OFF \
  -DGTA4_NATIVE_DXC_EXECUTABLE="$DXC_BIN" \
  -DGTA4_NATIVE_DXC_LIBRARY_PATH="$DXC_LIB" \
  -DGTA4_NATIVE_GLSLANG_VALIDATOR="$GLSLANG_VALIDATOR" \
  -DGTA4_NATIVE_SPIRV_VAL="$ANDROID_NDK_HOME/shader-tools/linux-x86_64/spirv-val"
cmake --build out/build/android-arm64-release --target LibertyRecomp liberty_install --parallel 4

cmake -S os/android/native -B out/build/android-driver-proxy -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build out/build/android-driver-proxy --parallel 4

python3 os/android/scripts/package_release.py \
  --keystore /private/xhyn-release.p12 \
  --password-file /private/password.txt \
  --output out/XHYN-IV.apk
```

Adjust build parallelism to available RAM. This compiles large generated sources and may take a long time. The packager stages and strips the native libraries, compiles Java/resources, checks native dependencies, aligns the APK for 16 KiB pages and signs it. The default key alias is `xhyn`; use `--alias` for a different alias.

Generate and protect your own signing key for personal builds. A build signed with a different key cannot update an installed XHYN release. Export saves before replacing an installation; uninstalling removes its data. Never commit a keystore, password file or signing credentials.

The release uses generic ARMv8-A with outlined atomics and ThinLTO. The PGO helper is available for developers, but this release does not claim a trained PGO profile.

## Windows / Git Bash scripts

The following is the original workflow. The helper scripts use Windows host shader tools and Gradle debug signing. Do not use their automatically generated debug key for published updates.

### Prerequisites

| Tool | Version used |
|---|---|
| Git for Windows (Git Bash) | any recent |
| Python | 3.12 |
| CMake | 3.31 (3.29 or newer is required) |
| Ninja | 1.11 |
| Android Studio | for the SDK, the platform tools and its bundled JDK (`jbr`, JDK 17+) |
| Android SDK platform | 35 |
| **Android NDK** | **r29, `29.0.14206865`** (older NDKs lack `std::jthread` in libc++) |

`os/android/scripts/env.sh` finds the SDK at `%LOCALAPPDATA%\Android\Sdk` and the JDK in
`C:\Program Files\Android\Android Studio\jbr`. Override `ANDROID_HOME`, `ANDROID_NDK`, `NDK_VER` or
`JAVA_HOME` if yours are elsewhere.

### Steps

All commands run in Git Bash from the repository root.

```bash
# 1. Fetch the pinned dependencies and apply the source patches (as upstream)
python tools/setup_repo.py

# 2. Build the Vulkan driver proxy and the libadrenotools hooks (once)
bash os/android/scripts/build_driver_proxy.sh

# 3. Build the game, the runtime and the renderer, and stage them into jniLibs.
#    The first run also downloads DXC and glslang into out/host-tools.
#    STRIP=1 strips the libraries (about a 50 MB APK instead of about 170 MB).
STRIP=1 bash os/android/scripts/build_native.sh

# 4. Package the APK (debug-signed)
rm -f os/android/app/build/outputs/apk/debug/app-debug.apk
bash os/android/scripts/build_apk.sh

# 5. Install on the connected device and start the game
#    (through the launcher with "--ez play true", which presses Play at once)
bash os/android/scripts/install.sh
```

The output is `os/android/app/build/outputs/apk/debug/app-debug.apk`.

Notes:

- The first native build compiles about 90 large generated source files and takes a long time.
  Later builds are incremental.
- **Delete the old APK before step 4.** Gradle's incremental packaging otherwise leaves tens of
  megabytes of dead space in it.
- Leave `STRIP` unset for development builds. Unstripped libraries let `ndk-stack`,
  `os/android/scripts/symbolize.sh` and simpleperf resolve symbols.
- The build type defaults to `RelWithDebInfo` (`BUILD_TYPE=Release` builds into a separate
  folder).
- `-DLIBERTY_ANDROID_PGO=generate` / `=<profile.profdata>` in `build_native.sh`'s configure step
  enables profile-guided optimization (see [PORTING.md](PORTING.md#1-building-for-android)).
- APKs built on different machines are signed with different debug keys. Export saves before replacing an installation. Uninstalling deletes game and save data (see
  [INSTALL.md](INSTALL.md#updating-and-saves)).

## Development helpers

| Script | Purpose |
|---|---|
| `logs.sh` | Follow the game's logcat output (`--dump` saves it, `--pull` also fetches the runtime's log files) |
| `push_args.sh <file>` | Push a settings profile as `args.txt` (`--clear` removes it) |
| `push_install_sources.sh <iso> [<tu>]` | Copy the installation sources to the device and preselect them |
| `live_cvar.sh --name=value ...` | Change settings in the running game |
| `fps.sh [seconds]` | Measure the presented frame rate through SurfaceFlinger |
| `cpu_frame.sh`, `gpu_counters.sh` | CPU and Adreno GPU profiling helpers |
| `symbolize.sh` | Symbolize a native crash backtrace |

`os/android/android_args/` holds the settings profiles used during development:
`perf.txt` (older handheld profile), `debug.txt` (verbose logging) and `rewrite_off.txt`
(disables the 0.5 renderer CPU changes).

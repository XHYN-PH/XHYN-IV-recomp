#!/bin/bash
# Build the Vulkan driver proxy (libvulkan.so) and the libadrenotools hook
# libraries, and stage them next to the engine in jniLibs.
set -euo pipefail
. "$(dirname "$0")/env.sh"
OUT="$REPO/out/build/android-driver-proxy"
cmake -S "$APP_DIR/native" -B "$OUT" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$OUT"
mkdir -p "$JNILIBS"
for lib in libvulkan.so libmain_hook.so libhook_impl.so libc11_threads_compat.so; do
  found=$(find "$OUT" -name "$lib" -type f | head -1)
  [ -n "$found" ] || { echo "!! $lib was not built"; exit 1; }
  "$NDK_BIN/llvm-strip" --strip-unneeded -o "$JNILIBS/$lib" "$found"
done
ls -la "$JNILIBS"/libvulkan.so "$JNILIBS"/libmain_hook.so "$JNILIBS"/libhook_impl.so "$JNILIBS"/libc11_threads_compat.so

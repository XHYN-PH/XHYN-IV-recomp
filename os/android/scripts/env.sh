#!/bin/bash
# Shared environment for the LibertyRecomp Android scripts. Source it:
#   . os/android/scripts/env.sh
# Written for Git Bash on Windows; every path can be overridden from outside.

_scripts_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export APP_DIR="$(cd "$_scripts_dir/.." && pwd)"
export REPO="$(cd "$APP_DIR/../.." && pwd)"

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -n "${LOCALAPPDATA:-}" ]; then
    ANDROID_HOME="$(cygpath -u "$LOCALAPPDATA")/Android/Sdk"
  else
    ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export NDK_VER="${NDK_VER:-29.0.14206865}"
export ANDROID_NDK="${ANDROID_NDK:-$ANDROID_HOME/ndk/$NDK_VER}"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) _host=windows-x86_64 ;;
  Darwin)               _host=darwin-x86_64 ;;
  *)                    _host=linux-x86_64 ;;
esac
export NDK_BIN="$ANDROID_NDK/toolchains/llvm/prebuilt/$_host/bin"
export NDK_SYSROOT_LIB="$ANDROID_NDK/toolchains/llvm/prebuilt/$_host/sysroot/usr/lib/aarch64-linux-android"

export BUILD_TYPE="${BUILD_TYPE:-RelWithDebInfo}"
case "$BUILD_TYPE" in
  Release) _build_suffix=-release ;;
  *)       _build_suffix= ;;
esac
export BUILD_DIR="${BUILD_DIR:-$REPO/out/build/android-arm64$_build_suffix}"
# rexglue writes every binary of the build here (REX_PLATFORM linux-arm64).
export REX_OUT_DIR="$REPO/glue/rexglue-sdk-main/out/linux-arm64"
export JNILIBS="$APP_DIR/app/src/main/jniLibs/arm64-v8a"
export ASSETS="$APP_DIR/app/src/main/assets"

export PKG="${PKG:-com.xhyn.ivrecomp}"
# The launcher; "--ez play true" makes it start the game at once.
export ACTIVITY="${ACTIVITY:-$PKG/com.libertyrecomp.LauncherActivity}"
# The game runs in its own process.
export GAME_PROC="$PKG:game"
export FILES="/sdcard/Android/data/$PKG/files"
export GAME_DIR="$FILES/LibertyRecomp/game"

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

# Whichever single device is attached, unless SERIAL is set.
if [ -z "${SERIAL:-}" ]; then
  _ready=$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
  if [ "$(printf '%s\n' "$_ready" | grep -c .)" = "1" ]; then
    SERIAL="$_ready"
  fi
fi
export SERIAL="${SERIAL:-}"
# Git Bash rewrites arguments that look like POSIX paths (/sdcard/...) into
# Windows paths; adb must see them unchanged.
# ADB_TIMEOUT (seconds, optional) ends an adb call that hangs - run-as shells
# occasionally never return while the game is busy.
adbs() {
  local limit=()
  [ -n "${ADB_TIMEOUT:-}" ] && limit=(timeout "$ADB_TIMEOUT")
  if [ -n "$SERIAL" ]; then
    MSYS_NO_PATHCONV=1 "${limit[@]}" adb -s "$SERIAL" "$@"
  else
    MSYS_NO_PATHCONV=1 "${limit[@]}" adb "$@"
  fi
}
# Local file arguments for adbs (push/install) in a form adb.exe understands.
winpath() { cygpath -m "$1" 2>/dev/null || printf '%s\n' "$1"; }

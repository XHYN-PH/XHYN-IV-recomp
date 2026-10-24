#!/bin/bash
# Obtain official host shader tools. Override HOST_* to use a verified local build.
set -euo pipefail
. "$(dirname "$0")/env.sh"
DEST="$REPO/out/host-tools"
mkdir -p "$DEST"
case "$(uname -s)" in
 MINGW*|MSYS*|CYGWIN*)
  if [ ! -f "$DEST/dxc/bin/x64/dxc.exe" ]; then curl --fail --location --retry 3 -o "$DEST/dxc.zip" https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.9.2609/dxc_2026_09_29.zip; powershell -NoProfile -Command "Expand-Archive -Force '$DEST/dxc.zip' '$DEST/dxc'";fi
  if [ ! -f "$DEST/glslang/bin/glslang.exe" ]; then curl --fail --location --retry 3 -o "$DEST/glslang.zip" https://github.com/KhronosGroup/glslang/releases/download/16.6.0/glslang-16.6.0-windows-x86_64-release.zip;unzip -q -o "$DEST/glslang.zip" -d "$DEST/glslang";fi
  HOST_DXC="${HOST_DXC:-$DEST/dxc/bin/x64/dxc.exe}";HOST_DXC_LIB="${HOST_DXC_LIB:-$DEST/dxc/bin/x64}";HOST_GLSLANG="${HOST_GLSLANG:-$DEST/glslang/bin/glslang.exe}";HOST_SPIRV_VAL="${HOST_SPIRV_VAL:-$ANDROID_NDK/shader-tools/windows-x86_64/spirv-val.exe}" ;;
 Linux)
  if [ -z "${HOST_DXC:-}" ] && [ ! -x "$DEST/dxc/bin/dxc" ];then curl --fail --location --retry 3 -o "$DEST/dxc.tar.gz" https://github.com/microsoft/DirectXShaderCompiler/releases/download/v1.9.2609/linux_dxc_2026_09_28.x86_x64.tar.gz;mkdir -p "$DEST/dxc";tar --no-same-owner -xzf "$DEST/dxc.tar.gz" -C "$DEST/dxc";fi
  if [ -z "${HOST_GLSLANG:-}" ] && [ ! -x "$DEST/glslang/bin/glslang" ];then curl --fail --location --retry 3 -o "$DEST/glslang.tar.gz" https://github.com/KhronosGroup/glslang/releases/download/16.6.0/glslang-16.6.0-linux-x86_64-release.tar.gz;mkdir -p "$DEST/glslang";tar --no-same-owner -xzf "$DEST/glslang.tar.gz" -C "$DEST/glslang";fi
  HOST_DXC="${HOST_DXC:-$DEST/dxc/bin/dxc}";HOST_DXC_LIB="${HOST_DXC_LIB:-$DEST/dxc/lib}";HOST_GLSLANG="${HOST_GLSLANG:-$DEST/glslang/bin/glslang}";HOST_SPIRV_VAL="${HOST_SPIRV_VAL:-$ANDROID_NDK/shader-tools/linux-x86_64/spirv-val}" ;;
 *) : "${HOST_DXC:?Set HOST_DXC for this host}";: "${HOST_DXC_LIB:?Set HOST_DXC_LIB}";: "${HOST_GLSLANG:?Set HOST_GLSLANG}";: "${HOST_SPIRV_VAL:?Set HOST_SPIRV_VAL}" ;;
esac
for program in "$HOST_DXC" "$HOST_GLSLANG" "$HOST_SPIRV_VAL";do [ -x "$program" ]||{ echo "Missing executable: $program" >&2;exit 1;};done
{ printf 'export HOST_DXC=%q\n' "$HOST_DXC";printf 'export HOST_DXC_LIB=%q\n' "$HOST_DXC_LIB";printf 'export HOST_GLSLANG=%q\n' "$HOST_GLSLANG";printf 'export HOST_SPIRV_VAL=%q\n' "$HOST_SPIRV_VAL"; } > "$DEST/paths.sh"
"$HOST_DXC" --version
"$HOST_GLSLANG" --version | head -1

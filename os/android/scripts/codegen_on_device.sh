#!/bin/bash
# Runs the ReXGlue code generator on the phone (there is no Windows build of
# it here) and pulls the generated C++ back, without touching the repository's
# generated/ folder.
#   codegen_on_device.sh <label> [config.toml]
# Builds the Android rexglue binary first. The generator runs at the lowest
# priority on the little cores, so a game in the foreground keeps its speed.
# Result: out/codegen/<label>/generated/ and out/codegen/<label>/codegen.log.
set -euo pipefail
. "$(dirname "$0")/env.sh"
LABEL="$1"
CONFIG="${2:-}"
export ADB_TIMEOUT="${ADB_TIMEOUT:-600}"
GEN_DIR="$REPO/glue/rexglue-sdk-main/gta4-recomp"
REX_OUT="$REPO/glue/rexglue-sdk-main/out/linux-arm64"
OUT="$REPO/out/codegen/$LABEL"
DEV=/data/local/tmp/lrcg

cmake --build "$BUILD_DIR" --target rexglue -- -j"$(nproc)" > /dev/null
mkdir -p "$OUT"
adbs shell "rm -rf $DEV && mkdir -p $DEV/assets"
for f in "$REX_OUT/rexglue" "$REX_OUT/librexruntime.so" "$REX_OUT/libSDL3.so" \
         "$APP_DIR/app/src/main/jniLibs/arm64-v8a/libc++_shared.so" \
         "$GEN_DIR/gta4_manifest.toml"; do
  adbs push "$(winpath "$f")" "$DEV/" > /dev/null
done
adbs push "$(winpath "${CONFIG:-$GEN_DIR/gta4_config.toml}")" "$DEV/gta4_config.toml" > /dev/null
for f in "$GEN_DIR"/assets/default_v8.xex*; do
  adbs push "$(winpath "$f")" "$DEV/assets/" > /dev/null
done
start=$(date +%s)
adbs shell "cd $DEV && chmod 755 rexglue && LD_LIBRARY_PATH=. nice -n ${CODEGEN_NICE:-19} taskset ${CODEGEN_CPUS:-0f} ./rexglue codegen gta4_manifest.toml > codegen.log 2>&1; echo exit=\$? >> codegen.log"
echo "[$LABEL] codegen took $(( $(date +%s) - start )) s"
adbs pull "$DEV/codegen.log" "$(winpath "$OUT/codegen.log")" > /dev/null
tail -3 "$OUT/codegen.log"
adbs shell "cd $DEV && tar cf generated.tar generated"
adbs pull "$DEV/generated.tar" "$(winpath "$OUT/generated.tar")" > /dev/null
rm -rf "$OUT/generated"
tar xf "$OUT/generated.tar" -C "$OUT" && rm -f "$OUT/generated.tar"
adbs shell "rm -rf $DEV"
echo "[$LABEL] $(ls "$OUT/generated" | wc -l) files in out/codegen/$LABEL/generated"

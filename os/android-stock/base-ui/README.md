# Base English Test 2

Version `0.9.24-xhyn-base-english-test2`, code 444. Package
`com.xhyn.ivrecomp`, existing XHYN release signer. This is the original v442
launcher, edited in place. The Test 1 replacement UI is excluded.

- Translate 299 distinct Android UI strings (345 occurrences), including settings,
  touch editor, accessibility labels, dialogs and errors. Keep artwork credits.
- Show the original ISO/TU8 installer in the existing scrollable Game data card.
  v442 constructed the controls without attaching them to that page. Keep them
  expanded after game detection, retain folder selection, optional episodes,
  native disc validation, permission prompts, progress and cancellation.
- Set the default game language to English in three device profiles and the stock
  settings defaults. A one-time atomic settings update changes only the language;
  subsequent user language choices persist. Saves and game paths are retained.
- Translate two hardcoded native hints (Skip, map drag/pinch). Only their checked
  NUL-terminated `.rodata` spans change. All executable instructions, ELF sizes,
  relocations and other native sections remain byte-identical. The other eleven
  libraries, all resource/artwork entries and unrelated assets remain identical.

## Build

Requires the exact base SHA pinned in `preflight.py`, JDK 17+, Android SDK/build
tools 35.0.0, apktool 3.0.3. Base APK and signing material remain local.

```sh
python3 os/android-stock/build_base_ui.py \
  --base /path/to/v442.apk --apktool /path/to/apktool.jar \
  --sdk "$ANDROID_SDK_ROOT" --out /path/to/test2
python3 -m unittest discover -s os/android-stock/tests -v
```

This emits an aligned **unsigned** APK and `verification.json`. Sign separately
with the existing XHYN key using APK Signature Schemes v2/v3; verify with
`apksigner verify --min-sdk-version 24 --verbose --print-certs` and
`zipalign -c -P 16 4`. Re-run `build_base_ui.verify_candidate(base, signed)`
after signing. The older `preflight.verify_candidate` is specific to Test 1.
Use `verify_base_ui.py --base … --apk … --aapt2 … --out …` to also check
the compiled manifest and the final DEX English-string coverage.

## Tests of the actual base UI

The optional isolated Robolectric module tests the assembled original classes,
without the Test 1 adapter's stock-class stubs. Convert DEX to a test-only JVM
JAR with official dex2jar 2.4, then compute JVM stack frames with bundled ASM.
The conversion is never used in the APK. The official `dex-tools-v2.4.zip`
used here has SHA-256
`ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a`.

```sh
python3 os/android-stock/base-ui/prepare_host_tests.py \
  --base /path/to/v442.apk --dex /path/to/test2/work/stock/build/apk/classes.dex \
  --dex2jar /path/to/dex-tools-v2.4 --sdk "$ANDROID_SDK_ROOT" --out /path/to/host
gradle -p os/android-stock :baseUiTests:testDebugUnitTest \
  -PstockUiTestJar=/path/to/host/stock-framed.jar \
  -PstockUiResources=/path/to/test2/work/stock/res \
  -PstockUiAssets=/path/to/host/assets
```

Checks cover English pages/tabs, the actual attached ISO/TU controls, scrolling
to both selectors and Install, the original document-picker request codes,
valid-disc/TU requirements, busy/cancel state and language migration that keeps
other settings and later language choices. Android 10 and 15 are exercised.
CRC, class/interface continuity and all preserved payload bytes are checked.

Native installation of a real ISO/TU, engine startup, translations supplied by
game data and sustained gameplay still require the user's phone test. This
build does not claim to fix native gameplay crashes or finish recompilation.

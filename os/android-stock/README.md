# Android changes for stock v442

The current focused build is **Base English Test 2 (code 444)**. It edits the
original launcher and touch-settings UI in place, restores the ISO/TU8 controls
inside Game data, and starts with English. See
[the build and verification instructions](base-ui/README.md).

The replacement UI described below is the earlier Test 1 and is **not included
in Test 2**. Further feature migration is paused for the user's phone test.

## Earlier Test 1 layer

This is a separate Android project. It does not build or replace the native
libraries in the source port. The supplied base APK is required locally and is
not checked into Git. Only the exact pinned base is accepted.

The layer retains the base's original `classes.dex`, resources, assets and all
12 native libraries, adding `classes2.dex` and manifest components. A lifecycle
adapter replaces the launcher view while retaining the stock installer and
launch checks. An opaque session Activity pauses/resumes the original game.

Read [the interface/scope notes](../../docs/development/V442-ANDROID-LAYER.md)
before changing the adapter. No old JNI methods may be assumed to exist.

Host verification (JDK 17+, Android SDK 35, Gradle 8.11.1):

```sh
gradle -p os/android-stock :layer:testDebugUnitTest :layer:lintDebug
python3 -m unittest discover -s os/android-stock/tests -v
python3 os/android-stock/preflight.py --base /path/to/v442.apk
```

Package only after review and host checks:

```sh
python3 os/android-stock/build_layer.py \
  --base /path/to/v442.apk \
  --apktool /path/to/apktool_3.0.3.jar \
  --sdk "$ANDROID_SDK_ROOT" \
  --out /path/to/output
```

The packager emits an **unsigned, aligned** APK and a verification report.
Sign it separately with the existing XHYN release identity; never commit a
keystore or password. Verify the signed result using `preflight.py --base …
--candidate …` and Android `apksigner verify`. Keep application ID
`com.xhyn.ivrecomp` so updates retain the user's data. Version code is 443.

The standalone Gradle APK is a host-test harness, **not a playable release**.
Use `build_layer.py` to include the pinned stock engine.

Phone validation remains required for SDL pause/resume, touch coordinates,
gamepad input, actual drivers, sustained gameplay and achievement data exposed
by the stock engine. Host tests cannot establish that the gameplay crashes are
fixed. Native shadow, recompiler, audio and pipeline changes are outside this
binary UI layer.

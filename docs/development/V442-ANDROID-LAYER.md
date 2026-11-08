# v442 Android layer

Work branch: `codex/v442-android-layer`. Requested features 1–11 are
implemented around the supplied stock APK, with the interface limits below. This does not complete the native
recompilation or incorporate the previous branch's C++ fixes.

## Pinned base and boundaries

- Base: `0.9.24-renderer-v442`, code 442, ARM64, Android 10+.
- APK SHA-256: `ae71035bbbed76c7bab9e55faca629e7524cd9fe22457c8fce15c04284a0b049`.
- All 12 native libraries, the original DEX and title assets must retain their
  exact bytes. The layer adds a second DEX and Android manifest components.
- No matching source was supplied or identified. Decompiled Java was inspected
  locally to document interfaces; it is not imported as replacement source.
- Existing XHYN signing identity and package name are used for updates. Saves
  must survive an update; the original APK and credentials are not committed.

## Verified Java interfaces

The adapter is specific to the pinned APK and must fail with a readable error
on any other build. Obfuscated names below are actual DEX names, not JADX aliases.

- `com.libertyrecomp.LauncherActivity.o()` retains the stock launch checks,
  process cleanup and save preparation. Its original view is retained for the
  stock installer/cache workflow.
- `a.i.E(Context, File)` reads stock defaults and effective settings;
  `a.i.J(File, Map)` performs the stock settings update/normalization;
  `a.i.q(Context, File)` resolves game data; `a.i.m(File, ArrayList)` resolves
  portable saves, including a `--saved_game_root` argument.
- `com.libertyrecomp.LibertyActivity.d(File)` parses launch arguments. Save
  resolution follows this stock parser; it does not assume a TOML-only path
  takes precedence over the portable path supplied by the launcher.
- `com.libertyrecomp.Installer.c(Context)` loads stock resources/installer.
- `TouchSettings` has private static native `nativeOpen(String)`,
  `nativeGet(String)`, `nativeSet(String,String)`, `nativeLayout(int,float,float,float)`,
  `nativeMove(int,float,float,float)`, `nativeReset(boolean)` and `nativeSave()`.
  Layout JSON contains `action`, `x`, `y`, `r`, `label`, `icon` in viewport pixels.
  Editor changes are saved for the next launch, not falsely applied live.
- Driver selection is `<external files>/driver.txt`; custom packages are flat
  files in `<external files>/drivers/<name>`, selected as `custom:<name>`.
  Stock Turnip r6/PanVK rc4 selection and family checks remain in the loader.

## Feature scope and validation

1. Compact, inset-aware Android screens and dialogs.
2. XHYN home and session menu; a separate opaque Activity pauses/resumes SDL
   through the stock Android lifecycle.
3. Menu controller navigation; gameplay events remain with SDL except the
   documented session-menu shortcut.
4. Searchable stock settings, staged Save/Discard/reset, no old unsupported CVar.
5. Fullscreen touch editor using the stock JNI; undo/redo, modes, size and placement.
6. Bounded diagnostics ZIPs, previous exit information and content-URI sharing.
7. OS monitoring HUD and fullscreen placement editor. No Java refresh-rate FPS.
8. Stock-compatible driver import/select/delete; no native driver-loader swap.
9. Portable save backup/restore and individual slots, with recovery copies.
10. Validated loose data mods with conflict ordering and reversible file staging;
    never claim the old native mod hook exists in this APK.
11. Read-only achievement catalog browser when readable local data exists.

Before packaging: review JNI signatures, process boundaries, input forwarding,
insets, failure handling, save paths and file mutations. Run host UI/data tests.
After packaging: verify original DEX/assets/native hashes, manifest, alignment
and release signer. Gameplay/crash, controller hardware, driver and visual
validation require the user's Android device and installed game data.

## Pre-package review

- Stock DEX method/field definitions checked directly against the pinned APK.
  No old `nativeApplySettings`, gyro, host-menu or achievement-preparation JNI
  call is added. Original resource synchronization clears/replaces its cache
  when the APK version stamp changes.
- Settings use a locked next-launch queue. Stock renderer and supported
  sustained mode preferences are prepared before stock launch; native settings
  activate before the new game Activity reads its arguments.
- Save operations reject paths containing game/app data. Full restores retain
  a verified snapshot and process-death journal; slot changes retain independent
  recovery ZIPs. Pending restore/mod recovery blocks Play until the matching
  tool completes recovery.
- Mods verify originals, snapshots, targets and the game executable identity.
  Paths/types must match existing loose Xbox files. A deployment supports up to
  1,024 files, 16 MiB per file and 256 MiB in total.
- Host tests use explicit test doubles for stock Java/JNI behavior. They test
  Android views and adapter use, not execution of the native engine. Packaging
  independently verifies the real DEX interface and every preserved payload.
- Android 10/15 view/data checks, packaging regressions and lint run before
  packaging. Programmatic English labels intentionally leave localization lint
  warnings; no native/runtime API error is accepted.

Pre-package result: **44 Android UI/data tests and 8 packaging tests passed**.
Android lint reports **0 errors**, with 20 English-label/localization warnings.
Checks include tap/hold controller Start routing, tool-to-editor back-stack
configuration, keyboard/system insets, fullscreen placement, save process-death
recovery, current/runtime log export, and preservation-boundary rejection tests.

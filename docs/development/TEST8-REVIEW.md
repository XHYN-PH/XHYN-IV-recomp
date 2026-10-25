# Test8 development review — 25 October 2026

This file records development checks. Player instructions are in the main README and Android help pages.

## Scope and recovery

Source base: `a562504d95d969bdf6fa09cff952158ad6138cf6` (Test7 app plus friendly documentation).
Before-work checkpoint: `xhyn/test8-before-refinements-20261025`.
The release keeps package `com.xhyn.ivrecomp`, uses version code 36, and retains the Test6/Test7 signing certificate.

## Implemented review areas

- Separate fullscreen performance-overlay editor, movable/collapsible tools, draft/save/discard behavior, integer font-size persistence, and controller positioning.
- Compact launcher/in-game menus and confirmation dialogs; right-to-left transitions; system/Reduce motion behavior; controller navigation and focus.
- Unified report UI with in-game and launcher export; ordinary Play keeps full recording off and the next launch clears the previous full log.
- Settings explanations, inline switches, correct Film grain semantics, UI-only preference isolation, controller tuning, transition delay, and reflection presets.
- Turnip cache choices and per-profile graphics workers, bounded to one through five with one as the default.
- Mod admission/status/help, conflict information and failed-save handling.
- Supplied icon provenance, original colour/shape, circle-only outline policy, trusted asset repair and bounded native retries. All 44 bundled images decoded with the renderer's image decoder.
- Compiler pool admission/deduplication/priority/stop behavior, finite controller-axis transforms, shorter audio producer locking with optional dropout protection, texture retention within existing memory limits, and bounded initial suboptimal-surface recovery.

## Checks completed

- Fresh ARM64 Release native link using NDK r29, including the final touch mapping and Android shadow default.
- All 11 host Java policy groups passed (logs, save transactions, drivers, icon repair, gyro, session reports, placement, layout merge and mods).
- All 14 Android View regression tests passed: 13 on API 35 and the launcher/footer test on minimum API 29. Software-rendered screenshots were inspected.
- ASan/UBSan controller and compiler-policy tests passed, including the five-worker upper limit, default identity, demanded queue eviction, duplicate work, results and shutdown. Leak detection was disabled because the container does not support it.
- All 44 PNG assets passed actual native decoder checks under ASan/UBSan.
- Android lint completed. No app-owned Error/Fatal findings remain. 27 findings remain in unchanged bundled SDL: Bluetooth permissions are guarded by the HID manager (the app also disables SDL HIDAPI) and the receiver registration has an API-33 branch. These are recorded, not presented as a clean upstream lint run.
- The signed APK passed ZIP CRC/duplicate checks, manifest/version/package checks, non-debuggable status, matching update certificate, all seven driver archive/member hashes, artwork hashes, expected JNI symbols, exact staged native bytes and 16 KiB ELF/ZIP alignment.
- `git diff --check` passed. Unrelated dirty vendor submodules and temporary files are excluded from the release source change.

## Limits and deliberate decisions

No physical Android phone or controller was attached. Actual game launch, sustained frame pacing, audio behavior, heat, surface recovery and controller feel require device feedback; no measured FPS or thermal improvement is claimed. UI screenshots are Android Views rendered by Robolectric, not gameplay screenshots.

Shadow distance below 1× remains unavailable: the existing native guard explains that shorter ranges can sink nearby vehicle/character shadows. Lower shadow detail, optional sun shadows and half/quarter reflections provide supported alternatives.

The optional temporal archive remains optional. No fake cache-drive mounts or unsupported driver environment variables were introduced. More graphics workers can increase heat; the default remains one. Supplied artwork retains its separate notice and provenance.

## Signed artifact

- File: `XHYN-IV-0.5.9.3-xhyn-r2-test8.apk`
- Bytes: 69722034
- SHA-256: `eac5d59d2b02d1097ccaffe354cedc4a98f958ae95555d1a23000cd93ab4eeb1`
- Certificate SHA-256: `106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`

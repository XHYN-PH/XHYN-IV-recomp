# XHYN IV next-update work

Historical Test 1 checkpoint. The current Test 2 changes, GTA III asset
provenance, checks and remaining phone validation are documented in
[R2-TEST2-WORK.md](R2-TEST2-WORK.md).

User-approved scope: the 79-item plan from the conversation. The latest instruction
also replaces the earlier request to keep stock sticks: analog-stick visuals now
match the borderless gray button theme, with input geometry kept compatible.

This is `0.5.9.3-xhyn-r2-test1`, version code 28, on `xhyn/r2-improvements`.
It is development work based on r1 (`dc7a8d0`), not a published release.

## Scope status by approved group

| Group | Area | Current status |
| --- | --- | --- |
| 1–10 | CPU measurement, PGO, generated code, native hotspots and workers | Added a session analyzer; existing bounded workers and wait policies retained. Real PGO training, game-function samples and experimental code changes still require representative device data. |
| 11–25 | Shaders, rendering overhead, shadows, reflections, visibility and pacing | Shadow defect already fixed by the user's last update. Existing caches and pacing policies retained; additional GPU changes require isolated timings and visual comparisons. |
| 26–33 | Texture budgets/conversion, storage cache, tiers, streaming and preparation | Existing retention, converted texture cache and preparation status retained. Actual alternate texture tiers and new streaming/preparation coverage remain pending; reducing resolution is not a substitute for texture tiers. |
| 34–41 | GPU capabilities, drivers, fallbacks and sustained performance | Existing per-device admission and feature checks retained; driver proxy rebuilt. Adreno, Mali/Immortalis and Xclipse hardware coverage remains pending. |
| 42–48 | Overlays, diagnostics, reports, CI, feedback and licensing | Touch diagnostics gated; CI now checks Java/resources, native regressions and ARM64 compilation. Feedback documentation corrected and original icon provenance recorded. Full device overhead comparisons remain pending. |
| 49–54 | Achievements and cheats | Added achievement pages, notification preference and backups; original base-game cheat menu and optional local award override. Native effects, unlock behavior and advanced cheat investigation still need phone verification. |
| 55–65 | Launcher/menu presentation and motion | Rounded menus/buttons, press/entrance motion, subtle artwork drift, reduced motion, Controls/Achievements shortcuts. Existing art, branding, Play flow and two-column settings preserved. Phone layout review remains pending. |
| 66–72 | Mobile glyphs and contextual controls | Added original borderless gray glyphs and press feedback. Shared editor and contextual action routing remain compatible; simultaneous gameplay controls need phone testing. |
| 73–79 | Xbox controls and analog sticks | Borderless ABXY with colored letters, menu/view symbols, themed stick bases/grips and matching editor imagery. Existing input geometry, separate saved layouts and input-mode handling preserved. Phone testing remains pending. |

The table records partial implementation and existing mechanisms explicitly;
it does not claim all 79 items are newly implemented or device-verified.

## Shadow status

On 2026-10-22 the user confirmed that the shadow artifact was fixed in the last
update. Treat r1 as the user-verified visual baseline. Item 20 is closed on that
evidence. Do not reintroduce speculative atlas, depth-clear or shadow-distance
changes. Shadow quality/reuse investigations require comparisons against it.

## Implemented so far

- Borderless mobile, contextual and Xbox touch discs, gray glyphs and matching
  stick caps with four separated grip marks from the approved theme.
- Immediate input with short visual press feedback; optional touch animations.
- Original vector touch artwork and reproducible transparent PNG generation.
  No GTA III APK textures or generated contact-sheet pixels are redistributed.
- Xbox buttons use these glyph PNGs through the existing bounded icon cache;
  unavailable assets retain text/vector fallbacks. The preview and editor use
  the same artwork as gameplay.
- Rounded shared Android buttons/panels, press feedback, entrance transitions,
  a Controls shortcut and launcher artwork movement that stops off-screen.
- Shared reduced-menu-motion preference, also respecting Android animation disable.
- Launcher and in-game local achievement pages using runtime metadata, hidden
  achievement handling, completion count, images and unlock timestamps.
- Optional notifications while tracking stays enabled; duplicate prevention
  continues to use the existing AchievementManager.
- Achievement records/catalog/icons in save export/import; older save-only ZIPs
  preserve current achievements. Import validation precedes replacement.
- Single-player base-game cheat requests using the observed ACTIVATE_CHEAT
  registration, executed on an existing game-thread safe point after Resume.
- Optional local AWARD_ACHIEVEMENT override, disabled by default. It does not
  fabricate achievement conditions, reset cheat statistics or sync Xbox Live.
- Guest touch-state logging is gated by the explicit diagnostics policy.
- Android resources/Java checks and native host regression checks in CI.
- Android ARM64 compilation checker for the seven modified runtime surfaces.
- Testable cheat admission policy with checks for world changes, expiry,
  missing native results, non-base episodes and network sessions.
- Session analyzer distinguishes approximate GPU envelopes, window statistics,
  publisher FPS and physical scanout; it refuses to invent missing CPU samples.

## Verification and remaining requirements

Android API 35 resources and 43 Java sources compiled after the UI changes.
NDK r29 ARM64 syntax checks passed for the changed achievement manager,
KernelState, cheat hooks, JNI bridge, both touch overlays and contextual drawing. Java regressions
passed for profile backups and legacy save imports. ASan/UBSan host checks passed
for production texture conversion, input/touch behavior, contextual drawing, shaders, frame statistics,
memory/cache behavior, achievement persistence/notifications and cheat admission.
The ARM64 driver proxy and its three hook libraries built successfully. The full
fresh `LibertyRecomp`/`liberty_install` runtime build and final incremental build
completed with NDK r29, DXC 1.9.2609 and glslang 16.6.0. The initial host glslang
build lacked optimizer support; it was replaced with the official 16.6.0 host
binary and the shader archive then compiled successfully. No old native library
is repackaged as new.

Final inspection corrected the secret-achievement flag interpretation: bit 3
means show while unachieved. Regression coverage checks both secret and public
catalog entries. This interpretation was checked against Xenia Manager's
[`AchievementEntry.ShowUnachieved`](https://github.com/xenia-manager/xenia-manager/blob/main/source/XeniaManager.Files/Models/Gpd/AchievementEntry.cs).

The first signed `0.5.9.3-xhyn-r2-test1` download was defective: its ZIP end-of-
central-directory record was absent, and Android reported a package parsing
error. The previous verification statement did not hold for that deliverable.
The intact unsigned build was re-signed using the user's original release key
as `XHYN-IV-0.5.9.3-xhyn-r2-test1-fixed.apk`. The corrected file passes ZIP CRC,
Android aapt2 manifest parsing, APK signature and alignment checks, and every
non-signature entry is byte-identical to the unsigned build. Its certificate
matches r1. The packaging script now validates the complete archive and Android
manifest before atomically publishing an output and checks the final path too.
Java/resources compiled, all required icon assets and native libraries are
present, and APK integrity plus 16 KiB LOAD-segment alignment passed for all
11 ARM64 libraries. Package identity remains `com.xhyn.ivrecomp`; version code
is 28, newer than r1's 27. Installation over r1 uses the same signing identity;
actual phone installation remains to be confirmed. No public release is published. Signing credentials are not
included in the recovery checkpoint.

Phone verification is still required for menu appearance, simultaneous controls,
achievement unlocks/toasts, original cheat effects, the achievement override and
regressions. The shadow fix is confirmed by the user, not newly claimed here.

## Measurement limits

The recovered 2026-10-19 capture is an older baseline, not a result for this work.
Its measured windows range from 25.08 to 30.06 publisher FPS at a 30 FPS cap;
maximum interval is 132.37 ms. The 119 post-first-sample approximate GPU queue
envelopes have a 9.17 ms median and 11.29 ms p95. All 120 captured presentation
observations report SUBOPTIMAL, which the existing presenter already treats as a
successful presentation with bounded recovery, not a failed frame.

There are zero detailed CPU scope rows, no game-function CPU samples and no
trained PGO profile. This evidence cannot justify named native CPU replacements,
universal worker settings, shader-cost claims or predicted FPS improvements.
The older capture's 20,104 age evictions and 7,613 detected content reuploads
motivated retention work already present in r1; that work must not be added twice.

## Runtime-bound work

PGO training, new native hotspot replacements, register-localization experiments,
render-pass/resolve/state-change changes, texture tiers, visibility changes,
shadow/reflection reuse, per-device driver policy tuning and sustained-performance
comparisons remain gated by representative measurements and phone regression
testing. Existing bounded workers, arenas, descriptor/upload caches, feature
checks and thermal handling remain the implementation starting point.

## Cheat references

The native name and retail base-game indices were cross-checked against
[Native Database](https://github.com/Native-Database/Grand-Theft-Auto-IV) and
[GTA IV Native Extended](https://github.com/Lorenzo3024it/GTA-IV-Native-Extended).
Only facts/indices are used; their implementation code is not copied. Index 5
(nonfunctional money placeholder) and episode-only indices are not exposed.
The native activation and achievement override remain unverified on a phone.

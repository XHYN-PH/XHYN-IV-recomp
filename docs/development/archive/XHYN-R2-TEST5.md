# XHYN IV R2 Test 5 — private validation build

Version: 0.5.9.3-xhyn-r2-test5 (33). Build and package verification are recorded separately in build.json. No public release is authorized or published. Phone gameplay has not been verified in this environment.

## Included changes

| Refinement | Current implementation |
| --- | --- |
| Save metadata | Recognized Xbox 360 saves display their own checked completion value and package-header mission name/date. Individual ZIPs coordinate package and header; failed restores roll back both. No visible recovery menu. |
| 59, 61, 62 | Compact achievement cards (two columns where space permits), distinct navigation icons, icon Back buttons, and spaced filters with compact search. |
| 60 | IV app icon and last active FPS/frame time/app RAM on the left; options on the right. Resume, Back and Quit share the header. Diagnostics are a separate tab. |
| 63, 74 | Smaller movable editor panel; Undo/Redo/Export/Import share one row. Inspector appears after selecting a control. Gameplay safe-area calibration is reused when the aspect ratio matches. Draft opacity survives rotation. Xbox geometry remains intact. |
| 64, 67 | Smaller launcher footer gap and an installed/completion status line. Completion remains labelled last observed, with per-slot values in the save browser. |
| 71 | Actual preparation counts, duration and remaining work; empty-cache/disabled outcomes are explicit. No minimum preparation delay. |
| 73 | Safe-area overlay placement recalculates when content or screen size changes; bottom/custom positions share the same tested math with a settings preview. Adjustable edge gap. |
| Touch icon option | Optional dark outline follows each supplied bitmap glyph. The same setting is reflected in the editor. Off is the default. |
| 11, 23, 80 | Bounded session history, ordinary-play summaries, full completed benchmark records and two-run comparisons. Records include configuration, actual driver/upscaler/AA, thermal state, profiling mode and user-labelled cache condition. Window percentiles are not combined. |
| 75 | User starts a 30-second input trace after loading from the in-game Diagnostics tab; it stops on timeout/background/exit. Normal Play keeps tracing off. |
| 76 | Original automatic log cleanup is preserved: the next game launch replaces the previous logs and capture sidecars. Small timestamped last-session/Android-exit summaries overwrite themselves; history is bounded. |
| 55 | Data overlay UI lists supported extensions/examples and distinguishes admission from game compatibility. Native mount counts and rejected mounts are recorded. |
| 58, 68 | Separate hash-pinned Vauzi v4.3 candidate for admitted Adreno 6xx/7xx/8xx families, including 613/710/720/722 changes. Existing V38/imported choices remain separate. Unavailable bundled PanVK/RADV choices show the admission reason. |
| 79 | Optional pipeline wait telemetry no longer reads the clock when detailed counters are off. Existing idle-recorder scope guards remain. |

## Investigations and remaining validation

These items are not claimed as fixed by a successful compile.

| Refinement | What is established / what is pending |
| --- | --- |
| 30 | Existing context and GPS routes are retained; test context transitions and waypoint actions on the phone. |
| 49 | Existing controller/touch ownership and offline save routes pass host checks. Phone reconnect, player-slot ownership and real offline saves remain to test. |
| 50 | Existing absolute-pointer/map transforms are exercised by host tests. Verify Native/FSR, output resolutions, zoom and letterbox/cutout behavior on the device. |
| 53, 65 | A water-entry quit or collision/guest-call crash still needs an exact repeatable phone capture. A prior clean SDL quit is not proof of a native crash or its cause. Android exit metadata and bounded input captures add evidence; no speculative gameplay patch is included. |
| 66, 72 | SDL consumes the full mouse button-state mask used by the Android bridge, so that argument was not changed. Existing semantic unarmed/melee bindings and separate movement/look routes remain. Capture unarmed LMB, RMB+LMB, armed fire and movement-only camera behavior before changing game input semantics. |
| 18 | Requested Turnip environment options remain available, with env.txt precedence. A package that forces SYSMEM in source may ignore a GMEM request; effective mode has not been independently verified. |
| 43 | Existing captures distinguish warnings/failures and mark omissions. Review the next device report for real allocation/overlap failures rather than treating every warning as fatal. |
| 44–46, 48 | Vulkan capability checks and existing vertex-location remapping are retained. Host shader/location checks pass. Missing depthClamp/shaderInt64 cannot be fixed by falsely advertising them. Correct full depth-clamp emulation and real PanVK/Xclipse/16-location hardware validation remain open. |
| 57 | V38 is labelled Mesa 27.0.0-devel and its exact existing archive is preserved. Current upstream source recipes differ from its historical binary recipe. Exact reproduction and a newly tuned XHYN derivative have not been built or performance-validated. |
| 77, 78 | Existing pipeline/descriptor/dynamic/vertex/index binding caches and bounded texture-retention experiments are retained. Further changes or claimed gains require matched device captures; compare with only one option changed. |

Deferred by request: **40 direct SAF streaming and 56/42 multiplayer**. Prior exclusions **51 foldable work and 52 Android 10 driver investigation** remain excluded.

## Validate one by one

1. Install the signed APK over Test 4. Open the launcher and confirm settings, driver choice and installed game are still present.
2. Open save management. Confirm Bleed Out shows 3.49% and Bull in a China Shop shows 4.88%. Export an individual slot, restore it, and load it in the game; confirm its menu name and progress agree. The four supplied packages/headers round-trip unchanged in host tests.
3. Check launcher spacing/status, achievements (details, search and filters), cheats, and the compact in-game menu. Test Back, Resume and Quit separately.
4. Toggle Touch icon outline in Settings → Touch controls; save, inspect the editor and resume gameplay. Turn it off again. Move/resize/hide a control, undo/redo, export/import and rotate with an unsaved opacity edit.
5. Enable the performance overlay. Try each bottom preset and custom positions 0% and 100%, adjust edge gap/text/opacity, and compare the preview with gameplay.
6. Observe cold/learned preparation: the summary must describe actual work and allow play as soon as it finishes. Skip must not add a wait.
7. For input issues use launcher ⋯ → Input diagnostics, load the game, then in-game Diagnostics → Input test. Test one issue for 30 seconds. Quit and share the latest log **before launching again**, because cleanup replaces it automatically.
8. Reproduce water entry and any collision/guest-call failure separately. Record the exact save/location/action. A clean quit, Java crash, native crash and OS termination must be distinguished.
9. Check controller reconnect/ownership and offline save/load. For map precision, compare Native and FSR at the same output resolution and zoom, including corners and cutouts.
10. Benchmark one fixed route. Use Play with logs; Diagnostics → Start performance test performs a 30-second warm-up and 60-second measurement. Label cold/warm accurately (the label does not clear caches). Repeat at similar starting thermal state. Select two completed records to compare. Compare sustained/unlimited settings in separate runs; the results are publisher FPS, not displayed FPS or GPU time.
11. Try drivers one at a time, keeping the known working V38 choice available. For another GPU collect its actual name, Android version and native rejection/features report. Candidate admission is not a promise of working gameplay.
12. For data mods use an original Xbox 360 loose-data path under mods/<name>/game or update. Review the admitted/rejected list, start the game, then check the last-session mount status and actual effect.

## Verification scope

Host tests cover save transactions/header/checksum rejection, automatic log cleanup and bounded exports, session record bounds and benchmark completeness, overlay placement, production touch drawing/input, shader feature checks, vertex mapping, cache corruption/lifetime/memory limits and phone rumble/gyro policies. Android Java/resources and changed JNI/input units are compiled against API 29/35. The final APK must additionally pass native linking, exact-library/asset checks, release certificate/update identity, manifest, CRC and 16 KiB alignment checks before delivery. These checks do not replace phone gameplay validation.

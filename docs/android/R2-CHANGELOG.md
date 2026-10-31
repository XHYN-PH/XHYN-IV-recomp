# XHYN IV r2 — through Test12

**Android testing release · r2 Test12**

[**Download the Android APK**](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test12/XHYN-IV-0.5.9.3-xhyn-r2-test12.apk)

Install it over your existing XHYN IV app. Back up saves first; uninstalling removes app data. This release includes all r2 changes since r1.

**Performance still needs work. Improvements will continue through future updates. No logs = no improvements.**

## Unreleased Test14 — CRT memory and string lengths

- Fix the reproduced CRT argument-width regression that could send an oversized length to native `memset` during gameplay. All 14 affected memory/string hooks now decode Xbox lengths and capacities as 32-bit values before calling the host C library.
- Preserve genuine 64-bit kernel arguments and the Test13 crash captures. Add guarded production-hook regressions and a source check against host-sized CRT parameters.
- Passed 45 core cases / 192 assertions with ASan/UBSan, 22 audit cases, 24 Android UI tests and the full ARM64 native build. Phone validation remains pending.

See [Test14 evidence and validation](TEST14-VALIDATION.md). The published download
above remains Test12 while this fix is tested on a device.

## Unreleased Test13 diagnostic candidate

- Add an opt-in native fatal-signal capture for instruction/fault addresses, thread IDs and current module mappings. Preserve the existing signal termination path.
- Match crash excerpts to the current capture and read the actual tail of large logs. Include app/device metadata and distinguish unavailable memory samples.
- Refresh exit summaries after gameplay and before export. Include matching crash reports and available raw system tombstones in the session ZIP.

This candidate investigates the reported gameplay crash. A fix for the underlying
fault has not been identified. The published download above remains Test12.
See [diagnostic validation and capture steps](TEST13-DIAGNOSTICS.md).

## Test12 — core compatibility and title-update decoding

This release includes the previously unreleased Test11 changes below and all
earlier r2 changes. The earlier signed Test11 candidate did not contain these
core calling-convention and title-update decoding fixes.

- Preserve full 64-bit integer arguments when game code calls native services, including identifiers and size values. Keep narrow arguments and guest pointers compatible, and correct big-endian integer storage.
- Fix unaligned title-update record reads in both decoding paths. Check record payloads and output ranges, use overlap-safe record copies, and pass actual allocation bounds from the runtime loader.
- Add optional checks against a privately prepared TU8 executable. All 25,414 exception-table entries and 21,344 distinct direct-call targets have bindings; 141,135 generated direct-call annotations match the original instructions.
- Record six additional call sites in exception-handler regions and the runtime's remaining exception-handling stubs in the [core completion tracker](../development/RECOMP-COMPLETION.md).

The production core suite passes 39 cases under ASan/UBSan, the integrated
LZX/delta suite passes 19 cases, and the full Android native build passes.
The supplied update reproduces the same prepared image through both fixed
decoders. Core completion, phone gameplay and rendering validation remain open;
no FPS improvement or shadow-band fix is claimed.

## Test12 validation and known issues

- Passed 39 production core cases / 172 assertions and 19 integrated LZX/delta cases / 267 assertions with strict ASan/UBSan. Passed 20 source/binary audit regressions, 12 Android native host groups, four independently decoded PNG fixtures, 11 Java regression groups and 19 Android UI tests.
- Full Android ARM64/API 29 native game, runtime, renderer and installer builds passed with NDK r29. The package uses version code 40 and the existing release signing identity; all 11 native libraries, 16 KiB alignment, 44 touch icons and six driver archives / 24 member hashes passed verification.
- Updated Android CI setup to request the supported `platform-tools` package instead of the removed legacy `tools` package.
- **The recompilation is not complete.** Exception handling, wider service behavior, mission coverage and device validation remain open. The camera-linked straight-line shadow band remains unresolved.
- Sustained FPS, audio endurance, thermal behavior and physical-controller use still need phone testing. Host and package checks do not establish a performance gain.
- SHA-256 (`XHYN-IV-0.5.9.3-xhyn-r2-test12.apk`): `94dbb87f28247c6fa5a58c79d9114a7053989e31aed0a066eab917d8050eb463`.

## Previously unreleased Test11 — included in Test12

Test11 was shared for device testing and was not published separately on GitHub.
Its shared-runtime fixes ship together in Test12.

- Escape quotes, backslashes and control characters when saving native settings, including JSON status values and Windows paths. Both full and category-only config exports use the same serializer.
- Report malformed configuration with its line/column in the final runtime log. Startup only reports a successful load after parsing succeeds; invalid files remain untouched.
- Prioritize unseen pipeline input variants, including fixed state, vertex declaration/strides, topology and attachment metadata, within the existing lookahead budget. Normal draws continue to resolve their exact pipelines; compiler-worker counts are unchanged.
- Record the exact shader files and pipeline identity for long demanded waits, and report whether learned recipe files loaded or were rejected.
- Keep up to 12 recent and four worst recorded hitch CSVs, within the existing 16-file limit. Host-menu pauses preserve the severe-event slots.

Phone performance and rendering still require validation. These changes do not establish an FPS gain or resolve the camera-linked shadow band.

## Test10 — session stability and diagnostics

This release includes the previously unreleased Test9 refinements below, plus all earlier r2 changes.

- Exclude host-menu pause transitions from frame statistics and adaptive draw-distance/FPS-guard windows. Logged sessions now keep up to 16 rolling hitch traces with recent presents, publish/outside-publish time, pipeline waits and texture activity. A long gap is evidence to investigate, not proof of a GPU stall.
- Prioritize unseen shader pairs during bounded recorder lookahead, while keeping exact-pipeline preparation and normal draw fallback. Compiler-worker counts are unchanged.
- Distinguish empty audio queues from intentional recovery silence. Repeated underruns can rebuild a bounded 2–6-block queue (about 11–32 ms), returning to two blocks after stable playback. Failed SDL device submission now returns the consumed frame’s producer credit.
- Show sampled thermal severity, Guard Off/monitoring/active, requested GPU Turbo/backoff and draw scale in session details, Performance settings and exported context. The optional Heat overlay includes the guard state. Requested Turbo is not an achieved clock reading.
- Give extended texture reuse its own bounded idle-image allowance, shared across memory heaps. Active textures no longer consume that extra-retention allowance. Reuse and retirement return reservations; OS/Vulkan pressure and allocation recovery bypass the extension. The existing opt-in setting is retained.
- Give rejected mods specific PC-plugin, loose-data layout or episode-installer guidance, including the expected Xbox 360 path. Compatible data files still require the existing validation checks.
- Classify intentional startup `cache:`/`cache0:`/`cache1:` probes as optional diagnostics while preserving missing-device behavior. Other missing paths and broken aliases retain their error path.

Interact’s bitmap loaded on the first attempt in the supplied Test9 report. The camera-linked shadow band did not reproduce in that session and remains unconfirmed; no new shadow-rendering fix is claimed. Phone performance, audio endurance and thermal behavior still require device validation.

## Previously unreleased Test9 — included in Test10

Test9 was shared for device testing and was not published separately on GitHub. These changes ship together in Test10.

- Compacted achievement rows, dialogs and the session panel. Bounded scrolling keeps Logs and editor controls reachable.
- Made the overlay editor use the full window, including zero-gap edge placement. Added optional app CPU, readable GPU counters and actual render/output dimensions; unavailable GPU counters are labelled explicitly.
- Split touch editor tools into Control, Arrange and Layout sections, with Save pinned in the header.
- Unified controller navigation for launcher, menus and editors: D-pad/stick movement, A activation, B back, shoulder tab switching and visible focus. Added left-stick inversion and controller presets.
- Extended the layout-switch hold to gameplay and phone layouts, including contextual parachute/minigame modes. Zero uses the earliest observation; menus/loading remain immediate.
- Removed bundled V38 Patched and migrated its saved selection to Automatic. Other bundles retain their pinned hashes.
- Kept CPU profiling from changing command batching unless heavy transport capture is explicitly selected. Added a separate graphics-detail capture with a larger query budget.
- Removed periodic full draw scans for upload staging in the sparse path, retaining checked on-demand growth and fence-safe overflow retirement. Spread bounded pipeline lookahead across the frame and avoided unused texture-array preparation for textureless indexed draws.
- Passed the installed touch-icon directory explicitly to native code and included the resolved path in failure logs. Added clearer mod-folder and unsupported PC-plugin guidance.
- Added bounded shadow image captures: presenter input, available directional depth/color surfaces, camera matrices, frame IDs and submission IDs. PNGs and lossless raw copies are included intact in exported reports.
- Marked unsupported sustained-performance requests in Settings and exposed actual internal/output dimensions during play.

The camera-linked straight-line shadow band remains under investigation. No visual shadow fix or phone performance improvement is claimed from host checks. Vehicle/pedestrian shadows retain their rendering path. Audio dropout protection remains enabled by default; game-side starvation still needs a device recording with low-latency audio off.

## Test10 validation and known issues

- Passed 19 Android UI tests, 11 Java regression groups, 11 native host groups with ASan/UBSan, and 83 contextual-input runtime checks. Android resources and 76 Java sources compiled; changed native sources and the full ARM64 build passed.
- Verified the existing release signing identity, APK version 38, all 11 ARM64 libraries, 16 KiB alignment, all 44 touch icons, six driver archives and 24 driver member hashes.
- **The camera-linked straight-line shadow band remains unresolved.** It did not reproduce in the supplied Test9 session. The new capture tools collect evidence; they do not establish a rendering fix.
- Sustained FPS, audio dropouts, thermal behavior and physical-controller use still need phone testing. Host checks do not establish a performance gain.
- SHA-256 (`XHYN-IV-0.5.9.3-xhyn-r2-test10.apk`): `37b2d1f9823033aae0953c4c4ab4b10aede1a95c7b8ea836c5a82ec03d01fa2b`.

## Test8 — easier menus, controls and performance tuning

- Added a fullscreen editor for the performance overlay: drag its sample, adjust its appearance, move/collapse the tools, and save back to Settings.
- Made the in-game menu and short dialogs more compact. Session details and Copy details sit side by side.
- Added right-to-left menu opening, consistent closing motion, controller navigation, and clearer focus.
- Simplified Logs into one report page. Play with logs and Play with detailed diagnostics sit together; the sessions/benchmarks browser and manual benchmark actions were removed.
- Added inline On/Off choices and completed setting explanations, including graphics preparation time.
- Cleaned up the controls editor with tool icons, control selection, precise movement, and clearer Undo/Redo state.
- Kept the supplied mobile artwork, corrected Pause/camera mappings and game/editor tint, and added recovery for missing or damaged icon files. Xbox control geometry remains independent.
- Added physical-controller deadzones, sensitivity, response curve, inversion, trigger threshold, vibration strength, and an input preview.
- Added a configurable delay for walking/vehicle button changes. Zero keeps the earliest reliable switch.
- Reworked data-file mods with clearer cards, setup help, conflicts, rejection messages, and unsaved-change protection.
- Removed the restore-last-driver action. Advanced Turnip now includes cache layout, larger cache choices and a graphics-worker override.
- Added one to five graphics compiler workers, half/quarter reflection sizes, an optional lower display refresh request, and a Balanced settings shortcut. Extra workers can increase heat; start with one or two.
- Reduced audio producer blocking and added optional dropout protection. Lightweight graphics capture is the default; deeper captures remain available when needed.
- Improved texture-retention work, bounded recovery for an initially suboptimal Android surface, and missing-asset diagnostics. Active VSync fallback is visible beside its setting.
- Shadow distance stays at a minimum of 1×: shorter ranges can break nearby character and vehicle shadows. Use shadow detail or sun-shadow controls when reducing shadow cost.

This is a testing release. Sustained performance, graphics changes and controller behavior still need feedback from real devices. Normal Play keeps full logs off; the next game replaces the previous full log.

## Fixed in Test7

- Fixed the crash when opening the three dots beside Play. Logs also opens directly from Settings.
- Lowered the main navigation and footer to remove the excess bottom gap; the achievement card follows the footer safely.
- Removed Last session, Last rendered and Free RAM from the launcher summary.
- Made the mobile controls and editor use the full window. Xbox layout geometry stays independent.
- Moved the transparent IV logo and gameplay details above Resume in the in-game menu.
- Changed optional outlines to follow button circles; glyphs stay clean.
- Corrected missing or misleading action artwork, including Interact, weapon, focus, pause and map tabs.
- Matched the controls editor with gameplay, including colors, opacity, hidden buttons and hints.
- Added optional touch-area and overlap guides to the editor.
- Added Save, Discard and Keep editing when leaving a changed layout or advanced Turnip options, including Android system Back.
- Fixed Turnip Reset labels and consistent spacing across options and secondary menus.
- Kept settings scroll position and focus after choosing an option, with saved next-launch settings preserved.
- The pause menu waits for gameplay information instead of showing empty statistics, and shows the driver actually in use.
- Simplified menu transitions and button feedback, and removed continuous launcher background movement.
- Replaced the large preparation completion toast with a short status chip.
- Launcher problems can be reported from Logs even when Play will not start. Normal Play still has logging off, and old full logs are cleared automatically.

## Launcher and menus

- Refined the launcher layout, Play button spacing, installation status, community links and safe-area handling. More now sits beside Play.
- Added an achievement progress card that opens the achievement browser, replacing the duplicate launcher entry.
- Expanded device, Android, GPU, memory, resolution, FPS-cap, graphics and driver information.
- Added a compact in-game menu with the IV icon, session information and grouped actions. Touch and Controller settings have separate sections.
- Added restrained menu animations and a shared Reduce motion preference.
- Preserved the selected category, scroll position and return screen when opening settings, achievements, cheats or the controls editor.

## Settings and performance overlay

- Added settings categories, search, help, exact value entry, Save/Discard and category reset.
- Separated live settings from changes that require a restart. Pending changes remain saved, with a Quit now/Later prompt.
- Added FPS and Detailed displays with adjustable position, text size and opacity, plus optional memory and temperature information.
- Added a preview for the performance display and better placement when rotating the phone.
- Updated defaults for new or reset configurations: VSync Off requested, motion blur Off, FXAA, Native upscaling, SMAA Low when selected, 256 shadows, reflection AA Off, Near reflection distance, water/environment updates Off, draw distance 0.85 and converted texture disk cache On. Existing custom values are preserved.
- Corrected AA Off handling so older MSAA values do not override it. Options that do not apply to the selected AA or upscaler are inactive.
- Added film-grain control for The Lost and Damned and output-dithering control.
- Show the actual presentation mode and active AA separately from the requested settings.

## Touch, mouse and gyro controls

- Updated mobile and Xbox touch artwork, matching editor previews and shared stick styling. Fixed icon installation so the renderer uses the bundled artwork after an update.
- Added optional outlines around circular touch buttons; outlines default Off and icons stay clean.
- Added mobile hold-to-lock aiming with an adjustable hold threshold, lock indicator and reset handling around gameplay transitions.
- Added phone gyro camera modes: Off, Aim only and Gameplay, with sensitivity, smoothing and X/Y inversion.
- Improved gyro behavior when pausing, switching apps and rotating the phone.
- Added mouse movement, buttons and wheel input, with sensitivity and inversion settings. Mouse capture is released around menus and app switching.
- Added camera, focus, radar, radio, cinematic and map/menu controls through the game's existing actions.
- Added a visible, time-limited input recording option under Diagnostics.

## Controls editor

- Unified the mobile and Xbox layout editor across the launcher and in-game menu. Each layout remains independent.
- Added context previews, center and alignment guides, adjustable grids and snapping, numeric position/scale/opacity, hide/show, context reset and Undo/Redo.
- Added layout import/export. Imports preserve controls and contexts that are not included in the imported file.
- Added explicit Save, Discard and Keep editing, with full-window mobile geometry matching gameplay.

## Achievements and cheats

- Unified launcher counts and native achievement tracking, with refresh after installation and save-backup import.
- Added achievement search, All/Locked/Unlocked filters, icons, points, descriptions, unlock dates and detail views.
- Added persistent secret-detail visibility and independent unlock-notification settings.
- Added achievement catalog preparation from installed game files during installation or from the browser, without starting gameplay.
- Added searchable cheat categories and clearer request status.
- Fixed cheat requests being cancelled before they could run.
- Added health, armour, vehicle repair, money, invincibility on/off and time-of-day actions with single-player guards. Health and armour check the resulting values where available.
- Retained the option to respect achievement restrictions when cheats are used.

## Saves, game files and mods

- Added individual save-slot browsing, export, import and deletion, with backups and recovery if an operation fails.
- Improved save names and progress information where available. Deleting a save slot keeps account achievements.
- Removed the separate Recover removed/replaced slot menu action while keeping internal transaction recovery.
- Added an accessible Android/media game folder, clearer installation locations and free-space checks. Original files are kept after copying.
- Added compatible Xbox 360 data-file mods with separate switches, load order, conflict information and a record of which mods loaded last time.
- Kept the mod menu responsive while scanning or saving. A mod that fails to load leaves earlier working mods in place.
- PC DLL/ASI/EXE plugins are not supported by the data-file mod menu.

## Graphics preparation and rendering

- Added graphics preparation before gameplay, with a time limit and Skip button.
- Made preparation progress and completion messages clearer, including skipped or unfinished work.
- Improved graphics preparation and reuse of previously processed drawing information.
- Improved handling of devices with tighter graphics limits.
- Added an option to keep recently used textures longer while respecting the memory budget.
- Reduced unnecessary background work from performance diagnostics and fixed detailed GPU statistics.
- Improved display refresh-rate changes and avoided repeatedly applying the same setting.
- Improved Thermal Guard messages, cooldown behavior and session information.
- Water entry remains user-confirmed fixed on the tested device.

## Drivers

- Added the **ESMERINGHOY 27.0.0-devel (A7XX/A8XX)** experimental driver for compatible Adreno devices.
- Included six bundled driver choices: Turnip R6, Vauzi v4.3, ESMERINGHOY, PanVK and RADV Xclipse 1.6/1.8. The original V38 Patched bundle was removed in Test9; its saved selection resolves to Automatic.
- Added compatible-first ordering, clearer driver labels, per-driver options, advanced Turnip rendering/cache controls.
- Show the actual loaded driver, including system-driver fallback.
- Added imported-driver deletion with confirmation and blocked driver imports/removal while playing.
- Improved driver import error handling. An interrupted replacement keeps the previous driver, and replacing or deleting a driver clears outdated records.
- Fixed compatibility problems in driver settings and graphics startup.
- Improved experimental driver compatibility. Experimental depth clipping stays Off by default, and unsupported drivers fall back to System.

## Logs and troubleshooting

- Added optional performance recordings with frame-rate, temperature and battery information in the shareable report.
- Added more useful graphics, settings and audio information to problem reports, with clearer messages when a recording is incomplete.
- Kept normal Play free of diagnostic recording. Share/Delete actions are easier to find, and manual deletion asks for confirmation.
- **Automatic cleanup of previous full logs remains enabled. Export your report before starting another session.**
- Fixed settings cleanup and included the project license and touch-artwork credits in the APK.

## Installation

Install the APK over your existing XHYN IV installation. Export saves first; do not uninstall just to update.

You must supply your own **Xbox 360 GTA IV USA game files and Title Update 8 (0.0.8.5)**. Game files and the title update are not included.

## Reporting a problem

Open **Settings → Logs → Play with logs**, reproduce the issue, then return to the launcher and share the latest ZIP before another session. Include your phone, Android version, actual graphics driver, settings and a screenshot or video for visual issues.

[Telegram](https://t.me/xhynports) · [YouTube](https://youtube.com/@xhyn_ph)

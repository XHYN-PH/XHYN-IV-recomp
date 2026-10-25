# XHYN IV r2 — through Test6

**0.5.9.3-xhyn-r2-test6 — testing prerelease**

Download **XHYN-IV-0.5.9.3-xhyn-r2-test6.apk** below. This release includes the accumulated r2 changes since r1.

**Performance still needs work. Improvements will continue through future updates. No logs = no improvements.**

## Launcher and menus

- Refined the launcher layout, Play button spacing, installation status, community links and safe-area handling. More now sits beside Play.
- Added an achievement progress card that opens the achievement browser, replacing the duplicate launcher entry.
- Expanded device, Android, GPU, memory, resolution, FPS-cap, graphics and driver information.
- Added a compact in-game menu with the IV icon, session information and grouped actions. Touch and Controller settings have separate sections.
- Added restrained menu animations and a shared Reduce motion preference.
- Preserved the selected category, scroll position and return screen when opening settings, achievements, cheats or the controls editor.

## Settings and performance overlay

- Added compact settings categories, search, contextual help, precise numeric entry, Save/Discard and category reset.
- Separated live settings from changes that require a restart. Pending changes remain saved, with a Quit now/Later prompt.
- Added FPS and Detailed overlays with corner, centered and custom positions; adjustable text size and opacity; optional RAM and thermal information; and compact or separate-line layouts.
- Added an overlay appearance preview and safe-area updates when the display rotates.
- Updated defaults for new or reset configurations: VSync Off requested, motion blur Off, FXAA, Native upscaling, SMAA Low when selected, 256 shadows, reflection AA Off, Near reflection distance, water/environment updates Off, draw distance 0.85 and converted texture disk cache On. Existing custom values are preserved.
- Corrected AA Off handling so older MSAA values do not override it. Options that do not apply to the selected AA or upscaler are inactive.
- Added film-grain control for The Lost and Damned and output-dithering control.
- Show the actual presentation mode and active AA separately from the requested settings.

## Touch, mouse and gyro controls

- Updated mobile and Xbox touch artwork, matching editor previews and shared stick styling. Fixed icon installation so the renderer uses the bundled artwork after an update.
- Added optional icon outlines that follow the glyph silhouette; outlines default Off.
- Added mobile hold-to-lock aiming with an adjustable hold threshold, lock indicator and reset handling around gameplay transitions.
- Added phone gyro camera modes: Off, Aim only and Gameplay, with sensitivity, smoothing and X/Y inversion.
- Improved gyro orientation, sensor timing and reset handling around pause, backgrounding, focus changes and rotation.
- Forwarded Android captured mouse movement, button changes and wheel input to SDL, with sensitivity, inversion and capture release around menus and focus changes.
- Added camera, focus, radar, radio, cinematic and map/menu controls through the game's existing actions.
- Added a visible, time-limited input recording option under Diagnostics.

## Controls editor

- Unified the mobile and Xbox layout editor across the launcher and in-game menu. Each layout remains independent.
- Added context previews, safe-area and center guides, adjustable grids and snapping, numeric position/scale/opacity, hide/show, context reset and Undo/Redo.
- Added layout import/export. Imports preserve controls and contexts that are not included in the imported file.
- Added explicit Save handling and checks that reject stale viewport calibration.

## Achievements and cheats

- Unified launcher counts and native achievement tracking, with refresh after installation and save-backup import.
- Added achievement search, All/Locked/Unlocked filters, icons, points, descriptions, unlock dates and detail views.
- Added persistent secret-detail visibility and independent unlock-notification settings.
- Added achievement catalog preparation from installed game files during installation or from the browser, without starting gameplay.
- Added searchable cheat categories and clearer request status.
- Fixed cheat requests being cancelled by an unstable per-frame identity.
- Added health, armour, vehicle repair, money, invincibility on/off and time-of-day actions with single-player guards. Health and armour check the resulting values where available.
- Retained the option to respect achievement restrictions when cheats are used.

## Saves, game files and mods

- Added individual save-slot discovery, export, import and deletion, with package validation, protective backups and rollback.
- Improved recognized save metadata and last-observed completion display. Account achievements remain separate from deleting a save slot.
- Removed the separate Recover removed/replaced slot menu action while keeping internal transaction recovery.
- Added an accessible Android/media game location, verified copying with originals retained, clearer extraction destinations and storage-space checks.
- Added compatible loose Xbox-data mod overlays with independent enable/disable, priority ordering, conflict winners, validation details and last-launch mount results.
- Moved mod scanning and saving off the UI thread. A failed mod rolls back its own overlay while preserving earlier valid mods.
- PC DLL/ASI/EXE plugins are unsupported by the data-mod overlay.

## Graphics preparation and rendering

- Added bounded preparation of compatible learned pipeline recipes and available shader modules before gameplay, with a time budget and Skip.
- Added clearer ready, skipped, cancelled, budget-expired and partial preparation results, including remaining work, failures and elapsed time.
- Added bounded pipeline lookahead and cached vertex-declaration lookups.
- Added coordinated shader/input-location remapping for devices with smaller vertex-input limits, preserving the normal mapping when remapping is unnecessary.
- Added optional longer texture retention within the existing memory budget and pressure policy, plus texture-reuse diagnostics.
- Reduced inactive profiler CPU work and corrected detailed GPU-query readback handling.
- Refined display-rate requests to avoid reapplying unchanged settings and to allow seamless refresh-rate changes.
- Refined Thermal Guard status precedence, cooldown handling and visibility in session information.
- Water entry remains user-confirmed fixed on the tested device.

## Drivers

- Added the separately source-built **ESMERINGHOY 27.0.0-devel (A7XX/A8XX)** experimental driver, with its source and build information.
- Bundled seven driver archives, including separate original V38 and Vauzi packages, A710/A720/A722 candidates and an experimental RADV Xclipse driver.
- Added compatible-first ordering, clearer driver labels, per-driver options, advanced Turnip rendering/cache controls and last-working-driver recovery.
- Show the actual loaded driver, including system-driver fallback.
- Added imported-driver deletion with confirmation and blocked driver imports/removal while playing.
- Hardened imports against invalid or duplicate paths, excessive sizes and unsafe metadata. Interrupted replacement preserves the previous package, and replacement/removal clears stale history.
- Corrected Android SDK compatibility and Vulkan feature-query initialization.
- Improved the experimental shader address rewrite and shared texture-detile compatibility path. Experimental depth clipping defaults Off; device admission continues to require the renderer's needed features.

## Logs and troubleshooting

- Added benchmark captures with fixed starting conditions, setting-change tracking, frame-spike counts, thermal/battery readings and bounded session history.
- Expanded pipeline, texture, effective-settings and audio-starvation diagnostics, including overlapping or incomplete capture notices.
- Kept normal Play free of diagnostic recording. Share/Delete actions are easier to find, and manual deletion asks for confirmation.
- **Automatic cleanup of previous full logs remains enabled. Export your report before starting another session.**
- Fixed native settings cleanup and included the project license and touch-artwork attribution in the APK.

## Installation

Install the APK over your existing XHYN IV installation. Export saves first; do not uninstall just to update.

You must supply your own **Xbox 360 GTA IV USA game files and Title Update 8 (0.0.8.5)**. Game files and the title update are not included.

## Reporting a problem

Open **Settings → Logs & troubleshooting → Play with logs**, reproduce the issue, then return to the launcher and share the latest ZIP before another session. Include your phone, Android version, actual graphics driver, settings and a screenshot or video for visual issues.

[Telegram](https://t.me/xhynports) · [YouTube](https://youtube.com/@xhyn_ph)

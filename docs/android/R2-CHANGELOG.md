# XHYN IV r2 — through Test7

**Android testing release · r2 Test7**

[**Download the Android APK**](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test7/XHYN-IV-0.5.9.3-xhyn-r2-test7.apk)

Install it over your existing XHYN IV app. Back up saves first; uninstalling removes app data. This release includes all r2 changes since r1.

**Performance still needs work. Improvements will continue through future updates. No logs = no improvements.**

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
- Included seven driver choices, with separate original V38 and Vauzi packages, additional Adreno options and experimental RADV for compatible Xclipse devices.
- Added compatible-first ordering, clearer driver labels, per-driver options, advanced Turnip rendering/cache controls and last-working-driver recovery.
- Show the actual loaded driver, including system-driver fallback.
- Added imported-driver deletion with confirmation and blocked driver imports/removal while playing.
- Improved driver import error handling. An interrupted replacement keeps the previous driver, and replacing or deleting a driver clears outdated records.
- Fixed compatibility problems in driver settings and graphics startup.
- Improved experimental driver compatibility. Experimental depth clipping stays Off by default, and unsupported drivers fall back to System.

## Logs and troubleshooting

- Added optional performance recordings with frame-rate, temperature and battery information, plus recent-session history.
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

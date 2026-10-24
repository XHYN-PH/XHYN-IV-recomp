# XHYN IV R2

Changes since R1. R2 is awaiting phone validation before a public release.

## Launcher, menus and settings

- Compact launcher actions, a horizontal overflow button beside Play, footer spacing and one achievement entry through the right-hand card.
- Expanded configured graphics, GPU, Android, driver and memory information. Session diagnostics distinguish requested graphics options from the renderer's actual AA and presentation mode, including FIFO fallback.
- Branded pause panel with separate touch/controller sections, a single-line performance summary, details in Diagnostics and horizontal entrance/exit motion. Reduced motion and navigation state are retained.
- Slimmer settings rows, search, contextual explanations, precise numeric entry, Save/Discard and category reset. Restart-only changes are queued independently of the running game's configuration, with Quit now/Later and a pending indicator.
- Recommended defaults for new/reset configurations: VSync Off requested, motion blur Off, FXAA, Native upscaling, SMAA Low when selected, 256 shadows, reflection AA Off, Near reflection distance, water/environment updates Off, draw distance 0.85 and converted texture disk cache On. Existing custom settings are preserved.
- Film grain control for The Lost and Damned and output dithering control.
- FPS/Detailed overlay with corners, centered positions or custom placement, text size, opacity, optional memory/thermal metrics and a compact layout.

## Input and touch editing

- Android captured mouse motion, button changes and wheel events are forwarded to SDL. Capture is released around menus, focus loss and backgrounding, with sensitivity/inversion settings and bounded input tracing.
- Shared mobile/Xbox layout editor from the launcher and game, with context previews, safe-area/grid guides, adjustable snapping, numeric position/scale/opacity, hide/show, undo/redo, context reset and profile import/export.
- Additional camera, focus, radio, radar and map/menu controls where the game's existing actions permit them. Mobile and Xbox saved layouts remain independent.
- Approved touch artwork, consistent stick styling and transparent camera-related glyphs. Touch icon synchronization refreshes assets on app updates.
- Optional phone gyro camera with aim-only/gameplay modes, smoothing, sensitivity and inversion. Samples are bounded, expire and reset around inactive states.

## Achievements, saves and game files

- Launcher and native tracking use the same achievement directory. Locked/unlocked filters, secret-detail preference, icons, descriptions and unlock dates share the native catalog.
- Installed-title achievement metadata can be prepared during installation or from the browser without starting gameplay. Existing unlock records are retained.
- Individual save-package discovery, backup, restore and deletion with recovery copies. Account achievements remain separate from slot deletion; full-backup import validates and stages data before replacement.
- Last observed in-game completion can be shown when the registered game statistic is available. It is labeled historical and is not tied to a verified save slot yet.
- An accessible app-owned Android/media game location with verified copying and retained originals. Selected SAF folders are imported into a direct native path.
- Optional read-only overlays for compatible loose Xbox game data. PC plugin, executable, script and archive mods are not supported by this overlay.
- Searchable cheat categories and additional guarded single-player actions. Handler submission is distinguished from a verified effect.

## Rendering, drivers and diagnostics

- Bounded preparation of compatible learned pipeline recipes and available shader modules before gameplay, with known-work status, a time budget and Skip. Unseen rendering states can still compile during play.
- Cached vertex-declaration location lookup and coordinated shader/declaration input-location compaction for devices with smaller limits. Output locations and the normal Adreno mapping are preserved when remapping is unnecessary.
- Optional longer texture retention within the existing budget and pressure policy, alongside reuse/eviction diagnostics. No blanket memory-budget increase.
- Exact, separately named Turnip Gen8 V38 Patched bundle with pinned provenance, plus an experimental Android RADV 1.8 Xclipse bundle. Existing driver choices and fallback options remain available.
- Advanced Turnip rendering/cache options, applied before driver creation, with explicit restart behavior and reset.
- Experimental ordinary depth-clipping fallback when depthClamp is absent, and more detailed GPU feature reporting. Missing shaderInt64 and unsupported user clip-plane paths still prevent full compatibility; Mali/Xclipse support requires device testing.
- Benchmark, pipeline, texture, effective-settings and audio-starvation diagnostics, with opt-in heavy capture and clear publisher-FPS/timing limits.
- Portable Linux host-tool/build handling, registry validation and automated Java, resource, native and package checks. ARMv8-A baseline and original signing identity are retained.

## Validation limits

This build has passed host checks and APK verification. Actual installation, controls, achievement preparation, saves, visual correctness, sustained heat/audio and driver behavior still need phone testing. No new FPS gain is claimed. Multiplayer, a reproducible V38-derived custom driver, full missing-feature shader emulation and verified per-slot completion remain research work. Foldable work and Android 10 custom-driver investigation were excluded from this scope.

# XHYN IV 0.5.9.3-xhyn-r2-test6

Testing pre-release for Android, package `com.xhyn.ivrecomp`, version code **34**. This update includes the accumulated r2 work since the public r1 release.

**Performance still needs work. Improvements will continue through future updates. No logs = no improvements.** Build checks passed; phone stability, visuals and sustained performance still need testing.

## Launcher, menus and information

- Added achievement and cheat browsers with compact search, spaced category filters, details and wider-screen achievement cards. Achievement catalog recovery is available when catalog data is missing.
- Refined the launcher spacing, status information and common footer. The achievement card opens the achievement browser; More sits beside Play.
- Added the Compact in-game menu with the IV icon and performance/phone details on the left, grouped actions on the right, and separate Touch and Controller settings.
- Preserved category and scroll position when returning from child screens. Added restrained animations with reduced-motion handling.
- Added a configurable performance HUD with position presets, custom placement, appearance/metric options and a matching preview.
- Separated pending restart settings from live changes and added a quit-now/later prompt after saving settings that require restarting.

## Controls and artwork

- Updated the mobile/Xbox touch artwork and matching editor previews. Optional outlines follow the icon silhouette and default Off.
- Added phone gyro controls with orientation/timing handling, and mobile aim-hold behavior.
- Improved the shared layout editor with import/export, Undo/Redo, guides, snapping and context-aware previews. Imports merge supplied entries while preserving other controls and contexts; explicit Save applies the draft.
- Added viewport/settings identity and freshness checks to reject stale editor calibration.
- Added a visible Diagnostics input capture that stops after 30 seconds, backgrounding or game exit. Input arriving at the native bridge does not certify every in-game action.

## Saves, installation and data mods

- Added individual save-slot export/import/delete with coordinated package headers, protective backups, validation and rollback. Verified save metadata supplies display names and last-observed completion where the format is recognized.
- Removed the separate Recover removed/replaced slot menu action. Protective transaction recovery remains internal.
- Added independent loose-data mod enablement, priority, conflict winners, validation/rejection details and last-launch mount results. A failed mod rolls back its game/update overlay without removing earlier valid mods.
- Moved mod scanning/saving away from the UI thread and updated placement instructions. PC DLL/ASI/EXE plugins are unsupported; a successful mount does not prove a mod is compatible.
- Clarified extraction destinations and the one-time folder-copy workflow, with space checks, hashes and grouped publish/rollback handling. Direct SAF streaming remains deferred.

## Drivers and compatibility

- Added the separately source-built **ESMERINGHOY 27.0.0-devel (A7XX/A8XX)** candidate with source, patch, toolchain pins and upstream credits. Original V38 and Vauzi packages remain separate. Exact original V38 binary reproduction and faster gameplay are not established.
- Bundled seven driver archives, including the additional V38, A710/A720/A722 and RADV Xclipse candidates. Admission depends on the actual device, Android version and required features.
- Added compatible-first ordering, per-driver options, actual loaded-driver observations and last-working recovery. System fallback is recorded as the actual driver.
- Hardened imports against duplicate/invalid paths, excessive sizes and unsafe metadata. Replacement rollback/interruption recovery preserves the earlier package, and stale history is invalidated after replacement/removal. Driver imports are blocked while playing.
- Corrected Android SDK compatibility in driver settings and Vulkan feature-query initialization in the proxy build.
- Fixed result-type/literal parsing and SPIR-V 1.3 pair selection in the experimental address rewrite; routed texture-detile compute through the shared compatibility path.
- Kept **shaderInt64 required** for automatic admission: the research rewrite does not cover the complete title cache. Ordinary depth clipping defaults Off and is not equivalent to depth clamping.

## Preparation, performance and diagnostics

- Added distinct ready/skipped/cancelled/budget-expired/partial preparation outcomes, remaining queues, failures and elapsed time.
- Refined existing bounded renderer/texture-cache experiments and skipped inactive profiler CPU recording. Detailed GPU readback clears only submitted, valid query slots.
- Added frozen benchmark start conditions, setting-change tracking, frame-spike counts, capture overlap/omission notices, thermal/battery readings and bounded session history. These tools do not establish an FPS improvement.
- Kept normal Play free of diagnostic recording. Manual deletion asks for confirmation, and sharing reflects report availability.
- **Automatic cleanup of previous full logs remains enabled. Export the report before starting another session.** Small session/exit summaries remain bounded.
- Fixed deletion through the native profile-setting base type and rebuilt the native libraries together.
- Included project COPYING and touch-artwork attribution in the APK.

## Checks and known limits

The candidate passed 11 Java regression groups, 10 native host groups with ASan/UBSan, Android UI/resource compilation for 70 sources, 12 ARM64 syntax units, the full native Release build and the driver-proxy build. Nine focused address-rewrite checks passed. Of 2,062 real early/late shader variants, 1,619 accepted outputs validated and 443 unsupported variants were rejected; no invalid accepted output was found.

APK verification checked the existing update certificate, package/version, all 11 exact ARM64 libraries, required JNI functions, 43 touch PNGs, all seven pinned driver archives and 16 KiB ELF/ZIP alignment. Leak coverage remains unverified because LeakSanitizer cannot access required process information in this environment.

Open work includes shaderInt64-free rendering, equivalent depth-clamp/clip-plane support, Mali/Xclipse phone acceptance, mouse unarmed targeting/attacks and camera follow, controller ownership/reconnect, contextual/GPS/map actions, complete save episode/autosave/loaded-slot identity, collision/guest-call investigations and measured renderer/texture/sustained-performance comparisons. Water entry remains user-confirmed fixed on the tested device. Multiplayer remains deferred.

Imported es2 touch artwork retains its attribution; no creator/redistribution license was supplied with that import. It is not claimed as original GPL artwork. See [artwork notices](ARTWORK.md) and [touch attribution](../../os/android/app/src/main/assets/TOUCH-NOTICE.txt).

## Installation and reporting

Install over the existing XHYN IV app using the same signing identity. Export saves first; do not uninstall just to update. Supply your own **Xbox 360 GTA IV USA game files and Title Update 8 (0.0.8.5)**. No game files or title update are provided.

Follow the [phone checklist](R2-TEST6-DEVICE-CHECKS.md) one section at a time. For a problem, use **Settings → Logs & troubleshooting → Play with logs**, reproduce it, return to the launcher and share the latest ZIP before starting another session. Include the phone, Android, actual driver, settings and a screenshot/video for visual issues.

See [verification details](R2-TEST6-VERIFICATION.md), [build identity](R2-TEST6-BUILD.json), [build instructions](BUILDING.md), [driver sources/credits](../../os/android/native/DRIVERS.md) and [mod instructions](../MOD_SUPPORT.md).

APK SHA-256: `0507caf127222bb5457d69b2b6b2545a044d14a35e2774eb43585cca86292e35`.

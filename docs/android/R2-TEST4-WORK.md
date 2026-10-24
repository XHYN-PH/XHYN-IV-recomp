# XHYN IV R2 Test 4: implementation and validation

Built 23 October 2026. `0.5.9.3-xhyn-r2-test4`, code 32, package `com.xhyn.ivrecomp`. The APK was rebuilt from the native sources and signed with the original release key. No repository push or public release was performed.

The public changelog is [R2-CHANGELOG.md](R2-CHANGELOG.md), describing the combined change since R1. This report follows the stable 54-item review. Items 51 and 52 were excluded by the user. “Implemented” below describes code in this candidate, not hardware certification. Open research and partial results are recorded explicitly.

## Scope status

| # | Requested area | Current result and limit |
| --- | --- | --- |
| 1 | Achievement synchronization | Implemented: launcher/browser and native tracking use `LibertyRecomp/saves/achievements`. Return, install and backup-import refreshes use that catalog. Persisted unlocks remain authoritative. Existing 1/65 and a new award need phone confirmation. |
| 2 | Complete/compact browsing | Implemented: locked/unlocked/all, compact search, details/icons/dates and saved secret visibility. Installation/on-demand tool-mode metadata loading uses the actual installed XEX without guest execution or a GPU. This path compiled and its JNI is packaged; a fresh-install phone run is still required. |
| 3 | Physical mouse | Implemented Android captured relative motion, button transitions/history and wheel forwarding to SDL, plus focus/menu capture reset and sensitivity/inversion. Existing native routing remains. USB/BT action correctness is unverified. |
| 4 | Individual saves | Implemented package discovery, time/size/path display, bounded slot ZIP backup/restore/delete and local recovery. Host tests prove two-slot isolation and achievement preservation. Episode/slot names are not decoded from unverified save contents; active-game mutation is blocked. |
| 5 | Real completion | Partial: optional registered GET_PROGRESS_PERCENTAGE/GET_CURRENT_EPISODE read on the game thread, finite-range checks and historical snapshot. Unavailable when handlers are absent. Not yet compared with Statistics or tied to a verified slot; launcher explicitly says Last observed. |
| 6 | Known pipeline preparation | Implemented: learned compatible recipes pre-register available vertex/pixel modules and use the existing replay/demand queue. Bounded startup preparation does not cover unseen states or guarantee hitch-free play. |
| 7 | Preparation progress | Implemented real known-work status, configurable time budget and Skip/background cancellation. Driver/settings identity remains part of existing cache policy. Cold/warm and interrupted-start tests require the phone. |
| 8 | Renderer CPU overhead | Narrow changes: cached declaration semantic/location lookup and touch opacity caching; existing lookahead/immutable-state work retained. No unprofiled speedup or broad hotspot rewrite is claimed. Further optimization needs representative captures. |
| 9 | Texture reuse | Optional longer retention only below the existing soft limit and without pressure, capped at 3600 frames. Default Off; budgets are unchanged. Existing conversion/cache sanitizer tests pass. Out-and-back route/PSS/reupload measurements remain. |
| 10 | Sustained performance | Existing Android sustained mode and optional Thermal Guard/hysteresis retained and exposed consistently; no forced clocks/default Turbo. Thermal quality tradeoff is described. Comparable 15–20 minute phone measurements remain. |
| 11 | Fair comparisons | Partial: existing warm-up/60-second benchmark, effective-setting snapshots, driver reports, thermal diagnostics and source analyzer are retained. A new compact in-app comparison/results screen and matched V38/custom-A750 measurements are not completed. Do not combine window percentiles into a session percentile. |
| 12 | Defaults | Implemented centralized missing-key defaults and category Reset values matching the requested table. Upgrades preserve custom values. Java checks cover preservation and pending launch merges. The renderer can still select FIFO when Immediate is unavailable. |
| 13 | Truthful Off controls | Implemented AA Off policy, inactive SMAA quality for FXAA/Off, inactive FSR controls for Native and accurate reflection-update Off help. Zero update intervals skip capture draws while retaining defining clears/resolves; this is not elimination of every reflection allocation/pass. |
| 14 | Actual presentation | Existing effective swapchain mode remains visible in session/settings diagnostics and overlay. Requested Off is distinct from FIFO fallback. Device pause/resume pacing still needs testing. |
| 15 | Exact V38 bundle | Implemented unchanged, hash-pinned upstream ZIP as `turnip_v38`, separately labeled from the original bundled Turnip. Existing imported selection is preserved. The reviewed log's V38 label alone does not establish an exact binary match. |
| 16 | Tuned Turnip derivative | Research open. V38 source/patch identity is not reproduced to binary equivalence. The separately saved Mesa 26.2.5 A750 candidate remains distinct; no invented V38 derivative or measured gain is claimed. |
| 17 | Other GPU families | Partial: explicit family/API admission and detailed feature reporting, preserved system/import options, plus experimental compatibility work below. No actual A710/Mali/Xclipse hardware was available here. |
| 18 | Turnip options | Implemented separate rendering policy and Mesa disk-cache enable/size settings with reset and next-launch application. Adreno-only environment application; `env.txt` retains final override. Driver-revision behavior still requires actual driver testing. |
| 19 | Driver management | Import at the top, compact rows, pinned labels/provenance, imported-driver delete confirmation and existing recovery/reporting retained. Selected configuration and actual runtime report are distinct; full fallback scenarios need a phone. |
| 20 | Launcher balance | Implemented footer spacing, safe-area layout and Play/status gap within responsive/scrolling layout. Large fonts/cutouts need visual review. |
| 21 | One achievement entry | Implemented right-hand card entry and canonical count; duplicate left action removed. Focus/count need phone confirmation. |
| 22 | More beside Play | Implemented horizontal overflow button and accessibility label, retaining log/secondary actions. |
| 23 | Launcher details | Partial: configured resolution/cap/AA/upscaler/driver, GPU, Android, free RAM, cache availability, pending state and historical completion. Actual active resolution/driver/presentation remain in session diagnostics; a full persisted last-session performance/SoC summary is not completed. |
| 24 | Menu motion | Implemented restrained shared press/entrance transitions, pause window entrance/exit and reduced-motion policy. Sampling/animation stops around inactive states; phone visual QA remains. |
| 25 | Settings density | Implemented compact rows/categories/search/help, drafts, numeric controls, restart distinction and navigation state. Legacy phone-performance entry now opens the same settings implementation to prevent divergent saved values. |
| 26 | Log actions | Implemented equal-width Share/Delete actions on one row. |
| 27 | Overlay positions | Implemented corners, top/bottom center and custom normalized X/Y, recalculated for safe-area/layout changes. |
| 28 | Overlay appearance | Implemented 9–24 sp text, opacity, optional RAM/thermal and compact/separate-line mode. It remains noninteractive and samples only when active. |
| 29 | Approved icons | Implemented transparent camera/video outline vectors/PNGs and provenance; other approved assets/theme retained. Version-stamped asset synchronization forces an update. APK checks compare all 43 PNGs with source. |
| 30 | Context controls | Added camera/focus/radar/radio/cinematic and map/menu actions using existing Xbox actions, with generated context defaults. Not a newly verified one-button GPS route setter; activity/vehicle coverage still needs in-game checks. |
| 31 | Touch/controller options | Implemented separate sections and independent mobile/Xbox editing paths/layout files. |
| 32 | Shared editor | Implemented one Java editor from launcher and game for mobile and Xbox layouts. Native edit callbacks open that editor. |
| 33 | Editing tools | Implemented grid sizes/snap toggle, safe area and center guides, numeric offsets/scale/opacity, hide/show, 50-step undo/redo, context reset and bounded import/export. New opacity field reads old layout records compatibly. Device drag/snapping/import coverage remains. |
| 34 | Menu branding | Implemented packaged XHYN logo, compact right panel/header, action rail and separate close/quit affordances. |
| 35 | Horizontal panel motion | Implemented window entrance from the right and reverse exit, respecting Reduce motion. Window animation preserves immediate dialog dismissal/pause callbacks. |
| 36 | One-line statistics | Implemented compact last-active FPS/frame-time/RAM/free summary; no inserted newline. Large text can still truncate on small screens and needs visual review. |
| 37 | Diagnostics details | Implemented Session details/Copy only under Diagnostics. |
| 38 | Return navigation | Retained category/scroll state and parent-menu return across settings, achievements, cheats and editor. Real Android pause/back ownership needs regression testing. |
| 39 | Cheats | Implemented categories/search and clearer existing guards/status. Existing 25 actions are retained; no effect or spawn is certified solely from queue dispatch. |
| 40 | Accessible game files | Implemented app-owned Android/media selection, per-file SHA-256 verified migration with original retained, and validated SAF folder import. Native streaming uses real paths; arbitrary SAF URI streaming is not implemented. Saves stay in the original save root. |
| 41 | Data-file mods | Implemented optional read-only VFS overlay union for permitted loose-data extensions, deterministic ordering, bounds and path/symlink checks. Base files remain unchanged. It does not load PC FusionFix/ASI/DLL/script/archive mods. Needs a real compatible Xbox-data example on the phone. |
| 42 | Android multiplayer | Research documented: Android currently omits the authenticated relay implementation and exposes no working backend. Enabling the upstream client needs transport/dependency/service/auth integration and two-device tests. No multiplayer support claim or dead toggle was added. |
| 43 | Diagnostic classification | Partial: existing opt-in/bounded captures, optional-DLC/online classification and audio-policy context remain; feature/preparation reports improved. Cache/cache1 probes still need a validated mount/behavior decision, and a new unified profiler-overlap summary is not completed. Errors are not globally suppressed. |
| 44 | Missing depthClamp | Experimental implementation: preflight no longer requires the feature; rasterization keeps depthClampEnable false when unsupported and uses ordinary depth clipping. This is not full depth-clamp emulation and can alter near-plane/shadow rendering. Toggle/documentation retained for comparison. |
| 45 | Missing shader features | Partial: exact depthClamp/clip-distance/sample-rate reporting, preserved shaderInt64/sample-rate/independent-blend gates and validated vertex remapping. Full shaderInt64 emulation and correct fragment clipping for unsupported guest clip planes are open. |
| 46 | Xclipse adaptation | Partial: existing conditional BC1/2/3 CPU decode is preserved, with direct compressed Adreno uploads retained. Exact Android RADV 1.8 bundle added with API/device admission. pt4droid's fragment clipping is not transplanted or claimed equivalent. Actual Xclipse validation remains. |
| 47 | Restart prompt | Implemented independent next-launch file, mixed live/restart saving, pending marker and Quit now/Later with unsaved-progress notice. Host tests cover pending values surviving current-config writes and entering next-launch arguments. Normal SDL/activity teardown is used. |
| 48 | Vertex input limits | Implemented per-shader compact input decoration plus matching Vulkan declaration mapping only when original locations exceed the actual limit. Outputs stay unchanged; module hash changes with rewritten code. Synthetic and compiled GLSL 17/32-location tests pass sanitizers/spirv-val. A real 16-location GPU and cutscene/override tests remain. |
| 49 | Controller/save identity | Audit/reproduction open: Android assigns physical devices to user slots, while virtual touch is tied to player one; existing merged-input behavior remains. No confirmed fork-specific ownership bug was established or speculative profile remapping added. Multi-controller/headset/controller-order tests are required. |
| 50 | Map/waypoint precision | Audit/reproduction open: physical safe-area and native input paths were inspected; added map/menu controls do not prove coordinate accuracy across render/output scaling. The same-landmark Native/FSR/zoom comparison is still required. |
| 51 | Foldables | Excluded by user. |
| 52 | Android 10 driver investigation | Excluded by user; existing C11 compatibility shim retained. |
| 53 | Collision/guest-call crashes | Research open: supplied session has no matching fatal fault. No unverified no-op/crash suppression was added. Requires matching symbolized report, build/game-data identity and route/mission reproduction. |
| 54 | Persistence/clean builds | Implemented pending/current settings separation, next-launch argument merge, unified settings entry and upgrade-default tests. Fixed portable SDK/JDK/NDK host selection and missing registry checker; all pinned dependencies initialized and native build completed. Shell/tool setup checks and 11 dependency setup tests pass. No clean Android device install is claimed. |

## Driver provenance

| Bundle | Upstream package | SHA-256 | Limits |
| --- | --- | --- | --- |
| Turnip Gen8 V38 Patched | StevenMXZ/Adreno-Tools-Drivers, v27.0.0-R1, `Turnip_Gen8_V38_Patched.zip` | `2bbb279a3da7680503a54a51a3051c7a4302c8fb2a089f8b183b4b5d6ba9ac17` | Offered experimentally for admitted Adreno families, selected explicitly. Upstream Gen8 advertising and one A750 run do not certify A710 or all listed families. |
| RADV Xclipse 1.8 | JimVulkan/radv-xclipse, v1.8.0, Android `radv-xclipse-27.0.0-devel-269238e.zip` | `0545b1b24817cc0ed4a9e4fc440a7080cbaf69a9ce5d1e4bb22b0e697129c223` | Android 14/API 34+ and admitted Xclipse models; experimental for this game. The Wayland package was not substituted. |

Both ZIPs are unchanged upstream bytes. Their embedded ARM64 driver libraries have 16 KiB PT_LOAD alignment; that does not certify their gameplay behavior. Exact per-file hashes, original notices, source URLs and Mesa licensing are retained with the assets. Build-script licensing is not misrepresented as the license of every compiled driver component.

## Build and checks

- Fresh native runtime, game, installer and driver proxy builds completed with NDK r29, CMake 3.31.6, DXC 1.9.2609, glslang 16.6.0, SDK/build-tools 35 and JDK 17. Generic ARMv8-A/outlined atomics retained. No trained PGO claim.
- All 57 Java/resource source files compile against API 35. Six Java fixture groups pass: settings/backups, save-slot isolation/recovery/rejection and pending merges, logs, driver admission, icon repair and gyro math.
- Eight production host regression groups pass under ASan/UBSan: texture conversion, touch/input, context drawing, vertex locations, shaders, frame/cache/memory, achievements and launch/cheat admission. Leak detection is disabled because the host tracer environment does not support that check.
- Nine ARM64 syntax checks pass; the full native link additionally covers the metadata-preparation JNI and renderer/VFS changes. Actual compiled high-location GLSL input modules were rewritten and accepted by spirv-val with output locations preserved.
- Eleven dependency setup tests pass. Registry structure validates 105 unique records; this is a structural check, not proof of exhaustive semantic CVar coverage. Portable shell syntax/host-tool invocation checks pass.
- Final APK passes complete ZIP CRC, Android manifest parsing, release signature, original certificate, package/code, dependency checks, all 11 exact staged ARM64 libraries, 16 KiB ELF/ZIP alignment, 43 exact touch PNGs, driver hashes, native JNI exports and absence of fixture classes.

The APK is 64,232,854 bytes. SHA-256: `4b15cdb62bee5bb1f17d3ec399fa98b2ac85fecf4c23427bd529f1a384c203ec`.

Signer SHA-256: `106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`.

## Next evidence needed

Use [R2-TEST4-DEVICE-CHECKS.md](R2-TEST4-DEVICE-CHECKS.md), starting with the existing achievement count, captured mouse actions, mixed restart settings and save-slot round trip on the Adreno 750 phone. Check new metadata preparation before gameplay. Then compare cold/warm preparation and sustained performance under controlled conditions. Mali, Xclipse and A710 claims require those actual GPUs.

There is no measured FPS result for this candidate. The earlier 29.58 publisher-FPS benchmark belongs to the previously supplied phone capture, with possible detailed-profiler overlap near its end; it must not be relabeled as this build's result. TV and the previously confirmed shadow fix are regression checks, not reopened defects.

## Recovery

The checkpoint and source upload kit carry a complete binary-capable patch against R1 `dc7a8d02a272ead6f2ee3b99433d7df599c0f6df`, current changed source/assets, exact dependency/build metadata, this report and verification logs. Apply the patch once to that base and initialize dependencies with `tools/setup_repo.py`. The kit excludes signing credentials, game data, user saves and toolchain/build intermediates. Keep the original release key separately for compatible future update signing.

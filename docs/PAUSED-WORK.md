# XHYN IV historical implementation checkpoint

The pause/build restrictions below are historical. On 24 October the user authorized tests, a signed test release and publication after fixing discovered errors. See docs/android/R2-TEST6-VERIFICATION.md for current validation and limitations. Direct SAF streaming (#40) remains deferred.

Paused at the user's request on 24 October 2026. Resume the existing implementation, not migration work. Do not build an app release/APK, push or publish. The user wants the selected implementation finished before testing. New work in this checkpoint is untested and may contain errors; nothing here establishes error-free gameplay.

## Preserve these decisions

- All originally selected 1–80 items remain in scope, including #40. #42/#56 multiplayer comes later; #51/#52 stay excluded.
- The water-entry issue (#65) is user-confirmed fixed on their device. Do not invent another water patch or treat it as proof that collision/guest-call failures (#53) are fixed.
- Exact driver family: ESMERINGHOY 27.0.0-devel (A7XX/A8XX). Broader GPU work includes Adreno 6xx as well as 7xx/8xx through independent candidates. Do not rename an upstream binary or remove original credits.
- Outline follows the supplied icon silhouette, is optional/default Off, and appears in the editor.
- Keep automatic startup cleanup of previous full logs. Bounded small session/exit summaries survive; do not retain five full sessions.
- Compact menu: performance/phone information left, options right.
- Migration was explicitly cancelled. This is a pause/recovery checkpoint only.

## Baseline and first completed batch

Working branch xhyn/first-batch-fixes, repository XHYN-PH/XHYN-IV-recomp. Base R1 dc7a8d02a272ead6f2ee3b99433d7df599c0f6df. Exact recovered Test5 tree 426cd3ed36bba800918c7472e955ed5ca8e7ad88, local commit 026dd5106f135b3310b47686568aa7ed38c90148. Original Test5 commit identity was 57fa3f575032c918b2d2c9e4b8e3b6218e8b7d56; its tree was recovered exactly.

First fix commit ec8faeeec1a5c4c7dcb8aa7e4a2bf8559a9cfa37: virtual profile-setting destructor, deep-copy layout import merge, per-mod isolation/enabled/priority/conflicts/read-only transactional overlays and matching docs. Before the latest all-first/test-later instruction, ten Java fixture groups, ten native host groups, Android UI/resources and changed ARM64 syntax checks passed. LeakSanitizer cannot inspect /proc task information here; the local host run used XHYN_HOST_DETECT_LEAKS=0. No leak-coverage claim. Logs are in the recovery archive.

## New implementation saved at pause — NOT TESTED

- Preparation now exposes ready/skipped/cancelled/budget-expired/partial outcomes, shader and queue/publication/replay counts, failures and elapsed time. Java summary includes all remaining work.
- Manual log delete confirms first and sharing reflects availability; automatic old-log cleanup is intact.
- Overlay sample obeys RAM/thermal/AA visibility.
- Per-driver options, compatible-first ordering, actual-load observations, and device-keyed last-published-frame recovery. Native selected-device features/limits are recorded separately from the loader probe.
- Benchmarks freeze native/UI start snapshots, monitor CVar changes and reversions through registry callbacks, retain over-50ms counts, capture overlap/omissions, labels, thermal/battery readings, background/change times and rotation-independent result sidecars.
- Editor calibration has generation, capture token, settings/display identity and one-hour freshness; stable native generation is checked around capture. Guides, snap strength, preference/state restoration and no snapping on ACTION_CANCEL were added.
- Automatic missing achievement catalog fallback when opening the browser; storage completion refreshes metadata.
- Game location UI documents extraction destination and one-time SAF local-copy behavior. Space reserve, per-file hashing/fsync, paired game/DLC publishing and rollback retain originals and recovery copies.
- ESMERINGHOY was actually compiled and linked from Mesa f28352d1179a4a85b4be286a883747bae2d3786b, NDK r29, API29. Source/patch/toolchain lock and own build.py are in drivers/esmeringhoy. Original V38/Vauzi archives unchanged. Candidate ZIP SHA256 29768adfaaa6fa7ee56cdbc11edd52aba0cb695360ca0fcf99dc751cdbeb0163. It has not run on a phone.
- Address-only uint64 SPIR-V lowering added for BDA devices without shaderInt64: explicit carry/borrow, pair shifts, multiplication/comparison and pointer bitcasts. Signed64/vectors/atomics/specialization arithmetic and other unreviewed operations reject the module. This substantial new transform is untested and needs careful review, SPIR-V validation and actual-cache coverage before any delivery.
- Ordinary depth clipping experiment now defaults Off and docs identify it as non-equivalent. Equivalent depthClamp/fragment clip fallback remains unfinished.
- Inactive profiler CPU phase/context recording is skipped. Detailed query readback clears only submitted query slots, with bounds rejection.

## Resume here

1. Inspect SELECTED-WORK.md and the saved source; do not recreate these changes. No new tests ran in this implementation phase.
2. Review the new address rewrite for legal SPIR-V, arithmetic edge cases, unsupported-operation handling and integration with stock, late, override, compute and UI modules. It currently admits missing Int64 in preflight; do not deliver that claim until real cache modules pass later validation.
3. Finish genuine depthClamp/clip-plane equivalence or keep it explicitly unsupported; do not claim ordinary clipping is equivalent. Native fallback currently omits title clip planes on missing shaderClipDistance.
4. Finish save-slot episode/autosave classification and a verified loaded-slot association. Do not infer the loaded slot from completion or the latest file alone. SaveMetadata only recognizes the supplied base-game layout. A primary-source save-format repository was cloned at pause for this research, but not yet analysed.
5. Complete capture-backed renderer CPU/binding and selective texture retention refinements. Current caches exist; the requested additional measured improvements and matched sustained/uncapped comparisons remain unproven.
6. Unarmed mouse attack/target/camera (#3/#66/#72), controller/offline identity (#49), map/cutout accuracy (#50), contextual controls/GPS (#30), and collision/guest crashes (#53) still need the relevant source diagnosis/device evidence. Do not suppress failing guest calls or declare the unknown cause fixed.
7. Complete the clean-clone/settings audit and diagnostics resource/audio/cache warning classification.
8. After selected implementation is complete, run appropriate checks and device acceptance. An app release remains held until the user asks for it again.

## Toolchain and driver reconstruction facts

NDK r29 Linux archive SHA1 87e2bb7e9be5d6a1c6cdf5ec40dd4e0c6d07c30b, previously restored at toolchain/android-ndk-r29. API35 android.jar and aapt2 under toolchain. Java17 compiler module and g++ are available. Re-download reproducible toolchain/dependency caches if cleanup removed them; unique source changes and the compiled driver are in the checkpoint.

Driver source lock pins StevenMXZ b3b32ad30e7d7b4bd897e678af064036eeb9df09 and WinNative c71f600a2977a141f21a922c9ed2e14fb09b13d3. Vauzi/710 research checkout dd75952b6d3df5c31d9bef3adbda0577697a16b2; the old named experiment branch did not exist at retrieval. Whitebelyash mesa-unified turnip/gen8 9c7e022677dfa3abb356c2b6732cbd2e25783d01 was VERSION26.3.0-devel and was not used as the 27.0 baseline. Official Mesa ed2b00fa28dfc60061e21ecbe2280a49940fb470 was inspected separately; exact V38 reported base f28352d117 was then recovered from official GitLab.

Seven Android scripts plus balance script produced the aggregate source patch. A825 insertion and the old draw-count-threshold anchor were not applied. Extra mesh patch 0001 failed strict checks; subsequent 0002–0006 were not force-applied. The pinned base already has nonblocking KGSL zero-timeout polling. Original V38 compiler flags/full patch manifest remain unavailable, so do not call this a byte-identical V38 reproduction. Bandwidth multiplier 11/10 →10/10 is a candidate tune, not a measured gain.

Mesa compilation needed private bison/flex/m4/pkg-config/glslang tools and Python meson1.9.0/ninja1.13.0/cmake3.31.6/mako1.3.10/ply3.11. BISON_PKGDATADIR and M4 must point into the extracted private host tools in restricted environments. Clang C++ narrowing errors were addressed with the recorded -Wno-c++11-narrowing build option. First driver build succeeded, but gameplay/ELF/package checks are still held for the later test phase.

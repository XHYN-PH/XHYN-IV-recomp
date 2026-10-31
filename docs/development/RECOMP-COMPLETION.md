# Core recompilation completion tracker

Core correctness and compatibility take priority over additional launcher/UI
features. A successful source audit or APK build does not establish that the
whole game is finished. This tracker records evidence and the remaining gates;
it deliberately assigns no overall completion percentage.

Android builds the Graine application under
`glue/rexglue-sdk-main/gta4-recomp`, as selected by the top-level CMake Android
branch. The empty stubs in the legacy `LibertyRecomp/cpu/ppc_missing_stubs.cpp`
are outside this Android target and are not a count of missing Android gameplay
functions.

## Current milestones

| Gate | Evidence and remaining work |
| --- | --- |
| Generated-code wiring | **Source consistency verified:** 89 translation units, 38,352 generated bodies, 38,607 unique bindings, 246 kernel imports and nine native CRT bindings. Registrations, image mappings, declarations, source list and direct-call targets agree. Original-binary analysis coverage remains unverified. |
| Guest/native calling convention | **First defect fixed:** signed/unsigned 64-bit integer arguments retain their width in registers and stack slots; narrow arguments and guest pointers retain their 32-bit behavior. Big-endian integer scalar wrappers now store the correct byte order. Broader callback, mixed floating-point and vector ABI coverage remains open. |
| Kernel and host services | **Inventory established:** 12 imported generic stubs remain. Ordinary export wrappers also need behavioral review; their count does not establish complete implementations. Triage against actual game call paths is pending. |
| Engine/game behavior | Startup and free-roam work in supplied device reports. Mission progression, script branches, cutscenes, interiors, wanted levels, activities, save/reload and DLC need a recorded compatibility matrix. |
| Rendering and shader behavior | Native Vulkan builds. The camera-linked shadow band remains open. Shadow projection, atlas edges, filtering, clear/resolve behavior and representative materials/effects require matching captures and reference comparisons. |
| Timing, streaming and audio | Pipeline-variant preparation and severe-hitch retention are in Test11. Remaining stalls, asynchronous I/O/callback behavior, audio starvation and long-session resource lifetime need targeted investigation and validation. |
| Final acceptance | A full ARM64 native build and host regressions are required after core edits. Device gameplay, sustained performance and regressions against the same original content/settings are separate requirements. |

The generated source contains 1,202 explicit `Switch case out of range` traps.
These are generated jump-table guards, not 1,202 identified missing functions.
Reaching one still requires investigation of the guest state and control-flow
analysis; removing a guard would not implement the missing behavior.

## First calling-convention fix

The bridge previously read only `.u32` for all eight integer registers, even
when the native signature took `uint64_t`/`int64_t`. Stack arguments similarly
read or wrote only the low four bytes of an eight-byte slot. For example, a
native hook receiving `0xE0000123456789AB` instead received `0x456789AB`.

The checked-in game imports eight ordinary export wrappers with 64-bit integer
parameters:

- `XamContentCreateEx`: content size.
- `XamNotifyCreateListener`: notification mask.
- `XamShowDeviceSelectorUI`: requested storage size.
- `XamShowMarketplaceUI`: offer identifier.
- `XamUserGetMembershipTierFromXUID`: user identifier.
- `XamUserGetOnlineCountryFromXUID`: user identifier.
- `XamUserCreateAchievementEnumerator`: user identifier.
- `XamUserCreateStatsEnumerator`: pivot identifier.

The fix derives integer width from the native signature. A wide ninth argument
uses the complete big-endian slot at `r1 + 0x50`; a narrow ninth argument keeps
using its low word at `r1 + 0x54`. Argument placement and floating-point policy
are otherwise unchanged. Pointer decoding still ignores unspecified upper GPR
bits, including when a guest null pointer has nonzero upper bits.

New regressions exercise the production native-hook wrapper and typed import
wrapper, all eight integer registers, signed/unsigned stack values, independent
raw ABI byte expectations, adjacent-slot canaries, narrow values, mapped/raw
null pointers and big-endian integer storage. With the original implementation,
five of the initial nine cases failed, including the high-bit identifier.

This establishes the bridge correction. It does not establish that every service
listed above is otherwise complete or that gameplay FPS increases.

The ABI correction is a source change after the signed Test11 candidate; a future
package is needed to test it on a device.

## Imported generic stubs requiring triage

| Group | Symbols | Next evidence needed |
| --- | --- | --- |
| Low-level I/O | `IoCheckShareAccess`, `IoCompleteRequest`, `IoDeleteDevice`, `IoDismountVolume`, `IoInvalidDeviceRequest`, `IoRemoveShareAccess`, `IoSetShareAccess` | Trace the guest device/driver branches and distinguish them from the implemented host VFS paths. Implement required contracts with status, completion and lifetime tests. |
| Object services | `ObIsTitleObject`, `ObReferenceObject` | Establish the guest object contract and balance reference/handle lifetime on reachable paths. |
| Unicode | `RtlUpcaseUnicodeChar` | Verify the Xbox character mapping and filename consumers, then implement matching behavior with Unicode cases. |
| Xbox online UI | `XamShowGamerCardUIForXUID`, `XamShowPlayerReviewUI` | Define supported host behavior and accurate unavailable/cancellation results; these platform services require different treatment from story-mode engine behavior. |

These 12 bindings are an inventory, not a list of 12 proven gameplay failures or
the entire remaining workload. A stub with zero generated direct calls may
still be called indirectly. The latest supplied Test10 report contains no
matching generic-stub warnings in its retained logs; it does not cover all paths.

## Reproduce the checks

```sh
python3 tools/test_audit_recomp_core.py
python3 tools/audit_recomp_core.py --json out/recomp-core-audit.json
git submodule update --init glue/rexglue-sdk-main/thirdparty/catch2
python3 tools/run_recomp_core_tests.py
python3 tools/run_android_host_tests.py
cmake --build out/build/android-arm64-release --target LibertyRecomp liberty_install --parallel 4
```

Use the configured compiler/SDK environment from the Android build guide. The
core runner uses pinned Catch2 and runs production bridge, stack and byte-order
tests under AddressSanitizer and UndefinedBehaviorSanitizer. GitHub's Android
regression workflow now runs the same audit and core suite.

The audit's `consistency_passed` field describes generated-source wiring only.
Its JSON retains the stub inventory, wide-parameter imports, trap reasons,
audited-source digest and explicit coverage limitations. Eight audit tests cover
the unfinished inventory and reject missing bodies, exports, declarations,
mappings, source-list entries, direct-call targets and duplicate registrations.

## Validation for this checkpoint

| Check | Result |
| --- | --- |
| Generated-source audit | Passed; remaining stubs retained in the report |
| Audit regression suite | Eight cases passed |
| Production PPC bridge, stack and byte-order suite | 30 cases / 94 assertions passed with ASan/UBSan |
| Same suite against the isolated upstream patch | 30 cases / 94 assertions passed with ASan/UBSan |
| Existing Android native host regressions | 12 groups passed with ASan/UBSan, plus four independently decoded PNG fixtures |
| Full Android ARM64/API 29 native build | Game, runtime, renderer and installer targets passed with NDK r29 |
| Phone gameplay and reference comparisons | Pending |

## Upstream patch

The bridge header and regression tests are also isolated in commit `59ac0645`,
based directly on upstream `1922957d`. That patch changes two files and excludes
this fork's launcher, diagnostics and rendering changes.

[Prepared upstream comparison](https://github.com/OZORDI/LibertyRecomp/compare/main...XHYN-PH:fix/ppc-integer-argument-widths)

GitHub rejected upstream PR creation with `Resource not accessible by
integration`. The branch is pushed and ready for review through the comparison
page. The isolated patch passed the host core suite; a full upstream build and
gameplay validation remain pending.

## What blocks a completion claim

The manifest's original input `assets/default_v8.xex` is absent from this
workspace. Generated source builds without it, but checking undiscovered code
and regenerating analysis require the matching TU8 executable. This workspace
also has no phone test connection. Supplied reports are useful observations,
and cannot substitute for full mission coverage or reference rendering checks.
The installed game's `default.xex` and TU8 `default.xexp` provide the base
executable and update needed to prepare that input.

Before marking the core complete, record results for the supported game revision
and hardware: installation/startup, existing and fresh saves, mission and script
progression, transitions/interiors/cutscenes, representative rendering, audio,
input, resource lifetime and sustained sessions. Keep optional online/platform
features separately scoped, with explicit supported/unavailable behavior.

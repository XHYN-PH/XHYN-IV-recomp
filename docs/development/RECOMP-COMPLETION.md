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
| Generated-code wiring | **Source and binary checks passed:** 89 translation units, 38,352 generated bodies, 38,607 bindings, 246 kernel imports and nine native CRT bindings. The supplied TU8 image has bindings for all 25,414 exception-table entries and 21,344 distinct in-code direct BL targets. All 141,135 generated BL annotations match the original instructions. Six additional BL sites in SEH handler regions remain under review. Indirect control flow and instruction semantics need separate validation. |
| Guest/native calling convention | **First defect fixed:** signed/unsigned 64-bit integer arguments retain their width in registers and stack slots; narrow arguments and guest pointers retain their 32-bit behavior. Big-endian integer scalar wrappers now store the correct byte order. Broader callback, mixed floating-point and vector ABI coverage remains open. |
| Kernel and host services | **Inventory established:** 12 generic imported stubs and 10 ordinary wrappers with explicit stub/partial diagnostics. Some diagnostics apply only to unsupported requests. Context capture, unwind and the C exception handler are explicitly stubbed and need core work. The diagnostic inventory is not exhaustive. |
| Title-update decoding | **Defect fixed:** byte-oriented records, explicit payload/image bounds, overlap-safe copies and bounded loader calls. The legacy four-argument SDK entry point remains available. Both decoder paths reproduce the supplied update's prepared image under strict ASan/UBSan. |
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

## TU8 executable and decoder audit

The supplied retail executable matches the installer's complete-file XXH3 pin
`2823947441600373906`, and its signature digest matches the delta descriptor.
The supplied/extracted patch's SHA-256 is the required
`480aee5e2b42707791e7571bb8407c5bb3f6c7534f07f9beb426db4cfc648fd3`.
Its source version is `0x00000005` and target version is `0x00000805`.

Real decoding exposed four-byte-aligned struct accesses to records whose
variable-length payloads leave later headers unaligned. A small synthetic
case also reproduced the active SDK defect with UBSan stopping on the first
invalid access. The fixed reader handles the 12-byte serialized header as bytes,
checks payload/reference/output extents and uses `memmove` for overlapping copy
records. The loader supplies actual allocation sizes and bounds decrypted
blocks. The legacy patcher uses aligned local headers and bounded copies.

Replaying the complete supplied pair through both fixed decoder paths produces
the same 19,939,328-byte prepared image as before, with strict ASan/UBSan clean:
`760ebac4e5f0351edb39015eba89e729b14eaf95afdabb529a447ff8f8e11cd1`.
This decrypted/expanded representation differs from the embedded canonical XEX
file hash; no canonical full-file hash match is claimed. Original inputs and
prepared images remain outside the repository and release packages.

The binary audit uses PE `VirtualAddress` offsets in this decoded image.
`PointerToRawData` describes a different layout and would yield false call gaps.
Synthetic fixtures deliberately give these fields different values to check
the mapping. Runtime instruction semantics, indirect calls and mission coverage
remain separate from these static checks.

## Exception-handling work still open

The six additional BL sites appear in four functions with original SEH scope
tables. Their regions are omitted from the ordinary generated control flow.
The audit retains the original scope metadata for review:

| Parent function | Scope handler | Unannotated BL sites |
| --- | --- | --- |
| `0x827AD0F8` | `0x827AD180` | `0x827AD188`, `0x827AD19C` |
| `0x82A009C0` | `0x82A00AEC` | `0x82A00B00`, `0x82A00B10` |
| `0x82A04C90` | `0x82A04D00` | `0x82A04D00` |
| `0x82A08E90` | `0x82A08EE8` | `0x82A08EFC` |

Their scope handler thunk is the imported `__C_specific_handler`. That export,
`RtlCaptureContext` and `RtlUnwind` currently log explicit stub diagnostics.
Correct exception dispatch must restore guest register/stack state and invoke
the matching filter/cleanup paths. This needs dedicated implementation and
fault-path tests before a complete exception-handling claim. The presence of
these regions does not establish that they caused a reported gameplay problem.

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
python3 tools/test_recomp_binary_audit.py
python3 tools/audit_recomp_core.py --json out/recomp-core-audit.json
# Optional, using a privately prepared decrypted/uncompressed TU8 image:
python3 tools/audit_recomp_core.py --xex /path/to/private/default_v8.xex --json out/recomp-binary-audit.json
git submodule update --init glue/rexglue-sdk-main/thirdparty/catch2
python3 tools/run_recomp_core_tests.py
python3 tools/run_android_host_tests.py
cmake --build out/build/android-arm64-release --target LibertyRecomp liberty_install --parallel 4
```

Use the configured compiler/SDK environment from the Android build guide. The
core runner uses pinned Catch2 and runs production bridge, stack, byte-order and
delta-record tests under ASan/UBSan, stopping on undefined behavior. GitHub's Android
regression workflow now runs the same audit and core suite.

The audit's `consistency_passed` field describes source wiring plus the optional
binary checks. Its JSON retains generic stubs, explicit partial diagnostics,
wide-parameter imports, trap reasons, audit digests and limitations. Ten source
tests and ten synthetic binary tests cover missing bindings, source-list drift,
wrong image layout, altered call targets, malformed metadata and SEH inventories.

## Validation for this checkpoint

| Check | Result |
| --- | --- |
| Source and supplied-executable audit | Passed within the stated scope; six SEH candidates retained |
| Source/binary audit regression suites | 10 + 10 cases passed |
| Production core suite including delta records | 39 cases / 172 assertions passed with strict ASan/UBSan |
| Integrated SDK LZX and delta-record suites | 19 cases / 267 assertions passed with strict ASan/UBSan |
| Isolated upstream calling-convention suite | 30 cases / 94 assertions passed with ASan/UBSan |
| Isolated upstream LZX/delta suite | 19 cases / 267 assertions passed with strict ASan/UBSan |
| Real TU8 replay through both decoder paths | Identical prepared image; strict ASan/UBSan clean |
| Earlier Android native host checkpoint | 12 groups passed, plus four independently decoded PNG fixtures |
| Full Android ARM64/API 29 native build | Game, runtime, renderer and installer targets passed with NDK r29 |
| Phone gameplay and reference comparisons | Pending |

## Upstream patch

The bridge header and regression tests are also isolated in commit `59ac0645`,
based directly on upstream `1922957d`. That patch changes two files and excludes
this fork's launcher, diagnostics and rendering changes.

[Prepared upstream comparison](https://github.com/OZORDI/LibertyRecomp/compare/main...XHYN-PH:fix/ppc-integer-argument-widths)

The separate [XEXP decoder patch](https://github.com/OZORDI/LibertyRecomp/compare/main...XHYN-PH:fix/xexp-unaligned-records)
is commit `899e565c`, also starts from `1922957d` and changes only shared decoding, loader integration
and tests. Its integrated LZX/delta suite passes in an isolated checkout.

GitHub rejected upstream PR creation with `Resource not accessible by
integration`. Both branches are pushed and ready for review through the comparison
pages. The isolated patches passed their host suites; a full upstream build and
gameplay validation remain pending.

## What blocks a completion claim

The original executable and TU8 update are now available privately and the
binary-backed checks above are complete. Remaining core work includes exception
handling, service behavior and rendering correctness. This workspace has no
phone test connection, so full mission coverage and reference rendering checks
remain pending.

Before marking the core complete, record results for the supported game revision
and hardware: installation/startup, existing and fresh saves, mission and script
progression, transitions/interiors/cutscenes, representative rendering, audio,
input, resource lifetime and sustained sessions. Keep optional online/platform
features separately scoped, with explicit supported/unavailable behavior.

# Test12 validation

Release `0.5.9.3-xhyn-r2-test12`, Android version code 40. This release combines
the previously unreleased Test11 runtime work with the core calling-convention,
title-update decoder and binary-audit changes. The complete accumulated history
is in [the r2 changelog](R2-CHANGELOG.md).

## Included changes

- Preserve full 64-bit integer arguments and correct big-endian integer storage
  in the PPC/native bridge, while retaining narrow and guest-pointer behavior.
- Decode variable-length XEXP records without unaligned struct reads. Validate
  record payloads, source/output extents and runtime loader block lengths; use
  overlap-safe record copies and retain the old SDK entry point.
- Audit the privately prepared TU8 image against generated bindings and direct
  call annotations. Retain original exception-scope metadata for six additional
  call sites instead of treating them as proven ordinary control-flow gaps.
- Include Test11 config serialization and load-error reporting, pipeline-variant
  preparation, demand-wait/recipe diagnostics and recent/worst hitch retention.
- Fix Android CI initialization after the SDK stopped providing the legacy
  `tools` package. Request `platform-tools` explicitly.

## Checks

| Check | Result |
| --- | --- |
| Production PPC/stack/byte-order/delta-record suite | 39 cases / 172 assertions, strict ASan/UBSan |
| Integrated SDK LZX/delta suite | 19 cases / 267 assertions, strict ASan/UBSan |
| Isolated upstream decoder suite | 19 cases / 267 assertions, strict ASan/UBSan |
| Real supplied executable/update replay | Both fixed paths reproduce the same prepared image, strict ASan/UBSan clean |
| Source/binary audit regressions | 20 cases passed |
| Native Android host regressions | 12 groups passed with ASan/UBSan; four PNG fixtures decoded independently |
| Java regression groups | 11 passed |
| Android UI tests | 19 passed; no failures, errors or skips |
| Android package | Resources, Java compilation and DEX preparation passed |
| Native Android ARM64/API 29 | Game, runtime, both renderers, installer and driver proxy built; final incremental check clean |
| Registry and setup metadata | 118 registry entries / 96 required CVars; metadata and 11 setup-tool cases passed |
| Reviewed dependency patches | Preserved and prepared successfully |

The optional real-image audit checks 25,414 exception-table entries, 21,344
distinct in-code BL targets and 141,135 generated BL annotations. No missing
binding or changed annotated call target was found. Six additional calls occur
in four functions with original SEH metadata. The runtime still has stubbed
context capture, unwind and C exception-dispatch services. This is an inventory
of unfinished work, not an implementation or a measured gameplay failure.

See the [core completion tracker](../development/RECOMP-COMPLETION.md) for binary
layout, prepared-image hash, isolated upstream patches and remaining gates.
The original executable, update and prepared game image stay outside Git and
release packages. Full desktop and other-platform builds are outside this
Android validation.

## Signed package

APK: `XHYN-IV-0.5.9.3-xhyn-r2-test12.apk`, 67,030,598 bytes.

SHA-256:
`94dbb87f28247c6fa5a58c79d9114a7053989e31aed0a066eab917d8050eb463`.

Release certificate SHA-256:
`106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`.

All 11 packaged ARM64 libraries match the freshly staged native output and have
at least 16 KiB ELF LOAD alignment. APK ZIP integrity/alignment, package/version
metadata, all 44 touch PNGs, six driver ZIPs and 24 driver member pins passed.
Every unsigned ZIP entry remains unchanged after signing. V38, test classes,
signing credentials and supplied game binaries are absent. Temporary signing
files were removed after verification.

## Remaining device work

Full recompilation, exception behavior, missions, sustained performance, audio,
heat, rendering and physical-controller use still need device validation. The
camera-linked straight-line shadow band remains open. No FPS improvement or
complete-game compatibility is claimed.

[Release and full changelog](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test12)
· [Signed APK](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test12/XHYN-IV-0.5.9.3-xhyn-r2-test12.apk).

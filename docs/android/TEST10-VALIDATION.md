# Test10 validation

Release: `0.5.9.3-xhyn-r2-test10`, version code 38. Includes the Test9 UI,
controller, touch-editor and capture changes described in TEST9-VALIDATION.md.
Test9 was not released separately; its refinements are included in Test10.

The supplied Test9 report ran for about 13 minutes. Interact loaded on the first
attempt, and there was no current-session crash/device loss. The user did not
encounter the shadow band. That does not establish a shadow-rendering fix.

## Changes in this release

- Pause revisions exclude a host menu opening and closing between presents from
  frame statistics, benchmark samples and adaptive quality windows. Benchmarks
  also watch pause state/revision so interrupted runs are marked as mixed.
- Logged hitches retain up to 16 rolling CSVs, each with up to 64 recent presents,
  and at least two seconds between captures. Work is queued to the existing
  diagnostic writer; a busy writer preserves the runtime summary. Times separate
  publish from outside-publish wall time and include matching pipeline/texture
  counters. These are correlational CPU measurements, not GPU timings.
- Recorder lookahead prioritizes unseen shader pairs within its existing 500 µs
  deadline, eight novel attempts and at most 4,096 inspected commands. Exact
  preparation and draw fallback remain available; workers are not increased.
- Adaptive audio recovery uses 2–6 blocks (about 11–32 ms), grows only on repeated
  underruns and returns to two after about two stable seconds. Empty-queue and
  refill silence have distinct counters. Producer gaps are reported. Failed
  device submission returns the already-consumed frame's semaphore credit.
- Thermal status describes sampled severity, Guard state, requested Turbo and
  backoff, and draw scale. Stale/unsupported samples are labelled. This change
  does not silently turn on protection or claim achieved clocks.
- Extended texture reuse reserves only eligible idle CPU-backed image allocations
  from a shared allowance bounded by available RAM, Vulkan heap budgets and the
  configured texture limit. Reuse/retirement returns the reservation. Memory
  pressure and allocation recovery bypass the extension. GPU-authored and
  referenced images retain their existing protections. Nonextended mode keeps
  its ordinary retention policy.
- Rejected mods get specific PC-plugin, Xbox data-path or episode-installer help.
  The importer still rejects incompatible files and unsafe folder layouts.
- Intentionally unmounted Xbox cache namespaces are logged as optional probes.
  No replacement device is mounted; real missing paths and broken aliases keep
  their existing error path.

## Pre-build checks

| Check | Result |
| --- | --- |
| Java fixtures | 11 groups passed, including selected-session hitch CSV export and mod guidance |
| Android UI tests | 19 passed; zero failures, errors or skips |
| Native host regressions | 11 groups with ASan/UBSan, including the production SDL callback |
| Diagnostic PNGs | Four independent lossless decode/CRC/bounds checks passed |
| Contextual controls | Five sanitized executables; 83 runtime checks and one source-order check passed |
| Android Java/resources | All 76 Java sources and resources compiled |
| NDK source checks | Seven changed production translation units passed with the configured Android flags |
| CVar registry | 118 entries; all 96 required CVars classified |
| Setup metadata/tests | Metadata passed; 11 setup-tool tests passed |
| Whitespace review | `git diff --check` passed |

The callback harness replaces the SDL device and guest-memory boundaries; it
executes the production queue, conversion, recovery, mute, pause and failure-credit
paths. It does not emulate Android scheduling or the physical device. ASan and
UBSan cover the host tests; unused guest-heap RTTI is excluded from vptr checking
in the SDL translation unit, as are unused physical backends in the input factory.

The optional all-platform `setup_repo.py --check` stops at uninitialized Catch2.
This Android checkout initializes its build dependencies; it does not include all
optional dependencies for other platform/test configurations. The Android build
and focused tests above do not require Catch2.

## Built APK checks

- Native runtime, both renderer libraries, game and installer linked successfully
  using NDK r29/ARM64/API 29; the driver proxy build passed.
- APK version 38, minimum API 29, target API 35, release/nondebuggable manifest.
- All 11 packaged ARM64 libraries match the new staged build; ELF LOAD segments
  have at least 16 KiB alignment. ZIP alignment and CRC/unique-entry checks pass.
- Six bundled driver archives and all 24 member pins match the runtime selector.
  V38 is absent. All 44 touch PNGs match source; no test classes or signing-key
  files are packaged.
- Existing release certificate verified:
  `106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`.
- Every unsigned APK ZIP entry remained byte for byte identical after signing.
  The task-local signing archive and temporary extracted keys were removed.
- Signed APK SHA-256:
  `37b2d1f9823033aae0953c4c4ab4b10aede1a95c7b8ea836c5a82ec03d01fa2b`.

Local verification reports and source inventory are under `out/` as
`XHYN-IV-0.5.9.3-xhyn-r2-test10*.verification.json` and
`Test10-build-provenance.json`. The compiler still emits preexisting warnings;
this validation does not claim a warning-free repository.

## Device validation still required

No Android gameplay, physical-controller test, sustained thermal/audio run or FPS
comparison was possible here. The largest reported stalls are not proven to be
shader waits; the new traces are intended to identify their remaining causes.
The camera-linked straight shadow band remains unresolved. Use Capture shadow
issue while it is visible and export the session to obtain matching images.

## Temporary signed APK backup

[Download Test10](https://tmpfiles.org/wjAbLq1Cs2et/xhyn-iv-0.5.9.3-xhyn-r2-test10.apk) — requested lifetime 48 hours.
The full downloaded APK SHA-256 matched the signed local candidate. Only the
signed APK was uploaded. Final incremental native build reported no work to do.

## GitHub release

[Test10 release and changelog](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test10) · [Signed APK](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test10/XHYN-IV-0.5.9.3-xhyn-r2-test10.apk).

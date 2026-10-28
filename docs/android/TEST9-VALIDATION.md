# Test9 candidate validation

Version: `0.5.9.3-xhyn-r2-test9`, Android version code **37**.

The candidate was built from fresh ARM64 native libraries on Linux, including
the game, runtime, renderers, installer and driver proxy. No previous APK's
native libraries were reused. The APK is signed with the existing XHYN release
identity. Signature verification and alignment checks passed after signing.
Test9 was not published separately on GitHub. Its changes are included in the
Test10 release; this document preserves the original Test9 build checks.

APK: `out/XHYN-IV-0.5.9.3-xhyn-r2-test9.apk` (66,989,638 bytes).

SHA-256:

```text
788c8f5c00dbfb15f28cfc7accfb0c4af2dce64bd9d643ea5a3e0de29b88df0f
```

The public signing certificate SHA-256 matches the previous XHYN release:
`106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`.
Every existing ZIP entry matches the verified unsigned candidate byte for byte.

## Implemented refinements

- Compact achievement rows, prompts and session panel; bounded scrolling for
  Logs and editor tools.
- Full-window overlay placement with zero-gap edges; optional app CPU, available
  GPU counters and actual internal/output dimensions. Enabling a metric selects
  Detailed mode. Blocked GPU counters show unavailable.
- Touch editor sections for Control, Arrange and Layout, with Save in the header.
- Shared controller navigation throughout the launcher, dialogs and editors,
  including shoulder tab switching, scrolling focused controls into view and
  avoiding duplicate activation. Controller presets and left-stick inversion.
- Layout transition hold across regular/contextual gameplay and phone layouts,
  including parachute/minigame modes. Menus, loading and suspended input bypass
  the hold. Zero is the earliest observation; negative prediction is not offered.
- Removed bundled V38 Patched, its selector entry and associated assets. Legacy
  selections resolve to Automatic in the launcher and runtime.
- Checked upload growth without periodic full draw scans in the sparse path,
  safe retirement of unused overflow allocations, distributed pipeline lookahead,
  and less preparation for textureless indexed draws.
- Separate heavy command-transport profiling from CPU detail, so ordinary timing
  captures retain batching. Graphics-detail capture uses a larger query budget.
- Explicit installed touch-icon directory, improved missing-icon path logging,
  clearer rejected-mod guidance and unsupported sustained-performance labels.
- Shadow captures with PNG visualization, lossless native-format raw data,
  camera matrices, frame/submission IDs and surface ownership information.

## Checks completed

| Check | Result |
| --- | --- |
| Java host fixtures | 11 groups passed |
| Android UI tests | 18 passed; 0 failures/errors/skips |
| Native host regressions | 10 groups passed with ASan/UBSan |
| Contextual input regressions | 5 executables passed with ASan/UBSan; 83 runtime checks and one source-order check |
| Diagnostic PNG decoding | Four images independently decoded; CRC, block boundaries and input bounds checked |
| Binary diagnostics export | Images and compressed raw data larger than the text limit retained byte for byte; partial files omitted |
| Android Java/resources | All 75 Java sources and resources compiled |
| Fresh native build | Game/runtime/renderers/installer linked with NDK r29; driver proxy built |
| Repository setup tests | 11 passed; metadata check passed |
| CVar registry | 118 entries; all 96 required CVars classified |
| APK native libraries | 11 ARM64 libraries; dependencies satisfied; all ELF LOAD alignments at least 16 KiB |
| APK contents | ZIP integrity and unique entries checked; all 44 touch icons match source, including Interact |
| Bundled drivers | Six archive hashes and all declared member hashes verified; V38 absent |
| APK metadata/alignment | Android reader and zipalign accepted the version-37 candidate |

Input test infrastructure was repaired without weakening assertions: checked-in
gesture samples replace a missing external generator, CVar stubs support typed
declarations and chained configuration, and the radar fixture supplies the current
authored/viewport fields. Fixed-gamepad rendering has separate production ImGui
coverage; contextual fixtures explicitly select the contextual backend.

## Device work still open

**The camera-linked straight-line shadow band is unresolved.** The video shows
its motion but does not identify a projection, atlas, filtering or clear/resolve
fault. No speculative shadow suppression was added. Vehicle and pedestrian
shadows retain their rendering path.

To collect the next evidence, start a logged session, choose **Capture shadow
issue**, rotate the camera with the band visible, wait a few seconds, then export
the report. Up to three frames are captured with a 96 MiB raw-copy budget;
unavailable/oversized sources are recorded explicitly. The images show targets at
the end of each native frame, with ownership metadata for matching the trace.
Visible-frame is the presenter input, excluding Android overlays. PNGs are opaque
RGB visualizations; compressed raw copies preserve native values and alpha.

No phone gameplay, physical-controller navigation, thermal endurance, GPU image
correctness or measured FPS improvement was validated in this environment.
Interact's actual bitmap display also needs device confirmation. For audio,
dropout protection remains enabled by default; the reported game-side starvation
still needs a recording with low-latency audio off. These are pending checks,
not confirmed fixes.

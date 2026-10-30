# Test11 shared-runtime candidate

Version `0.5.9.3-xhyn-r2-test11`, Android version code 39. Test10 remains the
published release. This is the first batch of shared-runtime work; it does not
claim the upstream recompilation is complete or that phone FPS has improved.

## Changes

- Fix native TOML serialization of JSON, quotes, backslashes, UTF-8 and control
  characters in both config exporters. Preserve the existing flat double-quoted
  format and read each getter once.
- Add a result-returning configuration loader while retaining the existing void
  entry point. A syntax failure applies no flags and leaves the file untouched.
  Final runtime logs now retain the error and source position after logging is
  initialized, instead of claiming the invalid file loaded successfully.
- Replace shader-pair-only lookahead history with fingerprints of recorded draw
  inputs: fixed state, vertex declaration/strides, topology, attachment metadata,
  shader identity and renderer policy. Remember readiness only after the exact
  pipeline lookup returns a pipeline. The history only prioritizes speculation;
  normal draws retain their exact-key lookup, failure path and required draws.
- Keep the existing 500 µs scheduling deadline, eight pending priority attempts,
  4,096-command priority scan and distributed exact fallback. Worker counts and
  persistent recipe format are unchanged.
- Log exact shader files and pipeline identity for demanded waits of at least
  20 ms, capped at eight messages per frame-statistics window. Report unreadable,
  incompatible or accepted learned recipes.
- Partition the existing 16 hitch CSV slots into 12 recent and four worst recorded
  events. Preserve severe-event ranking across host-menu pauses and retain the
  existing two-second capture cooldown and 64-present history.

Existing malformed configuration files are preserved and reported; this patch
prevents new serialization damage. It does not guess how to repair unknown file
contents. No shadow-rendering change was made.

## Checks before the native build

| Check | Result |
| --- | --- |
| SDK CVar suite, Android fork | 32 cases / 195 assertions passed with ASan/UBSan |
| SDK CVar suite, isolated upstream checkout | 32 cases / 195 assertions passed with ASan/UBSan |
| Serializer regression | Restoring only the old serializers reproduces the report's `saw 'a'` parse failure |
| Native host tests | 12 groups passed with ASan/UBSan, plus independent PNG decoding |
| Java fixtures | 11 groups passed, including export of recent and worst hitch CSVs |
| Android UI tests | 19 passed; no failures, errors or skips |
| ARM64/API 29 syntax | Changed renderer, CVar implementation and app startup passed |
| Registry | 118 entries, all 96 required CVars classified |
| Setup | Metadata and 11 setup-tool regressions passed |
| Diff review | Whitespace check passed; existing reviewed dependency patches preserved |

Replaying all 108 hitch summaries from the supplied Adreno 730 session with the
production retention policy preserves the worst 467.48 ms frame after recent
slots roll over. The total file bound remains 16. This replay tests evidence
retention, not Android rendering performance.

## Upstream contribution

The isolated config patch is based on upstream commit
`1922957d9f5a09acf0af12b918c43a910b3a13a5` and changes only the shared CVar header,
implementation, app-startup reporting and CVar tests. It excludes this fork's
launcher, renderer and diagnostics changes.

[Prepared upstream comparison](https://github.com/OZORDI/LibertyRecomp/compare/main...XHYN-PH:fix/cvar-toml-roundtrip)

GitHub rejected PR creation with `Resource not accessible by integration`.
The branch is pushed and can be submitted through that comparison page.

Standalone upstream `cvar.cpp` passes ARM64 syntax. Upstream `rex_app.cpp` retains
an existing NDK incompatibility in its `clock_time_conversion` specializations;
the unchanged upstream source produces the same error. Our fork's app-startup
integration passes syntax and native compilation. No complete upstream desktop
build or gameplay validation is claimed.

## Device validation still required

Compare Test10 and Test11 on the same phone, driver, settings and route. Inspect
pipeline-demand waits, repeated scene visits, configuration reloads and the severe
hitch CSVs. The Adreno 730/System report cannot be treated as a controlled FPS
comparison against the previous Adreno 750/ESMERINGHOY sessions. The camera-linked
shadow band and remaining sustained-performance/audio/thermal issues remain open.

## Built candidate

The full ARM64/API 29 game, runtime, renderer and installer build passed with NDK
r29. The driver proxy build passed, and the final incremental build reported no
work to do. The package contains the new config-load error, demanded-pipeline
wait/recipe diagnostics and worst-hitch file paths.

All 11 packaged native libraries match the staged build and have at least 16 KiB
ELF LOAD alignment. ZIP CRC/alignment, version metadata, all 44 touch PNGs, six
driver archives and 24 driver member pins passed verification. V38 and test/key
files are absent.

The existing release certificate SHA-256 is
`106de5f3a0f6bdf1cad369e87b8c509b8e34693b2c8eeac3a1c4627ac2a23d49`.
Every unsigned ZIP entry remained unchanged after signing. The task-local signing
archive and temporary extracted keys were removed.

Signed APK: `67,034,694` bytes, SHA-256
`ea309718e15c6809fcaf8e6756d295c9301b1bde02330e8c49986718d063e058`.

## Test APK

[Signed Test11 APK](https://tmpfiles.org/wNA9Tq6JabSY/xhyn-iv-0.5.9.3-xhyn-r2-test11.apk) — temporary 48-hour backup.
The downloaded file's SHA-256 matches the signed local candidate.

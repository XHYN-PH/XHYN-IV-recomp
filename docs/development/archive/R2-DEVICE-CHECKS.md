# R2 test-build checks

The shadow defect is already fixed in the user's last update. Compare R2 with
that build using the same scene and settings; preserve the working appearance.

## Installation

Use `XHYN-IV-0.5.9.3-xhyn-r2-test2-icons.apk`. The earlier Test 1 signed download had
a broken archive and produced Android's package parsing error. The corrected
APK has the same app ID and release certificate as r1, with version code 30.
Install it over r1 or the fixed Test 1 and verify the launcher opens before testing gameplay.

## Visuals and controls

1. Open the launcher, settings and both touch editors in landscape. Check text
   clipping, button labels, touch targets and scroll behavior on the phone.
2. Turn on **Reduce menu motion**. Reopen menus and verify artwork drift and
   menu/button animations stop. Check Android's system animation-disable setting
   too. Background the app and resume it.
3. Test mobile, contextual and Xbox layouts. Test move + camera + aim + fire,
   entering/driving/exiting vehicles, phone navigation, cover and cutscenes.
   Verify the new glyphs have no white outer border and sticks use the same theme.
   Confirm Aim is the supplied crosshair and Pause uses the supplied house glyph.
   The prior Test 2 bundled PNGs but copied them to external storage; the native
   reader uses the app's internal files folder. This hotfix corrects that path.
4. Move, resize and hide controls in each editor, save, restart and verify both
   layouts retain their independent edits. Connect and disconnect a physical pad.
5. In the default Mobile layout, tap Aim briefly: it must stop when released.
   Hold for 400 ms, release and verify aiming stays active with a lock indicator.
   Tap once to unlock. Try aim + camera + fire while moving. Change the hold
   duration in Controls, then disable the feature and verify momentary aim.
   Pause, background, die, load a save, enter a vehicle and start a cutscene:
   there must be no carried-over aim lock. Xbox LT remains a controller trigger.
6. Open each Session tab, expand details, then open Settings, Achievements and
   both editors and return. Gameplay must remain paused until Resume. FPS is
   labeled last-active publisher rate, and RAM/thermal data should refresh only
   while the panel is visible. Verify Android Back and an external controller's
   navigation, confirm/back buttons; check a physical keyboard and mouse too.
7. In Settings, switch categories with pending edits, search, enter precise
   numbers, discard, save and reset a category. Restart-only options must be
   disabled in a running session. Test a larger Android font size and the
   camera cutout/gesture edges. Launcher artwork/button animations must stop
   when Reduce menu motion is enabled or the app loses focus.

## Local achievements and original cheats

1. Start a game to populate the catalog. Open achievements from both menus.
   Locked secret achievements should hide their name and image.
   Check All/Locked/Unlocked, search, points, unlocked timestamp and tap-to-open
   details. Reveal secret details through Options. Missing artwork should use
   the new lock/trophy fallback, with a status badge on every entry.
2. Earn a known achievement without cheats. Verify one notification and unlock
   timestamp, then restart. Disable notifications and earn another achievement:
   its progress should still be saved. Repeated awards must not duplicate it.
3. Export saves and achievements, then import the ZIP. Verify both persist.
   Import an older saves-only ZIP and verify achievements stay unchanged.
4. In base GTA IV single-player, request an original cheat and resume. Verify its
   actual effect. Vehicle spawns need suitable open space. Loading, cutscenes,
   world changes and multiplayer must not execute a pending request.
   Test weapons and at least two different vehicle spawns in open space. The
   status "Submitted to game" confirms handler dispatch, not a verified effect.
   If there is no effect, export the resulting log; the queue now records stable
   world/player identity, cancellation reasons and handler invocation.
5. **Allow achievements while using cheats** starts off. Test a known original
   restriction with it off, then test the same award condition with it on. The
   implementation permits native award events; it cannot create an event that
   game scripts never submit. It does not grant official Xbox Gamerscore.

## Performance evidence

Use the same save, repeatable route, camera, weather, time of day, resolution,
driver, cap, graphics settings and cache state for each build. Keep cold and warm
cache runs separate. Repeat each comparison and include a sustained session so
temperature and throttling are visible. Record device/OS/driver, charging state,
route and settings with the result. Normal Play has detailed diagnostics off.

For a renderer capture, start **Play with logs**, use the in-game diagnostics
capture, return to the launcher and **Share latest log**. Analyze that ZIP with:

```bash
python3 tools/analyze_android_session.py session.zip --output report.json
python3 tools/analyze_android_session.py baseline.zip --compare candidate.zip
```

Publisher FPS measures the runtime's submissions; SurfaceFlinger/Perfetto is
needed to assess display delivery. GPU queue envelopes do not identify shader
cost. Renderer CPU scopes do not identify hot recompiled game functions; use
simpleperf sampling and symbols for that work. Compare sustained frame-time
distributions and stalls, rather than a single instantaneous FPS reading.

Real PGO training needs an instrumented build and representative gameplay from
the relevant episodes and workloads. Record the compiler, source revision and
routes with the raw profiles. Merge using the same compiler's `llvm-profdata`
and the existing `os/android/scripts/merge_pgo.py`; do not distribute an unrelated
profile as trained for R2. Validate the optimized build against the same routes
and input/visual checks before enabling it by default.

# XHYN IV R2 Test 2

Superseded by the icons hotfix (version code 30). See
[R2-ICONS-FIX.md](R2-ICONS-FIX.md). The original Test 2 APK included the imported
PNGs but copied them to the wrong Android directory, so the native renderer
could select its vector fallbacks. Bundling/preview checks did not verify this
connection. The hotfix corrects it and uses the user's new es2.zip selection.

Version `0.5.9.3-xhyn-r2-test2`, Android version code 29. Development build based
on r1 `dc7a8d02a272ead6f2ee3b99433d7df599c0f6df`, retaining Test 1 work. It uses
the original app ID and release signing identity. No public release is published.

The user's latest approved scope is the 43-item UI, controls, cheats and
performance-investigation list. The earlier 79-item optimization list remains
tracked in R2-WORK.md; this update does not claim unmeasured CPU/GPU rewrites,
PGO training, texture tiers or new FPS gains.

## What changed and why

| Approved items | Result in Test 2 | Remaining validation |
| --- | --- | --- |
| 1–6 Cheats | Fixed queue cancellation that treated a per-frame input counter as a world identity. Requests now use the stable world generation and player identity. Added specific rejection/cancellation logs and distinguish queueing from handler dispatch. Original retail cheat routing and achievement override remain bounded to the supported base-game single-player context. | Verify actual weapons/vehicle effects and the optional local award override on the phone. Handler dispatch alone does not prove an effect. |
| 7–14 Paused session | Rounded translucent right panel; Session, Controls, Controller, Graphics and Diagnostics tabs. Last-active publisher FPS/average interval, app PSS, available RAM, device/Android, selected driver and GPU. Expand resolution, FPS cap, actual presentation mode, thermal status, texture cache, pipeline count and cheat status. Worker sampling runs once per second only while visible and foregrounded; it stops on dismiss. | Layout, pause ownership and controller navigation on the phone. This is publisher telemetry, not physical display FPS. |
| 15–20 Launcher | Lower-left Play, Settings/Game files, Controls/Drivers/Achievements/More over existing XHYN city artwork. Configured resolution/cap/driver, readiness, original branding and community links. Rounded press/focus states, entry motion, artwork drift and reduced-motion support. Scrollable action group and cutout/gesture insets. | Review sizes and safe edges on the iQOO's landscape display. |
| 21–27 Settings | Top categories with search; options and contextual explanations, responsive right help panel, sliders plus precise numeric entry, draft state across tabs, Save/Discard, pending/restart counts, category reset and unsaved-close confirmation. Runtime-only and restart settings remain distinct. | Numeric keyboard, large fonts, save/relaunch and Android Back behavior. |
| 28–29 Pipeline investigation | Correlated supplied logs with first-use compilation stalls; kept existing demand-priority queue, worker bounds and learned preparation. Status now exposes ready, queued and active compilation plus learned recipes awaiting preparation; safely handles the synchronous fallback. | Repeat the same cold/warm route. New, previously unseen variants can still stall; no hitch-free or precompiled-everything claim. |
| 30 Thermal behavior | Thermal Guard setting is exposed. Current severe Android status cannot be overruled by a cooler predictive signal; cooldown requires all available Android signals to agree. GPU temperature is used only when Android signals are absent. Existing bounded distance response, Turbo backoff and cool recovery remain. | Sustained comparisons with Guard off/on and user-selected Turbo. Defaults are not silently forced. |
| 31 Presentation truth | Session details read the actual selected swapchain mode, including FIFO fallback, separately from the requested VSync option. FPS sampling avoids including a long pause in the next active window. | Verify the active driver/device result and display delivery using appropriate device capture. |
| 32 Audio investigation | Latest log contained two bounded starvation warnings, including startup; no audio failure or crash. Existing audio policy remains, with mute/latency options explained in settings. | Same-route audio and frame capture before changing buffer sizes. No unsupported buffer or CPU policy change. |
| 33 Cache investigation | Session details expose current texture usage/budget and pipeline totals; existing retention, budgets, learned caches and converted-cache controls remain. Supplied latest log showed startup cache reuse and no Android low-memory pressure. | Measure long-route churn/cold versus warm runs. No blanket cache increase or fabricated alternate texture tiers. |
| 34–36 Input, achievements and accessibility | Retained external input routing and focus states, separate editor layouts, local achievement backups and notification behavior. Shared motion preference now works across launcher/game processes using an atomic app-private preference file. Back from settings returns to the paused menu. | Physical controller/keyboard/mouse, backup round trip and achievement events need device checks. |
| 37 Preview | Reproducible four-screen source-aligned preview includes launcher, paused session, settings and achievements plus the actual current touch PNGs. | Preview uses example telemetry/catalog entries and artwork, not an Android screenshot or gameplay proof. |
| 38–42 Touch artwork/layout | Imported 19 GTA III variants from the supplied APK, removed the outside white ring/backing, normalized glyph tint/alpha, kept original XHYN fallbacks for missing IV actions and retained readable Xbox labels. Xbox Start keeps a separate menu glyph. Sticks share the borderless disc/grip theme. Existing hit areas, independent layouts and editor asset mapping are preserved. | Multitouch, opacity, thumb reach and layout persistence on the phone. No unnecessary layout reset. |
| 43 Aim locking | Default Mobile layout: quick taps aim while pressed; holding for the configurable threshold latches aim after release; the next press releases it without relatching during that same press. Amber lock marker, separate camera/fire, pause and gameplay-context resets. Default threshold 400 ms, adjustable 200–1500 ms. | Death/loading/cutscene/vehicle/background transitions and simultaneous controls on the phone. Xbox LT remains unchanged. |

## Achievement refinement

All/Locked/Unlocked filters and case-insensitive search show name, the relevant
description, state, points and saved unlock time. Tapping a row opens details.
Existing game achievement artwork is used when available; original vector
trophy/padlock fallbacks and a state badge make both states visible. Secret
names/details/images remain hidden until earned unless the user explicitly
reveals them through Options. Disabling unlock notifications does not disable
tracking. Progress remains local and included in save backups; no Xbox Gamerscore.

## Supplied evidence

The latest runtime log covers about nine minutes on vivo V2425A / iQOO Neo 10,
Android 16, SM8650/Adreno 750 with the selected patched Gen8 V38 Turnip driver.
It has no fatal exception, device loss, OOM or failed publication. A roughly
200 ms frame-interval window coincides with about 162 ms pipeline waiting.
Pipelines start at 352 and end at 397. Android thermal status reaches severe
after roughly four minutes while Thermal Guard is off. Two audio starvation
warnings appear. There are no cheat queue events in this log. The catalog has
65 entries and one persisted unlock, which does not prove a newly earned award.

The earlier 60-second capture reports 28.63 publisher FPS at a 30 cap, with
1280×576 active output despite the 720p preset name; it is not a Test 2 result.
The separate CPU scope capture is earlier than that measured interval. Window
percentiles are not averaged together and GPU queue envelopes are not treated
as shader execution cost. No usable video was present among the received files.
The shadow defect was already confirmed fixed by the user and was not reopened.

## Validation and limits

Fresh ARM64 `LibertyRecomp` and `liberty_install` runtime builds and driver proxy
build completed using NDK r29, DXC 1.9.2609 and optimizer-enabled glslang 16.6.0.
The APK packaging step compiles API 35 resources and all 45 Java sources, D8 for
API 29 minimum, stages fresh native libraries, checks their dependencies, signs
a temporary candidate and validates the final archive before/after publishing.
Validation results and hashes are recorded in the recovery checkpoint's
`build.json` and logs, including signature, manifest, CRC and ELF alignment.

Host regressions cover input/touch behavior, hold policy, stable-world cheat
policy, production texture conversions, shaders, frame statistics, memory/cache
fallbacks and achievement persistence/notifications. The NDK syntax checker
also covers seven runtime surfaces. Java fixtures cover config merges, backups,
older imports, log fallback/export and driver admission. None of these tests
substitute for Android installation, gameplay, touch reach or thermal testing.

Use [R2-DEVICE-CHECKS.md](R2-DEVICE-CHECKS.md) for the phone checklist.
Recreate previews with `python3 tools/preview_r2_ui.py --output out/r2-preview`.
Imported assets have separate Rockstar/Take-Two attribution in
`artwork/touch-icons/gta3-provenance.json` and `assets/TOUCH-NOTICE.txt`.
The supplied GTA III APK and private signing credentials are not in the source
recovery ZIP. No commit, push or public release was performed.

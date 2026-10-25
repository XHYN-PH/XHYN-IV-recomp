# XHYN IV R2 Test 3

Signed test build: `0.5.9.3-xhyn-r2-test3`, version code 31. This work retains the Test 2 icons hotfix and the supplied fan-game artwork. Texture-quality tiers and game-archive patching remain deferred.

## Requested UI refinements

- Compact shared controls remove Android's default minimum button dimensions and excess font padding; consistent corner radii, icon geometry and row spacing.
- Launcher actions use equal-width two-column rows, with an explicit 12 dp gap between installation status and Play. Achievements remain visible, with a progress card on wider screens.
- Imported drivers have a trash icon beside the selected driver and a confirmation dialog. Active-game deletion is blocked and the path is bounded to the imported-driver root.
- Settings use shorter category names, 34–36 dp navigation/value fields, roughly 42 dp rows and horizontal numeric controls. Help stays beside the options on wide screens. Settings remember category and scroll position.
- The session panel has a small header with separate Resume/Close and Quit icons, a compact information summary, a vertical icon rail, no duplicate Graphics tab, and scrollable action rows.
- Session tab and scroll position survive child dialogs. Editing a layout from settings returns to Controls settings; editing directly from the session panel returns to its selected category.
- Reveal secret achievement details uses the existing atomic cross-process preference file and survives closing/reopening. Notification and unlock persistence remain separate.
- Minimap offset defaults and the category Reset value are -6%. The one-time migration updates absent/old default values while preserving customized offsets.

## Requested features

- Optional phone gyroscope camera: Off, Aim only and Gameplay; sensitivity, smoothing and X/Y inversion. Android natural-axis rates are mapped to display rotation and integrated using sensor timestamps. No sensor permission is needed at the game sampling rate.
- Gyro samples enter a separate bounded, consume-once relative-camera buffer. Samples older than 150 ms are discarded. Input is gated by active gameplay and cleared on pause, background, editor and focus changes. This does not replace controller motion-sensor support or control vehicle steering.
- Optional FPS or Detailed overlay with four corner choices, app PSS, thermal severity and effective AA/presentation. It is non-interactive and stops while hidden, paused or backgrounded. FPS is the game publisher rate; it is not a physical-display measurement.
- Ten additional base-game single-player actions: health, armour, current vehicle repair, $10,000, invincibility on/off, and advancing to morning/noon/evening/midnight. Extra actions require registered retail handlers and active free roam. Unsupported requests fail closed.
- Health/armour actions check the resulting value where their getters are available. Other actions remain explicitly submitted/unverified. Named actions and request IDs appear in diagnostics.
- Read-only game-entry check and copyable paused-session details.

## Approved refinement items 1–11

| Item | Implementation / investigation |
| --- | --- |
| 1 — AA selection | Explicit Android AA launch choices establish the canonical policy marker. Launcher saves also write that marker, preventing old MSAA values from overriding Off. Effective AA and scene sample count appear in session metrics and settings help. Legacy desktop resolution policy is unchanged. |
| 2 — Pipeline hitches | Added a bounded pipeline lookahead on the frame recorder owner thread, which the existing assembly-side prewarming skips when recording is pipelined. Uses retained immutable commands and existing exact pipeline compilation/fallback. The scan stops at 256 commands or a 0.5 ms scan deadline; an individual driver operation is not preempted by the deadline. Can be disabled independently. Existing learned recipes, cache identity checks and demand priority remain. Device A/B measurement is required. |
| 3 — Sustained performance | Existing Thermal Guard/current-status precedence and cooldown regression coverage retained. The compact panel/HUD expose thermal severity and guard configuration. Guard and Turbo remain explicit settings; no forced device-specific defaults or unsupported new clock claims. Needs sustained phone comparison after the AA fix. |
| 4 — Pacing/resume | Display attributes are applied only when the requested mode/rate changes. Android surface rate hints allow seamless switches instead of requesting non-seamless transitions. Selected session/category state persists. The supplied swapchain interruption still needs a controlled phone reproduction. |
| 5 — Cheats | Named requests, serial IDs and limited health/armour postcondition checks; ten extra actions share the guarded game-thread queue. Dispatch is not mislabeled as a verified spawn. |
| 6 — Diagnostics | Effective setting snapshots, display-rate requests, native icon decode/fallback results, gyro availability, active AA/sample count, timestamped FPS samples and named cheat events. |
| 7 — Touch | Existing supplied glyphs, theme-matched sticks and hold-to-lock behavior retained. New phone gyro is independent. Visual reach/multitouch/editor/aim transitions need phone testing. |
| 8 — Texture reuse | Added bounded sampled reupload allocation bytes and age buckets to existing low-rate diagnostic windows. No speculative blanket retention increase; reported allocation bytes are not transfer bytes. |
| 9 — Audio | Starvation reports include active low-latency policy and window duration for correlation with renderer/thermal events. No unmeasured buffer-size or audio recompression change. Listening test required. |
| 10 — Startup/capabilities | Unsupported Android multiplayer is informational rather than a backend error; genuine failures on other platforms stay errors. Optional DLC presence is distinguished in the read-only game-entry check. Unsupported temporal modes are not offered by the Android UI. Existing cache/device/file failures are not globally suppressed. |
| 11 — Menus/achievements | Requested compact layout, launcher achievements, secret-detail preference persistence, stable navigation and original menu glyphs. Real-phone visual review remains necessary. |

## Validation status

Fresh ARM64 native and driver builds completed. All 51 Java/resource sources compiled. Nine ARM64 translation-unit checks, the production ASan/UBSan host regression suite, five Java fixture groups and seven dependency migration tests passed. APK signature, package/version, CRC, all 11 staged native libraries, 16 KiB ELF/ZIP alignment, new JNI entries and bundled artwork passed final verification. New host tests cover gyro rotations, time gaps, consume-once input, stale samples, finite bounds, pause rejection, extra action admission and canonical AA saving. Existing sanitizer and archive/driver/achievement regressions are retained.

Do not interpret source checks or a layout preview as Android gameplay, gyro direction, cheat spawn, sustained-FPS or UI appearance validation. The candidate is signed with the original release key and verified for update installation. No public push/release is authorized by this build task.

Build maintenance: aligned the reviewed LLVM libc++abi patch metadata with its already-pinned revision, verified the unchanged patch against that revision, and completed dependency setup. Release packaging excludes transient compilation files from class/resource archives.

APK SHA-256: `73d5a58f2ef49da01f6ffa47276521ddc53870210a54c15c48d6686abdfd399b`

Saved phone features restore from the native-ready callback after startup, avoiding an early onCreate read of default values. Display-rotation changes reset gyro integration and refresh overlay safe-area placement.

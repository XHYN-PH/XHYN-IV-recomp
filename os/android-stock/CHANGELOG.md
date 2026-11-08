# 0.9.24-xhyn-layer-test1 (code 443)

Android UI layer on the supplied stock `0.9.24-renderer-v442` APK. All 12
native libraries, original DEX, resources and title assets retain their exact
bytes. Earlier source-port C++ changes are not part of this APK.

- Compact XHYN home, dialogs, settings rows and session menu. Scrollable content
  and inset-aware footers keep actions above system navigation.
- D-pad/stick menu navigation, A/B actions, shoulder tabs, visible focus and
  a controller tester. Hold Start for 0.6 seconds or press Menu for Android
  session tools; tapping Start retains the game's own pause menu. Other input
  continues through stock SDL. The stock installer is retained.
- Searchable settings for 37 stock options, renderer choice and supported
  sustained-performance mode. Save/Discard/reset use a separate next-launch
  queue so game shutdown cannot overwrite pending changes. Unsupported engine
  flags from previous builds are not copied.
- Fullscreen touch editor for 11 stock contexts, selection/nudging, move/resize,
  undo/redo, compact preset and reset. Touch settings remain offline edits,
  saved for the next launch through the stock editor API.
- OS monitoring HUD for app CPU, app RAM/PSS, battery and thermal status, with
  optional readable GPU counters. Fullscreen placement supports both edges,
  undo/redo, text size and opacity. Game FPS is unavailable through this interface.
- Stock System, bundled Turnip r6 and PanVK rc4 driver choices, validated ARM64
  ZIP imports, deletion and last-launch inspection. Stock GPU admission and
  fallback remain in control; no previous patched-v38 bundle is added.
- Active portable-save path resolution, full and individual-slot backup/restore,
  deletion recovery, archive bounds, path checks and existing account progress.
  Older settings are backed up during migration; explicit save/game locations
  are preserved for the stock launch path.
- Loose data-mod admission, enable/order controls, conflict reports and
  reversible deployment into existing Xbox data paths. Original files and
  interrupted transactions retain verified recovery copies. PC plugins,
  scripts and RPF/model/texture archives are not admitted.
- Session diagnostics ZIPs with bounded startup/tail logs, device facts, stock
  settings, queued changes, Java crashes and available Android exit traces.
  Read-only content grants support sharing without exposing game files or saves.
- Read-only achievement browser with search, locked/unlocked filters and secret
  protection when an existing version-1 local catalog is readable. v442 does
  not expose the previous catalog generator; no unlock hook or live catalog
  updates are claimed.

Host tests cover Android 10/15 view layouts, navigation, queue persistence,
archive rejection, save isolation/recovery, mod rollback/conflicts and packaging
boundaries. Game stability, stock native rendering, real controller hardware,
driver behavior and native touch geometry still require phone validation.

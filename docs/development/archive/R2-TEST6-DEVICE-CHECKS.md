# XHYN IV Test6 phone checklist

Install over the existing XHYN IV app. Back up saves before testing.
Validate one section at a time. Export the latest log before starting another game session, because previous full logs are automatically cleared.

1. Start with the previously working driver. Confirm installation status and existing save mission/completion values.
2. Back up one save slot, restore it, and confirm the other slots remain intact. Load the restored save in the game.
3. Import a control context. Verify other contexts survive; check Cancel/Undo/Save and the gameplay/editor geometry. Toggle the optional glyph outline.
4. Check the Compact menu (performance left, actions right), achievement/cheat filters, HUD metric visibility and bottom/custom placement.
5. Import a driver while the game is stopped; restart and confirm the actual driver. Reimport the same package and verify selection/options persist. Try ESMERINGHOY only on admitted Adreno 7xx/8xx devices; record visual issues and the actual driver.
6. Enable one compatible loose-data mod, inspect its mount/conflict report, then verify disabling it. PC DLL/ASI/EXE code plugins are unsupported.
7. Start Diagnostics input capture, return to gameplay and test unarmed LMB, RMB+LMB, movement/look and menu return. Capture stops after 30 seconds or when backgrounded.
8. Check controller reconnect/player ownership, contextual actions, GPS/waypoints and map alignment at the chosen resolution/upscaler.
9. Observe preparation outcomes and benchmark/session summaries. Compare the same save, route, settings and cache condition; avoid overlapping heavy captures.
10. For sustained testing, record phone, Android, driver, settings, temperatures and elapsed time. Attach the log plus a screenshot/video for visual issues.

Build checks do not establish phone stability or an FPS gain. Missing shaderInt64 support, complete depth-clamp/clip-plane emulation, exact V38 reproduction, driver tuning and remaining input/save identity investigations are still open. Multiplayer and direct SAF streaming remain deferred.

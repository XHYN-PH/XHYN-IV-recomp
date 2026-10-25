# R2 Test 3 — phone checks

Install the signed APK over the current icons build. The package ID and signing certificate match; version code is 31.

1. Launcher: Controls, Drivers, Achievements and More align with Settings/Game files. The installed-status label has space above Play. Achievements are visible without horizontal scrolling.
2. Settings: compare row density with the supplied screenshots. Test all categories, search, numeric entry, Save, Discard and Android Back. Focused category labels should stay readable with a controller.
3. Session: check the compact header and icon rail. Close resumes; the separate exit icon asks before quitting. There is no duplicate Graphics tab.
4. Return navigation: open Controls, edit a layout, finish, and confirm the same category returns. Repeat through Controls settings and the Xbox editor.
5. Achievements: toggle Reveal secret details, close Options, close Achievements, reopen, and check it is retained. Check Locked/Unlocked/All, images, details and a new legitimate unlock. Earlier saved unlocks should remain visible.
6. Drivers: select an imported driver, cancel its trash dialog, then delete an expendable imported driver. Bundled/system choices have no delete button. Do not remove the only driver you intend to test with.
7. AA: select Off, save and restart. Session details should show off and one scene sample. Test FXAA and SMAA separately; these should not reactivate old 4× MSAA.
8. Gyro: enable Aim only, test both landscape orientations, horizontal/vertical direction and sensitivity. Test held and latched aim. Verify menus, loading, editor, background/resume and death do not create a catch-up camera movement. Test Gameplay mode separately and turn Off to compare touch-only input.
9. Overlay: enable FPS, then Detailed. Check selected corners, touch pass-through, readability, pause/background hiding and recovery. It reports publisher FPS, not physical-display delivery.
10. Cheats: record which effects actually occur. Test weapons, an original bike and car spawn, health/armour, current vehicle repair, money and invincibility on/off. Extra actions are for active base-game free roam; turning invincibility off is also allowed during active missions. Submitted does not confirm an effect.
11. Minimap: verify the -6% default/reset value, map/blips alignment and safe-area clipping. Existing non-default custom offsets are preserved.
12. Performance: keep driver, cap, route and other settings consistent. Allow caches to warm, then repeat the same route. Compare Pipeline lookahead on/off and Thermal Guard on/off over 10–15 minute sessions with similar starting thermal state. Capture short detailed timings separately from the timed benchmark.
13. Audio: note audible dropouts and their times. Reports now include the low-latency setting and interval length; no new buffer-size claim is made.

Useful report: a short UI/gyro clip, specific cheat outcomes, and a fresh Play with logs session. Use the in-game Diagnostics actions for a benchmark/short renderer capture. Texture tiers and game-data patching remain deferred.

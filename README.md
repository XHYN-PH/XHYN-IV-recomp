<p align="center">
  <img src="os/android/app/src/main/res/drawable-nodpi/launcher_iv.png" width="260" alt="XHYN IV logo">
</p>

<h1 align="center">XHYN IV</h1>

<p align="center">Grand Theft Auto IV on Android through static recompilation.<br>An XHYN fork of Liberty Recompiled for Android.</p>

<p align="center">
  <a href="https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test7">Download Test7 APK</a> ·
  <a href="https://youtube.com/@xhyn_ph">YouTube</a> ·
  <a href="https://t.me/xhynports">Telegram</a>
</p>

**Current testing version: 0.5.9.3-xhyn-r2-test7.** Performance still needs work, and improvements will continue through future updates.

**You must provide your own legally obtained Xbox 360 GTA IV USA game files and Title Update 8 (0.0.8.5).** The game, disc image and title update are not included.

## Features

- Refined launcher and compact in-game menu with the transparent IV logo and gameplay details above Resume.
- Mobile and Xbox touch layouts, physical controller support, phone gyro camera and mobile hold-to-lock aiming.
- Full-screen controls editor with context previews, snapping, Undo/Redo, touch-area guides and layout import/export.
- Adjustable resolution, graphics, FPS, camera sensitivity, control opacity and phone rumble on supported devices.
- Movable minimap with nearby help text following its position.
- Searchable local achievements and single-player cheats, achievement details and catalog preparation.
- Individual save-slot export, import and deletion, with protective backups.
- Compatible loose-data mods with enable/disable, priority ordering and conflict information.
- Configurable FPS/performance overlay, pipeline preparation and troubleshooting reports.
- Seven bundled driver choices, including experimental **ESMERINGHOY 27.0.0-devel (A7XX/A8XX)**, plus driver import and recovery.
- Separate live/restart settings, saved pending changes and stable return navigation.

Read the [full r2 changelog](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test7) for all accumulated changes since r1.

## Requirements

| Item | Requirement |
|---|---|
| Android | Android 10 or newer, 64-bit |
| CPU | ARM64; a capable gaming phone or handheld is recommended |
| Graphics | Vulkan 1.2 with the features required by the renderer |
| Memory | 8 GB RAM recommended |
| Storage | Around 15 GB free during installation, including room for your disc image |
| Game | Xbox 360 GTA IV USA and Title Update 8, version 0.0.8.5 |

Compatibility depends on your phone, Android version and graphics driver. Use the driver choices admitted for your device.

| GPU family | Driver options |
|---|---|
| Adreno 6xx | Compatible Turnip or system/imported driver |
| Adreno 7xx / 8xx | Compatible bundled candidates, including experimental ESMERINGHOY, or system/imported driver |
| Mali / Immortalis | System driver; PanVK is experimental on supported Android 15+ devices |
| Xclipse | System driver; RADV is experimental on supported Android 14+ devices |

See [driver compatibility and credits](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/os/android/native/DRIVERS.md) for details.

## Getting started

1. Download **XHYN-IV-0.5.9.3-xhyn-r2-test7.apk** from the [Test7 release](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test7).
2. Install it, open **Game files**, select your GTA IV disc image and Title Update 8, then press **Install**.
3. Wait until the game is ready, then press **Play**.

Updating XHYN IV? Install over the existing app. Export saves first; do not uninstall just to update.

The app uses `com.xhyn.ivrecomp`. The original Liberty Recompiled app has separate game and save data.

## Controls and settings

Open **Settings → Controls** to adjust sensitivity, gyro and opacity, or edit the mobile and Xbox layouts. The in-game menu also has an **Edit controls** shortcut.

Open **Settings → Graphics & performance** for resolution and rendering options. Lower resolution first if the game is too slow. Save changes when finished; settings that require a restart remain pending until the next launch.

Use **Game files → Save data** to export, import or delete saves. Quit gameplay before managing saves and keep a backup. Data mods accept compatible loose Xbox game files; PC DLL/ASI/EXE plugins are unsupported.

## Reporting a problem

**No logs = no improvements.**

1. Open **Settings → Logs → Play with logs**, or use the three dots beside Play.
2. Reproduce the problem, then return to the launcher.
3. Choose **Share latest log** and send the ZIP in [Telegram](https://t.me/xhynports).

Include your phone model, Android version, actual graphics driver, settings and what happened. Add a screenshot or short video for visual issues.

Launcher failures can be shared from **Logs** without starting a game. Unsaved settings, driver options and control layouts offer Save, Discard or Keep editing.

**Previous full logs are cleared automatically when a new session starts. Export the latest report before playing again.** Normal **Play** leaves diagnostic logging off.

## More information

[Installation](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/docs/android/INSTALL.md) · [Build Test7 from source](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/docs/android/BUILDING.md) · [Mod support](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/docs/MOD_SUPPORT.md) · [Original project README](README_UPSTREAM.md)

## Credits

- [vaduur/LibertyRecompAndroid](https://github.com/vaduur/LibertyRecompAndroid) for the Android base.
- [OZORDI/LibertyRecomp](https://github.com/OZORDI/LibertyRecomp) and [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) for the recompilation and runtime.
- [prollynotzjay/LibertyRecompAndroidMaliVer](https://github.com/prollynotzjay/LibertyRecompAndroidMaliVer) for the adapted mobile touch layout and icons. See [touch attribution](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/os/android/app/src/main/assets/TOUCH-NOTICE.txt) for imported artwork.
- SDL, Mesa, libadrenotools and the other upstream contributors. Driver sources and licenses are listed in [DRIVERS.md](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test7/os/android/native/DRIVERS.md).
- XHYN for this fork's Android refinements, interface and release maintenance.

Source is distributed under [GNU GPL v3](COPYING), with third-party components retaining their own licenses and notices. [Artwork and trademarks](docs/android/ARTWORK.md) remain with their respective owners. This unofficial project is not affiliated with Rockstar Games or Take-Two Interactive.

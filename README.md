<p align="center">
  <img src="os/android/app/src/main/res/drawable-nodpi/launcher_iv.png" width="260" alt="XHYN IV logo">
</p>

<h1 align="center">XHYN IV</h1>

<p align="center">Grand Theft Auto IV on Android through static recompilation.<br>An XHYN fork of Liberty Recompiled for Android.</p>

<p align="center">
  <a href="https://github.com/XHYN-PH/XHYN-IV-recomp/releases">Download APK</a> ·
  <a href="https://youtube.com/@xhyn_ph">YouTube</a> ·
  <a href="https://t.me/xhynports">Telegram</a>
</p>

**Current testing version: 0.5.9.3-xhyn-r1.** Performance is still not that good, and work to improve it will continue through future updates. Expect frame drops, occasional crashes and graphics issues while testing.

**You must provide your own legally obtained Xbox 360 game files and Title Update 8.** The game, disc image and title update are not included or provided here.

## Features

- GTA mobile-style touch controls, an Xbox-style on-screen controller and support for physical controllers.
- Fullscreen control editors in the launcher, plus live mobile layout editing while playing.
- Adjustable resolution, graphics settings, camera sensitivity and control opacity.
- Movable minimap, with nearby help text following its position.
- Adjustable phone rumble on supported devices.
- Save export, import and deletion from the launcher.
- Optional graphics drivers and simple log exports for reporting problems.

## Requirements

| Item | Requirement |
|---|---|
| Android | Android 10 or newer, 64-bit |
| CPU | ARM64; a capable gaming phone or handheld is recommended |
| Graphics | Vulkan 1.2 with the features required by the renderer |
| Memory | 8 GB RAM recommended; lower-memory devices may struggle |
| Storage | Around 15 GB free during installation, including room for your disc image |
| Game | Xbox 360 GTA IV USA release and Title Update 8, version 0.0.8.5 |

Support depends on the phone, Android version and driver. A supported GPU name does not guarantee that every game scene will render correctly or run smoothly.

| GPU family | Driver options |
|---|---|
| Adreno 6xx / 7xx | Bundled Turnip or a compatible imported driver |
| Newer Adreno | A compatible imported driver or the system driver |
| Mali / Immortalis | System driver; bundled PanVK is experimental on supported Android 15+ devices |
| Xclipse | System driver; bundled RADV is experimental on supported Android 14+ devices |

See [driver compatibility and credits](os/android/native/DRIVERS.md) before trying an experimental driver. These drivers cannot add missing kernel support to a phone.

## Getting started

1. Download and install the APK from [Releases](https://github.com/XHYN-PH/XHYN-IV-recomp/releases).
2. Open **Game files**, select your own GTA IV disc image and Title Update 8, then press **Install**.
3. Wait until the game is ready, then press **Play**.

The XHYN app uses `com.xhyn.ivrecomp`. An installation from the original Liberty Recompiled app has separate game and save data. Export your saves before changing apps or uninstalling.

## Controls and settings

Open **Settings → Controls** to adjust movement, camera, sensitivity and opacity, or edit the mobile and Xbox layouts. While playing, use the small controller button to switch on-screen control styles. The in-game menu has an **Edit controls** shortcut.

Graphics options are under **Settings → Graphics & performance**. Each option has a short explanation. Lower resolution first if the game is too slow; higher settings can increase heat and memory use. Some changes require restarting the game.

Use **Game files → Save data** to export, import or delete saves. Quit the game before managing saves, and keep an exported backup.

## Reporting a problem

**No logs = no improvements.** A log helps identify what went wrong on your device.

1. Open **Settings → Logs & troubleshooting → Play with logs**.
2. Reproduce the problem, then return to the launcher.
3. Choose **Share latest log** and attach the ZIP to the testing PR or send it in [Telegram](https://t.me/xhynports).

Include your phone model, Android version, chosen graphics driver, settings and what happened. Add a screenshot or short video for shadows, lighting or other visual problems. Old logs are cleared when a new session starts, so export the report before playing again. Normal **Play** leaves diagnostic logging off.

## More information

[Installation](docs/android/INSTALL.md) · [Build from source](docs/android/BUILDING.md) · [Mod support](docs/MOD_SUPPORT.md) · [Original project README](README_UPSTREAM.md)

## Credits

- [vaduur/LibertyRecompAndroid](https://github.com/vaduur/LibertyRecompAndroid) for the Android base.
- [OZORDI/LibertyRecomp](https://github.com/OZORDI/LibertyRecomp) and [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) for the recompilation and runtime.
- [prollynotzjay/LibertyRecompAndroidMaliVer](https://github.com/prollynotzjay/LibertyRecompAndroidMaliVer) for the mobile touch layout and icons adapted by this fork. See [touch credits](os/android/app/src/main/assets/TOUCH-NOTICE.txt).
- SDL, Mesa, libadrenotools and the other upstream contributors. Driver sources and licenses are listed in [DRIVERS.md](os/android/native/DRIVERS.md).
- XHYN for this fork's Android refinements, interface and release maintenance.

Source is distributed under [GNU GPL v3](COPYING), with third-party components retaining their own licenses and notices. [Artwork and trademarks](docs/android/ARTWORK.md) remain with their respective owners. This unofficial project is not affiliated with Rockstar Games or Take-Two Interactive.

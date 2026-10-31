<p align="center">
  <img src="os/android/app/src/main/res/drawable-nodpi/launcher_iv.png" width="260" alt="XHYN IV logo">
</p>

<h1 align="center">XHYN IV</h1>

<p align="center">Grand Theft Auto IV for Android.</p>

<p align="center">
  <a href="https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test12/XHYN-IV-0.5.9.3-xhyn-r2-test12.apk"><strong>Download APK</strong></a> ·
  <a href="https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test12">What's new</a> ·
  <a href="https://t.me/xhynports">Telegram</a> ·
  <a href="https://youtube.com/@xhyn_ph">YouTube</a>
</p>

**Current version: Test12 — 0.5.9.3-xhyn-r2-test12.** This is a testing release. Performance still needs work, and improvements will continue through future updates.

**Bring your own Xbox 360 GTA IV USA disc image and Title Update 8 (0.0.8.5).** Game files and the update are not included.

## How this fork differs from the original

XHYN IV is based on [vaduur's LibertyRecompAndroid](https://github.com/vaduur/LibertyRecompAndroid), which brings [Liberty Recompiled](https://github.com/OZORDI/LibertyRecomp) to Android. The original already provides the Android game runtime, installation, touch/controller input, driver import, and performance work.

This fork focuses on making more of the experience adjustable from your phone:

- Redesigned launcher and in-game menus, explained settings, and controller navigation.
- Separate editable mobile/Xbox layouts, gyro options, controller tuning, and a fullscreen FPS-overlay editor.
- More bundled driver choices, including ESMERINGHOY, plus graphics and heat-management options.
- Save backups, individual save-slot tools, achievement/cheat menus, and a data-file mod manager.
- One shareable problem report, with optional detailed captures.

**More options do not guarantee higher FPS or lower heat.** Device and driver compatibility still vary. The two apps keep separate saves and settings.

[Compare both versions and understand the choices](docs/FORK-DIFFERENCES.md).

## Get started

1. [Download the APK](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test12/XHYN-IV-0.5.9.3-xhyn-r2-test12.apk) and install it on your phone.
2. Open **Game files**, choose your GTA IV disc image and Title Update 8, then press **Install**.
3. Wait for installation to finish, return to the main menu, and press **Play**.

Updating? Install over your existing XHYN IV app. Back up your saves first; uninstalling removes app data.

Need more help? Read the [installation guide](docs/android/INSTALL.md) or [troubleshooting guide](docs/android/TROUBLESHOOTING.md).

You can adjust physical controllers in **Settings → Controller**, and position the performance display in **Settings → Overlay → Edit overlay**.

## What you need

| Item | Requirement |
| --- | --- |
| Phone | Android 10 or newer, with a 64-bit ARM processor |
| Graphics | Vulkan 1.2 support; compatibility varies by GPU and driver |
| Memory | 8 GB RAM recommended |
| Free space | Around 15 GB during installation |
| Game files | Xbox 360 GTA IV USA disc image and Title Update 8, version 0.0.8.5 |

Performance depends on your phone, temperature, graphics settings, and driver.

## Features

- Customizable mobile and Xbox touch layouts, plus physical controller support.
- Gyro camera controls, adjustable sensitivity, button opacity, and phone vibration.
- In-game menu with quick access to controls, settings, achievements, and cheats.
- Graphics and FPS options, a movable minimap, and an adjustable performance overlay.
- Save backups, individual save-slot management, and compatible [data-file mods](docs/MOD_SUPPORT.md).
- Built-in driver choices, including **ESMERINGHOY** for compatible Adreno 7xx/8xx devices, plus driver import.

See the [release notes](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/tag/0.5.9.3-xhyn-r2-test12) for the full list of changes.

## Find your settings

| What you want to change | Where to go |
| --- | --- |
| Button positions, size, or opacity | **Touch controls** on the main menu |
| Resolution and anti-aliasing | **Settings → Video** |
| Shadows and reflections | **Settings → Graphics** |
| FPS limit and performance options | **Settings → Performance** |
| FPS display and overlay position | **Settings → Overlay → Edit overlay** |
| Physical controller tuning and test | **Settings → Controller** |
| Graphics driver | **Drivers** on the main menu, before starting the game |
| Back up or restore saves | **Game files → Save data** |

Save your changes before leaving. Some settings take effect after restarting the game.

For drivers, start with **Automatic**. If a selected driver causes problems, try **System**. Use drivers intended for your phone's GPU; experimental options may not work on every device.

## Having a problem?

Check [troubleshooting](docs/android/TROUBLESHOOTING.md) first. To send a report:

1. Open the **three dots beside Play**, or **Settings → Logs**.
2. Choose **Play with logs** and reproduce the problem.
3. Return to the launcher, choose **Share report**, and send the ZIP in [Telegram](https://t.me/xhynports).

Include your phone model, Android version, selected driver, and what happened. Screenshots or a short video help with visual issues. If Play will not open, share any available launcher report from **Logs**.

**Share the report before starting another game session: previous full logs are cleared automatically.** Normal **Play** has logging off.

**No logs = no improvements.**

## Credits

- [vaduur/LibertyRecompAndroid](https://github.com/vaduur/LibertyRecompAndroid) for the Android base.
- [OZORDI/LibertyRecomp](https://github.com/OZORDI/LibertyRecomp) and [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk) for the underlying project and runtime.
- [prollynotzjay/LibertyRecompAndroidMaliVer](https://github.com/prollynotzjay/LibertyRecompAndroidMaliVer) for the adapted mobile touch layout and icons. See [touch artwork credits](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test12/os/android/app/src/main/assets/TOUCH-NOTICE.txt).
- SDL, Mesa, libadrenotools, and other upstream contributors. See [driver sources and licenses](https://github.com/XHYN-PH/XHYN-IV-recomp/blob/xhyn/r2-test12/os/android/native/DRIVERS.md).
- **XHYN-PH** for this fork's Android refinements, interface, and releases.

[Source code](https://github.com/XHYN-PH/XHYN-IV-recomp/tree/xhyn/r2-test12) · [Original project README](README_UPSTREAM.md) · [Artwork notices](docs/android/ARTWORK.md)

Source is distributed under [GNU GPL v3](COPYING). Third-party components keep their own licenses and credits. Artwork and trademarks belong to their respective owners. This unofficial project is not affiliated with Rockstar Games or Take-Two Interactive.

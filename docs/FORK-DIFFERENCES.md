# XHYN IV and the original Android port

XHYN IV builds on [vaduur's LibertyRecompAndroid](https://github.com/vaduur/LibertyRecompAndroid). That Android port is based on [OZORDI's Liberty Recompiled](https://github.com/OZORDI/LibertyRecomp) and the [ReXGlue SDK](https://github.com/rexglue/rexglue-sdk).

The original projects provide the game recompilation, Android platform support, Vulkan rendering, installation, input, audio, and save foundations. XHYN IV adds and refines phone controls, menus, configuration, and troubleshooting on top of that work. It also includes adapted touch work from the [Mali fork](https://github.com/prollynotzjay/LibertyRecompAndroidMaliVer); artwork and driver credits remain with their contributors.

## What changes in XHYN IV?

This comparison describes **XHYN IV r2 Test8**. Both projects continue to change.

| Area | Original Android port | XHYN IV |
| --- | --- | --- |
| Menus and settings | Launcher, driver controls, and game menus; advanced options also use configuration files. | Redesigned launcher and pause menu, categorized settings, search, explanations, inline On/Off choices, and Save/Discard prompts. More adjustments can be made inside the app. |
| Touch controls | On-screen Xbox controls and a context-sensitive layout. | Separate customizable mobile and Xbox layouts, a fullscreen editor, Undo/Redo, import/export, placement guides, gyro camera options, and a walking/vehicle switch delay. |
| Physical controllers | Gamepad input through Android and SDL. | Menu navigation and focus, deadzones, stick sensitivity, response curve, inversion, trigger threshold, vibration strength, and an input preview. |
| Drivers | Bundled Turnip, imported drivers, and System fallback. | Seven bundled choices, including ESMERINGHOY for compatible Adreno devices, compatibility labels, per-driver options, and more cache controls. Extra choices are options to try, not a guarantee of support. |
| Graphics and performance | Android Vulkan rendering, graphics settings, and existing Android performance work. | More settings in phone menus, half/quarter reflection sizes, one to five graphics preparation workers, a Balanced shortcut, and further cache, preparation, and audio changes. |
| Saves and game tools | Persistent game saves and the underlying game systems. | Full save/achievement backups, individual save-slot management, available save-progress information, and phone menus for local achievements and single-player cheats. |
| Data-file mods | No dedicated data-file mod manager in the Android launcher. | Enable/disable compatible loose Xbox data files, set their priority, inspect conflicts, and read rejection or last-launch messages. |
| Problem reports | Debug launch and Android crash reports. | One shareable ZIP from Logs or the in-game Diagnostics menu, optional input/graphics captures, and launcher reporting when the game cannot start. |

## Why do some choices work this way?

- **Two touch layouts:** mobile controls can change with gameplay context; the Xbox layout keeps a familiar controller shape. Their saved positions remain independent.
- **Separate overlay editor:** move the FPS display directly on a fullscreen preview. Its tools can move or collapse so they do not cover the area being edited.
- **Save and restart notices:** some changes can apply during play; others need the graphics or game systems to start again. The app saves those for the next launch.
- **Optional performance controls:** more graphics workers can prepare several effects together but use more CPU and heat. Lower reflections reduce detail. Balanced enables heat-management options rather than promising a particular FPS.
- **Requested versus active VSync:** a phone or driver can use a supported fallback even when Off is requested. The in-game setting shows the active mode.
- **Shadow distance starts at 1×:** the existing shadow guard prevents shorter ranges from breaking nearby character and vehicle shadows. Use shadow detail or sun-shadow controls to reduce their cost.
- **Logs are optional:** normal Play keeps full recording off. Use Play with logs when reporting a problem; detailed diagnostics can add overhead. Export the report before another game, because the next launch replaces previous full logs.
- **Mod limits:** the manager accepts supported loose Xbox data files. It does not load PC DLL/ASI/EXE plugins, scripts, or model/texture archives. See [mod help](MOD_SUPPORT.md).

## Which version should I choose?

Choose **XHYN IV** if you want its phone menus, editable controls, save tools, bundled driver choices, and experimental tuning options. Choose the **original Android port** if you prefer the original maintainer's release line and configuration workflow. Follow each project's own release notes and device requirements.

Neither choice guarantees better performance on every phone. Results depend on the game scene, settings, driver, and temperature. XHYN IV's added options do not establish an FPS advantage or lower heat compared with the original. Optional Mali, Immortalis, and Xclipse drivers remain experimental; their presence in the menu does not mean every device can run them.

Both use the Xbox 360 game and require your own matching game files and title update. This fork does not include the game or turn PC mods into compatible Xbox mods.

## Switching between the apps

The original uses `com.libertyrecomp`; XHYN IV uses `com.xhyn.ivrecomp`. They can be installed alongside each other and keep separate game data, saves, and settings. Installing XHYN IV does not automatically move progress from the original.

Keep a backup before moving save data, and keep the original installation until you have confirmed your progress in the other app. Updating an older XHYN IV installation with a newer XHYN IV APK is different from switching between the two projects. **Uninstalling removes that app's data.**

For the current XHYN IV release, see [installation](android/INSTALL.md), [settings and help](README.md), and the [full r2 changelog](android/R2-CHANGELOG.md).

Comparison checked on 25 October 2026 against the original Android port's [README](https://github.com/vaduur/LibertyRecompAndroid/blob/bc5e88607636a96f7c4ab6244b6f96e215fc384d/README.md), [configuration guide](https://github.com/vaduur/LibertyRecompAndroid/blob/bc5e88607636a96f7c4ab6244b6f96e215fc384d/docs/android/ADVANCED.md), and launcher source from the same revision.

[Back to the main README](../README.md)

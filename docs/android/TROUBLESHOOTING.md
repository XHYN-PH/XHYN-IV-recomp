# Troubleshooting XHYN IV

XHYN IV is a testing release. Performance and compatibility depend on your phone, driver, and settings.

## Installation does not finish

- Check that you selected the **Xbox 360 GTA IV USA** disc image and **Title Update 8 (0.0.8.5)**.
- Make sure there is enough free space; allow around **15 GB during installation**.
- Keep the app open while it installs and grant file access when Android asks.
- Note the error shown in the app and include it when asking for help.

See the [installation guide](INSTALL.md) for the complete steps.

## The game crashes, stays black, or has graphics problems

1. Close the game and return to the launcher.
2. Open **Drivers**. Try **Automatic**, or **System** if a custom driver is causing problems.
3. If you changed graphics settings, try a lower resolution in **Settings → Video** and save your changes.
4. If the problem continues, use **Play with logs** and share a report as described below.

Use drivers made for your phone's GPU. An experimental driver may work differently across devices.

## The game is slow or stutters

Lower the resolution in **Settings → Video**. Shadows and reflections are under **Settings → Graphics**; FPS limits are under **Settings → Performance**.

The **Balanced** shortcut in Performance turns on Thermal Guard and requests a lower compatible screen refresh rate. Graphics compiler workers can be set from one to five; more workers may reduce preparation time but increase CPU use and heat. Start with one or two.

Try one change at a time so you can see whether it helps. Performance can drop as the phone heats up. A higher FPS limit does not guarantee a higher frame rate.

## Buttons are misplaced or hard to use

Open **Touch controls**, select the layout you use, and adjust the buttons. Mobile and Xbox layouts are separate. Save before leaving the editor.

You can also open **Edit controls** from the in-game menu. Include a screenshot of both the editor and gameplay when reporting a layout problem.

## Android refuses an APK update

Use an APK from this repository's [releases](https://github.com/XHYN-PH/XHYN-IV-recomp/releases). Keep the current app installed while you export your saves through **Game files → Save data**.

**Uninstalling removes app data.** Include Android's exact error message when asking for help.

## Send a useful report

1. Open the **three dots beside Play**, or **Settings → Logs**.
2. Choose **Play with logs** and reproduce the problem.
3. Choose **Share report** from Logs in the launcher, or from **Diagnostics** in the in-game menu.
4. Send the ZIP in [Telegram](https://t.me/xhynports), with your phone model, Android version, selected driver, settings, and what you were doing.

Use **Play with detailed diagnostics** beside Play with logs if a graphics capture is needed. All completed captures go into the same ZIP. Detailed captures can temporarily slow the game.

A screenshot or short video helps with visual problems. If Play will not open, share any available launcher report from **Logs**.

**Share the report before starting another session. Previous full logs are cleared automatically.** Normal Play has logging off.

**No logs = no improvements.**

[Back to help](../README.md) · [Back to the README](../../README.md)

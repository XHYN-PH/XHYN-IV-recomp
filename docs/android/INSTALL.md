# Install and update XHYN IV

## Before you start

You need a 64-bit Android 10+ phone with compatible Vulkan 1.2 graphics, around 15 GB free during installation, and your own:

- **Xbox 360 GTA IV USA disc image** (`.iso`).
- **Title Update 8**, version **0.0.8.5**, for that game.

Game files and title updates are not included. Other regions and update versions are not supported by this build.

## Install and play

1. [Download the Test8 APK](https://github.com/XHYN-PH/XHYN-IV-recomp/releases/download/0.5.9.3-xhyn-r2-test8/XHYN-IV-0.5.9.3-xhyn-r2-test8.apk).
2. Open the APK and install it. If Android asks, allow your browser or file manager to install this APK.
3. Open XHYN IV and choose **Game files**.
4. Select the GTA IV disc image and Title Update 8. Allow file access when asked.
5. Press **Install** and keep the app open until it finishes.
6. Return to the main menu and press **Play**.

Keep your original game files until installation has finished successfully. Optional episodes need their own matching game files and remain experimental.

## Choose a driver

Open **Drivers** on the main menu before starting the game.

Start with **Automatic**. If you get a crash or graphics problems with a chosen driver, try **System**. Adreno phones may also have compatible Turnip or ESMERINGHOY choices. Mali, Immortalis, and Xclipse phones should start with System; their optional drivers are experimental.

A driver must match your phone's GPU. The available choices and results can vary by device and Android version.

## Adjust your controls

Open **Touch controls** on the main menu. Choose the mobile or Xbox layout, then edit the buttons' positions, size, and opacity. Save the layout when finished.

While playing, open the in-game menu for **Touch controls**, **Controller**, or **Edit controls**. Mobile and Xbox layouts are saved separately. A physical controller can also be used. Open **Settings → Controller** for deadzones, sensitivity, inversion, trigger threshold, vibration strength, and the input test.

For resolution, open **Settings → Video**. For shadows and reflections, use **Settings → Graphics**. FPS limits are under **Settings → Performance**. Open **Settings → Overlay → Edit overlay** to move the FPS display directly. Drag the sample; the tools can also be moved or collapsed. Save the editor, then save Settings.

## Update the app

Install the newer APK over your existing XHYN IV app. Back up your saves first. **Uninstalling removes app data.**

If Android refuses an update, keep your current installation and back up your saves before trying to replace it. Use APKs from this repository's [releases](https://github.com/XHYN-PH/XHYN-IV-recomp/releases).

## Back up and restore saves

Quit the game, then open **Game files → Save data**.

- **Export all saves & achievements** creates a ZIP backup that you can keep outside the app.
- **Import full backup** restores a previously exported backup.
- **Manage individual slots** lets you back up, restore, or delete one save slot.

Keep a separate copy of important backups. Saves from the original Liberty Recompiled app are stored separately from XHYN IV.

## Need help?

Read [troubleshooting](TROUBLESHOOTING.md), or ask in [Telegram](https://t.me/xhynports). Include your phone model, Android version, selected driver, and a screenshot of any error.

[Back to the README](../../README.md)

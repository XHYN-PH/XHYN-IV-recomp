# Installing XHYN IV

Download the testing APK from [Releases](https://github.com/XHYN-PH/XHYN-IV-recomp/releases) and install it on a 64-bit Android 10+ device. Android may ask you to allow installation from the browser or file manager you are using.

## Your game files

You need your own Xbox 360 GTA IV USA disc image (`.iso`) and matching Title Update 8, version **0.0.8.5**. The update can be its STFS package or an extracted `default.xexp`. Other regions and update versions are not interchangeable. No game files or download links are provided by this project.

1. Open **Game files** in the launcher.
2. Select your disc image and title update. Grant file access if Android asks for it.
3. Press **Install**, leave the app open and wait for it to finish.
4. Return to the main screen and press **Play**.

Allow around 15 GB free during installation. Keep your original files until installation succeeds. Optional episodes need their own matching game files and remain experimental.

## Choosing a graphics driver

Open **Settings → Graphics driver** before starting the game. Try the automatic selection first. Bundled Turnip targets supported Adreno 6xx/7xx GPUs; PanVK and Xclipse RADV are experimental, with Android and kernel requirements. See [driver compatibility](../../os/android/native/DRIVERS.md). A driver for one GPU family cannot run on another.

## Controls

Open **Settings → Controls** to edit the mobile or Xbox on-screen layout. While playing, the small controller button switches styles, and the in-game menu gives access to graphics, controls and quitting. A physical controller can also be used.

## Updating and saves

Install a newer official XHYN APK over the existing one to retain the app's data when its signing key matches. If Android refuses the update, export saves before changing or removing the existing app. Uninstalling removes app data.

**Game files → Save data** provides export, import and deletion. Quit the game first. Import keeps a local backup of the previous saves, but you should also keep your own exported copy.

XHYN IV uses package `com.xhyn.ivrecomp`, separate from the original `com.libertyrecomp` app. Its usual external app folder is `Android/data/com.xhyn.ivrecomp/files/`; use the app's menus to manage files where possible.

## Reporting problems

Use **Settings → Logs & troubleshooting → Play with logs**, reproduce the issue, then choose **Share latest log**. Export before starting another session, because old logs are cleared. Attach the ZIP, phone model, Android version and driver when reporting the issue in the testing PR or [Telegram](https://t.me/xhynports). Visual problems also need a screenshot or short video.

Normal **Play** has diagnostic logging off. Advanced diagnostics add overhead and should be used when a more detailed report is requested.

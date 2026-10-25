# Use data-file mods

XHYN IV supports compatible loose Xbox 360 data files, such as handling or weather settings. PC DLL/ASI/EXE plugins, scripts, and model or texture archives are not supported by this mod menu.

## Add a mod

Quit the game first. Inside the installation's **mods** folder, give each mod its own folder. Keep the game's original file paths under **game** or **update**:

```text
mods/MyHandling/game/common/data/handling.dat
mods/MyWeather/update/common/data/timecyc.dat
```

Files under **update** need the matching title update to be installed.

Supported file types are `.dat`, `.xml`, `.ide`, `.ipl`, `.txt`, `.meta`, and `.gxt`. Put a mod's instructions or readme outside its mod folder.

## Enable it

1. Open **Game files → Data-file mods**.
2. Turn on the main mod switch and enable the mods you want.
3. Adjust **Priority** if needed.
4. Press **Save for next launch**, then start the game.

When two mods replace the same file, the one with the higher priority number wins. The menu shows conflicts and which mod takes priority.

## If a mod does not work

Check its message in the mod menu and the **Last launch** result. If it causes a crash or unwanted changes, quit the game, disable it, save, and launch again.

Mods must use paths and file contents that match the Xbox 360 version. A PC mod is not automatically compatible. Try one mod at a time and keep a save backup.

The mod menu loads files over the installed game without replacing the original game files on disk.

[Back to help](README.md)

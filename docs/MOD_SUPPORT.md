# Android data-file mods

Open **Game files → Data-file mods** while the game is stopped. The global switch enables the feature; each mod has its own enabled switch and priority. Press **Save for next launch**. Scanning and saving run outside the UI thread.

Place compatible Xbox 360 loose data under the installation's mods folder:

```text
mods/MyHandling/game/common/data/handling.dat
mods/MyWeather/update/common/data/timecyc.dat
```

Use the exact path expected by your game data. The update example requires installed title-update data. A mod containing update files is rejected as a whole if that mount is unavailable.

Supported loose extensions: .dat, .xml, .ide, .ipl, .txt, .meta, .gxt. Each folder is limited to 8192 files, 256 MiB and 25 relative path components including game/update. Symlinks, nonregular files, duplicate paths ignoring ASCII case, unsupported files and content outside game/update are rejected. Put instructions/readmes outside the mod folder. The manager owns xhyn-mod.cfg and its temporary save files at each mod's root.

Higher priority loads later and wins. Priorities range from -1000 to 1000; ties use unsigned UTF-8 folder-name order. The manager lists conflicts and their winners. Enabled, admitted mods contribute to active conflicts; disabled or rejected folders do not.

A bad folder is rejected independently. If a mount fails partway, all of that mod's in-memory game/update changes roll back together. Previously mounted valid mods stay active and base-game launch continues. Original game files are not replaced on disk.

The **Last launch** result and timestamp describe the last mount attempt, with mounted game/update counts, disabled state or rejection reason per mod. Editing settings does not update that historical result. No per-mod result means that launch did not record that folder (for example, the global feature was off). Mounting a file does not establish that its contents are valid for the game.

This is a loose-data overlay manager. PC ASI/DLL/EXE plugins, SCO scripts and IMG/RPF/WPL/WFT/WTD/XTD archive/model/texture replacements are not supported by this admission route. It does not enable the full PC FusionFix plugin.

Test one mod at a time and keep a save backup. Check its actual effect and performance on your device before adding others.

# Mods on Android

XHYN IV runs the Xbox 360 game data. Files and plugins made for the PC edition
are not automatically compatible.

The current Android host reads the installed loose files in
`Android/data/com.xhyn.ivrecomp/files/LibertyRecomp/game/`.
Matching Xbox 360 data replacements can be tested at their original paths,
such as `common/data/handling.dat`. Keep a copy of each original file before
replacing it and quit the game first. A mod must match the installed game and
title update; changing traffic, models or scripts can also affect performance.

Xbox 360 texture and model formats are required. A matching filename alone
does not make a PC asset compatible. PC `.asi`, `.dll` and `.exe` plugins and
replacement Xbox executable code are not supported by this Android build.
Custom SCO scripts and replacement IMG/RPF archives have not been validated.

The Android RexGlue host mounts `game/` as `game:` and the installed
`game/update/` directory as `update:`. This is separate from the older desktop
host's FusionFix-style overlay. Simply creating `mods/update/` or copying PC
FusionFix plugins does not enable an Android overlay. A separate mod importer
or overlay manager has not been added to the launcher.

# XHYN IV — first-batch fixes

Updated 24 October 2026, Asia/Manila. This source checkpoint supersedes the earlier migration note saying this batch still needed reconstruction.

## Implemented

| Work | Result |
| --- | --- |
| Native profile-setting lifetime | Setting has a virtual destructor. Production binary/string/integer settings can be replaced and destroyed through unique_ptr<Setting>; the regression also checks derived destruction. |
| Layout import (#33) | Imports deep-copy supplied control entries into the current draft. Other controls and contexts survive; undo checkpoints and explicit Save behavior remain. |
| Mod isolation (#41/#55) | Invalid folders are rejected independently. A failed game/update mount rolls back that entire mod's in-memory changes while earlier valid mods remain mounted. |
| Mod controls | Independent enabled state, priority -1000..1000, deterministic UTF-8 ordering, conflict details showing the final winner, and timestamped per-mod last-launch results. |
| Mod responsiveness | Initial folder scanning and saving run outside the UI thread. Priority edits reorder the displayed cards. |
| Mod documentation | Placement examples, exact loose-data scope, title-update requirement, limits, rejection behavior and mount-versus-compatibility distinction now match the implementation. |

Java and native policies reject symlinks, unsupported files, malformed/oversized settings, control characters, case-insensitive duplicate paths, oversized data and excessive nesting. Own metadata/temp-file handling and priority ordering agree.

The overlay transaction reserves vector space before journaling a new insertion, so an allocation failure cannot roll back an insertion that never happened. Capacity still grows geometrically.

## Validation

| Check | Result |
| --- | --- |
| Java fixture groups | 10 passed |
| Android UI/resources | All 67 Java/resource sources compiled |
| Native host groups | 10 passed with ASan and UBSan |
| Android ARM64 syntax | All 12 changed units passed with pinned NDK r29; final runtime and overlay units were rechecked after the last policy edit |
| Additional host syntax | runtime.cpp and user_profile.cpp passed |
| Source whitespace check | git diff --check passed |

Focused cases cover real production Setting destruction/replacement, unrelated-context import preservation, deep-copy and unsaved-draft isolation, independent mod rejection, settings persistence, final conflict winners, metadata/report bounds, symlinks, depth/size limits, and real cross-device VFS rollback for failed mods and file/directory collisions. Existing save, log cleanup, controls, shader, cache and launch regressions also passed.

LeakSanitizer could not read /proc/<pid>/task in this environment and exited with its ptrace/process-information error. The successful host run used XHYN_HOST_DETECT_LEAKS=0, retaining AddressSanitizer's type-mismatch checks and UndefinedBehaviorSanitizer. **Leak coverage is not established.** The normal runner defaults the new lifetime/mod groups to leak checking when the host supports it.

No new full native library link, ARM64 execution or phone gameplay check was performed. These are source fixes, not device acceptance. All native libraries must be rebuilt together before a future APK includes the Setting lifetime change; do not combine it with old native binaries.

## Current scope

- No new APK or release was built, pushed or published.
- The existing Test5 APK is unchanged and does not contain these source fixes.
- Water entry remains user-confirmed fixed on the user's device; no water patch was added.
- Automatic cleanup of old full logs, optional touch outline and Compact layout decisions remain as before.
- ESMERINGHOY reconstruction/tuning, driver management, preparation/report gaps and the other numbered work remain pending.
- #42/#56 remain deferred; #51/#52 excluded; #40 stays later work, outside this batch.

The original uncommitted batch was unavailable, so these implementations were rebuilt from the saved requirements against the exact Test5 source tree 426cd3ed36bba800918c7472e955ed5ca8e7ad88. This checkpoint preserves the actual current files and patch, rather than just recovery notes.

# R2 Test 2 icons fix

Version `0.5.9.3-xhyn-r2-test2-icons`, version code 30, app `com.xhyn.ivrecomp`.
This is a Java/artwork hotfix over the signed Test 2 build, not a new native
engine or performance release. All 11 native libraries remain byte-identical.

## Confirmed cause

`LibertyActivity` previously copied touch PNGs beneath the external game-data
root. `filesystem_android.cpp::GetUserFolder()` returns `Context.getFilesDir()`;
`GTA4App::OnConfigurePaths` builds `LibertyRecomp/native.toml` beneath that
internal folder. `ContextTouchOverlay` reads icons beside that config in
`LibertyRecomp/touch-icons`. Consequently packaged PNGs could be present while
every native file lookup missed them and selected old vector fallback drawings.

The activity now calls `TouchIconAssets.sync(context.getFilesDir(), ...)` before
native startup. The editor continues to read the same bundled PNGs directly.
Sync refreshes on APK update, repairs missing/invalid-header assets, bounds file
names/count/size, writes each image through a temporary file and only publishes
the completed version marker after the copy finishes. Failed preparation is
logged and shown to the player instead of remaining silent. Saved touch layouts
and game data are not moved or reset.

## Requested asset set

The active import comes from the user's `es2.zip`, SHA-256
`eefa46f8d3dbf146ad558493253e6b5bc8939bda5627fc7f73a5ea7434a36cfe`.
Twenty-one mapped variants retain the supplied glyph shapes with the outside
ring/backing removed. Aim uses `lockon.png`, rather than the triangular target
symbol. Mobile Pause resolves `house.png`; both house/menu now contain the
supplied pause glyph. View and look-back use the supplied camera image.
Unmatched IV actions and Xbox labels retain the previously approved vector set.
Themed sticks are preserved. No backgrounds, characters, logos, map textures,
full APK or unrelated archive files are imported.

The archive is described by the user as fan-made; this does not establish the
original creator or redistribution license. Attribution/provenance remains
separate from the project code license. `es2-provenance.json` records every
mapped source, hash, rotation and output hash. The older GTA III manifest is
kept as history. The generator preserves the active imported variants.

## Verification

The Java regression executes the production synchronizer against internal and
external test folders, verifies the native-facing internal path, checks actual
bytes, repairs missing/invalid headers and verifies recovery after a failed
copy without publishing a successful marker. Traversal names are rejected.
Android resources and all application/SDL Java sources are compiled for the
new APK. Packaging pins the signed Test 2 input by SHA-256, preserves its native
libraries and remaining resources, and rebuilds Java/dex and the manifest.
Final verification compares every native library with Test 2 and every bundled
touch PNG with the current source; it checks CRC, signature, manifest and 16 KB
alignment. Build metadata/logs accompany the recovery ZIP.

The production path mismatch is fixed in source and host regression coverage.
Actual on-phone icon appearance still needs confirmation. No public release or
GitHub push was performed. Use the updated APK and upload kit instead of the
previous Test 2 files.

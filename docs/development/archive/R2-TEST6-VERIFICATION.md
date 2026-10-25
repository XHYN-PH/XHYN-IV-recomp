# XHYN IV 0.5.9.3-xhyn-r2-test6

Testing prerelease, Android package `com.xhyn.ivrecomp`, version code 34.
Checked on 24 October 2026. Device acceptance and performance measurements remain pending.

## Fixes recovered and checked

- Rebuilt the native profile-setting destructor change into all native libraries together.
- Preserved unrelated controls and contexts during layout import, including draft isolation.
- Preserved independent mod admission, priority, conflict reporting and transactional VFS rollback.
- Applied normalized driver selection to startup, retained the actual rendering driver (including System fallback), and retried failed history writes.
- Made driver replacement recoverable after promotion failure or process interruption. Rejected duplicate/path-invalid/oversized imports, limited metadata reads, and blocked imports while playing.
- Invalidated stale observed-driver records when replacing or removing a package.
- Replaced `Files.readString`, absent from the Android SDK, with bounded stream reading for driver settings.
- Fixed SPIR-V literal/result-type confusion and scalar-to-pair selection for SPIR-V 1.3; rejected unsupported 64-bit switch selectors explicitly.
- Routed the texture-detile compute module through the shared shader compatibility path.
- Kept shaderInt64 required for automatic device admission because the research rewrite does not cover the complete title cache.
- Corrected Vulkan feature-query initialization under the driver proxy's warning-as-error build.
- Tightened ESMERINGHOY source/toolchain checks, added missing dependency preflight, and fixed Linux host-tool archive extraction ownership handling.
- Included project COPYING and the imported touch-artwork attribution in the APK.

The accumulated post-r1 implementation also includes the Compact menus, achievement/cheat filters, optional icon outlines (default Off), save metadata/header operations, HUD positioning/preview, bounded input tracing, per-driver choices, benchmark/session reporting, and explicit preparation outcomes. Compilation is not device acceptance for those features.

## Validation

| Check | Result |
| --- | --- |
| Java regression groups | 11 passed, including driver replacement/interruption/history tests |
| Native host regression groups | 10 passed with AddressSanitizer and UndefinedBehaviorSanitizer |
| Android UI/resource compilation | 70 Java/resource sources passed |
| Android platform boot-classpath compilation | Passed with the SDK lambda compilation stubs |
| Android ARM64 syntax checks | 12 translation units passed |
| Full native Release build | Completed with NDK r29; all native libraries rebuilt/staged |
| Driver proxy Release build | Completed with warnings treated as errors |
| Address rewrite focused checks | 9 passed with real SPIR-V assembly and validation tools |
| Actual title shader cache | 2,062 early/late variants checked: 1,619 accepted outputs validated; 443 rejected; no invalid accepted output |
| ESMERINGHOY recipe source-drift regression | Passed; unexpected edits/files and symlink substitution rejected |
| APK | Signature/update certificate, identity/version, 11 exact ARM64 libraries, 16 KiB ELF/ZIP alignment, JNI interfaces, 43 touch PNGs, seven driver archives and every pinned archive member verified |
| Whitespace | git diff --check passed |

Leak checking remains unverified: the environment cannot supply the process information LeakSanitizer requires. The host run used `XHYN_HOST_DETECT_LEAKS=0`; AddressSanitizer type/lifetime checks and UndefinedBehaviorSanitizer remained active. Existing compiler/deprecation warnings remain. These checks do not establish bug-free gameplay.

## Remaining work

- ESMERINGHOY is a source-built **27.0.0-devel (A7XX/A8XX)** candidate with credits and pins. Original V38/Vauzi packages are unchanged. Exact original V38 binary reproduction and measured tuning gains are not established.
- shaderInt64-free rendering is incomplete. The rejected cache variants include signed64 and unreviewed operations; unsupported devices receive a feature reason. DepthClamp/fragment-clipping equivalence and Mali/Xclipse rendering acceptance remain open. Ordinary clipping defaults Off and is not equivalent clamping.
- Mouse unarmed attacks/targeting, camera-follow behavior, controller ownership/reconnect, contextual/GPS/map actions, save episode/autosave/loaded-slot identity, and collision/guest-call investigations remain open where device evidence is needed.
- Additional capture-backed draw-state/texture optimizations and matched sustained/uncapped performance measurements remain open. No measured FPS gain is claimed.
- Direct SAF streaming (#40) and multiplayer (#42/#56) remain deferred; #51/#52 remain excluded. Existing folder selection copies validated data locally.
- Imported es2 touch artwork did not include a creator/redistribution license. The APK preserves attribution and does not claim it as original GPL artwork.

Keep automatic cleanup of previous full logs. Export a report before starting another game session. Small session/exit summaries and benchmark history remain bounded.

Supply your own Xbox 360 GTA IV USA game files and TU8 (0.0.8.5). No game installation or title update is included.

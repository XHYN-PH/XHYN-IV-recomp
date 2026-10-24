# ESMERINGHOY 27.0.0-devel (A7XX/A8XX)

This is a separately source-built Turnip candidate for XHYN IV. The original V38 ZIP remains unchanged and separately selectable. Neither GPU admission nor a successful link certifies gameplay or a speed improvement.

The V38 runtime identifies Mesa `f28352d117`; its full upstream commit is pinned in `source.lock.json`. The derivative uses that exact source revision, an explicit aggregate Android patch, NDK r29, API 29 and 16 KiB load-segment alignment. The complete original V38 compiler options and patch manifest were not supplied with the binary, so byte-identical V38 reproduction is not claimed.

The aggregate patch includes the stable-C IMapper backend, gralloc/UBWC usage handling, A7xx quirks, registered A810/A829 chip IDs and conservative SYSMEM requirements, and the balanced bandwidth estimate (11/10 → 10/10). The obsolete draw-count threshold anchor did not match and was not changed. The old mesh-shader patch series was not forced onto this newer source. KGSL zero-timeout polling is already implemented in the pinned revision's `kgsl_poll_timestamp` path.

Mesa/Turnip contributors retain credit. Android recipe ancestry is StevenMXZ/Adreno-Tools-Drivers at `b3b32ad30e7d7b4bd897e678af064036eeb9df09` and WinNative-Emu/Drivers at `c71f600a2977a141f21a922c9ed2e14fb09b13d3`. The IMapper source carries an MIT notice. The upstream GPL build scripts were consulted; this repository's build.py is a separate recipe and does not copy those scripts. Source files retain their individual licenses. Package notices include Mesa and Expat license information.

On Linux, install GCC/G++, flex, bison, m4, pkg-config and glslang tools, plus the Python versions from the lock. Then run:

```sh
python3 drivers/esmeringhoy/build.py --ndk /absolute/path/android-ndk-r29 --work /absolute/path/esmeringhoy-build --jobs 4
```

The recipe verifies the exact NDK revision, Python package/tool versions and source patch hashes. It refuses unexpected tracked or untracked source edits and never resets an existing checkout. Mesa's pinned wraps hash-check fallback dependencies. SOURCE.json in the bundled driver records the source, patch digest, tuning and device status. Build output is a driver library, not an app release.

The bundled candidate was compiled on 24 October 2026 and has not been run on a phone in this implementation phase. Adreno 7xx/8xx registrations are candidates; A825 was not invented after the upstream insertion script failed to find its anchor. Broader Adreno 6xx work remains available through the independent Turnip/Vauzi choices. Mali/Immortalis and Xclipse need their own compatible drivers.

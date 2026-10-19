# Optional Vulkan drivers

Automatic selects the bundled Turnip driver on supported Adreno 6xx/7xx devices,
and the system Vulkan driver otherwise. System is always available. Optional
drivers are admitted by GPU family and Android version, then checked against the
renderer’s required Vulkan features before use. These checks do not establish
gameplay compatibility on every device. A rejected custom driver falls back to
System.

| Package | Source | Minimum Android |
| --- | --- | --- |
| Turnip 26.3.0 R6 | Original LibertyRecompAndroid pinned bundle | 10 |
| PanVK Kbase RC3 | [zenithblue-oss/panvk-kbase-android](https://github.com/zenithblue-oss/panvk-kbase-android/releases/tag/panvk-kbase-v0.1.0-rc3) | 15 |
| RADV Xclipse 1.6 | [JimVulkan/radv-xclipse](https://github.com/JimVulkan/radv-xclipse/releases/tag/v.1.6.0) | 14 |

PanVK and RADV are experimental options for matching Mali/Immortalis and Xclipse
GPUs. They are upstream binaries, not drivers built or performance-validated by
this fork. Their exact archive and member hashes are in `BundledDrivers.java`.
PanVK’s source manifest and feature matrix are retained inside its archive;
notices and license texts are included in `assets/drivers/licenses/panvk`.
RADV’s upstream notice remains inside its archive.

No driver can add missing kernel support or make every Android GPU compatible.
The renderer still requires Vulkan 1.2, shaderInt64, depthClamp,
sampleRateShading and independentBlend. Clip/cull-distance compatibility is a
separate shader fallback and does not emulate clipping.

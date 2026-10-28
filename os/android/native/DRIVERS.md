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
| Vauzi v4.3 (Mesa 27.0.0-devel) | [Vauzi-17 v4.3](https://github.com/Vauzi-17/710/releases/tag/4.3) | 10 |
| RADV Xclipse 1.8 | [JimVulkan/radv-xclipse](https://github.com/JimVulkan/radv-xclipse/releases/tag/v1.8.0) | 14 |
| RADV Xclipse 1.6 | [JimVulkan/radv-xclipse](https://github.com/JimVulkan/radv-xclipse/releases/tag/v.1.6.0) | 14 |

PanVK and RADV are experimental options for matching Mali/Immortalis and Xclipse
GPUs. They are upstream binaries, not drivers built or performance-validated by
this fork. Their exact archive and member hashes are in `BundledDrivers.java`.
PanVK’s source manifest and feature matrix are retained inside its archive;
notices and license texts are included in `assets/drivers/licenses/panvk`.
RADV’s upstream notice remains inside its archive.

No driver can add missing kernel support or make every Android GPU compatible.
The renderer requires Vulkan 1.2, shaderInt64, sampleRateShading, independentBlend,
bufferDeviceAddress, runtimeDescriptorArray, partially bound descriptors and
dynamicRendering. The loader probe reports these features and device limits.
The actual selected render device is recorded separately after initialization.

The address-only uint32-pair rewrite remains research code. A structural check
of all 2,062 early/late title variants accepted 1,619 and rejected 443, including
signed64 and other unreviewed operations. Accepted outputs passed SPIR-V
validation; this is not GPU execution or arithmetic certification. Automatic
device admission still requires shaderInt64, so an incomplete rewrite cannot
silently substitute missing title shaders. Complete coverage and device
validation are required before relaxing that requirement.

Full equivalent depthClamp emulation is not complete. The ordinary-depth-
clipping experiment now defaults Off and is explicitly a visual compromise.
Turning it on does not prevent near/far clipping or certify shadows. Existing
clip/cull-distance rewriting can omit title clip planes; it is not equivalent
fragment clipping. Mali/Xclipse correctness remains open pending a reviewed
shader/pipeline fallback and device validation.

The v4.3 standard archive remains unmodified and hash pinned. Its upstream
release reports Mesa commit `d1e369d8a63e`; the package includes changes for
Adreno 613, 710, 720 and 722 and is exposed as a candidate across admitted Adreno
6xx/7xx/8xx families. Family admission is followed by native capability checks.
The UI lists unavailable PanVK/RADV bundles with their GPU/API admission reason.
It does not auto-select the new candidate or replace a saved/imported choice.

The bundled V38 Patched package was removed in Test9. A saved `turnip_v38`
choice falls back to Automatic; imported custom drivers remain separate.

ESMERINGHOY 27.0.0-devel (A7XX/A8XX) is now a separate source-built candidate. See drivers/esmeringhoy/source.lock.json and its build recipe for the exact Mesa revision, reviewed patch output, compiler target, original credits and untested device status. The Vauzi archive remains unchanged. No measured performance improvement is claimed.

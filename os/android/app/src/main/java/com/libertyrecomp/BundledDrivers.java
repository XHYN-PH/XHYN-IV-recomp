package com.libertyrecomp;
import java.util.LinkedHashMap;
import java.util.Map;
/** Hash-pinned optional driver packages. See native/DRIVERS.md. */
final class BundledDrivers {
    static final class Bundle {
        final String mode, asset, label, library, zipHash;
        final int minApi;
        final Map<String,String> files;
        Bundle(String mode,String asset,String label,int minApi,String library,String zipHash,Map<String,String> files) {
            this.mode=mode;this.asset="drivers/"+asset;this.label=label;this.minApi=minApi;
            this.library=library;this.zipHash=zipHash;this.files=java.util.Collections.unmodifiableMap(files);
        }
    }
    static final Map<String,Bundle> ALL;
    static {
        Map<String,Bundle> bundles=new LinkedHashMap<>();
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("meta.json","b6dae160802aec1ab48a0953fd9b09d76465a282c4a1d134645218af2a62d9d3");
            files.put("libvulkan_freedreno.so","4fd2fd30311af301ae5564139bc24cc727390d4dc3c90a20192c25ec91287c59");
            bundles.put("turnip",new Bundle("turnip","turnip-r6.zip","Turnip 26.3.0-R6 (bundled)",29,"libvulkan_freedreno.so","5fa2305eb9eff64d9347a670e6ca5ef7c518f60e9e782a4db7ee5588c9852d81",files));
        }
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("libvulkan_panfrost.so","b46b9f0416f207a05482349dffb37b6f3d84c20762151c623774831ce3939445");
            files.put("meta.json","1a9492ad2064f196ce44194122819fee3d1070f14e9f076b8e1f395e0c93b231");
            files.put("MANIFEST.json","7083876d7288ae2a18035f0d56eb64a7912babc399a98944f0b731dc0603b4d0");
            files.put("SHA256SUMS.txt","ca41f491b3d8fed1cfc34fe62f0769c29aefb6f7a7634a964494d875af8418c4");
            files.put("SOURCE.json","9259392b8849547dff5153e05a21bc183e2f5ee677d33cc14732fc9ec4247160");
            files.put("VALIDATION.json","05778f757656281739f047edea93a083c8ee3a3f40a574c4144bf6705bca76ea");
            files.put("runtime-feature-matrix.json","9d9447fe7690d4d21b60f77f34b8c68ee9cd2015b3fd8c34b76124238cce9e86");
            files.put("extension-gap.json","18b1be69a7deb3db70da9dea48ade3acb6de63bf068d99fde459b971328ce613");
            bundles.put("panvk",new Bundle("panvk","panvk-rc3.zip","PanVK Kbase RC3 (experimental)",35,"libvulkan_panfrost.so","4ef95dfd5118f1f7854de97ed342a551971c1e339bea3160566a5725a792c85f",files));
        }
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("meta.json","ea5c0729485cc51ed8b7d2f7a89a90d62ea7ad468d5be815e447195fe62ad1f0");
            files.put("vulkan.radeon.so","14a9a945d6fff6bcedee577fa5e0c5a17bf1de3e8d8e477af75f354808c4d032");
            files.put("NOTICE.txt","5d7227105c869bcaab4d70c8d90b7da6e56a61e51b6f51bd091bcafc29310a55");
            bundles.put("radv",new Bundle("radv","radv-xclipse-1.6.zip","RADV Xclipse 1.6 (experimental)",34,"vulkan.radeon.so","2b2fd55f24143fff1008789efdb9fe4c1a4b02855fdfe1c68af4d40760cbee3f",files));
        }
        ALL=java.util.Collections.unmodifiableMap(bundles);
    }
    private BundledDrivers() {}
}

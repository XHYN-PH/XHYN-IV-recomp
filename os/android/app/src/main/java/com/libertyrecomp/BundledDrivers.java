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
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("meta.json","b27f1d1c401025704ee271a8ff768b8f68465c374c467c7238164fa79a546c8d");
            files.put("libvulkan_freedreno.so","7b0b16fb0c8fb7854868e77eb8b284a1e95789e32c1fd8b545ed91165bf78189");
            bundles.put("turnip_v38",new Bundle("turnip_v38","Turnip_Gen8_V38_Patched.zip","Turnip Mesa 27.0.0-devel · V38 Patched",29,"libvulkan_freedreno.so","2bbb279a3da7680503a54a51a3051c7a4302c8fb2a089f8b183b4b5d6ba9ac17",files));
        }
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("meta.json","e4bef43957b5754a1e0d1a0032134db0465508e4bc75e423f4b175bcc34eddc1");
            files.put("libvulkan_freedreno.so","919a19b3d49880292d88dd35ff92b516ca7c929a3321d62452e1361c3ceeefc5");
            bundles.put("turnip_v43",new Bundle("turnip_v43","Turnip-710-720-722-v4.3.zip","Turnip Mesa 27.0.0-devel · Vauzi v4.3",29,"libvulkan_freedreno.so","45ac070f30086cb67bd6f24600199293192c3fa4810c402b25ac185967a3b5e6",files));
        }
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("meta.json","0a4b8545e9e05a076963441dae006b1ad455b482047b92dd86eb8c5b14c2c437");
            files.put("vulkan.radeon.so","286aacb0dece09fc8e33c616b8a8a7e4ef5c827feb1d1bfc2bf12cc8b9e19f56");
            files.put("NOTICE.txt","b29b68c54946d4eb2ab86c8066b375fee423915b09191b945a68820141757c08");
            bundles.put("radv18",new Bundle("radv18","radv-xclipse-1.8.zip","RADV Xclipse 1.8 (experimental)",34,"vulkan.radeon.so","0545b1b24817cc0ed4a9e4fc440a7080cbaf69a9ce5d1e4bb22b0e697129c223",files));
        }
        {
            Map<String,String> files=new LinkedHashMap<>();
            files.put("EXPAT-LICENSE.txt","122f2c27000472a201d337b9b31f7eb2b52d091b02857061a8880371612d9534");
            files.put("MESA-LICENSE.txt","0d1a0472ecc81830e75c20d59b0ea02841e3db21255e0ebad97ab682c54d6615");
            files.put("NOTICE.txt","40fd3b347ed750817903051775541a8c52eb6398e8d13a6f0b7aea4ae03a9b14");
            files.put("SOURCE.json","a0e2e8b4871bbfed18cab4e7ecb9d05dab123803be11bc2632919075858f3419");
            files.put("libvulkan_freedreno.so","2336bbbc948eef0214bf5c785b0c66209398b1b1347683768a0b7d82dc88b837");
            files.put("meta.json","b5243fb09b9a5596d686029dcbb05012e365d217abc5d828db2cdaee1c266a95");
            bundles.put("esmeringhoy",new Bundle("esmeringhoy","ESMERINGHOY-27.0.0-devel-A7XX-A8XX.zip","ESMERINGHOY 27.0.0-devel (A7XX/A8XX)",29,"libvulkan_freedreno.so","29768adfaaa6fa7ee56cdbc11edd52aba0cb695360ca0fcf99dc751cdbeb0163",files));
        }
        ALL=java.util.Collections.unmodifiableMap(bundles);
    }
    private BundledDrivers() {}
}

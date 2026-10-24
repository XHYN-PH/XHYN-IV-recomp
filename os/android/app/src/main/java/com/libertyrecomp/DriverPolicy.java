package com.libertyrecomp;
import java.util.Locale;
import java.util.regex.Pattern;
/** Admission checks only. Actual Vulkan capabilities are verified at startup. */
final class DriverPolicy {
    private static final Pattern TURNIP=Pattern.compile(".*adreno\\D*[67]\\d{2}\\b.*");
    private static final Pattern ESMERINGHOY=Pattern.compile(".*adreno\\D*[78]\\d{2}\\b.*");
    private static final Pattern TURNIP_NEW=Pattern.compile(".*adreno\\D*[678]\\d{2}\\b.*");
    private static final Pattern PANVK=Pattern.compile(".*(?:mali|immortalis)[- ]*g(?:57|68|77|78|610|710|615|715|720|925)\\b.*");
    private static final Pattern RADV18=Pattern.compile(".*xclipse\\D*(?:530|540|550|920|940|950|960)\\b.*");
    private static final Pattern RADV=Pattern.compile(".*xclipse\\D*(?:530|920|940)\\b.*");
    static boolean supports(String mode,String renderer,int api) {
        String gpu=renderer==null ? "" : renderer.toLowerCase(Locale.ROOT);
        switch(mode) {
            case "esmeringhoy": return api>=29 && ESMERINGHOY.matcher(gpu).matches();
            case "turnip_v43":
            case "turnip_v38": return api>=29 && TURNIP_NEW.matcher(gpu).matches();
            case "turnip": return api>=29 && TURNIP.matcher(gpu).matches();
            case "panvk": return api>=35 && PANVK.matcher(gpu).matches();
            case "radv18": return api>=34 && RADV18.matcher(gpu).matches();
            case "radv": return api>=34 && RADV.matcher(gpu).matches();
            default: return "system".equals(mode)||"auto".equals(mode);
        }
    }
    static String reason(String mode,String renderer,int api){
        int minimum=mode.equals("panvk")?35:mode.startsWith("radv")?34:29;
        if(api<minimum)return "Requires Android "+(minimum==35?"15":minimum==34?"14":"10")+" or later";
        if(renderer==null||renderer.trim().isEmpty())return "GPU not identified; use System or import a matching driver";
        if(!supports(mode,renderer,api))return mode.equals("panvk")?"This bundled PanVK package does not admit this GPU":mode.startsWith("radv")?"This bundled RADV package does not admit this Xclipse model":"Requires a matching Adreno GPU";
        return mode.equals("esmeringhoy")?"Source-built Mesa f28352d117; experimental A7xx/A8xx candidate, device measurements pending":mode.equals("turnip_v43")?"Candidate for Adreno 6xx/7xx/8xx; includes 613, 710, 720 and 722 changes":mode.equals("turnip_v38")?"Mesa 27.0.0-devel; preserve as a separate test choice":"GPU/API admitted; Vulkan features and gameplay still need device checks";
    }
    static String automatic(String gpu,int api) { return supports("turnip",gpu,api) ? "turnip" : "system"; }
    private DriverPolicy() {}
}

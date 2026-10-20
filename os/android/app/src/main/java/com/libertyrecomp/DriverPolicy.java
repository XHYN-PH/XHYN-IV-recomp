package com.libertyrecomp;
import java.util.Locale;
import java.util.regex.Pattern;
/** Admission checks only. Actual Vulkan capabilities are verified at startup. */
final class DriverPolicy {
    private static final Pattern TURNIP=Pattern.compile(".*adreno\\D*[67]\\d{2}\\b.*");
    private static final Pattern PANVK=Pattern.compile(".*(?:mali|immortalis)[- ]*g(?:57|68|77|78|610|710|615|715|720|925)\\b.*");
    private static final Pattern RADV=Pattern.compile(".*xclipse\\D*(?:530|920|940)\\b.*");
    static boolean supports(String mode,String renderer,int api) {
        String gpu=renderer==null ? "" : renderer.toLowerCase(Locale.ROOT);
        switch(mode) {
            case "turnip": return api>=29 && TURNIP.matcher(gpu).matches();
            case "panvk": return api>=35 && PANVK.matcher(gpu).matches();
            case "radv": return api>=34 && RADV.matcher(gpu).matches();
            default: return "system".equals(mode)||"auto".equals(mode);
        }
    }
    static String automatic(String gpu,int api) { return supports("turnip",gpu,api) ? "turnip" : "system"; }
    private DriverPolicy() {}
}

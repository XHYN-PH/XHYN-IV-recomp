package com.libertyrecomp;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
/** Phone controls must leave saved renderer quality choices untouched. */
final class PerformanceLaunchOptions {
    static Map<String,String> nextSettings(File data)throws IOException {
        Map<String,String> settings=MobileConfig.read(MobileConfig.file(data));
        settings.putAll(NextLaunchConfig.read(data));
        return settings;
    }
    static void apply(List<String> args,boolean turbo,boolean thermal,boolean safe){
        DebugLaunchOptions.set(args,"android_gpu_turbo",Boolean.toString(turbo));
        DebugLaunchOptions.set(args,"android_thermal_guard",Boolean.toString(thermal));
        if(safe)DebugLaunchOptions.set(args,"gta4_safe_mode","true");
    }
    static void canonicalAntiAliasing(List<String> args){
        if(args.stream().anyMatch(value->value.startsWith("--gta4_native_anti_aliasing=")))DebugLaunchOptions.set(args,"gta4_native_anti_aliasing_unified","true");
    }
    private PerformanceLaunchOptions(){}
}

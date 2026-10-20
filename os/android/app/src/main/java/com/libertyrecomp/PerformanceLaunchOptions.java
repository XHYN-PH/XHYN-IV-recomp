package com.libertyrecomp;
import java.util.List;
/** Phone controls must leave saved renderer quality choices untouched. */
final class PerformanceLaunchOptions {
    static void apply(List<String> args,boolean turbo,boolean thermal,boolean safe){
        DebugLaunchOptions.set(args,"android_gpu_turbo",Boolean.toString(turbo));
        DebugLaunchOptions.set(args,"android_thermal_guard",Boolean.toString(thermal));
        if(safe)DebugLaunchOptions.set(args,"gta4_safe_mode","true");
    }
    private PerformanceLaunchOptions(){}
}

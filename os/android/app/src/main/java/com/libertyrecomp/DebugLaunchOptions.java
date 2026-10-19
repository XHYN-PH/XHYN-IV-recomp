package com.libertyrecomp;

import java.io.File;
import java.util.List;

/** Keep routine captures useful without logging every input poll. */
final class DebugLaunchOptions {
    static void apply(List<String> args, boolean debug, boolean inputTrace, File log) {
        // Normal Play owns the final logging policy, including saved overrides.
        defaults(args,"input_trace",false);
        defaults(args,"gta4_native_input_trace",false);
        defaults(args,"gta4_native_resource_trace",false);
        defaults(args,"gta4_native_frame_stats",false);
        if (!debug) {
            args.removeIf(a -> a.startsWith("--diagnostics"));
            set(args,"diagnostics","false");set(args,"diagnostics_categories","");
            set(args,"log_level","off");set(args,"log_file","/dev/null");
            for(String key:new String[]{"log_verbose","log_noisy","input_trace","gta4_native_input_trace","gta4_native_resource_trace","gta4_native_frame_stats","gta4_profile_native_autostart","present_timing_stats","android_thermal_log","gta4_profile_native_detailed_cpu","gta4_profile_native_detailed_gpu","gta4_native_detailed_counters","gta4_native_frame_timeline","gta4_pipeline_stats","gta4_gpu_counters"})set(args,key,"false");
            for(String key:new String[]{"gta4_native_benchmark_trigger","gta4_profile_native_capture_trigger","gta4_trace_shadow_targets","gta4_shadow_capture_trigger"})set(args,key,"0");
            return;
        }
        args.removeIf(a -> a.startsWith("--diagnostics") || a.startsWith("--log_level="));
        args.add("--diagnostics=true");args.add("--diagnostics_categories=logging");
        set(args,"log_level","info");
        set(args,"input_trace",Boolean.toString(inputTrace));
        set(args,"gta4_native_input_trace",Boolean.toString(inputTrace));
        set(args,"gta4_native_frame_stats","true");set(args,"present_timing_stats","true");
        if (log != null) {
            set(args,"log_file",new File(log.getParentFile(),log.getName()+".runtime.log").getAbsolutePath());
            set(args,"log_flush_interval","1");set(args,"log_max_file_size_mb","8");
            set(args,"log_max_files","1");
        }
    }
    static void rendererCapture(List<String> args) {
        set(args,"diagnostics_categories","logging,native-profiler");
        set(args,"gta4_profile_native_autostart","false");
        set(args,"gta4_profile_native_capture_trigger","0");
        set(args,"gta4_profile_native_samples","120");
        set(args,"gta4_profile_native_detailed_cpu","true");
        set(args,"gta4_profile_native_detailed_gpu","false");
        set(args,"gta4_profile_native_gpu_query_budget","64");
    }
    private static void defaults(List<String> args,String key,boolean value) {
        if(args.stream().noneMatch(a -> a.equals("--"+key)||a.startsWith("--"+key+"=")))
            args.add("--"+key+"="+value);
    }
    static void set(List<String> args,String key,String value) {
        args.removeIf(a -> a.equals("--"+key)||a.startsWith("--"+key+"="));
        args.add("--"+key+"="+value);
    }
}

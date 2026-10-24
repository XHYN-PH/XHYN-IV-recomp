package com.libertyrecomp;

import android.app.*;
import android.os.Build;
import android.widget.*;
import android.content.*;
import java.io.*;
import java.nio.file.Files;

final class CompatibilityPage {
    static AlertDialog show(Activity a,File data,MobileSettingsUi.Runtime runtime) {
        StringBuilder text=new StringBuilder();
        text.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\nABIs: ").append(String.join(", ",Build.SUPPORTED_ABIS));
        ActivityManager manager=(ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager!=null) {
            ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
            text.append(String.format(java.util.Locale.ROOT,"\nRAM: %.1f GiB total; %.1f GiB currently available\nMemory pressure: %s\n",
                memory.totalMem/1073741824.0,memory.availMem/1073741824.0,memory.lowMemory?"yes":"no"));
        }
        @SuppressWarnings("deprecation") android.view.Display display=a.getWindowManager().getDefaultDisplay();
        if(display!=null)text.append("\nActive display refresh: ").append(display.getRefreshRate()).append(" Hz\n");
        text.append("\nDetected GPU: ").append(GpuInfo.renderer()).append('\n');
        if(Build.VERSION.SDK_INT>=31)text.append("SoC: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append('\n');
        try{
            String raw=runtime==null?DriverState.observation(data,DriverState.readChoice(data)).getProperty("native","{}"):DriverBridge.status();
            org.json.JSONObject probe=new org.json.JSONObject(raw);
            text.append("\nRequested driver: ").append(probe.optString("requested_mode","unobserved")).append("\nLoaded selection: ").append(probe.optString("loaded_mode","unobserved")).append('\n');
            String fallback=probe.optString("selection_error",probe.optString("fallback_from",probe.optString("error","")));if(!fallback.isEmpty())text.append("Driver note: ").append(fallback).append('\n');
            org.json.JSONObject active=runtime==null?probe.optJSONObject("render_device"):new org.json.JSONObject(runtime.get("gta4_session_device_json"));
            if(active!=null&&active.has("name"))text.append(runtime==null?"\nLast actual render device\n":"\nActual render device\n").append(active.toString(2)).append('\n');
            org.json.JSONArray devices=probe.optJSONArray("devices");if(devices!=null)text.append("\nLoader capability probe (hardware features and limits)\n").append(devices.toString(2)).append('\n');
            if(runtime!=null)text.append("Shader compatibility: ").append(runtime.get("vulkan_shader_compatibility_status")).append('\n');
        }catch(Exception e){text.append("\nNo retained device capability report yet. Play once to collect it.\n");}
        if(runtime!=null) {
            text.append("\nCurrent runtime\n");
            for(String key:new String[]{"gpu_plugin","resolution","gta4_native_upscaler","present_effect","gta4_present_mode","gta4_session_present_mode","gta4_frame_limit","gta4_effective_upscaling","gta4_native_preparation_status"})
                text.append(key).append(": ").append(runtime.get(key)).append('\n');
        }
        text.append("\nAvailable: native scaling, FSR 1.\nTemporal upscaling / frame generation: unavailable in this build.\nCustom driver support depends on GPU, kernel and driver compatibility. Turnip requires Adreno; Mali/Immortalis and Xclipse use compatible device drivers.\n");
        File launch=new File(data,"last_launch.txt");
        if(launch.isFile())try{text.append("\nLast launch report\n").append(new String(Files.readAllBytes(launch.toPath()),java.nio.charset.StandardCharsets.UTF_8));}
        catch(IOException ignored){text.append("\nCould not read last launch report.");}
        TextView view=new TextView(a);view.setText(text);int pad=Math.round(18*a.getResources().getDisplayMetrics().density);view.setPadding(pad,pad,pad,pad);view.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(a);scroll.addView(view);
        return new AlertDialog.Builder(a).setTitle("Graphics compatibility").setView(scroll).setPositiveButton("Close",null).show();
    }
}

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
        if(runtime!=null) {
            text.append("\nCurrent runtime\n");
            for(String key:new String[]{"gpu_plugin","resolution","gta4_native_upscaler","present_effect","gta4_present_mode","gta4_frame_limit"})
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

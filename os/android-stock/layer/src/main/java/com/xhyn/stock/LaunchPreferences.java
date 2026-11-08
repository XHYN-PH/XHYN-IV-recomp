package com.xhyn.stock;

import android.content.Context;
import android.os.PowerManager;
import java.io.IOException;
import java.util.*;

/** Stock launcher preferences, kept separate from native CVar names. */
final class LaunchPreferences {
    static final String RENDERER="ui_renderer",SUSTAINED="ui_sustained";
    static final SettingSchema.Entry RENDERER_ENTRY=new SettingSchema.Entry(RENDERER,"Renderer","Display","Auto keeps the stock GPU policy. Native is the experimental mobile renderer; Previous uses the earlier stock renderer. GPU feature checks still apply.","auto","native","previous");
    static final SettingSchema.Entry SUSTAINED_ENTRY=new SettingSchema.Entry(SUSTAINED,"Sustained performance","Game","Android's sustained mode can favor steadier long sessions. Requires device support.","false","true");
    static boolean supported(Context c){PowerManager p=(PowerManager)c.getSystemService(Context.POWER_SERVICE);return p!=null&&p.isSustainedPerformanceModeSupported();}
    static boolean key(String key){return key.equals(RENDERER)||key.equals(SUSTAINED);}
    static boolean valid(String key,String value){return key.equals(RENDERER)?RENDERER_ENTRY.accepts(value):key.equals(SUSTAINED)&&SUSTAINED_ENTRY.accepts(value);}
    static Map<String,String> current(Context c) {
        Map<String,String> out=new LinkedHashMap<>();String renderer=c.getSharedPreferences("launch_renderer",0).getString("mode","auto");
        out.put(RENDERER,RENDERER_ENTRY.accepts(renderer)?renderer:"auto");out.put(SUSTAINED,Boolean.toString(c.getSharedPreferences("performance_test",0).getBoolean("sustained_performance",false)));return out;
    }
    static void apply(Context c,Map<String,String> values)throws IOException {
        if(values.containsKey(RENDERER)) {
            var editor=c.getSharedPreferences("launch_renderer",0).edit();String value=values.get(RENDERER);
            if(value.equals("auto"))editor.remove("mode");else editor.putString("mode",value);
            if(!editor.commit())throw new IOException("Could not save the stock renderer choice.");
        }
        if(values.containsKey(SUSTAINED)) {
            boolean value=Boolean.parseBoolean(values.get(SUSTAINED));if(value&&!supported(c))throw new IOException("Sustained performance is unavailable on this device.");
            if(!c.getSharedPreferences("performance_test",0).edit().putBoolean("sustained_performance",value).commit())throw new IOException("Could not save sustained performance.");
        }
    }
    private LaunchPreferences(){}
}

package com.xhyn.stock;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import java.util.Locale;
import org.json.JSONObject;

/** OS facts only. CPU/PSS refer to the process in which this sampler runs. */
final class MetricsSampler {
    private final Context context; private final OverlayMetrics cpuGpu=new OverlayMetrics();
    private long memoryAt=-1,pss;
    MetricsSampler(Context c) { context=c.getApplicationContext(); }
    String sample(JSONObject options,boolean preview) {
        StringBuilder s=new StringBuilder(preview?"Preview · Android metrics\n":"");
        if(options.optBoolean("cpu",true))s.append(cpuGpu.cpu()).append('\n');
        if(options.optBoolean("gpu",false))s.append(cpuGpu.gpu()).append(" · device\n");
        if(options.optBoolean("ram",true)) {
            long now=SystemClock.elapsedRealtime();
            if(memoryAt<0||now-memoryAt>5000) { pss=Debug.getPss();memoryAt=now; }
            s.append(String.format(Locale.ROOT,"RAM app %.0f MB PSS\n",pss/1024.0));
        }
        if(options.optBoolean("battery",true)) {
            Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null) { int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                s.append("Battery ").append(level>=0&&scale>0?Math.round(100f*level/scale)+"%":"unavailable");
                int temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
                if(temp!=Integer.MIN_VALUE)s.append(String.format(Locale.ROOT," · %.1f°C",temp/10.0)); s.append('\n');
            }
        }
        if(options.optBoolean("thermal",true)) {
            PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            s.append("Thermal ").append(power==null?"unavailable":thermal(power.getCurrentThermalStatus())).append('\n');
        }
        if(options.optBoolean("fps",false))s.append("Game FPS unavailable\n");
        if(s.length()==0)return "No metrics selected";
        return s.toString().trim();
    }
    static String thermal(int status) {
        String[] names={"none","light","moderate","severe","critical","emergency","shutdown"};
        return status>=0&&status<names.length?names[status]:"unavailable";
    }
}

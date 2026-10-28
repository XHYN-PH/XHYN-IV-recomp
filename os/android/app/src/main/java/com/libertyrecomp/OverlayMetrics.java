package com.libertyrecomp;

import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** Optional, one-second sampling. GPU counters may be inaccessible on production phones. */
final class OverlayMetrics {
    private long wall=-1,cpu;
    private String gpuPath;
    private boolean probed;
    String cpu(){
        long now=SystemClock.elapsedRealtime(),used=android.os.Process.getElapsedCpuTime();
        String value="CPU app —";
        if(wall>=0&&now>wall&&used>=cpu){
            double cores=(double)(used-cpu)/(now-wall);
            double percent=Math.min(100,100*cores/Math.max(1,Runtime.getRuntime().availableProcessors()));
            value=String.format(Locale.ROOT,"CPU app %.0f%% · %.1f cores",percent,cores);
        }
        wall=now;cpu=used;return value;
    }
    private static Double read(String path){
        try(BufferedReader in=new BufferedReader(new FileReader(path))){
            String line=in.readLine();if(line==null||line.length()>256)return null;
            String[] values=line.trim().split("\\s+");double busy=Double.parseDouble(values[0]);
            double percent=path.endsWith("gpubusy")?values.length>1&&Double.parseDouble(values[1])>0?busy*100/Double.parseDouble(values[1]):-1:busy;
            return Double.isFinite(percent)&&percent>=0&&percent<=100?percent:null;
        }catch(Exception denied){return null;}
    }
    String gpu(){
        if(!probed){probed=true;for(String path:new String[]{"/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage","/sys/class/kgsl/kgsl-3d0/gpubusy","/sys/class/devfreq/gpufreq/load"}){Double value=read(path);if(value!=null){gpuPath=path;return String.format(Locale.ROOT,"GPU %.0f%%",value);}}}
        Double value=gpuPath==null?null:read(gpuPath);
        return value==null?"GPU unavailable":String.format(Locale.ROOT,"GPU %.0f%%",value);
    }
}

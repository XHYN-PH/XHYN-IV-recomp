package com.libertyrecomp;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.Locale;

/** Low-frequency platform measurements; absent from ordinary play. */
final class PerformanceSampler {
    private final HandlerThread thread=new HandlerThread("PerformanceSummary",android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Activity activity;private Handler handler;private long lastWall,lastCpu;private volatile boolean stopped;
    PerformanceSampler(Activity activity){this.activity=activity;}
    void start(){thread.start();handler=new Handler(thread.getLooper());lastWall=SystemClock.elapsedRealtime();lastCpu=android.os.Process.getElapsedCpuTime();handler.postDelayed(sample,5000);}
    void stop(){stopped=true;if(handler!=null)handler.removeCallbacksAndMessages(null);thread.quitSafely();}
    private final Runnable sample=new Runnable(){public void run(){
        if(stopped)return;
        try {
        long wall=SystemClock.elapsedRealtime(),cpu=android.os.Process.getElapsedCpuTime();
        ActivityManager manager=(ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();if(manager!=null)manager.getMemoryInfo(memory);
        double cores=wall>lastWall?(double)(cpu-lastCpu)/(wall-lastWall):0;
        DebugLog.note(String.format(Locale.ROOT,"platform-perf: time_ms=%d uptime_ms=%d sample_ms=%d cpu_cores_used=%.2f pss_mb=%.1f available_ram_mb=%.1f memory_pressure=%s thermal_status=%d (thermal status is an Android severity, not degrees)",
            System.currentTimeMillis(),wall,wall-lastWall,cores,Debug.getPss()/1024.0,memory.availMem/1048576.0,memory.lowMemory,power==null?-1:power.getCurrentThermalStatus()));
        lastWall=wall;lastCpu=cpu;
        }catch(RuntimeException error){DebugLog.note("Platform performance summary unavailable: "+error.getClass().getSimpleName());}
        finally{if(!stopped)handler.postDelayed(this,5000);}
    }};
}

package com.libertyrecomp;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded background session samples and frozen benchmark-start records. */
final class SessionRecorder {
    private final Activity a;private final File data,log;private final MobileSettingsUi.Runtime runtime;
    private final HandlerThread thread=new HandlerThread("SessionHistory",android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler worker;private volatile boolean closed,foreground=true;
    private final Properties record=new Properties();
    private Properties benchmarkStart;
    private final long started=System.currentTimeMillis();
    private long lastWrite;private String lastBenchmark="";private boolean driverRemembered;
    SessionRecorder(Activity a,File data,MobileSettingsUi.Runtime runtime,File log){this.a=a;this.data=data;this.runtime=runtime;this.log=log;}
    void start(){
        record.setProperty("kind","session");record.setProperty("started",Long.toString(started));
        if(log!=null)record.setProperty("log.session",log.getName());
        record.setProperty("device",Build.MANUFACTURER+" "+Build.MODEL);record.setProperty("android",Build.VERSION.RELEASE);
        if(Build.VERSION.SDK_INT>=31)record.setProperty("soc",Build.SOC_MODEL);
        record.setProperty("cache.phase","unspecified");thread.start();worker=new Handler(thread.getLooper());worker.post(sample);
    }
    void foreground(boolean on){
        foreground=on;
        if(!on&&worker!=null){long time=System.currentTimeMillis();worker.post(()->{if(benchmarkStart!=null){record.setProperty("benchmark.interrupted","true");if(!record.containsKey("benchmark.background_ms"))record.setProperty("benchmark.background_ms",Long.toString(time));}});}
    }
    private String setting(String key){try{String value=runtime.get(key);return value==null?"":value;}catch(Exception e){return "";}}
    private int thermal(){PowerManager p=(PowerManager)a.getSystemService(Activity.POWER_SERVICE);return p==null?-1:p.getCurrentThermalStatus();}
    private String battery(){
        try{Intent value=a.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));int tenths=value==null?Integer.MIN_VALUE:value.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            return tenths > -200&&tenths<1000?Double.toString(tenths/10.0):"unknown";
        }catch(Exception e){return "unknown";}
    }
    void benchmarkStarted(String phase){benchmarkStarted(phase,"");}
    void benchmarkStarted(String phase,String route){
        if(worker==null)return;
        long trigger;try{trigger=(Long.parseLong(setting("gta4_native_benchmark_trigger"))+1)&0xffffffffL;}catch(NumberFormatException e){trigger=1;}
        final String expected=Long.toString(trigger);
        worker.post(()->{
            try{
                benchmarkStart=null;record.remove("benchmark.background_ms");record.remove("benchmark.ui_changed_ms");
                updateRecord();record.setProperty("cache.phase",phase);record.setProperty("route",route.substring(0,Math.min(route.length(),64)));
                record.setProperty("benchmark.interrupted","false");record.setProperty("benchmark.ui_changed","false");
                benchmarkStart=new Properties();benchmarkStart.putAll(record);
                for(String key:new String[]{"fps","frame_ms","sampled","thermal.last","thermal.maximum","ram.mb","ended"})benchmarkStart.remove(key);
                benchmarkStart.setProperty("thermal.start",Integer.toString(thermal()));benchmarkStart.setProperty("battery.start_c",battery());
                benchmarkStart.setProperty("ram.start_mb",Long.toString(Debug.getPss()/1024));
                benchmarkStart.setProperty("benchmark.requested_trigger",expected);
                benchmarkStart.setProperty("benchmark.requested_ms",Long.toString(System.currentTimeMillis()));
                if(log!=null)SessionHistory.writeSidecar(new File(log.getParentFile(),log.getName()+".runtime.log.benchmark.ui-start.properties"),benchmarkStart);
            }catch(Exception e){DebugLog.note("Benchmark start snapshot unavailable: "+e.getClass().getSimpleName());}
        });
    }
    private void updateRecord()throws Exception{
        try{
            JSONObject stats=new JSONObject(setting("gta4_session_stats"));long age=System.nanoTime()-stats.optLong("sample_ns",0);
            if(stats.has("sample_ns")&&age>=0&&age<5000000000L){
                if(!driverRemembered&&stats.optDouble("fps")>0){String actual=setting("gta4_session_device_json");
                    if(!new JSONObject(actual).optString("name").isEmpty())driverRemembered=DriverBridge.observeRenderer(data,actual)&&DriverBridge.rememberWorking(a,data);
                }
                record.setProperty("fps",Double.toString(stats.optDouble("fps")));record.setProperty("frame_ms",Double.toString(stats.optDouble("frame_ms")));
                record.setProperty("aa.actual",stats.optString("aa","unknown"));record.setProperty("sampled",Long.toString(System.currentTimeMillis()));
            }
        }catch(JSONException ignored){}
        for(String key:new String[]{"gta4_frame_limit","gta4_shadow_map_base_size","gta4_draw_distance_scale","gta4_native_upscaler","android_gpu_turbo","android_thermal_guard","resolution","gta4_native_pipeline_status","gta4_native_preparation_status","data_file_mod_status"})record.setProperty(key,setting(key));
        for(String key:MobileSettingsUi.KEYS)if(!key.startsWith("ui_"))record.setProperty("configured."+key,setting(key));
        record.setProperty("configured.ui_sustained",Boolean.toString(UiPreferences.get(a,"sustained")));
        if(benchmarkStart!=null&&!Objects.equals(benchmarkStart.getProperty("configured.ui_sustained"),record.getProperty("configured.ui_sustained"))){record.setProperty("benchmark.ui_changed","true");if(!record.containsKey("benchmark.ui_changed_ms"))record.setProperty("benchmark.ui_changed_ms",Long.toString(System.currentTimeMillis()));}
        record.setProperty("upscaler.actual",setting("gta4_effective_upscaling"));record.setProperty("resolution.configured",setting("resolution"));
        record.setProperty("present.actual",setting("gta4_session_present_mode"));record.setProperty("profile.configured",setting("gta4_profile_native_detailed_cpu"));record.setProperty("shader.compatibility",setting("vulkan_shader_compatibility_status"));
        record.setProperty("thermal.last",Integer.toString(thermal()));if(!record.containsKey("thermal.start"))record.setProperty("thermal.start",record.getProperty("thermal.last"));
        record.setProperty("thermal.maximum",Integer.toString(Math.max(Integer.parseInt(record.getProperty("thermal.maximum","-1")),thermal())));
        record.setProperty("ram.mb",Long.toString(Debug.getPss()/1024));
        if(!record.containsKey("driver.actual"))try{
            JSONObject status=new JSONObject(DriverBridge.status());JSONObject gpu=status.optJSONObject("render_device");
            if(gpu!=null){record.setProperty("driver.actual",gpu.optString("driver_info","unknown"));record.setProperty("gpu",gpu.optString("name","unknown"));}
        }catch(JSONException ignored){}
    }
    private final Runnable sample=new Runnable(){public void run(){
        if(closed)return;
        try{
            if(!foreground)return;updateRecord();
            if(SystemClock.uptimeMillis()-lastWrite>=10000){SessionHistory.save(data,"last-session",record);SessionHistory.save(data,"session-"+started,record);lastWrite=SystemClock.uptimeMillis();}
            if(log!=null){
                File runtimeLog=new File(log.getParentFile(),log.getName()+".runtime.log"),status=new File(runtimeLog+".benchmark.status.txt");
                String signature=Long.toString(status.lastModified());
                if(status.isFile()&&SessionHistory.tail(status,4096).startsWith("complete:")&&!signature.equals(lastBenchmark)){
                    Properties completed=SessionHistory.completedBenchmark(runtimeLog,record);
                    if(!completed.isEmpty()){completed.setProperty("sampled",Long.toString(System.currentTimeMillis()));SessionHistory.save(data,"benchmark-"+started+"-"+status.lastModified(),completed);lastBenchmark=signature;benchmarkStart=null;}
                }
            }
        }catch(Exception e){DebugLog.note("Session summary unavailable: "+e.getClass().getSimpleName());}
        finally{if(!closed)worker.postDelayed(this,5000);}
    }};
    void close(){
        closed=true;
        if(worker!=null){worker.removeCallbacksAndMessages(null);worker.post(()->{
            try{record.setProperty("ended",Long.toString(System.currentTimeMillis()));SessionHistory.save(data,"last-session",record);SessionHistory.save(data,"session-"+started,record);SessionHistory.recoverBenchmark(data,a.getFilesDir());}
            catch(IOException ignored){}thread.quitSafely();
        });}
    }
}


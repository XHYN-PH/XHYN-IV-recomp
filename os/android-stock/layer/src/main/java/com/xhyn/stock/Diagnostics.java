package com.xhyn.stock;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.PowerManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

final class Diagnostics {
    private static final long ENTRY_LIMIT=2L*1024*1024,TOTAL_LIMIT=24L*1024*1024;
    static List<String> sessions(Context c) {
        List<File> out=new ArrayList<>();
        File[] files=new File(StockApi.data(c),"debug_logs").listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&!Files.isSymbolicLink(f.toPath())&&f.getName().endsWith(".log")&&f.getName().length()<180)out.add(f);
        out.sort(Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));List<String> names=new ArrayList<>();for(File f:out)names.add(f.getName());return names;
    }
    static String device(Context c) {
        PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
        return "XHYN IV Android layer Test 1 · stock renderer v442\nBase SHA-256 ae71035bbbed76c7bab9e55faca629e7524cd9fe22457c8fce15c04284a0b049\n"+
            Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n"+
            "Hardware: "+Build.HARDWARE+(Build.VERSION.SDK_INT>=31?" · "+Build.SOC_MODEL:"")+"\nABIs: "+String.join(", ",Build.SUPPORTED_ABIS)+"\n"+
            "Thermal status: "+(power==null?"unavailable":MetricsSampler.thermal(power.getCurrentThermalStatus()))+"\n"+
            "Sustained performance: "+(power!=null&&power.isSustainedPerformanceModeSupported()?"supported":"unavailable")+"\n";
    }
    static void write(Context c,OutputStream output,String session) throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            long[] total={0};add(zip,"device.txt",device(c).getBytes(StandardCharsets.UTF_8),total);
            add(zip,"capture.txt",("Exported "+new Date()+"\nSession: "+(session.isEmpty()?"latest available / launch reports":session)+"\nLarge logs are truncated to their startup and tail. No APK, saves or game assets are included.\n").getBytes(StandardCharsets.UTF_8),total);
            try {
                JSONObject settings=new JSONObject();for(Map.Entry<String,String> entry:StockApi.settings(c).entrySet())
                    if(SettingSchema.find(entry.getKey())!=null||Arrays.asList("gpu_plugin","saved_game_root","game_data_root").contains(entry.getKey()))settings.put(entry.getKey(),entry.getValue());
                settings.put("requested_driver",DriverStore.selected(c));settings.put("diagnostics_mode",c.getSharedPreferences("diagnostics_settings",0).getString("mode","off"));
                settings.put("queued_for_next_launch",new JSONObject(NextSettings.pending(c)));
                add(zip,"settings.json",settings.toString(2).getBytes(StandardCharsets.UTF_8),total);
            }catch(JSONException e){throw new IOException(e);}catch(IOException unavailable){add(zip,"settings-error.txt",FilesEx.error(unavailable).getBytes(StandardCharsets.UTF_8),total);}
            List<String> sessions=sessions(c);String selected=session.isEmpty()?(sessions.isEmpty()?"":sessions.get(0)):session;
            if(!selected.isEmpty()) {
                if(!sessions.contains(selected))throw new IOException("The selected session no longer exists.");
                File root=new File(StockApi.data(c),"debug_logs");addFile(zip,FilesEx.within(root,selected),"session/"+selected,total);
                File[] siblings=root.listFiles();int count=0;
                if(siblings!=null)for(File f:siblings)if(count<16&&f.isFile()&&f.getName().startsWith(selected+".")&&f.length()<ENTRY_LIMIT) {addFile(zip,f,"session/"+f.getName(),total);count++;}
            }
            for(String name:new String[]{"last_launch.txt","crash_report.txt","last_crash.txt"}) {
                File f=new File(StockApi.data(c),name);if(f.isFile())addFile(zip,f,"launch/"+name,total);
            }
            File javaCrash=new File(StockApi.layer(c),"last-java-crash.txt");if(javaCrash.isFile())addFile(zip,javaCrash,"launch/last-java-crash.txt",total);
            File[] reports=new File(StockApi.data(c),"crash_reports").listFiles((d,n)->n.endsWith(".txt")||n.endsWith(".json")||n.endsWith(".pb"));
            if(reports!=null){Arrays.sort(reports,Comparator.comparingLong(File::lastModified).reversed());for(int i=0;i<Math.min(4,reports.length);i++)if(reports[i].isFile())addFile(zip,reports[i],"crashes/"+reports[i].getName(),total);}
            runtime(c,zip,total);
            exits(c,zip,total);
        }
    }
    private static void runtime(Context c,ZipOutputStream zip,long[] total)throws IOException {
        StringBuilder index=new StringBuilder("Recent runtime files are supplementary. Their modification times are listed below; verify their age against the selected session before attributing a failure.\n");
        for(String folder:new String[]{"LibertyRecomp/logs","Liberty Recompiled/logs","logs"}) {
            File[] files=new File(StockApi.data(c),folder).listFiles((d,n)->n.endsWith(".log")||n.endsWith(".txt"));if(files==null)continue;
            Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
            for(int i=0;i<Math.min(2,files.length);i++)supplement(zip,files[i],"runtime-recent/"+folder.replace('/','-')+"/"+files[i].getName(),total,index);
        }
        File downloads=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        for(File file:new File[]{new File(StockApi.data(c),"gta4_boot.log"),new File(downloads,"gta4_boot.log"),new File(downloads,"game_cache/gta4_boot.log")})
            supplement(zip,file,"runtime-recent/boot-"+Math.abs(file.getAbsolutePath().hashCode())+".log",total,index);
        add(zip,"runtime-file-times.txt",index.toString().getBytes(StandardCharsets.UTF_8),total);
    }
    private static void supplement(ZipOutputStream zip,File file,String name,long[] total,StringBuilder index)throws IOException {
        try{if(!file.isFile())return;index.append(name).append(" · modified ").append(new Date(file.lastModified())).append(" · ").append(file.length()).append(" bytes\n");addFile(zip,file,name,total);}
        catch(IOException|SecurityException unavailable){index.append(name).append(" · unavailable: ").append(FilesEx.error(unavailable)).append('\n');}
    }
    private static void exits(Context c,ZipOutputStream zip,long[] total) throws IOException {
        if(Build.VERSION.SDK_INT<30){add(zip,"previous-exits.txt","Android 11+ is required for process exit history.\n".getBytes(StandardCharsets.UTF_8),total);return;}
        StringBuilder summary=new StringBuilder();
        try {
            ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits=manager==null?Collections.emptyList():manager.getHistoricalProcessExitReasons(null,0,6);
            int index=0;
            for(ApplicationExitInfo exit:exits) {
                summary.append(new Date(exit.getTimestamp())).append(" · ").append(exit.getProcessName()).append("\nreason ").append(exit.getReason()).append(" · status ").append(exit.getStatus()).append(" · importance ").append(exit.getImportance())
                    .append("\nPSS ").append(exit.getPss()).append(" KB · RSS ").append(exit.getRss()).append(" KB\n").append(exit.getDescription()).append("\n\n");
                try(InputStream trace=exit.getTraceInputStream()){long available=Math.min(ENTRY_LIMIT,TOTAL_LIMIT-total[0]-65536);if(trace!=null&&available>0)add(zip,"exit-traces/exit-"+(index++)+".trace",FilesEx.read(trace,available),total);}
                catch(IOException denied){summary.append("Trace unavailable: ").append(FilesEx.error(denied)).append('\n');}
            }
            if(exits.isEmpty())summary.append("No process exit history is available.\n");
        }catch(RuntimeException denied){summary.append("Exit history unavailable: ").append(FilesEx.error(denied));}
        add(zip,"previous-exits.txt",summary.toString().getBytes(StandardCharsets.UTF_8),total);
    }
    private static void addFile(ZipOutputStream zip,File f,String name,long[] total) throws IOException {
        FilesEx.regular(f);byte[] data;long limit=Math.min(ENTRY_LIMIT,TOTAL_LIMIT-total[0]-65536);if(limit<1024)return;
        try(RandomAccessFile in=new RandomAccessFile(f,"r")) {
            long size=in.length();if(size<=limit){data=new byte[(int)size];in.readFully(data);}
            else {int head=(int)Math.min(256*1024,limit/4),tail=(int)limit-head-80;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[head];in.readFully(b);out.write(b);out.write("\n[... log truncated; startup and tail retained ...]\n".getBytes(StandardCharsets.UTF_8));in.seek(size-tail);b=new byte[tail];in.readFully(b);out.write(b);data=out.toByteArray();}
        }
        add(zip,name,data,total);
    }
    private static void add(ZipOutputStream zip,String name,byte[] bytes,long[] total) throws IOException {
        if((total[0]+=bytes.length)>TOTAL_LIMIT)throw new IOException("Diagnostic export exceeds 24 MiB.");zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
    }
    static File share(Context c,String session) throws IOException {
        File folder=new File(c.getCacheDir(),"layer-share");Files.createDirectories(folder.toPath());
        File[] old=folder.listFiles();if(old!=null)for(File f:old)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>24*60*60*1000L)Files.deleteIfExists(f.toPath());
        File output=new File(folder,"XHYN-IV-diagnostics-"+UUID.randomUUID()+".zip");
        try(OutputStream out=new FileOutputStream(output)){write(c,out,session);}catch(IOException e){output.delete();throw e;}return output;
    }
    private Diagnostics(){}
}

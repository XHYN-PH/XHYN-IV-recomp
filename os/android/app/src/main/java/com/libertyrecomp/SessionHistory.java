package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Bounded local session/benchmark records. Measurements are never synthesized from window percentiles. */
final class SessionHistory {
    private static final int LIMIT=65536, KEEP=24;
    static File directory(File data){return new File(data,"LibertyRecomp/diagnostics");}
    static Properties load(File file)throws IOException {
        Properties p=new Properties();if(!file.isFile())return p;
        if(Files.isSymbolicLink(file.toPath())||file.length()>LIMIT)throw new IOException("Invalid diagnostic record.");
        try(InputStream in=new FileInputStream(file)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>LIMIT)throw new IOException("Diagnostic record too large.");bytes.write(b,0,n);}p.load(new ByteArrayInputStream(bytes.toByteArray()));}catch(IllegalArgumentException error){throw new IOException("Invalid diagnostic record encoding.",error);}return p;
    }
    static synchronized void save(File data,String id,Properties values)throws IOException {
        if(!id.matches("(?:last-session|previous-exit|previous-launcher-exit|session-[0-9-]+|benchmark-[0-9-]+)"))throw new IOException("Invalid diagnostic identity.");
        File folder=directory(data);Files.createDirectories(folder.toPath());if(Files.isSymbolicLink(folder.toPath()))throw new IOException("Invalid diagnostic folder.");
        File target=new File(folder,id+".properties");if(Files.isSymbolicLink(target.toPath()))throw new IOException("Invalid diagnostic path.");
        Properties bounded=new Properties();if(values.size()>256)throw new IOException("Too many diagnostic fields.");
        for(String k:values.stringPropertyNames()){if(!k.matches("[a-zA-Z0-9_.-]{1,96}"))throw new IOException("Invalid diagnostic field.");String v=values.getProperty(k,"");bounded.setProperty(k,v.substring(0,Math.min(1024,v.length())));}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bounded.store(bytes,"XHYN IV local diagnostic record");if(bytes.size()>LIMIT)throw new IOException("Diagnostic record too large.");
        File temporary=File.createTempFile("record-",".tmp",folder);try{try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes.toByteArray());out.getFD().sync();}Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{temporary.delete();}
        File[] records=folder.listFiles((d,n)->n.matches("(?:session|benchmark)-[0-9-]+\\.properties"));if(records!=null&&records.length>KEEP){Arrays.sort(records,Comparator.comparingLong(File::lastModified).reversed());for(int i=KEEP;i<records.length;i++)Files.deleteIfExists(records[i].toPath());}
    }
    static List<Properties> records(File data)throws IOException {
        List<Properties> result=new ArrayList<>();File[] files=directory(data).listFiles((d,n)->n.matches("(?:session|benchmark)-[0-9-]+\\.properties"));
        if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File f:files){if(result.size()==KEEP)break;try{Properties p=load(f);if(!p.isEmpty())result.add(p);}catch(IOException ignored){}}}return result;
    }
    /** Recover a completed run before automatic log cleanup, including an immediate quit after completion. */
    static void recoverBenchmark(File data,File fallback)throws IOException {
        Properties session=load(new File(directory(data),"last-session.properties"));
        String name=session.getProperty("log.session",""),started=session.getProperty("started","");
        if(!name.matches("debug-[0-9-]+\\.log")||!started.matches("[0-9]+"))return;
        File runtimeLog=new File(new File(data,"debug_logs"),name+".runtime.log");
        if(!runtimeLog.isFile())runtimeLog=new File(new File(fallback,"debug_logs"),name+".runtime.log");
        File status=new File(runtimeLog+".benchmark.status.txt");if(!tail(status,4096).startsWith("complete:"))return;
        String id="benchmark-"+started+"-"+status.lastModified();if(new File(directory(data),id+".properties").isFile())return;
        Properties result=completedBenchmark(runtimeLog,session);if(result.isEmpty())return;
        session.putAll(result);session.setProperty("sampled",Long.toString(status.lastModified()));save(data,id,session);
    }
    static String tail(File file,int maximum)throws IOException {if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return "";try(RandomAccessFile in=new RandomAccessFile(file,"r")){int size=(int)Math.min(in.length(),maximum);byte[] bytes=new byte[size];in.seek(in.length()-size);in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}}
    static Properties benchmark(String text){
        Properties p=new Properties();int start=text.lastIndexOf("gta4-benchmark: start"),end=text.lastIndexOf("gta4-benchmark: complete");
        if(end<0||start>end)return p;int newline=text.indexOf('\n',end);String row=text.substring(end,newline<0?text.length():newline);
        Matcher fields=Pattern.compile("(measured_s|published_fps|interval_p50_ms|interval_p95_ms|interval_max_ms|failed|over_50ms)=([0-9]+(?:\\.[0-9]+)?)").matcher(row);
        while(fields.find())p.setProperty("benchmark."+fields.group(1),fields.group(2));
        if(!validBenchmark(p))return new Properties();
        p.setProperty("kind","benchmark");p.setProperty("metric","game frame publications");p.setProperty("quantiles","up to 32768 measured intervals");return p;
    }
    static double number(Properties p,String key,double fallback){try{double v=Double.parseDouble(p.getProperty(key,""));return Double.isFinite(v)?v:fallback;}catch(NumberFormatException error){return fallback;}}
    static String differences(Properties a,Properties b){
        TreeSet<String> keys=new TreeSet<>(Arrays.asList("driver.actual","upscaler.actual","present.actual","cache.phase","profile.configured","thermal.start","battery.start_c","route"));
        for(Properties p:new Properties[]{a,b})for(String key:p.stringPropertyNames())if(key.startsWith("configured."))keys.add(key);
        StringBuilder text=new StringBuilder();for(String key:keys)if(!Objects.equals(a.getProperty(key),b.getProperty(key)))text.append(key.replace("configured.","")).append(": ").append(a.getProperty(key,"unknown")).append(" → ").append(b.getProperty(key,"unknown")).append('\n');return text.toString();
    }
    static void writeSidecar(File file,Properties values)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();values.store(bytes,"XHYN IV benchmark start");
        if(bytes.size()>LIMIT)throw new IOException("Benchmark start record is too large.");
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Invalid benchmark sidecar.");
        File temporary=File.createTempFile(file.getName()+".tmp-",".tmp",file.getParentFile());
        try{
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes.toByteArray());out.getFD().sync();}
            Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{temporary.delete();}
    }
    private static Properties nativeProperties(File file)throws IOException{
        Properties result=new Properties();
        if(!file.isFile())return result;
        if(Files.isSymbolicLink(file.toPath())||file.length()>LIMIT)throw new IOException("Invalid benchmark sidecar.");
        try(InputStream input=new FileInputStream(file)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
            while((n=input.read(buffer))!=-1){if(bytes.size()+n>LIMIT)throw new IOException("Benchmark sidecar is too large.");bytes.write(buffer,0,n);}
            try(Reader reader=new InputStreamReader(new ByteArrayInputStream(bytes.toByteArray()),StandardCharsets.UTF_8)){result.load(reader);}
        }catch(IllegalArgumentException e){throw new IOException("Invalid benchmark sidecar.",e);}
        return result;
    }
    static Properties completedBenchmark(File runtimeLog,Properties session)throws IOException{
        Properties metrics=nativeProperties(new File(runtimeLog+".benchmark.result.properties"));
        if(!validBenchmark(metrics))metrics=benchmark(tail(runtimeLog,131072));
        if(!validBenchmark(metrics))return new Properties();
        Properties start=nativeProperties(new File(runtimeLog+".benchmark.settings.txt"));
        Properties end=nativeProperties(new File(runtimeLog+".benchmark.conditions.txt"));
        Properties ui=load(new File(runtimeLog+".benchmark.ui-start.properties"));
        Properties result=new Properties();result.putAll(session);
        boolean uiVerified=!start.getProperty("run.trigger","").isEmpty()&&start.getProperty("run.trigger").equals(ui.getProperty("benchmark.requested_trigger"));
        if(uiVerified)result.putAll(ui);
        result.putAll(metrics);result.setProperty("kind","benchmark");
        for(String key:start.stringPropertyNames())if(!key.equals("schema")&&!key.startsWith("conditions."))result.setProperty("native.start."+key,start.getProperty(key));
        boolean verified="2".equals(start.getProperty("schema"))&&end.containsKey("conditions.mixed")&&start.getProperty("run.trigger","").equals(end.getProperty("run.trigger"));
        result.setProperty("conditions.verified",Boolean.toString(verified));
        result.setProperty("ui.start.verified",Boolean.toString(uiVerified));
        result.setProperty("conditions.monitor",start.getProperty("conditions.monitor","unknown"));
        for(String key:end.stringPropertyNames())if(key.startsWith("conditions.")||key.startsWith("capture."))result.setProperty(key,end.getProperty(key));
        long finished=(long)number(end,"run.finished_ms",0);
        result.setProperty("benchmark.finished_ms",Long.toString(finished));
        for(String[] field:new String[][]{{"benchmark.interrupted","benchmark.background_ms"},{"benchmark.ui_changed","benchmark.ui_changed_ms"}}){
            long changed=(long)number(session,field[1],0);
            boolean during="true".equals(session.getProperty(field[0]))&&(finished<=0||changed<=0||changed<=finished);
            result.setProperty(field[0],Boolean.toString(during));
        }
        result.setProperty("metric","game frame publications");result.setProperty("quantiles","up to 32768 measured intervals");
        return result;
    }
    private static boolean validBenchmark(Properties p){
        for(String key:new String[]{"measured_s","published_fps","interval_p50_ms","interval_p95_ms","interval_max_ms","failed"})
            if(!p.containsKey("benchmark."+key)||number(p,"benchmark."+key,-1)<0)return false;
        return number(p,"benchmark.measured_s",0)>=59;
    }
    static String comparisonQuality(Properties p){
        List<String> issues=new ArrayList<>();
        if(!"true".equals(p.getProperty("conditions.verified")))issues.add("native start/end conditions are incomplete");
        if(!"true".equals(p.getProperty("ui.start.verified")))issues.add("the UI start snapshot is unverified");
        if("true".equals(p.getProperty("conditions.mixed")))issues.add("native settings changed: "+p.getProperty("conditions.changed","unknown"));
        if("true".equals(p.getProperty("benchmark.interrupted")))issues.add("the run was interrupted");
        if("true".equals(p.getProperty("benchmark.ui_changed")))issues.add("UI performance settings changed");
        if("true".equals(p.getProperty("capture.overlap")))issues.add("detailed capture overlapped; omissions: "+p.getProperty("capture.omissions","unknown"));
        if(p.getProperty("route","").trim().isEmpty())issues.add("route was not labelled");
        if("unspecified".equals(p.getProperty("cache.phase","unspecified")))issues.add("cache condition was not labelled");
        return String.join("; ",issues);
    }
    private SessionHistory(){}
}

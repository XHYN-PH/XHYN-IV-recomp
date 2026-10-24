package com.libertyrecomp;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Creates a real session file before starting SDL, even if logcat is denied. */
final class DebugLog {
    private static final String TAG="LibertyDebugLog";
    static final String DIRECTORY="debug_logs";
    private static DebugLogSink sink;
    private static Process capture;
    private static AtomicBoolean stopped;
    private DebugLog() {}
    static File directory(File root){return new File(root,DIRECTORY);}
    static File start(File root,File fallback) {
        return start(root,fallback,new String[]{"/system/bin/logcat","-v","threadtime","-b","main",
                    "-b","system","-b","crash","-T",
                    new SimpleDateFormat("MM-dd HH:mm:ss.SSS",Locale.ROOT).format(new Date())});
    }
    static synchronized File start(File root,File fallback,String[] command) {
        stop();stopLeftovers(root);
        // Automatic one-session cleanup: users do not need to manage old logs.
        // The small OS exit/session summaries live separately and replace themselves.
        try{DebugLogArchive.clear(root,fallback);}catch(IOException error){Log.w(TAG,"Some old diagnostics could not be removed",error);}
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.ROOT).format(new Date());
        String header="XHYN IV debug session "+stamp+"\npid="+android.os.Process.myPid()+"\n";
        for(File base:new File[]{root,fallback}) {
            try {
                sink=new DebugLogSink(new File(directory(base),"debug-"+stamp+".log"),8*1024*1024,header);
                prune(directory(base),sink.file);break;
            } catch(IOException error) { Log.e(TAG,"Cannot create session log in "+base,error); }
        }
        if(sink==null) return null;
        final DebugLogSink session=sink;
        final AtomicBoolean finished=new AtomicBoolean(false);stopped=finished;
        try {
            capture=new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process child=capture;
            Thread reader=new Thread(()-> {
                try(InputStream input=child.getInputStream()) {
                    byte[] bytes=new byte[8192];int count;
                    while((count=input.read(bytes))!=-1 && !finished.get()) session.append(bytes,count);
                    byte[] end=("\nlogcat stopped (exit "+child.waitFor()+")\n").getBytes(StandardCharsets.UTF_8);
                    if(!finished.get()) session.append(end,end.length);
                } catch(IOException error) { if(!finished.get())append(session,"\nLogcat read failed: "+error+"\n"); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); }
                finally { child.destroy(); }
            },"DebugLogCapture");
            reader.setDaemon(true);reader.start();
        } catch(IOException error) { append(session,"logcat could not start: "+error+"\n"); }
        return session.file;
    }
    static synchronized void note(String text) { if(sink!=null) append(sink,text+"\n"); }
    private static void append(DebugLogSink target,String text) {
        try {byte[] bytes=text.getBytes(StandardCharsets.UTF_8);target.append(bytes,bytes.length);}
        catch(IOException error) {Log.e(TAG,"Writing session log failed",error);}
    }
    static synchronized void stop() {
        if(stopped!=null) {stopped.set(true);stopped=null;}
        if(capture!=null) {capture.destroy();capture=null;}
        if(sink!=null) {try{sink.close();}catch(IOException ignored){}sink=null;}
    }
    static void stopLeftovers(File root) {
        String marker=directory(root).getAbsolutePath();
        File[] processes=new File("/proc").listFiles();if(processes==null)return;
        for(File process:processes) {
            String name=process.getName();if(name.isEmpty() || !Character.isDigit(name.charAt(0)))continue;
            String command=read(new File(process,"cmdline"));
            if(command.startsWith("logcat") && command.contains(marker))
                try{android.os.Process.sendSignal(Integer.parseInt(name),android.os.Process.SIGNAL_KILL);}
                catch(RuntimeException ignored){}
        }
        // A pipe capture from a crashed game has no reader. This same-UID
        // message makes a waiting child exit on its broken stdout pipe.
        Log.i(TAG,"Debug capture cleanup");
    }
    private static void prune(File directory,File current) {
        File[] files=directory.listFiles((d,n)->n.matches("debug-[0-9-]+\\.log")&&!n.equals(current.getName()));
        if(files==null)return;
        for(File old:files){
            final String prefix=old.getName();File[] related=directory.listFiles((d,n)->n.equals(prefix) || n.equals(prefix+".1") || n.startsWith(prefix+".runtime"));
            if(related!=null)for(File file:related) {
                if(file.isDirectory())try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(file.toPath())) {
                    for(java.nio.file.Path path:paths.sorted(java.util.Comparator.reverseOrder()).toArray(java.nio.file.Path[]::new))java.nio.file.Files.deleteIfExists(path);
                }catch(IOException error){Log.w(TAG,"Could not remove an old session profile",error);}
                else file.delete();
            }
        }
    }
    private static String read(File file) {
        try(InputStream input=new FileInputStream(file)) {
            byte[] bytes=new byte[4096];int count=input.read(bytes);
            return count>0 ? new String(bytes,0,count,StandardCharsets.UTF_8).replace('\0',' ') : "";
        }catch(IOException ignored){return "";}
    }
}

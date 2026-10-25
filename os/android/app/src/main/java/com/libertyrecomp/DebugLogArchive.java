package com.libertyrecomp;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DebugLogArchive {
    private static final long LIMIT=2*1024*1024;
    private static final long STARTUP=256*1024;
    /** Delete only app-owned diagnostics, including their rotated files and profiles. */
    static void clear(File primary,File fallback) throws IOException {
        for(File root:new File[]{primary,fallback}) {
            java.nio.file.Files.deleteIfExists(LauncherCrashReport.file(root).toPath());
            File directory=new File(root,DebugLog.DIRECTORY);
            File[] files=directory.listFiles();if(files==null)continue;
            for(File file:files) {
                if(!file.getName().matches("debug-[0-9-]+\\.log(?:\\..*)?"))continue;
                deleteOwned(file);
            }
        }
    }
    private static void deleteOwned(File file) throws IOException {
        if(java.nio.file.Files.isSymbolicLink(file.toPath())) {
            java.nio.file.Files.deleteIfExists(file.toPath());return;
        }
        if(file.isDirectory()) {
            File[] children=file.listFiles();
            if(children==null)throw new IOException("Cannot read old log folder.");
            for(File child:children)deleteOwned(child);
        }
        java.nio.file.Files.deleteIfExists(file.toPath());
    }
    static String[] sessions(File primary,File fallback) {
        java.util.TreeSet<String> names=new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for(File root:new File[]{primary,fallback}) {
            File[] files=new File(root,"debug_logs").listFiles();if(files==null)continue;
            for(File f:files)if(f.isFile()&&f.getName().matches("debug-[0-9-]+\\.log"))names.add(f.getName());
        }
        return names.toArray(new String[0]);
    }
    static void write(OutputStream output,File primary,File fallback) throws IOException {
        String[] names=sessions(primary,fallback);
        if(names.length==0)throw new IOException("Start a debug session first.");
        write(output,primary,fallback,names[0]);
    }
    static void write(OutputStream output,File primary,File fallback,String session) throws IOException {
        if("launcher".equals(session)){
            if(!LauncherCrashReport.available(primary))throw new IOException("The launcher report is no longer available.");
            try(ZipOutputStream zip=new ZipOutputStream(output)){add(zip,LauncherCrashReport.file(primary),"launcher/launcher-crash.txt");}
            return;
        }
        if(session==null||!session.matches("debug-[0-9-]+\\.log"))throw new IOException("Invalid session.");
        File folder=new File(primary,"debug_logs");
        if(!new File(folder,session).isFile())folder=new File(fallback,"debug_logs");
        if(!new File(folder,session).isFile())throw new IOException("The selected session is no longer available.");
        final String selected=session;
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            File[] files=folder.listFiles((dir,name)->name.equals(selected)||name.equals(selected+".1")||name.startsWith(selected+".runtime")||name.equals(selected+".launch.txt"));
            if(files!=null) {
                Arrays.sort(files,Comparator.comparing(File::getName));
                for(File f:files) {
                    if(f.isFile()&&!f.getName().endsWith(".partial"))add(zip,f,"session/"+f.getName());
                    else if(f.isDirectory()&&new File(f,"capture-complete.txt").isFile())profile(zip,f,"session/"+f.getName()+"/",0);
                }
            }
            File diagnostics=SessionHistory.directory(primary);
            for(String name:new String[]{"last-session.properties","previous-exit.properties","previous-launcher-exit.properties","launcher-crash.txt"}){File f=new File(diagnostics,name);if(f.isFile()&&!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.length()<=65536)add(zip,f,"context/"+name);}
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("XHYN IV diagnostics. Selected session: "+session+"\nOnly the selected session logs are included. The context folder contains timestamped last-session and historical Android exit summaries, which may describe a different session. Large text logs retain startup and the latest entries with an omission marker. Publisher frame rate is not a measurement of displayed frames; publisher wall time includes waits. CSV benchmark data is kept complete.\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    private static void profile(ZipOutputStream zip,File folder,String prefix,int depth)throws IOException {
        if(depth>3)return;File[] files=folder.listFiles();if(files==null)return;
        for(File file:files) {
            if(java.nio.file.Files.isSymbolicLink(file.toPath())||file.getName().endsWith(".partial"))continue;
            if(file.isDirectory())profile(zip,file,prefix+file.getName()+"/",depth+1);
            else if(file.isFile())add(zip,file,prefix+file.getName());
        }
    }
    private static void add(ZipOutputStream zip,File file,String name) throws IOException {
        if(!file.isFile())return;
        try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
            long length=input.length();
            zip.putNextEntry(new ZipEntry(name));
            boolean structured=name.endsWith(".csv")||name.endsWith(".json");
            if(structured&&length>64L*1024*1024)throw new IOException("Profile file exceeds export limit.");
            if(length>LIMIT&&!structured) {
                copy(zip,input,STARTUP);
                zip.write("\n\n--- Middle of large log omitted; startup and latest entries retained. ---\n\n".getBytes(StandardCharsets.UTF_8));
                input.seek(length-(LIMIT-STARTUP));
                copy(zip,input,LIMIT-STARTUP);
            } else copy(zip,input,length);
            zip.closeEntry();
        }
    }
    private static void copy(ZipOutputStream zip,RandomAccessFile input,long remaining) throws IOException {
        byte[] bytes=new byte[8192];int count;
        while(remaining>0 && (count=input.read(bytes,0,(int)Math.min(bytes.length,remaining)))!=-1) {
            zip.write(bytes,0,count);remaining-=count;
        }
    }
    private DebugLogArchive(){}
}

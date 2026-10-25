package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One bounded launcher failure, separate from opt-in gameplay recording. */
final class LauncherCrashReport {
    static File file(File data){return new File(SessionHistory.directory(data),"launcher-crash.txt");}
    static boolean available(File data){File f=file(data);return f.isFile()&&!Files.isSymbolicLink(f.toPath())&&f.length()>0&&f.length()<=65536;}
    static void save(File data,Throwable error)throws IOException {
        StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
        String text="XHYN IV launcher failure\nTimestamp: "+System.currentTimeMillis()+"\n"+trace;
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);File target=file(data);
        Files.createDirectories(target.getParentFile().toPath());
        File temporary=File.createTempFile("launcher-crash-",".tmp",target.getParentFile());
        try{try(FileOutputStream out=new FileOutputStream(temporary)){out.write(bytes,0,Math.min(bytes.length,65536));out.getFD().sync();}
            Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }finally{temporary.delete();}
    }
    private LauncherCrashReport(){}
}

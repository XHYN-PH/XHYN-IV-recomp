package com.libertyrecomp;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** The app owns the output file; a logcat child never needs storage access. */
final class DebugLogSink implements Closeable {
    final File file;
    private final long maximum;
    private FileOutputStream output;
    private long size;
    DebugLogSink(File file,long maximum,String header) throws IOException {
        if(maximum<1) throw new IllegalArgumentException("maximum");
        this.file=file;this.maximum=maximum;
        Files.createDirectories(file.getParentFile().toPath());
        output=new FileOutputStream(file);
        byte[] bytes=header.getBytes(StandardCharsets.UTF_8);
        try{append(bytes,bytes.length);}catch(IOException error){output.close();output=null;throw error;}
    }
    synchronized void append(byte[] bytes,int count) throws IOException {
        if(output==null) throw new IOException("Log is closed");
        if(count<0 || count>bytes.length) throw new IllegalArgumentException("count");
        int offset=0;
        while(offset<count) {
            if(size>=maximum) {
                output.close();
                Files.move(file.toPath(),new File(file.getPath()+".1").toPath(),StandardCopyOption.REPLACE_EXISTING);
                output=new FileOutputStream(file);size=0;
            }
            int length=(int)Math.min(count-offset,maximum-size);
            output.write(bytes,offset,length);offset+=length;size+=length;
        }
        output.flush();
    }
    @Override public synchronized void close() throws IOException {
        if(output!=null) { output.close();output=null; }
    }
}

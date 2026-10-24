package com.libertyrecomp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class AndroidLogFilesTest {
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("xhyn-log-test").toFile();
        File blocked=new File(root,"blocked");Files.write(blocked.toPath(),new byte[]{1});
        File fallback=new File(root,"fallback");
        File session=DebugLog.start(blocked,fallback);
        assert session!=null && session.isFile() && session.length()>0;
        DebugLog.note("early driver facts");DebugLog.stop();
        String text=Files.readString(session.toPath());
        assert text.contains("XHYN IV debug session") && text.contains("early driver facts");
        assert text.contains("logcat could not start");
        File denied=DebugLog.start(root,fallback,new String[]{"java","-cp",System.getProperty("java.class.path"),"com.libertyrecomp.FakeLogcat"});
        long deadline=System.nanoTime()+3_000_000_000L;
        do {text=Files.readString(denied.toPath());if(text.contains("exit 2"))break;Thread.sleep(10);}
        while(System.nanoTime()<deadline);
        assert text.contains("permission denied") && text.contains("exit 2");DebugLog.stop();
        assert !session.exists():"Starting a new logged session must remove fallback logs";
        File broken2=new File(root,"also-blocked");Files.write(broken2.toPath(),new byte[]{1});
        assert DebugLog.start(blocked,broken2)==null;
        File rotated=new File(root,"rotate/debug.log");
        byte[] bytes=new byte[100];for(int i=0;i<100;++i)bytes[i]=(byte)i;
        try(DebugLogSink sink=new DebugLogSink(rotated,32,"header\n")) {
            assert rotated.length()>0;sink.append(bytes,bytes.length);
        }
        assert rotated.length()<=32 && new File(rotated.getPath()+".1").length()<=32;
        byte[] newest=Files.readAllBytes(rotated.toPath());assert newest[newest.length-1]==99;
        File settings=new File(root,"settings");assert !TouchLayoutSettings.xbox(settings);
        TouchLayoutSettings.save(settings,true);assert TouchLayoutSettings.xbox(settings);
        TouchLayoutSettings.save(settings,false);assert !TouchLayoutSettings.xbox(settings);
        assert Objects.requireNonNull(settings.listFiles()).length==1;
        boolean failure=false;try{TouchLayoutSettings.save(blocked,true);}catch(IOException expected){failure=true;}
        assert failure && Files.readAllBytes(blocked.toPath())[0]==1;
        String chosen="debug-20990101-000000-000.log";
        File logs=new File(root,"debug_logs");logs.mkdirs();
        Files.writeString(new File(logs,chosen).toPath(),"chosen session");
        File large=new File(logs,chosen+".runtime.log");
        try(RandomAccessFile log=new RandomAccessFile(large,"rw")) {
            log.write("STARTUP shader/device details\n".getBytes(StandardCharsets.UTF_8));
            log.setLength(3*1024*1024);log.seek(log.length()-30);
            log.write("LATEST frame failure details\n".getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        DebugLogArchive.write(output,root,fallback,chosen);
        Set<String> names=new HashSet<>();boolean retained=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                assert names.add(entry.getName());
                if(entry.getName().equals("session/"+chosen+".runtime.log")) {
                    byte[] content=zip.readAllBytes();text=new String(content,StandardCharsets.UTF_8);
                    assert content.length<2*1024*1024+128;
                    assert text.startsWith("STARTUP shader/device details");
                    assert text.contains("Middle of large log omitted")&&text.contains("LATEST frame failure details");retained=true;
                }
            }
        }
        assert retained&&names.contains("README.txt")&&names.size()==3;
        failure=false;try {DebugLogArchive.write(new OutputStream(){public void write(int value)throws IOException{throw new IOException("full");}},root,fallback);}
        catch(IOException expected){failure=true;}assert failure;
        File profile=new File(logs,chosen+".runtime.log.profile/frames");assert profile.mkdirs();
        Files.writeString(new File(profile,"capture.json").toPath(),"old profile");
        Files.writeString(new File(logs,chosen+".1").toPath(),"old rotated log");
        File unrelated=new File(logs,"notes.txt");Files.writeString(unrelated.toPath(),"keep user files");
        File similarName=new File(logs,"debug-notes.log");Files.writeString(similarName.toPath(),"also keep user files");
        File latest=DebugLog.start(root,fallback,new String[]{"unavailable-logcat-for-host-test"});DebugLog.stop();
        assert latest!=null&&latest.isFile();
        assert !new File(logs,chosen).exists()&&!large.exists()&&!profile.exists();
        assert !new File(logs,chosen+".1").exists()&&unrelated.isFile()&&similarName.isFile();
        assert DebugLogArchive.sessions(root,fallback).length==1:"Only the latest log session should remain";
        System.out.println("Logcat startup failure/fallback, early file writes, bounded rotation, export failures and layout persistence passed");
    }
}
class FakeLogcat {
    public static void main(String[] args){System.err.println("permission denied");System.exit(2);}
}

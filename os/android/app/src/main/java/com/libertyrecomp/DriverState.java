package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Driver-scoped choices and observed startup history, separate from gameplay certification. */
final class DriverState {
    static String normalize(String mode){
        if(mode==null||mode.trim().isEmpty())return "auto";
        mode=mode.trim();
        for(int i=0;i<mode.length();i++)if(mode.charAt(i)<32||mode.charAt(i)==127)return "auto";
        if(mode.regionMatches(true,0,"custom:",0,7)){
            String name=mode.substring(7).trim();
            if(name.isEmpty()||name.contains("/")||name.contains("\\")||name.contains(".."))return "auto";
            return "custom:"+name;
        }
        String normalized=mode.toLowerCase(Locale.ROOT);return normalized.equals("turnip_v38")?"auto":normalized;
    }
    static String readChoice(File data){
        File file=new File(data,"driver.txt");
        try{
            if(file.length()<=4096&&!Files.isSymbolicLink(file.toPath()))try(InputStream input=new FileInputStream(file)){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
                while((n=input.read(buffer))!=-1){if(bytes.size()+n>4096)return "auto";bytes.write(buffer,0,n);}
                return normalize(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
        }catch(IOException ignored){}
        return "auto";
    }
    static void choose(File data,String mode)throws IOException{
        write(new File(data,"driver.txt"),(normalize(mode)+"\n").getBytes(StandardCharsets.UTF_8));
    }
    private static String id(String mode){
        try{
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(normalize(mode).getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static File profile(File data,String mode){return new File(data,"LibertyRecomp/driver-options/"+id(mode)+".toml");}
    static Map<String,String> options(File data,String mode)throws IOException{
        return MobileConfig.read(profile(data,mode));
    }
    static synchronized void migrateLegacy(File data,String mode)throws IOException{
        File marker=new File(data,"LibertyRecomp/driver-options/legacy.properties"),legacy=new File(data,"LibertyRecomp/turnip-options.toml");
        if(marker.isFile()||!legacy.isFile())return;
        File target=profile(data,mode);
        if(!target.exists())MobileConfig.update(target,MobileConfig.read(legacy));
        Properties migrated=new Properties();migrated.setProperty("profile",normalize(mode));save(marker,migrated);
    }
    static void saveOptions(File data,String mode,Map<String,String> values)throws IOException{
        MobileConfig.update(profile(data,mode),values);
    }
    static Properties observation(File data,String mode)throws IOException{
        return load(new File(data,"LibertyRecomp/driver-history/"+id(mode)+".properties"));
    }
    static void observe(File data,String requested,String loaded,String nativeStatus)throws IOException{
        Properties values=new Properties();values.setProperty("requested",normalize(requested));
        values.setProperty("loaded",normalize(loaded));values.setProperty("native",nativeStatus);
        values.setProperty("observed",Long.toString(System.currentTimeMillis()));
        save(new File(data,"LibertyRecomp/driver-history/"+id(requested)+".properties"),values);
    }
    static void working(File data,String mode,String device,int api,String actual)throws IOException{
        Properties values=new Properties();values.setProperty("mode",normalize(mode));values.setProperty("device",device);
        values.setProperty("api",Integer.toString(api));values.setProperty("actual",actual);
        values.setProperty("observed",Long.toString(System.currentTimeMillis()));
        save(new File(data,"LibertyRecomp/last-working-driver.properties"),values);
    }
    static String lastWorking(File data,String device,int api){
        try{
            Properties values=load(new File(data,"LibertyRecomp/last-working-driver.properties"));
            if(device.equals(values.getProperty("device"))&&Integer.toString(api).equals(values.getProperty("api")))
                return normalize(values.getProperty("mode"));
        }catch(IOException ignored){}
        return "";
    }
    static void forget(File data,String mode)throws IOException{
        Files.deleteIfExists(new File(data,"LibertyRecomp/driver-history/"+id(mode)+".properties").toPath());
        File last=new File(data,"LibertyRecomp/last-working-driver.properties");
        Properties values=load(last);
        if(normalize(mode).equals(values.getProperty("mode")))Files.deleteIfExists(last.toPath());
    }
    static Properties load(File file)throws IOException{
        Properties result=new Properties();
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return result;
        if(Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()>65536)throw new IOException("Invalid driver history.");
        try(InputStream input=new FileInputStream(file)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
            while((n=input.read(buffer))!=-1){if(bytes.size()+n>65536)throw new IOException("Driver history is too large.");bytes.write(buffer,0,n);}
            try{result.load(new ByteArrayInputStream(bytes.toByteArray()));}catch(IllegalArgumentException e){throw new IOException("Invalid driver history.",e);}
        }
        return result;
    }
    private static void save(File target,Properties values)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();values.store(bytes,"XHYN IV observed driver state");write(target,bytes.toByteArray());
    }
    private static void write(File target,byte[] bytes)throws IOException{
        if(bytes.length>65536)throw new IOException("Driver record is too large.");
        Files.createDirectories(target.getParentFile().toPath());
        if(Files.isSymbolicLink(target.toPath())||Files.isSymbolicLink(target.getParentFile().toPath()))throw new IOException("Driver state cannot be a symbolic link.");
        File temporary=File.createTempFile("driver-state-",".tmp",target.getParentFile());
        try{
            try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);output.getFD().sync();}
            try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{temporary.delete();}
    }
    private DriverState(){}
}


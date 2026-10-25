package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installs immutable UI glyphs where Android GetUserFolder()/native.toml lives. */
final class TouchIconAssets {
    interface Source {
        String[] names() throws IOException;
        InputStream open(String name) throws IOException;
    }
    private static final byte[] PNG={(byte)137,80,78,71,13,10,26,10};
    static File directory(File appFiles){return new File(new File(appFiles,"LibertyRecomp"),"touch-icons");}
    private static boolean valid(File file){
        if(!file.isFile()||file.length()<24||file.length()>4194304)return false;
        try(InputStream in=new FileInputStream(file)){byte[] header=new byte[8];return in.read(header)==8&&Arrays.equals(header,PNG);}catch(IOException e){return false;}
    }
    private static byte[] bounded(InputStream input)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(out.size()+n>4194304)throw new IOException("Touch icon too large");out.write(buffer,0,n);}return out.toByteArray();
    }
    static File sync(File appFiles,Source source,String stamp) throws IOException {
        File target=directory(appFiles),marker=new File(target,".version");String[] names=source.names();
        if(names==null||names.length==0||names.length>256)throw new IOException("Invalid touch icon catalog");
        Set<String> unique=new HashSet<>();
        for(String name:names)if(name==null||!name.matches("[a-z0-9_]+\\.png")||!unique.add(name))throw new IOException("Invalid touch icon filename");
        boolean complete=false;
        if(marker.isFile()&&marker.length()<256){complete=stamp.equals(new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8));}
        if(complete){for(String name:names){File current=new File(target,name);if(!valid(current)){complete=false;break;}try(InputStream bundled=source.open(name);InputStream installed=new FileInputStream(current)){byte[] expected=bounded(bundled),actual=bounded(installed);if(!Arrays.equals(expected,actual)){complete=false;break;}}}}
        if(complete)return target;
        if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Cannot create touch icon folder");
        Files.deleteIfExists(marker.toPath());
        for(String name:names){
            File temporary=File.createTempFile(".icon-",".tmp",target);
            try{
                try(InputStream in=source.open(name);OutputStream out=new FileOutputStream(temporary)){
                    byte[] buffer=new byte[8192];int n,total=0;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>4194304)throw new IOException("Touch icon too large");out.write(buffer,0,n);}
                }
                if(!valid(temporary))throw new IOException("Invalid bundled touch icon: "+name);
                Files.move(temporary.toPath(),new File(target,name).toPath(),StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temporary.toPath());}
        }
        File temporary=File.createTempFile(".version-",".tmp",target);
        try{Files.write(temporary.toPath(),stamp.getBytes(StandardCharsets.UTF_8));Files.move(temporary.toPath(),marker.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temporary.toPath());}
        return target;
    }
    private TouchIconAssets(){}
}

package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TouchIconAssetsTest {
    static final byte[] IMAGE=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
    static TouchIconAssets.Source source(boolean fail){return new TouchIconAssets.Source(){
        public String[] names(){return new String[]{"aim.png","gas.png"};}
        public InputStream open(String name)throws IOException{if(fail&&name.equals("gas.png"))throw new IOException("interrupted copy");return new ByteArrayInputStream(IMAGE);}
    };}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("touch-icons-test"),internal=root.resolve("internal/files"),external=root.resolve("external/files");Files.createDirectories(internal);Files.createDirectories(external);
        File target=TouchIconAssets.sync(internal.toFile(),source(false),"30:1");
        assert target.toPath().equals(internal.resolve("LibertyRecomp/touch-icons"));
        assert !Files.exists(external.resolve("LibertyRecomp/touch-icons"));
        for(String name:source(false).names())assert Arrays.equals(Files.readAllBytes(target.toPath().resolve(name)),IMAGE);
        Files.delete(target.toPath().resolve("gas.png"));TouchIconAssets.sync(internal.toFile(),source(false),"30:1");assert Files.isRegularFile(target.toPath().resolve("gas.png"));
        Files.write(target.toPath().resolve("aim.png"),new byte[40]);TouchIconAssets.sync(internal.toFile(),source(false),"30:1");assert Arrays.equals(Files.readAllBytes(target.toPath().resolve("aim.png")),IMAGE);
        try{TouchIconAssets.sync(internal.toFile(),source(true),"30:2");throw new AssertionError("Expected interrupted copy");}catch(IOException expected){}
        assert !Files.exists(target.toPath().resolve(".version"));assert Arrays.equals(Files.readAllBytes(target.toPath().resolve("gas.png")),IMAGE);
        TouchIconAssets.sync(internal.toFile(),source(false),"30:2");assert Files.readString(target.toPath().resolve(".version")).equals("30:2");
        try{TouchIconAssets.sync(internal.toFile(),new TouchIconAssets.Source(){public String[] names(){return new String[]{"../escape.png"};}public InputStream open(String name){return new ByteArrayInputStream(IMAGE);}},"bad");throw new AssertionError("Accepted traversal");}catch(IOException expected){}
        assert !Files.exists(target.toPath().getParent().resolve("escape.png"));
        System.out.println("Touch icons: native internal path, missing/corrupt repair, failed-copy recovery and filename bounds passed");
    }
}

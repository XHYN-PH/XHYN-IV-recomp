package com.xhyn.stock;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Imports the flat packages expected by the stock driver loader. */
final class DriverStore {
    static final long LIMIT=128L*1024*1024;
    static File root(Context c){return new File(StockApi.data(c),"drivers");}
    static String selected(Context c) throws IOException {String value=FilesEx.text(new File(StockApi.data(c),"driver.txt"),4096).trim();return value.isEmpty()?"system":value;}
    static void select(Context c,String mode) throws IOException {
        StockApi.offline(c);
        if(mode.startsWith("custom:")){String name=mode.substring(7);if(!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IOException("Invalid driver name.");validate(new File(root(c),name));}
        else if(!Arrays.asList("system","turnip","panvk").contains(mode))throw new IOException("Unsupported stock driver choice.");
        FilesEx.atomic(new File(StockApi.data(c),"driver.txt"),mode+"\n");
    }
    static List<File> installed(Context c) throws IOException {
        DriverPackages.recoverAll(root(c));List<File> result=new ArrayList<>();File[] entries=root(c).listFiles();
        if(entries!=null)for(File f:entries)if(f.isDirectory()&&f.getName().matches("[A-Za-z0-9_-]{1,64}")&&!Files.isSymbolicLink(f.toPath()))result.add(f);
        result.sort(Comparator.comparing(File::getName));return result;
    }
    static String validate(File folder) throws IOException {
        if(Files.isSymbolicLink(folder.toPath())||!folder.isDirectory())throw new IOException("Driver folder is unavailable.");
        JSONObject meta=new JSONObject();File description=new File(folder,"meta.json");
        try{if(description.isFile())meta=new JSONObject(FilesEx.text(description,65536));}catch(JSONException e){throw new IOException("Invalid driver metadata.",e);}
        if(meta.optInt("minApi",0)>Build.VERSION.SDK_INT)throw new IOException("Driver requires Android API "+meta.optInt("minApi")+" or later.");
        if(meta.optString("abi").toLowerCase(Locale.ROOT).contains("glibc"))throw new IOException("Desktop glibc drivers are unsupported.");
        String library=meta.optString("libraryName");
        if(library.isEmpty()) {File[] so=folder.listFiles((d,name)->name.toLowerCase(Locale.ROOT).endsWith(".so"));if(so==null||so.length!=1)throw new IOException("Several libraries need meta.json with libraryName.");library=so[0].getName();}
        if(!DriverPackages.filename(library)||!library.endsWith(".so"))throw new IOException("Invalid libraryName.");
        File elf=FilesEx.within(folder,library);FilesEx.regular(elf);
        try(InputStream in=new FileInputStream(elf)) {
            byte[] h=new byte[64];int read=0,n;while(read<h.length&&(n=in.read(h,read,h.length-read))!=-1)read+=n;
            if(read<64||h[0]!=0x7f||h[1]!='E'||h[2]!='L'||h[3]!='F'||h[4]!=2||h[5]!=1||(h[18]&255)!=183||h[19]!=0||h[16]!=3||h[17]!=0)
                throw new IOException("The selected library is not an ARM64 Android shared object.");
        }
        return meta.optString("name",folder.getName())+" · "+library;
    }
    static void importZip(Context c,InputStream input) throws IOException {
        StockApi.offline(c);File stage=DriverPackages.stage(root(c),input,LIMIT);
        try {
            validate(stage);String hash="";File[] entries=stage.listFiles((d,n)->n.endsWith(".so"));
            if(entries==null||entries.length==0)throw new IOException("No driver library found.");Arrays.sort(entries,Comparator.comparing(File::getName));hash=FilesEx.sha(entries[0]).substring(0,20);
            String name="import-"+hash;
            DriverPackages.activate(root(c),name,stage);
        }finally{if(stage.exists())DriverPackages.delete(stage);}
    }
    static void delete(Context c,File folder) throws IOException {
        StockApi.offline(c);File checked=FilesEx.within(root(c),folder.getName());
        if(!checked.getCanonicalFile().equals(folder.getCanonicalFile()))throw new IOException("Invalid driver folder.");
        if(selected(c).equals("custom:"+folder.getName()))select(c,"system");DriverPackages.delete(checked);
        File cached=new File(c.getFilesDir(),"drivers/custom-"+folder.getName());if(cached.exists())DriverPackages.delete(cached);
    }
    private DriverStore(){}
}

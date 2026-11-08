package com.xhyn.stock;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import java.util.zip.ZipFile;

/** Durable recovery for process death between the full-restore directory renames. */
final class SaveRecovery {
    static File folder(Context c){return new File(StockApi.layer(c),"save-recovery");}
    static boolean pending(Context c){return new File(folder(c),"pending.json").isFile();}
    static JSONObject last(Context c)throws IOException{return json(new File(folder(c),"last.json"));}
    private static JSONObject json(File f)throws IOException {
        try{String text=FilesEx.text(f,16384);if(text.isEmpty())return null;JSONObject record=new JSONObject(text);if(record.optInt("version")!=1)throw new IOException("Unsupported save recovery metadata.");return record;}catch(JSONException e){throw new IOException("Cannot read save recovery metadata.",e);}
    }
    private static File archive(File store,JSONObject record)throws IOException,JSONException {
        String name=record.getString("zip");if(!name.matches("full-[a-f0-9-]{36}\\.zip"))throw new IOException("Invalid save recovery filename.");
        File file=FilesEx.within(store,name);if(!FilesEx.sha(file).equals(record.getString("sha256")))throw new IOException("The save recovery ZIP is damaged. Keep the recovery folder.");return file;
    }
    static void recover(Context c)throws IOException {if(pending(c)){
        StockApi.offline(c);JSONObject record=json(new File(folder(c),"pending.json"));
        if(record!=null&&!StockApi.saves(c).getCanonicalPath().equals(record.optString("root")))throw new IOException("Select the original save folder before recovering its interrupted restore.");
        recover(folder(c));
    }}
    static void recover(File store)throws IOException {
        File journal=new File(store,"pending.json");JSONObject record=json(journal);if(record==null)return;
        try {
            if(record.optBoolean("committed")){Files.deleteIfExists(journal.toPath());return;}
            File root=new File(record.getString("root"));if(!root.isAbsolute()||root.getParentFile()==null)throw new IOException("Invalid save recovery destination.");
            if(record.getBoolean("existed")) {
                File copy=archive(store,record);try(InputStream in=new FileInputStream(copy)){SaveData.importZip(root,in);}
            } else if(root.exists()) {
                // Preserve incomplete new data for inspection instead of deleting it.
                Files.move(root.toPath(),new File(root.getParentFile(),"save-restore-interrupted-"+UUID.randomUUID()).toPath(),StandardCopyOption.ATOMIC_MOVE);
            }
            Files.deleteIfExists(journal.toPath());
        }catch(JSONException e){throw new IOException("Cannot read interrupted save restore metadata.",e);}
    }
    static void importZip(Context c,File root,InputStream input)throws IOException {StockApi.offline(c);importZip(root,folder(c),input);}
    static void importZip(File root,File store,InputStream input)throws IOException {
        recover(store);Files.createDirectories(store.toPath());JSONObject oldLast=json(new File(store,"last.json"));
        try {
            boolean existed=root.exists();JSONObject record=new JSONObject().put("version",1).put("root",root.getCanonicalPath());
            if(existed) {
                String name="full-"+UUID.randomUUID()+".zip";File copy=new File(store,name);
                try(OutputStream out=new FileOutputStream(copy)){SaveData.exportZip(root,out);}catch(IOException e){copy.delete();if("No save or achievement data found.".equals(e.getMessage()))existed=false;else throw e;}
                if(copy.isFile())try(ZipFile zip=new ZipFile(copy)){existed=zip.stream().anyMatch(e->!e.isDirectory()&&e.getSize()>0);}
                if(existed)record.put("zip",name).put("sha256",FilesEx.sha(copy));else copy.delete();
            }
            record.put("existed",existed);
            File journal=new File(store,"pending.json");FilesEx.atomic(journal,record.toString());
            try {
                SaveData.importZip(root,input);
                record.put("committed",true);FilesEx.atomic(journal,record.toString());
                if(existed)FilesEx.atomic(new File(store,"last.json"),record.toString());
                Files.deleteIfExists(journal.toPath());
            }catch(IOException failure){try{recover(store);}catch(IOException rollback){failure.addSuppressed(rollback);}throw failure;}
            if(existed&&oldLast!=null)try{File old=archive(store,oldLast);if(!old.getName().equals(record.optString("zip")))Files.deleteIfExists(old.toPath());}catch(IOException ignored){/* Keep an older recovery copy if cleanup fails. */}
        }catch(JSONException e){throw new IOException("Could not write save recovery metadata.",e);}
    }
    static void restoreLast(Context c,File root)throws IOException {
        StockApi.offline(c);JSONObject record=last(c);if(record==null)throw new IOException("No previous full import snapshot.");
        try{if(!root.getCanonicalPath().equals(record.getString("root")))throw new IOException("The snapshot belongs to another save folder.");File file=archive(folder(c),record);try(InputStream in=new FileInputStream(file)){importZip(c,root,in);}}
        catch(JSONException e){throw new IOException(e);}
    }
    private SaveRecovery(){}
}

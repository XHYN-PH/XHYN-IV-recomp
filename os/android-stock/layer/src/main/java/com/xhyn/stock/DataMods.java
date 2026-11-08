package com.xhyn.stock;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Reversible offline loose-file deployment. Does not depend on an absent native overlay hook. */
final class DataMods {
    static final long FILE_LIMIT=16L*1024*1024;
    static final int DEPLOYED_LIMIT=1024;
    static File store(Context c){return new File(StockApi.layer(c),"data-mods");}
    static File mods(Context c) throws IOException{return new File(StockApi.game(c).getParentFile(),"mods");}
    static JSONObject state(File store) throws IOException {
        try{String text=FilesEx.text(new File(store,"state.json"),2*1024*1024);return text.isEmpty()?new JSONObject():new JSONObject(text);}
        catch(JSONException e){throw new IOException("Cannot read the data mod recovery state.",e);}
    }
    static void recover(Context c) throws IOException {recover(store(c));}
    static void recover(File store) throws IOException {
        File tx=new File(store,"transaction"),journal=new File(tx,"pending.json");if(!tx.exists())return;
        if(!journal.isFile()||new File(tx,"committed").isFile()){DriverPackages.delete(tx);return;}
        try {
            JSONObject pending=new JSONObject(FilesEx.text(journal,2*1024*1024));File game=new File(pending.getString("root"));
            if(!game.isAbsolute()||!FilesEx.sha(new File(game,"default.xex")).equals(pending.getString("exeHash")))throw new IOException("Game files changed during a mod transaction. Keep the recovery folder and select the original installation.");
            JSONArray files=pending.getJSONArray("files");JSONObject snapshots=pending.getJSONObject("snapshots");
            for(int i=0;i<files.length();i++){String path=files.getString(i);if(!FilesEx.sha(FilesEx.within(new File(tx,"live"),path)).equals(snapshots.getString(path)))throw new IOException("Damaged recovery copy: "+path+". Keep the recovery folder.");}
            for(int i=0;i<files.length();i++) {
                String path=files.getString(i);File target=FilesEx.within(game,path),copy=FilesEx.within(new File(tx,"live"),path);
                FilesEx.copyAtomic(copy,target,FILE_LIMIT);
            }
            FilesEx.atomic(new File(store,"state.json"),pending.getJSONObject("before").toString());DriverPackages.delete(tx);
        }catch(JSONException e){throw new IOException("Cannot read the interrupted mod transaction. Keep its recovery folder.",e);}
    }
    static void apply(Context c,List<ModOverlayPolicy.Mod> mods) throws IOException {
        StockApi.offline(c);apply(StockApi.game(c),store(c),mods);
    }
    /** Package-visible for fault/recovery tests without Android or game binaries. */
    static void apply(File game,File store,List<ModOverlayPolicy.Mod> mods) throws IOException {
        Files.createDirectories(store.toPath());recover(store);game=game.getCanonicalFile();
        String executable=FilesEx.sha(new File(game,"default.xex"));JSONObject previous=state(store);
        JSONObject oldFiles=previous.optJSONObject("files");if(oldFiles==null)oldFiles=new JSONObject();
        if(oldFiles.length()>0&&(!game.getAbsolutePath().equals(previous.optString("root"))||!executable.equals(previous.optString("exeHash"))))
            throw new IOException("Restore mods in the previous installation before selecting another game folder.");
        try {
            Map<String,File> desired=plan(game,mods);Set<String> union=new TreeSet<>(desired.keySet());
            for(Iterator<String> keys=oldFiles.keys();keys.hasNext();) {String path=keys.next();union.add(path);JSONObject entry=oldFiles.getJSONObject(path);
                if(!FilesEx.sha(FilesEx.within(game,path)).equals(entry.getString("applied")))throw new IOException("Modified outside this manager: "+path+". Keep its backup and resolve the conflict before continuing.");
                if(!FilesEx.sha(FilesEx.within(new File(store,"originals"),path)).equals(entry.getString("original")))throw new IOException("Original mod backup is damaged: "+path);
            }
            if(union.size()>DEPLOYED_LIMIT)throw new IOException("More than 1024 affected files. Restore original files before applying this larger replacement set.");
            File tx=new File(store,"transaction");Files.createDirectories(tx.toPath());JSONArray affected=new JSONArray();JSONObject snapshots=new JSONObject();
            JSONObject nextFiles=new JSONObject();long total=0;
            for(String path:union) {
                File live=FilesEx.within(game,path);FilesEx.regular(live);total+=live.length();if(total>256L*1024*1024)throw new IOException("Combined data mods exceed 256 MiB.");
                FilesEx.copyAtomic(live,FilesEx.within(new File(tx,"live"),path),FILE_LIMIT);affected.put(path);snapshots.put(path,FilesEx.sha(live));
                File original=FilesEx.within(new File(store,"originals"),path);
                if(!oldFiles.has(path))FilesEx.copyAtomic(live,original,FILE_LIMIT);
                if(desired.containsKey(path))nextFiles.put(path,new JSONObject().put("original",FilesEx.sha(original)).put("applied",FilesEx.sha(desired.get(path))));
            }
            JSONObject pending=new JSONObject().put("root",game.getAbsolutePath()).put("exeHash",executable).put("files",affected).put("snapshots",snapshots).put("before",previous);
            if(pending.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("Data mod recovery metadata is too large. Apply a smaller set.");
            FilesEx.atomic(new File(tx,"pending.json"),pending.toString());
            try {
                for(String path:union) {
                    File source=desired.get(path);if(source==null)source=FilesEx.within(new File(store,"originals"),path);
                    FilesEx.copyAtomic(source,FilesEx.within(game,path),FILE_LIMIT);
                    String expected=desired.containsKey(path)?nextFiles.getJSONObject(path).getString("applied"):oldFiles.getJSONObject(path).getString("original");
                    if(!FilesEx.sha(FilesEx.within(game,path)).equals(expected))throw new IOException("Mod data changed during deployment: "+path);
                }
                FilesEx.atomic(new File(store,"state.json"),new JSONObject().put("version",1).put("root",game.getAbsolutePath()).put("exeHash",executable).put("files",nextFiles).toString());
                FilesEx.atomic(new File(tx,"committed"),"1");
            }catch(IOException|JSONException failure){IOException e=failure instanceof IOException?(IOException)failure:new IOException(failure);try{recover(store);}catch(IOException recovery){e.addSuppressed(recovery);}throw e;}
            try{DriverPackages.delete(tx);}catch(IOException ignored){/* A committed transaction is cleaned on the next visit. */}
        }catch(JSONException e){throw new IOException("Invalid data mod recovery metadata.",e);}
    }
    static Map<String,File> plan(File game,List<ModOverlayPolicy.Mod> mods) throws IOException {
        List<ModOverlayPolicy.Mod> sorted=new ArrayList<>(mods);sorted.sort(ModOverlayPolicy.ORDER);
        Map<String,File> result=new LinkedHashMap<>();Map<String,String> casePaths=new HashMap<>();
        for(ModOverlayPolicy.Mod mod:sorted)if(mod.enabled) {
            if(!mod.admitted())throw new IOException("Fix or disable "+mod.folder.getName()+": "+mod.problem);
            for(String path:mod.files) {
                String relative=path.startsWith("game/")?path.substring(5):path.startsWith("update/")?path:"";
                if(relative.isEmpty())throw new IOException("Unsupported mod target: "+path);
                File target=existing(game,relative);FilesEx.regular(target);
                String actual=game.getCanonicalFile().toPath().relativize(target.getCanonicalFile().toPath()).toString().replace(File.separatorChar,'/');
                if(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>512)throw new IOException("Data mod target path exceeds 512 bytes.");
                String key=actual.toLowerCase(Locale.ROOT);String canonical=casePaths.computeIfAbsent(key,k->actual);
                File source=FilesEx.within(mod.folder,path);FilesEx.regular(source);if(source.length()>FILE_LIMIT)throw new IOException("Data file exceeds 16 MiB: "+path);
                result.put(canonical,source);
                if(result.size()>DEPLOYED_LIMIT)throw new IOException("A deployment supports up to 1024 loose data files.");
            }
        }
        return result;
    }
    private static File existing(File root,String relative) throws IOException {
        FilesEx.within(root,relative);File current=root;
        for(String part:relative.split("/")) {
            File match=null;File[] children=current.listFiles();if(children==null)throw new IOException("No installed target for "+relative);
            for(File child:children)if(child.getName().equalsIgnoreCase(part)){if(match!=null)throw new IOException("Ambiguous target ignoring case: "+relative);match=child;}
            if(match==null)throw new IOException("No installed Xbox data file for "+relative);if(Files.isSymbolicLink(match.toPath()))throw new IOException("Mod targets cannot be symbolic links.");current=match;
        }
        return current;
    }
    private DataMods(){}
}

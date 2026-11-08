package com.xhyn.stock;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Keep old configurations recoverable instead of passing unsupported Test14 flags to v442. */
final class FirstRunMigration {
    static void run(Context c) throws IOException {
        File layer=StockApi.layer(c), marker=new File(layer,"v442-migration.json");
        if(marker.isFile())return;
        Files.createDirectories(layer.toPath());
        File backup=new File(layer,"before-v442"); Files.createDirectories(backup.toPath());
        List<String> paths=Arrays.asList("args.txt","env.txt","driver.txt","LibertyRecomp/native.toml","LibertyRecomp/touch-controls.ini");
        // First copy everything. A failure here leaves every original file in place.
        for(String path:paths) {
            File from=FilesEx.within(StockApi.data(c),path); if(!from.exists())continue;
            File to=FilesEx.within(backup,path);
            if(!to.exists()) FilesEx.atomic(to,FilesEx.text(from,1024*1024));
        }
        // Carry a custom installation selection into the stock cache selector.
        File selection=new File(StockApi.data(c),"LibertyRecomp/game-location.txt");
        String chosen=FilesEx.text(selection,16384).trim();
        if(!chosen.isEmpty() && new File(chosen).isAbsolute() && new File(chosen,"game/default.xex").isFile()) {
            if(!c.getSharedPreferences("cache_location",0).edit().putString("game",new File(chosen,"game").getAbsolutePath()).commit())
                throw new IOException("Could not preserve the selected game location.");
        }
        // Keep any explicit save destination. Stock migrates the older default save root itself.
        String saved="";
        String old=FilesEx.text(new File(backup,"args.txt"),1024*1024);
        for(String line:old.split("\n")) if(line.trim().startsWith("--saved_game_root="))saved=line.trim().substring(18);
        String toml=FilesEx.text(new File(backup,"LibertyRecomp/native.toml"),1024*1024);
        for(String line:toml.split("\n")) {
            int eq=line.indexOf('='); if(eq>0 && line.substring(0,eq).trim().equals("saved_game_root")) { String value=line.substring(eq+1).trim();
                if(value.startsWith("\"")&&value.indexOf('"',1)>0)saved=value.substring(1,value.indexOf('"',1));
            }
        }
        if(!saved.isEmpty() && (!new File(saved).isAbsolute() || saved.contains("\"") || saved.contains("\\") || saved.chars().anyMatch(Character::isISOControl)))
            throw new IOException("The custom save path needs review; the previous settings were kept.");
        String stockArgs="";
        if(!saved.isEmpty())try(InputStream in=c.getAssets().open(StockDefaults.profileAsset(c))) {
            stockArgs=new String(FilesEx.read(in,262144),java.nio.charset.StandardCharsets.UTF_8)+"\n--saved_game_root="+saved+"\n";
        }
        try {
            for(String path:paths) Files.deleteIfExists(FilesEx.within(StockApi.data(c),path).toPath());
            if(!saved.isEmpty())FilesEx.atomic(new File(StockApi.data(c),"LibertyRecomp/native.toml"),"saved_game_root = \""+saved+"\"\n");
            if(!stockArgs.isEmpty())FilesEx.atomic(new File(StockApi.data(c),"args.txt"),stockArgs);
            // System is supported on every GPU family. Importing a custom driver remains explicit.
            FilesEx.atomic(new File(StockApi.data(c),"driver.txt"),"system\n");
            JSONObject report=new JSONObject().put("version",1).put("time",System.currentTimeMillis()).put("settings_backup",backup.getAbsolutePath());
            FilesEx.atomic(marker,report.toString());
        } catch(IOException|JSONException failure) {
            for(String path:paths) { File copy=FilesEx.within(backup,path); if(copy.isFile())try{FilesEx.atomic(FilesEx.within(StockApi.data(c),path),FilesEx.text(copy,1024*1024));}catch(IOException e){failure.addSuppressed(e);} }
            throw new IOException("Could not prepare v442 settings: "+FilesEx.error(failure),failure);
        }
    }
    private FirstRunMigration(){}
}

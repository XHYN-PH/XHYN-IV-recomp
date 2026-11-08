package com.xhyn.stock;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.nio.channels.FileLock;
import java.util.*;
import org.json.*;

/** Active engine persistence cannot overwrite this separate next-launch queue. */
final class NextSettings {
    static File file(Context c){return new File(StockApi.layer(c),"next-settings.json");}
    static Map<String,String> pending(Context c)throws IOException {
        Map<String,String> out=new LinkedHashMap<>();String text=FilesEx.text(file(c),65536);if(text.isEmpty())return out;
        try {JSONObject json=new JSONObject(text);for(Iterator<String> keys=json.keys();keys.hasNext();) {
            String key=keys.next(),value=json.getString(key);SettingSchema.Entry definition=SettingSchema.find(key);
            if(!LaunchPreferences.valid(key,value)&&(definition==null||!definition.accepts(value)))throw new IOException("Unsupported queued setting: "+key);out.put(key,value);
        }}catch(JSONException e){throw new IOException("Cannot read queued settings.",e);}return out;
    }
    static Map<String,String> current(Context c)throws IOException {Map<String,String> values=StockApi.settings(c);values.putAll(LaunchPreferences.current(c));values.putAll(pending(c));return values;}
    static synchronized void save(Context c,Map<String,String> changes)throws IOException {
        Files.createDirectories(StockApi.layer(c).toPath());
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(StockApi.layer(c),"next-settings.lock"),"rw");FileLock lock=lockFile.getChannel().lock()) {
        Map<String,String> queued=pending(c);queued.putAll(changes);
        for(Map.Entry<String,String> e:queued.entrySet()){SettingSchema.Entry definition=SettingSchema.find(e.getKey());if(!LaunchPreferences.valid(e.getKey(),e.getValue())&&(definition==null||!definition.accepts(e.getValue())))throw new IOException("Unsupported setting: "+e.getKey());}
        // Queue even while stopped: a crash between save and the next launch is recoverable.
        FilesEx.atomic(file(c),new JSONObject(queued).toString());
        }
    }
    static synchronized void activate(Context c)throws IOException {
        Files.createDirectories(StockApi.layer(c).toPath());
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(StockApi.layer(c),"next-settings.lock"),"rw");FileLock lock=lockFile.getChannel().lock()) {
        Map<String,String> queued=pending(c);if(queued.isEmpty())return;
        LaunchPreferences.apply(c,queued);queued.keySet().removeIf(LaunchPreferences::key);
        if(!queued.isEmpty())StockApi.saveSettings(c,queued);Files.deleteIfExists(file(c).toPath());
        }
    }
    static synchronized void prepareLaunch(Context c)throws IOException {
        Files.createDirectories(StockApi.layer(c).toPath());
        try(RandomAccessFile lockFile=new RandomAccessFile(new File(StockApi.layer(c),"next-settings.lock"),"rw");FileLock lock=lockFile.getChannel().lock()) {LaunchPreferences.apply(c,pending(c));}
    }
    private NextSettings(){}
}

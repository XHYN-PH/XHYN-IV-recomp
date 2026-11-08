package com.xhyn.stock;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;

/** File-backed so the launcher and :game process never depend on stale SharedPreferences caches. */
final class LayerPreferences {
    static JSONObject read(Context c) {
        try { String s=FilesEx.text(new File(StockApi.layer(c),"preferences.json"),32768); return s.isEmpty()?new JSONObject():new JSONObject(s); }
        catch(IOException|JSONException e) { return new JSONObject(); }
    }
    static synchronized void update(Context c,JSONObject changes) throws IOException {
        File root=StockApi.layer(c); Files.createDirectories(root.toPath());
        try(RandomAccessFile f=new RandomAccessFile(new File(root,"preferences.lock"),"rw"); FileLock lock=f.getChannel().lock()) {
            JSONObject values=read(c);
            try { for(java.util.Iterator<String> keys=changes.keys();keys.hasNext();) { String key=keys.next(); values.put(key,changes.get(key)); } }
            catch(JSONException e){throw new IOException(e);}
            FilesEx.atomic(new File(root,"preferences.json"),values.toString());
        }
    }
    static void set(Context c,String key,Object value) throws IOException {
        try { update(c,new JSONObject().put(key,value)); } catch(JSONException e){throw new IOException(e);}
    }
    private LayerPreferences(){}
}

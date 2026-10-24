package com.libertyrecomp;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
/** Small atomic file shared by the launcher and game processes. */
final class UiPreferences {
    private static String path="";private static long stamp=Long.MIN_VALUE;private static JSONObject cached=new JSONObject();
    private static synchronized JSONObject read(Context a){File f=new File(a.getFilesDir(),"xhyn-ui.json");long modified=f.lastModified();if(!path.equals(f.toString())||stamp!=modified){path=f.toString();stamp=modified;cached=new JSONObject();if(f.isFile()&&f.length()<4096)try(InputStream in=new AtomicFile(f).openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[512];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4096)throw new IOException("UI preferences too large");out.write(buffer,0,n);}cached=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception ignored){}}return cached;}
    static synchronized boolean get(Context a,String key){JSONObject value=read(a);boolean old=key.equals("reduce_motion")?a.getSharedPreferences("xhyn_ui",0).getBoolean("reduce_motion",false):key.equals("sustained")&&a.getSharedPreferences("performance_test",0).getBoolean("sustained_performance",false);return value.optBoolean(key,old);}
    static synchronized void set(Context a,String key,boolean value)throws IOException{JSONObject next;try{next=new JSONObject(read(a).toString());next.put(key,value);}catch(JSONException e){throw new IOException(e);}File f=new File(a.getFilesDir(),"xhyn-ui.json");AtomicFile atomic=new AtomicFile(f);FileOutputStream out=null;try{out=atomic.startWrite();out.write(next.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);cached=next;path=f.toString();stamp=f.lastModified();}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}}
    private UiPreferences(){}
}

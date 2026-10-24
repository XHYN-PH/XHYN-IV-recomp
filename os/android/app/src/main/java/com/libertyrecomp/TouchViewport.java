package com.libertyrecomp;
import java.io.*;
import java.util.*;
/** Normalized native viewport with generation, capture identity and freshness. */
final class TouchViewport {
    static final class Snapshot {
        final float[] values;final long generation,captured;final String token,display,settings;
        Snapshot(float[] values,long generation,long captured,String token,String display,String settings){this.values=values.clone();this.generation=generation;this.captured=captured;this.token=token;this.display=display;this.settings=settings;}
        boolean matches(String display,String settings,String expectedToken,long expectedGeneration,long now){
            long age=now-captured;
            return generation>0&&age>=0&&age<=60*60*1000L&&this.display.equals(display)&&this.settings.equals(settings)&&
                (expectedToken.isEmpty()||(token.equals(expectedToken)&&generation==expectedGeneration));
        }
    }
    static boolean valid(float[] v){if(v==null||v.length!=6)return false;for(float x:v)if(!Float.isFinite(x))return false;return v[0]>=0&&v[1]>=0&&v[2]<=1&&v[3]<=1&&v[2]>v[0]&&v[3]>v[1]&&v[4]>0&&v[5]>0;}
    static float[] project(float[] v,float width,float height){if(!valid(v)||width<=0||height<=0||Math.abs((width/height)/(v[4]/v[5])-1)>.025)return null;return new float[]{v[0]*width,v[1]*height,v[2]*width,v[3]*height};}
    private static File file(File data){return new File(data,"LibertyRecomp/touch-viewport.toml");}
    static void save(File data,float[] v)throws IOException{if(!valid(v))return;Map<String,String> values=new LinkedHashMap<>();for(int i=0;i<6;i++)values.put("v"+i,Float.toString(v[i]));values.put("schema","1");MobileConfig.update(file(data),values);}
    static void save(File data,float[] v,long generation,String token,String display,String settings)throws IOException{
        if(!valid(v)||generation<=0)return;
        Map<String,String> values=new LinkedHashMap<>();for(int i=0;i<6;i++)values.put("v"+i,Float.toString(v[i]));
        values.put("schema","2");values.put("generation",Long.toString(generation));values.put("captured",Long.toString(System.currentTimeMillis()));
        values.put("token",token);values.put("display",encode(display));values.put("settings",encode(settings));MobileConfig.update(file(data),values);
    }
    static float[] read(File data){try{Map<String,String> values=MobileConfig.read(file(data));float[] v=new float[6];for(int i=0;i<6;i++)v[i]=Float.parseFloat(values.get("v"+i));return valid(v)?v:null;}catch(Exception e){return null;}}
    static Snapshot snapshot(File data){
        try{
            Map<String,String> values=MobileConfig.read(file(data));if(!"2".equals(values.get("schema")))return null;
            float[] v=new float[6];for(int i=0;i<6;i++)v[i]=Float.parseFloat(values.get("v"+i));if(!valid(v))return null;
            return new Snapshot(v,Long.parseLong(values.get("generation")),Long.parseLong(values.get("captured")),values.getOrDefault("token",""),decode(values.getOrDefault("display","")),decode(values.getOrDefault("settings","")));
        }catch(Exception e){return null;}
    }
    private static String encode(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String decode(String value){return new String(Base64.getUrlDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8);}
    private TouchViewport(){}
}


package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Edits only named flat TOML entries, keeping unrelated runtime settings. */
final class MobileConfig {
    static File file(File data) { return new File(data,"LibertyRecomp/native.toml"); }
    static Map<String,String> read(File file) throws IOException {
        Map<String,String> values=new LinkedHashMap<>();
        if(!file.isFile())return values;
        for(String line:Files.readAllLines(file.toPath(),StandardCharsets.UTF_8)) {
            int eq=line.indexOf('=');if(eq<=0)continue;
            String key=line.substring(0,eq).trim(),value=line.substring(eq+1).trim();
            boolean quoted=false,escape=false;
            for(int i=0;i<value.length();i++) {
                char c=value.charAt(i);if(c=='"'&&!escape)quoted=!quoted;
                if(c=='#'&&!quoted){value=value.substring(0,i).trim();break;}
                escape=c=='\\'&&!escape;
            }
            if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);
            values.put(key,value);
        }
        return values;
    }
    /** Replace the removed experiment without resetting other graphics choices. */
    static void migrateRemovedUpscaler(File file) throws IOException {
        Map<String,String> current=read(file);
        boolean replace="sgsr".equals(current.get("gta4_native_upscaler"));
        if(!replace&&!current.containsKey("present_sgsr"))return;
        StringBuilder text=new StringBuilder();
        for(String line:Files.readAllLines(file.toPath(),StandardCharsets.UTF_8)) {
            int eq=line.indexOf('=');String key=eq>0?line.substring(0,eq).trim():"";
            if(key.equals("present_sgsr"))continue;
            if(replace&&key.equals("gta4_native_upscaler"))line="gta4_native_upscaler = \"native\"";
            text.append(line).append('\n');
        }
        writeAtomic(file,text.toString());
    }
    static final Map<String,String> DEFAULTS=Collections.unmodifiableMap(new LinkedHashMap<String,String>(){{
        put("gta4_present_mode","immediate");put("gta4_motion_blur","false");put("gta4_native_smaa_quality","low");
        put("gta4_native_anti_aliasing","fxaa");put("gta4_native_upscaler","native");put("gta4_shadow_map_base_size","256");put("gta4_shadow_distance_scale","1.0");
        put("gta4_reflection_aa","off");put("gta4_reflection_capture_distance","near");
        put("gta4_native_water_reflection_interval","0");put("gta4_native_environment_reflection_interval","0");
        put("gta4_draw_distance_scale","0.85");put("gta4_native_converted_texture_disk_cache","true");
    }});
    static void ensureDefaults(File file)throws IOException {Map<String,String> saved=read(file),missing=new LinkedHashMap<>();for(Map.Entry<String,String> e:DEFAULTS.entrySet())if(!saved.containsKey(e.getKey()))missing.put(e.getKey(),e.getValue());if(!missing.isEmpty())update(file,missing);}
    static void migrateRadarMargin(File file) throws IOException {
        Map<String,String> saved=read(file),changes=new LinkedHashMap<>();String margin=saved.get("gta4_touch_radar_margin");
        if(margin==null||margin.matches("0(?:\\.0*)?" )||margin.matches("0\\.040*"))changes.put("gta4_touch_radar_margin","-0.06");
        try{double n=Double.parseDouble(saved.getOrDefault("gta4_shadow_distance_scale","1"));if(Double.isFinite(n)&&n<1)changes.put("gta4_shadow_distance_scale","1");}catch(NumberFormatException ignored){}
        if(!changes.isEmpty())update(file,changes);
    }
    static void update(File file,Map<String,String> changes) throws IOException {
        changes=new LinkedHashMap<>(changes);
        if(changes.containsKey("gta4_native_anti_aliasing"))changes.put("gta4_native_anti_aliasing_unified","true");
        for(Map.Entry<String,String> e:changes.entrySet())
            if(!e.getKey().matches("[a-zA-Z0-9_]+")||!e.getValue().matches("[a-zA-Z0-9_.-]+"))
                throw new IOException("Invalid setting");
        List<String> lines=file.isFile()?Files.readAllLines(file.toPath(),StandardCharsets.UTF_8):new ArrayList<>();
        StringBuilder text=new StringBuilder();
        for(String line:lines) {
            int eq=line.indexOf('=');
            if(eq>0&&changes.containsKey(line.substring(0,eq).trim()))continue;
            text.append(line).append('\n');
        }
        for(Map.Entry<String,String> e:changes.entrySet()) {
            String v=e.getValue();boolean bare=v.equals("true")||v.equals("false")||v.matches("-?[0-9]+(?:\\.[0-9]+)?");
            text.append(e.getKey()).append(" = ").append(bare?v:"\""+v+"\"").append('\n');
        }
        writeAtomic(file,text.toString());
    }
    private static void writeAtomic(File file,String text) throws IOException {
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create settings folder");
        File temp=File.createTempFile("mobile-settings-",".tmp",parent);
        try {
            try(FileOutputStream out=new FileOutputStream(temp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally {temp.delete();}
    }
}

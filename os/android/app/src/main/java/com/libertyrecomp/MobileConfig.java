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
    static void migrateRadarMargin(File file) throws IOException {
        Map<String,String> saved=read(file),changes=new LinkedHashMap<>();String margin=saved.get("gta4_touch_radar_margin");
        if(margin==null||margin.matches("0\\.040*"))changes.put("gta4_touch_radar_margin","0");
        try{double n=Double.parseDouble(saved.getOrDefault("gta4_shadow_distance_scale","1"));if(Double.isFinite(n)&&n<1)changes.put("gta4_shadow_distance_scale","1");}catch(NumberFormatException ignored){}
        if(!changes.isEmpty())update(file,changes);
    }
    static void update(File file,Map<String,String> changes) throws IOException {
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

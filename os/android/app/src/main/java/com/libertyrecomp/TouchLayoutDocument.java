package com.libertyrecomp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Uses the native editor's screen/control dx dy scale hidden format. */
final class TouchLayoutDocument {
    static final class Entry {float dx,dy,scale=1,opacity=1;boolean hidden;}
    final Map<String,Entry> entries=new TreeMap<>();
    Entry get(String key){return entries.computeIfAbsent(key,k->new Entry());}
    /** Imported records replace matching controls; absent contexts keep their edits. */
    void merge(TouchLayoutDocument imported) {
        for(Map.Entry<String,Entry> item:imported.entries.entrySet()) {
            Entry source=item.getValue(),copy=new Entry();
            copy.dx=source.dx;copy.dy=source.dy;copy.scale=source.scale;
            copy.hidden=source.hidden;copy.opacity=source.opacity;
            entries.put(item.getKey(),copy);
        }
    }
    static File file(File root){return new File(root,"LibertyRecomp/liberty_touch_layout.txt");}
    static void migrate(File root,File internal)throws IOException {
        File target=file(root);
        if(target.exists())return;
        for(File legacy:new File[]{new File(internal,"liberty_touch_layout.txt"),new File(root,"liberty_touch_layout.txt")}){
            if(!legacy.isFile())continue;
            Files.createDirectories(target.getParentFile().toPath());Files.copy(legacy.toPath(),target.toPath());return;
        }
    }
    static TouchLayoutDocument read(File root)throws IOException {
        TouchLayoutDocument doc=new TouchLayoutDocument();File file=file(root);if(!file.isFile())return doc;
        if(file.length()>1024*1024)throw new IOException("Layout file is too large.");
        for(String line:Files.readAllLines(file.toPath(),StandardCharsets.UTF_8)){
            String[] v=line.trim().split("\\s+");if(v.length<3||!v[0].matches("[a-z_]+/[a-z0-9_]+"))continue;
            try{Entry e=new Entry();e.dx=Float.parseFloat(v[1]);e.dy=Float.parseFloat(v[2]);if(v.length>=5){e.scale=Float.parseFloat(v[3]);e.hidden=!v[4].equals("0");}if(v.length>=6){float alpha=Float.parseFloat(v[5]);if(Float.isFinite(alpha))e.opacity=Math.max(0,Math.min(1,alpha));}
                if(Float.isFinite(e.dx)&&Float.isFinite(e.dy)&&Float.isFinite(e.scale)){e.dx=Math.max(-2000,Math.min(2000,e.dx));e.dy=Math.max(-800,Math.min(800,e.dy));e.scale=Math.max(.35f,Math.min(4f,e.scale));doc.entries.put(v[0],e);}
            }catch(NumberFormatException ignored){}
        }return doc;
    }
    void save(File root)throws IOException {
        File file=file(root);Files.createDirectories(file.getParentFile().toPath());File temp=File.createTempFile("touch-layout-",".tmp",file.getParentFile());
        try{StringBuilder out=new StringBuilder("# Liberty touch layout: screen/control dx dy scale hidden\n");
            for(Map.Entry<String,Entry> item:entries.entrySet()){
                Entry e=item.getValue();if(!item.getKey().matches("[a-z_]+/[a-z0-9_]+")||!Float.isFinite(e.dx)||!Float.isFinite(e.dy)||!Float.isFinite(e.scale))throw new IOException("Invalid control layout.");
                out.append(item.getKey()).append(' ').append(e.dx).append(' ').append(e.dy).append(' ').append(e.scale).append(' ').append(e.hidden?1:0).append(' ').append(e.opacity).append('\n');
            }
            try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(out.toString().getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}
            try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{temp.delete();}
    }
}

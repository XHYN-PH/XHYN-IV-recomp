package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ModOverlayPolicyTest {
    static void put(File root,String name,String value)throws IOException{
        File file=new File(root,name);Files.createDirectories(file.getParentFile().toPath());Files.writeString(file.toPath(),value);
    }
    static ModOverlayPolicy.Mod find(List<ModOverlayPolicy.Mod> mods,String name){
        return mods.stream().filter(x->x.folder.getName().equals(name)).findFirst().orElseThrow();
    }
    public static void main(String[] args)throws Exception{
        File root=Files.createTempDirectory("mod-policy").toFile(),mods=new File(root,"mods");assert mods.mkdir();
        try{
            put(mods,"A/game/common/Handling.dat","a");put(mods,"B/game/common/handling.dat","b");
            put(mods,"Bad/game/plugin.asi","bad");put(mods,"Off/game/test.dat","off");put(mods,"Off/xhyn-mod.cfg","enabled=false\n");
            put(mods,"Config/game/test.dat","bad");put(mods,"Config/xhyn-mod.cfg","priority=oops\n");
            List<ModOverlayPolicy.Mod> scan=ModOverlayPolicy.scan(mods);
            assert scan.stream().filter(x->x.enabled&&x.admitted()).count()==2:"A bad folder must not hide valid mods";
            ModOverlayPolicy.Mod a=find(scan,"A"),b=find(scan,"B");
            assert a.conflictCount==1&&b.conflictCount==1&&a.conflicts.get(0).contains("Overridden by B");
            a.priority=10;ModOverlayPolicy.save(a);scan=ModOverlayPolicy.scan(mods);a=find(scan,"A");b=find(scan,"B");
            assert a.priority==10&&b.conflicts.get(0).contains("Overridden by A");
            b.enabled=false;ModOverlayPolicy.save(b);scan=ModOverlayPolicy.scan(mods);assert find(scan,"A").conflictCount==0;
            put(mods,"C/game/common/handling.dat","c");put(mods,"C/xhyn-mod.cfg","priority=20\n");
            put(mods,"D/game/common/handling.dat","d");put(mods,"D/xhyn-mod.cfg","priority=30\n");
            scan=ModOverlayPolicy.scan(mods);assert find(scan,"A").conflicts.get(0).contains("Overridden by D"):"Report the final winner";
            put(mods,"Duplicate/game/same.dat","one");put(mods,"Duplicate/game/SAME.dat","two");
            Files.createSymbolicLink(new File(mods,"Link").toPath(),new File(mods,"A").toPath());
            put(mods,"MetaDirectory/xhyn-mod.cfg/child.dat","invalid");
            put(mods,"HugeConfig/game/test.dat","valid");put(mods,"HugeConfig/xhyn-mod.cfg","x".repeat(4097));
            put(mods,"Repeated/game/test.dat","valid");put(mods,"Repeated/xhyn-mod.cfg","enabled=true\nenabled=false\n");
            put(mods,"Large/game/test.dat","");try(RandomAccessFile file=new RandomAccessFile(new File(mods,"Large/game/test.dat"),"rw")){file.setLength(256L*1024*1024+1);}
            put(mods,"Control/game/test.dat","valid");put(mods,"Control/xhyn-mod.cfg","enabled="+(char)1+"true\n");
            String deep="game/"+"d/".repeat(23)+"valid.dat";
            put(mods,"DeepValid/"+deep,"valid");put(mods,"TooDeep/game/"+"d/".repeat(24)+"bad.dat","invalid");
            scan=ModOverlayPolicy.scan(mods);
            assert find(scan,"DeepValid").admitted();
            for(String name:new String[]{"Duplicate","Link","MetaDirectory","HugeConfig","Repeated","Large","Control","TooDeep"})assert !find(scan,name).admitted():name;
            assert ModOverlayPolicy.names("\uE000","\uD83D\uDE00")<0:"UTF-8 ordering must match native";
            a=find(scan,"A");a.priority=1001;boolean rejected=false;try{ModOverlayPolicy.save(a);}catch(IOException e){rejected=true;}assert rejected;
            put(root,ModOverlayPolicy.REPORT,ModOverlayPolicy.identity("A")+"\tmounted: 1 game / 0 update files\n");
            assert ModOverlayPolicy.lastRun(root).get("41").startsWith("mounted");
            Files.delete(new File(root,ModOverlayPolicy.REPORT).toPath());
            Files.createSymbolicLink(new File(root,ModOverlayPolicy.REPORT).toPath(),new File(mods,"A/xhyn-mod.cfg").toPath());
            rejected=false;try{ModOverlayPolicy.lastRun(root);}catch(IOException e){rejected=true;}assert rejected;
            System.out.println("Mod policy: independent rejection, settings, final conflict winners, bounds and launch reports passed");
        }finally{SaveData.deleteTree(root);}
    }
}

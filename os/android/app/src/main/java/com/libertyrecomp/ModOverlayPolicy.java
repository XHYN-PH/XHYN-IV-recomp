package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Admission/settings policy shared with the native loose-data overlay contract. */
final class ModOverlayPolicy {
    static final String CONFIG="xhyn-mod.cfg", REPORT="data-mod-last-run.txt";
    static final int MAX_FILES=8192, MAX_DEPTH=25;
    static final long MAX_BYTES=256L*1024*1024;
    static final class Mod {
        final File folder;
        boolean enabled=true;
        int priority,conflictCount;
        String problem="";
        final List<String> files=new ArrayList<>(), conflicts=new ArrayList<>();
        Mod(File folder){this.folder=folder;}
        boolean admitted(){return problem.isEmpty()&&!files.isEmpty();}
    }
    static String key(String value){
        StringBuilder out=new StringBuilder(value);
        for(int i=0;i<out.length();i++){char c=out.charAt(i);if(c>='A'&&c<='Z')out.setCharAt(i,(char)(c+32));}
        return out.toString();
    }
    static int names(String a,String b){
        byte[] x=a.getBytes(StandardCharsets.UTF_8),y=b.getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<Math.min(x.length,y.length);i++){int d=(x[i]&255)-(y[i]&255);if(d!=0)return d;}
        return Integer.compare(x.length,y.length);
    }
    static final Comparator<Mod> ORDER=(a,b)->a.priority!=b.priority?Integer.compare(a.priority,b.priority):names(a.folder.getName(),b.folder.getName());
    private static List<String> lines(File file,int limit)throws IOException{
        if(Files.isSymbolicLink(file.toPath())||!Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Expected a regular file: "+file.getName());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[1024];int n;
            while((n=in.read(buffer))!=-1){if(bytes.size()+n>limit)throw new IOException(file.getName()+" is too large.");bytes.write(buffer,0,n);}
        }
        return Arrays.asList(new String(bytes.toByteArray(),StandardCharsets.UTF_8).split("\n",-1));
    }
    static int priority(String text)throws IOException{
        if(!text.matches("-?[0-9]{1,4}"))throw new IOException("Priority must be -1000 to 1000.");
        int value=Integer.parseInt(text);
        if(value < -1000||value > 1000)throw new IOException("Priority must be -1000 to 1000.");
        return value;
    }
    private static void settings(Mod mod)throws IOException{
        File file=new File(mod.folder,CONFIG);
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        Set<String> seen=new HashSet<>();
        for(String raw:lines(file,4096)){
            for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(c<32&&c!='\t'&&c!='\r'&&c!='\f'&&c!=11)throw new IOException("Invalid control character in "+CONFIG);}
            String line=raw.trim();if(line.isEmpty()||line.startsWith("#"))continue;
            int at=line.indexOf('=');if(at<1)throw new IOException("Invalid "+CONFIG+".");
            String name=line.substring(0,at).trim(),value=line.substring(at+1).trim();
            if(!seen.add(name))throw new IOException("Duplicate mod setting: "+name);
            if(name.equals("enabled")){if(!value.equals("true")&&!value.equals("false"))throw new IOException("Invalid enabled setting.");mod.enabled=value.equals("true");}
            else if(name.equals("priority"))mod.priority=priority(value);
            else throw new IOException("Unknown mod setting: "+name);
        }
    }
    private static boolean ownMetadata(String name){
        return name.equals(CONFIG)||name.matches("mod-settings-[0-9]+\\.tmp");
    }
    private static void validate(Mod mod)throws IOException{
        Path root=mod.folder.toPath();
        if(Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Mod must be a regular folder.");
        settings(mod);Set<String> seen=new HashSet<>();long[] bytes={0};
        Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),MAX_DEPTH+1,new SimpleFileVisitor<Path>(){
            void check(Path path)throws IOException{
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Scan cancelled.");
                if(Files.isSymbolicLink(path))throw new IOException("Symbolic links are unsupported.");
                Path relative=root.relativize(path);
                if(relative.toString().isEmpty())return;
                if(relative.getNameCount()>MAX_DEPTH)throw new IOException("Mod folders are too deep.");
                String top=relative.getName(0).toString();
                if(!top.equals("game")&&!top.equals("update"))throw new IOException("Use only game/ and update/ content.");
            }
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{
                check(path);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                if(Files.isSymbolicLink(path))throw new IOException("Symbolic links are unsupported.");
                Path relative=root.relativize(path);String value=relative.toString().replace(File.separatorChar,'/');
                if(relative.getNameCount()==1&&ownMetadata(value)&&attrs.isRegularFile())return FileVisitResult.CONTINUE;
                check(path);
                if(!attrs.isRegularFile()||!value.matches("(?i).+\\.(dat|xml|ide|ipl|txt|meta|gxt)"))throw new IOException("Unsupported overlay file: "+value);
                if(!seen.add(key(value)))throw new IOException("Duplicate path ignoring case: "+value);
                if(mod.files.size()>=MAX_FILES||attrs.size()>MAX_BYTES-bytes[0])throw new IOException("Mod exceeds 8192 files or 256 MiB.");
                bytes[0]+=attrs.size();mod.files.add(value);return FileVisitResult.CONTINUE;
            }
        });
        if(mod.files.isEmpty())throw new IOException("No supported loose files.");
        Collections.sort(mod.files);
    }
    static List<Mod> scan(File root)throws IOException{
        if(Files.isSymbolicLink(root.toPath()))throw new IOException("The mods folder cannot be a symbolic link.");
        if(!Files.exists(root.toPath(),LinkOption.NOFOLLOW_LINKS))return new ArrayList<>();
        File[] folders=root.listFiles();if(folders==null)throw new IOException("Cannot read the mods folder.");
        List<Mod> mods=new ArrayList<>();
        for(File folder:folders){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Scan cancelled.");
            if(!folder.isDirectory()&&!Files.isSymbolicLink(folder.toPath()))continue;
            Mod mod=new Mod(folder);
            try{validate(mod);}catch(IOException e){mod.problem=e.getMessage()==null?"Cannot read mod files.":e.getMessage();mod.files.clear();}
            mods.add(mod);
        }
        mods.sort(ORDER);conflicts(mods);return mods;
    }
    static void conflicts(List<Mod> mods){
        for(Mod mod:mods){mod.conflictCount=0;mod.conflicts.clear();}
        List<Mod> sorted=new ArrayList<>(mods);sorted.sort(ORDER);
        Map<String,List<Mod>> owners=new TreeMap<>();
        for(Mod mod:sorted)if(mod.enabled&&mod.admitted())for(String path:mod.files)
            owners.computeIfAbsent(key(path),unused->new ArrayList<>()).add(mod);
        for(Map.Entry<String,List<Mod>> item:owners.entrySet()){
            List<Mod> list=item.getValue();if(list.size()<2)continue;
            Mod winner=list.get(list.size()-1);
            for(Mod mod:list){
                mod.conflictCount++;
                if(mod.conflicts.size()<32)mod.conflicts.add(mod==winner
                    ? "Overrides "+(list.size()-1)+" other mod(s): "+item.getKey()
                    : "Overridden by "+winner.folder.getName()+": "+item.getKey());
            }
        }
    }
    static void save(Mod mod)throws IOException{
        if(mod.priority < -1000||mod.priority > 1000)throw new IOException("Priority must be -1000 to 1000.");
        if(Files.isSymbolicLink(mod.folder.toPath())||!Files.isDirectory(mod.folder.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Mod folder is unavailable.");
        File target=new File(mod.folder,CONFIG);
        if(Files.isSymbolicLink(target.toPath()))throw new IOException("Settings cannot be a symbolic link.");
        File temporary=File.createTempFile("mod-settings-",".tmp",mod.folder);
        try{
            try(FileOutputStream stream=new FileOutputStream(temporary)){
                stream.write(("enabled="+mod.enabled+"\npriority="+mod.priority+"\n").getBytes(StandardCharsets.UTF_8));stream.getFD().sync();
            }
            try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{temporary.delete();}
    }
    static String identity(String name){
        StringBuilder out=new StringBuilder();for(byte b:name.getBytes(StandardCharsets.UTF_8))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static Map<String,String> lastRun(File installation)throws IOException{
        File report=new File(installation,REPORT);Map<String,String> result=new HashMap<>();
        if(!Files.exists(report.toPath(),LinkOption.NOFOLLOW_LINKS))return result;
        for(String line:lines(report,128*1024)){
            String[] pair=line.split("\t",2);
            if(pair.length==2&&pair[0].matches("(?:[0-9a-f]{2})+"))result.put(pair[0],pair[1]);
        }
        return result;
    }
    private ModOverlayPolicy(){}
}

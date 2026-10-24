package com.libertyrecomp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Validated backups. Account achievements are independent of individual save packages. */
final class SaveData {
    private static final long MAX_BYTES=256L*1024*1024;
    private static final int MAX_FILES=4096;
    static File root(File data)throws IOException {
        String override=MobileConfig.read(MobileConfig.file(data)).get("saved_game_root");
        File expected=new File(data,"LibertyRecomp/saves");
        if(override!=null&&!override.isEmpty()&&!new File(override).getCanonicalFile().equals(expected.getCanonicalFile()))
            throw new IOException("A custom save folder is configured. Manage that folder separately.");
        return expected;
    }
    static File achievements(File root){return new File(root,"achievements");}
    private static File availableAchievements(File root){File current=achievements(root);return current.isDirectory()?current:new File(root.getParentFile(),"achievements");}
    private static void addTree(ZipOutputStream zip,File folder,String prefix,long[] limits)throws IOException {
        if(!folder.exists())return;
        Path base=folder.toPath();
        try(java.util.stream.Stream<Path> paths=Files.walk(base)){
            for(Iterator<Path> it=paths.iterator();it.hasNext();){Path p=it.next();if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not supported.");if(!Files.isRegularFile(p))continue;
                String relative=base.relativize(p).toString().replace(File.separatorChar,'/');
                if(prefix.equals("saves/")&&(relative.equals("achievements")||relative.startsWith("achievements/")))continue;
                if(++limits[0]>MAX_FILES||(limits[1]+=Files.size(p))>MAX_BYTES)throw new IOException("Backup exceeds the size limit.");
                ZipEntry e=new ZipEntry(prefix+relative);e.setTime(Files.getLastModifiedTime(p).toMillis());zip.putNextEntry(e);Files.copy(p,zip);zip.closeEntry();
            }
        }
    }
    static void exportZip(File root,OutputStream out)throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(out)){long[] limits={0,0};addTree(zip,root,"saves/",limits);addTree(zip,availableAchievements(root),"achievements/",limits);if(limits[0]==0)throw new IOException("No save or achievement data found.");}
    }
    private static Path destination(Path base,String relative,Set<String> seen)throws IOException {
        if(relative.isEmpty()||relative.startsWith("/")||relative.indexOf('\\')>=0||relative.indexOf('\0')>=0)throw new IOException("Unsafe archive path.");
        for(String part:relative.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Unsafe archive path.");
        Path result=base.resolve(relative).normalize();if(!result.startsWith(base)||result.equals(base)||!seen.add(result.toString()))throw new IOException("Unsafe or duplicate archive path.");return result;
    }
    private static long extractEntry(ZipInputStream zip,ZipEntry e,Path path,long total)throws IOException {
        if(e.isDirectory()){Files.createDirectories(path);return total;}
        Files.createDirectories(path.getParent());try(OutputStream out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[65536];int n;while((n=zip.read(b))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Archive is too large.");out.write(b,0,n);}}
        if(e.getTime()>0)try{Files.setLastModifiedTime(path,java.nio.file.attribute.FileTime.fromMillis(e.getTime()));}catch(IOException ignored){}
        return total;
    }
    private static void copyTree(File source,File target)throws IOException {
        if(!source.exists())return;
        try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath())){long total=0;int count=0;for(Iterator<Path> it=paths.iterator();it.hasNext();){Path p=it.next();if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not supported.");Path dest=target.toPath().resolve(source.toPath().relativize(p));if(Files.isDirectory(p))Files.createDirectories(dest);else{if(++count>MAX_FILES||(total+=Files.size(p))>MAX_BYTES)throw new IOException("Data exceeds backup limit.");Files.createDirectories(dest.getParent());Files.copy(p,dest,StandardCopyOption.COPY_ATTRIBUTES);}}}
    }
    static void importZip(File root,InputStream input)throws IOException {
        File parent=root.getParentFile();Files.createDirectories(parent.toPath());File stage=Files.createTempDirectory(parent.toPath(),"save-import-").toFile();
        File stagedSaves=new File(stage,"saves"),stagedAchievements=new File(stage,"achievements"),backup=new File(parent,"saves-before-import");
        boolean moved=false,installed=false;
        try{
            long total=0;int count=0;Set<String> seen=new HashSet<>();
            try(ZipInputStream zip=new ZipInputStream(input)){ZipEntry e;while((e=zip.getNextEntry())!=null){String name=e.getName();
                // Normalize old exports that included runtime achievements under saves/.
                if(name.startsWith("saves/achievements/"))name=name.substring(6);
                if(!name.startsWith("saves/")&&!name.startsWith("achievements/"))throw new IOException("Choose an XHYN IV save export ZIP.");
                if((name.equals("saves/")||name.equals("achievements/"))&&e.isDirectory())continue;
                if(++count>MAX_FILES)throw new IOException("Too many archive entries.");total=extractEntry(zip,e,destination(stage.toPath(),name,seen),total);
            }}
            if(total==0)throw new IOException("Archive contains no save data.");
            // Stage a complete replacement before mutating live data. Older save-only
            // backups retain account progress; achievement-only backups retain saves.
            if(!stagedSaves.exists()){copyTree(root,stagedSaves);if(!stagedSaves.exists())Files.createDirectories(stagedSaves.toPath());}
            File canonical=new File(stagedSaves,"achievements");
            if(stagedAchievements.exists()){if(canonical.exists())deleteTree(canonical);Files.move(stagedAchievements.toPath(),canonical.toPath(),StandardCopyOption.ATOMIC_MOVE);}
            else if(!canonical.exists())copyTree(availableAchievements(root),canonical);
            if(backup.exists())deleteTree(backup);
            if(root.exists()){Files.move(root.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);moved=true;}
            Files.move(stagedSaves.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;
        }catch(IOException error){try{if(installed)deleteTree(root);if(moved)Files.move(backup.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException rollback){error.addSuppressed(rollback);}throw error;}
        finally{deleteTree(stage);}
    }
    static final class Slot {
        final String relative;final File file;final long bytes,modified;
        final SaveMetadata.Header header;final float completion;
        Slot(String relative,File file,long bytes,long modified,SaveMetadata.Header header,float completion){this.relative=relative;this.file=file;this.bytes=bytes;this.modified=modified;this.header=header;this.completion=completion;}
        String label(){return SaveMetadata.mission(header,file.getName())+(Float.isFinite(completion)?String.format(Locale.ROOT," · %.2f%%",completion):" · completion unavailable")+" · "+String.format(Locale.ROOT,"%.1f KB",bytes/1024.0);}
        String details(){String saved=SaveMetadata.savedAt(header);return (saved.isEmpty()?java.text.DateFormat.getDateTimeInstance().format(new Date(modified)):saved)+" · "+file.getName();}
    }
    static List<Slot> slots(File root)throws IOException {
        List<Slot> result=new ArrayList<>();if(!root.isDirectory())return result;
        try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath(),4)){for(Iterator<Path> it=paths.iterator();it.hasNext();){Path p=it.next();if(Files.isSymbolicLink(p))continue;Path relative=root.toPath().relativize(p);if(relative.getNameCount()!=4||!relative.getName(2).toString().equals("00000001"))continue;long size=0,modified=Files.getLastModifiedTime(p).toMillis();
            if(Files.isRegularFile(p))size=Files.size(p);else try(java.util.stream.Stream<Path> content=Files.walk(p)){int count=0;for(Iterator<Path> files=content.iterator();files.hasNext();){Path f=files.next();if(Files.isSymbolicLink(f))throw new IOException("Symbolic links are not supported.");if(Files.isRegularFile(f)){size+=Files.size(f);modified=Math.max(modified,Files.getLastModifiedTime(f).toMillis());if(++count>MAX_FILES||size>MAX_BYTES)throw new IOException("Save package is too large.");}}}
            String identity=relative.toString().replace(File.separatorChar,'/');
            File headerFile=slotHeader(root,identity);SaveMetadata.Header header=SaveMetadata.header(headerFile,p.getFileName().toString(),relative.getName(1).toString());
            if(headerFile.isFile())size+=headerFile.length();
            File save=Files.isDirectory(p)?new File(p.toFile(),p.getFileName().toString()):p.toFile();
            float completion=SaveMetadata.completion(save,relative.getName(1).toString());
            result.add(new Slot(identity,p.toFile(),size,modified,header,completion));
        }}result.sort(Comparator.comparingLong((Slot slot)->slot.modified).reversed());return result;
    }
    private static File slotPath(File root,String relative)throws IOException {
        String[] parts=relative.split("/");if(parts.length!=4||!parts[2].equals("00000001"))throw new IOException("Invalid save package.");
        return safePath(root,relative);
    }
    private static File safePath(File root,String relative)throws IOException {
        Path normalized=destination(root.toPath(),relative,new HashSet<>());Path cursor=root.toPath();if(Files.isSymbolicLink(cursor))throw new IOException("Symbolic links are not supported.");for(Path part:root.toPath().relativize(normalized)){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))throw new IOException("Symbolic links are not supported.");}return normalized.toFile();
    }
    private static File slotHeader(File root,String relative)throws IOException {
        slotPath(root,relative);String[] parts=relative.split("/");File header=safePath(root,parts[0]+"/"+parts[1]+"/Headers/"+parts[2]+"/"+parts[3]+".header");
        if(header.exists()&&!header.isFile())throw new IOException("The save package header is not a regular file.");return header;
    }
    static void exportSlot(File root,String relative,OutputStream out)throws IOException {
        File selected=slotPath(root,relative),header=slotHeader(root,relative);if(!selected.exists())throw new IOException("Save package no longer exists.");
        try(ZipOutputStream zip=new ZipOutputStream(out)){long[] limits={0,0};zip.putNextEntry(new ZipEntry("slot-path.txt"));zip.write(relative.getBytes(StandardCharsets.UTF_8));zip.closeEntry();if(selected.isDirectory())addTree(zip,selected,"slot/",limits);else{if(selected.length()>MAX_BYTES)throw new IOException("Save is too large.");limits[1]=selected.length();ZipEntry e=new ZipEntry("slot-file");e.setTime(selected.lastModified());zip.putNextEntry(e);Files.copy(selected.toPath(),zip);zip.closeEntry();}
            if(header.isFile()){if(++limits[0]>MAX_FILES||(limits[1]+=header.length())>MAX_BYTES)throw new IOException("Backup exceeds the size limit.");ZipEntry e=new ZipEntry("slot-header");e.setTime(header.lastModified());zip.putNextEntry(e);Files.copy(header.toPath(),zip);zip.closeEntry();}}
    }
    static File recoveryFolder(File root){return new File(root.getParentFile(),"slot-recovery");}
    static File recovery(File root,String relative)throws IOException {
        String name=new File(relative).getName().replaceAll("[^A-Za-z0-9._-]","_");if(name.length()>64)name=name.substring(0,64);StringBuilder digest=new StringBuilder();try{for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(relative.getBytes(StandardCharsets.UTF_8)))digest.append(String.format(Locale.ROOT,"%02x",b&255));}catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}Files.createDirectories(recoveryFolder(root).toPath());return new File(recoveryFolder(root),name+"-"+digest+".zip");
    }
    static void protectSlot(File root,String relative)throws IOException {
        File finalCopy=recovery(root,relative),temp=File.createTempFile("slot-copy-",".zip",recoveryFolder(root));try{try(FileOutputStream out=new FileOutputStream(temp)){exportSlot(root,relative,out);}Files.move(temp.toPath(),finalCopy.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{temp.delete();}
    }
    static void deleteSlot(File root,String relative)throws IOException {
        File slot=slotPath(root,relative),header=slotHeader(root,relative);if(!slot.exists())throw new IOException("Save no longer exists.");protectSlot(root,relative);
        File stage=Files.createTempDirectory(root.getParentFile().toPath(),"slot-delete-").toFile();boolean[] preserve={false};try{replaceSlot(slot,header,null,null,stage,preserve);}finally{if(!preserve[0])deleteTree(stage);}
    }
    private static byte[] bounded(InputStream in,int maximum)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1){if(out.size()+n>maximum)throw new IOException("Slot identity is too large.");out.write(b,0,n);}return out.toByteArray();}
    static String recoverySlot(File zip)throws IOException {try(ZipFile archive=new ZipFile(zip)){ZipEntry e=archive.getEntry("slot-path.txt");if(e==null||e.getSize()>2048)throw new IOException("Invalid slot backup.");try(InputStream in=archive.getInputStream(e)){return new String(bounded(in,2048),StandardCharsets.UTF_8);}}}
    static void importSlot(File root,String relative,InputStream input)throws IOException {
        File target=slotPath(root,relative),header=slotHeader(root,relative);Files.createDirectories(target.getParentFile().toPath());File stage=Files.createTempDirectory(root.getParentFile().toPath(),"slot-import-").toFile();File folder=new File(stage,"directory"),single=new File(stage,"file"),stagedHeader=new File(stage,"header");String archived=null;long total=0;int count=0;Set<String> seen=new HashSet<>();boolean[] preserve={false};
        try{try(ZipInputStream zip=new ZipInputStream(input)){ZipEntry e;while((e=zip.getNextEntry())!=null){if(++count>MAX_FILES)throw new IOException("Too many slot entries.");String name=e.getName();if(name.equals("slot-path.txt")){if(archived!=null)throw new IOException("Duplicate slot identity.");byte[] text=bounded(zip,2048);if(text.length>2048)throw new IOException("Invalid slot identity.");archived=new String(text,StandardCharsets.UTF_8);}else if(name.equals("slot-file")){if(e.isDirectory())throw new IOException("Invalid save file entry.");total=extractEntry(zip,e,destination(stage.toPath(),"file",seen),total);}else if(name.equals("slot-header")){if(e.isDirectory())throw new IOException("Invalid save header entry.");byte[] bytes=bounded(zip,0x148);if(bytes.length!=0x148)throw new IOException("Invalid save package header.");Files.write(destination(stage.toPath(),"header",seen),bytes,StandardOpenOption.CREATE_NEW);}else if(name.startsWith("slot/")){if(name.equals("slot/")&&e.isDirectory())continue;total=extractEntry(zip,e,destination(folder.toPath(),name.substring(5),seen),total);}else throw new IOException("Choose an individual slot backup.");}}
            if(!relative.equals(archived)||total==0||folder.exists()==single.exists())throw new IOException("This backup does not match the selected slot.");
            String[] parts=relative.split("/");
            if(stagedHeader.exists()){
                if(SaveMetadata.header(stagedHeader,target.getName(),parts[1])==null)throw new IOException("The package header does not match this save slot.");
            }else if(header.exists()||header.getParentFile().exists())throw new IOException("This older slot backup omits its package header. Use a full save backup instead.");
            // Recheck both live paths after reading an external archive, before any mutation.
            slotPath(root,relative);slotHeader(root,relative);
            if(stagedHeader.exists())Files.createDirectories(header.getParentFile().toPath());
            if(target.exists())protectSlot(root,relative);
            replaceSlot(target,header,folder.exists()?folder:single,stagedHeader.exists()?stagedHeader:null,stage,preserve);
        }finally{if(!preserve[0])deleteTree(stage);}
    }
    /** Coordinate package and enumeration header; retain originals if rollback itself fails. */
    private static void replaceSlot(File target,File header,File replacement,File replacementHeader,File stage,boolean[] preserve)throws IOException {
        File previous=new File(stage,"previous"),previousHeader=new File(stage,"previous-header");boolean old=false,oldHeader=false,installed=false,installedHeader=false;
        try{
            if(target.exists()){Files.move(target.toPath(),previous.toPath(),StandardCopyOption.ATOMIC_MOVE);old=true;}
            if(header.exists()){Files.move(header.toPath(),previousHeader.toPath(),StandardCopyOption.ATOMIC_MOVE);oldHeader=true;}
            if(replacement!=null){Files.move(replacement.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;}
            if(replacementHeader!=null){Files.move(replacementHeader.toPath(),header.toPath(),StandardCopyOption.ATOMIC_MOVE);installedHeader=true;}
        }catch(IOException failure){
            try{if(installedHeader)Files.delete(header.toPath());}catch(IOException error){preserve[0]=true;failure.addSuppressed(error);}
            try{if(installed)deleteTree(target);}catch(IOException error){preserve[0]=true;failure.addSuppressed(error);}
            try{if(old)Files.move(previous.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException error){preserve[0]=true;failure.addSuppressed(error);}
            try{if(oldHeader)Files.move(previousHeader.toPath(),header.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException error){preserve[0]=true;failure.addSuppressed(error);}
            if(preserve[0])throw new IOException("Save update failed; originals are retained in "+stage,failure);
            throw failure;
        }
    }
    static void deleteTree(File root)throws IOException {
        if(!root.exists())return;
        try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())){Path[] all=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);for(Path p:all)Files.delete(p);}
    }
}

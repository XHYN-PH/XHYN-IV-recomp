package com.libertyrecomp;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Native streaming uses direct paths. A picked SAF folder is a one-time copy source. */
final class GameStorage {
    private static final long RESERVE=64L*1024*1024,MAX_FILE=64L*1024*1024*1024;
    private static File choice(File data){return new File(data,"LibertyRecomp/game-location.txt");}
    static File installRoot(File data){
        File stored=choice(data);try{if(stored.isFile()&&stored.length()<4096){String path=new String(Files.readAllBytes(stored.toPath()),StandardCharsets.UTF_8).trim();if(!path.isEmpty()&&new File(path).isAbsolute())return new File(path);}}catch(IOException ignored){}
        return new File(data,"LibertyRecomp");
    }
    static File accessible(Activity a)throws IOException {
        File[] media=a.getExternalMediaDirs();if(media!=null)for(File folder:media)if(folder!=null){File target=new File(folder,"XHYN-IV");Files.createDirectories(target.toPath());if(target.canRead()&&target.canWrite())return target.getCanonicalFile();}
        throw new IOException("This device does not provide an accessible app media folder.");
    }
    private static void select(File data,File root)throws IOException {
        File target=choice(data);Files.createDirectories(target.getParentFile().toPath());File temp=File.createTempFile("game-location-",".tmp",target.getParentFile());
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(root.getCanonicalPath().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }finally{temp.delete();}
    }
    private static void space(File destination,long bytes)throws IOException {
        long free=destination.getUsableSpace();
        if(free>0&&(bytes>Long.MAX_VALUE-RESERVE||bytes+RESERVE>free))throw new IOException("Not enough free space in the destination. Keep at least "+((bytes+RESERVE)/1048576)+" MB available; the source is kept.");
    }
    private static long treeSize(File source)throws IOException {
        long bytes=0;int count=0;
        try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath(),33)){for(Iterator<Path> it=paths.iterator();it.hasNext();){Path p=it.next();
            if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not supported in game folders.");
            if(Files.isDirectory(p)){if(source.toPath().relativize(p).getNameCount()>32)throw new IOException("Game folder nesting is too deep.");continue;}
            if(!Files.isRegularFile(p)||++count>200000)throw new IOException("Unsupported or excessive game files.");
            long size=Files.size(p);if(size>MAX_FILE||bytes>Long.MAX_VALUE-size)throw new IOException("Game files are too large.");bytes+=size;
        }}return bytes;
    }
    private static final class RecoveryFailure extends IOException {
        RecoveryFailure(File stage,IOException cause){super("Could not finish rollback. Original and staged files were kept. Recovery folder: "+stage,cause);}
    }
    /** Publish both trees and the choice as one operation, rolling back every completed rename. */
    private static void publish(File data,File root,File stage,boolean replace)throws IOException {
        File backup=new File(root,"folder-import-recovery");List<String> old=new ArrayList<>(),installed=new ArrayList<>();
        if(replace&&backup.exists())throw new IOException("A folder-import recovery copy already exists at "+backup+". Keep it elsewhere before importing again.");
        try{
            for(String name:new String[]{"game","dlc"}){
                File next=new File(stage,name),target=new File(root,name);if(!next.exists())continue;
                if(target.exists()){
                    if(!replace)throw new IOException("Destination already contains "+name+". Select that installation separately.");
                    Files.createDirectories(backup.toPath());Files.move(target.toPath(),new File(backup,name).toPath(),StandardCopyOption.ATOMIC_MOVE);old.add(name);
                }
                Files.move(next.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE);installed.add(name);
            }
            select(data,root);
        }catch(IOException failure){
            IOException rollback=null;Collections.reverse(installed);Collections.reverse(old);
            for(String name:installed)try{Files.move(new File(root,name).toPath(),new File(stage,name).toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){if(rollback==null)rollback=e;else rollback.addSuppressed(e);}
            for(String name:old)try{Files.move(new File(backup,name).toPath(),new File(root,name).toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(IOException e){if(rollback==null)rollback=e;else rollback.addSuppressed(e);}
            if(backup.isDirectory()){File[] left=backup.listFiles();if(left!=null&&left.length==0)backup.delete();}
            if(rollback!=null){failure.addSuppressed(rollback);throw new RecoveryFailure(stage,failure);}throw failure;
        }
    }
    static void switchTo(File data,File target)throws IOException {
        File source=installRoot(data).getCanonicalFile();target=target.getCanonicalFile();if(source.equals(target))return;
        if(target.toPath().startsWith(new File(source,"game").toPath())||target.toPath().startsWith(new File(source,"dlc").toPath()))throw new IOException("Choose a destination outside the current game and episode folders.");
        Files.createDirectories(target.toPath());if(!target.canRead()||!target.canWrite())throw new IOException("Destination is unavailable or read-only. Select another location.");
        if(new File(target,"game").exists()){validate(new File(target,"game"));select(data,target);return;}
        if(!new File(source,"game").exists()){space(target,0);select(data,target);return;}
        long bytes=treeSize(new File(source,"game"));if(new File(source,"dlc").exists())bytes=Math.addExact(bytes,treeSize(new File(source,"dlc")));space(target,bytes);
        File stage=Files.createTempDirectory(target.toPath(),"game-copy-").toFile();boolean preserve=false;
        try{
            for(String name:new String[]{"game","dlc"}){File from=new File(source,name);if(from.exists())copyTree(from,new File(stage,name));}
            validate(new File(stage,"game"));publish(data,target,stage,false);
        }catch(RecoveryFailure e){preserve=true;throw e;}finally{if(!preserve)SaveData.deleteTree(stage);}
    }
    private static void validate(File game)throws IOException {String problem=Installer.nativeInstallState(game.getAbsolutePath());if(problem!=null&&!problem.isEmpty())throw new IOException(problem);}
    private static void copyTree(File source,File target)throws IOException {
        int count=0;try(java.util.stream.Stream<Path> paths=Files.walk(source.toPath(),33)){for(Iterator<Path> it=paths.iterator();it.hasNext();){Path p=it.next();
            if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not supported in game folders.");Path dest=target.toPath().resolve(source.toPath().relativize(p));
            if(Files.isDirectory(p))Files.createDirectories(dest);else{if(!Files.isRegularFile(p)||++count>200000)throw new IOException("Unsupported or excessive game files.");Files.createDirectories(dest.getParent());try(InputStream in=Files.newInputStream(p)){copyFile(in,dest.toFile(),Files.size(p));}Files.setLastModifiedTime(dest,Files.getLastModifiedTime(p));}
        }}
    }
    private static void copyFile(InputStream in,File target,long expected)throws IOException {
        if(expected>MAX_FILE)throw new IOException("Game file is too large.");space(target.getParentFile(),Math.max(0,expected));
        try{
            MessageDigest source=MessageDigest.getInstance("SHA-256");long bytes=0,nextSpace=0;byte[] buffer=new byte[1024*1024];
            try(FileOutputStream out=new FileOutputStream(target)){int n;while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>MAX_FILE)throw new IOException("Game file is too large.");if(bytes>=nextSpace){space(target.getParentFile(),n);nextSpace=bytes+16*1024*1024;}source.update(buffer,0,n);out.write(buffer,0,n);}out.getFD().sync();}
            if(expected>=0&&expected!=bytes)throw new IOException("Incomplete copy: "+target.getName());MessageDigest copied=MessageDigest.getInstance("SHA-256");
            try(InputStream check=new FileInputStream(target)){int n;while((n=check.read(buffer))!=-1)copied.update(buffer,0,n);}if(!Arrays.equals(source.digest(),copied.digest()))throw new IOException("Copy verification failed: "+target.getName());
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    static void importTree(Context context,File data,Uri tree)throws IOException {
        File root=installRoot(data);Files.createDirectories(root.toPath());space(root,0);File stage=Files.createTempDirectory(root.toPath(),"folder-import-").toFile();boolean preserve=false;
        try{
            File incoming=new File(stage,"incoming");String document=DocumentsContract.getTreeDocumentId(tree);copyDocuments(context,tree,document,incoming,new int[]{0},0);
            File game=new File(incoming,"default.xex").isFile()?incoming:new File(incoming,"game");validate(game);
            if(game.equals(incoming)){Files.move(incoming.toPath(),new File(stage,"game").toPath(),StandardCopyOption.ATOMIC_MOVE);}
            else{
                Files.move(game.toPath(),new File(stage,"game").toPath(),StandardCopyOption.ATOMIC_MOVE);
                File dlc=new File(incoming,"dlc");if(dlc.isDirectory())Files.move(dlc.toPath(),new File(stage,"dlc").toPath(),StandardCopyOption.ATOMIC_MOVE);
            }
            publish(data,root,stage,true);
        }catch(RecoveryFailure e){preserve=true;throw e;}catch(SecurityException e){throw new IOException("Folder access was revoked. Select the source folder again. The current installation was kept.",e);}
        finally{if(!preserve)SaveData.deleteTree(stage);}
    }
    private static void copyDocuments(Context c,Uri tree,String parent,File target,int[] count,int depth)throws IOException {
        if(depth>32)throw new IOException("Game folder nesting is too deep.");Files.createDirectories(target.toPath());Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE};
        try(Cursor cursor=c.getContentResolver().query(children,columns,null,null,null)){
            if(cursor==null)throw new IOException("Cannot read the selected folder.");Set<String> names=new HashSet<>();while(cursor.moveToNext()){
                if(++count[0]>200000)throw new IOException("Too many files.");String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\")||name.chars().anyMatch(Character::isISOControl)||!names.add(name.toLowerCase(Locale.ROOT)))throw new IOException("Invalid or duplicate game filename.");File next=new File(target,name);
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))copyDocuments(c,tree,id,next,count,depth+1);
                else{Uri file=DocumentsContract.buildDocumentUriUsingTree(tree,id);try(InputStream in=c.getContentResolver().openInputStream(file)){if(in==null)throw new IOException("Cannot read "+name);copyFile(in,next,cursor.isNull(3)?-1:cursor.getLong(3));}}
            }
        }catch(SecurityException e){throw new IOException("Folder permission was lost. Select the source folder again.",e);}
    }
    private GameStorage(){}
}

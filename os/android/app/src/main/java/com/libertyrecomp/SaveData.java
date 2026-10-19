package com.libertyrecomp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Save-only ZIPs. A complete import is validated before replacing live data. */
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
    static void exportZip(File root,OutputStream out)throws IOException {
        if(!root.isDirectory())throw new IOException("No save data found.");
        try(ZipOutputStream zip=new ZipOutputStream(out)) {
            final int[] count={0};final long[] bytes={0};
            try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())) {
                Iterator<Path> it=paths.iterator();
                while(it.hasNext()) {
                    Path p=it.next();if(Files.isSymbolicLink(p))throw new IOException("Symbolic links are not supported.");
                    if(!Files.isRegularFile(p))continue;
                    if(++count[0]>MAX_FILES||(bytes[0]+=Files.size(p))>MAX_BYTES)throw new IOException("Save data exceeds export limit.");
                    zip.putNextEntry(new ZipEntry("saves/"+root.toPath().relativize(p).toString().replace(File.separatorChar,'/')));
                    Files.copy(p,zip);zip.closeEntry();
                }
            }
            if(count[0]==0)throw new IOException("No save files found.");
        }
    }
    static void importZip(File root,InputStream input)throws IOException {
        File parent=root.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create save folder.");
        File stage=Files.createTempDirectory(parent.toPath(),"save-import-").toFile();
        File backup=new File(parent,"saves-before-import");
        boolean installed=false;
        try {
            long total=0;int count=0;Set<String> seen=new HashSet<>();
            try(ZipInputStream zip=new ZipInputStream(input)) {
                ZipEntry e;byte[] buffer=new byte[65536];
                while((e=zip.getNextEntry())!=null) {
                    String name=e.getName();
                    if(!name.startsWith("saves/")||name.indexOf('\\')>=0||name.indexOf('\0')>=0)
                        throw new IOException("Choose an XHYN IV save export ZIP.");
                    String relative=name.substring(6);if(relative.isEmpty()&&e.isDirectory())continue;
                    Path dest=stage.toPath().resolve(relative).normalize();
                    if(relative.isEmpty()||!dest.startsWith(stage.toPath())||dest.equals(stage.toPath())||!seen.add(dest.toString()))
                        throw new IOException("Unsafe or duplicate save entry.");
                    if(++count>MAX_FILES)throw new IOException("Too many save entries.");
                    if(e.isDirectory()){Files.createDirectories(dest);continue;}
                    Files.createDirectories(dest.getParent());
                    try(OutputStream out=Files.newOutputStream(dest,StandardOpenOption.CREATE_NEW)) {
                        int n;while((n=zip.read(buffer))!=-1) {
                            total+=n;if(total>MAX_BYTES)throw new IOException("Save archive is too large.");out.write(buffer,0,n);
                        }
                    }
                }
            }
            if(total==0)throw new IOException("Archive contains no save data.");
            if(backup.exists())deleteTree(backup); // Import confirmation explains replacement of the previous backup.
            if(root.exists())Files.move(root.toPath(),backup.toPath(),StandardCopyOption.ATOMIC_MOVE);
            try {Files.move(stage.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);installed=true;}
            catch(IOException failure) {
                if(backup.exists()&&!root.exists())Files.move(backup.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);
                throw failure;
            }
        } finally {if(!installed)deleteTree(stage);}
    }
    static void deleteTree(File root)throws IOException {
        if(!root.exists())return;
        // walk does not follow symlinks; deleting a link never touches its target.
        try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())) {
            Path[] all=paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for(Path p:all)Files.delete(p);
        }
    }
}

package com.libertyrecomp;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** File-backed choice shared by the launcher and the separate game process. */
final class TouchLayoutSettings {
    static boolean xbox(File root) {
        File file=new File(root,"touch-layout.txt");
        try{return file.length()<64 && "gamepad".equals(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).trim());}
        catch(IOException error){return false;}
    }
    static void save(File root,boolean xbox) throws IOException {
        Files.createDirectories(root.toPath());
        File temporary=File.createTempFile("touch-layout-",".tmp",root);
        try {
            Files.write(temporary.toPath(),(xbox?"gamepad\n":"liberty\n").getBytes(StandardCharsets.UTF_8));
            File target=new File(root,"touch-layout.txt");
            try{Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{temporary.delete();}
    }
    private TouchLayoutSettings(){}
}

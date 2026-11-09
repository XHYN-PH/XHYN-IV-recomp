import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
public class Reframe {
 public static void main(String[] args) throws Exception {
  try(ZipInputStream in=new ZipInputStream(Files.newInputStream(Path.of(args[0]))); ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
   ZipEntry e;
   while((e=in.getNextEntry())!=null) {
    byte[] data=in.readAllBytes();
    if(e.getName().endsWith(".class")) {
     ClassReader r=new ClassReader(data); ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
     r.accept(w,ClassReader.SKIP_FRAMES); data=w.toByteArray();
    }
    out.putNextEntry(new ZipEntry(e.getName())); out.write(data); out.closeEntry();
   }
  }
 }
}

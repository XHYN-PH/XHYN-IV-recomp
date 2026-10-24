package com.libertyrecomp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** Exercise independent packages, account progress, recovery and rejected imports. */
public final class SaveSlotsTest {
 private static byte[] archive(String identity,String... names)throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("slot-path.txt"));zip.write(identity.getBytes(StandardCharsets.UTF_8));zip.closeEntry();for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write(new byte[]{99});zip.closeEntry();}}return bytes.toByteArray();}
 public static void main(String[] args)throws Exception {
  File data=Files.createTempDirectory("xhyn-slot-tests-").toFile();try{
   File root=SaveData.root(data);String first="0000000000000001/545407F2/00000001/SGTA401",second="0000000000000001/545407F2/00000001/SGTA402";
   File one=new File(root,first+"/save.dat"),two=new File(root,second+"/save.dat"),awards=new File(SaveData.achievements(root),"unlocked.json");
   one.getParentFile().mkdirs();two.getParentFile().mkdirs();awards.getParentFile().mkdirs();Files.write(one.toPath(),new byte[]{1,2});Files.write(two.toPath(),new byte[]{3,4});Files.writeString(awards.toPath(),"earned");
   assert SaveData.slots(root).size()==2;assert !SaveData.recovery(root,first.replace("SGTA401","SGTA4-01")).equals(SaveData.recovery(root,first.replace("SGTA401","SGTA4_01")));
   ByteArrayOutputStream backup=new ByteArrayOutputStream();SaveData.exportSlot(root,first,backup);Files.write(one.toPath(),new byte[]{5,6});SaveData.importSlot(root,first,new ByteArrayInputStream(backup.toByteArray()));assert Arrays.equals(Files.readAllBytes(one.toPath()),new byte[]{1,2});assert Arrays.equals(Files.readAllBytes(two.toPath()),new byte[]{3,4});assert Files.readString(awards.toPath()).equals("earned");
   for(byte[] bad:new byte[][]{archive(first,"slot/../../escape"),archive(second,"slot/save.dat"),archive(first,"slot-file","slot/save.dat"),archive(first,"unknown")}){try{SaveData.importSlot(root,first,new ByteArrayInputStream(bad));throw new AssertionError("Unsafe or mismatched backup accepted");}catch(IOException expected){}assert Arrays.equals(Files.readAllBytes(one.toPath()),new byte[]{1,2});assert two.isFile()&&awards.isFile();}
   SaveData.deleteSlot(root,first);assert !one.exists()&&two.isFile()&&awards.isFile();File recovery=SaveData.recovery(root,first);assert SaveData.recoverySlot(recovery).equals(first);try(InputStream in=new FileInputStream(recovery)){SaveData.importSlot(root,first,in);}assert Arrays.equals(Files.readAllBytes(one.toPath()),new byte[]{1,2});
   ByteArrayOutputStream all=new ByteArrayOutputStream();SaveData.exportZip(root,all);try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(all.toByteArray()))){ZipEntry e;boolean found=false;while((e=z.getNextEntry())!=null){assert !e.getName().startsWith("saves/achievements/");if(e.getName().equals("achievements/unlocked.json"))found=true;}assert found;}
   MobileConfig.ensureDefaults(MobileConfig.file(data));MobileConfig.update(MobileConfig.file(data),Collections.singletonMap("gta4_draw_distance_scale","1.2"));MobileConfig.ensureDefaults(MobileConfig.file(data));assert MobileConfig.read(MobileConfig.file(data)).get("gta4_draw_distance_scale").equals("1.2");assert MobileConfig.read(MobileConfig.file(data)).get("gta4_native_converted_texture_disk_cache").equals("true");
   NextLaunchConfig.save(data,Map.of("resolution","1080p","gta4_shadow_map_base_size","512"));MobileConfig.update(MobileConfig.file(data),Map.of("gta4_frame_limit","40"));assert NextLaunchConfig.read(data).get("gta4_shadow_map_base_size").equals("512");NextLaunchConfig.activate(data);assert !NextLaunchConfig.file(data).exists();assert MobileConfig.read(MobileConfig.file(data)).get("gta4_shadow_map_base_size").equals("512");assert MobileConfig.read(MobileConfig.file(data)).get("gta4_frame_limit").equals("40");
   NextLaunchConfig.save(data,Map.of("android_gpu_turbo","true","gta4_safe_mode","true"));
   Map<String,String> launch=PerformanceLaunchOptions.nextSettings(data);
   assert launch.get("android_gpu_turbo").equals("true")&&launch.get("gta4_safe_mode").equals("true");
   System.out.println("Save slots: isolation, round trip, deletion recovery, archive rejection, achievements and upgrade-safe defaults passed");
  }finally{SaveData.deleteTree(data);}
 }
}

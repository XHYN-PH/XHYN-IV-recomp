package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class MobileOptionsTest {
    static byte[] zip(String... names)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(bytes)) {
            for(String name:names){z.putNextEntry(new ZipEntry(name));z.write(new byte[]{1,2,3});z.closeEntry();}
        }return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        assert PhoneRumblePolicy.amplitude(0,0,1)==0;
        assert PhoneRumblePolicy.amplitude(65535,0,1)==255;
        assert PhoneRumblePolicy.amplitude(0,65535,.5f)==128;
        assert PhoneRumblePolicy.amplitude(65535,65535,0)==0;
        assert PhoneRumblePolicy.amplitude(-1,1,1)==1;
        assert PhoneRumblePolicy.amplitude(Integer.MAX_VALUE,-5,2)==255;
        assert PhoneRumblePolicy.amplitude(65535,0,Float.NaN)==0;
        assert PhoneRumblePolicy.amplitude(65535,0,Float.POSITIVE_INFINITY)==0;
        ArrayList<String> a=new ArrayList<>(List.of("--input_trace=true"));
        DebugLaunchOptions.apply(a,false,false,null);assert a.contains("--input_trace=false")&&a.contains("--log_file=/dev/null")&&a.contains("--log_level=off")&&a.contains("--diagnostics=false")&&a.contains("--diagnostics_categories=")&&a.contains("--android_thermal_log=false");
        DebugLaunchOptions.apply(a,true,false,new File("/tmp/debug-20000101-000000-000.log"));
        assert a.contains("--input_trace=false")&&a.contains("--gta4_native_frame_stats=true");
        assert a.stream().filter(v->v.startsWith("--input_trace=")).count()==1;
        DebugLaunchOptions.apply(a,true,true,null);assert a.contains("--input_trace=true");
        DebugLaunchOptions.rendererCapture(a);assert a.contains("--gta4_profile_native_autostart=false")&&a.contains("--gta4_profile_native_samples=120");
        a.add("--gta4_pipeline_stats=true");a.add("--gta4_native_detailed_counters=true");a.add("--log_file=/tmp/old-debug.log");a.add("--diagnostics_categories=logging,native-profiler");a.add("--present_timing_stats=true");
        DebugLaunchOptions.apply(a,false,false,null);
        assert a.contains("--gta4_pipeline_stats=false")&&a.contains("--gta4_native_detailed_counters=false")&&!a.contains("--gta4_pipeline_stats=true");
        assert a.contains("--diagnostics_categories=")&&!a.contains("--diagnostics_categories=logging,native-profiler")&&a.contains("--present_timing_stats=false")&&!a.contains("--log_file=/tmp/old-debug.log");
        ArrayList<String> quality=new ArrayList<>(List.of("--gta4_native_shadow_interval=2","--gta4_native_water_reflection_interval=3","--gta4_draw_distance_scale=0.75"));
        PerformanceLaunchOptions.apply(quality,false,true,false);
        assert quality.contains("--gta4_native_shadow_interval=2")&&quality.contains("--gta4_native_water_reflection_interval=3")&&quality.contains("--gta4_draw_distance_scale=0.75");
        assert RefreshPolicy.choose(new float[]{60,90,120},40)==120;
        assert RefreshPolicy.choose(new float[]{60,90,120},90)==90;
        assert RefreshPolicy.choose(new float[]{60,120},0)==120;
        assert RefreshPolicy.choose(new float[]{Float.NaN,0},60)==0;
        File data=Files.createTempDirectory("mobile-options").toFile(),config=MobileConfig.file(data);
        config.getParentFile().mkdirs();Files.writeString(config.toPath(),"# existing\nother = 7\nresolution = \"720p\"\n");
        MobileConfig.update(config,Map.of("resolution","540p","gta4_motion_blur","false"));
        assert MobileConfig.read(config).get("resolution").equals("540p");
        assert Files.readString(config.toPath()).contains("other = 7");
        MobileConfig.update(config,Map.of("gta4_touch_radar_margin","0.04","gta4_shadow_distance_scale","0.75"));
        MobileConfig.migrateRadarMargin(config);assert MobileConfig.read(config).get("gta4_touch_radar_margin").equals("0")&&MobileConfig.read(config).get("gta4_shadow_distance_scale").equals("1");
        MobileConfig.update(config,Map.of("gta4_touch_radar_margin","0.08"));MobileConfig.migrateRadarMargin(config);assert MobileConfig.read(config).get("gta4_touch_radar_margin").equals("0.08");
        MobileConfig.update(config,Map.of("gta4_touch_radar_margin","-0.015"));MobileConfig.migrateRadarMargin(config);assert MobileConfig.read(config).get("gta4_touch_radar_margin").equals("-0.015");
        boolean rejected=false;try{MobileConfig.update(config,Map.of("resolution","720p\nmalicious=true"));}catch(IOException expected){rejected=true;}assert rejected;
        File oldConfig=new File(data,"old-upscaler.toml");
        Files.writeString(oldConfig.toPath(),"# keep this comment\ngta4_native_upscaler = \"sgsr\" # old selection\npresent_sgsr = true\nresolution = \"540p\"\n");
        MobileConfig.migrateRemovedUpscaler(oldConfig);
        assert MobileConfig.read(oldConfig).get("gta4_native_upscaler").equals("native");
        assert !MobileConfig.read(oldConfig).containsKey("present_sgsr");
        assert MobileConfig.read(oldConfig).get("resolution").equals("540p");
        assert Files.readString(oldConfig.toPath()).contains("# keep this comment");
        String migrated=Files.readString(oldConfig.toPath());MobileConfig.migrateRemovedUpscaler(oldConfig);
        assert Files.readString(oldConfig.toPath()).equals(migrated);
        MobileConfig.update(oldConfig,Map.of("gta4_native_upscaler","fsr1"));
        MobileConfig.migrateRemovedUpscaler(oldConfig);
        assert MobileConfig.read(oldConfig).get("gta4_native_upscaler").equals("fsr1");
        {
        File legacyRoot=Files.createTempDirectory("layout-legacy").toFile();
        Files.writeString(new File(legacyRoot,"liberty_touch_layout.txt").toPath(),"on_foot/fire 10 20 1 0\n");
        TouchLayoutDocument.migrate(data,legacyRoot);
        TouchLayoutDocument layout=TouchLayoutDocument.read(data);
        assert layout.get("on_foot/fire").dx==10;
        layout.get("xbox/a").dx=-350;layout.get("xbox/a").scale=4;layout.get("xbox/a").hidden=true;layout.save(data);
        TouchLayoutDocument.migrate(data,legacyRoot);layout=TouchLayoutDocument.read(data);
        assert layout.get("on_foot/fire").dx==10&&layout.get("xbox/a").dx==-350&&layout.get("xbox/a").scale==4&&layout.get("xbox/a").hidden;
        File externalLegacy=Files.createTempDirectory("layout-external").toFile();
        Files.writeString(new File(externalLegacy,"liberty_touch_layout.txt").toPath(),"on_foot/aim 7 3 1 0\n");
        TouchLayoutDocument.migrate(externalLegacy,new File(externalLegacy,"empty"));
        assert TouchLayoutDocument.read(externalLegacy).get("on_foot/aim").dx==7;
        }
        File saves=SaveData.root(data);new File(saves,"profile/545407F2/00000001/slot").mkdirs();
        File slot=new File(saves,"profile/545407F2/00000001/slot/save.dat");Files.write(slot.toPath(),new byte[]{5,6});
        ByteArrayOutputStream out=new ByteArrayOutputStream();SaveData.exportZip(saves,out);
        for(byte[] bad:new byte[][]{zip("saves/../../escape"),zip("other/file"),zip("saves/a/../same","saves/same"),new byte[]{1,2,3}}) {
            rejected=false;try{SaveData.importZip(saves,new ByteArrayInputStream(bad));}catch(IOException expected){rejected=true;}
            assert rejected&&Files.readAllBytes(slot.toPath())[0]==5;
        }
        SaveData.importZip(saves,new ByteArrayInputStream(zip("saves/profile/new-save")));
        assert new File(saves,"profile/new-save").isFile();assert new File(saves.getParentFile(),"saves-before-import/profile/545407F2/00000001/slot/save.dat").isFile();
        SaveData.deleteTree(saves);assert !saves.exists()&&config.isFile();
        File logs=new File(data,"debug_logs");logs.mkdirs();
        String old="debug-20000101-000000-000.log",latest="debug-20000102-000000-000.log";
        Files.writeString(new File(logs,old).toPath(),"old failure");Files.writeString(new File(logs,latest).toPath(),"new session");
        Files.writeString(new File(logs,latest+".runtime.log").toPath(),"frame stats");
        Files.writeString(new File(logs,old+".runtime.log").toPath(),"old crash");
        assert DebugLogArchive.sessions(data,data)[0].equals(latest);
        out=new ByteArrayOutputStream();DebugLogArchive.write(out,data,data,latest);
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;int files=0;while((e=z.getNextEntry())!=null){assert !e.getName().contains(old);z.readAllBytes();++files;}assert files==3;
        }
        File profile=new File(logs,latest+".runtime.log.profile");profile.mkdirs();
        Files.writeString(new File(profile,"capture-complete.txt").toPath(),"complete");
        String json="{\"data\":\""+"x".repeat(3*1024*1024)+"\"}";
        Files.writeString(new File(profile,"details.json").toPath(),json);
        File incomplete=new File(logs,latest+".runtime.log.incomplete");incomplete.mkdirs();
        Files.writeString(new File(incomplete,"partial.json").toPath(),"unfinished");
        out=new ByteArrayOutputStream();DebugLogArchive.write(out,data,data,latest);boolean completeJson=false;
        try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry e;while((e=z.getNextEntry())!=null) {
                assert !e.getName().contains("incomplete");
                byte[] bytes=z.readAllBytes();if(e.getName().endsWith("details.json")) {
                    assert bytes.length==json.length();completeJson=true;
                }
            }
        }
        assert completeJson;
        File unrelated=new File(logs,"keep-me.txt");Files.writeString(unrelated.toPath(),"keep");
        File outside=new File(data,"outside.txt");Files.writeString(outside.toPath(),"outside");
        try{Files.createSymbolicLink(new File(logs,"debug-20000103-000000-000.log.link").toPath(),outside.toPath());}catch(UnsupportedOperationException|IOException ignored){}
        DebugLogArchive.clear(data,data);
        assert DebugLogArchive.sessions(data,data).length==0;
        assert unrelated.isFile()&&outside.isFile()&&config.isFile();
        TouchLayoutDocument layout=new TouchLayoutDocument();layout.get("driving/gas").dx=32;layout.get("driving/gas").scale=1.5f;
        layout.get("on_foot/camera").hidden=true;layout.save(data);layout=TouchLayoutDocument.read(data);
        assert layout.get("driving/gas").dx==32&&layout.get("driving/gas").scale==1.5f&&layout.get("on_foot/camera").hidden;

        System.out.println("Debug modes, refresh selection, settings merge, safe save replacement and one-session exports passed");
    }
}

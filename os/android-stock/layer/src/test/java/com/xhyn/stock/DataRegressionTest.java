package com.xhyn.stock;

import static org.junit.Assert.*;
import a.i;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class DataRegressionTest {
    File root;Context context;
    @Before public void setup()throws Exception{root=Files.createTempDirectory("stock-layer-test-").toFile();context=RuntimeEnvironment.getApplication();i.values.clear();i.writes=0;i.failWrites=false;SaveData.deleteTree(StockApi.data(context));StockApi.data(context).mkdirs();}
    @After public void cleanup()throws Exception{SaveData.deleteTree(root);}
    static void put(File root,String path,String text)throws IOException{File f=new File(root,path);f.getParentFile().mkdirs();Files.write(f.toPath(),text.getBytes(StandardCharsets.UTF_8));}
    static String read(Path p)throws IOException{return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
    static byte[] zip(String... names)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write("payload".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return out.toByteArray();}
    interface Action{void run()throws Exception;}
    static void rejects(Action action)throws Exception{try{action.run();fail("Expected rejection");}catch(IOException expected){}}
    @Test public void unsafeDriverArchivesLeaveNoPartialImports()throws Exception{
        File drivers=new File(root,"drivers");
        for(String path:new String[]{"../driver.so","/driver.so","a//driver.so","a/../driver.so","C:/driver.so","bad\n.so"})rejects(()->DriverPackages.stage(drivers,new ByteArrayInputStream(zip(path)),1024));
        rejects(()->DriverPackages.stage(drivers,new ByteArrayInputStream(zip("a/driver.so","b/DRIVER.SO")),1024));
        assertEquals(0,Objects.requireNonNull(drivers.list()).length);
    }
    @Test public void driverReplacementRollsBackAfterSecondMoveFails()throws Exception{
        File drivers=new File(root,"drivers");drivers.mkdirs();File old=DriverPackages.stage(drivers,new ByteArrayInputStream(zip("driver.so")),1024);DriverPackages.activate(drivers,"candidate",old);
        String hash=FilesEx.sha(new File(drivers,"candidate/driver.so"));File replacement=DriverPackages.stage(drivers,new ByteArrayInputStream(zip("new.so")),1024);int[] count={0};
        rejects(()->DriverPackages.activate(drivers,"candidate",replacement,(from,to)->{if(++count[0]==2)throw new IOException("simulated failure");Files.move(from.toPath(),to.toPath());}));
        assertEquals(hash,FilesEx.sha(new File(drivers,"candidate/driver.so")));
    }
    @Test public void nonArm64AndMetadataTraversalDriversAreRejected()throws Exception{
        File folder=new File(root,"bad-driver");put(folder,"driver.so","not an ELF");rejects(()->DriverStore.validate(folder));
        put(folder,"meta.json","{\"libraryName\":\"../outside.so\"}");rejects(()->DriverStore.validate(folder));
    }
    @Test public void saveRestoreRejectsTraversalAndRetainsLiveData()throws Exception{
        File save=new File(root,"save");put(save,"account/live.dat","original");
        for(String name:new String[]{"saves/../../escape","saves/../escape","unknown/file","saves/"+"../".repeat(3)+"escape"})rejects(()->SaveData.importZip(save,new ByteArrayInputStream(zip(name))));
        assertEquals("original",read(new File(save,"account/live.dat").toPath()));assertFalse(new File(root,"escape").exists());
    }
    @Test public void slotDeletionAndRecoveryPreserveOtherSlotsAndAchievements()throws Exception{
        File save=new File(root,"save");String one="0000000000000001/545407F2/00000001/SGTA401",two=one.replace("401","402");
        put(save,one+"/SGTA401","one");put(save,two+"/SGTA402","two");put(save,"achievements/unlocked.json","earned");
        assertEquals(2,SaveData.slots(save).size());SaveData.deleteSlot(save,one);assertFalse(new File(save,one).exists());
        File recovery=SaveData.recovery(save,one);assertEquals(one,SaveData.recoverySlot(recovery));try(InputStream in=new FileInputStream(recovery)){SaveData.importSlot(save,one,in);}
        assertEquals("one",read(new File(save,one+"/SGTA401").toPath()));assertEquals("two",read(new File(save,two+"/SGTA402").toPath()));assertEquals("earned",read(new File(save,"achievements/unlocked.json").toPath()));
    }
    @Test public void fullBackupKeepsAchievementDataIndependent()throws Exception{
        File save=new File(root,"save");put(save,"profile/file","save");put(save,"achievements/unlocked.json","earned");ByteArrayOutputStream output=new ByteArrayOutputStream();SaveData.exportZip(save,output);
        Set<String> paths=new HashSet<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null)paths.add(e.getName());}
        assertTrue(paths.contains("achievements/unlocked.json"));assertFalse(paths.contains("saves/achievements/unlocked.json"));
        File restored=new File(root,"restored");SaveData.importZip(restored,new ByteArrayInputStream(output.toByteArray()));assertEquals("earned",read(new File(restored,"achievements/unlocked.json").toPath()));
    }
    private File game()throws IOException{File game=new File(root,"game");put(game,"default.xex","test executable fixture");put(game,"common/data/handling.dat","original");return game;}
    private List<ModOverlayPolicy.Mod> mods()throws IOException{File mods=new File(root,"mods");put(mods,"A/game/common/data/handling.dat","A");put(mods,"B/game/common/data/handling.dat","B");return ModOverlayPolicy.scan(mods);}
    @Test public void dataModPriorityAndRestoreKeepOriginalBytes()throws Exception{
        File game=game(),store=new File(root,"recovery");List<ModOverlayPolicy.Mod> mods=mods();mods.get(0).priority=20;
        DataMods.apply(game,store,mods);assertEquals("A",read(new File(game,"common/data/handling.dat").toPath()));
        mods.get(0).enabled=false;DataMods.apply(game,store,mods);assertEquals("B",read(new File(game,"common/data/handling.dat").toPath()));
        DataMods.apply(game,store,Collections.emptyList());assertEquals("original",read(new File(game,"common/data/handling.dat").toPath()));assertFalse(new File(store,"transaction").exists());
    }
    @Test public void externalDataEditsAreNeverSilentlyOverwritten()throws Exception{
        File game=game(),store=new File(root,"recovery");DataMods.apply(game,store,mods());put(game,"common/data/handling.dat","external edit");
        rejects(()->DataMods.apply(game,store,Collections.emptyList()));assertEquals("external edit",read(new File(game,"common/data/handling.dat").toPath()));
    }
    @Test public void unsupportedAndMissingDataTargetsAreRejectedBeforeMutation()throws Exception{
        File game=game(),mods=new File(root,"mods");put(mods,"Bad/game/plugin.asi","PC");List<ModOverlayPolicy.Mod> scanned=ModOverlayPolicy.scan(mods);assertFalse(scanned.get(0).admitted());
        List<ModOverlayPolicy.Mod> invalid=scanned;rejects(()->DataMods.plan(game,invalid));put(mods,"Missing/game/common/data/no-such-file.dat","data");scanned=ModOverlayPolicy.scan(mods);List<ModOverlayPolicy.Mod> selected=new ArrayList<>();for(ModOverlayPolicy.Mod m:scanned)if(m.admitted())selected.add(m);
        rejects(()->DataMods.apply(game,new File(root,"recovery"),selected));assertEquals("original",read(new File(game,"common/data/handling.dat").toPath()));
    }
    @Test public void interruptedModDeploymentRestoresThePreviousLiveSnapshot()throws Exception{
        File game=game(),store=new File(root,"recovery"),tx=new File(store,"transaction");put(tx,"live/common/data/handling.dat","previous live");put(game,"common/data/handling.dat","interrupted replacement");
        JSONObject before=new JSONObject().put("version",1).put("files",new JSONObject());
        FilesEx.atomic(new File(tx,"pending.json"),new JSONObject().put("root",game.getCanonicalPath()).put("exeHash",FilesEx.sha(new File(game,"default.xex"))).put("files",new JSONArray().put("common/data/handling.dat")).put("snapshots",new JSONObject().put("common/data/handling.dat",FilesEx.sha(new File(tx,"live/common/data/handling.dat")))).put("before",before).toString());
        DataMods.recover(store);assertEquals("previous live",read(new File(game,"common/data/handling.dat").toPath()));assertFalse(tx.exists());assertEquals(1,DataMods.state(store).getInt("version"));
    }
    @Test public void migrationBacksUpUnknownFlagsAndPreservesExplicitSaveLocation()throws Exception{
        File data=StockApi.data(context);put(data,"args.txt","--gta4_overlay_cpu=true\n--saved_game_root="+new File(root,"my-save").getAbsolutePath()+"\n");put(data,"LibertyRecomp/native.toml","gta4_touch_context_delay_ms = 40\n");
        FirstRunMigration.run(context);assertTrue(new File(StockApi.layer(context),"before-v442/args.txt").isFile());assertFalse(FilesEx.text(new File(data,"args.txt"),4096).contains("gta4_overlay_cpu"));
        assertEquals(new File(root,"my-save").getCanonicalFile(),StockApi.saves(context).getCanonicalFile());
        assertTrue(FilesEx.text(new File(data,"LibertyRecomp/native.toml"),4096).contains("my-save"));assertFalse(FilesEx.text(new File(data,"LibertyRecomp/native.toml"),4096).contains("context_delay"));
        put(data,"args.txt","new stock configuration");FirstRunMigration.run(context);assertEquals("new stock configuration",FilesEx.text(new File(data,"args.txt"),4096));
    }
    @Test public void unsupportedOldNativeSettingsCannotBeSavedThroughAdapter()throws Exception{
        rejects(()->StockApi.saveSettings(context,Map.of("gta4_overlay_cpu","true")));rejects(()->StockApi.saveSettings(context,Map.of("resolution","9999p")));assertEquals(0,i.writes);
        StockApi.saveSettings(context,Map.of("resolution","1080p"));assertEquals("1080p",i.values.get("resolution"));
    }
    @Test public void diagnosticsExcludeGameDataAndRetainTheLogTail()throws Exception{
        File data=StockApi.data(context);put(data,"LibertyRecomp/game/private.dat","game secret");put(data,"debug_logs/session.log","START\n"+"x".repeat(3*1024*1024)+"\nNATIVE CRASH END");
        ByteArrayOutputStream output=new ByteArrayOutputStream();Diagnostics.write(context,output,"session.log");boolean found=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null){assertFalse(e.getName().contains("private.dat"));String text=new String(FilesEx.read(zip,4L*1024*1024),StandardCharsets.UTF_8);if(e.getName().equals("session/session.log")){assertTrue(text.startsWith("START"));assertTrue(text.endsWith("NATIVE CRASH END"));assertTrue(text.contains("truncated"));found=true;}}}assertTrue(found);
    }
    @Test public void preferencesMergeAcrossIndependentReadModifyWrites()throws Exception{
        LayerPreferences.set(context,"x",1);LayerPreferences.set(context,"cpu",false);assertEquals(1,LayerPreferences.read(context).getInt("x"));assertFalse(LayerPreferences.read(context).getBoolean("cpu"));
    }
    @Test public void queuedSettingsSurviveAnEngineRewriteAndActivateOnlyAtNextLaunch()throws Exception {
        NextSettings.save(context,Map.of("resolution","1080p"));i.values.put("resolution","720p");assertEquals("1080p",NextSettings.current(context).get("resolution"));assertEquals(0,i.writes);
        NextSettings.activate(context);assertEquals("1080p",i.values.get("resolution"));assertFalse(NextSettings.file(context).exists());
    }
    @Test public void saveToolsRejectAnAncestorContainingGameData()throws Exception {
        put(StockApi.data(context),"args.txt","--saved_game_root="+StockApi.data(context).getAbsolutePath()+"\n");rejects(()->StockApi.saves(context));
    }
    @Test public void saveResolutionMatchesStockArgumentsRatherThanAnIgnoredTomlPath()throws Exception {
        File chosen=new File(root,"chosen-save");put(StockApi.data(context),"args.txt","# custom saves\r\n--saved_game_root="+chosen+"\r\n");i.values.put("saved_game_root",new File(root,"ignored-toml-path").toString());
        assertEquals(chosen.getCanonicalFile(),StockApi.saves(context).getCanonicalFile());
    }
    @Test public void stockProfileContinuousDefaultsRemainValidAfterReset()throws Exception {
        Map<String,String> values=StockDefaults.read(context);assertTrue(SettingSchema.find("gta4_cas_sharpness").accepts(values.get("gta4_cas_sharpness")));assertTrue(SettingSchema.find("gta4_traffic_density_scale").accepts(values.get("gta4_traffic_density_scale")));
    }
    @Test public void rendererChoiceStaysQueuedUntilStockLaunchPreparation()throws Exception {
        NextSettings.save(context,Map.of(LaunchPreferences.RENDERER,"native"));assertEquals("auto",LaunchPreferences.current(context).get(LaunchPreferences.RENDERER));
        NextSettings.prepareLaunch(context);assertEquals("native",context.getSharedPreferences("launch_renderer",0).getString("mode",null));NextSettings.activate(context);assertEquals(0,i.writes);assertFalse(NextSettings.file(context).exists());
    }
    @Test public void failedActivationRetainsQueuedSettings()throws Exception {
        NextSettings.save(context,Map.of("resolution","1080p"));i.failWrites=true;
        try{rejects(()->NextSettings.activate(context));assertEquals("1080p",NextSettings.pending(context).get("resolution"));}finally{i.failWrites=false;}
    }
    @Test public void damagedModRecoveryIsRejectedBeforeOverwritingLiveFiles()throws Exception {
        File game=game(),store=new File(root,"recovery"),tx=new File(store,"transaction");put(tx,"live/common/data/handling.dat","damaged snapshot");
        JSONObject pending=new JSONObject().put("root",game.getCanonicalPath()).put("exeHash",FilesEx.sha(new File(game,"default.xex"))).put("files",new JSONArray().put("common/data/handling.dat")).put("snapshots",new JSONObject().put("common/data/handling.dat","invalid hash")).put("before",new JSONObject());
        FilesEx.atomic(new File(tx,"pending.json"),pending.toString());rejects(()->DataMods.recover(store));assertEquals("original",read(new File(game,"common/data/handling.dat").toPath()));assertTrue(tx.isDirectory());
    }
    @Test public void fullImportSnapshotRestoresDataAfterAnInterruptedDirectorySwap()throws Exception {
        File save=new File(root,"save"),store=new File(root,"save-recovery");put(save,"profile/data","before import");store.mkdirs();
        String name="full-"+UUID.randomUUID()+".zip";File snapshot=new File(store,name);try(OutputStream out=new FileOutputStream(snapshot)){SaveData.exportZip(save,out);}
        FilesEx.atomic(new File(store,"pending.json"),new JSONObject().put("version",1).put("root",save.getCanonicalPath()).put("existed",true).put("zip",name).put("sha256",FilesEx.sha(snapshot)).toString());
        SaveData.deleteTree(save);SaveRecovery.recover(store);assertEquals("before import",read(new File(save,"profile/data").toPath()));assertFalse(new File(store,"pending.json").exists());
    }
    @Test public void emptySaveFolderCanAcceptItsFirstBackupRestore()throws Exception {
        File save=new File(root,"empty"),source=new File(root,"source"),store=new File(root,"recovery");save.mkdirs();put(source,"profile/data","restored");
        ByteArrayOutputStream zip=new ByteArrayOutputStream();SaveData.exportZip(source,zip);SaveRecovery.importZip(save,store,new ByteArrayInputStream(zip.toByteArray()));assertEquals("restored",read(new File(save,"profile/data").toPath()));
    }
    @Test public void invalidFullImportRestoresItsVerifiedOriginalSnapshot()throws Exception {
        File save=new File(root,"save"),store=new File(root,"recovery");put(save,"profile/data","original");
        rejects(()->SaveRecovery.importZip(save,store,new ByteArrayInputStream(zip("saves/../../outside"))));assertEquals("original",read(new File(save,"profile/data").toPath()));assertFalse(new File(store,"pending.json").exists());
    }
    @Test public void logSessionsUseModificationTimeAndExportsIncludeNativeRuntimeLogs()throws Exception {
        File data=StockApi.data(context);put(data,"debug_logs/zzz-old.log","old");put(data,"debug_logs/aaa-new.log","new");new File(data,"debug_logs/zzz-old.log").setLastModified(1000);new File(data,"debug_logs/aaa-new.log").setLastModified(2000);
        assertEquals("aaa-new.log",Diagnostics.sessions(context).get(0));put(data,"LibertyRecomp/logs/native.log","native runtime evidence");ByteArrayOutputStream output=new ByteArrayOutputStream();Diagnostics.write(context,output,"");boolean found=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().equals("runtime-recent/LibertyRecomp-logs/native.log")){found=true;assertEquals("native runtime evidence",new String(FilesEx.read(zip,1024),StandardCharsets.UTF_8));}}assertTrue(found);
    }
}

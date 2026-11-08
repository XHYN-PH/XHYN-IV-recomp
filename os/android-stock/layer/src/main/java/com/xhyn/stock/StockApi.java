package com.xhyn.stock;

import android.app.*;
import android.content.Context;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Adapter for one pinned DEX. Never substitutes a native library or a missing JNI method. */
final class StockApi {
    static final String LAUNCHER = "com.libertyrecomp.LauncherActivity";
    static final String GAME = "com.libertyrecomp.LibertyActivity";
    static File data(Context c) { File f = c.getExternalFilesDir(null); return f == null ? c.getFilesDir() : f; }
    static File layer(Context c) { return new File(data(c), "xhyn-layer"); }
    static Object call(String owner, String method, Class<?>[] types, Object... args) throws IOException {
        try {
            Method m = Class.forName(owner).getDeclaredMethod(method, types); m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
            throw new IOException("Stock interface " + method + ": " + FilesEx.error(e), e);
        }
    }
    @SuppressWarnings("unchecked")
    static Map<String,String> settings(Context c) throws IOException {
        return new LinkedHashMap<>((Map<String,String>) call("a.i", "E", new Class[]{Context.class, File.class}, c, data(c)));
    }
    static void saveSettings(Context c, Map<String,String> changes) throws IOException {
        for (Map.Entry<String,String> entry : changes.entrySet()) {
            SettingSchema.Entry definition = SettingSchema.find(entry.getKey());
            if (definition == null || !definition.accepts(entry.getValue())) throw new IOException("Unsupported setting: " + entry.getKey());
        }
        call("a.i", "J", new Class[]{File.class, Map.class}, data(c), changes);
    }
    static File game(Context c) throws IOException {
        return ((File) call("a.i", "q", new Class[]{Context.class, File.class}, c, data(c))).getCanonicalFile();
    }
    static ArrayList<String> arguments(Context c) throws IOException {
        File file=new File(data(c),"args.txt");if(file.exists()){FilesEx.regular(file);if(file.length()>262144)throw new IOException("The launch arguments file exceeds 256 KiB.");}
        return new ArrayList<>(Arrays.asList((String[])call(GAME,"d",new Class[]{File.class},new File(data(c),"args.txt"))));
    }
    static File saves(Context c) throws IOException {
        ArrayList<String> args = arguments(c);
        // Match stock startup exactly: it resolves portable saves from its parsed args,
        // then supplies --saved_game_root to the engine. TOML alone does not select this path.
        File game=game(c),root=((File)call("a.i", "m", new Class[]{File.class, ArrayList.class},game,args)).getCanonicalFile();
        for(File protectedFolder:new File[]{game,data(c).getCanonicalFile(),c.getFilesDir().getCanonicalFile()})
            if(protectedFolder.toPath().startsWith(root.toPath()))throw new IOException("The save path contains game or app data. Select a dedicated save folder before using backup tools.");
        if(root.exists()&&!root.isDirectory())throw new IOException("The configured save destination is not a folder.");
        return root;
    }
    static void launch(Activity a) throws IOException {
        if (!a.getClass().getName().equals(LAUNCHER)) throw new IOException("Open Play from the home screen.");
        if(SaveRecovery.pending(a))throw new IOException("An interrupted save restore needs recovery. Open Saves before playing.");
        if(new File(DataMods.store(a),"transaction").exists())throw new IOException("A data-mod transaction needs recovery. Open Data mods before playing.");
        NextSettings.prepareLaunch(a);
        try { a.getClass().getMethod("o").invoke(a); }
        catch (ReflectiveOperationException e) { throw new IOException(FilesEx.error(e), e); }
    }
    static boolean playing(Context c) {
        ActivityManager manager = (ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager == null ? null : manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes)
            if ((c.getPackageName() + ":game").equals(p.processName)) return true;
        return false;
    }
    static void offline(Context c) throws IOException {
        if (playing(c)) throw new IOException("Exit the game before changing these files.");
    }
    static void openTouch(Context c) throws IOException {
        if (!Boolean.TRUE.equals(call("com.libertyrecomp.Installer", "c", new Class[]{Context.class}, c)))
            throw new IOException("The stock touch editor could not be loaded.");
        touch("nativeOpen", new Class[]{String.class}, new File(data(c), "LibertyRecomp/touch-controls.ini").getAbsolutePath());
    }
    static Object touch(String name, Class<?>[] types, Object... args) throws IOException {
        return call("com.libertyrecomp.TouchSettings", name, types, args);
    }
    static String touchGet(String key) throws IOException { return (String)touch("nativeGet", new Class[]{String.class}, key); }
    static void touchSet(String key, String value) throws IOException {
        if (!Boolean.TRUE.equals(touch("nativeSet", new Class[]{String.class, String.class}, key, value)))
            throw new IOException("The stock editor rejected " + key + ".");
    }
    private StockApi() {}
}

package com.libertyrecomp;

import android.content.Context;
import android.os.Process;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Chooses a compatible Vulkan driver once per game process before SDL loads the engine.
 * Automatic uses bundled Turnip for supported Adreno GPUs and the system driver otherwise.
 * Optional PanVK and RADV packages are admitted by GPU/API checks; actual Vulkan features
 * and driver identity are verified by the native bridge. See native/PROVENANCE.md.
 */
final class DriverBridge {

    static final String AUTO = "auto";
    static final String TURNIP = "turnip";
    static final String SYSTEM = "system";
    /** "custom:NAME" loads an AdrenoTools package unpacked in drivers/NAME next to driver.txt. */
    static final String CUSTOM_PREFIX = "custom:";

    private static final String TAG = "LibertyDriver";
    private static final String META="meta.json";

    private static boolean sInitialized;
    /** What the first initialization returned; the proxy is set up once per process. */
    private static String sStatus;

    private DriverBridge() {}

    static native String nativeInit(String nativeLibraryDir, String internalDriverDir,
                                    String driverFilename, boolean custom,
                                    boolean allowSystemFallback);

    /** VK_API_VERSION_1_2: the native renderer's minimum. */
    private static final long VULKAN_1_2 = (1L << 22) | (2L << 12);

    /** Loads and initializes the proxy. Returns the native status JSON. */
    static synchronized String initialize(Context context, String mode, String renderer) {
        if (sInitialized) {
            return sStatus;
        }
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;
        boolean unknownKgsl=(renderer==null || renderer.isEmpty()) &&
                (new File("/dev/kgsl-3d0").exists() || new File("/sys/class/kgsl").exists());
        if(AUTO.equals(mode) || mode==null) mode=unknownKgsl ? TURNIP : DriverPolicy.automatic(renderer,Build.VERSION.SDK_INT);
        boolean custom=false;String library="";
        File driverDir=new File(context.getFilesDir(),"drivers");
        try {
            if(mode.startsWith(CUSTOM_PREFIX)) {
                driverDir=prepareCustom(context,mode.substring(CUSTOM_PREFIX.length()).trim());
                library=customLibrary(driverDir);custom=true;
            } else if(BundledDrivers.ALL.containsKey(mode)) {
                BundledDrivers.Bundle bundle=BundledDrivers.ALL.get(mode);
                if(!DriverPolicy.supports(mode,renderer,Build.VERSION.SDK_INT) && !(TURNIP.equals(mode)&&unknownKgsl))
                    throw new IOException(bundle.label+" is not supported on this GPU / Android version");
                driverDir=prepareBundle(context,bundle);library=bundle.library;custom=true;
            }
        } catch(IOException | RuntimeException error) {
            Log.e(TAG,"Selected driver unavailable; using the system driver",error);
            custom=false;library="";
        }
        if (!driverDir.isDirectory() && !driverDir.mkdirs()) {
            Log.w(TAG, "cannot create " + driverDir);
        }
        System.load(new File(nativeDir, "libvulkan.so").getAbsolutePath());
        // A custom driver that cannot run this GPU falls back to the system
        // driver; an explicit "system" choice has nothing to fall back to.
        String status = nativeInit(nativeDir, driverDir.getAbsolutePath(),
                custom ? library : "", custom, custom);
        sInitialized = true;
        sStatus = status;
        boolean ok = false;
        try {
            ok = new JSONObject(status).optBoolean("ok", false);
        } catch (JSONException ignored) {
        }
        if (ok) {
            Log.i(TAG, "driver ready: " + status);
        } else {
            Log.e(TAG, "driver failed: " + status);
        }
        return status;
    }

    /**
     * Null when the initialized driver can run the game, otherwise a message
     * for the player saying what is missing.
     */
    static String unusableReason(String status) {
        JSONObject json;
        try {
            json = new JSONObject(status);
        } catch (JSONException e) {
            return "The Vulkan driver could not be initialized (unreadable status).";
        }
        if (!json.optBoolean("ok", false)) {
            return "No usable Vulkan driver was found on this device.\n\n"
                    + json.optString("error", "unknown error");
        }
        org.json.JSONArray devices = json.optJSONArray("devices");
        String missing="No compatible GPU was reported.";
        for(int i=0;devices!=null && i<devices.length();++i) {
            JSONObject device=devices.optJSONObject(i);if(device==null) continue;
            if(device.optLong("api_version",0)>=VULKAN_1_2 && device.optBoolean("required_features",false)) return null;
            missing=device.optString("name","GPU")+": "+device.optString("missing_features","Vulkan 1.2 and required shader features");
        }
        return "This driver cannot run the game.\n\n"+missing+"\n\nChoose a compatible driver in the launcher.";
    }

    /** Copies an unpacked package from external storage into exec-capable private storage. */
    private static File prepareCustom(Context context, String name) throws IOException {
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IOException("bad custom driver name: " + name);
        }
        File external=context.getExternalFilesDir(null);
        File source=new File(external!=null ? external : context.getFilesDir(),"drivers/"+name);
        File[] files = source.listFiles();
        if (files == null || files.length == 0) {
            throw new IOException("no custom driver files in " + source);
        }
        File destination = new File(new File(context.getFilesDir(), "drivers"), "custom-" + name);
        deleteRecursively(destination);
        if (!destination.mkdirs()) {
            throw new IOException("cannot create " + destination);
        }
        for (File file : files) {
            if (!file.isFile()) continue;
            try (InputStream in = new java.io.FileInputStream(file);
                 OutputStream out = new FileOutputStream(new File(destination, file.getName()))) {
                copy(in, out);
            }
        }
        return destination;
    }

    /** meta.json libraryName, else the only .so in the package. */
    private static String customLibrary(File dir) throws IOException {
        File meta = new File(dir, META);
        if (meta.isFile()) {
            try (InputStream in = new java.io.FileInputStream(meta)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                copy(in, bytes);
                JSONObject metadata=new JSONObject(bytes.toString("UTF-8"));
                if(metadata.optInt("minApi",29)>Build.VERSION.SDK_INT) throw new IOException("Driver requires a newer Android version");
                String name=metadata.optString("libraryName","");
                if (!name.isEmpty() && !name.contains("/") && !name.contains("\\") && !name.contains("..") && new File(dir,name).isFile()) return name;
            } catch (JSONException ignored) {
            }
        }
        File[] libraries = dir.listFiles((d, n) -> n.endsWith(".so"));
        if (libraries == null || libraries.length != 1) {
            throw new IOException("cannot pick the driver library in " + dir);
        }
        return libraries[0].getName();
    }

    /** Verify all archived bytes before activating a private executable package. */
    private static File prepareBundle(Context context,BundledDrivers.Bundle bundle) throws IOException {
        File parent=new File(context.getFilesDir(),"drivers");
        File destination=new File(parent,bundle.mode+"-"+bundle.zipHash.substring(0,12));
        boolean ready=destination.isDirectory();
        for(java.util.Map.Entry<String,String> file:bundle.files.entrySet()) ready &= valid(new File(destination,file.getKey()),file.getValue());
        if(ready) return destination;
        if(!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create "+parent);
        File archive=File.createTempFile("driver-",".zip",context.getCacheDir());
        File staging=new File(parent,"."+bundle.mode+"-staging-"+Process.myPid());
        try {
            try(InputStream in=context.getAssets().open(bundle.asset);OutputStream out=new FileOutputStream(archive)) { copy(in,out); }
            if(!bundle.zipHash.equals(digest(archive))) throw new IOException("Driver archive integrity check failed");
            deleteRecursively(staging);if(!staging.mkdirs()) throw new IOException("Cannot create "+staging);
            try(ZipFile zip=new ZipFile(archive)) {
                Set<String> names=new HashSet<>();
                for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();)
                    if(!names.add(e.nextElement().getName())) throw new IOException("Duplicate archive entry");
                if(!names.equals(bundle.files.keySet())) throw new IOException("Unexpected driver archive members");
                for(java.util.Map.Entry<String,String> file:bundle.files.entrySet())
                    extract(zip,file.getKey(),new File(staging,file.getKey()),file.getValue());
            }
            deleteRecursively(destination);
            if(!staging.renameTo(destination)) throw new IOException("Cannot activate "+destination);
            return destination;
        } finally { if(!archive.delete()) archive.deleteOnExit();deleteRecursively(staging); }
    }

    private static void extract(ZipFile zip, String name, File target, String hash)
            throws IOException {
        try (InputStream in = zip.getInputStream(zip.getEntry(name));
             OutputStream out = new FileOutputStream(target)) {
            copy(in, out);
        }
        if (!valid(target, hash)) {
            throw new IOException(name + " failed its integrity check");
        }
        if (!target.setReadOnly()) {
            throw new IOException("cannot protect " + target);
        }
    }

    private static boolean valid(File file, String hash) {
        try {
            return file.isFile() && hash.equals(digest(file));
        } catch (IOException e) {
            return false;
        }
    }

    private static String digest(File file) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                sha.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists()) {
            file.setWritable(true);
            file.delete();
        }
    }
}

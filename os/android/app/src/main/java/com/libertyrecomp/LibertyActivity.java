package com.libertyrecomp;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Hosts the Graine RexGlue runtime.
 *
 * <p>SDL owns the window, the event loop and the native thread. Before SDL
 * loads libmain.so this class only prepares what the runtime reads from the
 * environment:</p>
 * <ul>
 *   <li>{@code XDG_DATA_HOME} - the app's external files directory. The title
 *       keeps everything under {@code LibertyRecomp/} there, so the game is
 *       pushed to {@code /sdcard/Android/data/com.libertyrecomp/files/LibertyRecomp/game}
 *       without any storage permission.</li>
 *   <li>{@code REX_RESOURCES_DIR} - fonts, button prompts and the RPF key,
 *       copied out of the APK on the first launch of each install.</li>
 *   <li>{@code args.txt} next to {@code LibertyRecomp/} - one cvar per line,
 *       passed to SDL_main, so settings change without a rebuild.</li>
 *   <li>{@code driver.txt} beside it - "system" to bypass the bundled Turnip
 *       Vulkan driver.</li>
 * </ul>
 */
public class LibertyActivity extends SDLActivity {

    private static final String TAG = "LibertyRecomp";
    private static final String RESOURCES = "Resources";
    private static final String RESOURCES_STAMP = ".stamp";
    private static final String DEFAULT_ARGS = "default_args.txt";

    private String[] mArguments = new String[0];
    /** "--android_surface=WxH" in args.txt: fixed swapchain size, scaled to the panel by the display hardware. */
    private static final String SURFACE_ARGUMENT = "--android_surface=";
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private String mDriverMode = DriverBridge.AUTO;
    /** driver.txt as found, for the launch report. */
    private String mDriverText;
    private File mDataRoot;
    private static final String LAUNCH_REPORT = "last_launch.txt";
    /** Intent extra from the launcher's "Play (Debug Log)". */
    static final String EXTRA_DEBUG_LOG = "debug_log";
    static final String EXTRA_INPUT_TRACE="input_trace", EXTRA_BENCHMARK="benchmark",EXTRA_RENDERER_CAPTURE="renderer_capture";
    /** Launcher "Performance test settings": no sun shadow map. */
    static final String EXTRA_SHADOWS_OFF = "perf_shadows_off";
    /** Launcher "Performance test settings": fixed 0.5 draw distance. */
    static final String EXTRA_HALF_DRAW_DISTANCE = "perf_half_draw_distance";
    /** Launcher "Performance test settings": GPU held at its highest clock. */
    static final String EXTRA_GPU_TURBO = "perf_gpu_turbo";
    /** Launcher "Performance test settings": Android's sustained performance mode. */
    static final String EXTRA_SUSTAINED = "perf_sustained";
    /** Launcher "Performance test settings": draw distance follows thermal headroom. */
    static final String EXTRA_THERMAL_GUARD = "perf_thermal_guard";
    /** Launcher "Performance test settings": the port's shortcuts off (gta4_hack_registry.h). */
    static final String EXTRA_SAFE_MODE = "perf_safe_mode";
    /** Launcher "Performance test settings": reflection intervals in frames. */
    static final String EXTRA_WATER_REFLECTION_INTERVAL = "perf_water_reflection_interval";
    static final String EXTRA_OTHER_REFLECTION_INTERVAL = "perf_other_reflection_interval";
    static final String EXTRA_GPU_RENDERER="gpu_renderer",EXTRA_TOUCH_CAMERA="touch_camera";
    static final String EXTRA_TOUCH_SCALE="touch_scale",EXTRA_TOUCH_OPACITY="touch_opacity";
    static final String EXTRA_TOUCH_SENSITIVITY="touch_sensitivity",EXTRA_TURNIP_SYSMEM="turnip_sysmem";
    private boolean mDebugLog;
    private File mDebugLogFile;
    private boolean mXboxTouch;
    private static native void nativeSetTouchLayout(boolean xbox);
    private static native String nativeGetSetting(String key);
    private static native boolean nativeApplySettings(String[] keys,String[] values);
    private static native void nativeHostMenu(boolean open);
    private static native void nativeEditLayout(int context);
    private static native void nativeFinishTouchEditor();
    private static native void nativeReloadTouchLayout();
    private static native int nativeTouchEditorContext();
    private static final int REQUEST_TOUCH_EDITOR=719;
    private android.app.AlertDialog mGameMenu;
    private android.widget.ImageButton mControllerButton,mMenuButton;
    private boolean mKeepMenuPaused;
    private volatile PhoneRumble mPhoneRumble;
    private boolean mHostMenuOpen,mForeground,mWindowFocused,mNativeTouchEditor;
    private PerformanceSampler mPerformanceSampler;
    private final MobileSettingsUi.Runtime mRuntimeSettings=new MobileSettingsUi.Runtime() {
        public String get(String key){return nativeGetSetting(key);}
        public boolean apply(String[] keys,String[] values){
            boolean ok=nativeApplySettings(keys,values);
            if(ok){updateOverlayOpacity();applyDisplayRate();updateRumbleStrength();}return ok;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPhoneRumble=new PhoneRumble(this);
        prepareEnvironment();
        super.onCreate(savedInstanceState);
        if(!mBrokenLibraries && mLayout!=null) addTouchLayoutSwitch();
        if(!mBrokenLibraries)updateRumbleStrength();
        // A controller-driven game sends no touch events for minutes at a time.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(mDebugLog){mPerformanceSampler=new PerformanceSampler(this);mPerformanceSampler.start();}
        // Steady clocks the device can hold, instead of peaks followed by
        // throttling (only where the device supports it).
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra(EXTRA_SUSTAINED, false)) {
            android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            boolean supported = power != null && power.isSustainedPerformanceModeSupported();
            if (supported) getWindow().setSustainedPerformanceMode(true);
            android.util.Log.w("LibertyRecomp", "sustained performance mode: "
                    + (supported ? "on" : "not supported by this device"));
        }
    }

    private android.widget.ImageButton overlayButton(boolean controller,String description,int right) {
        android.widget.ImageButton button=new android.widget.ImageButton(this);
        button.setImageDrawable(new GameButtonIcon(controller));button.setContentDescription(description);
        button.setFocusable(false);float d=getResources().getDisplayMetrics().density;
        button.setPadding((int)(7*d),(int)(7*d),(int)(7*d),(int)(7*d));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);background.setColor(0x90202020);
        button.setBackground(background);
        android.widget.RelativeLayout.LayoutParams params=new android.widget.RelativeLayout.LayoutParams((int)(38*d),(int)(38*d));
        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        params.setMargins(0,(int)(10*d),(int)(right*d),0);mLayout.addView(button,params);return button;
    }
    private void addTouchLayoutSwitch() {
        mControllerButton=overlayButton(true,"Switch mobile / Xbox controls",58);
        mControllerButton.setOnClickListener(view->{
            mXboxTouch=!mXboxTouch;nativeSetTouchLayout(mXboxTouch);
            try{TouchLayoutSettings.save(mDataRoot,mXboxTouch);}catch(IOException error){Log.w(TAG,"Saving controller layout failed",error);}
            android.widget.Toast.makeText(this,mXboxTouch?"Xbox controls":"Mobile controls",android.widget.Toast.LENGTH_SHORT).show();
            hideSystemBars();
        });
        mMenuButton=overlayButton(false,"Game menu",10);mMenuButton.setOnClickListener(view->showGameMenu());
        updateOverlayOpacity();
        if(mSurface!=null)mSurface.getHolder().addCallback(new android.view.SurfaceHolder.Callback(){
            public void surfaceCreated(android.view.SurfaceHolder holder){applyDisplayRate();}
            public void surfaceChanged(android.view.SurfaceHolder holder,int format,int width,int height){applyDisplayRate();}
            public void surfaceDestroyed(android.view.SurfaceHolder holder){}
        });
        mMenuButton.postDelayed(this::applyDisplayRate,500);
    }
    private void updateOverlayOpacity() {
        float alpha=1;
        try{alpha=Math.max(0.3f,Math.min(1f,Float.parseFloat(nativeGetSetting("gta4_touch_gamepad_opacity"))));}catch(Exception ignored){}
        if(mControllerButton!=null)mControllerButton.setAlpha(alpha);if(mMenuButton!=null)mMenuButton.setAlpha(alpha);
    }
    // Native callbacks may hold the CVar registry lock. Always enqueue this
    // work, even when called from Android's UI thread, before querying settings.
    public void onNativeFrameLimitChanged(int cap) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            if(!isFinishing())DisplayRefresh.apply(this,mSurface==null?null:mSurface.getHolder().getSurface(),cap);
        });
    }
    private void applyDisplayRate() {
        float cap=30;
        try{cap=Float.parseFloat(nativeGetSetting("gta4_frame_limit"));}catch(Exception ignored){}
        DisplayRefresh.apply(this,mSurface==null?null:mSurface.getHolder().getSurface(),cap);
    }
    private void updateRumbleStrength(){
        if(mPhoneRumble==null)return;
        try{mPhoneRumble.strength(Float.parseFloat(nativeGetSetting("gta4_phone_rumble_strength")));}catch(RuntimeException ignored){}
    }
    public boolean isPhoneRumbleAvailable(){return mPhoneRumble!=null&&mPhoneRumble.available();}
    public boolean onNativePhoneRumble(int low,int high,float strength){return mPhoneRumble!=null&&mPhoneRumble.motors(low,high,strength);}
    private void setHostMenu(boolean open){
        mHostMenuOpen=open;
        if(mPhoneRumble!=null)mPhoneRumble.active(mForeground&&mWindowFocused&&!open);
        nativeHostMenu(open);
    }
    @Override protected void onResume(){super.onResume();mForeground=true;if(mPhoneRumble!=null)mPhoneRumble.active(mWindowFocused&&!mHostMenuOpen);}
    @Override protected void onPause(){mForeground=false;if(mPhoneRumble!=null)mPhoneRumble.active(false);super.onPause();}
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if(mBrokenLibraries){super.onBackPressed();return;}
        if(mGameMenu!=null&&mGameMenu.isShowing())mGameMenu.dismiss();else showGameMenu();
    }
    private void showGameMenu() {
        if(mNativeTouchEditor){nativeFinishTouchEditor();return;}
        if(mGameMenu!=null&&mGameMenu.isShowing())return;
        setHostMenu(true);mKeepMenuPaused=false;
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.addView(ModernUi.text(this,"Paused",14,ModernUi.MUTED));
        ModernUi.action(this,panel,"Edit controls",()->{mKeepMenuPaused=true;mGameMenu.dismiss();editCurrentLayout(mXboxTouch);});
        ModernUi.action(this,panel,"Settings",()->{mKeepMenuPaused=true;mGameMenu.dismiss();showGameSettings();});
        ModernUi.action(this,panel,"Quit to launcher",()->{mKeepMenuPaused=true;mGameMenu.dismiss();confirmQuit();});
        mGameMenu=ModernUi.dialog(this,"XHYN IV",panel,"Resume",null,null);
        mGameMenu.setOnDismissListener(dialog->{if(!mKeepMenuPaused)setHostMenu(false);hideSystemBars();});mGameMenu.show();
    }
    private void showGameSettings() {
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.app.AlertDialog hub=ModernUi.dialog(this,"Settings",panel);final boolean[] leaving={false};
        ModernUi.action(this,panel,"Graphics & performance",()->{leaving[0]=true;hub.dismiss();showChildSettings(false);});
        ModernUi.action(this,panel,"Controls",()->{leaving[0]=true;hub.dismiss();showChildSettings(true);});
        if(mDebugLog){
            ModernUi.action(this,panel,"Start performance test",()->{hub.dismiss();triggerDiagnostic("gta4_native_benchmark_trigger","30 seconds warm-up, then 60 seconds measured. Follow the same route.");});
            ModernUi.action(this,panel,"Capture shadow issue",()->{
                hub.dismiss();android.widget.Toast.makeText(this,"Shadow capture starts in one second. Keep the issue visible.",android.widget.Toast.LENGTH_LONG).show();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                    if(!isFinishing()&&!isDestroyed())triggerDiagnostic("gta4_shadow_capture_trigger","Recording shadow state for six frames.");
                },1000);
            });
            if(getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_RENDERER_CAPTURE,false))ModernUi.action(this,panel,"Capture renderer timings",()->{hub.dismiss();triggerDiagnostic("gta4_profile_native_capture_trigger","Recording a short renderer capture. It adds overhead.");});
        }
        hub.setOnDismissListener(d->{if(!leaving[0])setHostMenu(false);hideSystemBars();});hub.show();
    }
    private void triggerDiagnostic(String key,String message){
        int trigger=0;try{trigger=Integer.parseInt(nativeGetSetting(key));}catch(Exception ignored){}
        boolean ok=nativeApplySettings(new String[]{key},new String[]{Integer.toString(trigger+1)});
        android.widget.Toast.makeText(this,ok?message:"Capture is unavailable.",android.widget.Toast.LENGTH_LONG).show();
    }
    private void showChildSettings(boolean touch) {
        final boolean[] editing={false};
        android.app.AlertDialog child=touch?MobileSettingsUi.touch(this,mDataRoot,mRuntimeSettings,xbox->{editing[0]=true;editCurrentLayout(xbox);}):MobileSettingsUi.graphics(this,mDataRoot,mRuntimeSettings);
        if(child==null){setHostMenu(false);return;}
        child.setOnDismissListener(d->{if(!editing[0])setHostMenu(false);hideSystemBars();});
    }
    private void editCurrentLayout(boolean xbox) {
        mXboxTouch=xbox;try{TouchLayoutSettings.save(mDataRoot,xbox);}catch(IOException ignored){}
        if(mGameMenu!=null&&mGameMenu.isShowing()){mKeepMenuPaused=true;mGameMenu.dismiss();}
        setHostMenu(true);
        if(!xbox){
            mNativeTouchEditor=true;
            if(mControllerButton!=null)mControllerButton.setEnabled(false);
            nativeEditLayout(nativeTouchEditorContext());hideSystemBars();return;
        }
        nativeSetTouchLayout(true);
        Intent editor=new Intent(this,TouchEditorActivity.class);editor.putExtra("context",nativeTouchEditorContext());editor.putExtra("xbox",xbox);
        startActivityForResult(editor,REQUEST_TOUCH_EDITOR);
    }
    public void onNativeTouchEditorRequested(){
        // The native callback can hold the overlay lock on the UI thread.
        // Queue the editor before asking native code for its current context.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!isFinishing()&&!isDestroyed())editCurrentLayout(false);});
    }
    public void onNativeTouchEditorClosed(){
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            if(isFinishing()||isDestroyed()||!mNativeTouchEditor)return;
            mNativeTouchEditor=false;if(mControllerButton!=null)mControllerButton.setEnabled(true);
            showGameMenu();
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_TOUCH_EDITOR){if(result==RESULT_OK)nativeReloadTouchLayout();showGameMenu();hideSystemBars();}
    }
    private void confirmQuit() {
        new android.app.AlertDialog.Builder(this).setTitle("Quit to launcher?")
            .setMessage("Unsaved game progress will be lost.").setNegativeButton("Keep playing",(d,w)->setHostMenu(false))
            .setOnCancelListener(d->setHostMenu(false)).setPositiveButton("Quit",(d,w)->{
                DebugLog.note("Player quit to launcher");DebugLog.stop();
                Intent launcher=new Intent(this,LauncherActivity.class);launcher.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launcher);finish();
                // SDL's normal teardown requests native shutdown. Close this
                // separate game process after giving teardown time to finish.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),750);
            }).show();
    }

    @Override protected void onDestroy() {
        if(mPhoneRumble!=null)mPhoneRumble.close();
        if(mPerformanceSampler!=null)mPerformanceSampler.stop();
        try{super.onDestroy();}finally{DebugLog.stop();}
    }

    /**
     * Picks the Vulkan driver before SDL loads libmain.so; see DriverBridge.
     * When no driver can run the game, the error thrown here is shown by
     * SDLActivity in a dialog instead of the game closing without a word.
     */
    @Override
    public void loadLibraries() {
        String status = DriverBridge.initialize(this,mDriverMode,getIntent()==null ? "" : getIntent().getStringExtra(EXTRA_GPU_RENDERER));
        String reason = DriverBridge.unusableReason(status);
        writeLaunchReport(status, reason);
        if (reason != null) {
            throw new UnsatisfiedLinkError(reason
                    + "\n\nGPU support depends on the driver and required Vulkan features."
                    + (mDebugLog?"\nA report was saved to Android/data/"+getPackageName()+"/files/"+LAUNCH_REPORT+".":"\nUse Play with logs in Settings to record a report."));
        }
        super.loadLibraries();
    }

    /**
     * Device, GPU and driver facts of this launch in the data folder, so a
     * player without adb can attach them to a bug report.
     */
    @SuppressWarnings("deprecation")
    private void writeLaunchReport(String driverStatus, String problem) {
        if (!mDebugLog || mDataRoot == null) return;
        StringBuilder report = new StringBuilder();
        String version = "?";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName + " (" + info.versionCode + ")";
        } catch (Exception ignored) {
        }
        report.append("Liberty Recompiled for Android ").append(version).append('\n');
        report.append("time: ").append(new java.util.Date()).append('\n');
        report.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(", board ").append(Build.BOARD)
                .append(", hardware ").append(Build.HARDWARE).append(")\n");
        if (Build.VERSION.SDK_INT >= 31) {
            report.append("soc: ").append(Build.SOC_MANUFACTURER).append(' ')
                    .append(Build.SOC_MODEL).append('\n');
        }
        report.append("android: ").append(Build.VERSION.RELEASE).append(" (API ")
                .append(Build.VERSION.SDK_INT).append(")\n");
        report.append("abis: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append('\n');
        try {
            report.append("page size: ")
                    .append(Os.sysconf(android.system.OsConstants._SC_PAGESIZE)).append('\n');
        } catch (Exception ignored) {
        }
        String kgsl = readFirstLine(new File("/sys/class/kgsl/kgsl-3d0/gpu_model"));
        report.append("kgsl gpu: ").append(kgsl != null ? kgsl : "(not readable or not Adreno)")
                .append('\n');
        report.append("driver.txt: ")
                .append(mDriverText == null ? "(none)" : "\"" + mDriverText + "\"")
                .append(" -> ").append(mDriverMode).append('\n');
        report.append("driver status: ").append(driverStatus).append('\n');
        if (mDebugLog) {
            report.append("debug log: ")
                    .append(mDebugLogFile != null ? mDebugLogFile.getAbsolutePath() : "could not start")
                    .append('\n');
        }
        report.append("result: ").append(problem == null ? "driver usable, starting the game"
                : "cannot start: " + problem.replace('\n', ' ')).append('\n');
        if(mDebugLog) DebugLog.note(report.toString());
        try (OutputStream out = new FileOutputStream(new File(mDataRoot, LAUNCH_REPORT))) {
            out.write(report.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "writing the launch report failed", e);
        }
        if(mDebugLogFile!=null)try {
            Files.write(new File(mDebugLogFile.getParentFile(),mDebugLogFile.getName()+".launch.txt").toPath(),report.toString().getBytes(StandardCharsets.UTF_8));
        }catch(IOException error){Log.w(TAG,"Saving session launch report failed",error);}
        Log.i(TAG, "launch report:\n" + report);
    }

    @Override
    protected SDLSurface createSDLSurface(Context context) {
        SDLSurface surface = super.createSDLSurface(context);
        if (mSurfaceWidth > 0 && mSurfaceHeight > 0) {
            surface.getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
            if(mDebugLog)Log.i(TAG, "fixed surface " + mSurfaceWidth + "x" + mSurfaceHeight);
        }
        return surface;
    }

    @Override
    protected String[] getArguments() {
        return mArguments;
    }

    /**
     * SDL re-requests the orientation when it creates its window. It reads the
     * SDL_ORIENTATIONS hint from application-level SDL_ENV.* metadata only, so
     * the manifest's activity-level entry never reached it: with no hint and a
     * resizable window it asked for FULL_USER, and phones held upright turned
     * the game to portrait (seen on a moto edge 30 fusion and a Samsung).
     * The game is landscape only, in either direction.
     */
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        super.setOrientationBis(w, h, resizable, "LandscapeLeft LandscapeRight");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mWindowFocused=hasFocus;
        if(mPhoneRumble!=null)mPhoneRumble.active(mForeground&&hasFocus&&!mHostMenuOpen);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    // The launcher's performance test settings replace these cvars' lines in
    // args.txt for this run (the file itself is left as it is).
    private void applyPerformanceTestArguments(List<String> arguments) {
        Intent intent=getIntent();if(intent==null)return;
        if(intent.hasExtra(EXTRA_GPU_TURBO)||intent.hasExtra(EXTRA_THERMAL_GUARD))
            PerformanceLaunchOptions.apply(arguments,intent.getBooleanExtra(EXTRA_GPU_TURBO,false),intent.getBooleanExtra(EXTRA_THERMAL_GUARD,false),intent.getBooleanExtra(EXTRA_SAFE_MODE,false));
        // Older development launch intents remain usable, but ordinary launch
        // no longer replaces saved graphics choices with default test values.
        if(intent.getBooleanExtra(EXTRA_SHADOWS_OFF,false))DebugLaunchOptions.set(arguments,"gta4_native_shadow_interval","0");
        if(intent.hasExtra(EXTRA_WATER_REFLECTION_INTERVAL))DebugLaunchOptions.set(arguments,"gta4_native_water_reflection_interval",Integer.toString(Math.max(0,Math.min(3,intent.getIntExtra(EXTRA_WATER_REFLECTION_INTERVAL,1)))));
        if(intent.hasExtra(EXTRA_OTHER_REFLECTION_INTERVAL))DebugLaunchOptions.set(arguments,"gta4_native_environment_reflection_interval",Integer.toString(Math.max(0,Math.min(3,intent.getIntExtra(EXTRA_OTHER_REFLECTION_INTERVAL,1)))));
        if(intent.getBooleanExtra(EXTRA_HALF_DRAW_DISTANCE,false)){DebugLaunchOptions.set(arguments,"gta4_draw_distance_scale","0.5");DebugLaunchOptions.set(arguments,"gta4_dynamic_draw_distance","false");}
    }

    private void prepareEnvironment() {
        File external = getExternalFilesDir(null);
        File dataRoot = external != null ? external : getFilesDir();
        mDataRoot = dataRoot;
        mXboxTouch=TouchLayoutSettings.xbox(dataRoot);
        mDebugLog=getIntent()!=null && getIntent().getBooleanExtra(EXTRA_DEBUG_LOG,false);
        if(mDebugLog) {
            mDebugLogFile=DebugLog.start(dataRoot,getFilesDir());
            DebugLog.note("Device: "+Build.MANUFACTURER+" "+Build.MODEL+"; Android "+Build.VERSION.RELEASE);
            if(mDebugLogFile==null) android.widget.Toast.makeText(this,"Could not create a debug log. Check free storage.",android.widget.Toast.LENGTH_LONG).show();
        }
        if(!mDebugLog)new File(dataRoot,LAUNCH_REPORT).delete();
        File resources = new File(getFilesDir(), RESOURCES);
        try {
            syncResources(this);
        } catch (IOException e) {
            Log.e(TAG, "copying bundled resources failed", e);
        }
        try { syncTouchIcons(this,new File(new File(dataRoot,"LibertyRecomp"),"touch-icons")); }
        catch(IOException error) { Log.w(TAG,"Touch icons unavailable; using vector icons",error); }
        setenv("XDG_DATA_HOME", dataRoot.getAbsolutePath());
        setenv("HOME", dataRoot.getAbsolutePath());
        setenv("REX_RESOURCES_DIR", resources.getAbsolutePath());
        // Preserve upstream's conservative Turnip mode by default on supported
        // Adreno devices. Explicit choices and env.txt keep their precedence.
        boolean defaultSysmem=getIntent()!=null && DriverPolicy.supports("turnip",
            getIntent().getStringExtra(EXTRA_GPU_RENDERER),Build.VERSION.SDK_INT);
        if(getIntent()!=null && getIntent().getBooleanExtra(EXTRA_TURNIP_SYSMEM,defaultSysmem)) setenv("TU_DEBUG","sysmem");
        applyEnvironmentFile(new File(dataRoot, "env.txt"));
        File argsFile = new File(dataRoot, "args.txt");
        if (!argsFile.isFile()) {
            // First launch: start from the tuned handheld profile bundled in
            // the APK; the copy stays editable next to LibertyRecomp/.
            try {
                copyAssetTree(getAssets(), DEFAULT_ARGS, argsFile);
            } catch (IOException e) {
                Log.e(TAG, "writing default args failed", e);
            }
        }
        migrateArguments(argsFile);
        migrateConfig(new File(new File(dataRoot, "LibertyRecomp"), "native.toml"));
        List<String> arguments = new ArrayList<>(Arrays.asList(readArguments(argsFile)));
        applyPerformanceTestArguments(arguments);
        Intent controls=getIntent();
        if(controls!=null && controls.hasExtra(EXTRA_TOUCH_CAMERA)) {
            arguments.add("--gta4_touch_camera="+controls.getStringExtra(EXTRA_TOUCH_CAMERA));
            arguments.add("--gta4_touch_gamepad_scale="+controls.getFloatExtra(EXTRA_TOUCH_SCALE,1f));
            arguments.add("--gta4_touch_gamepad_opacity="+controls.getFloatExtra(EXTRA_TOUCH_OPACITY,1f));
            arguments.add("--gta4_touch_swipe_sensitivity="+controls.getFloatExtra(EXTRA_TOUCH_SENSITIVITY,1f));
        }
        DebugLaunchOptions.apply(arguments,mDebugLog,getIntent()!=null && getIntent().getBooleanExtra(EXTRA_INPUT_TRACE,false),mDebugLogFile);
        if(mDebugLog&&getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_RENDERER_CAPTURE,false))DebugLaunchOptions.rendererCapture(arguments);
        // Saved choices override bundled startup defaults, not deliberate
        // settings from a different Android preferences store.
        try {
            for(java.util.Map.Entry<String,String> entry:MobileConfig.read(MobileConfig.file(dataRoot)).entrySet()) {
                if(!MobileSettingsUi.KEYS.contains(entry.getKey()))continue;
                final String prefix="--"+entry.getKey()+"=";arguments.removeIf(a->a.startsWith(prefix));
                arguments.add(prefix+entry.getValue());
            }
        }catch(IOException failure){Log.w(TAG,"Reading mobile settings failed",failure);}
        arguments.add("--gta4_native_benchmark_trigger=0");
        arguments.add("--gta4_shadow_capture_trigger=0");arguments.add("--gta4_trace_shadow_targets=0");
        // Apply after saved settings so a previous debug session cannot re-arm logging.
        if(!mDebugLog)DebugLaunchOptions.apply(arguments,false,false,null);
        addInstallSources(new File(dataRoot, "install"), arguments);
        for (int i = arguments.size() - 1; i >= 0; --i) {
            String argument = arguments.get(i);
            if (!argument.startsWith(SURFACE_ARGUMENT)) continue;
            arguments.remove(i);  // Java-side setting; the runtime does not know it.
            String[] size = argument.substring(SURFACE_ARGUMENT.length()).split("x");
            try {
                if (size.length == 2) {
                    mSurfaceWidth = Integer.parseInt(size[0].trim());
                    mSurfaceHeight = Integer.parseInt(size[1].trim());
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "bad " + argument);
            }
        }
        arguments.add("--gta4_touch_layout="+(mXboxTouch?"gamepad":"liberty"));
        java.util.LinkedHashMap<String,String> unique=new java.util.LinkedHashMap<>();int positional=0;
        for(String argument:arguments) {
            int equal=argument.indexOf('=');String key=argument.startsWith("--") ?
                (equal>0 ? argument.substring(0,equal) : argument) : "#"+positional++;
            unique.put(key,argument);
        }
        mArguments=unique.values().toArray(new String[0]);
        if(mDebugLog)DebugLog.note("Effective arguments: "+java.util.Arrays.toString(mArguments));
        String driver = readFirstLine(new File(dataRoot, "driver.txt"));
        mDriverText = driver;
        String lower = driver == null ? "" : driver.toLowerCase(Locale.ROOT);
        if (lower.startsWith(DriverBridge.CUSTOM_PREFIX)) {
            mDriverMode = DriverBridge.CUSTOM_PREFIX
                    + driver.substring(DriverBridge.CUSTOM_PREFIX.length()).trim();
        } else if(BundledDrivers.ALL.containsKey(lower) || lower.equals(DriverBridge.AUTO)) {
            mDriverMode=lower;
        } else if (lower.equals(DriverBridge.SYSTEM)) {
            mDriverMode = DriverBridge.SYSTEM;
        } else if (!lower.isEmpty() && !lower.equals(DriverBridge.TURNIP)) {
            Log.w(TAG, "unknown driver.txt value \"" + driver + "\", using automatic selection");
        }
        if(mDebugLog)Log.i(TAG, "data=" + dataRoot + " resources=" + resources
                + " args=" + mArguments.length + " driver=" + mDriverMode);
    }

    /**
     * Optional NAME=value lines (e.g. Turnip's TU_DEBUG) exported before the
     * Vulkan driver loads; '#' starts a comment.
     */
    private static void applyEnvironmentFile(File file) {
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = cleanLine(line);
                int equals = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || equals <= 0) {
                    continue;
                }
                String name = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                setenv(name, value);
                Log.i(TAG, "env " + name + "=" + value);
            }
        } catch (IOException e) {
            Log.e(TAG, "reading " + file + " failed", e);
        }
    }

    /**
     * The SDL file picker returns content:// URIs the installer cannot read, so
     * sources are taken from files/install/ (writable over USB without any
     * storage permission): the first *.iso is the disc, any other file the
     * title update. Explicit --install_* lines in args.txt win.
     */
    private static void addInstallSources(File dir, List<String> arguments) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        for (String argument : arguments) {
            if (argument.startsWith("--install_")) {
                return;
            }
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        File disc = null;
        File update = null;
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith(".")) {
                continue;
            }
            if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".iso")) {
                if (disc == null) disc = file;
            } else if (update == null || file.length() > update.length()) {
                update = file;
            }
        }
        if (disc != null) {
            arguments.add("--install_game_source=" + disc.getAbsolutePath());
            Log.i(TAG, "install disc " + disc);
        }
        if (update != null) {
            arguments.add("--install_update_source=" + update.getAbsolutePath());
            Log.i(TAG, "install title update " + update);
        }
    }

    private static void setenv(String name, String value) {
        try {
            Os.setenv(name, value, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "setenv " + name + " failed", e);
        }
    }

    /**
     * Re-copies the bundled resources into files/Resources whenever the
     * installed APK changes, and returns that folder. Shared with the
     * launcher's installer, which needs the RPF key from it.
     */
    static synchronized File syncResources(Context context) throws IOException {
        File target = new File(context.getFilesDir(), RESOURCES);
        String stamp;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            stamp = info.versionCode + ":" + info.lastUpdateTime;
        } catch (Exception e) {
            stamp = "unknown";
        }
        File stampFile = new File(target, RESOURCES_STAMP);
        if (stamp.equals(readFirstLine(stampFile))) {
            return target;
        }
        deleteRecursively(target);
        copyAssetTree(context.getAssets(), RESOURCES, target);
        try (OutputStream out = new FileOutputStream(stampFile)) {
            out.write(stamp.getBytes(StandardCharsets.UTF_8));
        }
        Log.i(TAG, "bundled resources copied to " + target);
        return target;
    }

    /** Copy the small immutable icon set once per APK update. */
    private static void syncTouchIcons(Context context,File target) throws IOException {
        String stamp;
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            stamp=info.versionCode+":"+info.lastUpdateTime;
        } catch(Exception error) { stamp="unknown"; }
        File marker=new File(target,".version");
        if(marker.isFile() && stamp.equals(new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8))) return;
        copyAssetTree(context.getAssets(),"touch-icons",target);
        Files.write(marker.toPath(),stamp.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyAssetTree(AssetManager assets, String path, File target)
            throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            try (InputStream in = assets.open(path);
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            return;
        }
        if (!target.isDirectory() && !target.mkdirs()) {
            throw new IOException("cannot create " + target);
        }
        for (String child : children) {
            copyAssetTree(assets, path + "/" + child, new File(target, child));
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "cannot delete " + file);
        }
    }

    private static String readFirstLine(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? null : cleanLine(line);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Trims a line of a settings file, including the byte-order mark some
     * editors (Windows Notepad among them) put at the start of a file.
     */
    private static String cleanLine(String line) {
        return line.replace("\uFEFF", "").trim();
    }

    /**
     * Earlier defaults replaced in args.txt files copied by older versions.
     * Only the exact old line is rewritten; edited values are left alone.
     */
    private static final String[][] ARGUMENT_MIGRATIONS = {
            {"--gta4_native_upscaler=sgsr", "--gta4_native_upscaler=native"},
            {"--present_sgsr=true", null},
            {"--present_sgsr=false", null},
            {"--present_sgsr", null},
            // 0.5.7.9: dynamic draw distance floor 0.80 (0.75 broke dynamic
            // shadows; 0.85 was an interim default of test builds).
            {"--gta4_dynamic_draw_distance_min=0.75", "--gta4_dynamic_draw_distance_min=0.80"},
            {"--gta4_dynamic_draw_distance_min=0.85", "--gta4_dynamic_draw_distance_min=0.80"},
            // 0.5.9.1: the in-game Display menu owns these settings (their
            // Android defaults are now built in). Copied default lines
            // outranked its saved choices on every launch; a null target drops
            // the line. Edited values stay.
            {"--gta4_native_upscaler=native", null},
            {"--gta4_native_anti_aliasing=fxaa", null},
            {"--gta4_draw_distance_scale=1.0", null},
            {"--gta4_shadow_map_base_size=256", null},
            {"--gta4_reflection_resolution=original", null},
            {"--gta4_frame_limit=30", null},
            {"--gta4_motion_blur=false", null},
            {"# Native 720p (the console's resolution); the presenter scales it to the panel.", null},
            {"--gta4_output_height_cap=720", null},
            {"# 30 FPS with VSync: 30 divides the 60 Hz panel evenly.", null},
            {"--gta4_present_mode=vsync", null},
            {"--gta4_fsr1_quality=quality", null},
    };

    /**
     * Lines the runtime saved into its config from those old launch defaults
     * (it saves every non-default value); the Display menu never writes them.
     * native.toml's cap kept capping the menu's resolution choice at 720 lines.
     */
    private static final String[] CONFIG_LINE_REMOVALS = {
            "gta4_output_height_cap = 720",
    };

    /**
     * Saved values reset once, on the first launch of 0.5.9.1: until then the
     * launch defaults overrode them, so a heavy choice made in the Display
     * menu never took effect (a 1024 shadow base is a 5120x5120 atlas, ~10
     * FPS on an Adreno 650). Afterwards the menu's choices apply and stay.
     */
    private static final String[] CONFIG_ONE_TIME_RESETS = {
            "gta4_shadow_map_base_size", "gta4_reflection_resolution",
    };
    private static final String CONFIG_RESET_MARKER = ".display_settings_0591";

    private static void migrateConfig(File file) {
        if (!file.isFile()) return;
        try {
            MobileConfig.migrateRemovedUpscaler(file);
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean changed = false;
            for (String removal : CONFIG_LINE_REMOVALS) {
                changed |= lines.removeIf(line -> cleanLine(line).equals(removal));
            }
            File marker = new File(file.getParentFile(), CONFIG_RESET_MARKER);
            if (!marker.exists()) {
                for (String key : CONFIG_ONE_TIME_RESETS) {
                    changed |= lines.removeIf(line -> {
                        String clean = cleanLine(line);
                        return clean.startsWith(key + " =") || clean.startsWith(key + "=");
                    });
                }
                try {
                    marker.createNewFile();
                } catch (IOException e) {
                    Log.w(TAG, "cannot create " + marker, e);
                }
            }
            if (changed) {
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
                Log.i(TAG, "removed old launch defaults from " + file);
            }
        } catch (IOException e) {
            Log.w(TAG, "cannot update " + file, e);
        }
    }

    private static void migrateArguments(File file) {
        if (!file.isFile()) return;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean changed = false;
            for (int i = lines.size() - 1; i >= 0; --i) {
                for (String[] migration : ARGUMENT_MIGRATIONS) {
                    if (cleanLine(lines.get(i)).equals(migration[0])) {
                        if (migration[1] == null) {
                            lines.remove(i);
                        } else {
                            lines.set(i, migration[1]);
                        }
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
                Log.i(TAG, "updated old defaults in " + file);
            }
        } catch (IOException e) {
            Log.w(TAG, "cannot update " + file, e);
        }
    }

    /** One argument per line; blank lines and lines starting with '#' are skipped. */
    private static String[] readArguments(File file) {
        List<String> arguments = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = cleanLine(line);
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        arguments.add(line);
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "cannot read " + file, e);
            }
        }
        return arguments.toArray(new String[0]);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}

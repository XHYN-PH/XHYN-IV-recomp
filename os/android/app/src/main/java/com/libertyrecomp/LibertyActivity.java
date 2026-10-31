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
    private static native boolean nativeQueueCheat(int index);
    private static final int REQUEST_TOUCH_EDITOR=719,REQUEST_REPORT_EXPORT=720;
    private android.app.AlertDialog mGameMenu;
    private android.widget.ImageButton mControllerButton,mMenuButton;
    private boolean mKeepMenuPaused;
    private volatile PhoneRumble mPhoneRumble;
    private boolean mHostMenuOpen,mForeground,mWindowFocused,mNativeTouchEditor;
    private PerformanceSampler mPerformanceSampler;
    private SessionRecorder mSessionRecorder;private long mInputTraceUntil,mInputTraceLast;
    private final Runnable mInputTraceEnd=this::stopInputTrace;
    private static native boolean nativeSetInputTrace(boolean enabled);
    private static native float[] nativeTouchViewport();
    private static native long nativeTouchViewportGeneration();
    private final android.os.Handler mPhoneUi=new android.os.Handler(android.os.Looper.getMainLooper());
    private PreparationUi mPreparation;
    private PhoneGyro mPhoneGyro;private PerformanceHud mPerformanceHud;private boolean mReturnToSettings;
    private final SessionMenuUi.State mSessionState=new SessionMenuUi.State();
    private static native void nativeGyroSample(float x,float y);
    private static native void nativeGyroReset();
    private final MobileSettingsUi.Runtime mRuntimeSettings=new MobileSettingsUi.Runtime() {
        public String get(String key){return nativeGetSetting(key);}
        public boolean apply(String[] keys,String[] values){
            boolean ok=nativeApplySettings(keys,values);
            if(ok){updateOverlayOpacity();applyDisplayRate();updateRumbleStrength();updatePhoneFeatures();if(mDebugLog)recordRuntimeSettings("settings-saved");}return ok;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mPhoneRumble=new PhoneRumble(this);
        prepareEnvironment();
        if(getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_INPUT_TRACE,false))mSessionState.tab="Diagnostics";
        super.onCreate(savedInstanceState);
        if(!mBrokenLibraries && mLayout!=null) addTouchLayoutSwitch();
        if(!mBrokenLibraries&&mSurface!=null)mSurface.setOnCapturedPointerListener((v,event)->forwardMouse(event,true));
        if(!mBrokenLibraries){
            updateRumbleStrength();mPhoneGyro=new PhoneGyro(this,new PhoneGyro.Output(){public void sample(float x,float y){nativeGyroSample(x,y);}public void reset(){nativeGyroReset();}});
            if(mLayout instanceof android.widget.RelativeLayout)mPerformanceHud=new PerformanceHud(this,(android.widget.RelativeLayout)mLayout,mRuntimeSettings);
            mPreparation=new PreparationUi(this,mRuntimeSettings);mPreparation.start();
            mSessionRecorder=new SessionRecorder(this,mDataRoot,mRuntimeSettings,mDebugLogFile);mSessionRecorder.start();
            updatePhoneFeatures();DebugLog.note("phone-gyro: available="+mPhoneGyro.available());
            if(mDebugLog)mPhoneUi.postDelayed(()->recordRuntimeSettings("startup"),2500);
        }
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

    private int mMouseButtons,mMouseTraceEvents;private boolean mRestoreMouseCapture;
    private void releaseMouse(){
        if(mSurface==null)return;
        mRestoreMouseCapture|=mSurface.hasPointerCapture();
        int held=mMouseButtons;for(int bit:new int[]{1,2,4,8,16})if((held&bit)!=0){held&=~bit;org.libsdl.app.SDLActivity.onNativeMouse(held,android.view.MotionEvent.ACTION_UP,0,0,true);}
        mMouseButtons=0;if(mSurface.hasPointerCapture())mSurface.releasePointerCapture();
    }
    private void restoreMouseCapture(){if(mRestoreMouseCapture&&mSurface!=null&&mForeground&&mWindowFocused&&!mHostMenuOpen){mRestoreMouseCapture=false;mSurface.post(()->{if(!isFinishing()&&mForeground&&mWindowFocused&&!mHostMenuOpen)mSurface.requestPointerCapture();});}}
    private boolean forwardMouse(android.view.MotionEvent e,boolean relative){
        if(mHostMenuOpen||!mForeground||!mWindowFocused)return false;
        int action=e.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_SCROLL){org.libsdl.app.SDLActivity.onNativeMouse(0,action,e.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL),e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL),false);return true;}
        if(action==android.view.MotionEvent.ACTION_MOVE||action==android.view.MotionEvent.ACTION_HOVER_MOVE){
            for(int n=0;n<e.getHistorySize();n++)org.libsdl.app.SDLActivity.onNativeMouse(0,android.view.MotionEvent.ACTION_MOVE,e.getHistoricalX(n),e.getHistoricalY(n),relative);
            org.libsdl.app.SDLActivity.onNativeMouse(0,android.view.MotionEvent.ACTION_MOVE,e.getX(),e.getY(),relative);
        }else if(action==android.view.MotionEvent.ACTION_BUTTON_PRESS||action==android.view.MotionEvent.ACTION_BUTTON_RELEASE||action==android.view.MotionEvent.ACTION_DOWN||action==android.view.MotionEvent.ACTION_UP){
            int next=e.getButtonState(),changed=mMouseButtons^next;
            for(int bit:new int[]{1,2,4,8,16})if((changed&bit)!=0){mMouseButtons=(mMouseButtons&~bit)|(next&bit);org.libsdl.app.SDLActivity.onNativeMouse(mMouseButtons,(next&bit)!=0?android.view.MotionEvent.ACTION_DOWN:android.view.MotionEvent.ACTION_UP,relative?0:e.getX(),relative?0:e.getY(),relative);}
        }else return false;
        if(android.os.SystemClock.uptimeMillis()<mInputTraceUntil&&mMouseTraceEvents<512&&android.os.SystemClock.uptimeMillis()-mInputTraceLast>=50){mInputTraceLast=android.os.SystemClock.uptimeMillis();mMouseTraceEvents++;DebugLog.note("Mouse event action="+action+" relative="+relative+" buttons="+mMouseButtons+" dx="+e.getX()+" dy="+e.getY());}
        return true;
    }
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent e){
        if(e.isFromSource(android.view.InputDevice.SOURCE_MOUSE)&&(e.getActionMasked()==android.view.MotionEvent.ACTION_BUTTON_PRESS||e.getActionMasked()==android.view.MotionEvent.ACTION_BUTTON_RELEASE)&&forwardMouse(e,mSurface!=null&&mSurface.hasPointerCapture()))return true;
        return super.dispatchGenericMotionEvent(e);
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
            if(!isFinishing()){
                DisplayRefresh.apply(this,mSurface==null?null:mSurface.getHolder().getSurface(),cap);
                // This callback also runs after native startup has loaded saved
                // CVars. onCreate may have seen their defaults before that.
                updatePhoneFeatures();
            }
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
        if(open)releaseMouse();
        mHostMenuOpen=open;
        if(mPhoneRumble!=null)mPhoneRumble.active(mForeground&&mWindowFocused&&!open);
        nativeHostMenu(open);updatePhoneFeatureActivity();
    }
    private void recordRuntimeSettings(String reason){
        try{org.json.JSONObject report=new org.json.JSONObject();for(String key:MobileSettingsUi.KEYS)if(!key.startsWith("ui_"))report.put(key,nativeGetSetting(key));
            report.put("renderer_device",new org.json.JSONObject(nativeGetSetting("gta4_session_device_json")));report.put("shader_compatibility",nativeGetSetting("vulkan_shader_compatibility_status"));report.put("actual_present_mode",nativeGetSetting("gta4_session_present_mode"));report.put("thermal_protection",new org.json.JSONObject(nativeGetSetting("gta4_thermal_status")));report.put("session_stats",new org.json.JSONObject(nativeGetSetting("gta4_session_stats")));report.put("display_hz",getWindowManager().getDefaultDisplay().getRefreshRate());android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);if(power!=null)report.put("thermal_status",power.getCurrentThermalStatus());
            DebugLog.note("runtime-settings: reason="+reason+" "+report);
        }catch(Exception error){DebugLog.note("runtime-settings: unavailable="+error.getClass().getSimpleName());}
    }
    private void updatePhoneFeatures(){
        if(mPhoneGyro!=null)try{mPhoneGyro.configure(Float.parseFloat(nativeGetSetting("gta4_phone_gyro_sensitivity")),Float.parseFloat(nativeGetSetting("gta4_phone_gyro_smoothing")),"true".equals(nativeGetSetting("gta4_phone_gyro_invert_x")),"true".equals(nativeGetSetting("gta4_phone_gyro_invert_y")));}catch(RuntimeException ignored){}
        if(mPerformanceHud!=null)mPerformanceHud.configure(nativeGetSetting("gta4_performance_overlay"),nativeGetSetting("gta4_overlay_position"));updatePhoneFeatureActivity();
    }
    private void updatePhoneFeatureActivity(){restoreMouseCapture();boolean active=mForeground&&mWindowFocused&&!mHostMenuOpen&&!mNativeTouchEditor;
        if(mPhoneGyro!=null)mPhoneGyro.active(active&&!"off".equals(nativeGetSetting("gta4_phone_gyro_mode")));
        if(mPerformanceHud!=null)mPerformanceHud.active(active);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){
        super.onConfigurationChanged(configuration);
        if(!mBrokenLibraries&&mLayout!=null)mLayout.post(()->{if(!isFinishing()){updatePhoneFeatures();applyDisplayRate();}});
    }
    @Override protected void onResume(){super.onResume();mForeground=true;SessionMenuUi.foreground(true);if(mSessionRecorder!=null)mSessionRecorder.foreground(true);if(mPhoneRumble!=null)mPhoneRumble.active(mWindowFocused&&!mHostMenuOpen);updatePhoneFeatureActivity();}
    @Override protected void onPause(){if(mPreparation!=null)mPreparation.cancel();releaseMouse();mForeground=false;SessionMenuUi.foreground(false);if(mSessionRecorder!=null)mSessionRecorder.foreground(false);stopInputTrace();if(mPhoneRumble!=null)mPhoneRumble.active(false);updatePhoneFeatureActivity();super.onPause();}
    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if(mBrokenLibraries){super.onBackPressed();return;}
        if(mGameMenu!=null&&mGameMenu.isShowing())mGameMenu.dismiss();else showGameMenu();
    }
    private void showGameMenu() {
        if(mNativeTouchEditor){nativeFinishTouchEditor();return;}
        if(mGameMenu!=null&&mGameMenu.isShowing())return;
        setHostMenu(true);mKeepMenuPaused=false;
        java.util.Map<String,Runnable> actions=new java.util.LinkedHashMap<>();
        actions.put("Resume",()->mGameMenu.dismiss());
        actions.put("Edit mobile controls",()->{mKeepMenuPaused=true;mGameMenu.dismiss();editCurrentLayout(false);});
        actions.put("Edit Xbox controls",()->{mKeepMenuPaused=true;mGameMenu.dismiss();editCurrentLayout(true);});
        actions.put("Control settings",()->{mKeepMenuPaused=true;mGameMenu.dismiss();showChildSettings(true);});
        actions.put("Controller settings",()->{mKeepMenuPaused=true;mGameMenu.dismiss();showChildSettings(false,"Controller");});
        actions.put("Settings",()->{mKeepMenuPaused=true;mGameMenu.dismiss();showChildSettings(false);});
        actions.put("Achievements",()->{mKeepMenuPaused=true;mGameMenu.dismiss();android.app.AlertDialog child=AchievementsUi.show(this,mDataRoot,mRuntimeSettings);child.setOnDismissListener(d->showGameMenu());});
        actions.put("Cheats",()->{mKeepMenuPaused=true;mGameMenu.dismiss();android.app.AlertDialog child=CheatsUi.show(this,mRuntimeSettings,LibertyActivity::nativeQueueCheat,()->setHostMenu(false));child.setOnDismissListener(d->showGameMenu());});
        if(mDebugLog)actions.put("Input test (30 seconds)",()->{mGameMenu.dismiss();startInputTrace();});
        actions.put("Quit to launcher",()->{mKeepMenuPaused=true;mGameMenu.dismiss();confirmQuit();});
        if(mDebugLog){
            actions.put("Share report",this::exportCurrentReport);
            if(getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_RENDERER_CAPTURE,false))actions.put("Capture renderer timings",()->{mGameMenu.dismiss();chooseGraphicsCapture();});
            actions.put("Capture shadow issue",()->{mGameMenu.dismiss();triggerDiagnostic("gta4_shadow_capture_trigger","Capturing shadow images and camera state. Keep rotating the camera briefly.");});
        }
        mGameMenu=SessionMenuUi.show(this,mRuntimeSettings,mDriverMode,mSessionState,actions,()->{if(!mKeepMenuPaused)setHostMenu(false);hideSystemBars();});
    }
    private void showGameSettings() {
        android.widget.LinearLayout panel=new android.widget.LinearLayout(this);panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.app.AlertDialog hub=ModernUi.dialog(this,"Settings",panel);final boolean[] leaving={false};
        ModernUi.action(this,panel,"Graphics & performance",()->{leaving[0]=true;hub.dismiss();showChildSettings(false);});
        ModernUi.action(this,panel,"Controls",()->{leaving[0]=true;hub.dismiss();showChildSettings(true);});
        if(mDebugLog){
            ModernUi.action(this,panel,"Capture shadow issue",()->{
                hub.dismiss();android.widget.Toast.makeText(this,"Shadow capture starts in one second. Keep the issue visible.",android.widget.Toast.LENGTH_LONG).show();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                    if(!isFinishing()&&!isDestroyed())triggerDiagnostic("gta4_shadow_capture_trigger","Capturing shadow images and camera state. Keep rotating the camera briefly.");
                },1000);
            });
            if(getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_RENDERER_CAPTURE,false))ModernUi.action(this,panel,"Capture renderer timings",()->{hub.dismiss();triggerDiagnostic("gta4_profile_native_capture_trigger","Recording a short renderer capture. It adds overhead.");});
        }
        hub.setOnDismissListener(d->{if(!leaving[0])setHostMenu(false);hideSystemBars();});hub.show();
    }
    private void startInputTrace(){
        if(!mDebugLog){android.widget.Toast.makeText(this,"Start Input diagnostics from the launcher first.",android.widget.Toast.LENGTH_LONG).show();return;}
        recordRuntimeSettings("capture-input");mPhoneUi.removeCallbacks(mInputTraceEnd);mMouseTraceEvents=0;mInputTraceLast=0;mInputTraceUntil=android.os.SystemClock.uptimeMillis()+30000;nativeSetInputTrace(true);DebugLog.note("input-test: start duration_seconds=30; test movement, camera, unarmed LMB, RMB+LMB and armed buttons");mPhoneUi.postDelayed(mInputTraceEnd,30000);android.widget.Toast.makeText(this,"Input capture active for 30 seconds.",android.widget.Toast.LENGTH_LONG).show();
    }
    private void stopInputTrace(){mPhoneUi.removeCallbacks(mInputTraceEnd);if(mInputTraceUntil==0)return;mInputTraceUntil=0;nativeSetInputTrace(false);DebugLog.note("input-test: complete or lifecycle-cancelled");}
    private void exportCurrentReport(){
        if(mDebugLogFile==null)return;
        recordRuntimeSettings("export-report");mKeepMenuPaused=true;mGameMenu.dismiss();
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("application/zip");save.putExtra(Intent.EXTRA_TITLE,"XHYN-IV-"+mDebugLogFile.getName()+".zip");
        try{startActivityForResult(save,REQUEST_REPORT_EXPORT);}catch(Exception error){android.widget.Toast.makeText(this,"No file picker is available.",android.widget.Toast.LENGTH_LONG).show();showGameMenu();}
    }
    private void chooseGraphicsCapture(){
        ModernUi.choose(this,"Graphics capture",new String[]{"Quick timing report","Processor detail","Graphics detail (slower)","Command transport (heavy)"},0,mode->{nativeApplySettings(new String[]{"gta4_profile_native_detailed_cpu","gta4_profile_native_detailed_gpu","gta4_profile_native_transport","gta4_profile_native_gpu_query_budget"},new String[]{Boolean.toString(mode==1||mode==3),Boolean.toString(mode==2),Boolean.toString(mode==3),mode==2?"512":"64"});triggerDiagnostic("gta4_profile_native_capture_trigger","Recording a short graphics capture. Detailed modes add overhead.");});
    }
    private void triggerDiagnostic(String key,String message){
        recordRuntimeSettings("capture-"+key);
        int trigger=0;try{trigger=Integer.parseInt(nativeGetSetting(key));}catch(Exception ignored){}
        boolean ok=nativeApplySettings(new String[]{key},new String[]{Integer.toString(trigger+1)});
        android.widget.Toast.makeText(this,ok?message:"Capture is unavailable.",android.widget.Toast.LENGTH_LONG).show();
    }
    private void showChildSettings(boolean touch,String category){MobileSettingsUi.select(this,category);showChildSettings(touch);}
    private void showChildSettings(boolean touch) {
        final boolean[] editing={false};
        android.app.AlertDialog child=touch?MobileSettingsUi.touch(this,mDataRoot,mRuntimeSettings,xbox->{editing[0]=true;mReturnToSettings=true;editCurrentLayout(xbox);}):MobileSettingsUi.graphics(this,mDataRoot,mRuntimeSettings);
        if(child==null){showGameMenu();return;}
        child.setOnDismissListener(d->{applyDisplayRate();updatePhoneFeatures();if(!editing[0])showGameMenu();hideSystemBars();});
    }
    private void editCurrentLayout(boolean xbox) {
        mXboxTouch=xbox;try{TouchLayoutSettings.save(mDataRoot,xbox);}catch(IOException ignored){}
        if(mGameMenu!=null&&mGameMenu.isShowing()){mKeepMenuPaused=true;mGameMenu.dismiss();}
        setHostMenu(true);
        nativeSetTouchLayout(xbox);
        Intent editor=new Intent(this,TouchEditorActivity.class);editor.putExtra("context",nativeTouchEditorContext());editor.putExtra("xbox",xbox);
        startActivityForResult(editor,REQUEST_TOUCH_EDITOR);overridePendingTransition(ModernUi.motion(this)?R.anim.session_panel_in:0,0);
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
            restoreAfterEditor();
        });
    }
    private void restoreAfterEditor(){if(mReturnToSettings){mReturnToSettings=false;showChildSettings(true);}else showGameMenu();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQUEST_REPORT_EXPORT){
            if(result!=RESULT_OK||data==null||data.getData()==null){showGameMenu();return;}
            android.app.AlertDialog progress=ModernUi.builder(this).setTitle("Saving report").setMessage("Preparing one ZIP file to share…").setCancelable(false).create();progress.show();android.net.Uri uri=data.getData();
            new Thread(()->{String message;try(java.io.OutputStream output=getContentResolver().openOutputStream(uri)){if(output==null)throw new IOException("Cannot write report.");DebugLogArchive.write(output,mDataRoot,getFilesDir(),mDebugLogFile.getName());message="Report saved. Share the ZIP when reporting the problem.";}catch(IOException|RuntimeException error){message="Could not save report: "+error.getMessage();}String complete=message;mPhoneUi.post(()->{if(isFinishing()||isDestroyed())return;progress.dismiss();android.widget.Toast.makeText(this,complete,android.widget.Toast.LENGTH_LONG).show();showGameMenu();});},"SaveGameReport").start();return;
        }
        if(request==REQUEST_TOUCH_EDITOR){if(result==RESULT_OK)nativeReloadTouchLayout();restoreAfterEditor();hideSystemBars();}
    }
    void quitForRestart(){ DebugLog.note("Quit for settings restart"); DebugLog.stop(); finishAffinity(); }
    private void confirmQuit() {
        ModernUi.builder(this).setTitle("Quit to launcher?")
            .setMessage("Unsaved game progress will be lost.").setNegativeButton("Cancel",(d,w)->showGameMenu())
            .setOnCancelListener(d->showGameMenu()).setPositiveButton("Quit",(d,w)->{
                DebugLog.note("Player quit to launcher");DebugLog.stop();
                Intent launcher=new Intent(this,LauncherActivity.class);launcher.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launcher);finish();
                // SDL's normal teardown requests native shutdown. Close this
                // separate game process after giving teardown time to finish.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),750);
            }).show();
    }

    @Override protected void onDestroy() {
        if(mPreparation!=null)mPreparation.close();
        mPhoneUi.removeCallbacksAndMessages(null);if(mPhoneGyro!=null)mPhoneGyro.close();if(mPerformanceHud!=null)mPerformanceHud.close();
        if(mPhoneRumble!=null)mPhoneRumble.close();
        if(mPerformanceSampler!=null)mPerformanceSampler.stop();if(mSessionRecorder!=null)mSessionRecorder.close();stopInputTrace();
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
        report.append("pid: ").append(android.os.Process.myPid()).append('\n');
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
        if(!hasFocus)releaseMouse();
        mWindowFocused=hasFocus;updatePhoneFeatureActivity();
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
        configureDriverChoice(dataRoot);
        try{MobileConfig.ensureDefaults(MobileConfig.file(dataRoot));PendingSettings.activate(dataRoot);}catch(IOException e){throw new IllegalStateException("Cannot prepare settings",e);}
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
        try { syncTouchIcons(this); }
        catch(IOException error) { Log.w(TAG,"Touch icons unavailable; using vector icons",error);DebugLog.note("Touch icon preparation failed: "+error.getMessage());android.widget.Toast.makeText(this,"Could not prepare button icons. Using fallback artwork.",android.widget.Toast.LENGTH_LONG).show(); }
        setenv("XDG_DATA_HOME", dataRoot.getAbsolutePath());
        setenv("HOME", dataRoot.getAbsolutePath());
        setenv("REX_RESOURCES_DIR", resources.getAbsolutePath());
        // Preserve upstream's conservative Turnip mode by default on supported
        // Adreno devices. Explicit choices and env.txt keep their precedence.
        String profileMode=mDriverMode.equals(DriverBridge.AUTO)?DriverPolicy.automatic(getIntent()==null?"":getIntent().getStringExtra(EXTRA_GPU_RENDERER),Build.VERSION.SDK_INT):mDriverMode;
        boolean turnipSelected=DriverBridge.isTurnip(dataRoot,profileMode);
        boolean defaultSysmem=turnipSelected&&getIntent()!=null && DriverPolicy.supports("turnip",
            getIntent().getStringExtra(EXTRA_GPU_RENDERER),Build.VERSION.SDK_INT);
        if(turnipSelected&&getIntent()!=null && getIntent().getBooleanExtra(EXTRA_TURNIP_SYSMEM,defaultSysmem)) setenv("TU_DEBUG","sysmem");
        try{if(turnipSelected)for(java.util.Map.Entry<String,String> option:TurnipSettingsUi.environment(dataRoot,profileMode).entrySet())setenv(option.getKey(),option.getValue());}catch(IOException e){Log.w(TAG,"Driver options unavailable",e);}
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
        DebugLaunchOptions.apply(arguments,mDebugLog,false,mDebugLogFile);
        if(mDebugLog&&getIntent()!=null&&getIntent().getBooleanExtra(EXTRA_RENDERER_CAPTURE,false))DebugLaunchOptions.rendererCapture(arguments);
        // Saved choices override bundled startup defaults, not deliberate
        // settings from a different Android preferences store.
        try {
            for(java.util.Map.Entry<String,String> entry:MobileConfig.read(MobileConfig.file(dataRoot)).entrySet()) {
                if(!MobileSettingsUi.KEYS.contains(entry.getKey())||entry.getKey().startsWith("ui_"))continue;
                final String prefix="--"+entry.getKey()+"=";arguments.removeIf(a->a.startsWith(prefix));
                arguments.add(prefix+entry.getValue());
            }
        }catch(IOException failure){Log.w(TAG,"Reading mobile settings failed",failure);}
        try{if(turnipSelected){String workers=TurnipSettingsUi.values(dataRoot).getOrDefault("workers","game");if(workers.matches("[1-5]"))DebugLaunchOptions.set(arguments,"gta4_native_pipeline_workers",workers);}}catch(IOException error){Log.w(TAG,"Driver worker override unavailable",error);}
        arguments.add("--gta4_native_benchmark_trigger=0");
        arguments.add("--gta4_native_benchmark_watchlist="+String.join(",",MobileSettingsUi.KEYS));
        arguments.add("--gta4_shadow_capture_trigger=0");arguments.add("--gta4_trace_shadow_targets=0");
        arguments.add("--gta4_session_paused=false");
        arguments.add("--gta4_native_preparation_cancel=false");arguments.add("--gta4_native_preparation_status=");
        arguments.add("--gta4_completion_percent=-1");arguments.add("--gta4_completion_episode=-1");
        PerformanceLaunchOptions.canonicalAntiAliasing(arguments);
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
        try{arguments.add("--data_file_mods="+MobileConfig.read(MobileConfig.file(dataRoot)).getOrDefault("data_file_mods","false"));}catch(IOException e){Log.w(TAG,"Mod setting unavailable",e);}
        arguments.add("--gta4_touch_icon_directory="+TouchIconAssets.directory(getFilesDir()).getAbsolutePath());
        arguments.add("--game_data_root="+new File(GameStorage.installRoot(dataRoot),"game").getAbsolutePath());
        arguments.add("--gta4_touch_layout="+(mXboxTouch?"gamepad":"liberty"));
        java.util.LinkedHashMap<String,String> unique=new java.util.LinkedHashMap<>();int positional=0;
        for(String argument:arguments) {
            int equal=argument.indexOf('=');String key=argument.startsWith("--") ?
                (equal>0 ? argument.substring(0,equal) : argument) : "#"+positional++;
            unique.put(key,argument);
        }
        mArguments=unique.values().toArray(new String[0]);
        if(mDebugLog)DebugLog.note("Effective arguments: "+java.util.Arrays.toString(mArguments));
        if(mDebugLog)Log.i(TAG, "data=" + dataRoot + " resources=" + resources
                + " args=" + mArguments.length + " driver=" + mDriverMode);
    }


    private void configureDriverChoice(File dataRoot){
        String driver = readFirstLine(new File(dataRoot, "driver.txt"));
        mDriverText = driver;
        String lower = DriverState.normalize(driver);
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
    private static void syncTouchIcons(Context context) throws IOException {
        String stamp;
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            stamp=info.versionCode+":"+info.lastUpdateTime;
        } catch(Exception error) { stamp="unknown"; }
        // Native Android GetUserFolder returns getFilesDir(), regardless of
        // XDG_DATA_HOME. The external data root hid every PNG from the renderer.
        File target=TouchIconAssets.sync(context.getFilesDir(),new TouchIconAssets.Source(){
            public String[] names()throws IOException{return context.getAssets().list("touch-icons");}
            public InputStream open(String name)throws IOException{return context.getAssets().open("touch-icons/"+name);}
        },stamp);
        Log.i(TAG,"Touch icon set ready: "+target);DebugLog.note("Touch icon set ready: "+target);
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

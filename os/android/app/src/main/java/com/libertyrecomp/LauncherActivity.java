package com.libertyrecomp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.app.AlertDialog;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The first screen of the app: game status and installation, the Vulkan
 * driver choice and Play.
 *
 * <p>The driver has to be chosen before the game loads any native code
 * (DriverBridge sets it up once per process), so this is a plain Android
 * screen, and the game runs in its own process ({@code :game}). Play ends a
 * game process left from an earlier session first, so the selected driver
 * always takes effect.</p>
 *
 * <p>The choice is stored in {@code driver.txt}, which LibertyActivity reads:
 * {@code turnip} (bundled), {@code system}, or {@code custom:NAME} for a
 * driver package imported from a .zip into {@code files/drivers/NAME/}.</p>
 */
public class LauncherActivity extends Activity {

    private static final String TAG = "LibertyLauncher";
    private static final int REQUEST_DRIVER_ZIP = 1;
    private static final int REQUEST_DEBUG_EXPORT = 30;
    private static final int REQUEST_SAVE_EXPORT=31,REQUEST_SAVE_IMPORT=32;
    private String mLogSession;
    private int mDebugMode;
    private static final String GAME_PROCESS_SUFFIX = ":game";
    /** Largest driver package accepted, unpacked. */
    private static final long MAX_DRIVER_BYTES = 256L * 1024 * 1024;

    /** Requests for the four installation sources: REQUEST_SOURCE + row index. */
    private static final int REQUEST_SOURCE = 10;
    private static final int SOURCE_GAME = 0;
    private static final int SOURCE_UPDATE = 1;
    private static final int SOURCE_TLAD = 2;
    private static final int SOURCE_TBOGT = 3;
    private static final String[] SOURCE_LABELS = {
            "Base game (disc image .iso)", "Title Update 8", "The Lost and Damned (optional)",
            "The Ballad of Gay Tony (optional)"};

    private File mDataRoot;
    private RadioGroup mDrivers;
    private TextView mDriverNote;
    private Button mPlay;
    private final java.util.Map<String,View> mDriverTrash=new java.util.LinkedHashMap<>();
    private final List<String> mDriverModes = new ArrayList<>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private boolean mBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null){mLogSession=savedInstanceState.getString("log_session");mSelectedSlot=savedInstanceState.getString("selected_slot");}
        // The app icon over a running game: some home screens start a second
        // launcher on top of the task instead of bringing it forward. Step
        // aside so the game underneath comes back.
        Intent launch = getIntent();
        if (!isTaskRoot() && launch != null && Intent.ACTION_MAIN.equals(launch.getAction())
                && launch.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish();
            return;
        }
        File external = getExternalFilesDir(null);
        mDataRoot = external != null ? external : getFilesDir();
        LauncherCrashCapture.install(mDataRoot);
        PreviousExitCapture.capture(this,mDataRoot);
        if(!gameRunning()){
            migrateTouchPreferences();migratePerformanceGraphics();try{MobileConfig.ensureDefaults(MobileConfig.file(mDataRoot));}catch(IOException e){Log.w(TAG,"Default settings deferred",e);}
            android.content.SharedPreferences ui=getSharedPreferences("interface",Context.MODE_PRIVATE);
            if(!ui.getBoolean("radar_offset_v7",false))try{MobileConfig.migrateRadarMargin(MobileConfig.file(mDataRoot));ui.edit().putBoolean("radar_offset_v7",true).apply();}catch(IOException e){Log.w(TAG,"Minimap preference migration deferred",e);}
        }
        setContentView(buildLayout());
        hideSystemBars();
        mPlay.requestFocus();
        new Thread(() -> {
            boolean loaded = Installer.load(this);
            mMain.post(() -> {
                mInstallerReady = loaded;
                refreshInstall();
            });
        }, "InstallerLoad").start();
        // `am start ... --ez play true` (the development scripts) goes
        // straight into the game with the current driver choice.
        if (savedInstanceState == null && getIntent().getBooleanExtra("play", false)) play(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDrivers();
        refreshStatus();
        if (mInstallerReady) refreshInstall();
        refreshCrash();refreshAchievementSummary();
        mMain.removeCallbacks(mStopDebugLog);
        mMain.postDelayed(mStopDebugLog, 10000);
    }

    // Only once the game process is gone: a running game keeps recording.
    private final Runnable mStopDebugLog = () -> new Thread(() -> {
        if (!gameProcessRunning()) DebugLog.stopLeftovers(mDataRoot);
    }, "DebugLogStop").start();

    @Override
    protected void onPause() {
        super.onPause();
        mMain.removeCallbacks(mStopDebugLog);
    }

    private boolean gameProcessRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes =
                manager != null ? manager.getRunningAppProcesses() : null;
        if (processes == null) return false;
        String game = getPackageName() + GAME_PROCESS_SUFFIX;
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (game.equals(info.processName)) return true;
        }
        return false;
    }

    /** A small GitHub mark and the author's name, opening the project page. */
    private View buildGithubFooter() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_github);
        row.addView(mark, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView name = text("vaduur", 13, R.color.picker_muted);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = dp(6);
        row.addView(name, nameParams);
        row.setFocusable(true);
        row.setBackground(focusBackground(Color.TRANSPARENT));
        row.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/vaduur/LibertyRecompAndroid")));
            } catch (Exception e) {
                toast("github.com/vaduur/LibertyRecompAndroid");
            }
        });
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMain.removeCallbacksAndMessages(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ----------------------------------------------------------------- layout

    private TextView mStatus;
    private TextView mLastLaunch;

    private View mDriverPanel,mFilesPanel;
    private TextView mLaunchSummary,mAchievementSummary;
    private String launchSummary(){
        try{java.util.Map<String,String> c=MobileConfig.read(MobileConfig.file(mDataRoot));c.putAll(PendingSettings.read(mDataRoot));String mode=readFirstLine(new File(mDataRoot,"driver.txt"));if(mode==null)mode="Automatic";String driver=BundledDrivers.ALL.containsKey(mode)?BundledDrivers.ALL.get(mode).label:mode.replace("custom:","");String summary=c.getOrDefault("resolution","720p")+" · Cap "+c.getOrDefault("gta4_frame_limit","30")+" FPS · "+c.getOrDefault("gta4_native_anti_aliasing","fxaa").toUpperCase(Locale.ROOT)+" / "+c.getOrDefault("gta4_native_upscaler","native")+"\n"+driver+" · Android "+Build.VERSION.RELEASE+" · "+GpuInfo.renderer();
            if(PendingSettings.pending(mDataRoot))summary+=" · Restart pending";
            return summary;
        }catch(Exception e){return "Configured graphics and driver apply when you play.";}
    }
    private String observedCompletion(){
        File completion=new File(mDataRoot,"LibertyRecomp/saves/last-session-completion.json");
        try{if(completion.isFile()&&completion.length()<4096){JSONObject progress=new JSONObject(new String(java.nio.file.Files.readAllBytes(completion.toPath()),StandardCharsets.UTF_8));double value=progress.optDouble("percent",-1);if(Double.isFinite(value)&&value>=0&&value<=100)return String.format(Locale.ROOT," │ Last observed completion %.2f%%",value);}}catch(Exception ignored){}
        return "";
    }
    private View buildLayout() {
        android.widget.FrameLayout canvas=new android.widget.FrameLayout(this);
        LauncherArtView artwork=new LauncherArtView(this);canvas.addView(artwork,new android.widget.FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xe808090a,0xa008090a,0x1808090a}));canvas.addView(shade,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);ModernUi.safeInsets(this,root,26,8,0);canvas.addView(root,new android.widget.FrameLayout.LayoutParams(-1,-1));
        ScrollView viewport=new ScrollView(this);viewport.setFillViewport(true);viewport.setVerticalScrollBarEnabled(false);
        LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setGravity(Gravity.BOTTOM);viewport.addView(block,new ScrollView.LayoutParams(-1,-1));
        int width=Math.min(dp(500),(int)(getResources().getDisplayMetrics().widthPixels*.48f));root.addView(viewport,new LinearLayout.LayoutParams(width,0,1));
        block.addView(buildCrashCard(),matchWrap(dp(3)));
        mLastLaunch=ModernUi.text(this,"",12,0xffffb7a3);block.addView(mLastLaunch,matchWrap(dp(2)));
        block.addView(ModernUi.text(this,"LIBERTY CITY IS WAITING",12,ModernUi.ACCENT));
        TextView brand=ModernUi.text(this,"XHYN IV",38,ModernUi.TEXT);brand.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));block.addView(brand,matchWrap(dp(3)));
        mLaunchSummary=ModernUi.text(this,launchSummary(),12,ModernUi.MUTED);block.addView(mLaunchSummary,matchWrap(dp(3)));
        mStatus=ModernUi.text(this,"Checking game files…",12,ModernUi.MUTED);LinearLayout.LayoutParams statusGap=matchWrap(dp(5));statusGap.bottomMargin=dp(7);block.addView(mStatus,statusGap);
        mPlay=ModernUi.icon(this,"Play","play",true);mPlay.setTextSize(19);mPlay.setOnClickListener(v->play(false));LinearLayout playRow=new LinearLayout(this);playRow.addView(mPlay,new LinearLayout.LayoutParams(0,dp(50),1));Button overflow=ModernUi.button(this,"⋯",false);overflow.setTextSize(24);overflow.setContentDescription("More actions");LinearLayout.LayoutParams overflowSize=new LinearLayout.LayoutParams(dp(48),dp(50));overflowSize.leftMargin=dp(8);playRow.addView(overflow,overflowSize);overflow.setOnClickListener(v->showLogs());block.addView(playRow);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
        launcherAction(actions,"Settings","settings",this::showSettings);launcherAction(actions,"Game files","folder",this::showFilesPanel);
        launcherAction(actions,"Touch controls","touch",this::showTouchSettings);launcherAction(actions,"Drivers","driver",this::showDriverPanel);

        block.addView(actions,matchWrap(dp(2)));
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.addView(ModernUi.text(this,versionLine(),11,ModernUi.MUTED),new LinearLayout.LayoutParams(-2,-2));LinearLayout links=new LinearLayout(this);links.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);addFooterLink(links,"YouTube ↗","https://youtube.com/@xhyn_ph");addFooterLink(links,"Telegram ↗","https://t.me/xhynports");addFooterLink(links,"GitHub ↗","https://github.com/XHYN-PH/XHYN-IV-recomp");HorizontalScrollView linkStrip=new HorizontalScrollView(this);linkStrip.setHorizontalScrollBarEnabled(false);linkStrip.setFillViewport(true);linkStrip.addView(links);footer.addView(linkStrip,new LinearLayout.LayoutParams(0,-2,1));root.addView(footer,matchWrap(dp(3)));
        if(getResources().getDisplayMetrics().widthPixels>getResources().getDisplayMetrics().heightPixels){
            LinearLayout achievements=ModernUi.panel(this);achievements.setBackground(ModernUi.surface(this,0xc4181a1c,12));
            achievements.addView(ModernUi.text(this,"ACHIEVEMENTS",11,ModernUi.ACCENT));mAchievementSummary=ModernUi.text(this,"Prepare the installed game’s achievement list",14,ModernUi.TEXT);mAchievementSummary.setPadding(0,dp(6),0,dp(6));achievements.addView(mAchievementSummary);
            Button browse=ModernUi.icon(this,"View achievements","trophy",false);browse.setOnClickListener(v->showAchievements());achievements.addView(browse,new LinearLayout.LayoutParams(-1,dp(38)));
            android.widget.FrameLayout.LayoutParams card=new android.widget.FrameLayout.LayoutParams(dp(235),-2,Gravity.RIGHT|Gravity.BOTTOM);canvas.addView(achievements,card);
            footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{android.widget.FrameLayout.LayoutParams p=(android.widget.FrameLayout.LayoutParams)achievements.getLayoutParams();int margin=canvas.getHeight()-root.getTop()-footer.getTop()+dp(8);if(p.bottomMargin!=margin||p.rightMargin!=root.getPaddingRight()){p.setMargins(0,0,root.getPaddingRight(),margin);achievements.setLayoutParams(p);}});
            ModernUi.enter(this,achievements,0);
        }
        footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->artwork.setNavigationTop(root.getTop()+footer.getTop()));
        ModernUi.enter(this,block,0);buildSecondaryPanels();return canvas;
    }
    private void addFooterLink(LinearLayout links,String label,String url){
        TextView link=ModernUi.text(this,label,12,ModernUi.ACCENT);link.setPadding(dp(10),dp(4),dp(3),0);link.setMinHeight(dp(24));link.setGravity(Gravity.CENTER);link.setSingleLine(true);
        link.setContentDescription(label+", "+url);
        link.setFocusable(true);link.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){toast(url);}});links.addView(link);
    }
    private void launcherAction(LinearLayout grid,String label,String icon,Runnable action){
        LinearLayout row=grid.getChildCount()>0?(LinearLayout)grid.getChildAt(grid.getChildCount()-1):null;
        if(row==null||row.getChildCount()==2){row=new LinearLayout(this);row.setBaselineAligned(false);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        Button button=ModernUi.icon(this,label,icon,false);button.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.topMargin=dp(6);if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(button,lp);
    }
    private void buildSecondaryPanels() {
        LinearLayout drivers=new LinearLayout(this);drivers.setOrientation(LinearLayout.VERTICAL);
        drivers.addView(ModernUi.text(this,"Applies next time you play.",13,ModernUi.MUTED));
        ModernUi.action(this,drivers,"Import driver ZIP",this::pickDriverZip);
        ModernUi.action(this,drivers,"Advanced Turnip options",()->TurnipSettingsUi.show(this,mDataRoot,gameRunning()));
        ModernUi.action(this,drivers,"Restore last driver that published frames",()->{
            if(gameRunning()){toast("Quit the game before changing drivers.");return;}
            String working=DriverState.lastWorking(mDataRoot,Build.FINGERPRINT,Build.VERSION.SDK_INT);
            if(working.isEmpty()){toast("No working driver has been recorded for this Android build yet.");return;}
            if(working.startsWith(DriverBridge.CUSTOM_PREFIX)&&!new File(customRoot(),working.substring(DriverBridge.CUSTOM_PREFIX.length())).isDirectory()){toast("The recorded driver package is no longer installed. Choose System or import it again.");return;}
            if(writeDriverMode(working)){refreshDrivers();toast("Recorded working driver selected for next launch.");}
        });
        mDrivers=new RadioGroup(this);mDrivers.setOnCheckedChangeListener((group,id)->onDriverChecked(id));drivers.addView(mDrivers);
        mDriverNote=ModernUi.text(this,"",13,ModernUi.MUTED);drivers.addView(mDriverNote);

        mDriverPanel=drivers;
        LinearLayout files=new LinearLayout(this);files.setOrientation(LinearLayout.VERTICAL);files.addView(buildInstallCard());ModernUi.action(this,files,"Check game files",()->GameDataCheck.show(this,mDataRoot));
        ModernUi.action(this,files,"Save data",this::showSaveData);ModernUi.action(this,files,"Game file location",this::showGameLocation);ModernUi.action(this,files,"Data-file mods",()->ModOverlayUi.show(this,mDataRoot,gameRunning()));
        mRightScroll=new ScrollView(this);mRightScroll.setFillViewport(true);mRightScroll.addView(files);mFilesPanel=mRightScroll;
    }
    private void refreshAchievementSummary(){if(mAchievementSummary==null)return;try{
        File catalog=new File(mDataRoot,"LibertyRecomp/saves/achievements/catalog.json");if(!catalog.isFile()||catalog.length()>2*1024*1024)return;
        org.json.JSONArray entries=new JSONObject(new String(java.nio.file.Files.readAllBytes(catalog.toPath()),StandardCharsets.UTF_8)).getJSONArray("achievements");int unlocked=0;for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optBoolean("unlocked"))unlocked++;
        mAchievementSummary.setText(unlocked+" / "+entries.length()+" unlocked");
    }catch(Exception ignored){mAchievementSummary.setText("Progress unavailable");}}
    private void showAchievements(){
        File catalog=new File(mDataRoot,"LibertyRecomp/saves/achievements/catalog.json");
        if(!catalog.isFile()&&!gameRunning()&&!mBusy&&!Installer.running()&&Installer.installState(mDataRoot)==null){prepareAchievementCatalog();return;}
        AchievementsUi.show(this,mDataRoot,null,gameRunning()?null:this::prepareAchievementCatalog);
    }
    private void prepareAchievementCatalog(){
        if(mBusy||gameRunning()||Installer.running()){toast("Quit the game and finish file operations first.");return;}
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.addView(new ProgressBar(this));body.addView(ModernUi.text(this,"Reading the installed game's achievement metadata…",15,ModernUi.TEXT));android.app.AlertDialog progress=ModernUi.dialog(this,"Preparing achievements",body);progress.setCancelable(false);progress.show();
        setBusy(true,"Preparing achievement metadata...");
        new Thread(()->{String error=Installer.prepareAchievements(this,mDataRoot);mMain.post(()->{progress.dismiss();if(isFinishing()||isDestroyed())return;setBusy(false,null);refreshAchievementSummary();if(error==null||error.isEmpty())showAchievements();else toast(error);});},"PrepareAchievements").start();
    }
    private void showDriverPanel(){showPanel("Graphics driver",mDriverPanel);}
    private void showFilesPanel(){showPanel("Game files & saves",mFilesPanel);}
    private void showPanel(String title,View panel){
        if(panel.getParent() instanceof ViewGroup)((ViewGroup)panel.getParent()).removeView(panel);
        ModernUi.dialog(this,title,panel).show();
    }
    private void showSettings(){MobileSettingsUi.graphics(this,mDataRoot,null,()->showLogs());}
    private void showLogs(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(ModernUi.text(this,"Play with logs records a report you can share after a problem. Previous logs are replaced automatically when the next game session starts. For an input capture, open the in-game menu → Diagnostics → Input test when ready.",14,ModernUi.MUTED));
        ModernUi.action(this,panel,"Play with logs",()->{mDebugMode=0;play(true);});
        ModernUi.action(this,panel,"Input diagnostics (start capture in-game)",()->{mDebugMode=1;play(true);});
        ModernUi.action(this,panel,"Recorded sessions & benchmarks",()->SessionResultsUi.show(this,mDataRoot));
        TextView latest=ModernUi.text(this,"",12,ModernUi.MUTED);panel.addView(latest);
        LinearLayout logActions=new LinearLayout(this);panel.addView(logActions);
        Button share=ModernUi.gridAction(this,logActions,"Share latest log",()->exportLatestLog());
        final Runnable refreshLogs=()->{
            String[] sessions=DebugLogArchive.sessions(mDataRoot,getFilesDir());
            boolean launcher=LauncherCrashReport.available(mDataRoot);
            latest.setText(sessions.length==0?(launcher?"A launcher failure report is ready to share.":"No saved log. Use Play with logs to create one."):"Latest saved log: "+sessions[0]+(gameRunning()?" · recording":""));
            share.setEnabled(sessions.length>0||launcher);
        };
        ModernUi.gridAction(this,logActions,"Delete saved logs",()->{
            if(gameRunning()){toast("Quit the game before deleting its log.");return;}
            final AlertDialog[] confirm={null};
            confirm[0]=ModernUi.dialog(this,"Delete saved logs?",
                ModernUi.text(this,"Remove the saved game logs and diagnostic captures? Recorded performance results are kept.",14,ModernUi.TEXT),
                "Cancel","Delete",()->{
                    if(gameRunning()){toast("Quit the game before deleting its log.");return;}
                    confirm[0].dismiss();share.setEnabled(false);
                    new Thread(()->{String result;try{DebugLogArchive.clear(mDataRoot,getFilesDir());result="Logs deleted.";}catch(IOException e){result="Could not delete logs: "+e.getMessage();}
                        String message=result;mMain.post(()->{if(isFinishing()||isDestroyed())return;refreshLogs.run();toast(message);});},"DeleteLogs").start();
                });confirm[0].show();
        });
        refreshLogs.run();
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        ModernUi.action(this,panel,"Advanced troubleshooting",()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        advanced.addView(ModernUi.text(this,"Use this when asked for a deeper report. Short timing and shadow captures are available from the in-game menu. Captures temporarily add overhead.",13,ModernUi.MUTED));
        ModernUi.action(this,advanced,"Play with detailed diagnostics",()->{mDebugMode=3;play(true);});
        panel.addView(advanced);ModernUi.dialog(this,"Logs",panel).show();
    }
    private void exportLatestLog(){
        String[] sessions=DebugLogArchive.sessions(mDataRoot,getFilesDir());
        if(sessions.length==0&&!LauncherCrashReport.available(mDataRoot)){toast("Use Play with logs first.");return;}
        mLogSession=sessions.length==0?"launcher":sessions[0];Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/zip");save.putExtra(Intent.EXTRA_TITLE,"XHYN-IV-"+mLogSession+".zip");
        try{startActivityForResult(save,REQUEST_DEBUG_EXPORT);}catch(Exception e){toast("No file picker is available.");}
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("selected_slot",mSelectedSlot);state.putString("log_session",mLogSession);super.onSaveInstanceState(state);
    }
    private boolean gameRunning() {
        ActivityManager manager=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return false;
        java.util.List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        if(processes!=null)for(ActivityManager.RunningAppProcessInfo info:processes)
            if((getPackageName()+GAME_PROCESS_SUFFIX).equals(info.processName))return true;
        return false;
    }
    private void migrateTouchPreferences() {
        android.content.SharedPreferences old=getSharedPreferences("touch",Context.MODE_PRIVATE);
        if(old.getBoolean("toml_settings_v4",false))return;
        try {
            java.util.Map<String,String> saved=MobileConfig.read(MobileConfig.file(mDataRoot)),changes=new java.util.LinkedHashMap<>();
            if(old.contains("camera")&&!saved.containsKey("gta4_touch_camera"))changes.put("gta4_touch_camera",old.getString("camera","swipe"));
            String[][] names={{"scale","gta4_touch_gamepad_scale"},{"opacity","gta4_touch_gamepad_opacity"},{"sensitivity","gta4_touch_swipe_sensitivity"}};
            for(String[] pair:names)if(old.contains(pair[0])&&!saved.containsKey(pair[1])) {
                float value=old.getFloat(pair[0],1f);if(Float.isFinite(value)&&value>0)changes.put(pair[1],Float.toString(value));
            }
            if(!changes.isEmpty())MobileConfig.update(MobileConfig.file(mDataRoot),changes);
            old.edit().putBoolean("toml_settings_v4",true).apply();
        }catch(IOException|RuntimeException error){Log.w(TAG,"Touch preference migration failed",error);}
    }
    private void showTouchSettings() {
        if(gameRunning()){toast("Open Touch controls from the in-game menu while playing.");return;}
        MobileSettingsUi.touch(this,mDataRoot,null);
    }
    private String mSelectedSlot;
    private static final int REQUEST_SLOT_EXPORT=33,REQUEST_SLOT_IMPORT=34;
    private static final int REQUEST_GAME_TREE=35;
    private void showGameLocation(){
        if(mBusy||gameRunning()){toast("Quit the game and finish the current task first.");return;}
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.addView(ModernUi.text(this,"Current location and extraction destination:\n"+Installer.installRoot(mDataRoot)+"\n\nNew installs extract here. Choose a location before installing, or copy the current installation here. Save slots and achievements stay in their existing folder.\n\nPicked folders are copied locally for native streaming; they are not used as a live SAF mount. Folder permission is needed only while copying. Keep this screen open until the copy finishes.",13,ModernUi.MUTED));
        ModernUi.action(this,panel,"Use accessible Android/media folder",()->{try{File target=GameStorage.accessible(this);new android.app.AlertDialog.Builder(this).setTitle("Use accessible game folder?").setMessage(target+"\n\nExisting game files are copied and verified first. This needs enough free storage for a second copy. The original remains available.").setPositiveButton("Copy and select",(d,w)->storageOperation(()->GameStorage.switchTo(mDataRoot,target))).setNegativeButton("Cancel",null).show();}catch(IOException e){toast(e.getMessage());}});
        ModernUi.action(this,panel,"Use app data folder",()->storageOperation(()->GameStorage.switchTo(mDataRoot,new File(mDataRoot,"LibertyRecomp"))));
        ModernUi.action(this,panel,"Copy an existing game folder here",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);new android.app.AlertDialog.Builder(this).setTitle("Import a local game copy?").setMessage("Choose the folder containing default.xex, or its parent containing game and optionally dlc. A validated copy replaces those folders here. Any replaced files are kept in folder-import-recovery; other app data stays in place.").setNegativeButton("Cancel",null).setPositiveButton("Choose folder",(d,w)->{try{startActivityForResult(pick,REQUEST_GAME_TREE);}catch(Exception e){toast("No folder picker available.");}}).show();});
        ModernUi.dialog(this,"Game file location",panel).show();
    }
    private void storageOperation(SlotTask task){if(mBusy||gameRunning())return;setBusy(true,"Preparing game folder…");new Thread(()->{String result;try{task.run();String catalog=Installer.prepareAchievements(this,mDataRoot);result="Game folder ready. Original files were kept."+(catalog==null||catalog.isEmpty()?"":" Achievement catalog: "+catalog);}catch(IOException|RuntimeException e){result="Game folder operation did not finish: "+e.getMessage();}String value=result;mMain.post(()->{setBusy(false,null);toast(value);refreshInstall();refreshStatus();});},"GameStorage").start();}
    private void showSaveData() {
        if(mBusy)return;if(gameRunning()){toast("Quit the game before managing saves.");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Save data").setItems(new String[]{"Manage individual slots","Export all saves & achievements","Import full backup"},(dialog,which)->{if(which==0)showSaveSlots();else if(which==1)pickSaveDocument(true);else new android.app.AlertDialog.Builder(this).setTitle("Restore full backup?").setMessage("Validate and replace included save data, keeping a recovery copy. Older save-only backups preserve achievements.").setPositiveButton("Choose ZIP",(d,w)->pickSaveDocument(false)).setNegativeButton("Cancel",null).show();}).show();
    }
    private void showSaveSlots(){
        if(mBusy||gameRunning())return;setBusy(true,"Reading save slots…");new Thread(()->{try{java.util.List<SaveData.Slot> slots=SaveData.slots(SaveData.root(mDataRoot));mMain.post(()->{setBusy(false,null);if(slots.isEmpty()){toast("No save slots found. Make an in-game save first.");return;}String[] labels=new String[slots.size()];for(int i=0;i<labels.length;i++){SaveData.Slot slot=slots.get(i);labels[i]=slot.label()+"\n"+slot.details();}
        new android.app.AlertDialog.Builder(this).setTitle("Individual save slots").setItems(labels,(d,index)->{mSelectedSlot=slots.get(index).relative;new android.app.AlertDialog.Builder(this).setTitle(slots.get(index).label()).setItems(new String[]{"Back up this slot","Restore this slot from ZIP","Delete this slot"},(choice,action)->{if(action<2)pickSlotDocument(action==0);else new android.app.AlertDialog.Builder(this).setTitle("Delete selected slot?").setMessage("Only this save package and its header are removed. Other slots and achievements are preserved.").setPositiveButton("Delete",(x,w)->slotOperation(()->SaveData.deleteSlot(SaveData.root(mDataRoot),mSelectedSlot),"Selected save removed.")).setNegativeButton("Cancel",null).show();}).show();}).show();});}catch(IOException e){mMain.post(()->{setBusy(false,null);toast(e.getMessage());});}},"ReadSaveSlots").start();
    }
    private interface SlotTask{void run()throws IOException;}
    private void slotOperation(SlotTask work,String message){if(mBusy||gameRunning()){toast("Quit the game and finish the current task first.");return;}setBusy(true,"Updating selected slot…");new Thread(()->{String result;try{work.run();result=message;}catch(IOException e){result="Save operation failed: "+e.getMessage();}String value=result;mMain.post(()->{setBusy(false,null);toast(value);refreshAchievementSummary();});},"ManageSaveSlot").start();}
    private void pickSlotDocument(boolean export){Intent i=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");if(export)i.putExtra(Intent.EXTRA_TITLE,"XHYN-IV-slot-"+new File(mSelectedSlot).getName()+".zip");try{startActivityForResult(i,export?REQUEST_SLOT_EXPORT:REQUEST_SLOT_IMPORT);}catch(Exception e){toast("No file picker available.");}}
    private void pickSaveDocument(boolean export) {
        Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/zip");
        if(export)intent.putExtra(Intent.EXTRA_TITLE,"XHYN-IV-saves.zip");
        try{startActivityForResult(intent,export?REQUEST_SAVE_EXPORT:REQUEST_SAVE_IMPORT);}
        catch(Exception error){toast("No file picker is available.");}
    }
    private android.widget.SeekBar touchSlider(LinearLayout panel,String name,int min,int max,float value) {
        TextView label=new TextView(this);panel.addView(label);
        android.widget.SeekBar slider=new android.widget.SeekBar(this);slider.setMax(max-min);
        slider.setProgress(Math.max(0,Math.min(max-min,Math.round(value*100)-min)));
        label.setText(name+": "+(slider.getProgress()+min)+"%");
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean user) { label.setText(name+": "+(progress+min)+"%"); }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });panel.addView(slider);return slider;
    }

    private String versionLine() {
        String version = "?";
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
        }
        // The chip model is only known from Android 12 on.
        String soc = Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : null;
        String gpu = readFirstLine(new File("/sys/class/kgsl/kgsl-3d0/gpu_model"));
        return "v" + version + "  ·  " + Build.MODEL
                + (soc != null && !soc.isEmpty() && !"unknown".equals(soc) ? "  ·  " + soc : "")
                + (gpu != null ? "  ·  " + gpu : "");
    }

    // ------------------------------------------------------------- game state

    private void refreshStatus() {
        if(mLaunchSummary!=null)mLaunchSummary.setText(launchSummary());
        File game = new File(Installer.installRoot(mDataRoot), "game");
        String[] installed = game.list();
        // The installer's own check once it is loaded; before that, a quick look.
        boolean installedNow = mInstallerReady ? mInstalled
                : installed != null && installed.length > 0;
        if (installedNow) {
            mStatus.setText("Game installed"+observedCompletion());
        } else {
            File[] sources = new File(mDataRoot, "install").listFiles();
            boolean disc = false;
            boolean update = false;
            for (File file : sources != null ? sources : new File[0]) {
                if (!file.isFile() || file.getName().startsWith(".")) continue;
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".iso")) disc = true;
                else update = true;
            }
            if (disc && update) {
                mStatus.setText("Game files found in install/. Open Game files and press Install.");
            } else {
                mStatus.setText("Game not installed. Choose the disc image and Title Update 8 "
                        + "in Game files and press Install.");
            }
        }
        String result = null;
        File report = new File(mDataRoot, "last_launch.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(report), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("result: cannot start: ")) {
                    result = line.substring("result: cannot start: ".length());
                }
            }
        } catch (IOException ignored) {
        }
        mLastLaunch.setText(result != null ? "Last launch failed: " + result : "");
        mLastLaunch.setVisibility(result != null ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- crashes

    private LinearLayout mCrashCard;
    private TextView mCrashText;
    private CrashReports.Report mCrash;

    /** A red card that says where the last crash report was saved, with Share. */
    private View buildCrashCard() {
        mCrashCard = new LinearLayout(this);
        mCrashCard.setOrientation(LinearLayout.VERTICAL);
        mCrashCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        android.graphics.drawable.GradientDrawable background = rounded(0x33FF5A5A, dp(10));
        background.setStroke(dp(2), 0xFFFF5A5A);
        mCrashCard.setBackground(background);
        TextView title = text("The game crashed - a crash report was saved", 15,
                R.color.picker_text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        mCrashCard.addView(title);
        mCrashText = text("", 12, R.color.picker_muted);
        mCrashCard.addView(mCrashText, matchWrap(dp(4)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button share = button("Share report", false);
        share.setOnClickListener(v -> shareCrash());
        actions.addView(share, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button dismiss = button("Dismiss", false);
        dismiss.setOnClickListener(v -> {
            if (mCrash != null) CrashReports.dismiss(this, mCrash);
            mCrash = null;
            mCrashCard.setVisibility(View.GONE);
        });
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(dp(110), dp(44));
        dismissParams.leftMargin = dp(10);
        actions.addView(dismiss, dismissParams);
        mCrashCard.addView(actions, matchWrap(dp(8)));
        mCrashCard.setVisibility(View.GONE);
        return mCrashCard;
    }

    private void refreshCrash() {
        new Thread(() -> {
            CrashReports.Report report = CrashReports.collect(this, mDataRoot);
            mMain.post(() -> {
                mCrash = report;
                if (report == null) {
                    mCrashCard.setVisibility(View.GONE);
                    return;
                }
                mCrashText.setText(report.headline + "\n\nSaved to Android/data/" + getPackageName()
                        + "/files/crash_reports/" + report.file.getName()
                        + "\nPlease attach it to an issue on GitHub (vaduur/LibertyRecompAndroid), "
                        + "or press Share report to send it from here.");
                mCrashCard.setVisibility(View.VISIBLE);
            });
        }, "CrashReports").start();
    }

    private void shareCrash() {
        if (mCrash == null) return;
        String text = mCrash.text;
        if (text.length() > 100000) text = text.substring(0, 100000) + "\n... (truncated)";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Liberty Recompiled crash report: " + mCrash.headline);
        send.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(send, "Share crash report"));
        } catch (Exception e) {
            toast("No app to share with. The report is in " + mCrash.file.getAbsolutePath());
        }
    }

    // ----------------------------------------------------------- installation

    private boolean mInstallerReady;
    private boolean mInstalled;
    private boolean mExpanded;
    private final String[] mSources = new String[SOURCE_LABELS.length];
    private final TextView[] mSourceValues = new TextView[SOURCE_LABELS.length];
    private Installer.Inspection mInspection;
    private boolean mInspecting;
    private TextView mInstallTitle;
    private TextView mInstallNote;
    private TextView mInspectionText;
    private LinearLayout mInstallBody;
    private LinearLayout mSourceRows;
    private ScrollView mRightScroll;
    private Button mExpand;
    private Button mInstall;
    private Button mCancelInstall;
    private ProgressBar mProgress;
    private TextView mProgressText;

    private View buildInstallCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(rounded(color(R.color.picker_card), dp(10)));

        mInstallTitle = text("Game", 18, R.color.picker_text);
        mInstallTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(mInstallTitle);
        mInstallNote = text("Checking the installation...", 12, R.color.picker_muted);
        card.addView(mInstallNote, matchWrap(dp(2)));

        mExpand = button("Reinstall or add episodes", false);
        mExpand.setOnClickListener(v -> {
            mExpanded = true;
            refreshInstall();
        });
        card.addView(mExpand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) mExpand.getLayoutParams()).topMargin = dp(10);

        mInstallBody = new LinearLayout(this);
        mInstallBody.setOrientation(LinearLayout.VERTICAL);
        mSourceRows = new LinearLayout(this);
        mSourceRows.setOrientation(LinearLayout.VERTICAL);
        mInstallBody.addView(mSourceRows, matchWrap(0));
        for (int i = 0; i < SOURCE_LABELS.length; ++i) {
            final int index = i;
            TextView label = text(SOURCE_LABELS[i], 14, R.color.picker_text);
            mSourceRows.addView(label, matchWrap(dp(12)));
            mSourceValues[i] = text("Not selected", 12, R.color.picker_muted);
            mSourceRows.addView(mSourceValues[i], matchWrap(dp(2)));
            if (i == SOURCE_GAME) {
                mInspectionText = text("", 12, R.color.picker_muted);
                mSourceRows.addView(mInspectionText, matchWrap(dp(2)));
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button choose = button("Choose file...", false);
            choose.setOnClickListener(v -> chooseSource(index));
            row.addView(choose, new LinearLayout.LayoutParams(0, dp(44), 1f));
            Button clear = button("Clear", false);
            clear.setOnClickListener(v -> setSource(index, null));
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(100), dp(44));
            clearParams.leftMargin = dp(10);
            row.addView(clear, clearParams);
            mSourceRows.addView(row, matchWrap(dp(6)));
        }

        mProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mProgress.setMax(1000);
        mInstallBody.addView(mProgress, matchWrap(dp(14)));
        mProgressText = text("", 12, R.color.picker_muted);
        mInstallBody.addView(mProgressText, matchWrap(dp(2)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        mInstall = button("Install", true);
        mInstall.setOnClickListener(v -> startInstall());
        actions.addView(mInstall, new LinearLayout.LayoutParams(0, dp(52), 1f));
        mCancelInstall = button("Cancel", false);
        mCancelInstall.setOnClickListener(v -> {
            Installer.cancel();
            mProgressText.setText("Cancelling...");
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(120), dp(52));
        cancelParams.leftMargin = dp(10);
        actions.addView(mCancelInstall, cancelParams);
        mInstallBody.addView(actions, matchWrap(dp(12)));

        mInstallBody.addView(text("Files can be picked anywhere on the device storage or an "
                + "SD card (the app asks for file access once). Alternatively copy them into "
                + "Android/data/" + getPackageName() + "/files/install/ - they are picked up "
                + "automatically.", 12, R.color.picker_muted), matchWrap(dp(10)));
        card.addView(mInstallBody, matchWrap(0));
        return card;
    }

    /** Re-reads the installation state and the install/ folder, then redraws. */
    private void refreshInstall() {
        if (!mInstallerReady) {
            mInstallTitle.setText("Game");
            mInstallNote.setText(Installer.loadError() == null ? "Checking the installation..."
                    : "The installer is unavailable (" + Installer.loadError() + "). The game "
                    + "installs itself on Play from the install/ folder.");
            mExpand.setVisibility(View.GONE);
            mInstallBody.setVisibility(View.GONE);
            return;
        }
        if (Installer.running()) {
            showInstallProgress();
            return;
        }
        new Thread(() -> {
            String reason = Installer.installState(mDataRoot);
            mMain.post(() -> {
                mInstalled = reason == null;
                preselectFromInstallFolder();
                updateInstallViews(reason);
                refreshStatus();
            });
        }, "InstallState").start();
    }

    private void updateInstallViews(String reason) {
        boolean running = Installer.running();
        mInstallTitle.setText(mInstalled ? "Game installed" : "Install the game");
        mInstallNote.setText(mInstalled
                ? "GTA IV with Title Update 8 is installed and ready."
                : "You need your own USA disc image and Title Update 8 (0.0.8.5)."
                        + (reason != null && !reason.equals("not installed")
                        && new File(Installer.installRoot(mDataRoot), "game").exists()
                        ? "\nCurrent installation: " + reason : ""));
        boolean showBody = !mInstalled || mExpanded || running;
        mExpand.setVisibility(showBody ? View.GONE : View.VISIBLE);
        mInstallBody.setVisibility(showBody ? View.VISIBLE : View.GONE);
        for (int i = 0; i < SOURCE_LABELS.length; ++i) {
            mSourceValues[i].setText(mSources[i] != null ? mSources[i] : "Not selected");
        }
        if (mInspecting) {
            mInspectionText.setText("Checking the disc image...");
            mInspectionText.setTextColor(0xFFFFC840);
        } else if (mInspection != null && mSources[SOURCE_GAME] != null) {
            mInspectionText.setText(mInspection.summary);
            mInspectionText.setTextColor(mInspection.supported ? 0xFF5AE673 : 0xFFFF5A5A);
        } else {
            mInspectionText.setText("");
        }
        boolean gameReady = mSources[SOURCE_GAME] != null && mInspection != null
                && mInspection.supported && mSources[SOURCE_UPDATE] != null;
        boolean episodesOnly = mInstalled && mSources[SOURCE_GAME] == null
                && mSources[SOURCE_UPDATE] == null
                && (mSources[SOURCE_TLAD] != null || mSources[SOURCE_TBOGT] != null);
        boolean canInstall = !running && !mInspecting && (gameReady || episodesOnly);
        mInstall.setEnabled(canInstall);
        mInstall.setAlpha(canInstall ? 1f : 0.4f);
        mInstall.setText(episodesOnly ? "Install episodes" : "Install");
        mCancelInstall.setVisibility(running ? View.VISIBLE : View.GONE);
        mSourceRows.setVisibility(running ? View.GONE : View.VISIBLE);
        mInstall.setVisibility(running ? View.GONE : View.VISIBLE);
        mProgress.setVisibility(running ? View.VISIBLE : View.GONE);
        if (!running) mProgressText.setText("");
        mPlay.setAlpha(mInstalled && !running ? 1f : 0.5f);
    }

    /** Sources found in files/install/ fill empty rows: the first .iso and the largest other file. */
    private void preselectFromInstallFolder() {
        if (mSources[SOURCE_GAME] != null || mSources[SOURCE_UPDATE] != null) return;
        File[] files = new File(mDataRoot, "install").listFiles();
        if (files == null) return;
        File disc = null;
        File update = null;
        for (File file : files) {
            if (!file.isFile() || file.getName().startsWith(".")) continue;
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".iso")) {
                if (disc == null) disc = file;
            } else if (update == null || file.length() > update.length()) {
                update = file;
            }
        }
        if (disc == null && update == null) return;
        if (update != null) mSources[SOURCE_UPDATE] = update.getAbsolutePath();
        if (disc != null) setSource(SOURCE_GAME, disc.getAbsolutePath());
    }

    private void setSource(int index, String path) {
        mSources[index] = path;
        if (index == SOURCE_GAME) {
            mInspection = null;
            if (path != null) {
                mInspecting = true;
                new Thread(() -> {
                    Installer.Inspection inspection = Installer.inspect(path);
                    mMain.post(() -> {
                        if (path.equals(mSources[SOURCE_GAME])) {
                            mInspection = inspection;
                            mInspecting = false;
                            updateInstallViews(null);
                        }
                    });
                }, "InspectSource").start();
            } else {
                mInspecting = false;
            }
        }
        updateInstallViews(null);
    }

    @SuppressWarnings("deprecation")
    private void chooseSource(int index) {
        if (!Installer.hasFileAccess(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Allow file access")
                    .setMessage("To read the game files from anywhere on this device, Liberty "
                            + "Recompiled needs access to all files. Android opens the setting "
                            + "next; enable it and come back.\n\nWithout it, copy the files into "
                            + "Android/data/" + getPackageName() + "/files/install/ instead.")
                    .setPositiveButton("Open setting", (d, w) -> Installer.requestFileAccess(this, 2))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_SOURCE + index);
        } catch (Exception e) {
            toast("No file picker is available on this device.");
        }
    }

    private void onSourcePicked(int index, Uri uri) {
        String path = Installer.pathFromUri(this, uri);
        if (path == null) {
            toast("This file has no path on the device (cloud storage?). Pick it from the "
                    + "device storage or an SD card.");
            return;
        }
        if (!new File(path).canRead()) {
            toast("Cannot read " + path + ". Check that file access is allowed.");
            return;
        }
        setSource(index, path);
    }

    private void startInstall() {
        boolean episodesOnly = mSources[SOURCE_GAME] == null;
        if (!Installer.start(mSources[SOURCE_GAME], mSources[SOURCE_UPDATE], mSources[SOURCE_TLAD],
                mSources[SOURCE_TBOGT], mDataRoot)) {
            toast("An installation is already running.");
            return;
        }
        Log.i(TAG, "installation started" + (episodesOnly ? " (episodes only)" : ""));
        showInstallProgress();
        mRightScroll.post(() -> mRightScroll.smoothScrollTo(0, 0));
        mCancelInstall.requestFocus();
    }

    private void showInstallProgress() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateInstallViews(null);
        mInstallTitle.setText("Installing...");
        mInstallNote.setText("Keep the app open. This takes several minutes.");
        long[] progress = Installer.progress();
        long copied = progress[0];
        long total = progress[1];
        mProgress.setProgress(total > 0 ? (int) Math.min(1000, copied * 1000 / total) : 0);
        mProgressText.setText(total > 0
                ? String.format(Locale.ROOT, "%.1f of %.1f GB (%d%%)", copied / 1e9, total / 1e9,
                copied * 100 / total)
                : "Checking the sources...");
        mStatus.setText(total > 0
                ? String.format(Locale.ROOT, "Installing the game... %d%%", copied * 100 / total)
                : "Installing the game...");
        if (Installer.running()) {
            mMain.postDelayed(this::showInstallProgress, 300);
            return;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String result = Installer.takeResult();
        if (result != null && result.isEmpty()) {
            toast("Installation complete. Press Play.");
            Arrays.fill(mSources, null);
            mInspection = null;
            mExpanded = false;
            refreshInstall();
            refreshAchievementSummary();
            mPlay.requestFocus();
        } else {
            if (result != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Installation failed")
                        .setMessage(result)
                        .setPositiveButton("OK", null)
                        .show();
            }
            refreshInstall();
        }
    }

    // ---------------------------------------------------------------- drivers

    private File customRoot() {
        return new File(mDataRoot, "drivers");
    }

    private void refreshDrivers() {
        if(!mBusy&&!gameRunning())try{DriverPackages.recoverAll(customRoot());}
        catch(IOException error){Log.w(TAG,"Driver recovery could not finish",error);}
        String current = currentDriverMode();
        mDrivers.setOnCheckedChangeListener(null);
        mDrivers.clearCheck();
        mDrivers.removeAllViews();
        mDriverModes.clear();mDriverTrash.clear();
        String renderer=GpuInfo.renderer();
        addDriver(DriverBridge.AUTO,"Automatic","Bundled Turnip for admitted Adreno GPUs; System otherwise");
        addDriver(DriverBridge.SYSTEM,"System driver","The device's Vulkan driver; actual required features are checked before use");
        java.util.List<BundledDrivers.Bundle> bundles=new java.util.ArrayList<>(BundledDrivers.ALL.values());
        bundles.sort(java.util.Comparator.comparing(bundle->bundle.label));
        for(BundledDrivers.Bundle bundle:bundles)if(DriverPolicy.supports(bundle.mode,renderer,Build.VERSION.SDK_INT))
            addDriver(bundle.mode,bundle.label,DriverPolicy.reason(bundle.mode,renderer,Build.VERSION.SDK_INT));
        java.util.List<File> unavailable=new java.util.ArrayList<>();
        File[] packages=customRoot().listFiles();
        if(packages!=null){
            Arrays.sort(packages);
            for(File dir:packages){
                if(!dir.isDirectory()||dir.getName().startsWith(".")||!hasLibrary(dir))continue;
                JSONObject meta=readMeta(dir);
                int minimum=meta==null?29:meta.optInt("minApi",29);
                if(Build.VERSION.SDK_INT<minimum){unavailable.add(dir);continue;}
                String name=meta==null?dir.getName():meta.optString("name",dir.getName());
                String details=meta==null?"Imported; device support is unverified":join(meta.optString("description"),meta.optString("driverVersion"),meta.optString("author").isEmpty()?"":"by "+meta.optString("author"));
                addDriver(DriverBridge.CUSTOM_PREFIX+dir.getName(),name,details.isEmpty()?"Imported; feature checks run before use":details);
            }
        }
        for(BundledDrivers.Bundle bundle:bundles)if(!DriverPolicy.supports(bundle.mode,renderer,Build.VERSION.SDK_INT)){
            RadioButton choice=addDriver(bundle.mode,bundle.label,DriverPolicy.reason(bundle.mode,renderer,Build.VERSION.SDK_INT));choice.setEnabled(false);
        }
        for(File dir:unavailable){
            JSONObject meta=readMeta(dir);
            RadioButton choice=addDriver(DriverBridge.CUSTOM_PREFIX+dir.getName(),meta==null?dir.getName():meta.optString("name",dir.getName()),"Requires Android API "+(meta==null?29:meta.optInt("minApi",29)));
            choice.setEnabled(false);
        }
        int index = mDriverModes.indexOf(current);
        if (index < 0) index = 0;
        mDrivers.check(index + 1);
        mDrivers.setOnCheckedChangeListener((group, id) -> onDriverChecked(id));
        updateDriverNote(index);
    }

    private RadioButton addDriver(String mode, String name, String details) {
        mDriverModes.add(mode);
        RadioButton button = new RadioButton(this);
        button.setId(mDriverModes.size());
        if (details.length() > 80) details = details.substring(0, 77).trim() + "...";
        android.text.SpannableStringBuilder label = new android.text.SpannableStringBuilder(name);
        label.append('\n');
        int start = label.length();
        label.append(details);
        label.setSpan(new android.text.style.RelativeSizeSpan(0.8f), start, label.length(), 0);
        label.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.picker_muted)),
                start, label.length(), 0);
        button.setText(label);
        button.setTextColor(color(R.color.picker_text));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setPadding(dp(6), dp(8), dp(6), dp(8));
        button.setBackground(focusBackground(Color.TRANSPARENT));
        if(mode.startsWith(DriverBridge.CUSTOM_PREFIX)){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);
            row.addView(button,new LinearLayout.LayoutParams(0,-2,1));button.setOnClickListener(v->mDrivers.check(button.getId()));
            Button trash=ModernUi.iconButton(this,"trash","Delete "+name);trash.setVisibility(View.INVISIBLE);trash.setOnClickListener(v->confirmRemoveDriver(mode,name));row.addView(trash,new LinearLayout.LayoutParams(dp(40),dp(40)));mDriverTrash.put(mode,trash);
            mDrivers.addView(row,new RadioGroup.LayoutParams(-1,-2));
        }else mDrivers.addView(button,new RadioGroup.LayoutParams(-1,-2));
        return button;
    }

    private void onDriverChecked(int id) {
        int index = id - 1;
        if (index < 0 || index >= mDriverModes.size()) return;
        if(gameRunning()){toast("Quit the game before changing drivers.");refreshDrivers();return;}
        if(!writeDriverMode(mDriverModes.get(index))){refreshDrivers();return;}
        updateDriverNote(index);
    }

    private void updateDriverNote(int index) {
        String mode = index >= 0 && index < mDriverModes.size() ? mDriverModes.get(index) : "";
        for(java.util.Map.Entry<String,View> entry:mDriverTrash.entrySet())entry.getValue().setVisibility(entry.getKey().equals(mode)?View.VISIBLE:View.INVISIBLE);
        for(int i=0;i<mDrivers.getChildCount();i++){View child=mDrivers.getChildAt(i);if(child instanceof LinearLayout){RadioButton radio=(RadioButton)((LinearLayout)child).getChildAt(0);radio.setChecked(radio.getId()==index+1);}}
        mDriverNote.setText((GpuInfo.renderer().isEmpty() ? "GPU could not be identified" : GpuInfo.renderer())+(BundledDrivers.ALL.containsKey(mode)?"\n"+DriverPolicy.reason(mode,GpuInfo.renderer(),Build.VERSION.SDK_INT):""));
        try{
            java.util.Properties observed=DriverState.observation(mDataRoot,mode);
            if(observed.containsKey("observed")){
                JSONObject status=new JSONObject(observed.getProperty("native","{}"));
                String reason=status.optString("selection_error",status.optString("fallback_from",status.optString("error","")));
                mDriverNote.append("\nLast observed: "+observed.getProperty("loaded","unknown")+" · "+new java.util.Date(Long.parseLong(observed.getProperty("observed","0")))+(reason.isEmpty()?"":"\n"+reason));
                JSONObject gpu=status.optJSONObject("render_device");
                if(gpu!=null)mDriverNote.append("\nRendered on "+gpu.optString("name")+" · "+gpu.optString("driver_info",gpu.optString("driver_name","")));
                else{org.json.JSONArray devices=status.optJSONArray("devices");if(devices!=null)for(int i=0;i<devices.length();i++){JSONObject probe=devices.optJSONObject(i);if(probe!=null)mDriverNote.append("\nProbe: "+probe.optString("name")+(probe.optString("missing_features").isEmpty()?"":" · Missing "+probe.optString("missing_features"))+(probe.optString("compatibility_warnings").isEmpty()?"":"\n"+probe.optString("compatibility_warnings")));}}
                if(Build.FINGERPRINT.equals(status.optString("device_fingerprint"))&&mode.equals(DriverState.lastWorking(mDataRoot,Build.FINGERPRINT,Build.VERSION.SDK_INT)))mDriverNote.append("\nPreviously published frames on this device. Visual correctness and matched performance remain separate checks.");
            }
        }catch(Exception ignored){}

    }

    private String currentDriverMode() {
        String line = readFirstLine(new File(mDataRoot, "driver.txt"));
        if (line == null) return DriverBridge.AUTO;
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.equals(DriverBridge.AUTO) || BundledDrivers.ALL.containsKey(lower)) return lower;
        if (lower.equals(DriverBridge.SYSTEM)) return DriverBridge.SYSTEM;
        if (lower.startsWith(DriverBridge.CUSTOM_PREFIX)) {
            return DriverBridge.CUSTOM_PREFIX + line.substring(DriverBridge.CUSTOM_PREFIX.length()).trim();
        }
        return DriverBridge.AUTO;
    }

    private boolean writeDriverMode(String mode) {
        try {DriverState.choose(mDataRoot,mode);return true;}
        catch(IOException e){Log.e(TAG,"writing driver choice failed",e);toast("Could not save the driver choice: "+e.getMessage());return false;}
    }

    private void confirmRemoveDriver(String mode,String name){
        if(mBusy){toast("Finish the current task first.");return;}
        if(!mode.startsWith(DriverBridge.CUSTOM_PREFIX))return;
        new AlertDialog.Builder(this).setTitle("Delete driver?").setMessage("Remove "+name+"? Automatic selection will be used next time you play.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                if(mBusy){toast("Finish the current task first.");return;}
                if(gameRunning()){toast("Quit the game before deleting a driver.");return;}
                File root=customRoot(),dir=new File(root,mode.substring(DriverBridge.CUSTOM_PREFIX.length()));
                try{if(!dir.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("Invalid driver path");DriverState.forget(mDataRoot,mode);DriverPackages.delete(dir);if(mode.equals(currentDriverMode()))writeDriverMode(DriverBridge.AUTO);refreshDrivers();refreshStatus();toast("Driver deleted.");}
                catch(IOException error){toast("Could not delete driver: "+error.getMessage());}
            }).show();
    }

    @SuppressWarnings("deprecation")
    private void pickDriverZip() {
        if(mBusy||gameRunning()){toast("Quit the game and finish the current task before importing a driver.");return;}
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_DRIVER_ZIP);
        } catch (Exception e) {
            toast("No file picker is available on this device.");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==REQUEST_GAME_TREE){if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){final Uri uri=data.getData();storageOperation(()->GameStorage.importTree(this,mDataRoot,uri));}return;}
        if(requestCode==REQUEST_SLOT_EXPORT||requestCode==REQUEST_SLOT_IMPORT){if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;if(mSelectedSlot==null){toast("Select the save slot again.");return;}final String selected=mSelectedSlot;final Uri uri=data.getData();slotOperation(()->{File root=SaveData.root(mDataRoot);if(requestCode==REQUEST_SLOT_EXPORT){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot open destination.");SaveData.exportSlot(root,selected,out);}}else{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open backup.");SaveData.importSlot(root,selected,in);}}},requestCode==REQUEST_SLOT_EXPORT?"Selected slot backed up.":"Selected slot restored.");return;}
        if(requestCode==REQUEST_SAVE_EXPORT||requestCode==REQUEST_SAVE_IMPORT) {
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            if(gameRunning()){toast("Quit the game before managing saves.");return;}
            final Uri uri=data.getData();setBusy(true,requestCode==REQUEST_SAVE_EXPORT?"Exporting saves and achievements...":"Importing backup...");
            new Thread(()->{
                String result;
                try {
                    File root=SaveData.root(mDataRoot);
                    if(requestCode==REQUEST_SAVE_EXPORT)try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)) {
                        if(out==null)throw new IOException("Cannot open export file");SaveData.exportZip(root,out);
                    }else try(java.io.InputStream in=getContentResolver().openInputStream(uri)) {
                        if(in==null)throw new IOException("Cannot open save ZIP");SaveData.importZip(root,in);
                    }
                    result=requestCode==REQUEST_SAVE_EXPORT?"Saves and achievements exported.":"Backup imported. Replaced data was kept locally.";
                }catch(IOException|RuntimeException error){result="Save operation failed: "+error.getMessage();}
                final String message=result;mMain.post(()->{setBusy(false,null);toast(message);refreshAchievementSummary();});
            },"ManageSaves").start();return;
        }
        if(requestCode==REQUEST_DEBUG_EXPORT) {
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null) {
                final Uri uri=data.getData();setBusy(true,"Exporting logs...");
                new Thread(()->{
                    String result;
                    try(java.io.OutputStream output=getContentResolver().openOutputStream(uri)) {
                        if(output==null)throw new IOException("Cannot open the selected file");
                        DebugLogArchive.write(output,mDataRoot,getFilesDir(),mLogSession);result="Debug logs exported.";
                    }catch(IOException|RuntimeException error){result="Log export failed: "+error.getMessage();}
                    final String message=result;mMain.post(()->{setBusy(false,null);toast(message);});
                },"ExportDebugLogs").start();
            }
            return;
        }
        if (requestCode >= REQUEST_SOURCE && requestCode < REQUEST_SOURCE + SOURCE_LABELS.length) {
            if (resultCode == RESULT_OK && data != null) {
                onSourcePicked(requestCode - REQUEST_SOURCE, data.getData());
            }
            return;
        }
        if (requestCode != REQUEST_DRIVER_ZIP || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if(mBusy||gameRunning()){toast("Quit the game and finish the current task before importing a driver.");return;}
        setBusy(true, "Importing driver...");
        new Thread(() -> {
            String message;
            String mode = null;
            try {
                mode = importDriver(uri);
                message = "Driver imported and selected.";
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "driver import failed", e);
                message = "Import failed: " + e.getMessage();
            }
            final String finalMessage = message;
            final String finalMode = mode;
            mMain.post(() -> {
                setBusy(false, null);
                boolean selected=finalMode==null||writeDriverMode(finalMode);
                refreshDrivers();
                toast(selected?finalMessage:"Driver imported; the selection could not be saved.");
            });
        }, "DriverImport").start();
    }

    /** Unpacks a driver zip into files/drivers/NAME/ and returns "custom:NAME". */
    private String importDriver(Uri uri) throws IOException {
        if(gameRunning())throw new IOException("Quit the game before importing a driver.");
        File root=customRoot(),staging=null;
        DriverPackages.recoverAll(root);
        try {
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("the file could not be opened");
            staging=DriverPackages.stage(root,raw,MAX_DRIVER_BYTES);
        }
        File[] libraries=staging.listFiles((dir,name)->name.endsWith(".so"));
        if (libraries==null||libraries.length==0) {
            throw new IOException("no driver library (.so) in the zip");
        }
        JSONObject meta = readMeta(staging);
        if(new File(staging,"meta.json").exists()&&meta==null)throw new IOException("Invalid driver metadata.");
        String library = meta != null ? meta.optString("libraryName", "") : "";
        if (!library.isEmpty() && (!DriverPackages.filename(library)||!library.endsWith(".so")||!new File(staging, library).isFile())) {
            throw new IOException("Invalid or missing driver library in meta.json.");
        }
        if (library.isEmpty() && libraries.length != 1) {
            throw new IOException("several .so files and no meta.json saying which is the driver");
        }
        String base = meta != null && !meta.optString("name").isEmpty()
                ? meta.optString("name") : displayName(uri);
        String name = sanitize(base);
        String mode=DriverBridge.CUSTOM_PREFIX+name;
        DriverState.forget(mDataRoot,mode);
        DriverPackages.activate(root,name,staging);
        Log.i(TAG, "imported driver " + name);
        return DriverBridge.CUSTOM_PREFIX + name;
        } finally { if(staging!=null)DriverPackages.delete(staging); }
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (RuntimeException ignored) {
        }
        if (name == null) name = "driver";
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        return name;
    }

    private static String sanitize(String name) {
        String clean = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");
        if (clean.length() > 48) clean = clean.substring(0, 48);
        return clean.isEmpty() ? "driver" : clean;
    }

    private static boolean hasLibrary(File dir) {
        String[] names = dir.list();
        if (names == null) return false;
        for (String name : names) if (name.endsWith(".so")) return true;
        return false;
    }

    private static JSONObject readMeta(File dir) {
        File meta = new File(dir, "meta.json");
        if (!meta.isFile()||meta.length()>65536||java.nio.file.Files.isSymbolicLink(meta.toPath())) return null;
        try (InputStream in = new FileInputStream(meta)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {if(bytes.size()+read>65536)return null;bytes.write(buffer, 0, read);}
            return new JSONObject(bytes.toString("UTF-8").replace("﻿", ""));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------- play

    // Experimental speed settings, kept by the launcher and passed to the game
    // as arguments when it starts (LibertyActivity.applyPerformanceTestArguments).
    private static final String PERF_PREFERENCES = "performance_test";
    private static final String PERF_SHADOWS_OFF = "sun_shadows_off";
    private static final String PERF_HALF_DRAW_DISTANCE = "half_draw_distance";
    private static final String PERF_GPU_TURBO = "gpu_turbo";
    private static final String PERF_SUSTAINED = "sustained_performance";
    private static final String PERF_THERMAL_GUARD = "thermal_guard";
    private static final String PERF_SAFE_MODE = "safe_mode";
    private static final String PERF_WATER_REFLECTIONS = "water_reflection_interval";
    private static final String PERF_OTHER_REFLECTIONS = "other_reflection_interval";

    private android.content.SharedPreferences perfTestPreferences() {
        return getSharedPreferences(PERF_PREFERENCES, Context.MODE_PRIVATE);
    }

    private void migratePerformanceGraphics(){
        android.content.SharedPreferences prefs=perfTestPreferences();if(prefs.getBoolean("graphics_unified",false))return;
        try{java.util.Map<String,String> saved=MobileConfig.read(MobileConfig.file(mDataRoot));java.util.Map<String,String> changes=new java.util.LinkedHashMap<>();
            if(prefs.getBoolean(PERF_HALF_DRAW_DISTANCE,false)){changes.put("gta4_draw_distance_scale","0.5");changes.put("gta4_dynamic_draw_distance","false");}
            if(prefs.getBoolean(PERF_SHADOWS_OFF,false))changes.put("gta4_native_shadow_interval","0");
            if(prefs.contains(PERF_WATER_REFLECTIONS)&&!saved.containsKey("gta4_native_water_reflection_interval"))changes.put("gta4_native_water_reflection_interval",Integer.toString(prefs.getInt(PERF_WATER_REFLECTIONS,1)));
            if(prefs.contains(PERF_OTHER_REFLECTIONS)&&!saved.containsKey("gta4_native_environment_reflection_interval"))changes.put("gta4_native_environment_reflection_interval",Integer.toString(prefs.getInt(PERF_OTHER_REFLECTIONS,1)));
            if(!changes.isEmpty())MobileConfig.update(MobileConfig.file(mDataRoot),changes);
            prefs.edit().putBoolean("graphics_unified",true).remove(PERF_HALF_DRAW_DISTANCE).remove(PERF_SHADOWS_OFF).remove(PERF_WATER_REFLECTIONS).remove(PERF_OTHER_REFLECTIONS).apply();
        }catch(IOException e){Log.w(TAG,"Graphics preference migration deferred",e);}
    }
    private void showPerformanceTestSettings() {
        if(gameRunning()){toast("Use Settings from the in-game menu while playing.");return;}
        MobileSettingsUi.graphics(this,mDataRoot,null);
    }

    /** A check box in the launcher's style: label, muted note, gamepad focus outline. */
    private android.widget.CheckBox styledCheck(String label, String note, boolean checked) {
        android.widget.CheckBox box = new android.widget.CheckBox(this);
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(label);
        text.append('\n');
        int start = text.length();
        text.append(note);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.8f), start, text.length(), 0);
        text.setSpan(new android.text.style.ForegroundColorSpan(color(R.color.picker_muted)),
                start, text.length(), 0);
        box.setText(text);
        box.setTextColor(color(R.color.picker_text));
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        box.setPadding(dp(6), dp(8), dp(6), dp(8));
        box.setBackground(focusBackground(Color.TRANSPARENT));
        box.setFocusable(true);
        box.setChecked(checked);
        return box;
    }

    // Every frame / every 2nd / every 3rd frame / off; radio ids are base + 10 + interval.
    private RadioGroup intervalChoice(LinearLayout panel, String title, int current, int idBase) {
        TextView heading = text(title, 15, R.color.picker_text);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(heading, matchWrap(dp(14)));
        RadioGroup group = new RadioGroup(this);
        String[] labels = {"Every frame", "Every 2nd frame", "Every 3rd frame", "Off"};
        int[] intervals = {1, 2, 3, 0};
        for (int i = 0; i < labels.length; ++i) {
            RadioButton choice = new RadioButton(this);
            choice.setId(idBase + 10 + intervals[i]);
            choice.setText(labels[i]);
            choice.setTextColor(color(R.color.picker_text));
            choice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            choice.setPadding(dp(6), dp(6), dp(6), dp(6));
            choice.setBackground(focusBackground(Color.TRANSPARENT));
            choice.setFocusable(true);
            group.addView(choice);
        }
        boolean known = false;
        for (int interval : intervals) known |= interval == current;
        group.check(idBase + 10 + (known ? current : 1));
        panel.addView(group, matchWrap(dp(4)));
        return group;
    }

    private static int checkedInterval(RadioGroup group, int idBase) {
        int interval = group.getCheckedRadioButtonId() - idBase - 10;
        return interval >= 0 && interval <= 3 ? interval : 1;
    }

    private void play(boolean debugLog) {
        if (mBusy) return;
        if (Installer.running()) {
            toast("The game is being installed.");
            return;
        }
        if (mInstallerReady && !mInstalled) {
            toast("Open Game files and install the game first.");
            return;
        }
        setBusy(true, "Starting...");
        new Thread(() -> {
            // A game process from an earlier session keeps the driver it
            // started with; end it so this launch uses the current choice.
            stopGameProcess();
            try{SessionHistory.recoverBenchmark(mDataRoot,getFilesDir());}catch(IOException e){Log.w(TAG,"Previous benchmark could not be recovered",e);}
            DebugLog.stopLeftovers(mDataRoot);
            try{DebugLogArchive.clear(mDataRoot,getFilesDir());}catch(IOException e){Log.w(TAG,"Old logs could not be fully removed",e);}
            mMain.post(() -> {
                setBusy(false, null);
                Intent intent=new Intent(this,LibertyActivity.class);
                intent.putExtra(LibertyActivity.EXTRA_GPU_RENDERER,GpuInfo.renderer());
                android.content.SharedPreferences touch=getSharedPreferences("touch",Context.MODE_PRIVATE);
                intent.putExtra(LibertyActivity.EXTRA_TURNIP_SYSMEM,touch.getBoolean("turnip_sysmem",
                    DriverPolicy.supports("turnip",GpuInfo.renderer(),Build.VERSION.SDK_INT)));
                if (debugLog) {
                    intent.putExtra(LibertyActivity.EXTRA_DEBUG_LOG,true);
                    intent.putExtra(LibertyActivity.EXTRA_INPUT_TRACE,mDebugMode==1);
                    intent.putExtra(LibertyActivity.EXTRA_BENCHMARK,mDebugMode==2);
                    intent.putExtra(LibertyActivity.EXTRA_RENDERER_CAPTURE,mDebugMode==3);
                }
                android.content.SharedPreferences perf = perfTestPreferences();
                java.util.Map<String,String> settings;try{settings=PerformanceLaunchOptions.nextSettings(mDataRoot);}catch(IOException e){settings=java.util.Collections.emptyMap();}
                intent.putExtra(LibertyActivity.EXTRA_GPU_TURBO, Boolean.parseBoolean(settings.getOrDefault("android_gpu_turbo",Boolean.toString(perf.getBoolean(PERF_GPU_TURBO,false)))));
                intent.putExtra(LibertyActivity.EXTRA_SUSTAINED, UiPreferences.get(this,"sustained"));
                intent.putExtra(LibertyActivity.EXTRA_THERMAL_GUARD,
                        Boolean.parseBoolean(settings.getOrDefault("android_thermal_guard",Boolean.toString(perf.getBoolean(PERF_THERMAL_GUARD,false)))));
                intent.putExtra(LibertyActivity.EXTRA_SAFE_MODE,Boolean.parseBoolean(settings.getOrDefault("gta4_safe_mode",Boolean.toString(perf.getBoolean(PERF_SAFE_MODE,false)))));
                startActivity(intent);
            });
        }, "LaunchGame").start();
    }

    private void stopGameProcess() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        String gameProcess = getPackageName() + GAME_PROCESS_SUFFIX;
        for (int attempt = 0; attempt < 40; ++attempt) {
            boolean running = false;
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo info : processes) {
                    if (gameProcess.equals(info.processName)) {
                        running = true;
                        android.os.Process.killProcess(info.pid);
                    }
                }
            }
            if (!running) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
        Log.w(TAG, "the previous game process did not exit in time");
    }

    // --------------------------------------------------------------- controls

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BUTTON_START:
                    play(false);
                    return true;
                case KeyEvent.KEYCODE_BUTTON_A: {
                    View focused = getCurrentFocus();
                    if (focused != null) focused.performClick();
                    return true;
                }
                case KeyEvent.KEYCODE_BUTTON_B:
                    if (Installer.running()) {
                        toast("The game is being installed.");
                    } else {
                        finish();
                    }
                    return true;
                default:
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BUTTON_START:
                case KeyEvent.KEYCODE_BUTTON_A:
                case KeyEvent.KEYCODE_BUTTON_B:
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ---------------------------------------------------------------- helpers

    private void setBusy(boolean busy, String text) {
        mBusy = busy;
        mPlay.setEnabled(!busy);
        mPlay.setAlpha(busy || (mInstallerReady && !mInstalled) ? 0.5f : 1f);
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
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

    private TextView text(String value, int sp, int colorRes) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color(colorRes));
        return view;
    }

    private Button button(String label, boolean primary) {
        return ModernUi.button(this,label,primary);
    }
    private StateListDrawable focusBackground(int fill) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},rounded(0x554d4533,dp(3)));
        states.addState(new int[]{android.R.attr.state_focused},rounded(0x554d4533,dp(3)));
        states.addState(new int[]{},rounded(fill,dp(3)));return states;
    }

    private static GradientDrawable rounded(int fill, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int color(int res) {
        return getResources().getColor(res, getTheme());
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private static String join(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (out.length() > 0) out.append(" | ");
            out.append(part);
        }
        return out.toString();
    }

    private static String readFirstLine(File file) {
        if (!file.isFile()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? null : line.replace("﻿", "").trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}

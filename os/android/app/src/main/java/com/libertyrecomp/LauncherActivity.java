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
    private Button mRemove;
    private final List<String> mDriverModes = new ArrayList<>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private boolean mBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null)mLogSession=savedInstanceState.getString("log_session");
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
        if(!gameRunning()){
            migrateTouchPreferences();migratePerformanceGraphics();
            android.content.SharedPreferences ui=getSharedPreferences("interface",Context.MODE_PRIVATE);
            if(!ui.getBoolean("radar_top_v6",false))try{MobileConfig.migrateRadarMargin(MobileConfig.file(mDataRoot));ui.edit().putBoolean("radar_top_v6",true).apply();}catch(IOException e){Log.w(TAG,"Minimap preference migration deferred",e);}
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
        refreshCrash();
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
    private View buildLayout() {
        android.widget.FrameLayout canvas=new android.widget.FrameLayout(this);
        LauncherArtView artwork=new LauncherArtView(this);canvas.addView(artwork,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24),dp(14),dp(24),dp(14));canvas.addView(root,new android.widget.FrameLayout.LayoutParams(-1,-1));
        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setGravity(Gravity.BOTTOM);
        details.addView(buildCrashCard(),matchWrap(dp(6)));
        mLastLaunch=ModernUi.text(this,"",13,0xffffb7a3);details.addView(mLastLaunch,matchWrap(dp(4)));
        mStatus=ModernUi.text(this,"Checking game files…",14,Color.BLACK);details.addView(mStatus,matchWrap(dp(8)));
        TextView brand=ModernUi.text(this,"XHYN IV",38,Color.BLACK);brand.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        details.addView(brand,matchWrap(dp(5)));
        root.addView(details,new LinearLayout.LayoutParams(-1,0,1));
        android.widget.HorizontalScrollView navigation=new android.widget.HorizontalScrollView(this);
        navigation.setHorizontalScrollBarEnabled(false);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        mPlay=ModernUi.button(this,"PLAY",true);mPlay.setOnClickListener(v->play(false));
        actions.addView(mPlay,new LinearLayout.LayoutParams(dp(140),dp(54)));
        addNavigation(actions,"Game files",this::showFilesPanel);
        addNavigation(actions,"Settings",this::showSettings);
        navigation.addView(actions);root.addView(navigation,matchWrap(dp(14)));
        navigation.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->artwork.setNavigationTop(top));
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(ModernUi.text(this,versionLine(),11,ModernUi.MUTED),new LinearLayout.LayoutParams(-2,-2));
        LinearLayout links=new LinearLayout(this);links.setOrientation(LinearLayout.HORIZONTAL);links.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        addFooterLink(links,"XHYN · GitHub ↗","https://github.com/XHYN-PH/XHYN-IV-recomp");
        addFooterLink(links,"YouTube ↗","https://youtube.com/@xhyn_ph");
        addFooterLink(links,"Telegram ↗","https://t.me/xhynports");
        android.widget.HorizontalScrollView linkStrip=new android.widget.HorizontalScrollView(this);linkStrip.setHorizontalScrollBarEnabled(false);linkStrip.setFillViewport(true);linkStrip.addView(links);
        // Keep every link on one line even in a small multi-window viewport.
        footer.addView(linkStrip,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(footer,matchWrap(dp(6)));
        buildSecondaryPanels();return canvas;
    }
    private void addFooterLink(LinearLayout links,String label,String url){
        TextView link=ModernUi.text(this,label,12,ModernUi.ACCENT);link.setPadding(dp(10),dp(7),dp(3),dp(7));link.setMinHeight(dp(32));link.setGravity(Gravity.CENTER);link.setSingleLine(true);
        link.setContentDescription(label+", "+url);
        link.setFocusable(true);link.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){toast(url);}});links.addView(link);
    }
    private void addNavigation(LinearLayout row,String title,Runnable run) {
        Button b=ModernUi.button(this,title,false);b.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.leftMargin=dp(8);row.addView(b,lp);
    }
    private void buildSecondaryPanels() {
        LinearLayout drivers=new LinearLayout(this);drivers.setOrientation(LinearLayout.VERTICAL);
        drivers.addView(ModernUi.text(this,"Applies next time you play.",13,ModernUi.MUTED));
        mDrivers=new RadioGroup(this);mDrivers.setOnCheckedChangeListener((group,id)->onDriverChecked(id));drivers.addView(mDrivers);
        mDriverNote=ModernUi.text(this,"",13,ModernUi.MUTED);drivers.addView(mDriverNote);
        ModernUi.action(this,drivers,"Import driver ZIP",this::pickDriverZip);
        mRemove=ModernUi.button(this,"Remove selected imported driver",false);mRemove.setOnClickListener(v->removeSelectedDriver());drivers.addView(mRemove,matchWrap(dp(10)));
        mDriverPanel=drivers;
        LinearLayout files=new LinearLayout(this);files.setOrientation(LinearLayout.VERTICAL);files.addView(buildInstallCard());
        ModernUi.action(this,files,"Save data",this::showSaveData);
        mRightScroll=new ScrollView(this);mRightScroll.setFillViewport(true);mRightScroll.addView(files);mFilesPanel=mRightScroll;
    }
    private void showDriverPanel(){showPanel("Graphics driver",mDriverPanel);}
    private void showFilesPanel(){showPanel("Game files & saves",mFilesPanel);}
    private void showPanel(String title,View panel){
        if(panel.getParent() instanceof ViewGroup)((ViewGroup)panel.getParent()).removeView(panel);
        ModernUi.dialog(this,title,panel).show();
    }
    private void showSettings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        ModernUi.action(this,panel,"Graphics & performance",this::showPerformanceTestSettings);
        ModernUi.action(this,panel,"Controls",this::showTouchSettings);
        ModernUi.action(this,panel,"Graphics driver",this::showDriverPanel);
        ModernUi.action(this,panel,"Logs & troubleshooting",this::showLogs);
        ModernUi.dialog(this,"Settings",panel).show();
    }
    private void showLogs(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(ModernUi.text(this,"Play with logs records a report you can share after a problem. Old logs are cleared when a new game session starts.",14,ModernUi.MUTED));
        ModernUi.action(this,panel,"Play with logs",()->{mDebugMode=0;play(true);});
        ModernUi.action(this,panel,"Share latest log",()->exportLatestLog());
        ModernUi.action(this,panel,"Delete saved logs",()->{
            if(gameRunning()){toast("Quit the game before deleting its log.");return;}
            new Thread(()->{String result;try{DebugLogArchive.clear(mDataRoot,getFilesDir());result="Logs deleted.";}catch(IOException e){result="Could not delete logs: "+e.getMessage();}
                String message=result;mMain.post(()->toast(message));},"DeleteLogs").start();
        });
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        ModernUi.action(this,panel,"Advanced troubleshooting",()->advanced.setVisibility(advanced.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        advanced.addView(ModernUi.text(this,"Use this when asked for a deeper report. Short timing and shadow captures are available from the in-game menu. Captures temporarily add overhead.",13,ModernUi.MUTED));
        ModernUi.action(this,advanced,"Play with detailed diagnostics",()->{mDebugMode=3;play(true);});
        panel.addView(advanced);ModernUi.dialog(this,"Logs",panel).show();
    }
    private void exportLatestLog(){
        String[] sessions=DebugLogArchive.sessions(mDataRoot,getFilesDir());
        if(sessions.length==0){toast("Use Play with logs first.");return;}
        mLogSession=sessions[0];Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/zip");save.putExtra(Intent.EXTRA_TITLE,"XHYN-IV-"+mLogSession+".zip");
        try{startActivityForResult(save,REQUEST_DEBUG_EXPORT);}catch(Exception e){toast("No file picker is available.");}
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("log_session",mLogSession);super.onSaveInstanceState(state);
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
    private void showSaveData() {
        if(mBusy)return;
        if(gameRunning()){toast("Quit the game before managing save data.");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Save data")
            .setItems(new String[]{"Export saves","Import saves","Delete save data"},(dialog,which)->{
                if(which==2) {
                    new android.app.AlertDialog.Builder(this).setTitle("Delete save data?")
                        .setMessage("This removes local saves and player profiles, including the previous import backup. Game files and graphics settings are kept. Export your saves first if you want to keep them.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                            if(gameRunning()){toast("Quit the game first.");return;}
                            setBusy(true,"Deleting save data...");
                            new Thread(()->{
                                String result;
                                try{File root=SaveData.root(mDataRoot);SaveData.deleteTree(root);SaveData.deleteTree(new File(root.getParentFile(),"saves-before-import"));result="Save data deleted.";}
                                catch(IOException error){result="Delete failed: "+error.getMessage();}
                                final String message=result;mMain.post(()->{setBusy(false,null);toast(message);});
                            },"DeleteSaves").start();
                        }).show();return;
                }
                if(which==1) {
                    new android.app.AlertDialog.Builder(this).setTitle("Import saves?")
                        .setMessage("Choose a save ZIP exported by XHYN IV. Current saves are kept in a local backup before replacement. Any previous import backup is replaced. Quit the game first.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Choose ZIP",(d,w)->pickSaveDocument(false)).show();
                }else pickSaveDocument(true);
            }).show();
    }
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
        File game = new File(mDataRoot, "LibertyRecomp/game");
        String[] installed = game.list();
        // The installer's own check once it is loaded; before that, a quick look.
        boolean installedNow = mInstallerReady ? mInstalled
                : installed != null && installed.length > 0;
        if (installedNow) {
            mStatus.setText("Game installed. Press Play.");
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
        String current = currentDriverMode();
        mDrivers.setOnCheckedChangeListener(null);
        mDrivers.clearCheck();
        mDrivers.removeAllViews();
        mDriverModes.clear();
        String renderer=GpuInfo.renderer();
        addDriver(DriverBridge.AUTO,"Automatic (recommended)","Turnip for supported Adreno GPUs; system driver otherwise");
        for(BundledDrivers.Bundle bundle:BundledDrivers.ALL.values()) {
            if(DriverPolicy.supports(bundle.mode,renderer,Build.VERSION.SDK_INT))
                addDriver(bundle.mode,bundle.label,bundle.mode.equals("turnip") ? "For Adreno 6xx/7xx" :
                    "Optional community driver; compatibility needs device testing");
        }
        addDriver(DriverBridge.SYSTEM, "System driver",
                "The device's own Vulkan driver (needs Vulkan 1.2)");
        File[] packages = customRoot().listFiles();
        if (packages != null) {
            Arrays.sort(packages);
            for (File dir : packages) {
                if (!dir.isDirectory() || dir.getName().startsWith(".") || !hasLibrary(dir)) continue;
                JSONObject meta = readMeta(dir);
                String name = meta != null ? meta.optString("name", dir.getName()) : dir.getName();
                String details = meta == null ? "Imported driver" : join(
                        meta.optString("description"), meta.optString("driverVersion"),
                        meta.optString("author").isEmpty() ? "" : "by " + meta.optString("author"));
                addDriver(DriverBridge.CUSTOM_PREFIX + dir.getName(), name,
                        details.isEmpty() ? "Imported driver" : details);
            }
        }
        int index = mDriverModes.indexOf(current);
        if (index < 0) index = 0;
        mDrivers.check(index + 1);
        mDrivers.setOnCheckedChangeListener((group, id) -> onDriverChecked(id));
        updateDriverNote(index);
    }

    private void addDriver(String mode, String name, String details) {
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
        mDrivers.addView(button, new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void onDriverChecked(int id) {
        int index = id - 1;
        if (index < 0 || index >= mDriverModes.size()) return;
        writeDriverMode(mDriverModes.get(index));
        updateDriverNote(index);
    }

    private void updateDriverNote(int index) {
        String mode = index >= 0 && index < mDriverModes.size() ? mDriverModes.get(index) : "";
        boolean custom = mode.startsWith(DriverBridge.CUSTOM_PREFIX);
        mRemove.setEnabled(custom);
        mRemove.setAlpha(custom ? 1f : 0.4f);
        mDriverNote.setText(GpuInfo.renderer().isEmpty() ? "GPU could not be identified; use System or import a compatible driver." : GpuInfo.renderer());
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

    private void writeDriverMode(String mode) {
        try (OutputStream out = new FileOutputStream(new File(mDataRoot, "driver.txt"))) {
            out.write((mode + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e(TAG, "writing driver.txt failed", e);
            toast("Could not save the driver choice: " + e.getMessage());
        }
    }

    private void removeSelectedDriver() {
        int index = mDrivers.getCheckedRadioButtonId() - 1;
        if (index < 0 || index >= mDriverModes.size()) return;
        String mode = mDriverModes.get(index);
        if (!mode.startsWith(DriverBridge.CUSTOM_PREFIX)) return;
        File dir = new File(customRoot(), mode.substring(DriverBridge.CUSTOM_PREFIX.length()));
        deleteRecursively(dir);
        writeDriverMode(DriverBridge.AUTO);
        refreshDrivers();
        toast("Driver removed. Using automatic selection.");
    }

    @SuppressWarnings("deprecation")
    private void pickDriverZip() {
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
        if(requestCode==REQUEST_SAVE_EXPORT||requestCode==REQUEST_SAVE_IMPORT) {
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            if(gameRunning()){toast("Quit the game before managing saves.");return;}
            final Uri uri=data.getData();setBusy(true,requestCode==REQUEST_SAVE_EXPORT?"Exporting saves...":"Importing saves...");
            new Thread(()->{
                String result;
                try {
                    File root=SaveData.root(mDataRoot);
                    if(requestCode==REQUEST_SAVE_EXPORT)try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)) {
                        if(out==null)throw new IOException("Cannot open export file");SaveData.exportZip(root,out);
                    }else try(java.io.InputStream in=getContentResolver().openInputStream(uri)) {
                        if(in==null)throw new IOException("Cannot open save ZIP");SaveData.importZip(root,in);
                    }
                    result=requestCode==REQUEST_SAVE_EXPORT?"Save data exported.":"Save data imported. Previous saves were kept locally.";
                }catch(IOException|RuntimeException error){result="Save operation failed: "+error.getMessage();}
                final String message=result;mMain.post(()->{setBusy(false,null);toast(message);});
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
                if (finalMode != null) writeDriverMode(finalMode);
                refreshDrivers();
                toast(finalMessage);
            });
        }, "DriverImport").start();
    }

    /** Unpacks a driver zip into files/drivers/NAME/ and returns "custom:NAME". */
    private String importDriver(Uri uri) throws IOException {
        File staging = new File(customRoot(), ".import");
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IOException("cannot create " + staging);
        long total = 0;
        List<String> libraries = new ArrayList<>();
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("the file could not be opened");
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                ZipEntry entry;
                byte[] buffer = new byte[1 << 16];
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    // Packages keep their files at the root or in one folder;
                    // only the file names matter.
                    String name = entry.getName().replace('\\', '/');
                    name = name.substring(name.lastIndexOf('/') + 1);
                    if (name.isEmpty() || name.startsWith(".") || name.equals("..")) continue;
                    File out = new File(staging, name);
                    try (OutputStream output = new FileOutputStream(out)) {
                        int read;
                        while ((read = zip.read(buffer)) > 0) {
                            total += read;
                            if (total > MAX_DRIVER_BYTES) throw new IOException("the zip is too large");
                            output.write(buffer, 0, read);
                        }
                    }
                    if (name.endsWith(".so")) libraries.add(name);
                }
            }
        } catch (IOException e) {
            deleteRecursively(staging);
            throw e;
        }
        if (libraries.isEmpty()) {
            deleteRecursively(staging);
            throw new IOException("no driver library (.so) in the zip");
        }
        JSONObject meta = readMeta(staging);
        String library = meta != null ? meta.optString("libraryName", "") : "";
        if (!library.isEmpty() && !new File(staging, library).isFile()) {
            deleteRecursively(staging);
            throw new IOException("meta.json names " + library + ", which is not in the zip");
        }
        if (library.isEmpty() && libraries.size() != 1) {
            deleteRecursively(staging);
            throw new IOException("several .so files and no meta.json saying which is the driver");
        }
        String base = meta != null && !meta.optString("name").isEmpty()
                ? meta.optString("name") : displayName(uri);
        String name = sanitize(base);
        File target = new File(customRoot(), name);
        deleteRecursively(target);
        if (!staging.renameTo(target)) {
            deleteRecursively(staging);
            throw new IOException("cannot move the driver into place");
        }
        Log.i(TAG, "imported driver " + name + " (" + total + " bytes)");
        return DriverBridge.CUSTOM_PREFIX + name;
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
        if (!meta.isFile()) return null;
        try (InputStream in = new FileInputStream(meta)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) bytes.write(buffer, 0, read);
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
        android.content.SharedPreferences perf=perfTestPreferences();
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(ModernUi.text(this,"Graphics and optional phone performance controls. Compare the same route when testing changes.",14,ModernUi.MUTED));
        ModernUi.action(this,panel,"Graphics settings",()->{
            if(gameRunning()){toast("Use Graphics from the in-game menu while playing.");return;}MobileSettingsUi.graphics(this,mDataRoot,null);
        });
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);advanced.setVisibility(View.GONE);
        ModernUi.action(this,panel,"Advanced phone options",()->advanced.setVisibility(advanced.getVisibility()==View.GONE?View.VISIBLE:View.GONE));
        android.widget.CheckBox sustained=styledCheck("Sustained performance","Ask Android for steadier long-session performance, where supported.",perf.getBoolean(PERF_SUSTAINED,false));advanced.addView(sustained,matchWrap(dp(12)));
        android.widget.CheckBox guard=styledCheck("Thermal guard","Temporarily reduce draw distance and back off optional GPU turbo when thermal pressure rises.",perf.getBoolean(PERF_THERMAL_GUARD,false));advanced.addView(guard,matchWrap(dp(6)));
        android.widget.CheckBox turbo=styledCheck("Prefer peak GPU speed","Adreno only. Higher heat and battery use; may reduce sustained performance.",perf.getBoolean(PERF_GPU_TURBO,false));turbo.setEnabled(DriverPolicy.supports("turnip",GpuInfo.renderer(),Build.VERSION.SDK_INT));advanced.addView(turbo,matchWrap(dp(8)));
        android.widget.CheckBox safe=styledCheck("Compatibility mode","Disable optional speed shortcuts for troubleshooting.",perf.getBoolean(PERF_SAFE_MODE,false));advanced.addView(safe,matchWrap(dp(6)));panel.addView(advanced);
        Runnable save=()->perf.edit().putBoolean(PERF_SUSTAINED,sustained.isChecked()).putBoolean(PERF_THERMAL_GUARD,guard.isChecked()).putBoolean(PERF_GPU_TURBO,turbo.isChecked()).putBoolean(PERF_SAFE_MODE,safe.isChecked()).apply();
        final android.app.AlertDialog[] dialog={null};
        dialog[0]=ModernUi.dialog(this,"Graphics & performance",panel,"Save changes",()->{save.run();dialog[0].dismiss();});dialog[0].show();
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
                intent.putExtra(LibertyActivity.EXTRA_GPU_TURBO, perf.getBoolean(PERF_GPU_TURBO, false));
                intent.putExtra(LibertyActivity.EXTRA_SUSTAINED, perf.getBoolean(PERF_SUSTAINED, false));
                intent.putExtra(LibertyActivity.EXTRA_THERMAL_GUARD,
                        perf.getBoolean(PERF_THERMAL_GUARD, false));
                intent.putExtra(LibertyActivity.EXTRA_SAFE_MODE, perf.getBoolean(PERF_SAFE_MODE, false));
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

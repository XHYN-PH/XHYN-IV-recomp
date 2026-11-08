package com.xhyn.stock;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Android-only tools. The game Activity is never subclassed or replaced. */
public class PanelActivity extends Activity {
    interface Job<T> { T run() throws Exception; }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    LinearLayout root,body; TextView status;
    String page,pendingAction="",pendingSlot=""; boolean dirty,busy;
    Runnable discard=()->{}; ControllerPage controller;
    static void open(Activity a,String page) { a.startActivity(new Intent(a,PanelActivity.class).putExtra("page",page)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); page=getIntent().getStringExtra("page");if(page==null)page="session";
        if(state!=null) { pendingAction=state.getString("action","");pendingSlot=state.getString("slot",""); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);
        render();Navigation.install(this,getWindow(),false,null);
    }
    void shell(String title) {
        root=Ui.screen(this,title,this::onBackPressed);body=Ui.column(this);
        status=Ui.text(this,"",12,Ui.MUTED);status.setVisibility(View.GONE);root.addView(status);
        root.addView(Ui.scroll(this,body),new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    void render() {
        dirty=false;discard=()->{};
        try {
            switch(page) {
                case "settings":new SettingsPage(this).show();break;
                case "touch":new TouchPage(this).show();break;
                case "overlay":new OverlayPage(this).show();break;
                case "overlay-editor":new OverlayEditor(this).show();break;
                case "drivers":new DriversPage(this).show();break;
                case "saves":new SavesPage(this).show();break;
                case "mods":new ModsPage(this).show();break;
                case "achievements":new AchievementsPage(this).show();break;
                case "controllers":controller=new ControllerPage(this);controller.show();break;
                case "logs":new LogsPage(this).show();break;
                default:session();break;
            }
        } catch(Exception|LinkageError e) { shell("Tool unavailable");Ui.add(body,Ui.text(this,FilesEx.error(e),14,Ui.TEXT)); }
        root.post(()->Ui.focusFirst(root));
    }
    void session() {
        shell("Session");
        Button resume=Ui.button(this,"Return to game",true);Ui.add(body,resume);resume.setOnClickListener(v->finish());
        String[][] choices={{"Settings for next launch","settings"},{"Monitoring overlay","overlay"},{"Controller & navigation","controllers"},{"Achievements","achievements"},{"Logs & diagnostics","logs"},{"Exit game","exit"}};
        LinearLayout row=null;
        for(int i=0;i<choices.length;i++) {
            if(i%2==0){row=new LinearLayout(this);Ui.add(body,row);}final String page=choices[i][1];Button button=Ui.button(this,choices[i][0],false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i%2==1)p.leftMargin=Ui.dp(this,6);row.addView(button,p);
            button.setOnClickListener(v->{if(page.equals("exit"))exitGame();else open(this,page);});
        }
    }
    private void exitGame() {
        Ui.dialog(this,"Exit game?","Save your progress in the game before exiting.","Exit",()->{
            Activity game=LayerApplication.game.get();if(game!=null)game.finish();finish();
        });
    }
    <T> void job(String label,Job<T> task,Consumer<T> done) {
        if(busy)return;busy=true;if(status!=null){status.setText(label+"…");status.setVisibility(View.VISIBLE);}
        worker.execute(()->{
            T result=null;Throwable failure=null;
            try { result=task.run(); } catch(Exception|LinkageError e){failure=e;}
            final T value=result;final Throwable error=failure;
            runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;
                if(status!=null)status.setVisibility(View.GONE);
                if(error!=null)Ui.error(this,error);else if(done!=null)done.accept(value);
            });
        });
    }
    <T> T files(Job<T> task) throws Exception {
        File folder=StockApi.layer(this);Files.createDirectories(folder.toPath());
        try(RandomAccessFile file=new RandomAccessFile(new File(folder,"files.lock"),"rw");FileLock lock=file.getChannel().tryLock()) {
            if(lock==null)throw new IOException("Another file operation is running.");return task.run();
        }
    }
    void choose(String action) {
        if(busy)return;pendingAction=action;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});
        try{startActivityForResult(intent,71);}catch(ActivityNotFoundException e){Ui.error(this,e);pendingAction="";}
    }
    void export(String action,String name) {
        if(busy)return;pendingAction=action;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(intent,71);}catch(ActivityNotFoundException e){Ui.error(this,e);pendingAction="";}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=71)return;
        String action=pendingAction,slot=pendingSlot;pendingAction="";
        if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        job("Working with selected ZIP",()->files(()->{
            if(action.startsWith("save-")||action.startsWith("slot-"))SaveRecovery.recover(this);
            switch(action) {
                case "driver-import":StockApi.offline(this);try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open driver ZIP.");DriverStore.importZip(this,in);}break;
                case "save-import":StockApi.offline(this);try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open save ZIP.");SaveRecovery.importZip(this,StockApi.saves(this),in);}break;
                case "slot-import":StockApi.offline(this);try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open slot ZIP.");SaveData.importSlot(StockApi.saves(this),slot,in);}break;
                case "save-export":StockApi.offline(this);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot write backup.");SaveData.exportZip(StockApi.saves(this),out);}break;
                case "slot-export":StockApi.offline(this);try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot write slot backup.");SaveData.exportSlot(StockApi.saves(this),slot,out);}break;
                case "logs-export":try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot write diagnostics.");Diagnostics.write(this,out,slot);}break;
                default:throw new IOException("The file operation expired. Select the action again.");
            }
            return true;
        }),ok->{render();Toast.makeText(this,"Completed",Toast.LENGTH_SHORT).show();});
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("action",pendingAction);state.putString("slot",pendingSlot);super.onSaveInstanceState(state);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);if(root!=null)root.requestApplyInsets();
    }
    @Override public void onBackPressed() {
        if(busy){Toast.makeText(this,"Wait for the file operation to finish.",Toast.LENGTH_SHORT).show();return;}
        if(dirty) { Ui.dialog(this,"Discard changes?","Your saved settings will be kept.","Discard",()->{discard.run();dirty=false;finish();}); }
        else { discard.run();super.onBackPressed(); }
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if(controller!=null && controller.motion(e))return true;return super.dispatchGenericMotionEvent(e);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(controller!=null&&controller.key(e))return true;return super.dispatchKeyEvent(e);
    }
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}

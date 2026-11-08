package com.xhyn.stock;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.io.*;
import java.util.*;

/** Hooks Android lifecycle only. The original launcher, SDL and native engine retain ownership. */
public final class LayerApplication extends Application implements Application.ActivityLifecycleCallbacks {
    static WeakReference<Activity> game=new WeakReference<>(null);
    private final Map<Activity,HomeScreen> homes=new WeakHashMap<>();
    private final Map<Activity,MonitoringHud> huds=new WeakHashMap<>();
    private String migrationError;
    @Override public void onCreate() {
        super.onCreate(); registerActivityLifecycleCallbacks(this);
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try { StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
                String value=trace.toString(); if(value.length()>131072)value=value.substring(0,131072);
                FilesEx.atomic(new File(StockApi.layer(this),"last-java-crash.txt"),new Date()+"\n"+value);
            } catch(Exception ignored) {}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    @Override public void onActivityPreCreated(Activity a,Bundle state) {
        if(a.getClass().getName().equals(StockApi.LAUNCHER))try{FirstRunMigration.run(a);}catch(IOException e){migrationError=FilesEx.error(e);Log.e("XhynLayer","Settings migration",e);}
        if(a.getClass().getName().equals(StockApi.GAME))try{NextSettings.activate(a);}catch(IOException e){throw new IllegalStateException("Could not apply queued settings: "+FilesEx.error(e),e);}
    }
    @Override public void onActivityPostCreated(Activity a,Bundle state) {
        try {
            if(a.getClass().getName().equals(StockApi.LAUNCHER)) {
                HomeScreen home=new HomeScreen(a,migrationError); homes.put(a,home); home.show(); Navigation.install(a,a.getWindow(),false,null);
            } else if(a.getClass().getName().equals(StockApi.GAME)) {
                game=new WeakReference<>(a);
                MonitoringHud hud=new MonitoringHud(a); huds.put(a,hud); hud.attach();
                Navigation.install(a,a.getWindow(),true,()->openSession(a));
            }
        } catch(RuntimeException e) { HomeScreen home=homes.remove(a);if(home!=null)home.restoreOriginal();Log.e("XhynLayer","Could not attach Android UI; keeping the stock screen",e); }
    }
    static void openSession(Activity a) {
        if(a.isFinishing()||a.isDestroyed())return;
        a.startActivity(new Intent(a,SessionActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    @Override public void onActivityPostResumed(Activity a) {
        MonitoringHud hud=huds.get(a); if(hud!=null)hud.start(); HomeScreen home=homes.get(a); if(home!=null)home.refresh();
    }
    @Override public void onActivityPaused(Activity a) { MonitoringHud hud=huds.get(a); if(hud!=null)hud.stop(); }
    @Override public void onActivityDestroyed(Activity a) {
        MonitoringHud hud=huds.remove(a); if(hud!=null)hud.stop(); homes.remove(a);
        if(game.get()==a)game.clear();
    }
    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityResumed(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
}

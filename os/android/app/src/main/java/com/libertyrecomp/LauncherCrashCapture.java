package com.libertyrecomp;

import java.io.File;
import java.io.IOException;

final class LauncherCrashCapture {
    private static boolean installed;
    static synchronized void install(File data){
        if(installed)return;installed=true;
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{LauncherCrashReport.save(data,error);}catch(IOException|RuntimeException ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
            else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
        });
    }
    private LauncherCrashCapture(){}
}

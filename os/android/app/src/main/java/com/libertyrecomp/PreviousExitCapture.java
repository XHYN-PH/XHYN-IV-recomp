package com.libertyrecomp;
import android.app.*;
import android.os.Build;
import java.io.*;
import java.util.*;
/** Own-process historical exit information; never guesses that a clean quit was a crash. */
final class PreviousExitCapture {
 static void capture(Activity a,File data){if(Build.VERSION.SDK_INT<30)return;new Thread(()->{try{ActivityManager m=(ActivityManager)a.getSystemService(Activity.ACTIVITY_SERVICE);if(m==null)return;for(ApplicationExitInfo e:m.getHistoricalProcessExitReasons(a.getPackageName(),0,16)){if(!e.getProcessName().equals(a.getPackageName()+":game"))continue;Properties p=new Properties();p.setProperty("timestamp",Long.toString(e.getTimestamp()));p.setProperty("reason",Integer.toString(e.getReason()));p.setProperty("reason.label",label(e.getReason()));p.setProperty("status",Integer.toString(e.getStatus()));p.setProperty("description",String.valueOf(e.getDescription()));p.setProperty("pss.kb",Long.toString(e.getPss()));p.setProperty("importance",Integer.toString(e.getImportance()));p.setProperty("process",e.getProcessName());SessionHistory.save(data,"previous-exit",p);break;}}catch(Exception ignored){}},"PreviousGameExit").start();}
 static String label(int reason){String[] names={"Unknown","Self exit","Signal","Low memory","Java crash","Native crash","ANR","Initialization failure","Permission change","Excessive resource use","User requested","User stopped","Dependency died","Other","Frozen process","Package state change","Package updated"};return reason>=0&&reason<names.length?names[reason]:"Unknown";}
 private PreviousExitCapture(){}
}

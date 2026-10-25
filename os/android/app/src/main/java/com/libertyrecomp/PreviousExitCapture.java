package com.libertyrecomp;
import android.app.*;
import android.os.Build;
import java.io.*;
import java.util.*;
/** Own-process historical exit information; never guesses that a clean quit was a crash. */
final class PreviousExitCapture {
 static void capture(Activity a,File data){
  if(Build.VERSION.SDK_INT<30)return;
  new Thread(()->{try{
   ActivityManager manager=(ActivityManager)a.getSystemService(Activity.ACTIVITY_SERVICE);if(manager==null)return;
   Set<String> captured=new HashSet<>();
   for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(a.getPackageName(),0,16)){
    String process=exit.getProcessName();String id=process.equals(a.getPackageName())?"previous-launcher-exit":process.equals(a.getPackageName()+":game")?"previous-exit":null;
    if(id==null||!captured.add(id))continue;
    Properties p=new Properties();p.setProperty("timestamp",Long.toString(exit.getTimestamp()));p.setProperty("reason",Integer.toString(exit.getReason()));
    p.setProperty("reason.label",label(exit.getReason()));p.setProperty("status",Integer.toString(exit.getStatus()));p.setProperty("description",String.valueOf(exit.getDescription()));
    p.setProperty("pss.kb",Long.toString(exit.getPss()));p.setProperty("importance",Integer.toString(exit.getImportance()));p.setProperty("process",process);
    SessionHistory.save(data,id,p);if(captured.size()==2)break;
   }
  }catch(Exception ignored){}},"PreviousAppExit").start();
 }
 static String label(int reason){String[] names={"Unknown","Self exit","Signal","Low memory","Java crash","Native crash","ANR","Initialization failure","Permission change","Excessive resource use","User requested","User stopped","Dependency died","Other","Frozen process","Package state change","Package updated"};return reason>=0&&reason<names.length?names[reason]:"Unknown";}
 private PreviousExitCapture(){}
}

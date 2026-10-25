package com.libertyrecomp;
import android.app.*;
import java.io.*;
import java.util.*;
/** Restart-only edits live separately so a running engine cannot overwrite them. */
final class PendingSettings {
 static File file(File data){return NextLaunchConfig.file(data);}
 static Map<String,String> read(File data)throws IOException{return NextLaunchConfig.read(data);}
 static boolean pending(File data){try{return !read(data).isEmpty();}catch(IOException e){return true;}}
 static void save(File data,Map<String,String> values)throws IOException{NextLaunchConfig.save(data,values);}
 static void activate(File data)throws IOException{NextLaunchConfig.activate(data);}

 static void prompt(Activity a,File data,boolean playing){ModernUi.builder(a).setTitle("Settings saved — restart required").setMessage(playing?"Restart the app to apply these options. Game progress since your last save will be lost if you quit now.":"Restart the app to apply these options. You can also apply them the next time you press Play.").setPositiveButton("Quit now",(d,w)->{if(a instanceof LibertyActivity)((LibertyActivity)a).quitForRestart();else a.finishAffinity();}).setNegativeButton("Later",null).show();}
 private PendingSettings(){}
}

package com.libertyrecomp;
import android.app.Activity;
import android.widget.*;
import java.io.File;
/** Read-only presence check; never edits archives or mistakes DLC for required data. */
final class GameDataCheck {
 static void show(Activity a,File data){
  File game=new File(Installer.installRoot(data),"game");LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  body.addView(ModernUi.text(a,"Checks core entry files and folders. This does not verify every asset inside an archive or the Title Update version.",13,ModernUi.MUTED));
  int missing=0;
  for(String name:new String[]{"default.xex","common.rpf","xbox360.rpf","audio.rpf"}){File file=new File(game,name);boolean ok=file.isFile()&&file.length()>0;if(!ok)missing++;body.addView(ModernUi.text(a,(ok?"✓  ":"Missing: ")+name,14,ok?ModernUi.TEXT:0xffffb7a3));}
  for(String name:new String[]{"audio","common","xbox360"}){boolean ok=new File(game,name).isDirectory();if(!ok)missing++;body.addView(ModernUi.text(a,(ok?"✓  ":"Missing folder: ")+name,14,ok?ModernUi.TEXT:0xffffb7a3));}
  body.addView(ModernUi.text(a,missing==0?"Core entries found":"Missing core entries: "+missing,15,missing==0?ModernUi.ACCENT:0xffffb7a3));
  body.addView(ModernUi.text(a,"Optional episodes",12,ModernUi.ACCENT));
  for(String name:new String[]{"TLAD","TBOGT"})body.addView(ModernUi.text(a,name+": "+(new File(Installer.installRoot(data),"dlc/"+name).isDirectory()?"folder present; game validates its contents":"not installed"),13,ModernUi.MUTED));
  ModernUi.dialog(a,"Game file check",body).show();
 }
 private GameDataCheck(){}
}

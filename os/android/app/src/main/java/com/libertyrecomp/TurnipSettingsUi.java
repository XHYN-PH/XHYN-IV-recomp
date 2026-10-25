package com.libertyrecomp;
import android.app.*;
import android.widget.*;
import java.io.*;
import java.util.*;
final class TurnipSettingsUi {
 static String selected(File data){String mode=DriverState.readChoice(data);return mode.equals("auto")?DriverPolicy.automatic(GpuInfo.renderer(),android.os.Build.VERSION.SDK_INT):mode;}
 static File file(File data){return DriverState.profile(data,selected(data));}
 static Map<String,String> values(File data)throws IOException{String mode=selected(data);DriverState.migrateLegacy(data,mode);return DriverState.options(data,mode);}
 static Map<String,String> environment(File data)throws IOException{return environment(data,selected(data));}
 static Map<String,String> environment(File data,String mode)throws IOException{DriverState.migrateLegacy(data,mode);Map<String,String> v=DriverState.options(data,mode),out=new LinkedHashMap<>();String rendering=v.getOrDefault("rendering","launcher");if(rendering.equals("auto"))out.put("TU_DEBUG","");if(rendering.equals("sysmem")||rendering.equals("gmem"))out.put("TU_DEBUG",rendering);if(Arrays.asList("true","false").contains(v.get("disk_cache")))out.put("MESA_SHADER_CACHE_DISABLE",Boolean.toString(!Boolean.parseBoolean(v.get("disk_cache"))));if(Arrays.asList("128M","256M","512M","1G","2G").contains(v.get("cache_size")))out.put("MESA_SHADER_CACHE_MAX_SIZE",v.get("cache_size"));String layout=v.getOrDefault("cache_layout","default");if(layout.equals("database")||layout.equals("files")){out.put("MESA_DISK_CACHE_SINGLE_FILE","false");out.put("MESA_DISK_CACHE_DATABASE",Boolean.toString(layout.equals("database")));out.put("MESA_DISK_CACHE_MULTI_FILE",Boolean.toString(layout.equals("files")));}return out;}
 static void show(Activity a,File data,boolean playing){
  if(playing){Toast.makeText(a,"Quit the game before changing driver settings.",Toast.LENGTH_LONG).show();return;}
  final String mode=selected(data);
  if(!DriverBridge.isTurnip(data,mode)){Toast.makeText(a,"Select a Turnip driver to edit its options.",Toast.LENGTH_LONG).show();return;}
  final Map<String,String> draft;
  try{draft=new LinkedHashMap<>(values(data));}catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();return;}
  String[] keys={"rendering","disk_cache","cache_size","cache_layout","workers"};
  String[] labels={"Rendering policy","Driver disk shader cache","Driver cache limit","Cache storage layout","Graphics workers override"};
  String[] defaults={"launcher","true","256M","default","game"};
  String[][] values={{"launcher","auto","sysmem","gmem"},{"true","false"},{"128M","256M","512M","1G","2G"},{"default","database","files"},{"game","1","2","3","4","5"}};
  String[][] names={{"Launcher default","Automatic","System memory","Tile memory"},{"On","Off"},{"128 MB","256 MB","512 MB","1 GB","2 GB"},{"Driver default","Database (compact)","Individual files"},{"Use game setting","1","2","3","4","5"}};
  for(int i=0;i<keys.length;i++)draft.putIfAbsent(keys[i],defaults[i]);
  Map<String,String> original=new LinkedHashMap<>(draft);
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  body.addView(ModernUi.text(a,"Applies to the selected Turnip driver on the next launch. Driver shader cache and the game's pipeline cache are separate. Extra workers can prepare effects together but use more CPU and heat. Default cache layout keeps the driver's choice.",13,ModernUi.MUTED));
  Button[] buttons=new Button[keys.length];
  Runnable refresh=()->{for(int i=0;i<keys.length;i++){int n=Arrays.asList(values[i]).indexOf(draft.get(keys[i]));if(n<0){draft.put(keys[i],defaults[i]);n=Arrays.asList(values[i]).indexOf(defaults[i]);}buttons[i].setText(labels[i]+": "+(n<0?draft.get(keys[i]):names[i][n]));}};
  for(int n=0;n<keys.length;n++){
   final int i=n;Button button=ModernUi.button(a,"",false);button.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);buttons[i]=button;
   LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,ModernUi.dp(a,44));row.topMargin=ModernUi.dp(a,8);body.addView(button,row);
   button.setOnClickListener(v->ModernUi.choose(a,labels[i],names[i],Arrays.asList(values[i]).indexOf(draft.get(keys[i])),choice->{draft.put(keys[i],values[i][choice]);refresh.run();}));
  }
  ModernUi.action(a,body,"Reset driver options",()->ModernUi.builder(a).setTitle("Reset driver options?").setMessage("Select defaults for this driver. Save to apply.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{for(int i=0;i<keys.length;i++)draft.put(keys[i],defaults[i]);refresh.run();}).show());
  refresh.run();
  java.util.function.BooleanSupplier save=()->{try{DriverState.saveOptions(data,mode,draft);original.clear();original.putAll(draft);return true;}catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();return false;}};
  AlertDialog[] dialog={null};
  java.util.function.BooleanSupplier close=()->{
   if(original.equals(draft))return true;
   ModernUi.builder(a).setTitle("Unsaved driver options").setMessage("Save your changes before leaving?")
    .setPositiveButton("Save",(d,w)->{if(save.getAsBoolean())dialog[0].dismiss();})
    .setNegativeButton("Discard",(d,w)->dialog[0].dismiss()).setNeutralButton("Keep editing",null).show();return false;
  };
  ScrollView scroll=new ScrollView(a);scroll.addView(body);
  dialog[0]=ModernUi.screen(a,"Advanced Turnip",scroll,"Save",()->{if(save.getAsBoolean())Toast.makeText(a,"Driver options saved for next launch.",Toast.LENGTH_SHORT).show();},close);dialog[0].show();
 }
 private TurnipSettingsUi(){}
}

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
 static Map<String,String> environment(File data,String mode)throws IOException{DriverState.migrateLegacy(data,mode);Map<String,String> v=DriverState.options(data,mode),out=new LinkedHashMap<>();String rendering=v.getOrDefault("rendering","launcher");if(rendering.equals("auto"))out.put("TU_DEBUG","");if(rendering.equals("sysmem")||rendering.equals("gmem"))out.put("TU_DEBUG",rendering);if(v.containsKey("disk_cache"))out.put("MESA_SHADER_CACHE_DISABLE",Boolean.toString(!Boolean.parseBoolean(v.get("disk_cache"))));if(v.containsKey("cache_size"))out.put("MESA_SHADER_CACHE_MAX_SIZE",v.get("cache_size"));return out;}
 static void show(Activity a,File data,boolean playing){
  if(playing){Toast.makeText(a,"Quit the game before changing driver settings.",Toast.LENGTH_LONG).show();return;}
  final String mode=selected(data);
  if(!DriverBridge.isTurnip(data,mode)){Toast.makeText(a,"Select a Turnip driver to edit its options.",Toast.LENGTH_LONG).show();return;}
  final Map<String,String> v;try{v=values(data);}catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();return;}
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.addView(ModernUi.text(a,"Options for "+mode+" only. Applies next launch. Forced modes are requests; a source-forced policy can ignore them. env.txt keeps its final override. Driver shader cache and the game's pipeline cache are separate.",13,ModernUi.MUTED));
  String[] keys={"rendering","disk_cache","cache_size"},labels={"Rendering policy","Driver disk shader cache","Driver cache limit"};String[][] options={{"launcher","auto","sysmem","gmem"},{"true","false"},{"128M","256M","512M","1G"}};
  for(int n=0;n<keys.length;n++){final int i=n;Button b=ModernUi.button(a,labels[i]+": "+v.getOrDefault(keys[i],i==0?"launcher":i==1?"true":"256M"),false);body.addView(b,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,42)));b.setOnClickListener(x->ModernUi.choose(a,labels[i],options[i],Arrays.asList(options[i]).indexOf(v.get(keys[i])),choice->{v.put(keys[i],options[i][choice]);b.setText(labels[i]+": "+options[i][choice]);}));}
  ModernUi.action(a,body,"Reset driver options",()->{v.clear();v.put("rendering","launcher");v.put("disk_cache","true");v.put("cache_size","256M");Toast.makeText(a,"Defaults selected. Save to apply.",Toast.LENGTH_SHORT).show();});
  ModernUi.dialog(a,"Advanced Turnip",body,"Save",()->{try{DriverState.saveOptions(data,mode,v);Toast.makeText(a,"Driver options saved for next launch.",Toast.LENGTH_LONG).show();}catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}}).show();
 }
 private TurnipSettingsUi(){}
}

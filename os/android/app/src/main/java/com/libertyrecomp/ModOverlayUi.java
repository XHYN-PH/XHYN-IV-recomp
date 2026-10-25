package com.libertyrecomp;

import android.app.*;
import android.text.InputType;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class ModOverlayUi {
    private static final class Row {
        final ModOverlayPolicy.Mod mod;
        final boolean originalEnabled;
        final int originalPriority;
        final LinearLayout panel;
        final CheckBox enabled;
        final Button priority;
        final TextView state;
        Row(Activity a,ModOverlayPolicy.Mod mod){
            this.mod=mod;originalEnabled=mod.enabled;originalPriority=mod.priority;
            panel=ModernUi.panel(a);
            enabled=new CheckBox(a);enabled.setText(mod.folder.getName());enabled.setSingleLine(true);enabled.setEllipsize(android.text.TextUtils.TruncateAt.END);enabled.setMinHeight(ModernUi.dp(a,40));enabled.setTextColor(ModernUi.TEXT);enabled.setChecked(mod.enabled);
            enabled.setEnabled(mod.admitted()&&!Files.isSymbolicLink(mod.folder.toPath()));panel.addView(enabled);
            priority=ModernUi.button(a,"Priority: "+mod.priority,false);panel.addView(priority);
            state=ModernUi.text(a,"",12,ModernUi.MUTED);panel.addView(state);
        }
    }
    static void show(Activity a,File data,boolean playing){
        if(playing){Toast.makeText(a,"Quit the game before changing data-file mods.",Toast.LENGTH_LONG).show();return;}
        File installation=Installer.installRoot(data),root=new File(installation,"mods");
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(ModernUi.text(a,"Enable compatible Xbox 360 data mods, then save and start the game. Higher priority wins when files overlap.",14,ModernUi.TEXT));
        ModernUi.action(a,body,"How to add a mod",()->ModernUi.dialog(a,"Add a data mod",ModernUi.text(a,"Create one folder per mod:\nmods/MyMod/game/common/data/handling.dat\n\nKeep the original Xbox 360 file paths inside game/ or update/. PC plugins, scripts and texture/model archives do not work here.\n\nMods folder: "+root,13,ModernUi.TEXT)).show());
        CheckBox global=new CheckBox(a);global.setText("Enable data-file mods");global.setTextColor(ModernUi.TEXT);
        try{global.setChecked("true".equals(MobileConfig.read(MobileConfig.file(data)).get("data_file_mods")));}catch(IOException ignored){}
        body.addView(global);
        TextView scanning=ModernUi.text(a,"Scanning mod folders…",13,ModernUi.MUTED);body.addView(scanning);
        LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
        Button save=ModernUi.button(a,"Save for next launch",true);save.setEnabled(false);body.addView(save);
        boolean[] dirty={false},saving={false};AlertDialog[] active={null};
        AlertDialog dialog=ModernUi.navigable(a,()->{if(saving[0])return false;if(!dirty[0])return true;ModernUi.builder(a).setTitle("Unsaved mod changes").setMessage("Save changes before leaving?").setPositiveButton("Save",(d,w)->save.performClick()).setNegativeButton("Discard",(d,w)->active[0].dismiss()).setNeutralButton("Keep editing",null).show();return false;});active[0]=dialog;
        ScrollView scroll=new ScrollView(a);scroll.addView(body);LinearLayout page=ModernUi.panel(a);page.addView(ModernUi.text(a,"DATA-FILE MODS",18,ModernUi.TEXT));Button back=ModernUi.iconButton(a,"back","Back");page.addView(back,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,40)));back.setOnClickListener(v->dialog.onBackPressed());page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setView(page);dialog.setCanceledOnTouchOutside(false);dialog.setOnShowListener(v->{android.view.Window w=dialog.getWindow();if(w!=null){ModernUi.immersive(w);w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.min(ModernUi.dp(a,700),(int)(a.getResources().getDisplayMetrics().widthPixels*.94)),(int)(a.getResources().getDisplayMetrics().heightPixels*.9));ModernUi.focusFirst(page);}});dialog.show();global.setOnCheckedChangeListener((v,on)->dirty[0]=true);
        Thread scan=new Thread(()->{
            List<ModOverlayPolicy.Mod> mods=new ArrayList<>();Map<String,String> actual=new HashMap<>();String problem="";
            try{
                if(!Files.exists(root.toPath(),LinkOption.NOFOLLOW_LINKS))Files.createDirectories(root.toPath());
                mods=ModOverlayPolicy.scan(root);
            }catch(IOException e){problem=e.getMessage()==null?"Cannot read mod folders.":e.getMessage();}
            try{actual=ModOverlayPolicy.lastRun(installation);}catch(IOException ignored){}
            final List<ModOverlayPolicy.Mod> result=mods;final Map<String,String> loaded=actual;final String error=problem;
            a.runOnUiThread(()->{
                if(a.isFinishing()||a.isDestroyed()||!dialog.isShowing())return;
                scanning.setText(error.isEmpty()?(result.isEmpty()?"No mod folders found.":result.stream().filter(ModOverlayPolicy.Mod::admitted).count()+" compatible · "+result.stream().filter(m->!m.admitted()).count()+" need attention"):error);
                if(!loaded.isEmpty())body.addView(ModernUi.text(a,"Last launch report: "+new Date(new File(installation,ModOverlayPolicy.REPORT).lastModified()),12,ModernUi.ACCENT),body.indexOfChild(rows));
                List<Row> views=new ArrayList<>();
                for(ModOverlayPolicy.Mod mod:result)views.add(new Row(a,mod));
                Runnable refresh=()->{
                    ModOverlayPolicy.conflicts(result);
                    views.sort((x,y)->ModOverlayPolicy.ORDER.compare(x.mod,y.mod));rows.removeAllViews();
                    for(Row row:views){
                        String status=row.mod.admitted()?"Ready · "+row.mod.files.size()+" files":"Needs attention: "+row.mod.problem;
                        if(!row.mod.enabled)status="Disabled · "+status;
                        if(row.mod.conflictCount>0)status+="\nConflicts: "+row.mod.conflictCount+" (later mount wins)";
                        String last=loaded.get(ModOverlayPolicy.identity(row.mod.folder.getName()));
                        if(last!=null)status+="\nLast launch: "+last;
                        row.state.setText(status);row.priority.setText("Priority: "+row.mod.priority);ModernUi.optionSpacing(a,row.panel);LinearLayout.LayoutParams rowSpace=new LinearLayout.LayoutParams(-1,-2);rowSpace.topMargin=ModernUi.dp(a,8);rows.addView(row.panel,rowSpace);
                    }
                };
                for(Row row:views){
                    row.enabled.setOnCheckedChangeListener((button,checked)->{row.mod.enabled=checked;dirty[0]=true;refresh.run();row.enabled.post(()->row.enabled.requestFocus());});
                    row.priority.setEnabled(!Files.isSymbolicLink(row.mod.folder.toPath()));
                    row.priority.setOnClickListener(v->{
                        EditText input=new EditText(a);input.setText(Integer.toString(row.mod.priority));
                        input.setTextColor(ModernUi.TEXT);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
                        final AlertDialog[] edit={null};
                        edit[0]=ModernUi.dialog(a,"Priority (-1000 to 1000)",input,"Apply",()->{
                            try{row.mod.priority=ModOverlayPolicy.priority(input.getText().toString().trim());dirty[0]=true;refresh.run();edit[0].dismiss();}
                            catch(IOException e){input.setError(e.getMessage());}
                        });edit[0].show();
                    });
                    ModernUi.action(a,row.panel,"Files and conflicts",()->{
                        StringBuilder text=new StringBuilder();
                        if(!row.mod.problem.isEmpty())text.append(row.mod.problem).append("\n");
                        for(int i=0;i<Math.min(100,row.mod.files.size());i++)text.append(row.mod.files.get(i)).append("\n");
                        if(row.mod.files.size()>100)text.append("Showing the first 100 files.\n");
                        for(String conflict:row.mod.conflicts)text.append(conflict).append("\n");
                        if(text.length()==0)text.append("No supported files.");
                        ModernUi.dialog(a,row.mod.folder.getName(),ModernUi.text(a,text.toString(),13,ModernUi.TEXT)).show();
                    });
                }
                refresh.run();save.setEnabled(true);
                save.setOnClickListener(v->{
                    final boolean enableGlobal=global.isChecked();saving[0]=true;
                    List<ModOverlayPolicy.Mod> changes=new ArrayList<>();
                    for(Row row:views)if(row.mod.enabled!=row.originalEnabled||row.mod.priority!=row.originalPriority){
                        ModOverlayPolicy.Mod copy=new ModOverlayPolicy.Mod(row.mod.folder);copy.enabled=row.mod.enabled;copy.priority=row.mod.priority;changes.add(copy);
                    }
                    save.setEnabled(false);global.setEnabled(false);
                    for(Row row:views){row.enabled.setEnabled(false);row.priority.setEnabled(false);}
                    new Thread(()->{
                        StringBuilder failures=new StringBuilder();
                        for(ModOverlayPolicy.Mod mod:changes)try{ModOverlayPolicy.save(mod);}catch(IOException e){failures.append(mod.folder.getName()).append(": ").append(e.getMessage()).append("\n");}
                        try{MobileConfig.update(MobileConfig.file(data),Collections.singletonMap("data_file_mods",Boolean.toString(enableGlobal)));}
                        catch(IOException e){failures.append("Global setting: ").append(e.getMessage());}
                        a.runOnUiThread(()->{
                            if(a.isFinishing()||a.isDestroyed())return;
                            saving[0]=false;dirty[0]=failures.length()!=0;
                            if(dialog.isShowing()&&failures.length()==0)dialog.dismiss();else{save.setEnabled(true);global.setEnabled(true);for(Row row:views){row.enabled.setEnabled(row.mod.admitted());row.priority.setEnabled(!Files.isSymbolicLink(row.mod.folder.toPath()));}}
                            String message=failures.length()==0?"Mod settings saved for next launch.":"Some settings could not be saved:\n"+failures;
                            Toast.makeText(a,message,Toast.LENGTH_LONG).show();
                        });
                    },"xhyn-mod-save").start();
                });
            });
        },"xhyn-mod-scan");
        dialog.setOnDismissListener(v->scan.interrupt());scan.start();
    }
    private ModOverlayUi(){}
}

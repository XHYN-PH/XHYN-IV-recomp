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
            panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
            enabled=new CheckBox(a);enabled.setText(mod.folder.getName());enabled.setTextColor(ModernUi.TEXT);enabled.setChecked(mod.enabled);
            enabled.setEnabled(!Files.isSymbolicLink(mod.folder.toPath()));panel.addView(enabled);
            priority=ModernUi.button(a,"Priority: "+mod.priority,false);panel.addView(priority);
            state=ModernUi.text(a,"",12,ModernUi.MUTED);panel.addView(state);
        }
    }
    static void show(Activity a,File data,boolean playing){
        if(playing){Toast.makeText(a,"Quit the game before changing data-file mods.",Toast.LENGTH_LONG).show();return;}
        File installation=Installer.installRoot(data),root=new File(installation,"mods");
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(ModernUi.text(a,"Folder: "+root+"\nPlace Xbox 360 loose data under mods/<name>/game/ or update/. Higher priority loads later and wins; equal priorities use UTF-8 folder-name order. Each invalid mod is skipped independently. Supported: .dat, .xml, .ide, .ipl, .txt, .meta, .gxt. A mount is not a compatibility test. PC plugins/scripts and archive/model/texture replacements are unsupported.",13,ModernUi.MUTED));
        CheckBox global=new CheckBox(a);global.setText("Enable compatible data-file overlays");global.setTextColor(ModernUi.TEXT);
        try{global.setChecked("true".equals(MobileConfig.read(MobileConfig.file(data)).get("data_file_mods")));}catch(IOException ignored){}
        body.addView(global);
        TextView scanning=ModernUi.text(a,"Scanning mod folders…",13,ModernUi.MUTED);body.addView(scanning);
        LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
        Button save=ModernUi.button(a,"Save for next launch",true);save.setEnabled(false);body.addView(save);
        AlertDialog dialog=ModernUi.dialog(a,"Data-file mods",body);dialog.show();
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
                scanning.setText(error.isEmpty()?(result.isEmpty()?"No mod folders found.":"Only admitted, enabled mods mount on the next launch."):error);
                if(!loaded.isEmpty())body.addView(ModernUi.text(a,"Last launch report: "+new Date(new File(installation,ModOverlayPolicy.REPORT).lastModified()),12,ModernUi.ACCENT),body.indexOfChild(rows));
                List<Row> views=new ArrayList<>();
                for(ModOverlayPolicy.Mod mod:result)views.add(new Row(a,mod));
                Runnable refresh=()->{
                    ModOverlayPolicy.conflicts(result);
                    views.sort((x,y)->ModOverlayPolicy.ORDER.compare(x.mod,y.mod));rows.removeAllViews();
                    for(Row row:views){
                        String status=row.mod.admitted()?"Admitted: "+row.mod.files.size()+" loose files":"Rejected: "+row.mod.problem;
                        if(!row.mod.enabled)status="Disabled · "+status;
                        if(row.mod.conflictCount>0)status+="\nConflicts: "+row.mod.conflictCount+" (later mount wins)";
                        String last=loaded.get(ModOverlayPolicy.identity(row.mod.folder.getName()));
                        status+="\nLast launch: "+(last==null?"no per-mod result":last);
                        row.state.setText(status);row.priority.setText("Priority: "+row.mod.priority);rows.addView(row.panel);
                    }
                };
                for(Row row:views){
                    row.enabled.setOnCheckedChangeListener((button,checked)->{row.mod.enabled=checked;refresh.run();});
                    row.priority.setEnabled(!Files.isSymbolicLink(row.mod.folder.toPath()));
                    row.priority.setOnClickListener(v->{
                        EditText input=new EditText(a);input.setText(Integer.toString(row.mod.priority));
                        input.setTextColor(ModernUi.TEXT);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
                        final AlertDialog[] edit={null};
                        edit[0]=ModernUi.dialog(a,"Priority (-1000 to 1000)",input,"Apply",()->{
                            try{row.mod.priority=ModOverlayPolicy.priority(input.getText().toString().trim());refresh.run();edit[0].dismiss();}
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
                    final boolean enableGlobal=global.isChecked();
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
                            if(dialog.isShowing())dialog.dismiss();
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

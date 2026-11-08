package com.xhyn.stock;

import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class ModsPage {
    private final PanelActivity a;ModsPage(PanelActivity a){this.a=a;}
    void show() throws IOException {
        a.shell("Data file mods");File folder=DataMods.mods(a);
        Ui.add(a.body,Ui.text(a,"Folder: "+folder+"\nEach mod needs game/common/data/handling.dat or another existing Xbox data path. PC plugins, scripts and RPF/model/texture archives are rejected. Content must match the Xbox format.",13,Ui.MUTED));
        Ui.add(a.body,Ui.text(a,"Apply backs up original files and copies enabled loose data into the selected installation. Higher priority wins. Exit the game before applying or restoring.",13,Ui.TEXT));
        org.json.JSONObject deployed=DataMods.state(DataMods.store(a)).optJSONObject("files");Ui.add(a.body,Ui.text(a,"Currently deployed: "+(deployed==null?0:deployed.length())+" files. Checkboxes stage the next Apply operation.",12,Ui.ACCENT));
        LinearLayout list=Ui.column(a);Ui.add(a.body,list);
        a.job("Checking mod folders",()->a.files(()->{
            StockApi.offline(a);DataMods.recover(a);Files.createDirectories(folder.toPath());return ModOverlayPolicy.scan(folder);
        }),mods->{
            Map<ModOverlayPolicy.Mod,TextView> descriptions=new LinkedHashMap<>();
            Runnable conflicts=()->{ModOverlayPolicy.conflicts(mods);for(var entry:descriptions.entrySet()){ModOverlayPolicy.Mod m=entry.getKey();entry.getValue().setText(m.admitted()?m.files.size()+" supported paths/types · "+m.conflictCount+" overlapping paths":"Needs attention: "+m.problem);}};
            if(mods.isEmpty())Ui.add(list,Ui.text(a,"No data mod folders found.",14,Ui.MUTED));
            for(ModOverlayPolicy.Mod mod:mods) {
                if(!mod.admitted())mod.enabled=false;
                LinearLayout card=Ui.card(a);CheckBox enabled=new CheckBox(a);enabled.setText(mod.folder.getName());enabled.setTextColor(Ui.TEXT);enabled.setChecked(mod.enabled);enabled.setEnabled(mod.admitted());Ui.add(card,enabled);
                TextView state=Ui.text(a,"",12,Ui.MUTED);Ui.add(card,state);descriptions.put(mod,state);
                enabled.setOnCheckedChangeListener((b,on)->{mod.enabled=on;conflicts.run();a.dirty=true;});
                Button priority=Ui.button(a,"Priority: "+mod.priority,false);Ui.add(card,priority);priority.setEnabled(mod.admitted());
                priority.setOnClickListener(v->Ui.number(a,"Mod priority",mod.priority,-1000,1000,n->{mod.priority=n;priority.setText("Priority: "+n);conflicts.run();a.dirty=true;priority.requestFocus();}));
                Ui.action(a,card,"Files & conflicts",()->Ui.info(a,mod.folder.getName(),mod.problem+"\n"+String.join("\n",mod.files.subList(0,Math.min(80,mod.files.size())))+"\n"+String.join("\n",mod.conflicts)));
                Ui.add(list,card);
            }
            conflicts.run();
            Ui.action(a,a.body,"Apply enabled mods",()->Ui.dialog(a,"Apply data mods?","Original files are backed up. Enabled data files replace matching installed files in priority order.","Apply",()->a.job("Applying data mods",()->a.files(()->{
                DataMods.apply(a,mods);for(ModOverlayPolicy.Mod mod:mods)if(mod.admitted())ModOverlayPolicy.save(mod);return true;
            }),done->{a.dirty=false;a.render();})));
            Ui.action(a,a.body,"Restore original files",()->Ui.dialog(a,"Restore originals?","Restore the original data files preserved by this manager.","Restore",()->a.job("Restoring original data",()->a.files(()->{DataMods.apply(a,Collections.emptyList());return true;}),done->{a.dirty=false;a.render();})));
        });
    }
}

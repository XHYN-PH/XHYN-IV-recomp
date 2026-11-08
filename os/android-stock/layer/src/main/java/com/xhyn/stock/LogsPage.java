package com.xhyn.stock;

import android.content.*;
import android.net.Uri;
import android.widget.*;
import java.util.*;

final class LogsPage {
    private final PanelActivity a;private String selected="";LogsPage(PanelActivity a){this.a=a;}
    void show() {
        a.shell("Logs & diagnostics");Ui.add(a.body,Ui.text(a,Diagnostics.device(a),13,Ui.MUTED));
        Ui.add(a.body,Ui.text(a,"Diagnostics apply on the next launch. Render tracing is heavier; use it only when investigating graphics issues.",13,Ui.MUTED));
        String mode=a.getSharedPreferences("diagnostics_settings",0).getString("mode","off");
        for(String[] item:new String[][]{{"off","Off"},{"logs","Runtime logs"},{"performance","Performance profile"},{"render","Render trace"}}) {
            Button b=Ui.button(a,(mode.equals(item[0])?"✓ ":"")+item[1],false);Ui.add(a.body,b);
            b.setOnClickListener(v->{if(!a.getSharedPreferences("diagnostics_settings",0).edit().putString("mode",item[0]).commit())Ui.info(a,"Couldn’t save","Diagnostics mode was kept.");else a.render();});
        }
        List<String> sessions=Diagnostics.sessions(a);if(!sessions.isEmpty())selected=sessions.get(0);
        TextView current=Ui.text(a,selected.isEmpty()?"No session log yet. Launch/exit reports can still be exported.":"Selected: "+selected,13,Ui.TEXT);Ui.add(a.body,current);
        Ui.action(a,a.body,"Share diagnostics ZIP",()->a.job("Preparing diagnostics",()->Diagnostics.share(a,selected),file->{
            Uri uri=LayerFileProvider.uri(a,file);Intent share=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("Diagnostics ZIP",uri));try{a.startActivity(Intent.createChooser(share,"Share diagnostics"));}catch(ActivityNotFoundException e){Ui.error(a,e);}
        }));
        Ui.action(a,a.body,"Save diagnostics ZIP",()->{a.pendingSlot=selected;a.export("logs-export","XHYN-IV-diagnostics.zip");});
        for(String name:sessions.subList(0,Math.min(30,sessions.size())))Ui.action(a,a.body,name,()->{selected=name;current.setText("Selected: "+name);});
        Ui.add(a.body,Ui.text(a,"Exports include the selected session, launch reports, device facts, current stock settings and available Android exit traces. Saves, APKs and game assets are excluded.",12,Ui.MUTED));
    }
}

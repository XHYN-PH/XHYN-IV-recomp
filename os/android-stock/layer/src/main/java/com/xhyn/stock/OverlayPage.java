package com.xhyn.stock;

import android.widget.*;
import java.io.IOException;
import org.json.*;

final class OverlayPage {
    private final PanelActivity a;private JSONObject draft;
    OverlayPage(PanelActivity a){this.a=a;}
    void show() throws JSONException {
        a.shell("Monitoring overlay");draft=LayerPreferences.read(a);
        Ui.add(a.body,Ui.text(a,"CPU and RAM describe the game process while playing. GPU counters depend on Android permissions. Game FPS is unavailable in this stock interface.",13,Ui.MUTED));
        for(String[] row:new String[][]{{"hud","Show overlay"},{"cpu","App CPU usage"},{"ram","App RAM (PSS)"},{"battery","Battery level & temperature"},{"thermal","Android thermal status"},{"gpu","GPU usage when readable"}}) {
            CheckBox check=new CheckBox(a);check.setText(row[1]);check.setTextColor(Ui.TEXT);check.setMinHeight(Ui.dp(a,44));
            check.setChecked(draft.optBoolean(row[0],!row[0].equals("hud")&&!row[0].equals("gpu")));Ui.add(a.body,check);
            check.setOnCheckedChangeListener((v,on)->{try{draft.put(row[0],on);a.dirty=true;}catch(JSONException ignored){}});
        }
        size("Text size", "textSize",9,24,13);size("Background opacity", "opacity",0,230,170);
        Ui.action(a,a.body,"Save & edit placement",()->save(()->PanelActivity.open(a,"overlay-editor")));
        Ui.action(a,a.body,"Save",()->save(()->Toast.makeText(a,"Overlay saved",Toast.LENGTH_SHORT).show()));
        Ui.action(a,a.body,"Discard",a::render);
    }
    private void size(String label,String key,int min,int max,int value) {
        TextView name=Ui.text(a,label,14,Ui.TEXT);Ui.add(a.body,name);SeekBar slider=new SeekBar(a);slider.setMax(max-min);slider.setProgress(draft.optInt(key,value)-min);Ui.add(a.body,slider);
        name.setText(label+": "+(slider.getProgress()+min));slider.setContentDescription(label);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int n,boolean user){if(!user)return;name.setText(label+": "+(n+min));try{draft.put(key,n+min);a.dirty=true;}catch(JSONException ignored){}}
        });
    }
    private void save(Runnable next) {
        try { JSONObject changes=new JSONObject();for(String key:new String[]{"hud","cpu","ram","battery","thermal","gpu","textSize","opacity"})if(draft.has(key))changes.put(key,draft.get(key));
            LayerPreferences.update(a,changes);a.dirty=false;next.run();
        }catch(IOException|JSONException e){Ui.error(a,e);}
    }
}

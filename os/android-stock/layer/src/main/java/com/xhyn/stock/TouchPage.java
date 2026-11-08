package com.xhyn.stock;

import android.widget.*;
import java.io.*;

final class TouchPage {
    private final PanelActivity a;private boolean opened;
    TouchPage(PanelActivity a){this.a=a;}
    void show() throws IOException {
        a.shell("Touch controls");
        if(StockApi.playing(a)) {Ui.add(a.body,Ui.text(a,"Exit the game to edit touch layouts. The saved layout is loaded on the next launch.",14,Ui.TEXT));return;}
        StockApi.openTouch(a);opened=true;
        renderControls();
    }
    private void renderControls() throws IOException {
        a.shell("Touch controls");
        a.discard=()->{if(opened)try{StockApi.openTouch(a);}catch(IOException ignored){}};
        Ui.action(a,a.body,"Open fullscreen layout editor",()->new TouchEditor(a).show());
        Ui.add(a.body,Ui.text(a,"Changes are staged until Save. Layouts are separate for each context, orientation, screen class and handedness.",13,Ui.MUTED));
        slider("Button size","scale",.65f,1.8f);slider("Opacity","opacity",.2f,1);
        slider("Camera sensitivity","camera",.1f,3);slider("Aim sensitivity","aim",.05f,1.5f);slider("Vehicle camera","vehicle",.1f,3);slider("Flight camera","flight",.1f,3);
        for(String[] item:new String[][]{{"left","Left handed layout"},{"floating","Floating movement stick"},{"invert","Invert vertical camera"}}) {
            CheckBox check=new CheckBox(a);check.setText(item[1]);check.setTextColor(Ui.TEXT);check.setChecked("true".equals(StockApi.touchGet(item[0])));Ui.add(a.body,check);
            check.setOnCheckedChangeListener((b,on)->{try{StockApi.touchSet(item[0],Boolean.toString(on));a.dirty=true;}catch(IOException e){Ui.error(a,e);}});
        }
        Button camera=Ui.button(a,"Camera: "+StockApi.touchGet("camera_mode"),false);Ui.add(a.body,camera);
        camera.setOnClickListener(v->{try{String mode=StockApi.touchGet("camera_mode").equals("swipe")?"stick":"swipe";StockApi.touchSet("camera_mode",mode);camera.setText("Camera: "+mode);a.dirty=true;}catch(IOException e){Ui.error(a,e);}});
        Ui.action(a,a.body,"Reset touch controls",()->Ui.dialog(a,"Reset controls?","Stage the stock defaults. Save to keep them.","Reset",()->{
            try{StockApi.touch("nativeReset",new Class[]{boolean.class},true);renderControls();a.dirty=true;Toast.makeText(a,"Defaults staged; press Save",Toast.LENGTH_SHORT).show();}catch(IOException e){Ui.error(a,e);}
        }));
        Ui.action(a,a.body,"Save",()->{
            try{StockApi.offline(a);if(!Boolean.TRUE.equals(StockApi.touch("nativeSave",new Class[0])))throw new IOException("Could not save touch controls.");a.dirty=false;opened=false;a.finish();}catch(IOException e){Ui.error(a,e);}
        });
        Ui.action(a,a.body,"Discard",()->{a.discard.run();a.dirty=false;a.finish();});
    }
    private void slider(String title,String key,float min,float max) throws IOException {
        float value;try{value=Float.parseFloat(StockApi.touchGet(key));}catch(NumberFormatException e){throw new IOException("Invalid stock touch setting: "+key,e);}
        TextView label=Ui.text(a,title+": "+String.format(java.util.Locale.ROOT,"%.2f",value),14,Ui.TEXT);Ui.add(a.body,label);
        SeekBar bar=new SeekBar(a);bar.setMax(100);bar.setProgress(Math.round((value-min)*100/(max-min)));bar.setContentDescription(title);Ui.add(a.body,bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int n,boolean user){if(!user)return;float v=min+(max-min)*n/100;
                try{StockApi.touchSet(key,Float.toString(v));a.dirty=true;label.setText(title+": "+String.format(java.util.Locale.ROOT,"%.2f",v));}catch(IOException e){Ui.error(a,e);}}
        });
    }
}

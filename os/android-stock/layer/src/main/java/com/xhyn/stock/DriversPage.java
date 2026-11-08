package com.xhyn.stock;

import android.widget.*;
import java.io.*;

final class DriversPage {
    private final PanelActivity a;DriversPage(PanelActivity a){this.a=a;}
    void show() throws IOException {
        a.shell("Graphics drivers");String selected=DriverStore.selected(a);
        Ui.add(a.body,Ui.text(a,"Selected: "+selected+"\nThe stock loader checks the GPU family and may fall back to System. Selection applies on the next launch.",13,Ui.MUTED));
        for(String[] choice:new String[][]{{"system","System Vulkan"},{"turnip","Bundled Turnip r6 · Adreno"},{"panvk","Bundled PanVK rc4 · supported Mali"}})
            Ui.action(a,a.body,(selected.equals(choice[0])?"✓ ":"")+choice[1],()->a.job("Selecting driver",()->a.files(()->{DriverStore.select(a,choice[0]);return true;}),done->a.render()));
        Ui.action(a,a.body,"Import driver ZIP",()->{if(StockApi.playing(a)){Ui.info(a,"Game is active","Exit the game before importing a driver.");return;}a.choose("driver-import");});
        for(File folder:DriverStore.installed(a)) {
            LinearLayout card=Ui.card(a);String description;try{description=DriverStore.validate(folder);}catch(IOException e){description="Needs attention: "+FilesEx.error(e);}
            Ui.add(card,Ui.text(a,folder.getName(),15,Ui.TEXT));Ui.add(card,Ui.text(a,description,12,Ui.MUTED));
            Ui.action(a,card,"Select",()->a.job("Selecting driver",()->a.files(()->{DriverStore.select(a,"custom:"+folder.getName());return true;}),done->a.render()));
            Ui.action(a,card,"Delete",()->Ui.dialog(a,"Delete driver?",folder.getName()+". If selected, System will be chosen first.","Delete",()->a.job("Deleting driver",()->a.files(()->{DriverStore.delete(a,folder);return true;}),done->a.render())));
            Ui.add(a.body,card);
        }
        Ui.action(a,a.body,"View last loaded driver",()->{
            try{Ui.info(a,"Last launch",FilesEx.text(new File(StockApi.data(a),"last_launch.txt"),1024*1024));}catch(IOException e){Ui.error(a,e);}
        });
    }
}

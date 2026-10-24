package com.libertyrecomp;

import android.app.*;
import android.widget.*;
import java.util.function.IntPredicate;

/** Requests retail cheat natives on the game thread after resuming. */
final class CheatsUi {
    private static final int[] IDS={2,0,1,4,3,6,7,8,9,10,11,12,13,14,15,100,101,102,103,104,105,106,107,108,109};
    private static final String[] NAMES={"Health, armour & ammunition","Weapons · set one","Weapons · set two",
        "Clear wanted level","Increase wanted level","Change weather","Spawn Annihilator","Spawn NRG 900",
        "Spawn FIB Buffalo","Spawn Jetmax","Spawn Comet","Spawn Turismo","Spawn Cognoscenti","Spawn Super GT","Spawn Sanchez","Restore health","Add armour","Repair current vehicle","Add $10,000","Invincibility on","Invincibility off","Advance to morning","Advance to noon","Advance to evening","Advance to midnight"};
    static AlertDialog show(Activity a,MobileSettingsUi.Runtime runtime,IntPredicate request,Runnable resume){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(ModernUi.text(a,"Base GTA IV · single-player. Extra actions require free roam. Requests run after Resume; a submitted request does not confirm its effect. Cheats may affect saved progress.",13,ModernUi.MUTED));
        body.addView(ModernUi.text(a,"Last request: "+runtime.get("gta4_cheat_status"),12,ModernUi.MUTED));
        CheckBox override=new CheckBox(a);override.setText("Allow achievements while using cheats");override.setTextColor(ModernUi.TEXT);
        override.setChecked("true".equals(runtime.get("gta4_cheat_achievements")));body.addView(override);
        final boolean[] updating={false};
        override.setOnCheckedChangeListener((button,on)->{
            if(updating[0])return;
            if(!runtime.apply(new String[]{"gta4_cheat_achievements"},new String[]{Boolean.toString(on)})){
                updating[0]=true;button.setChecked(!on);updating[0]=false;
                Toast.makeText(a,"Could not save achievement preference.",Toast.LENGTH_LONG).show();
            }
        });
        body.addView(ModernUi.text(a,"Off follows the original restrictions. On permits local award events; it does not grant achievements automatically or sync with Xbox Live.",12,ModernUi.MUTED));
        final AlertDialog[] current={null};
        LinearLayout grid=new LinearLayout(a);grid.setOrientation(LinearLayout.VERTICAL);body.addView(grid);
        final String[] category={"All"};CompactFilterBar filters=new CompactFilterBar(a,"Search");final EditText search=filters.search;
        body.removeView(grid);body.addView(filters);body.addView(grid);
        final Runnable[] render={null};java.util.List<Button> buttons=new java.util.ArrayList<>();String[] categories={"All","Player","Weapons","Wanted","Vehicles","World"};
        render[0]=()->{grid.removeAllViews();for(int n=0;n<buttons.size();n++)ModernUi.selected(buttons.get(n),categories[n].equals(category[0]));String query=search.getText().toString().toLowerCase(java.util.Locale.ROOT);for(int i=0;i<IDS.length;i++){final int index=i;int id=IDS[i];String group=id==0||id==1?"Weapons":id==3||id==4?"Wanted":id>=7&&id<=15||id==102?"Vehicles":id==6||id>=106?"World":"Player";if(!category[0].equals("All")&&!category[0].equals(group)||!NAMES[i].toLowerCase(java.util.Locale.ROOT).contains(query))continue;ModernUi.gridAction(a,grid,NAMES[i],()->{
            if(request.test(IDS[index])){current[0].setOnDismissListener(null);current[0].dismiss();resume.run();Toast.makeText(a,"Cheat queued: "+NAMES[index],Toast.LENGTH_SHORT).show();}
            else Toast.makeText(a,runtime.get("gta4_cheat_status"),Toast.LENGTH_LONG).show();
        });}};
        for(String name:categories)buttons.add(filters.add(a,name,()->{category[0]=name;render[0].run();}));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}public void afterTextChanged(android.text.Editable e){}});render[0].run();
        AlertDialog dialog=ModernUi.dialog(a,"Cheats",body);current[0]=dialog;dialog.show();return dialog;
    }
    private CheatsUi(){}
}

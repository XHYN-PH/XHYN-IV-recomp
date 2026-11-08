package com.xhyn.stock;

import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class SettingsPage {
    private final PanelActivity a; private Map<String,String> saved,draft;private LinearLayout rows;
    private String section="All";private EditText search;private TextView pending;
    SettingsPage(PanelActivity a){this.a=a;}
    void show() throws IOException {
        saved=NextSettings.current(a);draft=new LinkedHashMap<>(saved);a.shell("Settings");
        a.root.setContentDescription("Settings saved here apply on the next game launch.");
        search=new EditText(a);search.setSingleLine(true);search.setHint("Search settings");search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);Ui.add(a.body,search);
        HorizontalScrollView tabs=new HorizontalScrollView(a);LinearLayout tabRow=new LinearLayout(a);
        for(String title:new String[]{"All","Display","Graphics","World","Controls","Game"}) {
            Button tab=Ui.button(a,title,false);tab.setTag("tab");tab.setSelected(title.equals(section));tabRow.addView(tab);
            tab.setOnClickListener(v->{section=title;for(int i=0;i<tabRow.getChildCount();i++)tabRow.getChildAt(i).setSelected(tabRow.getChildAt(i)==tab);render();tab.requestFocus();});
        }
        tabs.addView(tabRow);
        // Search and tabs share a row on landscape phones; neither reserves a tall header.
        a.body.removeView(search);LinearLayout filters=new LinearLayout(a);filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.addView(search,new LinearLayout.LayoutParams(Ui.dp(a,190),-2));filters.addView(tabs,new LinearLayout.LayoutParams(0,-2,1));Ui.add(a.body,filters);
        pending=Ui.text(a,"",12,Ui.ACCENT);((LinearLayout)a.root.getChildAt(0)).addView(pending);
        rows=Ui.column(a);Ui.add(a.body,rows);
        LinearLayout footer=new LinearLayout(a);Button discard=Ui.button(a,"Discard",false),reset=Ui.button(a,"Reset section",false),save=Ui.button(a,"Save",true);
        for(Button button:new Button[]{discard,reset,save}) {LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(Ui.dp(a,3),Ui.dp(a,6),Ui.dp(a,3),0);footer.addView(button,p);}
        a.root.addView(footer);discard.setOnClickListener(v->{draft.clear();draft.putAll(saved);a.dirty=false;render();});
        // Defaults are read from the stock profile asset, not hard-coded performance guesses.
        reset.setOnClickListener(v->Ui.dialog(a,"Reset "+section.toLowerCase(Locale.ROOT)+" settings?","Stage the stock defaults. Press Save to keep them.","Reset",()->{
            try { Map<String,String> defaults=StockDefaults.read(a);
                for(SettingSchema.Entry e:SettingSchema.ALL)if(section.equals("All")||section.equals(e.section)) {
                    String value=defaults.get(e.key);if(e.accepts(value))draft.put(e.key,value);
                }
                if(section.equals("All")||section.equals("Display"))draft.put(LaunchPreferences.RENDERER,"auto");
                if(section.equals("All")||section.equals("Game"))draft.put(LaunchPreferences.SUSTAINED,"false");
                render();
            }catch(IOException e){Ui.error(a,e);}
        }));
        save.setOnClickListener(v->{Map<String,String> changes=changes();if(changes.isEmpty())return;
            a.job("Saving settings",()->a.files(()->{NextSettings.save(a,changes);return NextSettings.current(a);}),next->{saved=next;draft=new LinkedHashMap<>(next);a.dirty=false;render();});
        });
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(Editable e){}});
        render();
    }
    private Map<String,String> changes() {
        Map<String,String> out=new LinkedHashMap<>();for(SettingSchema.Entry e:SettingSchema.ALL)
            if(!Objects.equals(saved.get(e.key),draft.get(e.key)))out.put(e.key,draft.get(e.key));
        for(String key:new String[]{LaunchPreferences.RENDERER,LaunchPreferences.SUSTAINED})if(!Objects.equals(saved.get(key),draft.get(key)))out.put(key,draft.get(key));return out;
    }
    private void render() {
        rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
        List<SettingSchema.Entry> definitions=new ArrayList<>();definitions.add(LaunchPreferences.RENDERER_ENTRY);definitions.add(LaunchPreferences.SUSTAINED_ENTRY);definitions.addAll(SettingSchema.ALL);
        for(SettingSchema.Entry e:definitions) {
            if(!section.equals("All")&&!section.equals(e.section))continue;
            if(!query.isEmpty()&&!(e.label+" "+e.hint+" "+e.section).toLowerCase(Locale.ROOT).contains(query))continue;count++;
            LinearLayout card=Ui.card(a);card.setPadding(Ui.dp(a,10),Ui.dp(a,4),Ui.dp(a,6),Ui.dp(a,4));
            LinearLayout line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);TextView label=Ui.text(a,e.label,14,Ui.TEXT);
            line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String current=draft.getOrDefault(e.key,"");Button value=Ui.button(a,e.display(current),false);
            line.addView(value,new LinearLayout.LayoutParams(Ui.dp(a,Math.max(110,Math.min(190,a.getResources().getConfiguration().screenWidthDp/4))),-2));
            Button help=Ui.button(a,"?",false);help.setContentDescription("About "+e.label);line.addView(help,new LinearLayout.LayoutParams(Ui.dp(a,40),-2));
            help.setOnClickListener(v->Ui.info(a,e.label,e.hint+"\n\nChanges apply on the next launch."));card.addView(line);Ui.add(rows,card);
            if(e.key.equals(LaunchPreferences.SUSTAINED)&&!LaunchPreferences.supported(a)){value.setText("Unavailable on this device");value.setEnabled(false);}
            else value.setOnClickListener(v->choose(e,value));
        }
        if(count==0)Ui.add(rows,Ui.text(a,"No matching settings",14,Ui.MUTED));
        int n=changes().size();a.dirty=n>0;pending.setText(n==0?"No unsaved changes":n+" unsaved change"+(n==1?"":"s"));
    }
    private void choose(SettingSchema.Entry e,Button value) {
        LinearLayout list=Ui.column(a);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(a).create();
        list.setBackgroundColor(Ui.BG);list.setPadding(Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12),Ui.dp(a,12));list.addView(Ui.text(a,e.label,18,Ui.TEXT));
        LinearLayout options=Ui.column(a);final Button[] selected={null};for(String choice:e.choices){Button option=Ui.action(a,options,e.display(choice),()->{
            draft.put(e.key,choice);value.setText(e.display(choice));int n=changes().size();a.dirty=n>0;
            pending.setText(n==0?"No unsaved changes":n+" unsaved changes");dialog.dismiss();value.requestFocus();
        });if(choice.equals(draft.get(e.key))){option.setSelected(true);selected[0]=option;}}
        ScrollView sc=Ui.scroll(a,options);list.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Ui.action(a,list,"Cancel",dialog::dismiss);
        dialog.setView(list,0,0,0,0);dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(Math.min(Ui.dp(a,440),a.getResources().getDisplayMetrics().widthPixels-Ui.dp(a,24)),Math.min(Ui.dp(a,460),a.getResources().getDisplayMetrics().heightPixels-Ui.dp(a,24)));
        Navigation.install(a,dialog.getWindow(),false,null);list.post(()->{if(selected[0]!=null)selected[0].requestFocus();else Ui.focusFirst(options);});
    }
}

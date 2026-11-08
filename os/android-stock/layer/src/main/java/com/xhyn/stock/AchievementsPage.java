package com.xhyn.stock;

import android.text.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Read-only snapshots. Missing metadata never becomes a pretend locked/unlocked achievement. */
final class AchievementsPage {
    private final PanelActivity a;private final List<JSONObject> entries=new ArrayList<>();private LinearLayout rows;
    private EditText search;private int filter;private boolean reveal;private int limit=100;
    AchievementsPage(PanelActivity a){this.a=a;}
    void show() {
        a.shell("Achievements");reveal=LayerPreferences.read(a).optBoolean("revealSecrets",false);
        a.job("Reading local achievements",()->{
            File save=StockApi.saves(a);List<File> candidates=Arrays.asList(new File(save,"achievements/catalog.json"),new File(save.getParentFile(),"achievements/catalog.json"),new File(StockApi.data(a),"LibertyRecomp/saves/achievements/catalog.json"));
            for(File f:candidates)if(f.isFile())return f;return null;
        },file->{
            try {
                if(file==null){Ui.add(a.body,Ui.text(a,"No readable achievement catalog is available. This stock build does not expose the previous catalog-generation interface. Existing game progress is retained.",14,Ui.MUTED));return;}
                JSONObject catalog=new JSONObject(FilesEx.text(file,2*1024*1024));if(catalog.optInt("version")!=1)throw new IOException("Unsupported local achievement catalog.");
                JSONArray all=catalog.getJSONArray("achievements");if(all.length()>2048)throw new IOException("Too many achievements.");
                int earned=0;for(int i=0;i<all.length();i++){JSONObject e=all.getJSONObject(i);entries.add(e);if(e.optBoolean("unlocked"))earned++;}
                entries.sort(Comparator.comparingInt(e->e.optInt("id")));
                Ui.add(a.body,Ui.text(a,earned+" / "+entries.size()+" unlocked · local snapshot\nUpdated "+new Date(file.lastModified())+"\n"+file.getAbsolutePath()+"\nThis browser does not unlock achievements or assume v442 updates this catalog.",12,Ui.MUTED));
                search=new EditText(a);search.setSingleLine(true);search.setHint("Search achievements");search.setHintTextColor(Ui.MUTED);search.setTextColor(Ui.TEXT);Ui.add(a.body,search);
                LinearLayout tabs=new LinearLayout(a);String[] names={"All","Locked","Unlocked"};
                for(int i=0;i<3;i++){final int choice=i;Button b=Ui.button(a,names[i],false);b.setTag("tab");b.setSelected(i==filter);tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{filter=choice;limit=100;for(int j=0;j<3;j++)tabs.getChildAt(j).setSelected(j==choice);render();b.requestFocus();});}Ui.add(a.body,tabs);
                CheckBox secrets=new CheckBox(a);secrets.setText("Reveal secret details");secrets.setTextColor(Ui.MUTED);secrets.setChecked(reveal);Ui.add(a.body,secrets);
                secrets.setOnCheckedChangeListener((b,on)->{try{LayerPreferences.set(a,"revealSecrets",on);reveal=on;render();}catch(IOException e){Ui.error(a,e);}});
                rows=Ui.column(a);Ui.add(a.body,rows);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){limit=100;render();}});render();
            }catch(IOException|JSONException e){Ui.error(a,e);}
        });
    }
    static String title(JSONObject e,boolean reveal){return e.optBoolean("hidden")&&!e.optBoolean("unlocked")&&!reveal?"Secret achievement":e.optString("name","Achievement");}
    static String description(JSONObject e,boolean reveal){if(e.optBoolean("hidden")&&!e.optBoolean("unlocked")&&!reveal)return "Keep playing to discover this achievement.";String value=e.optString(e.optBoolean("unlocked")?"description":"locked_description");return value.isEmpty()?e.optString("description","Details unavailable."):value;}
    private void render() {
        rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0,total=0;
        for(JSONObject e:entries){boolean unlocked=e.optBoolean("unlocked");if(filter==1&&unlocked||filter==2&&!unlocked)continue;
            String title=title(e,reveal),detail=description(e,reveal);if(!query.isEmpty()&&!(title+" "+detail).toLowerCase(Locale.ROOT).contains(query))continue;total++;if(shown++>=limit)continue;
            Ui.action(a,rows,(unlocked?"✓ ":"◇ ")+title,()->Ui.info(a,title,(unlocked?"Unlocked":"Locked")+"\n\n"+detail));
        }
        if(total==0)Ui.add(rows,Ui.text(a,"No matching achievements",14,Ui.MUTED));
        if(total>limit)Ui.action(a,rows,"Show more",()->{limit+=100;render();});
    }
}

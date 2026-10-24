package com.libertyrecomp;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

/** Browses the runtime's local achievement catalog without starting the engine. */
final class AchievementsUi {
    static final class Badge extends Drawable {
        private final boolean unlocked;private final Paint p=new Paint(3);
        Badge(boolean unlocked){this.unlocked=unlocked;}
        public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/64f,b.height()/64f);
            p.setStyle(Paint.Style.FILL);p.setColor(unlocked?0xff44371f:0xff303236);c.drawRoundRect(1,1,63,63,16,16,p);
            p.setColor(unlocked?ModernUi.ACCENT:0xffb6b8bd);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);
            if(unlocked){c.drawRoundRect(20,14,44,35,5,5,p);p.setStyle(Paint.Style.STROKE);c.drawArc(11,16,28,34,80,210,false,p);c.drawArc(36,16,53,34,-110,210,false,p);c.drawLine(32,35,32,47,p);c.drawLine(23,49,41,49,p);}
            else{p.setStyle(Paint.Style.STROKE);c.drawRoundRect(22,13,42,38,10,10,p);p.setStyle(Paint.Style.FILL);c.drawRoundRect(16,29,48,52,5,5,p);p.setColor(ModernUi.CARD);c.drawCircle(32,38,3,p);c.drawRect(30,39,34,45,p);}
            c.restore();}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static boolean hidden(JSONObject e,boolean reveal){return e.optBoolean("hidden")&&!e.optBoolean("unlocked")&&!reveal;}
    static String title(JSONObject e,boolean reveal){return hidden(e,reveal)?"Secret achievement":e.optString("name","Achievement");}
    static String description(JSONObject e,boolean reveal){
        if(hidden(e,reveal))return "Keep playing to discover this achievement.";
        String value=e.optString(e.optBoolean("unlocked")?"description":"locked_description");
        return value.isEmpty()?e.optString("description","Complete its in-game requirement to unlock."):value;
    }
    static Drawable icon(Activity a,File folder,JSONObject e,boolean reveal){
        boolean earned=e.optBoolean("unlocked");File f=new File(folder,"icons/"+e.optInt("image")+".png");
        if(!hidden(e,reveal)&&f.isFile()&&f.length()<2*1024*1024){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.toString(),o);
            if(o.outWidth>0&&o.outHeight>0&&o.outWidth<=4096&&o.outHeight<=4096){o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>128)o.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeFile(f.toString(),o);if(bitmap!=null)return new android.graphics.drawable.BitmapDrawable(a.getResources(),bitmap);}
        }
        return new Badge(earned);
    }
    static String state(JSONObject e){
        if(!e.optBoolean("unlocked"))return "LOCKED";
        long time=e.optLong("time");return "UNLOCKED"+(time>0&&time<Long.MAX_VALUE/1000?" · "+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(time*1000)):"");
    }
    static AlertDialog show(Activity a,File data,MobileSettingsUi.Runtime runtime){
        return show(a,data,runtime,null);
    }
    static AlertDialog show(Activity a,File data,MobileSettingsUi.Runtime runtime,Runnable prepare){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        File folder=new File(data,"LibertyRecomp/saves/achievements");ArrayList<JSONObject> entries=new ArrayList<>();String problem="";
        try{File file=new File(folder,"catalog.json");if(file.isFile()){
            if(file.length()>2*1024*1024)throw new IOException("Achievement catalog is too large.");
            JSONObject catalog=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));if(catalog.optInt("version")!=1)throw new IOException("Unsupported achievement catalog.");
            JSONArray all=catalog.getJSONArray("achievements");if(all.length()>2048)throw new IOException("Too many achievements.");for(int i=0;i<all.length();i++)entries.add(all.getJSONObject(i));
        }}catch(Exception e){problem="Could not read achievements: "+e.getMessage();}
        int unlocked=0;for(JSONObject e:entries)if(e.optBoolean("unlocked"))unlocked++;
        entries.sort(Comparator.comparing((JSONObject e)->!e.optBoolean("unlocked")).thenComparingInt(e->e.optInt("id")));
        TextView summary=ModernUi.text(a,unlocked+" / "+entries.size()+" unlocked",15,ModernUi.TEXT);LinearLayout summaryRow=new LinearLayout(a);summaryRow.setGravity(Gravity.CENTER_VERTICAL);summaryRow.addView(summary,new LinearLayout.LayoutParams(0,-2,1));Button options=ModernUi.button(a,"Options",false);summaryRow.addView(options,new LinearLayout.LayoutParams(-2,ModernUi.dp(a,36)));body.addView(summaryRow);
        ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(Math.max(1,entries.size()));progress.setProgress(unlocked);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ModernUi.ACCENT));body.addView(progress,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,6)));
        CompactFilterBar tools=new CompactFilterBar(a,"Search");body.addView(tools);
        final int[] filter={0};final boolean[] reveal={UiPreferences.get(a,"reveal_secrets")};final Runnable[] render={null};
        String[] tabs={"All","Locked","Unlocked"};Button[] buttons=new Button[3];
        for(int i=0;i<3;i++){final int which=i;buttons[i]=tools.add(a,tabs[i],()->{filter[0]=which;render[0].run();});}
        EditText search=tools.search;
        LinearLayout preferences=new LinearLayout(a);preferences.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox notifications=new CheckBox(a);notifications.setText("Unlock notifications");notifications.setTextColor(ModernUi.TEXT);preferences.addView(notifications);
        try{Map<String,String> config=MobileConfig.read(MobileConfig.file(data));String current=runtime==null?config.getOrDefault("achievement_notifications","true"):runtime.get("achievement_notifications");notifications.setChecked(!"false".equals(current));}catch(IOException ignored){notifications.setChecked(true);}
        final boolean[] updating={false};notifications.setOnCheckedChangeListener((button,on)->{if(updating[0])return;try{if(runtime==null)MobileConfig.update(MobileConfig.file(data),Collections.singletonMap("achievement_notifications",Boolean.toString(on)));else if(!runtime.apply(new String[]{"achievement_notifications"},new String[]{Boolean.toString(on)}))throw new IOException("Could not save notification preference.");}catch(IOException e){updating[0]=true;notifications.setChecked(!on);updating[0]=false;Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}});
        CheckBox secrets=new CheckBox(a);secrets.setText("Reveal secret details");secrets.setTextColor(ModernUi.MUTED);secrets.setChecked(reveal[0]);preferences.addView(secrets);secrets.setOnCheckedChangeListener((v,on)->{try{UiPreferences.set(a,"reveal_secrets",on);reveal[0]=on;render[0].run();}catch(IOException error){secrets.setChecked(reveal[0]);Toast.makeText(a,"Could not save secret details preference.",Toast.LENGTH_LONG).show();}});
        ScrollView scroll=new ScrollView(a);LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        options.setOnClickListener(v->{if(preferences.getParent() instanceof ViewGroup)((ViewGroup)preferences.getParent()).removeView(preferences);ModernUi.dialog(a,"Achievement options",preferences).show();});
        final String error=problem;
        render[0]=()->{
            list.removeAllViews();for(int i=0;i<3;i++)ModernUi.selected(buttons[i],filter[0]==i);
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;boolean two=a.getResources().getConfiguration().screenWidthDp/a.getResources().getConfiguration().fontScale>=540;LinearLayout pair=null;
            for(JSONObject e:entries){boolean earned=e.optBoolean("unlocked");if(filter[0]==1&&earned||filter[0]==2&&!earned)continue;
                String name=title(e,reveal[0]),detail=description(e,reveal[0]);if(!query.isEmpty()&&!(name+" "+detail).toLowerCase(Locale.ROOT).contains(query))continue;shown++;
                if(pair==null||pair.getChildCount()==(two?2:1)){pair=new LinearLayout(a);pair.setBaselineAligned(false);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=ModernUi.dp(a,6);list.addView(pair,pp);}
                LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);int pad=ModernUi.dp(a,8);row.setPadding(pad,pad,pad,pad);row.setBackground(ModernUi.surface(a,earned?0xff252119:0xff1c1d1f,12));
                ImageView picture=new ImageView(a);picture.setImageDrawable(icon(a,folder,e,reveal[0]));picture.setAlpha(earned?1f:.6f);picture.setContentDescription(earned?"Unlocked achievement icon":"Locked achievement icon");row.addView(picture,new LinearLayout.LayoutParams(ModernUi.dp(a,38),ModernUi.dp(a,38)));
                LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);TextView nameView=ModernUi.text(a,name,14,ModernUi.TEXT);nameView.setMaxLines(2);nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(nameView);TextView brief=ModernUi.text(a,detail,11,ModernUi.MUTED);brief.setMaxLines(2);brief.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(brief);text.addView(ModernUi.text(a,(earned?"UNLOCKED":"LOCKED")+(e.optInt("score")>0?" · "+e.optInt("score")+" pts":""),10,earned?ModernUi.ACCENT:ModernUi.MUTED));LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(0,-2,1);t.leftMargin=pad;row.addView(text,t);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ModernUi.dp(a,90),1);if(pair.getChildCount()>0)lp.leftMargin=ModernUi.dp(a,7);pair.addView(row,lp);row.setFocusable(true);row.setContentDescription(name+", "+state(e));row.setOnClickListener(v->{
                    LinearLayout info=new LinearLayout(a);info.setOrientation(LinearLayout.VERTICAL);ImageView large=new ImageView(a);large.setImageDrawable(icon(a,folder,e,reveal[0]));info.addView(large,new LinearLayout.LayoutParams(ModernUi.dp(a,80),ModernUi.dp(a,80)));info.addView(ModernUi.text(a,detail,18,ModernUi.TEXT));info.addView(ModernUi.text(a,state(e),13,ModernUi.ACCENT));ModernUi.dialog(a,name,info).show();});
            }
            if(two&&pair!=null&&pair.getChildCount()==1)pair.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
            if(shown==0)list.addView(ModernUi.text(a,!error.isEmpty()?error:entries.isEmpty()?"Prepare the installed game's achievement list. Earlier progress may not unlock retroactively.":"No achievements match this filter.",15,ModernUi.MUTED));
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}public void afterTextChanged(Editable e){}});
        body.addView(ModernUi.text(a,"Local progress · Included in save backups · No Xbox Gamerscore",11,ModernUi.MUTED));render[0].run();
        AlertDialog dialog=ModernUi.screen(a,"Achievements",body,null,null,null);
        if(entries.isEmpty()&&prepare!=null){Button load=ModernUi.button(a,"Prepare achievement list",true);body.addView(load);load.setOnClickListener(v->{dialog.dismiss();prepare.run();});}
        dialog.show();return dialog;
    }
    private AchievementsUi(){}
}

package com.libertyrecomp;
import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Shared, borderless Android menus; these never load the renderer. */
final class ModernUi {
    static final int BG=0xff080808,CARD=0xff151515,TEXT=0xfff4f1e8,MUTED=0xffaaa9a5,ACCENT=0xffdfc28a;
    static int dp(Activity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static TextView text(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));return t;}
    static LinearLayout panel(Activity a){LinearLayout p=new LinearLayout(a);p.setOrientation(LinearLayout.VERTICAL);int pad=dp(a,22);p.setPadding(pad,pad,pad,pad);p.setBackground(surface(a,CARD,4));return p;}
    static Button button(Activity a,String title,boolean primary){
        Button b=new Button(a);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{MUTED,BG,BG,primary?BG:TEXT}));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},surface(a,ACCENT,3));states.addState(new int[]{android.R.attr.state_focused},surface(a,ACCENT,3));states.addState(new int[]{},surface(a,primary?TEXT:0xff242424,3));
        b.setBackgroundTintList(null);b.setBackground(states);b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(a,48));b.setPadding(dp(a,16),dp(a,6),dp(a,16),dp(a,6));return b;
    }
    static void action(Activity a,LinearLayout panel,String title,Runnable run){Button b=button(a,title,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,48));lp.topMargin=dp(a,9);panel.addView(b,lp);b.setOnClickListener(v->run.run());}
    static void gridAction(Activity a,LinearLayout grid,String title,Runnable run){
        LinearLayout row=grid.getChildCount()==0?null:(LinearLayout)grid.getChildAt(grid.getChildCount()-1);
        if(row==null||row.getChildCount()==2){row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        Button b=button(a,title,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a,48),1);lp.topMargin=dp(a,8);if(row.getChildCount()>0)lp.leftMargin=dp(a,8);row.addView(b,lp);b.setOnClickListener(v->run.run());
    }
    static AlertDialog dialog(Activity a,String title,View body){
        return dialog(a,title,body,null,null);
    }
    static AlertDialog dialog(Activity a,String title,View body,String actionLabel,Runnable action){
        return dialog(a,title,body,"Back",actionLabel,action);
    }
    static AlertDialog dialog(Activity a,String title,View body,String backLabel,String actionLabel,Runnable action){
        LinearLayout panel=panel(a),header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView h=text(a,title.toUpperCase(java.util.Locale.ROOT),25,TEXT);h.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        Button back=button(a,backLabel,false);header.addView(back,new LinearLayout.LayoutParams(-2,dp(a,42)));panel.addView(header);
        if(actionLabel!=null){Button save=button(a,actionLabel,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(a,42));lp.leftMargin=dp(a,8);header.addView(save,lp);save.setOnClickListener(v->{if(action!=null)action.run();});}
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(body);
        LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,0,1);content.topMargin=dp(a,16);panel.addView(scroll,content);
        AlertDialog d=new AlertDialog.Builder(a).setView(panel).create();back.setOnClickListener(v->d.dismiss());
        d.setOnShowListener(v->{if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.72f);d.getWindow().setLayout(Math.min(dp(a,760),(int)(a.getResources().getDisplayMetrics().widthPixels*.94f)),(int)(a.getResources().getDisplayMetrics().heightPixels*.92f));}});return d;
    }
    static void choose(Activity a,String title,String[] labels,int selected,IntConsumer changed){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);AlertDialog dialog=dialog(a,title,body);
        for(int i=0;i<labels.length;i++){final int index=i;Button b=button(a,(i==selected?"●  ":"")+labels[i],false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,46));lp.topMargin=dp(a,5);body.addView(b,lp);b.setOnClickListener(v->{changed.accept(index);dialog.dismiss();});}dialog.show();
    }
    static void tint(SeekBar slider){slider.setProgressTintList(ColorStateList.valueOf(ACCENT));slider.setThumbTintList(ColorStateList.valueOf(ACCENT));}
    private ModernUi(){}
}

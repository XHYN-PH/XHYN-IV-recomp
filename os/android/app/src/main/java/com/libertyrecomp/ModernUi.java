package com.libertyrecomp;
import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

/** Shared, borderless Android menus; these never load the renderer. */
final class ModernUi {
    static final int BG=0xff080808,CARD=0xff151515,TEXT=0xfff4f1e8,MUTED=0xffaaa9a5,ACCENT=0xffdfc28a;
    static int dp(Activity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static void safeInsets(Activity a,View view,int horizontal,int vertical){
        safeInsets(a,view,horizontal,vertical,vertical);
    }
    static void safeInsets(Activity a,View view,int horizontal,int topPadding,int bottomPadding){
        view.setOnApplyWindowInsetsListener((v,insets)->{
            DisplayCutout cutout=insets.getDisplayCutout();
            int left,right,top,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets gestures=insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
                left=safe.left;right=safe.right;top=safe.top;bottom=Math.max(safe.bottom,gestures.bottom);
            }else{
                boolean fullscreen=(a.getWindow().getDecorView().getSystemUiVisibility()&View.SYSTEM_UI_FLAG_FULLSCREEN)!=0;
                left=Math.max(insets.getSystemWindowInsetLeft(),cutout==null?0:cutout.getSafeInsetLeft());
                right=Math.max(insets.getSystemWindowInsetRight(),cutout==null?0:cutout.getSafeInsetRight());
                top=Math.max(fullscreen?0:insets.getSystemWindowInsetTop(),cutout==null?0:cutout.getSafeInsetTop());
                bottom=Math.max(insets.getSystemWindowInsetBottom(),insets.getMandatorySystemGestureInsets().bottom);
            }
            v.setPadding(dp(a,horizontal)+left,dp(a,topPadding)+top,dp(a,horizontal)+right,dp(a,bottomPadding)+bottom);return insets;
        });view.requestApplyInsets();
    }
    static void immersive(Window window){
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if(android.os.Build.VERSION.SDK_INT>=30){WindowInsetsController c=window.getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}
    }
    static GradientDrawable surface(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static TextView text(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));return t;}
    static boolean motion(android.content.Context a){return ValueAnimator.areAnimatorsEnabled()&&!UiPreferences.get(a,"reduce_motion");}
    static void enter(Activity a,View view,int delay){
        view.animate().cancel();view.setTranslationX(0);view.setTranslationY(0);
        if(!motion(a)){view.setAlpha(1);return;}
        view.setAlpha(0);
        view.animate().alpha(1).setStartDelay(0).setDuration(120)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    static void slideIn(Activity a,View view){enter(a,view,0);}
    static LinearLayout panel(Activity a){LinearLayout p=new LinearLayout(a);p.setOrientation(LinearLayout.VERTICAL);int pad=dp(a,12);p.setPadding(pad,pad,pad,pad);p.setBackground(surface(a,CARD,14));return p;}
    static Button button(Activity a,String title,boolean primary){
        Button b=new Button(a){
            @Override protected void drawableStateChanged(){
                if(getWindowToken()!=null&&getBackground() instanceof StateListDrawable){
                    StateListDrawable background=(StateListDrawable)getBackground();boolean enabled=motion(a);
                    background.setEnterFadeDuration(enabled?80:0);background.setExitFadeDuration(enabled?100:0);
                    if(!enabled)background.jumpToCurrentState();
                }
                super.drawableStateChanged();
                if(getWindowToken()!=null){setScaleX(1);setScaleY(1);}
            }
            @Override protected void onDetachedFromWindow(){animate().cancel();super.onDetachedFromWindow();}
        };b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{MUTED,BG,BG,primary?BG:TEXT}));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},surface(a,ACCENT,10));states.addState(new int[]{android.R.attr.state_focused},surface(a,ACCENT,10));states.addState(new int[]{},surface(a,primary?TEXT:0xff242424,10));
        states.setEnterFadeDuration(motion(a)?80:0);states.setExitFadeDuration(motion(a)?100:0);
        b.setBackgroundTintList(null);b.setBackground(states);b.setStateListAnimator(null);b.setElevation(0);b.setPadding(dp(a,12),dp(a,4),dp(a,12),dp(a,4));return b;
    }
    static void selected(Button button,boolean selected){button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{MUTED,BG,BG,selected?ACCENT:TEXT}));}
    static Button icon(Activity a,String title,String glyph,boolean primary){Button b=button(a,title,primary);UiIcon icon=new UiIcon(glyph,primary?BG:TEXT);int size=dp(a,19);icon.setBounds(0,0,size,size);b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(a,9));return b;}
    static Button iconButton(Activity a,String glyph,String description){Button b=button(a,"",false);UiIcon icon=new UiIcon(glyph,TEXT);int size=dp(a,20);icon.setBounds(0,0,size,size);b.setCompoundDrawables(null,null,null,null);b.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{b.getBackground(),icon}));android.graphics.drawable.LayerDrawable layer=(android.graphics.drawable.LayerDrawable)b.getBackground();layer.setLayerGravity(1,Gravity.CENTER);layer.setLayerSize(1,size,size);b.setContentDescription(description);return b;}
    static void action(Activity a,LinearLayout panel,String title,Runnable run){Button b=button(a,title,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,42));lp.topMargin=dp(a,6);panel.addView(b,lp);b.setOnClickListener(v->run.run());}
    static Button gridAction(Activity a,LinearLayout grid,String title,Runnable run){
        View last=grid.getChildCount()==0?null:grid.getChildAt(grid.getChildCount()-1);
        LinearLayout row=last instanceof LinearLayout&&"action-row".equals(last.getTag())?(LinearLayout)last:null;
        if(row==null||row.getChildCount()==2){row=new LinearLayout(a);row.setTag("action-row");row.setGravity(Gravity.CENTER_VERTICAL);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
        Button b=button(a,title,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a,42),1);lp.topMargin=dp(a,8);if(row.getChildCount()>0)lp.leftMargin=dp(a,8);row.addView(b,lp);b.setOnClickListener(v->run.run());return b;
    }
    static AlertDialog dialog(Activity a,String title,View body){
        return dialog(a,title,body,null,null);
    }
    static AlertDialog dialog(Activity a,String title,View body,String actionLabel,Runnable action){
        return dialog(a,title,body,"Back",actionLabel,action);
    }
    static AlertDialog dialog(Activity a,String title,View body,String backLabel,String actionLabel,Runnable action){
        if(body instanceof LinearLayout)optionSpacing(a,(LinearLayout)body);
        LinearLayout panel=panel(a),header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView h=text(a,title.toUpperCase(java.util.Locale.ROOT),20,TEXT);h.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        boolean iconBack=backLabel==null||backLabel.equals("Back");Button back=iconBack?iconButton(a,"back","Back"):button(a,backLabel,false);header.addView(back,new LinearLayout.LayoutParams(iconBack?dp(a,42):-2,dp(a,42)));panel.addView(header);
        if(actionLabel!=null){Button save=button(a,actionLabel,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(a,42));lp.leftMargin=dp(a,8);header.addView(save,lp);save.setOnClickListener(v->{if(action!=null)action.run();});}
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(body);
        LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,0,1);content.topMargin=dp(a,8);panel.addView(scroll,content);
        AlertDialog d=new AlertDialog.Builder(a).setView(panel).create();back.setOnClickListener(v->d.dismiss());
        d.setOnShowListener(v->{if(d.getWindow()!=null){immersive(d.getWindow());safeInsets(a,panel,12,8);d.getWindow().getDecorView().setPadding(0,0,0,0);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.6f);d.getWindow().setLayout(Math.min(dp(a,720),(int)(a.getResources().getDisplayMetrics().widthPixels*.94f)),(int)(a.getResources().getDisplayMetrics().heightPixels*.92f));enter(a,panel,0);}});return d;
    }
    static void choose(Activity a,String title,String[] labels,int selected,IntConsumer changed){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);AlertDialog dialog=dialog(a,title,body);
        for(int i=0;i<labels.length;i++){final int index=i;Button b=button(a,(i==selected?"●  ":"")+labels[i],false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,46));lp.topMargin=dp(a,5);body.addView(b,lp);b.setOnClickListener(v->{changed.accept(index);dialog.dismiss();});}dialog.show();
    }
    /** Wide landscape screen with an independently scrolling content area. */
    static AlertDialog screen(Activity a,String title,View body,String actionLabel,Runnable action,
                              java.util.function.BooleanSupplier close){
        if(body instanceof LinearLayout)optionSpacing(a,(LinearLayout)body);
        LinearLayout panel=panel(a),header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(a,title.toUpperCase(java.util.Locale.ROOT),20,TEXT);
        heading.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button back=iconButton(a,"back","Back");header.addView(back,new LinearLayout.LayoutParams(dp(a,42),dp(a,42)));
        if(actionLabel!=null){Button save=button(a,actionLabel,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(a,36));p.leftMargin=dp(a,8);header.addView(save,p);save.setOnClickListener(v->action.run());}
        panel.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));panel.addView(header);LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,0,1);content.topMargin=dp(a,8);panel.addView(body,content);
        AlertDialog d=new AlertDialog(a){
            @Override @SuppressWarnings("deprecation") public void onBackPressed(){
                if(close==null||close.getAsBoolean())dismiss();
            }
        };
        d.setView(panel);
        back.setOnClickListener(v->d.onBackPressed());
        d.setCanceledOnTouchOutside(false);
        d.setOnShowListener(v->{if(d.getWindow()!=null){immersive(d.getWindow());safeInsets(a,panel,10,8);d.getWindow().getDecorView().setPadding(0,0,0,0);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.6f);d.getWindow().setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.96f),(int)(a.getResources().getDisplayMetrics().heightPixels*.94f));enter(a,panel,0);}});
        return d;
    }
    static void tint(SeekBar slider){slider.setProgressTintList(ColorStateList.valueOf(ACCENT));slider.setThumbTintList(ColorStateList.valueOf(ACCENT));}
    static void optionSpacing(Activity a,LinearLayout body){
        if(body.getOrientation()!=LinearLayout.VERTICAL)return;
        for(int i=0;i<body.getChildCount();i++){
            View child=body.getChildAt(i);
            if(child instanceof LinearLayout)optionSpacing(a,(LinearLayout)child);
            if(i>0&&(child instanceof Button||child instanceof CheckBox)&&child.getLayoutParams() instanceof LinearLayout.LayoutParams){
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();p.topMargin=Math.max(p.topMargin,dp(a,8));child.setLayoutParams(p);
            }
        }
    }
    private ModernUi(){}
}

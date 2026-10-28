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
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(android.os.Build.VERSION.SDK_INT>=30){WindowInsetsController c=window.getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}
    }
    /** An AlertDialog's default custom-view padding otherwise crops full-window editors. */
    static void fullscreen(Activity a,AlertDialog dialog,View content){
        dialog.setView(content,0,0,0,0);
        Window w=dialog.getWindow();if(w==null)return;
        w.setBackgroundDrawableResource(android.R.color.transparent);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        if(android.os.Build.VERSION.SDK_INT>=30)w.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams attrs=w.getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;w.setAttributes(attrs);
        w.getDecorView().setPadding(0,0,0,0);immersive(w);w.setLayout(-1,-1);
    }
    static ScrollView scroll(Activity a,int reservedDp){
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int width,int height){
            int limit=Math.max(dp(a,48),a.getResources().getDisplayMetrics().heightPixels-dp(a,reservedDp));
            WindowInsets insets=getRootWindowInsets();if(insets!=null)limit=Math.max(dp(a,48),limit-insets.getSystemWindowInsetTop()-insets.getSystemWindowInsetBottom());
            if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }};scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(a,8));return scroll;
    }
    static void focusableCard(Activity a,View view,int color){
        StateListDrawable states=new StateListDrawable();GradientDrawable focus=surface(a,color,10);focus.setStroke(dp(a,2),ACCENT);
        states.addState(new int[]{android.R.attr.state_focused},focus);states.addState(new int[]{},surface(a,color,10));view.setBackground(states);view.setFocusable(true);
    }
    static GradientDrawable surface(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    static TextView text(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));return t;}
    static boolean motion(android.content.Context a){return ValueAnimator.areAnimatorsEnabled()&&!UiPreferences.get(a,"reduce_motion");}
    static void enter(Activity a,View view,int delay){
        view.animate().cancel();view.setTranslationX(0);view.setTranslationY(0);
        if(!motion(a)){view.setAlpha(1);return;}
        view.setAlpha(.85f);view.setTranslationX(dp(a,32));
        view.animate().translationX(0).alpha(1).setStartDelay(Math.max(0,Math.min(80,delay))).setDuration(180)
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
        };b.setText(title);b.setFocusable(true);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{MUTED,BG,BG,primary?BG:TEXT}));
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},surface(a,ACCENT,10));states.addState(new int[]{android.R.attr.state_focused},surface(a,ACCENT,10));states.addState(new int[]{},surface(a,primary?TEXT:0xff242424,10));
        states.setEnterFadeDuration(motion(a)?80:0);states.setExitFadeDuration(motion(a)?100:0);
        b.setTooltipText(title);b.setBackgroundTintList(null);b.setBackground(states);b.setStateListAnimator(null);b.setElevation(0);b.setPadding(dp(a,12),dp(a,4),dp(a,12),dp(a,4));return b;
    }
    static void selected(Button button,boolean selected){button.setSelected(selected);button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{MUTED,BG,BG,selected?ACCENT:TEXT}));}
    static Button icon(Activity a,String title,String glyph,boolean primary){Button b=button(a,title,primary);UiIcon icon=new UiIcon(glyph,primary?BG:TEXT);int size=dp(a,19);icon.setBounds(0,0,size,size);b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(a,9));return b;}
    static Button iconButton(Activity a,String glyph,String description){Button b=button(a,"",false);UiIcon icon=new UiIcon(glyph,TEXT);int size=dp(a,20);icon.setBounds(0,0,size,size);b.setCompoundDrawables(null,null,null,null);b.setBackground(new android.graphics.drawable.LayerDrawable(new Drawable[]{b.getBackground(),icon}));android.graphics.drawable.LayerDrawable layer=(android.graphics.drawable.LayerDrawable)b.getBackground();layer.setLayerGravity(1,Gravity.CENTER);layer.setLayerSize(1,size,size);b.setContentDescription(description);b.setTooltipText(description);return b;}
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
        TextView h=text(a,title,17,TEXT);h.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
        boolean iconBack=backLabel==null||backLabel.equals("Back");Button back=iconBack?iconButton(a,"back","Back"):button(a,backLabel,false);header.addView(back,new LinearLayout.LayoutParams(iconBack?dp(a,42):-2,dp(a,42)));panel.addView(header);
        if(actionLabel!=null){Button save=button(a,actionLabel,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(a,42));lp.leftMargin=dp(a,8);header.addView(save,lp);save.setOnClickListener(v->{if(action!=null)action.run();});}
        ScrollView scroll=scroll(a,112);scroll.addView(body);
        LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,-2);content.topMargin=dp(a,8);panel.addView(scroll,content);
        AlertDialog d=navigable(a,null);d.setView(panel,0,0,0,0);back.setOnClickListener(v->d.onBackPressed());
        d.setOnShowListener(v->{if(d.getWindow()!=null){immersive(d.getWindow());safeInsets(a,panel,12,8);d.getWindow().getDecorView().setPadding(0,0,0,0);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.6f);d.getWindow().setLayout(Math.min(dp(a,body instanceof TextView?420:560),(int)(a.getResources().getDisplayMetrics().widthPixels*.94f)),-2);d.getWindow().setWindowAnimations(motion(a)?R.style.SessionPanelAnimation:0);focusFirst(panel);}});return d;
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
        AlertDialog d=navigable(a,close);
        d.setView(panel,0,0,0,0);
        back.setOnClickListener(v->d.onBackPressed());
        d.setCanceledOnTouchOutside(false);
        d.setOnShowListener(v->{if(d.getWindow()!=null){immersive(d.getWindow());safeInsets(a,panel,10,8);d.getWindow().getDecorView().setPadding(0,0,0,0);d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setDimAmount(.6f);d.getWindow().setLayout(Math.min(dp(a,760),(int)(a.getResources().getDisplayMetrics().widthPixels*.96f)),(int)(a.getResources().getDisplayMetrics().heightPixels*.94f));d.getWindow().setWindowAnimations(motion(a)?R.style.SessionPanelAnimation:0);focusFirst(panel);}});
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
    /** Keep confirmation and input dialogs navigable with the same controller rules. */
    static AlertDialog.Builder builder(Activity a){return new AlertDialog.Builder(a){
        private CharSequence heading;
        @Override public AlertDialog.Builder setTitle(CharSequence title){heading=title;return super.setTitle(title);}
        @Override public AlertDialog.Builder setTitle(int title){return setTitle(a.getText(title));}
        @Override public AlertDialog create(){
            if(heading!=null){TextView title=text(a,heading.toString(),17,TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setPadding(dp(a,18),dp(a,14),dp(a,18),dp(a,4));setCustomTitle(title);}
            AlertDialog dialog=super.create();if(heading!=null)dialog.setTitle(heading);ControllerNavigation navigation=new ControllerNavigation();
            dialog.setOnKeyListener((d,key,event)->navigation.key(dialog.getWindow(),event,dialog::onBackPressed));
            Window window=dialog.getWindow();if(window!=null){window.getDecorView().setOnGenericMotionListener((v,event)->navigation.motion(window,event));window.setWindowAnimations(motion(a)?R.style.SessionPanelAnimation:0);
                window.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewDetachedFromWindow(View v){}public void onViewAttachedToWindow(View v){v.post(()->{
                    immersive(window);window.setLayout(Math.min(dp(a,460),(int)(a.getResources().getDisplayMetrics().widthPixels*.92f)),-2);
                    TextView message=dialog.findViewById(android.R.id.message);if(message!=null){message.setTextSize(14);message.setLineSpacing(dp(a,2),1);}
                    for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=dialog.getButton(id);if(b!=null){b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(dp(a,40));}}
                    focusFirst(v);
                });}});
            }
            return dialog;
        }
    };}
    static AlertDialog navigable(Activity a,java.util.function.BooleanSupplier close){
        return new AlertDialog(a){
            final ControllerNavigation navigation=new ControllerNavigation();
            @Override protected void onCreate(android.os.Bundle state){super.onCreate(state);if(getWindow()!=null)getWindow().setWindowAnimations(motion(a)?R.style.SessionPanelAnimation:0);}
            @Override public void dismiss(){if(getWindow()!=null)getWindow().setWindowAnimations(motion(a)?R.style.SessionPanelAnimation:0);super.dismiss();}
            @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return navigation.motion(getWindow(),e)||super.dispatchGenericMotionEvent(e);}
            @Override public boolean dispatchKeyEvent(KeyEvent e){return navigation.key(getWindow(),e,this::onBackPressed)||super.dispatchKeyEvent(e);}
            @Override @SuppressWarnings("deprecation") public void onBackPressed(){if(close==null||close.getAsBoolean())dismiss();}
        };
    }
    static void focusFirst(View root){root.post(()->{if(root.findFocus()!=null)return;java.util.ArrayList<View> views=new java.util.ArrayList<>();root.addFocusables(views,View.FOCUS_FORWARD);for(View v:views)if(v.isEnabled()&&v.getVisibility()==View.VISIBLE){v.requestFocus();break;}});}
    static Button tool(Activity a,String icon,String label){return iconButton(a,icon,label);}
    private ModernUi(){}
}

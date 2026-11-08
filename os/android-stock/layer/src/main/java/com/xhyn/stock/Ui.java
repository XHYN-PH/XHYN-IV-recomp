package com.xhyn.stock;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared compact controls. Content can grow for accessibility; actions never sit below a clipped list. */
final class Ui {
    static final int BG=0xff0c1017, CARD=0xff18202c, TEXT=0xffedf1f7, MUTED=0xffacb8ca, ACCENT=0xff9bceff;
    static int dp(android.content.Context c,float n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static TextView text(Activity a,String s,int size,int color) {
        TextView t=new TextView(a); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setIncludeFontPadding(false); t.setPadding(0,dp(a,3),0,dp(a,3)); return t;
    }
    static GradientDrawable surface(Activity a,int color) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(a,10)); return d;
    }
    static Button button(Activity a,String label,boolean primary) {
        Button b=new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setMinHeight(dp(a,44)); b.setMinimumHeight(dp(a,44)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(a,12),dp(a,7),dp(a,12),dp(a,7)); b.setIncludeFontPadding(false);
        b.setFocusable(true); b.setFocusableInTouchMode(true); b.setStateListAnimator(null); b.setBackgroundTintList(null);
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},surface(a,ACCENT));
        states.addState(new int[]{android.R.attr.state_pressed},surface(a,ACCENT));
        states.addState(new int[]{android.R.attr.state_selected},surface(a,0xff304c69));
        states.addState(new int[]{},surface(a,primary?ACCENT:CARD)); b.setBackground(states);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{android.R.attr.state_selected},new int[]{}},
                new int[]{MUTED,BG,BG,ACCENT,primary?BG:TEXT})); b.setTooltipText(label); return b;
    }
    static LinearLayout column(Activity a) {
        LinearLayout p=new LinearLayout(a); p.setOrientation(LinearLayout.VERTICAL); return p;
    }
    static LinearLayout card(Activity a) {
        LinearLayout p=column(a); p.setBackground(surface(a,CARD)); int pad=dp(a,12); p.setPadding(pad,pad,pad,pad); return p;
    }
    static void add(LinearLayout parent,View view) {
        if(view.isFocusable())view.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(parent.getContext(),6); parent.addView(view,p);
    }
    static Button action(Activity a,LinearLayout p,String title,Runnable action) {
        Button b=button(a,title,false); add(p,b); b.setOnClickListener(v->action.run()); return b;
    }
    static ScrollView scroll(Activity a,View content) {
        ScrollView scroll=new ScrollView(a); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        scroll.setPadding(0,0,0,dp(a,12)); scroll.addView(content); return scroll;
    }
    static void insets(Activity a,View root,int pad) {
        root.setOnApplyWindowInsetsListener((v,in)->{
            int l,r,t,b;
            if(Build.VERSION.SDK_INT>=30) {
                Insets safe=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                Insets gestures=in.getInsets(WindowInsets.Type.mandatorySystemGestures());
                l=safe.left; r=safe.right; t=safe.top; b=Math.max(safe.bottom,gestures.bottom);
            } else {
                DisplayCutout cut=in.getDisplayCutout(); l=Math.max(in.getSystemWindowInsetLeft(),cut==null?0:cut.getSafeInsetLeft());
                r=Math.max(in.getSystemWindowInsetRight(),cut==null?0:cut.getSafeInsetRight());
                t=Math.max(in.getSystemWindowInsetTop(),cut==null?0:cut.getSafeInsetTop());
                b=Math.max(in.getSystemWindowInsetBottom(),in.getMandatorySystemGestureInsets().bottom);
            }
            v.setPadding(dp(a,pad)+l,dp(a,pad)+t,dp(a,pad)+r,dp(a,pad)+b); return in;
        }); root.requestApplyInsets();
    }
    static void immersive(Window w) {
        w.getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(Build.VERSION.SDK_INT>=30) { w.setDecorFitsSystemWindows(false); WindowInsetsController c=w.getInsetsController();
            if(c!=null) { c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); c.hide(WindowInsets.Type.systemBars()); }
        }
        WindowManager.LayoutParams p=w.getAttributes(); p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; w.setAttributes(p);
    }
    static LinearLayout screen(Activity a,String title,Runnable back) {
        immersive(a.getWindow()); LinearLayout root=column(a); root.setBackgroundColor(BG); insets(a,root,12);
        LinearLayout bar=new LinearLayout(a); bar.setGravity(Gravity.CENTER_VERTICAL);
        Button close=button(a,"‹ Back",false); close.setOnClickListener(v->back.run()); bar.addView(close,new LinearLayout.LayoutParams(-2,-2));
        TextView heading=text(a,title,20,TEXT); heading.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1); hp.leftMargin=dp(a,12); bar.addView(heading,hp);
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2)); return root;
    }
    static void focusFirst(View root) {
        if(root.isShown() && root.isEnabled() && root.isFocusable() && !(root instanceof EditText)) { root.setFocusableInTouchMode(true);root.requestFocus(); return; }
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View child=((ViewGroup)root).getChildAt(i); focusFirst(child); if(child.hasFocus()) return;
        }
    }
    static AlertDialog dialog(Activity a,String title,String message,String yes,Runnable run) {
        LinearLayout content=column(a); content.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));
        content.setBackground(surface(a,CARD)); content.addView(text(a,title,18,TEXT));
        TextView explanation=text(a,message,14,MUTED);
        ScrollView sc=new ScrollView(a){@Override protected void onMeasure(int width,int height){
            View title=content.getChildAt(0),actions=content.getChildAt(content.getChildCount()-1);
            title.measure(width,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            if(actions!=this)actions.measure(width,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int reserved=title.getMeasuredHeight()+(actions==this?0:actions.getMeasuredHeight())+content.getPaddingTop()+content.getPaddingBottom()+dp(a,40);
            int limit=Math.max(dp(a,24),a.getResources().getDisplayMetrics().heightPixels-reserved);
            if(MeasureSpec.getMode(height)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }};sc.setClipToPadding(false);sc.addView(explanation);
        content.addView(sc,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(a); actions.setGravity(Gravity.END);
        AlertDialog d=new AlertDialog.Builder(a).create();
        if(run!=null) { Button cancel=button(a,"Cancel",false); cancel.setOnClickListener(v->d.dismiss()); actions.addView(cancel); }
        Button ok=button(a,yes==null?"Close":yes,run!=null); ok.setOnClickListener(v->{d.dismiss(); if(run!=null)run.run();});
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-2,-2); op.leftMargin=dp(a,8); actions.addView(ok,op); add(content,actions);
        d.setView(content,0,0,0,0); d.setOnShowListener(ignored->{
            Window w=d.getWindow(); if(w==null)return; w.setBackgroundDrawableResource(android.R.color.transparent);
            int width=Math.min(dp(a,520),Math.max(dp(a,200),a.getWindow().getDecorView().getWidth()-dp(a,24)));
            w.setLayout(width,-2);
            Navigation.install(a,w,false,null); ok.requestFocus();
        }); d.show(); return d;
    }
    static void info(Activity a,String title,String detail) { dialog(a,title,detail,"Close",null); }
    static void number(Activity a,String title,int value,int min,int max,java.util.function.IntConsumer apply) {
        LinearLayout body=card(a);body.addView(text(a,title,18,TEXT));EditText input=new EditText(a);input.setTextColor(TEXT);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);input.setSingleLine(true);input.setText(Integer.toString(value));add(body,input);
        AlertDialog d=new AlertDialog.Builder(a).create();LinearLayout actions=new LinearLayout(a);Button cancel=button(a,"Cancel",false),save=button(a,"Apply",true);
        actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));add(body,actions);
        cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{try{int n=Integer.parseInt(input.getText().toString().trim());if(n<min||n>max)throw new NumberFormatException();apply.accept(n);d.dismiss();}catch(NumberFormatException e){input.setError("Enter "+min+" to "+max);input.requestFocus();}});
        d.setView(body,0,0,0,0);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(Math.min(dp(a,400),a.getResources().getDisplayMetrics().widthPixels-dp(a,24)),-2);Navigation.install(a,w,false,null);}save.requestFocus();
    }
    static void error(Activity a,Throwable e) { info(a,"Couldn’t finish",FilesEx.error(e)); }
    private Ui() {}
}

package com.libertyrecomp;
import android.app.Activity;
import android.view.Gravity;
import android.widget.*;
/** Shared spaced, horizontally scrolling filters and a bounded search field. */
final class CompactFilterBar extends LinearLayout {
 final EditText search;final LinearLayout tabs;
 CompactFilterBar(Activity a,String hint){super(a);setGravity(Gravity.CENTER_VERTICAL);setBaselineAligned(false);HorizontalScrollView strip=new HorizontalScrollView(a);strip.setHorizontalScrollBarEnabled(false);tabs=new LinearLayout(a);tabs.setGravity(Gravity.CENTER_VERTICAL);strip.addView(tabs);addView(strip,new LayoutParams(0,ModernUi.dp(a,42),1));search=new EditText(a);search.setSingleLine(true);search.setTextSize(13);search.setTextColor(ModernUi.TEXT);search.setHintTextColor(ModernUi.MUTED);search.setHint(hint);search.setContentDescription(hint);search.setPadding(ModernUi.dp(a,9),0,ModernUi.dp(a,9),0);search.setBackground(ModernUi.surface(a,0xff242424,8));LayoutParams lp=new LayoutParams(ModernUi.dp(a,Math.min(190,a.getResources().getConfiguration().screenWidthDp/3)),ModernUi.dp(a,38));lp.leftMargin=ModernUi.dp(a,10);addView(search,lp);}
 Button add(Activity a,String label,Runnable selected){Button b=ModernUi.button(a,label,false);b.setTextSize(12);LayoutParams lp=new LayoutParams(-2,ModernUi.dp(a,38));lp.rightMargin=ModernUi.dp(a,6);tabs.addView(b,lp);b.setOnClickListener(v->selected.run());return b;}
}

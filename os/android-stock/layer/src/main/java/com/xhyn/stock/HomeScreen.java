package com.xhyn.stock;

import android.app.Activity;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import java.io.IOException;

final class HomeScreen {
    private final Activity a; private final View original; private final String migrationError;
    private TextView state; private boolean stock;
    HomeScreen(Activity a,String migrationError) {
        this.a=a; this.migrationError=migrationError;
        ViewGroup content=a.findViewById(android.R.id.content); original=content.getChildAt(0);
        if(original!=null)content.removeView(original);
    }
    void show() {
        stock=false; LinearLayout root=Ui.column(a); root.setBackgroundColor(Ui.BG); Ui.immersive(a.getWindow()); Ui.insets(a,root,14);
        LinearLayout bar=new LinearLayout(a); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=Ui.text(a,"XHYN IV",28,Ui.TEXT); title.setTypeface(null,android.graphics.Typeface.BOLD); bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(Ui.text(a,"v442 · Android layer Test 1",12,Ui.MUTED)); root.addView(bar);
        state=Ui.text(a,"",13,Ui.MUTED); Ui.add(root,state);
        LinearLayout body=Ui.column(a);
        Button play=Ui.button(a,"Play",true); Ui.add(body,play);
        play.setOnClickListener(v->{if(migrationError!=null){Ui.info(a,"Settings need attention",migrationError);return;}try{StockApi.launch(a);}catch(IOException e){Ui.error(a,e);}});
        LinearLayout row=null;
        String[][] pages={{"Settings","settings"},{"Touch layout","touch"},{"Monitoring overlay","overlay"},{"Controllers","controllers"},{"Drivers","drivers"},{"Saves","saves"},{"Data mods","mods"},{"Achievements","achievements"},{"Logs & diagnostics","logs"}};
        boolean wide=a.getResources().getConfiguration().screenWidthDp/a.getResources().getConfiguration().fontScale>=600;
        for(int i=0;i<pages.length;i++) {
            if(row==null || row.getChildCount()==(wide?3:2)) { row=new LinearLayout(a); Ui.add(body,row); }
            final String page=pages[i][1]; Button b=Ui.button(a,pages[i][0],false);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); if(row.getChildCount()>0)p.leftMargin=Ui.dp(a,6); row.addView(b,p);
            b.setOnClickListener(v->PanelActivity.open(a,page));
        }
        Ui.action(a,body,"Game files & stock installer",this::showStock);
        if(new java.io.File(StockApi.layer(a),"before-v442/args.txt").isFile())Ui.add(body,Ui.text(a,"Previous settings are backed up. Game data and saves are retained.",12,Ui.MUTED));
        root.addView(Ui.scroll(a,body),new LinearLayout.LayoutParams(-1,0,1)); a.setContentView(root); root.post(()->play.requestFocus()); refresh();
    }
    void showStock() {
        if(original==null)return; stock=true;
        if(original.getParent() instanceof ViewGroup)((ViewGroup)original.getParent()).removeView(original);
        LinearLayout root=Ui.screen(a,"Game files & installer",this::show);
        root.addView(original,new LinearLayout.LayoutParams(-1,0,1)); a.setContentView(root);
        // Stock installer retains its own file picker, progress and native validation.
        root.post(()->Ui.focusFirst(root));
    }
    void restoreOriginal(){if(original==null)return;if(original.getParent() instanceof ViewGroup)((ViewGroup)original.getParent()).removeView(original);a.setContentView(original);}
    void refresh() {
        if(stock||state==null)return;
        if(migrationError!=null){state.setText(migrationError);return;}
        try { java.io.File game=StockApi.game(a);state.setText(StockApi.playing(a)?"Game session active":new java.io.File(game,"default.xex").isFile()?"Game files found · "+DriverStore.selected(a):"Choose game files to begin");state.setTooltipText(game.getAbsolutePath()); }
        catch(IOException e){state.setText("Open Game files to check the installation.");}
    }
}

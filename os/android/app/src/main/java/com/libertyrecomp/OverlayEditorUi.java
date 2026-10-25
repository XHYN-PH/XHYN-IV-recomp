package com.libertyrecomp;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Full-window direct manipulation; changes join the parent settings draft. */
final class OverlayEditorUi {
    static void show(Activity a,Map<String,String> target,Runnable changed){
        Map<String,String> draft=new LinkedHashMap<>(target);
        if("off".equals(draft.get("gta4_performance_overlay")))draft.put("gta4_performance_overlay","fps");
        FrameLayout page=new FrameLayout(a);page.setBackgroundColor(ModernUi.BG);
        OverlayPreview preview=new OverlayPreview(a,draft);preview.editable(()->{});page.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout tools=ModernUi.panel(a);preview.setOnClickListener(v->ModernUi.focusFirst(tools));tools.setBackground(ModernUi.surface(a,0xe8151515,12));
        TextView title=ModernUi.text(a,"OVERLAY EDITOR",16,ModernUi.TEXT);tools.addView(title);
        tools.addView(ModernUi.text(a,"Drag the sample, or choose Move overlay and use the D-pad. Press A to return to tools. Save returns to Settings.",12,ModernUi.MUTED));
        LinearLayout switches=new LinearLayout(a);tools.addView(switches);
        for(String[] item:new String[][]{{"gta4_overlay_ram","RAM"},{"gta4_overlay_thermal","Heat"},{"gta4_overlay_compact","Compact"}}){CheckBox c=new CheckBox(a);c.setText(item[1]);c.setTextColor(ModernUi.TEXT);c.setChecked("true".equals(draft.get(item[0])));switches.addView(c);c.setOnCheckedChangeListener((b,on)->{draft.put(item[0],Boolean.toString(on));preview.invalidate();});}
        LinearLayout actions=new LinearLayout(a);tools.addView(actions);
        ModernUi.gridAction(a,actions,"FPS / Details",()->{draft.put("gta4_performance_overlay","detailed".equals(draft.get("gta4_performance_overlay"))?"fps":"detailed");preview.invalidate();});
        ModernUi.gridAction(a,actions,"Position",()->ModernUi.choose(a,"Position",new String[]{"Top left","Top center","Top right","Bottom left","Bottom center","Bottom right"},-1,n->{draft.put("gta4_overlay_position",new String[]{"top_left","top_center","top_right","bottom_left","bottom_center","bottom_right"}[n]);preview.invalidate();}));
        for(String[] item:new String[][]{{"gta4_overlay_text_size","Text size","9","24","1","12"},{"gta4_overlay_opacity","Opacity","0.2","1","0.05","1"},{"gta4_overlay_edge_gap","Edge gap","0","40","1","2"}}){
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);TextView amount=ModernUi.text(a,"",12,ModernUi.TEXT);row.addView(amount,new LinearLayout.LayoutParams(0,-2,1));
            Runnable update=()->{amount.setText(item[1]+": "+draft.get(item[0]));preview.invalidate();};
            for(int direction:new int[]{-1,1}){Button b=ModernUi.button(a,direction<0?"−":"+",false);b.setContentDescription(item[1]+(direction<0?" decrease":" increase"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ModernUi.dp(a,42),ModernUi.dp(a,40));lp.leftMargin=ModernUi.dp(a,6);row.addView(b,lp);b.setOnClickListener(v->{float old;try{old=Float.parseFloat(draft.get(item[0]));}catch(Exception e){old=Float.parseFloat(item[5]);}float value=Math.max(Float.parseFloat(item[2]),Math.min(Float.parseFloat(item[3]),old+direction*Float.parseFloat(item[4])));draft.put(item[0],Float.parseFloat(item[4])>=1?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.2f",value));update.run();});}tools.addView(row);update.run();
        }
        AlertDialog[] current={null};Runnable save=()->{for(String k:draft.keySet())if(k.startsWith("gta4_overlay_")||k.equals("gta4_performance_overlay"))target.put(k,draft.get(k));changed.run();current[0].dismiss();};
        java.util.function.BooleanSupplier close=()->{if(draft.equals(target))return true;ModernUi.builder(a).setTitle("Keep overlay changes?").setPositiveButton("Keep",(d,w)->save.run()).setNegativeButton("Discard",(d,w)->current[0].dismiss()).setNeutralButton("Keep editing",null).show();return false;};
        LinearLayout footer=new LinearLayout(a);tools.addView(footer);ModernUi.gridAction(a,footer,"Reset",()->{draft.put("gta4_overlay_position","top_right");draft.put("gta4_overlay_x","1");draft.put("gta4_overlay_y","0");draft.put("gta4_overlay_text_size","12");draft.put("gta4_overlay_opacity","1");preview.invalidate();});ModernUi.gridAction(a,footer,"Move overlay",()->preview.requestFocus());
        Button back=ModernUi.iconButton(a,"back","Back");back.setOnClickListener(v->current[0].onBackPressed());tools.removeView(title);LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button collapse=ModernUi.iconButton(a,"more","Collapse overlay tools"),keep=ModernUi.iconButton(a,"save","Save");keep.setOnClickListener(v->save.run());for(Button b:new Button[]{collapse,keep,back})header.addView(b,new LinearLayout.LayoutParams(ModernUi.dp(a,40),ModernUi.dp(a,40)));tools.addView(header,0);
        collapse.setOnClickListener(v->{boolean expanded=tools.getChildCount()>1&&tools.getChildAt(1).getVisibility()==View.VISIBLE;for(int i=1;i<tools.getChildCount();i++)tools.getChildAt(i).setVisibility(expanded?View.GONE:View.VISIBLE);collapse.setContentDescription(expanded?"Expand overlay tools":"Collapse overlay tools");});
        ScrollView scroll=new ScrollView(a);scroll.addView(tools);FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(Math.min(ModernUi.dp(a,340),a.getResources().getDisplayMetrics().widthPixels/2),-2,Gravity.RIGHT|Gravity.BOTTOM);size.setMargins(ModernUi.dp(a,12),ModernUi.dp(a,60),ModernUi.dp(a,12),ModernUi.dp(a,12));page.addView(scroll,size);
        title.setContentDescription("Drag to move overlay tools");title.setOnTouchListener(new View.OnTouchListener(){float x,y,ox,oy;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();ox=scroll.getX();oy=scroll.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){scroll.setX(Math.max(0,Math.min(page.getWidth()-scroll.getWidth(),ox+e.getRawX()-x)));scroll.setY(Math.max(0,Math.min(page.getHeight()-scroll.getHeight(),oy+e.getRawY()-y)));return true;}return e.getActionMasked()==MotionEvent.ACTION_UP;}});
        current[0]=ModernUi.navigable(a,close);current[0].setView(page);current[0].setCanceledOnTouchOutside(false);current[0].setOnShowListener(d->{Window w=current[0].getWindow();if(w!=null){ModernUi.immersive(w);w.getDecorView().setPadding(0,0,0,0);w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);ModernUi.focusFirst(tools);}});current[0].show();
    }
}

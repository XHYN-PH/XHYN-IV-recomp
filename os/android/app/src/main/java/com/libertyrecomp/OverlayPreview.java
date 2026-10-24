package com.libertyrecomp;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import java.util.Map;

/** Settings preview using the same safe-area placement math as the live HUD. */
final class OverlayPreview extends View {
    private final Activity activity;private final Map<String,String> draft;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    OverlayPreview(Activity activity,Map<String,String> draft){super(activity);this.activity=activity;this.draft=draft;setContentDescription("Performance overlay position preview with sample values");}
    private float number(String key,float fallback){try{float value=Float.parseFloat(draft.get(key));return Float.isFinite(value)?value:fallback;}catch(Exception error){return fallback;}}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int screenWidth=activity.getResources().getDisplayMetrics().widthPixels,screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
        float scale=Math.min((getWidth()-8f)/screenWidth,(getHeight()-8f)/screenHeight);if(scale<=0)return;
        canvas.save();canvas.translate((getWidth()-screenWidth*scale)/2,(getHeight()-screenHeight*scale)/2);canvas.scale(scale,scale);
        paint.setColor(0xff30363b);canvas.drawRoundRect(0,0,screenWidth,screenHeight,18,18,paint);
        paint.setTextSize(ModernUi.dp(activity,11));paint.setColor(ModernUi.MUTED);canvas.drawText("Sample preview",ModernUi.dp(activity,12),ModernUi.dp(activity,30),paint);
        if(!"off".equals(draft.getOrDefault("gta4_performance_overlay","off"))){
            paint.setTextSize(number("gta4_overlay_text_size",12)*getResources().getDisplayMetrics().scaledDensity);
            java.util.List<String> sample=new java.util.ArrayList<>();sample.add("30.0 FPS · 33.3 ms");
            if("detailed".equals(draft.get("gta4_performance_overlay"))){
                if("true".equals(draft.get("gta4_overlay_ram")))sample.add("RAM 2048 MB");
                if("true".equals(draft.get("gta4_overlay_thermal")))sample.add("Normal");
                sample.add(draft.getOrDefault("gta4_native_anti_aliasing","fxaa").toUpperCase(java.util.Locale.ROOT)+" · VSync");
            }
            String[] lines=sample.toArray(new String[0]);
            if("true".equals(draft.get("gta4_overlay_compact")))lines=new String[]{String.join(" · ",lines)};
            int padding=ModernUi.dp(activity,7),lineHeight=(int)Math.ceil(paint.getFontSpacing()),boxWidth=0;
            for(String line:lines)boxWidth=Math.max(boxWidth,(int)Math.ceil(paint.measureText(line))+padding*2);
            boxWidth=Math.min(screenWidth,boxWidth);int boxHeight=lineHeight*lines.length+ModernUi.dp(activity,8);
            WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
            int left=cutout==null?0:cutout.getSafeInsetLeft(),right=cutout==null?0:cutout.getSafeInsetRight(),top=Math.max(ModernUi.dp(activity,52),cutout==null?0:cutout.getSafeInsetTop()),bottom=cutout==null?0:cutout.getSafeInsetBottom();
            if(insets!=null)bottom=Math.max(bottom,insets.getMandatorySystemGestureInsets().bottom);
            float[] point=OverlayPlacement.place(draft.getOrDefault("gta4_overlay_position","top_right"),number("gta4_overlay_x",.5f),number("gta4_overlay_y",.1f),screenWidth,screenHeight,boxWidth,boxHeight,left,top,right,bottom,ModernUi.dp(activity,number("gta4_overlay_edge_gap",2)));
            canvas.save();canvas.clipRect(point[0],point[1],point[0]+boxWidth,point[1]+boxHeight);
            paint.setColor(0xb0101214);paint.setAlpha(Math.round(176*number("gta4_overlay_opacity",1)));canvas.drawRoundRect(point[0],point[1],point[0]+boxWidth,point[1]+boxHeight,7,7,paint);
            paint.setColor(ModernUi.TEXT);paint.setAlpha(Math.round(255*number("gta4_overlay_opacity",1)));for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],point[0]+padding,point[1]+ModernUi.dp(activity,4)-paint.ascent()+i*lineHeight,paint);canvas.restore();
        }
        canvas.restore();paint.setAlpha(255);
    }
}

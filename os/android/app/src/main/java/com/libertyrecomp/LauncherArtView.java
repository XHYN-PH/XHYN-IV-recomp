package com.libertyrecomp;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.animation.ValueAnimator;

/** Full-screen city artwork, IV corner icon, and bottom navigation shade. */
final class LauncherArtView extends View {
    private final Bitmap art,icon;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private float navigationTop=Float.NaN;
    private final RectF imageRect=new RectF();
    private LinearGradient shade;
    private ValueAnimator drift;
    private float pan;
    LauncherArtView(Context context){super(context);art=BitmapFactory.decodeResource(getResources(),R.drawable.launcher_art);icon=BitmapFactory.decodeResource(getResources(),R.drawable.launcher_iv);}
    void setNavigationTop(float top){if(Float.isFinite(top)&&(!Float.isFinite(navigationTop)||Math.abs(top-navigationTop)>.5f)){navigationTop=top;shade=null;invalidate();}}
    private void updateMotion(){
        if(drift!=null){drift.cancel();drift=null;}
        if(!isAttachedToWindow()||!isShown()||!hasWindowFocus()||!ModernUi.motion(getContext()))return;
        drift=ValueAnimator.ofFloat(-1,1);drift.setDuration(18000);drift.setRepeatCount(ValueAnimator.INFINITE);drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.addUpdateListener(a->{pan=(float)a.getAnimatedValue();invalidate();});drift.start();
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateMotion();}
    @Override protected void onDetachedFromWindow(){if(drift!=null){drift.cancel();drift=null;}super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);updateMotion();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateMotion();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);shade=null;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(ModernUi.BG);float density=getResources().getDisplayMetrics().density;
        if(art!=null){float scale=Math.max(getWidth()/(float)art.getWidth(),getHeight()/(float)art.getHeight())*1.035f;float w=art.getWidth()*scale,h=art.getHeight()*scale,dx=pan*Math.min((w-getWidth())*.48f,getWidth()*.012f);imageRect.set((getWidth()-w)/2+dx,(getHeight()-h)/2,(getWidth()+w)/2+dx,(getHeight()+h)/2);canvas.drawBitmap(art,null,imageRect,paint);}
        // One continuous blend: the light title fade never ends at an opaque
        // rectangle edge. Anchor the transition to the actual navigation row.
        float height=Math.max(1,getHeight()),nav=Float.isFinite(navigationTop)?navigationTop:height-118*density;
        float start=Math.max(0,Math.min(height-1,nav-140*density));
        float title=Math.max(start,Math.min(height,nav-20*density));
        float dark=Math.max(title,Math.min(height,nav+58*density));
        if(shade==null)shade=new LinearGradient(0,start,0,height,
            new int[]{0x00000000,0x20000000,0x9008090a,0xcc08090a},
            new float[]{0,(title-start)/(height-start),(dark-start)/(height-start),1},Shader.TileMode.CLAMP);
        paint.setShader(shade);
        canvas.drawRect(0,0,getWidth(),height,paint);paint.setShader(null);
        if(icon!=null){float size=Math.min(getHeight()*.28f,112*density),right=getWidth()-24*density,top=14*density;imageRect.set(right-size,top,right,top+size);canvas.drawBitmap(icon,null,imageRect,paint);}
    }
}

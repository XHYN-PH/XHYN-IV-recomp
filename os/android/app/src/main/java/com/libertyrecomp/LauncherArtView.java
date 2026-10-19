package com.libertyrecomp;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Full-screen city artwork, IV corner icon, and bottom navigation shade. */
final class LauncherArtView extends View {
    private final Bitmap art,icon;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private float navigationTop=Float.NaN;
    LauncherArtView(Context context){super(context);art=BitmapFactory.decodeResource(getResources(),R.drawable.launcher_art);icon=BitmapFactory.decodeResource(getResources(),R.drawable.launcher_iv);}
    void setNavigationTop(float top){if(Float.isFinite(top)&&(!Float.isFinite(navigationTop)||Math.abs(top-navigationTop)>.5f)){navigationTop=top;invalidate();}}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(ModernUi.BG);float density=getResources().getDisplayMetrics().density;
        if(art!=null){float scale=Math.max(getWidth()/(float)art.getWidth(),getHeight()/(float)art.getHeight());float w=art.getWidth()*scale,h=art.getHeight()*scale;canvas.drawBitmap(art,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),paint);}
        // One continuous blend: the light title fade never ends at an opaque
        // rectangle edge. Anchor the transition to the actual navigation row.
        float height=Math.max(1,getHeight()),nav=Float.isFinite(navigationTop)?navigationTop:height-118*density;
        float start=Math.max(0,Math.min(height-1,nav-140*density));
        float title=Math.max(start,Math.min(height,nav-20*density));
        float dark=Math.max(title,Math.min(height,nav+58*density));
        paint.setShader(new LinearGradient(0,start,0,height,
            new int[]{0x00ffffff,0x80ffffff,0xe6090909,0xff090909},
            new float[]{0,(title-start)/(height-start),(dark-start)/(height-start),1},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,getWidth(),height,paint);paint.setShader(null);
        if(icon!=null){float size=Math.min(getHeight()*.28f,112*density),right=getWidth()-24*density,top=14*density;canvas.drawBitmap(icon,null,new RectF(right-size,top,right,top+size),paint);}
    }
}

package com.libertyrecomp;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Small scalable icons; no font-dependent emoji or rectangular label. */
final class GameButtonIcon extends Drawable {
    private final boolean controller;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha=255;
    GameButtonIcon(boolean controller){this.controller=controller;}
    public void draw(Canvas canvas) {
        Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/32f,b.height()/32f);
        paint.setColor(Color.WHITE);paint.setAlpha(alpha);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);
        if(controller) {
            canvas.drawRoundRect(4,8,28,24,5,5,paint);canvas.drawLine(8,16,16,16,paint);canvas.drawLine(12,12,12,20,paint);
            paint.setStyle(Paint.Style.FILL);canvas.drawCircle(23,13,1.6f,paint);canvas.drawCircle(20,18,1.6f,paint);
        }else for(int y=10;y<=22;y+=6)canvas.drawLine(7,y,25,y,paint);
        canvas.restore();
    }
    public void setAlpha(int value){alpha=value;invalidateSelf();}
    public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @SuppressWarnings("deprecation")public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

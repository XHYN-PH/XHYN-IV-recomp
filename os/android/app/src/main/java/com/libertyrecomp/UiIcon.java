package com.libertyrecomp;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small original line glyphs. Geometry stays sharp at every Android density. */
final class UiIcon extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String name;private final int color;
    UiIcon(String name,int color){this.name=name;this.color=color;}
    private void line(Canvas c,float... points){for(int i=0;i+3<points.length;i+=2)c.drawLine(points[i],points[i+1],points[i+2],points[i+3],p);}
    public boolean isStateful(){return true;}
    protected boolean onStateChange(int[] states){invalidateSelf();return true;}
    public void draw(Canvas c){Rect b=getBounds();c.save();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);p.setColor(color);for(int state:getState())if(state==android.R.attr.state_pressed||state==android.R.attr.state_focused)p.setColor(ModernUi.BG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(name){
            case "left":line(c,15,5,8,12,15,19);break;
            case "right":line(c,9,5,16,12,9,19);break;
            case "up":line(c,5,15,12,8,19,15);break;
            case "down":line(c,5,9,12,16,19,9);break;
            case "undo":line(c,9,4,3,10,9,16);c.drawArc(4,7,21,23,205,205,false,p);break;
            case "redo":line(c,15,4,21,10,15,16);c.drawArc(3,7,20,23,130,-205,false,p);break;
            case "import":line(c,12,2,12,15,7,10);line(c,12,15,17,10);line(c,3,16,3,21,21,21,21,16);break;
            case "export":line(c,12,16,12,3,7,8);line(c,12,3,17,8);line(c,3,16,3,21,21,21,21,16);break;
            case "reset":c.drawArc(4,4,20,20,-60,290,false,p);line(c,3,3,3,10,10,10);break;
            case "save":line(c,4,3,18,3,21,6,21,21,3,21,3,3);c.drawRect(7,3,16,9,p);c.drawRect(7,14,17,21,p);break;
            case "position":line(c,12,2,12,22);line(c,2,12,22,12);line(c,8,6,12,2,16,6);line(c,8,18,12,22,16,18);break;
            case "play":line(c,8,4,20,12,8,20,8,4);break;
            case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
            case "back":line(c,13,5,6,12,13,19);line(c,6,12,21,12);break;
            case "exit":line(c,10,4,4,4,4,20,10,20);line(c,10,12,21,12,17,8);line(c,21,12,17,16);break;
            case "trash":line(c,4,6,20,6);line(c,9,3,15,3);line(c,6,6,7,21,17,21,18,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            case "folder":line(c,3,7,3,20,21,20,21,7,12,7,9,4,3,4,3,7);break;
            case "controls":c.drawRoundRect(2,6,22,19,5,5,p);line(c,6,12,12,12);line(c,9,9,9,15);c.drawCircle(17,11,1,p);c.drawCircle(19,14,1,p);break;
            case "touch":c.drawCircle(11,5,3,p);line(c,11,4,11,13,8,10,6,12,10,20,17,20,20,12,18,10,16,11,14,9,13,10,13,5);break;
            case "trophy":line(c,7,4,17,4,16,13,12,16,8,13,7,4);line(c,12,16,12,21);line(c,8,21,16,21);line(c,7,6,3,6,4,12,8,13);line(c,17,6,21,6,20,12,16,13);break;
            case "driver":c.drawRoundRect(5,5,19,19,2,2,p);c.drawRect(9,9,15,15,p);for(int n=8;n<19;n+=4){line(c,n,2,n,5);line(c,n,19,n,22);line(c,2,n,5,n);line(c,19,n,22,n);}break;
            case "cheats":line(c,13,2,5,13,11,13,10,22,20,10,13,10,13,2);break;
            case "diagnostics":line(c,2,13,6,13,9,6,13,19,16,10,19,13,22,13);break;
            case "gyro":c.drawRoundRect(7,3,17,21,2,2,p);line(c,2,10,2,6,6,6);line(c,22,14,22,18,18,18);c.drawCircle(12,17,1,p);break;
            case "volume":line(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);c.drawArc(11,5,22,19,-65,130,false,p);break;
            case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawCircle(12,7,0.7f,p);break;
            case "more":for(int n=5;n<=19;n+=7)c.drawCircle(n,12,1,p);break;
            default:for(int n=5;n<21;n+=7){line(c,3,n,21,n);c.drawCircle(n==12?8:16,n,2,p);}break;
        }c.restore();}
    public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

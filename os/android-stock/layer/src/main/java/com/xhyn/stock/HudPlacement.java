package com.xhyn.stock;

import android.graphics.RectF;

final class HudPlacement {
    static float unit(float n) { return Float.isFinite(n)?Math.max(0,Math.min(1,n)):0; }
    static RectF bounds(float width,float height,float textWidth,float textHeight,float x,float y) {
        float w=Math.max(0,Math.min(width,textWidth)),h=Math.max(0,Math.min(height,textHeight));
        float left=Math.max(0,width-w)*unit(x),top=Math.max(0,height-h)*unit(y);
        return new RectF(left,top,left+w,top+h);
    }
    private HudPlacement(){}
}

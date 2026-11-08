package com.xhyn.stock;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Canvas owns the entire viewport. Floating tools never reduce the placement bounds. */
final class OverlayEditor {
    private final PanelActivity a; private float x,y;private CanvasView canvas;private LinearLayout tools;
    private final Deque<float[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    OverlayEditor(PanelActivity a){this.a=a;}
    void show() {
        JSONObject values=LayerPreferences.read(a);x=HudPlacement.unit((float)values.optDouble("x",0));y=HudPlacement.unit((float)values.optDouble("y",0));
        FrameLayout root=new FrameLayout(a);root.setBackgroundColor(Ui.BG);Ui.immersive(a.getWindow());
        canvas=new CanvasView();root.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        tools=Ui.card(a);tools.addView(Ui.text(a,"Overlay placement",17,Ui.TEXT));
        tools.addView(Ui.text(a,"Drag the preview. D-pad moves it; A hides tools.",12,Ui.MUTED));
        pair(tools,"Top left",()->preset(0,0),"Bottom right",()->preset(1,1));
        pair(tools,"Undo",()->history(undo,redo),"Redo",()->history(redo,undo));
        pair(tools,"Save",()->{
            try { LayerPreferences.update(a,new JSONObject().put("x",x).put("y",y));a.dirty=false;a.finish(); }
            catch(Exception e){Ui.error(a,e);}
        },"Back",a::onBackPressed);
        ScrollView toolScroll=new ScrollView(a){@Override protected void onMeasure(int width,int height){
            int limit=Math.max(Ui.dp(a,80),a.getResources().getDisplayMetrics().heightPixels-Ui.dp(a,32));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }};toolScroll.setClipToPadding(false);toolScroll.addView(tools);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(Ui.dp(a,280),-2,Gravity.CENTER);root.addView(toolScroll,tp);
        Button toggle=Ui.button(a,"Tools",false);toggle.setOnClickListener(v->{toolScroll.setVisibility(toolScroll.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);canvas.requestFocus();});
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL);bp.rightMargin=Ui.dp(a,12);root.addView(toggle,bp);
        a.root=Ui.column(a); // Only used for safe initial focus; actual content is the full FrameLayout.
        a.setContentView(root);canvas.requestFocus();canvas.setOnClickListener(v->{toolScroll.setVisibility(toolScroll.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);canvas.requestFocus();});
        root.setOnApplyWindowInsetsListener((v,in)->{canvas.invalidate();return in;});
        Ui.info(a,"Place the overlay","The full screen is available, including the top and bottom edges. Hide Tools to reach behind the panel. Preview values are placeholders; game measurements appear during play.");
    }
    private void pair(LinearLayout parent,String left,Runnable a1,String right,Runnable a2){
        LinearLayout row=new LinearLayout(a);Button one=Ui.button(a,left,false),two=Ui.button(a,right,false);
        row.addView(one,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=Ui.dp(a,6);row.addView(two,p);Ui.add(parent,row);one.setOnClickListener(v->a1.run());two.setOnClickListener(v->a2.run());
    }
    private void push(){undo.push(new float[]{x,y});while(undo.size()>100)undo.removeLast();redo.clear();a.dirty=true;}
    private void preset(float nx,float ny){push();x=nx;y=ny;canvas.invalidate();canvas.requestFocus();}
    private void history(Deque<float[]> from,Deque<float[]> to){if(from.isEmpty())return;to.push(new float[]{x,y});float[] value=from.pop();x=value[0];y=value[1];a.dirty=true;canvas.invalidate();canvas.requestFocus();}
    private final class CanvasView extends View implements Navigation.DirectionalEditor {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);RectF box=new RectF();float dx,dy,downX,downY;int pointer=-1;boolean moved;
        CanvasView(){super(a);setFocusable(true);setFocusableInTouchMode(true);setContentDescription("Full screen overlay position. D-pad moves the overlay.");}
        @Override protected void onDraw(Canvas c) {
            Rect safe=MonitoringHud.safe(this);float w=getWidth()-safe.left-safe.right,h=getHeight()-safe.top-safe.bottom;
            paint.setColor(0xff263346);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(a,1));c.drawRect(safe.left,safe.top,getWidth()-safe.right,getHeight()-safe.bottom,paint);
            JSONObject options=LayerPreferences.read(a);paint.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,Math.max(9,Math.min(24,options.optInt("textSize",13))),a.getResources().getDisplayMetrics()));paint.setTypeface(Typeface.MONOSPACE);
            String[] lines={"CPU app — · — cores","RAM app — MB PSS","Battery —% · —°C","Thermal —"};float tw=0;for(String line:lines)tw=Math.max(tw,paint.measureText(line));
            float line=paint.getFontSpacing(),pad=Ui.dp(a,6);box=HudPlacement.bounds(w,h,tw+pad*2,line*lines.length+pad*2,x,y);box.offset(safe.left,safe.top);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xcc101824);c.drawRoundRect(box,Ui.dp(a,6),Ui.dp(a,6),paint);
            paint.setColor(Ui.ACCENT);for(int i=0;i<lines.length;i++)c.drawText(lines[i],box.left+pad,box.top+pad-paint.ascent()+i*line,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {pointer=e.getPointerId(0);moved=false;downX=e.getX();downY=e.getY();dx=e.getX()-box.left;dy=e.getY()-box.top;requestFocus();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE && pointer>=0) {
                int i=e.findPointerIndex(pointer);if(i<0)return true;Rect safe=MonitoringHud.safe(this);
                if(!moved){if(Math.hypot(e.getX(i)-downX,e.getY(i)-downY)<ViewConfiguration.get(a).getScaledTouchSlop())return true;push();moved=true;}
                x=HudPlacement.unit((e.getX(i)-dx-safe.left)/Math.max(1,getWidth()-safe.left-safe.right-box.width()));
                y=HudPlacement.unit((e.getY(i)-dy-safe.top)/Math.max(1,getHeight()-safe.top-safe.bottom-box.height()));invalidate();return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(e.getActionMasked()==MotionEvent.ACTION_UP&&!moved)performClick();pointer=-1;return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        public boolean direction(int key) {
            push();if(key==KeyEvent.KEYCODE_DPAD_LEFT)x=HudPlacement.unit(x-.01f);if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x=HudPlacement.unit(x+.01f);
            if(key==KeyEvent.KEYCODE_DPAD_UP)y=HudPlacement.unit(y-.01f);if(key==KeyEvent.KEYCODE_DPAD_DOWN)y=HudPlacement.unit(y+.01f);invalidate();return true;
        }
    }
}

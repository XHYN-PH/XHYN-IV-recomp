package com.xhyn.stock;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Full viewport preview using v442's layout coordinates, with reversible staged edits. */
final class TouchEditor {
    private static final String[] MODES={"On foot","Car","Motorcycle","Boat","Helicopter","Other vehicle","Passenger","Phone","Freefall","Parachute","Shops & selection"};
    private final PanelActivity a;private int mode,selected=-1;private CanvasView canvas;private TextView label,detail;
    private AlertDialog dialog;private LinearLayout tools;private List<Point> points=new ArrayList<>();
    private final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private static final class Point {
        final int id;final float x,y,r;final String label;
        Point(JSONObject j) throws JSONException{id=j.getInt("action");x=(float)j.getDouble("x");y=(float)j.getDouble("y");r=(float)j.getDouble("r");label=j.getString("label");
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(r)||r<=0)throw new JSONException("Invalid stock control bounds");}
    }
    private static final class State {final Map<Integer,List<Point>> layouts=new LinkedHashMap<>();}
    TouchEditor(PanelActivity a){this.a=a;}
    void show() {
        FrameLayout root=new FrameLayout(a);root.setBackgroundColor(Ui.BG);canvas=new CanvasView();root.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        tools=Ui.card(a);label=Ui.text(a,MODES[mode],17,Ui.TEXT);tools.addView(label);
        LinearLayout switches=new LinearLayout(a);Button prev=Ui.button(a,"‹ Mode",false),next=Ui.button(a,"Mode ›",false);
        switches.addView(prev,new LinearLayout.LayoutParams(0,-2,1));switches.addView(next,new LinearLayout.LayoutParams(0,-2,1));Ui.add(tools,switches);
        prev.setOnClickListener(v->changeMode(-1));next.setOnClickListener(v->changeMode(1));
        detail=Ui.text(a,"Select a button, then drag. D-pad nudges; LB/RB selects buttons.",12,Ui.MUTED);Ui.add(tools,detail);
        LinearLayout sizes=new LinearLayout(a);Button small=Ui.button(a,"Smaller",false),large=Ui.button(a,"Larger",false);
        sizes.addView(small,new LinearLayout.LayoutParams(0,-2,1));sizes.addView(large,new LinearLayout.LayoutParams(0,-2,1));Ui.add(tools,sizes);
        small.setOnClickListener(v->resize(.9f));large.setOnClickListener(v->resize(1.1f));
        Ui.action(a,tools,"Undo",()->history(undo,redo));Ui.action(a,tools,"Redo",()->history(redo,undo));
        Ui.action(a,tools,"Compact this mode",()->{
            try{push();for(Point p:new ArrayList<>(points))move(p,p.x,p.y,p.r*.85f);reload();}catch(IOException e){Ui.error(a,e);}
        });
        Ui.action(a,tools,"Reset positions",()->Ui.dialog(a,"Reset button positions?","Stage the stock positions. You can undo this or discard from Touch controls.","Reset",()->{
            try{State state=capture(true);undo.push(state);redo.clear();StockApi.touch("nativeReset",new Class[]{boolean.class},false);a.dirty=true;reload();}catch(IOException e){Ui.error(a,e);}
        }));
        Ui.action(a,tools,"Done · review & save",()->dialog.dismiss());
        ScrollView sc=Ui.scroll(a,tools);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(Ui.dp(a,270),-2,Gravity.CENTER);root.addView(sc,tp);
        Button toggle=Ui.button(a,"Tools",false);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL);bp.rightMargin=Ui.dp(a,8);root.addView(toggle,bp);
        toggle.setOnClickListener(v->{sc.setVisibility(sc.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);canvas.requestFocus();});
        canvas.setOnClickListener(v->{sc.setVisibility(sc.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);canvas.requestFocus();});
        dialog=new AlertDialog.Builder(a).create();dialog.setView(root,0,0,0,0);dialog.show();
        Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);Ui.immersive(w);w.getDecorView().setPadding(0,0,0,0);w.setLayout(-1,-1);Navigation.install(a,w,false,null);}
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{sc.getLayoutParams().height=Math.min(Ui.dp(a,440),Math.max(Ui.dp(a,80),b-t-Ui.dp(a,24)));sc.requestLayout();});
        canvas.requestFocus();
    }
    private List<Point> layout(int context) throws IOException {
        try {
            Rect safe=MonitoringHud.safe(canvas);float w=canvas.getWidth()-safe.left-safe.right,h=canvas.getHeight()-safe.top-safe.bottom;
            if(w<=0||h<=0)return new ArrayList<>();
            String json=(String)StockApi.touch("nativeLayout",new Class[]{int.class,float.class,float.class,float.class},context,w,h,a.getResources().getDisplayMetrics().density);
            JSONArray all=new JSONArray(json);if(all.length()>128)throw new IOException("Too many stock controls.");List<Point> out=new ArrayList<>();for(int i=0;i<all.length();i++)out.add(new Point(all.getJSONObject(i)));return out;
        }catch(JSONException e){throw new IOException("Could not read the stock layout.",e);}
    }
    private void reload() throws IOException {points=layout(mode);label.setText(MODES[mode]);canvas.invalidate();}
    private State capture(boolean all) throws IOException {
        State state=new State();if(all)for(int i=0;i<MODES.length;i++)state.layouts.put(i,layout(i));else state.layouts.put(mode,new ArrayList<>(points));
        layout(mode);return state;
    }
    private void push() throws IOException {undo.push(capture(false));while(undo.size()>100)undo.removeLast();redo.clear();a.dirty=true;}
    private void history(Deque<State> from,Deque<State> to) {
        if(from.isEmpty())return;try {
            State state=from.peek();to.push(capture(state.layouts.size()>1));
            for(Map.Entry<Integer,List<Point>> entry:state.layouts.entrySet()) {layout(entry.getKey());for(Point p:entry.getValue())move(p,p.x,p.y,p.r);}
            from.pop();a.dirty=true;reload();canvas.requestFocus();
        }catch(IOException e){Ui.error(a,e);}
    }
    private void changeMode(int delta) {mode=Math.floorMod(mode+delta,MODES.length);selected=-1;try{reload();canvas.requestFocus();}catch(IOException e){Ui.error(a,e);}}
    private Point selected() {for(Point p:points)if(p.id==selected)return p;return null;}
    private void move(Point p,float x,float y,float radius) throws IOException {
        Rect safe=MonitoringHud.safe(canvas);float w=canvas.getWidth()-safe.left-safe.right,h=canvas.getHeight()-safe.top-safe.bottom;
        float r=Math.max(Ui.dp(a,12),Math.min(Math.min(w,h)/3,radius));x=Math.max(r,Math.min(w-r,x));y=Math.max(r,Math.min(h-r,y));
        if(!Boolean.TRUE.equals(StockApi.touch("nativeMove",new Class[]{int.class,float.class,float.class,float.class},p.id,x,y,r)))throw new IOException("This placement is outside the stock control limits.");
    }
    private void resize(float factor) {Point p=selected();if(p==null){detail.setText("Select a button first.");return;}try{push();move(p,p.x,p.y,p.r*factor);reload();}catch(IOException e){Ui.error(a,e);}}
    private final class CanvasView extends View implements Navigation.DirectionalEditor {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int pointer=-1;float dx,dy,downX,downY;boolean started,controlTap,tap;
        CanvasView(){super(a);setFocusable(true);setFocusableInTouchMode(true);setContentDescription("Full screen touch layout. Drag a control; use shoulder buttons to select.");}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){try{reload();}catch(IOException e){detail.setText(FilesEx.error(e));}}
        @Override protected void onDraw(Canvas c) {
            Rect safe=MonitoringHud.safe(this);c.save();c.translate(safe.left,safe.top);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);
            for(Point p:points) {paint.setColor(p.id==selected?0xff4e7394:0xff26384a);c.drawCircle(p.x,p.y,p.r,paint);paint.setColor(p.id==selected?Ui.ACCENT:Ui.TEXT);
                paint.setTextSize(Math.min(Ui.dp(a,13),p.r*.55f));String value=p.label;while(value.length()>1&&paint.measureText(value)>p.r*1.8f)value=value.substring(0,value.length()-1);
                c.drawText(value,p.x,p.y-(paint.ascent()+paint.descent())/2,paint);
            }c.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            Rect safe=MonitoringHud.safe(this);
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                downX=e.getX();downY=e.getY();tap=true;controlTap=false;pointer=-1;
                requestFocus();float x=e.getX()-safe.left,y=e.getY()-safe.top;Point nearest=null;float distance=Float.MAX_VALUE;
                for(Point p:points){float d=(float)Math.hypot(x-p.x,y-p.y);if(d<=p.r*1.3f&&d<distance){distance=d;nearest=p;}}
                if(nearest==null)return true;controlTap=true;selected=nearest.id;pointer=e.getPointerId(0);dx=x-nearest.x;dy=y-nearest.y;started=false;
                detail.setText(nearest.label+" · drag to move, Smaller/Larger to resize");invalidate();return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE && pointer>=0) {
                Point p=selected();int i=e.findPointerIndex(pointer);if(p==null||i<0)return true;
                try {if(!started){if(Math.hypot(e.getX(i)-downX,e.getY(i)-downY)<ViewConfiguration.get(a).getScaledTouchSlop())return true;push();started=true;tap=false;}move(p,e.getX(i)-safe.left-dx,e.getY(i)-safe.top-dy,p.r);reload();}
                catch(IOException error){detail.setText(FilesEx.error(error));}return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(a).getScaledTouchSlop())tap=false;
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(e.getActionMasked()==MotionEvent.ACTION_UP&&tap&&!controlTap)performClick();pointer=-1;return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        public boolean direction(int key) {
            Point p=selected();if(p==null){if(!points.isEmpty()){selected=points.get(0).id;invalidate();}return true;}
            try{push();float step=Ui.dp(a,2);move(p,p.x+(key==KeyEvent.KEYCODE_DPAD_RIGHT?step:key==KeyEvent.KEYCODE_DPAD_LEFT?-step:0),p.y+(key==KeyEvent.KEYCODE_DPAD_DOWN?step:key==KeyEvent.KEYCODE_DPAD_UP?-step:0),p.r);reload();}
            catch(IOException e){detail.setText(FilesEx.error(e));}return true;
        }
        @Override public boolean onKeyDown(int key,KeyEvent event) {
            if(key==KeyEvent.KEYCODE_BUTTON_L1||key==KeyEvent.KEYCODE_BUTTON_R1) {
                if(points.isEmpty())return true;int index=0;for(int i=0;i<points.size();i++)if(points.get(i).id==selected)index=i;
                selected=points.get(Math.floorMod(index+(key==KeyEvent.KEYCODE_BUTTON_R1?1:-1),points.size())).id;invalidate();return true;
            }return super.onKeyDown(key,event);
        }
    }
}

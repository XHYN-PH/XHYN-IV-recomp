package com.libertyrecomp;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Full-screen preview using gameplay geometry, icons and saved edits. */
public final class TouchEditorActivity extends Activity {
    private File root;
    private TouchLayoutDocument document;
    private JSONArray contexts;
    private int context;
    private float buttonScale=1,cameraScale=1,panelX=.35f,panelY;
    private boolean xbox,dirty,collapsed,hints=true,actionNames;
    private String selectedKey;
    private FrameLayout page;
    private LinearLayout panel,selectionTools;
    private ScrollView toolsScroll;
    private Editor canvas;
    private TextView selected,amount;
    private SeekBar size;
    private CheckBox hidden;
    private Button picker,collapse;
    private final Map<String,Bitmap> icons=new HashMap<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams attrs=getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        hideSystemBars();xbox=getIntent().getBooleanExtra("xbox",false);
        root=getExternalFilesDir(null);if(root==null)root=getFilesDir();
        try{
            TouchLayoutDocument.migrate(root,getFilesDir());document=TouchLayoutDocument.read(root);
            try(InputStream in=getAssets().open(xbox?"xbox-defaults.json":"touch-defaults.json")){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
                contexts=new JSONArray(bytes.toString("UTF-8"));
            }
        }catch(Exception e){Toast.makeText(this,"Cannot open layouts: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();return;}
        try{
            Map<String,String> settings=MobileConfig.read(MobileConfig.file(root));
            buttonScale=Float.parseFloat(settings.getOrDefault("gta4_touch_gamepad_scale","1"));
            cameraScale=Float.parseFloat(settings.getOrDefault("gta4_touch_camera_scale","1"));
            hints=!"false".equals(settings.get("gta4_touch_button_hints"));actionNames="true".equals(settings.get("gta4_touch_labels"));
        }catch(Exception ignored){}
        buttonScale=finite(buttonScale,.5f,2,1);cameraScale=finite(cameraScale,.25f,3,1);
        context=Math.max(0,Math.min(contexts.length()-1,state==null?getIntent().getIntExtra("context",0):state.getInt("context",0)));
        if(state!=null){
            dirty=state.getBoolean("dirty");collapsed=state.getBoolean("collapsed");selectedKey=state.getString("selected");
            panelX=finite(state.getFloat("panel_x",.35f),0,1,.35f);panelY=finite(state.getFloat("panel_y",0),0,1,0);
            Bundle draft=state.getBundle("draft");
            if(draft!=null)forEachControl((key,c)->{
                float[] v=draft.getFloatArray(key);if(v==null||v.length!=4)return;
                TouchLayoutDocument.Entry e=document.get(key);e.dx=finite(v[0],-2000,2000,0);e.dy=finite(v[1],-800,800,0);
                e.scale=finite(v[2],.35f,4,1);e.hidden=v[3]==1;
            });
        }
        page=new FrameLayout(this);page.setBackgroundColor(Color.BLACK);
        canvas=new Editor();page.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        buildPanel();page.addView(panel,new FrameLayout.LayoutParams(-2,-2));setContentView(page);
        page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int width=Math.min(Math.max(1,page.getWidth()-dp(16)),Math.max(dp(300),Math.min(dp(600),Math.round(page.getWidth()*.62f))));
            if(panel.getLayoutParams().width!=width){panel.getLayoutParams().width=width;panel.requestLayout();}
            positionPanel();canvas.invalidate();
        });
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionPanel());
        updateSelection();
    }

    private void buildPanel(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackground(ModernUi.surface(this,0xf2141414,4));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,dp(6),0);header.setBackgroundColor(0xff252525);
        TextView title=ModernUi.text(this,xbox?"Xbox layout editor":"Mobile layout editor",15,ModernUi.TEXT);
        title.setTypeface(Typeface.MONOSPACE);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription("Drag this header to move the editor panel");header.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        title.setGravity(Gravity.CENTER_VERTICAL);enablePanelDrag(title);
        collapse=ModernUi.button(this,collapsed?"+":"−",false);collapse.setMinWidth(0);collapse.setPadding(dp(8),0,dp(8),0);
        collapse.setContentDescription("Collapse or expand editor tools");header.addView(collapse,new LinearLayout.LayoutParams(dp(40),dp(36)));
        Button back=ModernUi.button(this,"Back",false);back.setMinWidth(0);back.setPadding(dp(10),0,dp(10),0);header.addView(back,new LinearLayout.LayoutParams(-2,dp(36)));
        back.setOnClickListener(v->onBackPressed());panel.addView(header);
        toolsScroll=new ScrollView(this){
            @Override protected void onMeasure(int w,int h){
                int available=page.getHeight()>0?page.getHeight():getResources().getDisplayMetrics().heightPixels;
                int limit=Math.max(dp(64),available-dp(72));
                if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(h));
                super.onMeasure(w,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
            }
        };
        toolsScroll.setFillViewport(false);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),dp(6),dp(8),dp(8));
        picker=ModernUi.button(this,contextName()+(xbox?"":" ▾"),false);picker.setEnabled(!xbox);body.addView(picker,new LinearLayout.LayoutParams(-1,dp(42)));
        picker.setOnClickListener(v->{
            String[] labels=new String[contexts.length()];for(int i=0;i<labels.length;i++)labels[i]=contexts.optJSONObject(i).optString("name");
            ModernUi.choose(this,"Context",labels,context,index->{context=index;canvas.cancelDrag();selectedKey=null;picker.setText(contextName()+" ▾");updateSelection();canvas.invalidate();});
        });
        TextView help=ModernUi.text(this,"Drag a control to move it. Select one for size or visibility. Drag the header if it covers a control.",12,ModernUi.MUTED);
        help.setPadding(0,dp(6),0,dp(4));body.addView(help);
        selectionTools=new LinearLayout(this);selectionTools.setOrientation(LinearLayout.VERTICAL);
        selected=ModernUi.text(this,"",13,ModernUi.TEXT);selected.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);selectionTools.addView(selected);
        LinearLayout sizeRow=new LinearLayout(this);sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        amount=ModernUi.text(this,"",12,ModernUi.ACCENT);sizeRow.addView(amount,new LinearLayout.LayoutParams(dp(54),-2));
        size=new SeekBar(this);size.setMax(365);ModernUi.tint(size);sizeRow.addView(size,new LinearLayout.LayoutParams(0,-2,1));selectionTools.addView(sizeRow);
        hidden=new CheckBox(this);hidden.setText("Hidden in play");hidden.setTextColor(ModernUi.TEXT);selectionTools.addView(hidden);body.addView(selectionTools);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean user){
                if(user&&selectedKey!=null){document.get(selectedKey).scale=.35f+value*.01f;dirty=true;amount.setText(sizeLabel(document.get(selectedKey).scale));canvas.invalidate();}
            }
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        hidden.setOnCheckedChangeListener((button,on)->{if(selectedKey!=null&&document.get(selectedKey).hidden!=on){document.get(selectedKey).hidden=on;dirty=true;canvas.invalidate();}});
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        Button save=ModernUi.button(this,"Save layout",true),reset=ModernUi.button(this,"Reset context",false);
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.leftMargin=dp(8);actions.addView(reset,rp);body.addView(actions);
        save.setOnClickListener(v->saveAndClose());
        reset.setOnClickListener(v->{String prefix=contexts.optJSONObject(context).optString("id")+"/";document.entries.keySet().removeIf(k->k.startsWith(prefix));dirty=true;selectedKey=null;canvas.cancelDrag();updateSelection();canvas.invalidate();});
        toolsScroll.addView(body);toolsScroll.setVisibility(collapsed?View.GONE:View.VISIBLE);panel.addView(toolsScroll);
        collapse.setOnClickListener(v->{collapsed=!collapsed;toolsScroll.setVisibility(collapsed?View.GONE:View.VISIBLE);collapse.setText(collapsed?"+":"−");});
    }

    private int dp(float value){return ModernUi.dp(this,value);}
    private static float finite(float v,float min,float max,float fallback){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;}
    private static String sizeLabel(float value){return String.format(Locale.ROOT,"%.2f×",value);}
    private String contextName(){return contexts.optJSONObject(context).optString("name");}
    private void positionPanel(){
        if(page==null||panel==null||page.getWidth()<=0)return;
        panel.setX(Math.max(dp(8),Math.min(page.getWidth()-panel.getWidth()-dp(8),page.getWidth()*panelX)));
        panel.setY(Math.max(dp(8),Math.min(page.getHeight()-panel.getHeight()-dp(8),page.getHeight()*panelY)));
    }
    private void enablePanelDrag(View header){
        header.setOnTouchListener(new View.OnTouchListener(){float x,y,startX,startY;
            public boolean onTouch(View view,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();startX=panel.getX();startY=panel.getY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&page.getWidth()>0&&page.getHeight()>0){
                    panelX=(startX+e.getRawX()-x)/page.getWidth();panelY=(startY+e.getRawY()-y)/page.getHeight();positionPanel();return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_UP){view.performClick();return true;}return true;
            }
        });
    }
    private void updateSelection(){
        JSONObject control=null;if(selectedKey!=null)for(int i=0;i<canvas.controls().length();i++){
            JSONObject c=canvas.controls().optJSONObject(i);if(canvas.key(c).equals(selectedKey)){control=c;break;}
        }
        selectionTools.setVisibility(control==null?View.GONE:View.VISIBLE);
        if(control==null){selectedKey=null;return;}
        TouchLayoutDocument.Entry e=document.get(selectedKey);selected.setText("Selected: "+control.optString("label"));
        amount.setText(sizeLabel(e.scale));size.setProgress(Math.round((e.scale-.35f)*100));hidden.setChecked(e.hidden);
    }
    private interface ControlVisitor {void accept(String key,JSONObject control);}
    private void forEachControl(ControlVisitor visitor){
        for(int i=0;i<contexts.length();i++){JSONObject ctx=contexts.optJSONObject(i);JSONArray list=ctx.optJSONArray("controls");
            for(int j=0;j<list.length();j++){JSONObject c=list.optJSONObject(j);visitor.accept(ctx.optString("id")+"/"+c.optString("id"),c);}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(document!=null){
            state.putInt("context",context);state.putBoolean("dirty",dirty);state.putBoolean("collapsed",collapsed);state.putString("selected",selectedKey);
            state.putFloat("panel_x",panelX);state.putFloat("panel_y",panelY);Bundle draft=new Bundle();
            forEachControl((key,c)->{TouchLayoutDocument.Entry e=document.get(key);draft.putFloatArray(key,new float[]{e.dx,e.dy,e.scale,e.hidden?1:0});});state.putBundle("draft",draft);
        }super.onSaveInstanceState(state);
    }
    private void saveAndClose(){
        try{document.save(root);setResult(RESULT_OK);Toast.makeText(this,"Layout saved.",Toast.LENGTH_SHORT).show();finish();}
        catch(IOException e){Toast.makeText(this,"Could not save layout: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){if(document!=null&&dirty)saveAndClose();else finish();}
    private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideSystemBars();}
    @Override protected void onDestroy(){icons.clear();super.onDestroy();}

    private final class Editor extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ColorFilter iconTint=new PorterDuffColorFilter(0xffdedede,PorterDuff.Mode.SRC_IN);
        private float lastX,lastY;
        private int pointer=-1;
        private JSONObject dragging;
        Editor(){super(TouchEditorActivity.this);setContentDescription("Full-screen control layout. Drag a control, then use the floating editor panel to resize or hide it.");}
        private JSONArray controls(){return contexts.optJSONObject(context).optJSONArray("controls");}
        private String key(JSONObject c){return contexts.optJSONObject(context).optString("id")+"/"+c.optString("id");}
        private RectF viewport(){
            RectF area=new RectF(0,0,getWidth(),getHeight());WindowInsets insets=getRootWindowInsets();
            if(!xbox&&insets!=null&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();area.set(cutout.getSafeInsetLeft(),cutout.getSafeInsetTop(),getWidth()-cutout.getSafeInsetRight(),getHeight()-cutout.getSafeInsetBottom());}
            return area;
        }
        private float unit(){return viewport().height()/737f;}
        private float x(JSONObject c){
            RectF a=viewport();float r=radius(c);TouchLayoutDocument.Entry e=document.get(key(c));
            if(xbox)return Math.max(a.left+r,Math.min(a.right-r,a.left+a.width()*(float)c.optDouble("xf")+a.height()*((float)c.optDouble("xh")+(float)c.optDouble("xs")*buttonScale)+e.dx*unit()));
            float value=((float)c.optDouble("x")+e.dx)*unit();String anchor=c.optString("anchor");
            return Math.max(a.left+r,Math.min(a.right-r,anchor.equals("right")?a.right-value:anchor.equals("center")?a.centerX()+value:a.left+value));
        }
        private float y(JSONObject c){
            RectF a=viewport();float r=radius(c);TouchLayoutDocument.Entry e=document.get(key(c));
            return Math.max(a.top+r,Math.min(a.bottom-r,a.top+(xbox?a.height()*((float)c.optDouble("yf")+(float)c.optDouble("ys")*buttonScale)+e.dy*unit():((float)c.optDouble("y")+e.dy)*unit())));
        }
        private float radius(JSONObject c){
            RectF a=viewport();float scale=document.get(key(c)).scale;
            float r=xbox?(float)c.optDouble("r")*a.height()*(c.optBoolean("buttons")?buttonScale:1)*(c.optBoolean("camera")?cameraScale:1)*scale:
                (float)c.optDouble("r")*unit()*buttonScale*(c.optString("id").equals("camera")?cameraScale:1)*scale;
            return Math.min(Math.min(a.width(),a.height())*.45f,Math.max(4,r));
        }
        private Bitmap icon(JSONObject c){
            String file=xbox&&c.optString("id").startsWith("dpad_")?c.optString("id").substring(5):c.optString("image");
            if(file.isEmpty())return null;
            if(!icons.containsKey(file)){Bitmap bitmap=null;try(InputStream in=getAssets().open("touch-icons/"+file+".png")){bitmap=BitmapFactory.decodeStream(in);}catch(IOException ignored){}icons.put(file,bitmap);}
            return icons.get(file);
        }
        @Override protected void onDraw(Canvas out){
            super.onDraw(out);out.drawColor(Color.BLACK);if(getWidth()<=0||getHeight()<=0)return;
            JSONArray controls=controls();paint.setTypeface(Typeface.MONOSPACE);
            for(int i=0;i<controls.length();i++){
                JSONObject c=controls.optJSONObject(i);String key=key(c);TouchLayoutDocument.Entry e=document.get(key);float x=x(c),y=y(c),r=radius(c);
                boolean stick=xbox?c.optString("id").endsWith("_stick"):c.optString("kind").equals("stick");
                paint.setStyle(Paint.Style.FILL);paint.setColor(0x30000000);out.drawCircle(x,y,r,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,r*.035f));paint.setColor(0xaae1e1e1);out.drawCircle(x,y,r,paint);
                paint.setStyle(Paint.Style.FILL);
                if(stick){paint.setColor(0x96d7d7d7);out.drawCircle(x,y,r*.34f,paint);}
                else{
                    Bitmap image=icon(c);
                    if(image!=null){float half=r*.82f;paint.setColorFilter(iconTint);paint.setAlpha(205);out.drawBitmap(image,null,new RectF(x-half,y-half,x+half,y+half),paint);paint.setColorFilter(null);paint.setAlpha(255);}
                    else{paint.setColor(0xffdedede);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(9,Math.min(dp(16),r*.7f)));out.drawText(c.optString("label"),x,y-(paint.ascent()+paint.descent())*.5f,paint);}
                }
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(key.equals(selectedKey)?3:2);paint.setColor(e.hidden?0xfff65a5a:0xfff6cf5c);out.drawCircle(x,y,r*1.12f,paint);
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xffdedede);paint.setTextAlign(Paint.Align.CENTER);
                String hint=c.optString("hint");float textY=y+r+Math.max(2,unit()*4);
                if(!xbox&&hints&&!hint.isEmpty()){
                    paint.setTextSize(Math.max(8,Math.min(r*.36f,19*unit())));textY=Math.min(textY,getHeight()-paint.descent()+paint.ascent());out.drawText(hint,x,textY-paint.ascent(),paint);textY+=paint.getTextSize()*1.2f;
                }
                if(!xbox&&actionNames){paint.setTextSize(Math.max(9,Math.min(r*.30f,17*unit())));out.drawText(c.optString("label"),x,Math.min(textY-paint.ascent(),getHeight()-paint.descent()),paint);}
            }
        }
        void cancelDrag(){dragging=null;pointer=-1;}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                pointer=event.getPointerId(0);float px=event.getX(),py=event.getY(),best=Float.MAX_VALUE;dragging=null;
                JSONArray controls=controls();for(int i=0;i<controls.length();i++){
                    JSONObject c=controls.optJSONObject(i);float capture=Math.max(radius(c)*1.2f,dp(22));float score=(float)Math.hypot(px-x(c),py-y(c))/capture;
                    if(score<=1&&score<best){best=score;dragging=c;}
                }
                if(dragging!=null){selectedKey=key(dragging);updateSelection();}lastX=px;lastY=py;invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&dragging!=null&&unit()>0){
                int index=event.findPointerIndex(pointer);if(index<0){cancelDrag();return true;}
                float px=event.getX(index),py=event.getY(index);TouchLayoutDocument.Entry e=document.get(selectedKey);
                float sign=dragging.optString("anchor").equals("right")?-1:1;
                float dx=finite(e.dx+sign*(px-lastX)/unit(),-2000,2000,0),dy=finite(e.dy+(py-lastY)/unit(),-800,800,0);
                dirty|=dx!=e.dx||dy!=e.dy;e.dx=dx;e.dy=dy;lastX=px;lastY=py;invalidate();return true;
            }
            if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer)){
                cancelDrag();if(action!=MotionEvent.ACTION_CANCEL)performClick();return true;
            }return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}

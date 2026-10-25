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
    private boolean grid=true,snap=true,guides=true,iconOutline,hitAreas;private float snapStrength=1;
    private int gridSize=20;
    private final ArrayDeque<Map<String,float[]>> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private static final int REQUEST_LAYOUT_IMPORT=740,REQUEST_LAYOUT_EXPORT=741;
    private File root;
    private TouchLayoutDocument document;
    private JSONArray contexts;
    private int context;
    private float buttonScale=1,cameraScale=1,buttonOpacity=1,cameraOpacity=1,panelX=.35f,panelY;
    private boolean xbox,dirty,collapsed,hints=true,actionNames;
    private String selectedKey;
    private FrameLayout page;
    private LinearLayout panel,selectionTools;
    private ScrollView toolsScroll;
    private Editor canvas;
    private TextView selected,amount;
    private SeekBar size;
    private CheckBox hidden;
    private Button picker,collapse,undoAction,redoAction;
    private final Map<String,Bitmap> icons=new HashMap<>();

    private final ControllerNavigation menuNavigation=new ControllerNavigation();
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return menuNavigation.motion(getWindow(),e)||super.dispatchGenericMotionEvent(e);}
    @Override public void finish(){super.finish();overridePendingTransition(0,ModernUi.motion(this)?R.anim.session_panel_out:0);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams attrs=getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        hideSystemBars();xbox=getIntent().getBooleanExtra("xbox",false);
        root=getExternalFilesDir(null);if(root==null)root=getFilesDir();
        android.content.SharedPreferences aids=getPreferences(MODE_PRIVATE);
        grid=aids.getBoolean("grid",true);snap=aids.getBoolean("snap",true);guides=aids.getBoolean("guides",true);hitAreas=aids.getBoolean("hit_areas",false);
        gridSize=Math.max(5,Math.min(40,aids.getInt("spacing",20)));snapStrength=finite(aids.getFloat("strength",1),.25f,1,1);
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
            cameraScale=Float.parseFloat(settings.getOrDefault("gta4_touch_camera_scale","1"));buttonOpacity=Float.parseFloat(settings.getOrDefault("gta4_touch_gamepad_opacity","1"));cameraOpacity=Float.parseFloat(settings.getOrDefault("gta4_touch_camera_opacity","1"));
            iconOutline="true".equals(settings.get("gta4_touch_icon_outline"));hints=!"false".equals(settings.get("gta4_touch_button_hints"));actionNames="true".equals(settings.get("gta4_touch_labels"));
        }catch(Exception ignored){}
        buttonScale=finite(buttonScale,.5f,2,1);cameraScale=finite(cameraScale,.25f,3,1);buttonOpacity=finite(buttonOpacity,0,1.6f,1);cameraOpacity=finite(cameraOpacity,0,1,1);
        context=Math.max(0,Math.min(contexts.length()-1,state==null?getIntent().getIntExtra("context",0):state.getInt("context",0)));
        if(state!=null){
            dirty=state.getBoolean("dirty");collapsed=state.getBoolean("collapsed");selectedKey=state.getString("selected");
            grid=state.getBoolean("grid",grid);snap=state.getBoolean("snap",snap);guides=state.getBoolean("guides",guides);hitAreas=state.getBoolean("hit_areas",hitAreas);
            gridSize=Math.max(5,Math.min(40,state.getInt("spacing",gridSize)));snapStrength=finite(state.getFloat("strength",snapStrength),.25f,1,1);
            panelX=finite(state.getFloat("panel_x",.35f),0,1,.35f);panelY=finite(state.getFloat("panel_y",0),0,1,0);
            Bundle draft=state.getBundle("draft");
            if(draft!=null)forEachControl((key,c)->{
                float[] v=draft.getFloatArray(key);if(v==null||v.length<4)return;
                TouchLayoutDocument.Entry e=document.get(key);e.dx=finite(v[0],-2000,2000,0);e.dy=finite(v[1],-800,800,0);
                e.scale=finite(v[2],.35f,4,1);e.hidden=v[3]==1;if(v.length>=5)e.opacity=finite(v[4],0,1,1);
            });
        }
        page=new FrameLayout(this);page.setBackgroundColor(Color.BLACK);
        canvas=new Editor();page.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        buildPanel();page.addView(panel,new FrameLayout.LayoutParams(-2,-2));setContentView(page);
        page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int width=Math.min(Math.max(1,page.getWidth()-dp(16)),Math.max(dp(250),Math.min(dp(360),Math.round(page.getWidth()*.34f))));
            if(panel.getLayoutParams().width!=width){panel.getLayoutParams().width=width;panel.requestLayout();}
            positionPanel();canvas.invalidate();
        });
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionPanel());
        updateSelection();
    }

    private void buildPanel(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackground(ModernUi.surface(this,0xf2141414,18));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,dp(6),0);header.setBackgroundColor(0xff252525);
        TextView title=ModernUi.text(this,xbox?"Xbox layout editor":"Mobile layout editor",15,ModernUi.TEXT);
        title.setTypeface(Typeface.MONOSPACE);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription("Drag this header to move the editor panel");header.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        title.setGravity(Gravity.CENTER_VERTICAL);enablePanelDrag(title);
        collapse=ModernUi.button(this,collapsed?"+":"−",false);collapse.setMinWidth(0);collapse.setPadding(dp(8),0,dp(8),0);
        collapse.setContentDescription("Collapse or expand editor tools");header.addView(collapse,new LinearLayout.LayoutParams(dp(40),dp(36)));
        Button back=ModernUi.iconButton(this,"back","Back");header.addView(back,new LinearLayout.LayoutParams(dp(36),dp(36)));
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
        picker=ModernUi.button(this,contextName()+(xbox?"":" ▾"),false);picker.setEnabled(!xbox);body.addView(picker,new LinearLayout.LayoutParams(-1,dp(36)));
        picker.setOnClickListener(v->{
            String[] labels=new String[contexts.length()];for(int i=0;i<labels.length;i++)labels[i]=contexts.optJSONObject(i).optString("name");
            ModernUi.choose(this,"Context",labels,context,index->{context=index;canvas.cancelDrag();selectedKey=null;picker.setText(contextName()+" ▾");updateSelection();canvas.invalidate();});
        });
        Button chooseControl=ModernUi.icon(this,"Select control","touch",false);body.addView(chooseControl,new LinearLayout.LayoutParams(-1,dp(40)));
        chooseControl.setOnClickListener(v->{JSONArray controls=contexts.optJSONObject(context).optJSONArray("controls");String[] labels=new String[controls.length()];for(int i=0;i<labels.length;i++)labels[i]=controls.optJSONObject(i).optString("label",controls.optJSONObject(i).optString("id"));ModernUi.choose(this,"Select control",labels,-1,i->{selectedKey=contexts.optJSONObject(context).optString("id")+"/"+controls.optJSONObject(i).optString("id");updateSelection();canvas.invalidate();});});
        TextView help=ModernUi.text(this,"Drag controls to move. Tap one to inspect. Drag the header to move this panel.",11,ModernUi.MUTED);
        help.setPadding(0,dp(6),0,dp(4));body.addView(help);
        selectionTools=new LinearLayout(this);selectionTools.setOrientation(LinearLayout.VERTICAL);
        selected=ModernUi.text(this,"",13,ModernUi.TEXT);selected.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);selectionTools.addView(selected);
        LinearLayout nudge=new LinearLayout(this);selectionTools.addView(nudge);
        String[] directions={"left","right","up","down"};for(int i=0;i<4;i++){final int direction=i;Button b=ModernUi.tool(this,directions[i],"Move selected control "+directions[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.leftMargin=dp(3);nudge.addView(b,lp);b.setOnClickListener(v->{if(selectedKey==null)return;checkpoint();TouchLayoutDocument.Entry e=document.get(selectedKey);if(direction<2)e.dx=finite(e.dx+(direction==0?-5:5),-2000,2000,0);else e.dy=finite(e.dy+(direction==2?-5:5),-800,800,0);dirty=true;canvas.invalidate();});}
        LinearLayout sizeRow=new LinearLayout(this);sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        amount=ModernUi.text(this,"",12,ModernUi.ACCENT);sizeRow.addView(amount,new LinearLayout.LayoutParams(dp(54),-2));
        size=new SeekBar(this);size.setMax(365);ModernUi.tint(size);sizeRow.addView(size,new LinearLayout.LayoutParams(0,-2,1));selectionTools.addView(sizeRow);
        hidden=new CheckBox(this);hidden.setText("Hidden in play");hidden.setTextColor(ModernUi.TEXT);selectionTools.addView(hidden);body.addView(selectionTools);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean user){
                if(user&&selectedKey!=null){document.get(selectedKey).scale=.35f+value*.01f;dirty=true;amount.setText(sizeLabel(document.get(selectedKey).scale));canvas.invalidate();}
            }
            public void onStartTrackingTouch(SeekBar b){checkpoint();}public void onStopTrackingTouch(SeekBar b){}
        });
        hidden.setOnCheckedChangeListener((button,on)->{if(selectedKey!=null&&document.get(selectedKey).hidden!=on){checkpoint();document.get(selectedKey).hidden=on;dirty=true;canvas.invalidate();}});
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        Button save=ModernUi.icon(this,"Save","save",true),reset=ModernUi.tool(this,"reset","Reset this context");
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(44),1);rp.leftMargin=dp(8);actions.addView(reset,rp);body.addView(actions);
        save.setOnClickListener(v->saveAndClose());
        reset.setOnClickListener(v->{checkpoint();String prefix=contexts.optJSONObject(context).optString("id")+"/";document.entries.keySet().removeIf(k->k.startsWith(prefix));dirty=true;selectedKey=null;canvas.cancelDrag();updateSelection();canvas.invalidate();});
        LinearLayout aids=new LinearLayout(this);CheckBox gridToggle=new CheckBox(this),snapToggle=new CheckBox(this);gridToggle.setText("Grid");snapToggle.setText("Snap");gridToggle.setTextColor(ModernUi.TEXT);snapToggle.setTextColor(ModernUi.TEXT);gridToggle.setChecked(grid);snapToggle.setChecked(snap);aids.addView(gridToggle);aids.addView(snapToggle);body.addView(aids);gridToggle.setOnCheckedChangeListener((b,on)->{grid=on;canvas.invalidate();});snapToggle.setOnCheckedChangeListener((b,on)->snap=on);Button spacing=ModernUi.button(this,"Grid: "+gridSize,false);aids.addView(spacing);spacing.setOnClickListener(v->{gridSize=gridSize==5?10:gridSize==10?20:gridSize==20?40:5;spacing.setText("Grid: "+gridSize);canvas.invalidate();});
        LinearLayout alignment=new LinearLayout(this);alignment.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox guideToggle=new CheckBox(this);guideToggle.setText("Guides");guideToggle.setTextColor(ModernUi.TEXT);guideToggle.setChecked(guides);alignment.addView(guideToggle);
        guideToggle.setOnCheckedChangeListener((b,on)->{guides=on;canvas.invalidate();});
        Button strength=ModernUi.button(this,"Snap: "+Math.round(snapStrength*100)+"%",false);alignment.addView(strength,new LinearLayout.LayoutParams(0,dp(36),1));body.addView(alignment);
        strength.setContentDescription("Snap strength. Tap to cycle 25, 50, 75 and 100 percent.");
        strength.setOnClickListener(v->{snapStrength=snapStrength>=1?.25f:snapStrength+.25f;strength.setText("Snap: "+Math.round(snapStrength*100)+"%");});
        CheckBox touchBounds=new CheckBox(this);touchBounds.setText("Touch areas & overlaps");touchBounds.setTextColor(ModernUi.TEXT);touchBounds.setChecked(hitAreas);body.addView(touchBounds);
        touchBounds.setOnCheckedChangeListener((b,on)->{hitAreas=on;updateSelection();canvas.invalidate();});
        LinearLayout history=new LinearLayout(this);Button backStep=ModernUi.tool(this,"undo","Undo"),forwardStep=ModernUi.tool(this,"redo","Redo"),export=ModernUi.tool(this,"export","Export layout"),imported=ModernUi.tool(this,"import","Import layout");
        undoAction=backStep;redoAction=forwardStep;updateHistory();
        for(Button action:new Button[]{backStep,forwardStep,export,imported}){action.setTextSize(11);action.setMinWidth(0);action.setPadding(dp(3),0,dp(3),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.setMargins(dp(2),0,dp(2),0);history.addView(action,lp);}body.addView(history);backStep.setOnClickListener(v->history(false));forwardStep.setOnClickListener(v->history(true));
        Button numeric=ModernUi.icon(this,"Position & appearance","position",false);selectionTools.addView(numeric,new LinearLayout.LayoutParams(-1,dp(36)));numeric.setOnClickListener(v->numeric());
        export.setOnClickListener(v->pickLayout(true));imported.setOnClickListener(v->pickLayout(false));
        toolsScroll.addView(body);toolsScroll.setVisibility(collapsed?View.GONE:View.VISIBLE);panel.addView(toolsScroll);
        collapse.setOnClickListener(v->{collapsed=!collapsed;toolsScroll.setVisibility(collapsed?View.GONE:View.VISIBLE);collapse.setText(collapsed?"+":"−");});
    }

    private Map<String,float[]> snapshot(){Map<String,float[]> state=new TreeMap<>();for(Map.Entry<String,TouchLayoutDocument.Entry> item:document.entries.entrySet()){TouchLayoutDocument.Entry e=item.getValue();state.put(item.getKey(),new float[]{e.dx,e.dy,e.scale,e.hidden?1:0,e.opacity});}return state;}
    private void updateHistory(){if(undoAction!=null){undoAction.setEnabled(!undo.isEmpty());undoAction.setAlpha(undo.isEmpty()?.35f:1);redoAction.setEnabled(!redo.isEmpty());redoAction.setAlpha(redo.isEmpty()?.35f:1);}}
    private void checkpoint(){undo.addLast(snapshot());while(undo.size()>50)undo.removeFirst();redo.clear();updateHistory();}
    private void restore(Map<String,float[]> state){document.entries.clear();for(Map.Entry<String,float[]> item:state.entrySet()){TouchLayoutDocument.Entry e=document.get(item.getKey());float[] v=item.getValue();e.dx=v[0];e.dy=v[1];e.scale=v[2];e.hidden=v[3]!=0;e.opacity=v[4];}dirty=true;canvas.cancelDrag();updateSelection();canvas.invalidate();}
    private void history(boolean forward){ArrayDeque<Map<String,float[]>> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;to.addLast(snapshot());restore(from.removeLast());updateHistory();}
    private void numeric(){if(selectedKey==null)return;TouchLayoutDocument.Entry e=document.get(selectedKey);LinearLayout inputs=new LinearLayout(this);inputs.setOrientation(LinearLayout.VERTICAL);String[] labels={"Horizontal offset","Vertical offset","Scale","Opacity (0–1)"};float[] values={e.dx,e.dy,e.scale,e.opacity};EditText[] fields=new EditText[4];for(int i=0;i<4;i++){inputs.addView(ModernUi.text(this,labels[i],12,ModernUi.MUTED));fields[i]=new EditText(this);fields[i].setText(Float.toString(values[i]));fields[i].setInputType(4098|8192);inputs.addView(fields[i]);}android.app.AlertDialog dialog=ModernUi.builder(this).setTitle("Selected control").setView(inputs).setPositiveButton("Apply",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{float[] n=new float[4];for(int i=0;i<4;i++){n[i]=Float.parseFloat(fields[i].getText().toString());if(!Float.isFinite(n[i]))throw new NumberFormatException();}checkpoint();e.dx=finite(n[0],-2000,2000,0);e.dy=finite(n[1],-800,800,0);e.scale=finite(n[2],.35f,4,1);e.opacity=finite(n[3],0,1,1);dirty=true;updateSelection();canvas.invalidate();dialog.dismiss();}catch(NumberFormatException error){Toast.makeText(this,"Enter finite numbers in every field.",Toast.LENGTH_SHORT).show();}}));dialog.show();}
    private void pickLayout(boolean export){android.content.Intent i=new android.content.Intent(export?android.content.Intent.ACTION_CREATE_DOCUMENT:android.content.Intent.ACTION_OPEN_DOCUMENT);i.addCategory(android.content.Intent.CATEGORY_OPENABLE);i.setType("text/plain");if(export)i.putExtra(android.content.Intent.EXTRA_TITLE,"XHYN-IV-touch-layout.txt");try{startActivityForResult(i,export?REQUEST_LAYOUT_EXPORT:REQUEST_LAYOUT_IMPORT);}catch(Exception e){Toast.makeText(this,"No file picker available.",Toast.LENGTH_SHORT).show();}}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{if(request==REQUEST_LAYOUT_EXPORT){File temp=new File(getCacheDir(),"layout-export");temp.mkdirs();try{document.save(temp);try(InputStream in=new FileInputStream(TouchLayoutDocument.file(temp));OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out==null)throw new IOException("Cannot write layout.");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}finally{SaveData.deleteTree(temp);}Toast.makeText(this,"Layout exported.",Toast.LENGTH_SHORT).show();}else if(request==REQUEST_LAYOUT_IMPORT){File temp=new File(getCacheDir(),"layout-import");temp.mkdirs();try{File f=TouchLayoutDocument.file(temp);f.getParentFile().mkdirs();try(InputStream in=getContentResolver().openInputStream(data.getData());OutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("Cannot read layout.");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){if((total+=n)>1024*1024)throw new IOException("Layout is too large.");out.write(b,0,n);}}TouchLayoutDocument imported=TouchLayoutDocument.read(temp);if(imported.entries.isEmpty())throw new IOException("No valid control records found.");checkpoint();document.merge(imported);canvas.cancelDrag();dirty=true;updateSelection();canvas.invalidate();Toast.makeText(this,"Imported controls merged. Other controls and contexts are preserved. Save layout to apply.",Toast.LENGTH_LONG).show();}finally{SaveData.deleteTree(temp);}}}catch(IOException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
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
        TouchLayoutDocument.Entry e=document.get(selectedKey);selected.setText("Selected: "+control.optString("label")+(hitAreas?canvas.overlapLabel(control):""));
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
            state.putBoolean("grid",grid);state.putBoolean("snap",snap);state.putBoolean("guides",guides);state.putBoolean("hit_areas",hitAreas);state.putInt("spacing",gridSize);state.putFloat("strength",snapStrength);
            state.putInt("context",context);state.putBoolean("dirty",dirty);state.putBoolean("collapsed",collapsed);state.putString("selected",selectedKey);
            state.putFloat("panel_x",panelX);state.putFloat("panel_y",panelY);Bundle draft=new Bundle();
            forEachControl((key,c)->{TouchLayoutDocument.Entry e=document.get(key);draft.putFloatArray(key,new float[]{e.dx,e.dy,e.scale,e.hidden?1:0,e.opacity});});state.putBundle("draft",draft);
        }super.onSaveInstanceState(state);
    }
    private void saveAndClose(){
        try{document.save(root);setResult(RESULT_OK);Toast.makeText(this,"Layout saved.",Toast.LENGTH_SHORT).show();finish();}
        catch(IOException e){Toast.makeText(this,"Could not save layout: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){
        if(document==null||!dirty){finish();return;}
        ModernUi.builder(this).setTitle("Unsaved control layout")
            .setMessage("Save your changes before leaving?").setPositiveButton("Save",(d,w)->saveAndClose())
            .setNegativeButton("Discard",(d,w)->finish()).setNeutralButton("Keep editing",null).show();
    }
    private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideSystemBars();}
    @Override protected void onStop(){getPreferences(MODE_PRIVATE).edit().putBoolean("grid",grid).putBoolean("snap",snap).putBoolean("guides",guides).putBoolean("hit_areas",hitAreas).putInt("spacing",gridSize).putFloat("strength",snapStrength).apply();super.onStop();}
    @Override protected void onDestroy(){icons.clear();super.onDestroy();}

    private final class Editor extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final ColorFilter iconTint=new PorterDuffColorFilter(0xffc2c2c2,PorterDuff.Mode.SRC_IN);
        private final Map<Integer,ColorFilter> xboxTints=new HashMap<>();
        private final RectF gripBounds=new RectF();
        private float lastX,lastY;
        private int pointer=-1;
        private JSONObject dragging;private boolean moved;
        Editor(){super(TouchEditorActivity.this);setContentDescription("Full-screen control layout. Drag a control, then use the floating editor panel to resize or hide it.");}
        private JSONArray controls(){return contexts.optJSONObject(context).optJSONArray("controls");}
        private String key(JSONObject c){return contexts.optJSONObject(context).optString("id")+"/"+c.optString("id");}
        private RectF viewport(){return new RectF(0,0,getWidth(),getHeight());}
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
            String file=c.optString("image");
            if(xbox){String id=c.optString("id");
                if(id.startsWith("dpad_"))file=id.substring(5);
                else if(id.equals("start"))file="xbox_menu";
                else if(id.equals("back"))file="view";
                else file=c.optString("label").toLowerCase(java.util.Locale.ROOT);
            }
            if(file.isEmpty())return null;
            if(!icons.containsKey(file)){Bitmap bitmap=null;try(InputStream in=getAssets().open("touch-icons/"+file+".png")){bitmap=BitmapFactory.decodeStream(in);}catch(IOException ignored){}icons.put(file,bitmap);}
            return icons.get(file);
        }
        @Override protected void onDraw(Canvas out){
            super.onDraw(out);out.drawColor(0xff080a0d);if(getWidth()<=0||getHeight()<=0)return;
            RectF safe=viewport();
            if(grid){paint.setColor(0x244f6478);float step=gridSize*unit();if(step>0){for(float x=safe.left;x<=safe.right;x+=step)out.drawLine(x,safe.top,x,safe.bottom,paint);for(float y=safe.top;y<=safe.bottom;y+=step)out.drawLine(safe.left,y,safe.right,y,paint);}paint.setColor(0x707c94ad);out.drawLine(safe.centerX(),safe.top,safe.centerX(),safe.bottom,paint);out.drawLine(safe.left,safe.centerY(),safe.right,safe.centerY(),paint);}paint.setStyle(Paint.Style.FILL);
            if(guides&&dragging!=null){float[] target=guideTargets(dragging);paint.setColor(0xff55c9eb);paint.setStrokeWidth(dp(1));
                if(Float.isFinite(target[0]))out.drawLine(target[0],safe.top,target[0],safe.bottom,paint);
                if(Float.isFinite(target[1]))out.drawLine(safe.left,target[1],safe.right,target[1],paint);
            }
            JSONArray controls=controls();paint.setTypeface(Typeface.MONOSPACE);
            for(int i=0;i<controls.length();i++){
                JSONObject c=controls.optJSONObject(i);String key=key(c);TouchLayoutDocument.Entry e=document.get(key);float x=x(c),y=y(c),r=radius(c);
                boolean stick=xbox?c.optString("id").endsWith("_stick"):c.optString("kind").equals("stick");
                float alpha=e.hidden?0:e.opacity*buttonOpacity*((c.optString("id").equals("camera")||xbox&&c.optString("id").equals("right_stick"))?cameraOpacity:1);
                paint.setStyle(Paint.Style.FILL);paint.setColor(faded(0x60121212,alpha*(!xbox&&stick?.9f:1)));out.drawCircle(x,y,r,paint);
                if(iconOutline&&!stick){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,r*.025f));paint.setColor(faded(0xe1c2c2c2,alpha));out.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);}
                paint.setStyle(Paint.Style.FILL);
                if(stick){paint.setColor(faded(xbox?0xbec2c2c2:0xaac2c2c2,alpha));out.drawCircle(x,y,r*.30f,paint);
                    gripBounds.set(x-r*.41f,y-r*.41f,x+r*.41f,y+r*.41f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(r*.06f);
                    for(int mark=0;mark<4;mark++)out.drawArc(gripBounds,mark*90-22,44,false,paint);paint.setStyle(Paint.Style.FILL);}
                else{
                    Bitmap image=icon(c);
                    if(image!=null){float half=r*(xbox?1f:.82f);
                        int tint=0xffc2c2c2;
                        if(xbox)switch(c.optString("id")){case "a":tint=0xff41be50;break;case "b":tint=0xffdc3c3c;break;case "x":tint=0xff4b91f0;break;case "y":tint=0xfff5cd32;break;}

                        paint.setColorFilter(xbox?xboxTints.computeIfAbsent(tint,color->new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN)):iconTint);paint.setAlpha(Color.alpha(faded(xbox&&Arrays.asList("a","b","x","y").contains(c.optString("id"))?0xaa000000:0xe1000000,alpha)));out.drawBitmap(image,null,new RectF(x-half,y-half,x+half,y+half),paint);paint.setColorFilter(null);paint.setAlpha(255);}
                    else{paint.setColor(faded(0xffdedede,alpha));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(9,Math.min(dp(16),r*.7f)));out.drawText(c.optString("label"),x,y-(paint.ascent()+paint.descent())*.5f,paint);}
                }
                if(hitAreas||key.equals(selectedKey)||e.hidden){
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(key.equals(selectedKey)?dp(1.5f):dp(.6f));paint.setColor(e.hidden?0xfff65a5a:key.equals(selectedKey)?0xffdfc28a:0x9055c9eb);out.drawCircle(x,y,r*(hitAreas?(xbox?1.4f:stick?1.5f:1.35f):1f),paint);
                }
                paint.setStyle(Paint.Style.FILL);paint.setColor(faded(0xffffffff,alpha*.95f));paint.setTextAlign(Paint.Align.CENTER);
                String hint=c.optString("hint");float textY=y+r+Math.max(2,unit()*4);
                if(!xbox&&hints&&!hint.isEmpty()){
                    paint.setTextSize(Math.max(8,Math.min(r*.36f,19*unit())));textY=Math.min(textY,getHeight()-paint.descent()+paint.ascent());out.drawText(hint,x,textY-paint.ascent(),paint);textY+=paint.getTextSize()*1.2f;
                }
                if(!xbox&&actionNames){paint.setTextSize(Math.max(9,Math.min(r*.30f,17*unit())));out.drawText(c.optString("label"),x,Math.min(textY-paint.ascent(),getHeight()-paint.descent()),paint);}
            }
        }
        private int faded(int color,float opacity){
            float factor=xbox?opacity:Math.max(0,Math.min(1,opacity));
            return (color&0x00ffffff)|(Math.max(0,Math.min(255,(int)(Color.alpha(color)*factor)))<<24);
        }
        private float captureRadius(JSONObject control){boolean stick=xbox?control.optString("id").endsWith("_stick"):control.optString("kind").equals("stick");return radius(control)*(xbox?1.4f:stick?1.5f:1.35f);}
        private String overlapLabel(JSONObject control){
            int overlaps=0;JSONArray all=controls();for(int i=0;i<all.length();i++){JSONObject other=all.optJSONObject(i);if(key(other).equals(key(control))||document.get(key(other)).hidden)continue;if(Math.hypot(x(control)-x(other),y(control)-y(other))<captureRadius(control)+captureRadius(other))overlaps++;}
            return overlaps==0?"\nNo overlapping buttons":"\nOverlaps "+overlaps+" control"+(overlaps==1?"":"s");
        }
        // A guide only attracts controls already within a small screen-space radius.
        private float[] guideTargets(JSONObject control){
            RectF a=viewport();float px=x(control),py=y(control),tolerance=dp(8);
            float gx=Math.abs(px-a.centerX())<=tolerance?a.centerX():Float.NaN;
            float gy=Math.abs(py-a.centerY())<=tolerance?a.centerY():Float.NaN;
            JSONArray list=controls();for(int i=0;i<list.length();i++){JSONObject other=list.optJSONObject(i);if(key(other).equals(key(control))||document.get(key(other)).hidden)continue;
                float ox=x(other),oy=y(other);
                if(Math.abs(ox-px)<=tolerance&&(!Float.isFinite(gx)||Math.abs(ox-px)<Math.abs(gx-px)))gx=ox;
                if(Math.abs(oy-py)<=tolerance&&(!Float.isFinite(gy)||Math.abs(oy-py)<Math.abs(gy-py)))gy=oy;
            }return new float[]{gx,gy};
        }
        private void applySnap(){
            if(dragging==null||selectedKey==null||!snap||!moved||unit()<=0)return;
            TouchLayoutDocument.Entry e=document.get(selectedKey);RectF a=viewport();float step=gridSize*unit(),px=x(dragging),py=y(dragging);
            float tx=a.left+Math.round((px-a.left)/step)*step,ty=a.top+Math.round((py-a.top)/step)*step;
            if(guides){float[] guide=guideTargets(dragging);if(Float.isFinite(guide[0])&&Math.abs(guide[0]-px)<=Math.abs(tx-px))tx=guide[0];if(Float.isFinite(guide[1])&&Math.abs(guide[1]-py)<=Math.abs(ty-py))ty=guide[1];}
            float sign=!xbox&&dragging.optString("anchor").equals("right")?-1:1;
            float dx=finite(e.dx+sign*(tx-px)*snapStrength/unit(),-2000,2000,0),dy=finite(e.dy+(ty-py)*snapStrength/unit(),-800,800,0);
            dirty|=dx!=e.dx||dy!=e.dy;e.dx=dx;e.dy=dy;
        }
        void cancelDrag(){dragging=null;pointer=-1;moved=false;}
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                pointer=event.getPointerId(0);float px=event.getX(),py=event.getY(),best=Float.MAX_VALUE;dragging=null;moved=false;
                JSONArray controls=controls();for(int i=0;i<controls.length();i++){
                    JSONObject c=controls.optJSONObject(i);float capture=Math.max(radius(c)*1.2f,dp(22));float score=(float)Math.hypot(px-x(c),py-y(c))/capture;
                    if(score<=1&&score<best){best=score;dragging=c;}
                }
                if(dragging!=null){checkpoint();selectedKey=key(dragging);updateSelection();}lastX=px;lastY=py;invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE&&dragging!=null&&unit()>0){
                int index=event.findPointerIndex(pointer);if(index<0){cancelDrag();return true;}
                float px=event.getX(index),py=event.getY(index);TouchLayoutDocument.Entry e=document.get(selectedKey);
                float sign=dragging.optString("anchor").equals("right")?-1:1;
                float dx=finite(e.dx+sign*(px-lastX)/unit(),-2000,2000,0),dy=finite(e.dy+(py-lastY)/unit(),-800,800,0);
                moved|=dx!=e.dx||dy!=e.dy;dirty|=dx!=e.dx||dy!=e.dy;e.dx=dx;e.dy=dy;lastX=px;lastY=py;if(hitAreas)updateSelection();invalidate();return true;
            }
            if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer)){
                if(action!=MotionEvent.ACTION_CANCEL)applySnap();cancelDrag();invalidate();if(action!=MotionEvent.ACTION_CANCEL)performClick();return true;
            }return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}

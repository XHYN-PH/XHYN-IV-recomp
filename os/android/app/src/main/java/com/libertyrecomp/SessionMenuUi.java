package com.libertyrecomp;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Session identity above Resume, with independently scrolling action categories. */
final class SessionMenuUi {
 static final class State {String tab="Session";final Map<String,Integer> scroll=new HashMap<>();}
 private static volatile boolean foreground=true;
 static void foreground(boolean value){foreground=value;}
 private static String setting(MobileSettingsUi.Runtime r,String k){try{String v=r.get(k);return v==null?"":v;}catch(Exception e){return "";}}
 static AlertDialog show(Activity a,MobileSettingsUi.Runtime runtime,String driver,State state,Map<String,Runnable> actions,Runnable closed){
  LinearLayout root=ModernUi.panel(a);root.setBackground(ModernUi.surface(a,0xf2111315,14));
  LinearLayout identity=new LinearLayout(a);identity.setGravity(Gravity.CENTER_VERTICAL);identity.setBaselineAligned(false);
  ImageView logo=new ImageView(a);logo.setImageResource(R.drawable.launcher_iv);logo.setContentDescription("IV logo");logo.setBackgroundColor(android.graphics.Color.TRANSPARENT);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
  LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(ModernUi.dp(a,54),ModernUi.dp(a,54));logoSize.rightMargin=ModernUi.dp(a,12);identity.addView(logo,logoSize);
  LinearLayout information=new LinearLayout(a);information.setOrientation(LinearLayout.VERTICAL);identity.addView(information,new LinearLayout.LayoutParams(0,-2,1));
  TextView paused=ModernUi.text(a,"PAUSED",11,ModernUi.ACCENT);information.addView(paused);
  TextView stats=ModernUi.text(a,"Waiting for gameplay sample",13,ModernUi.TEXT);stats.setSingleLine(true);stats.setEllipsize(android.text.TextUtils.TruncateAt.END);information.addView(stats);
  TextView hardware=ModernUi.text(a,Build.MODEL+" · Android "+Build.VERSION.RELEASE,11,ModernUi.MUTED);hardware.setMaxLines(2);hardware.setEllipsize(android.text.TextUtils.TruncateAt.END);information.addView(hardware);
  root.addView(identity,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout right=root;
  LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams headerSpace=new LinearLayout.LayoutParams(-1,-2);headerSpace.topMargin=ModernUi.dp(a,10);right.addView(header,headerSpace);
  Button resume=ModernUi.icon(a,"Resume","play",true),quit=ModernUi.iconButton(a,"exit","Quit to launcher");
  header.addView(resume,new LinearLayout.LayoutParams(0,ModernUi.dp(a,42),1));LinearLayout.LayoutParams quitSize=new LinearLayout.LayoutParams(ModernUi.dp(a,42),ModernUi.dp(a,42));quitSize.leftMargin=ModernUi.dp(a,8);header.addView(quit,quitSize);
  HorizontalScrollView tabStrip=new HorizontalScrollView(a);tabStrip.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(a);tabStrip.addView(tabs);LinearLayout.LayoutParams ts=new LinearLayout.LayoutParams(-1,ModernUi.dp(a,42));ts.topMargin=ModernUi.dp(a,7);right.addView(tabStrip,ts);
  ScrollView scroll=new ScrollView(a);LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  TextView details=ModernUi.text(a,"",11,ModernUi.MUTED);details.setTextIsSelectable(true);
  AlertDialog dialog=new AlertDialog.Builder(a).setView(root).create();dialog.setCanceledOnTouchOutside(false);resume.setOnClickListener(v->dialog.dismiss());quit.setOnClickListener(v->{Runnable run=actions.get("Quit to launcher");if(run!=null)run.run();});
  List<Button> buttons=new ArrayList<>();String[] names={"Session","Touch controls","Controller","Diagnostics"},icons={"info","touch","controls","diagnostics"};
  final java.util.function.Consumer<String> change=name->{state.scroll.put(state.tab,scroll.getScrollY());state.tab=name;content.removeAllViews();for(int n=0;n<buttons.size();n++)ModernUi.selected(buttons.get(n),names[n].equals(name));
   String[] keys=name.equals("Touch controls")?new String[]{"Edit mobile controls","Control settings"}:name.equals("Controller")?new String[]{"Edit Xbox controls","Control settings"}:name.equals("Diagnostics")?new String[]{"Start performance test","Recorded results","Input test (30 seconds)","Capture renderer timings","Capture shadow issue"}:new String[]{"Achievements","Cheats","Settings"};
   if(name.equals("Controller")){int count=0;for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&(d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD){content.addView(ModernUi.text(a,d.getName(),11,ModernUi.MUTED));count++;}}if(count==0)content.addView(ModernUi.text(a,"No controller connected",11,ModernUi.MUTED));}
   for(String key:keys){Runnable action=actions.get(key);if(action==null)continue;String icon=key.equals("Achievements")?"trophy":key.equals("Cheats")?"cheats":key.equals("Settings")?"settings":key.contains("mobile")?"touch":key.contains("Xbox")?"controls":"diagnostics";Button b=ModernUi.icon(a,key,icon,false);b.setTextSize(12);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ModernUi.dp(a,38));lp.topMargin=ModernUi.dp(a,5);content.addView(b,lp);b.setOnClickListener(v->{state.scroll.put(state.tab,scroll.getScrollY());action.run();});}
   if(name.equals("Diagnostics")){Button more=ModernUi.button(a,"Session details",false);content.addView(more,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,38)));if(details.getParent() instanceof ViewGroup)((ViewGroup)details.getParent()).removeView(details);details.setVisibility(View.GONE);content.addView(details);more.setOnClickListener(v->details.setVisibility(details.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));Button copy=ModernUi.button(a,"Copy details",false);content.addView(copy,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,36)));copy.setOnClickListener(v->{ClipboardManager c=(ClipboardManager)a.getSystemService(Activity.CLIPBOARD_SERVICE);if(c!=null)c.setPrimaryClip(ClipData.newPlainText("XHYN IV session",hardware.getText()+"\n"+stats.getText()+"\n"+details.getText()));});}
   scroll.post(()->scroll.scrollTo(0,state.scroll.getOrDefault(name,0)));
  };
  for(int n=0;n<names.length;n++){String name=names[n];Button b=ModernUi.icon(a,name,icons[n],false);b.setTextSize(11);b.setPadding(ModernUi.dp(a,7),0,ModernUi.dp(a,7),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,ModernUi.dp(a,36));lp.rightMargin=ModernUi.dp(a,5);tabs.addView(b,lp);buttons.add(b);b.setOnClickListener(v->change.accept(name));}
  HandlerThread thread=new HandlerThread("CompactSessionStats",android.os.Process.THREAD_PRIORITY_BACKGROUND);thread.start();Handler worker=new Handler(thread.getLooper()),main=new Handler(Looper.getMainLooper());java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
  Runnable sample=new Runnable(){long memoryAt;double ram;String actual="";public void run(){if(!active.get())return;if(!foreground){worker.postDelayed(this,1000);return;}try{JSONObject s=new JSONObject(setting(runtime,"gta4_session_stats"));if(SystemClock.uptimeMillis()-memoryAt>5000){ram=Debug.getPss()/1048576.0;memoryAt=SystemClock.uptimeMillis();}boolean valid=s.has("sample_ns")&&s.optLong("sample_ns",0)>0&&s.optDouble("fps",0)>0&&Double.isFinite(s.optDouble("fps"));String text=valid?String.format(Locale.ROOT,"%.1f FPS · %.1f ms · %.2f GB",s.optDouble("fps"),s.optDouble("frame_ms"),ram):"Waiting for gameplay sample";if(actual.isEmpty()){JSONObject status=new JSONObject(DriverBridge.status());JSONObject gpu=status.optJSONObject("render_device");if(gpu!=null)actual=gpu.optString("name")+"\n"+gpu.optString("driver_info");}String h=Build.MODEL+" · Android "+Build.VERSION.RELEASE+(actual.isEmpty()?"":" · "+actual.replace("\n"," · "));String d=setting(runtime,"gta4_effective_upscaling")+"\nAA "+s.optString("aa","unknown")+" · "+setting(runtime,"gta4_session_present_mode")+"\n"+setting(runtime,"gta4_native_pipeline_status")+"\n"+setting(runtime,"gta4_native_preparation_status");main.post(()->{if(active.get()){stats.setText(text);hardware.setText(h);details.setText(d);}});}catch(Exception ignored){}if(active.get())worker.postDelayed(this,1000);}};
  dialog.setOnDismissListener(d->{state.scroll.put(state.tab,scroll.getScrollY());active.set(false);worker.removeCallbacksAndMessages(null);main.removeCallbacksAndMessages(null);thread.quitSafely();closed.run();});
  dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null){ModernUi.immersive(w);ModernUi.safeInsets(a,root,10,8);w.getDecorView().setPadding(0,0,0,0);w.setBackgroundDrawableResource(android.R.color.transparent);w.setDimAmount(.3f);w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);w.setLayout(Math.min(ModernUi.dp(a,760),(int)(a.getResources().getDisplayMetrics().widthPixels*.94)),(int)(a.getResources().getDisplayMetrics().heightPixels*.94));}worker.post(sample);});
  change.accept(Arrays.asList(names).contains(state.tab)?state.tab:"Session");if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(ModernUi.motion(a)?R.style.SessionPanelAnimation:0);dialog.show();return dialog;
 }
 private SessionMenuUi(){}
}

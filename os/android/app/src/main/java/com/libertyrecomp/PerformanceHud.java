package com.libertyrecomp;

import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.Locale;

/** A non-interactive overlay; it never steals touches or samples while hidden. */
final class PerformanceHud {
    private final Activity activity;private final RelativeLayout root;private final MobileSettingsUi.Runtime runtime;private final TextView view;
    private final Handler main=new Handler(Looper.getMainLooper());private HandlerThread thread;private Handler sampler;
    private volatile boolean active;private String mode="off",position="top_right";private volatile int generation;
    PerformanceHud(Activity activity,RelativeLayout root,MobileSettingsUi.Runtime runtime){this.activity=activity;this.root=root;this.runtime=runtime;view=ModernUi.text(activity,"",12,ModernUi.TEXT);view.setPadding(ModernUi.dp(activity,7),ModernUi.dp(activity,4),ModernUi.dp(activity,7),ModernUi.dp(activity,4));view.setBackground(ModernUi.surface(activity,0xb0101214,7));view.setClickable(false);view.setFocusable(false);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setEllipsize(android.text.TextUtils.TruncateAt.END);view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->place());root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->place());view.setOnApplyWindowInsetsListener((v,insets)->{v.post(this::place);return insets;});root.addView(view);view.setVisibility(View.GONE);}
    void configure(String mode,String position){
        this.mode=mode;this.position=position;
        RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(-2,-2);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);view.setLayoutParams(lp);
        view.setTextSize((float)number("gta4_overlay_text_size",12));view.setAlpha((float)number("gta4_overlay_opacity",1));
        view.setMaxLines("true".equals(runtime.get("gta4_overlay_compact"))?1:8);view.post(this::place);
        boolean was=active;active(false);if(was)active(true);
    }
    synchronized void active(boolean value){value=value&&!mode.equals("off");if(value==active)return;active=value;final int token=++generation;view.setVisibility(value?View.VISIBLE:View.GONE);if(!value){if(sampler!=null)sampler.removeCallbacksAndMessages(null);if(thread!=null)thread.quitSafely();thread=null;sampler=null;return;}thread=new HandlerThread("PerformanceOverlay",android.os.Process.THREAD_PRIORITY_BACKGROUND);thread.start();sampler=new Handler(thread.getLooper());final Handler worker=sampler;final String selected=mode;
        worker.post(new Runnable(){long memoryAt;double pss;final OverlayMetrics usage=new OverlayMetrics();public void run(){if(!active||generation!=token)return;String text="FPS —";try{JSONObject stats=new JSONObject(runtime.get("gta4_session_stats"));long age=System.nanoTime()-stats.optLong("sample_ns",0);boolean fresh=stats.has("sample_ns")&&age>=0&&age<3000000000L;text=fresh?String.format(Locale.ROOT,"%.1f FPS · %.1f ms",stats.optDouble("fps"),stats.optDouble("frame_ms")):"FPS — · waiting";if(selected.equals("detailed")){if("true".equals(runtime.get("gta4_overlay_ram"))&&SystemClock.uptimeMillis()-memoryAt>5000){pss=Debug.getPss()/1024.0;memoryAt=SystemClock.uptimeMillis();}PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);String separator="true".equals(runtime.get("gta4_overlay_compact"))?" · ":"\n";if("true".equals(runtime.get("gta4_overlay_ram")))text+=separator+String.format(Locale.ROOT,"RAM %.0f MB",pss);if("true".equals(runtime.get("gta4_overlay_thermal")))text+=separator+ThermalStatus.summary(runtime.get("gta4_thermal_status"),System.nanoTime(),true);if("true".equals(runtime.get("gta4_overlay_cpu")))text+=separator+usage.cpu();if("true".equals(runtime.get("gta4_overlay_gpu")))text+=separator+usage.gpu();if("true".equals(runtime.get("gta4_overlay_resolution")))text+=separator+runtime.get("gta4_effective_upscaling");text+=separator+stats.optString("aa","—").toUpperCase(Locale.ROOT)+" · "+runtime.get("gta4_session_present_mode");}}catch(Exception ignored){}final String result=text;main.post(()->{if(active&&generation==token)view.setText(result);});if(active&&generation==token)worker.postDelayed(this,1000);}});
    }
    private void place(){
        if(root.getWidth()<=0||root.getHeight()<=0)return;
        WindowInsets insets=root.getRootWindowInsets();DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
        int left=cutout==null?0:cutout.getSafeInsetLeft(),right=cutout==null?0:cutout.getSafeInsetRight();
        int top=(cutout==null?0:cutout.getSafeInsetTop());
        int bottom=cutout==null?0:cutout.getSafeInsetBottom();
        int gap=ModernUi.dp(activity,(float)number("gta4_overlay_edge_gap",2));
        int maxWidth=Math.max(1,root.getWidth()-left-right-2*gap);if(view.getMaxWidth()!=maxWidth)view.setMaxWidth(maxWidth);
        float[] point=OverlayPlacement.place(position,(float)number("gta4_overlay_x",.5),(float)number("gta4_overlay_y",.1),root.getWidth(),root.getHeight(),view.getWidth(),view.getHeight(),left,top,right,bottom,gap);
        view.setX(point[0]);view.setY(point[1]);
    }
    private double number(String key,double fallback){try{double n=Double.parseDouble(runtime.get(key));return Double.isFinite(n)?n:fallback;}catch(Exception e){return fallback;}}
    static String thermal(int value){String[] names={"Normal","Light","Moderate","Severe","Critical","Emergency","Shutdown"};return value>=0&&value<names.length?names[value]:"Thermal unavailable";}
    void close(){active(false);main.removeCallbacksAndMessages(null);}
}

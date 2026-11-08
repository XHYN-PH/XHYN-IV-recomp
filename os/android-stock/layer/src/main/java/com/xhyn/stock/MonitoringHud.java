package com.xhyn.stock;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.*;

final class MonitoringHud {
    private final Activity a; private final Handler ui=new Handler(Looper.getMainLooper());
    private FrameLayout root; private TextView text; private JSONObject options;
    private ScheduledExecutorService worker; private int generation;
    MonitoringHud(Activity a){this.a=a;}
    void attach() {
        root=new FrameLayout(a); root.setClipChildren(true); root.setFocusable(false);
        text=Ui.text(a,"",13,Ui.TEXT); text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setPadding(Ui.dp(a,6),Ui.dp(a,3),Ui.dp(a,6),Ui.dp(a,3)); text.setFocusable(false);text.setClickable(false);
        root.addView(text,new FrameLayout.LayoutParams(-2,-2));
        Button menu=Ui.button(a,"Menu",false);menu.setTextSize(11);menu.setMinHeight(Ui.dp(a,32));menu.setMinimumHeight(Ui.dp(a,32));
        menu.setFocusable(false);menu.setPadding(Ui.dp(a,10),0,Ui.dp(a,10),0);
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-2,Ui.dp(a,32),Gravity.TOP|Gravity.CENTER_HORIZONTAL);root.addView(menu,mp);
        menu.setOnClickListener(v->LayerApplication.openSession(a));
        a.addContentView(root,new android.view.ViewGroup.LayoutParams(-1,-1));
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->place());
        root.setOnApplyWindowInsetsListener((v,in)->{root.post(this::place);return in;});
        // Only the button consumes touch. The empty FrameLayout forwards the rest to SDL.
    }
    void start() {
        stop(); options=LayerPreferences.read(a); if(!options.optBoolean("hud",false)){text.setVisibility(View.GONE);return;}
        text.setVisibility(View.VISIBLE); text.setTextSize(Math.max(9,Math.min(24,options.optInt("textSize",13))));
        int alpha=Math.max(0,Math.min(230,options.optInt("opacity",170)));text.setBackgroundColor((alpha<<24)|0x00101824);
        worker=Executors.newSingleThreadScheduledExecutor(); final int token=++generation;
        final MetricsSampler sampler=new MetricsSampler(a);
        worker.scheduleWithFixedDelay(()->{
            String value; try{value=sampler.sample(options,false);}catch(RuntimeException denied){value="Monitoring unavailable";}
            final String result=value;ui.post(()->{if(token!=generation||a.isDestroyed())return;text.setText(result);root.post(this::place);});
        },0,1,TimeUnit.SECONDS);place();
    }
    void stop() { generation++;if(worker!=null){worker.shutdownNow();worker=null;}ui.removeCallbacksAndMessages(null); }
    void place() {
        if(root==null||options==null||text.getVisibility()!=View.VISIBLE)return;
        Rect safe=safe(root); int w=Math.max(0,root.getWidth()-safe.left-safe.right),h=Math.max(0,root.getHeight()-safe.top-safe.bottom);
        text.setMaxWidth(w);text.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.AT_MOST));
        RectF bounds=HudPlacement.bounds(w,h,text.getMeasuredWidth(),text.getMeasuredHeight(),(float)options.optDouble("x",0),(float)options.optDouble("y",0));
        text.setTranslationX(safe.left+bounds.left);text.setTranslationY(safe.top+bounds.top);
    }
    static Rect safe(View view) {
        Rect out=new Rect();WindowInsets in=view.getRootWindowInsets();
        // Hidden system bars must not reserve the editor's top/bottom. Only real cutouts constrain the HUD.
        DisplayCutout cut=in==null?null:in.getDisplayCutout();
        if(cut!=null)out.set(cut.getSafeInsetLeft(),cut.getSafeInsetTop(),cut.getSafeInsetRight(),cut.getSafeInsetBottom());return out;
    }
}

package com.libertyrecomp;

import static org.junit.Assert.*;
import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w900dp-h400dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class RefinementRegressionTest {
    @Test public void thermalSummaryDistinguishesRequestsProtectionAndStaleSamples(){
        String status="{\"sampled\":true,\"sample_ns\":100,\"severity\":3,\"guard_enabled\":false,\"turbo_requested\":true,\"draw_scale\":0.9}";
        assertTrue(ThermalStatus.summary(status,200,false).contains("Guard Off"));
        assertTrue(ThermalStatus.summary(status,200,false).contains("GPU Turbo requested"));
        assertTrue(ThermalStatus.summary(status,200,false).contains("Severe"));
        status=status.replace("\"guard_enabled\":false","\"guard_enabled\":true,\"guard_active\":true,\"turbo_backoff\":true");
        assertTrue(ThermalStatus.summary(status,200,false).contains("Guard active"));
        assertTrue(ThermalStatus.summary(status,200,false).contains("backed off"));
        assertEquals("Thermal status pending",ThermalStatus.summary(status,20_000_000_101L,false));
        assertEquals("Thermal status pending",ThermalStatus.summary(status,99,false));
        assertEquals("Thermal status pending",ThermalStatus.summary("{\"sampled\":false}",200,false));
        assertEquals("Thermal status unavailable",ThermalStatus.summary("invalid",200,false));
    }
    private static KeyEvent key(int action,int code){return new KeyEvent(0,1,action,code,0,0,1,0,0,InputDevice.SOURCE_GAMEPAD);}
    @Test public void controllerActivationOccursOnceAndCanceledBackIsIgnored(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();Button b=ModernUi.button(a,"Action",false);b.setFocusableInTouchMode(true);a.setContentView(b);assertTrue(b.requestFocus());int[] clicks={0},backs={0};b.setOnClickListener(v->clicks[0]++);ControllerNavigation n=new ControllerNavigation();
            assertTrue(n.key(a.getWindow(),key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A),()->backs[0]++));assertEquals(0,clicks[0]);
            assertTrue(n.key(a.getWindow(),key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A),()->backs[0]++));assertEquals(1,clicks[0]);
            KeyEvent canceled=KeyEvent.changeFlags(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B),KeyEvent.FLAG_CANCELED);n.key(a.getWindow(),canceled,()->backs[0]++);assertEquals(0,backs[0]);
        }
    }
    @Test public void overlayPreviewReachesBothEdgesWithZeroGap(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();Map<String,String> draft=new HashMap<>(Map.of("gta4_performance_overlay","fps","gta4_overlay_position","custom","gta4_overlay_x","0","gta4_overlay_y","0","gta4_overlay_edge_gap","0"));
            OverlayPreview preview=new OverlayPreview(a,draft);a.setContentView(preview);
            int width=a.getResources().getDisplayMetrics().widthPixels,height=a.getResources().getDisplayMetrics().heightPixels;
            preview.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));preview.layout(0,0,width,height);
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);preview.draw(new Canvas(bitmap));RectF box=ReflectionHelpers.getField(preview,"box");assertEquals(0,box.left,.01);assertEquals(0,box.top,.01);
            draft.put("gta4_overlay_x","1");draft.put("gta4_overlay_y","1");preview.draw(new Canvas(bitmap));assertEquals(width,box.right,.01);assertEquals(height,box.bottom,.01);bitmap.recycle();
        }
    }
    @Test public void longDialogContentHasBoundedScrollableViewport(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);for(int i=0;i<24;i++)ModernUi.action(a,body,"Option "+i,()->{});
            AlertDialog d=ModernUi.dialog(a,"Logs",body);d.show();Shadows.shadowOf(Looper.getMainLooper()).idle();
            ScrollView viewport=(ScrollView)body.getParent();int width=ModernUi.dp(a,500),height=a.getResources().getDisplayMetrics().heightPixels;
            viewport.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));
            assertTrue(viewport.getMeasuredHeight()<=height-ModernUi.dp(a,112));assertTrue(body.getMeasuredHeight()>viewport.getMeasuredHeight());assertEquals(ModernUi.dp(a,8),viewport.getPaddingBottom());d.dismiss();
        }
    }
    @Test public void shoulderButtonsCycleVisibleTabs(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();LinearLayout tabs=new LinearLayout(a);int[] selected={0};for(int i=0;i<3;i++){final int n=i;Button b=ModernUi.button(a,"Tab "+i,false);b.setTag("ui-tab");b.setSelected(i==0);tabs.addView(b);b.setOnClickListener(v->selected[0]=n);}a.setContentView(tabs);
            new ControllerNavigation().key(a.getWindow(),key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R1),()->{});assertEquals(1,selected[0]);
        }
    }
}

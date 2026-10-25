package com.libertyrecomp;
import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

/** Minimum supported Android version: real launcher click and footer geometry. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=29,qualifiers="w800dp-h360dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Android10MenuTest {
 private static View find(View v,String text){
  if(text.equals(String.valueOf(v.getContentDescription()))||v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),text);if(result!=null)return result;}}return null;
 }
 @Test public void overflowAndBottomFooter()throws Exception{
  Field cached=GpuInfo.class.getDeclaredField("cached");cached.setAccessible(true);cached.set(null,"Adreno (TM) 750");
  try(ActivityController<LauncherActivity> c=Robolectric.buildActivity(LauncherActivity.class).setup()){
   LauncherActivity a=c.get();View root=a.getWindow().getDecorView(),more=find(root,"More actions");assertNotNull(more);more.performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
   AlertDialog logs=ShadowAlertDialog.getLatestAlertDialog();assertTrue(logs.isShowing());assertNotNull(find(logs.getWindow().getDecorView(),"Play with logs"));logs.dismiss();
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
   root.measure(View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));root.layout(0,0,1600,720);
   View link=find(root,"GitHub ↗");assertNotNull(link);int[] pos=new int[2];link.getLocationInWindow(pos);assertTrue("No excess bottom gap",root.getHeight()-pos[1]-link.getHeight()<=ModernUi.dp(a,24));
  }
 }
}

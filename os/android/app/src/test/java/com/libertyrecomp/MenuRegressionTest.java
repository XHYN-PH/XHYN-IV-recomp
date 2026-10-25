package com.libertyrecomp;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import java.time.Duration;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;

/** Real Android View trees and click callbacks; no renderer or game data required. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w900dp-h400dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MenuRegressionTest {
    @Before public void noPhysicalGpuRequired()throws Exception {
        Field cached=GpuInfo.class.getDeclaredField("cached");cached.setAccessible(true);cached.set(null,"Adreno (TM) 750");
    }
    private static View find(View root,String text){
        if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;
        if(text.equals(String.valueOf(root.getContentDescription())))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private static View root(Activity activity){return activity.getWindow().getDecorView();}
    private static void click(View root,String text){View v=find(root,text);assertNotNull("Missing action: "+text,v);assertTrue("Disabled action: "+text,v.isEnabled());assertTrue(v.performClick());Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static void press(AlertDialog dialog,int button){dialog.getButton(button).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private static AlertDialog latest(){AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static File data(Activity a){File f=a.getExternalFilesDir(null);return f==null?a.getFilesDir():f;}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void field(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private static void capture(Activity a,View view,String name)throws Exception{
        String folder=System.getProperty("xhyn.screenshots");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        int width=view.getWidth()>0?view.getWidth():a.getResources().getDisplayMetrics().widthPixels,height=view.getHeight()>0?view.getHeight():a.getResources().getDisplayMetrics().heightPixels;
        view.requestLayout();view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
        if(folder==null)return;
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);view.draw(canvas);
        File output=new File(folder,name+".png");output.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(output)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}image.recycle();
    }
    @Test public void overflowOpensLogsRepeatedly()throws Exception{
        try(ActivityController<LauncherActivity> c=Robolectric.buildActivity(LauncherActivity.class).setup()){
            LauncherActivity a=c.get();click(root(a),"More actions");AlertDialog logs=latest();
            assertNotNull(find(logs.getWindow().getDecorView(),"Play with logs"));assertTrue(find(logs.getWindow().getDecorView(),"Share report") instanceof Button);
            logs.dismiss();click(root(a),"More actions");latest().dismiss();capture(a,root(a),"launcher-test8");
        }
    }
    @Test public void logsAlsoOpenThroughSettings(){
        try(ActivityController<LauncherActivity> c=Robolectric.buildActivity(LauncherActivity.class).setup()){
            click(root(c.get()),"Settings");click(latest().getWindow().getDecorView(),"Logs");assertNotNull(find(latest().getWindow().getDecorView(),"Play with logs"));latest().dismiss();
        }
    }
    @Test public void gridActionReturnsActualButton(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            android.widget.LinearLayout grid=new android.widget.LinearLayout(c.get());boolean[] clicked={false};Button button=ModernUi.gridAction(c.get(),grid,"Share",()->clicked[0]=true);
            assertTrue(grid.getChildAt(0) instanceof android.widget.LinearLayout);assertSame(button,((ViewGroup)grid.getChildAt(0)).getChildAt(0));button.performClick();assertTrue(clicked[0]);
        }
    }
    @Test public void turnipResetRefreshesAndDiscardPreservesSavedValues()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();File data=data(a);DriverState.choose(data,"turnip_v38");DriverState.saveOptions(data,"turnip_v38",Map.of("rendering","gmem","disk_cache","false","cache_size","1G"));
            TurnipSettingsUi.show(a,data,false);AlertDialog options=latest();assertNotNull(find(options.getWindow().getDecorView(),"Rendering policy: Tile memory"));
            click(options.getWindow().getDecorView(),"Reset driver options");press(latest(),AlertDialog.BUTTON_POSITIVE);
            assertNotNull(find(options.getWindow().getDecorView(),"Rendering policy: Launcher default"));assertNotNull(find(options.getWindow().getDecorView(),"Driver disk shader cache: On"));
            View row=find(options.getWindow().getDecorView(),"Rendering policy: Launcher default");
            assertTrue(((android.view.ViewGroup.MarginLayoutParams)row.getLayoutParams()).topMargin>=ModernUi.dp(a,8));
            capture(a,options.getWindow().getDecorView(),"turnip-test8");options.onBackPressed();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Unsaved driver options",Shadows.shadowOf(latest()).getTitle().toString());press(latest(),AlertDialog.BUTTON_NEUTRAL);assertTrue(options.isShowing());
            click(options.getWindow().getDecorView(),"Back");press(latest(),AlertDialog.BUTTON_NEGATIVE);
            assertEquals("gmem",DriverState.options(data,"turnip_v38").get("rendering"));
        }
    }
    @Test public void mobileEditorUsesFullWindowAndBackCanDiscard()throws Exception{
        Intent intent=new Intent().putExtra("xbox",false);
        try(ActivityController<TouchEditorActivity> c=Robolectric.buildActivity(TouchEditorActivity.class,intent).setup()){
            TouchEditorActivity a=c.get();View canvas=(View)field(a,"canvas");canvas.layout(0,0,2048,921);
            java.lang.reflect.Method viewport=canvas.getClass().getDeclaredMethod("viewport");viewport.setAccessible(true);android.graphics.RectF bounds=(android.graphics.RectF)viewport.invoke(canvas);
            assertEquals(0,bounds.left,0);assertEquals(0,bounds.top,0);assertEquals(2048,bounds.right,0);assertEquals(921,bounds.bottom,0);
            TouchLayoutDocument doc=(TouchLayoutDocument)field(a,"document");doc.get("on_foot/jump").dx=123;field(a,"dirty",true);
            a.onBackPressed();AlertDialog confirm=latest();assertEquals("Unsaved control layout",Shadows.shadowOf(confirm).getTitle().toString());press(confirm,AlertDialog.BUTTON_NEUTRAL);assertFalse(a.isFinishing());
            capture(a,root(a),"mobile-editor-test8");a.onBackPressed();press(latest(),AlertDialog.BUTTON_NEGATIVE);assertFalse(TouchLayoutDocument.file(data(a)).exists());
        }
    }
    @Test public void xboxEditorRetainsIndependentFullscreenMode()throws Exception{
        Intent intent=new Intent().putExtra("xbox",true);
        try(ActivityController<TouchEditorActivity> c=Robolectric.buildActivity(TouchEditorActivity.class,intent).setup()){
            assertTrue((Boolean)field(c.get(),"xbox"));assertNotNull(find(root(c.get()),"Xbox layout editor"));capture(c.get(),root(c.get()),"xbox-editor-test8");
        }
    }
    @Test public void sessionHeaderPrecedesResumeAndUsesTransparentLogo()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();MobileSettingsUi.Runtime runtime=new MobileSettingsUi.Runtime(){public String get(String key){return key.equals("gta4_session_stats")?"{}":"";}public boolean apply(String[] k,String[] v){return true;}};
            Map<String,Runnable> actions=new LinkedHashMap<>();for(String action:new String[]{"Achievements","Cheats","Settings","Edit mobile controls","Control settings","Edit Xbox controls","Start performance test","Recorded results","Input test (30 seconds)","Capture renderer timings","Capture shadow issue"})actions.put(action,()->{});
            AlertDialog menu=SessionMenuUi.show(a,runtime,"turnip_v38",new SessionMenuUi.State(),actions,()->{});
            View resume=find(menu.getWindow().getDecorView(),"Resume"),waiting=find(menu.getWindow().getDecorView(),"Waiting for gameplay sample");assertNotNull(resume);assertNotNull(waiting);assertNull(find(menu.getWindow().getDecorView(),"Back"));
            capture(a,menu.getWindow().getDecorView(),"session-menu-test8");
            int[] headerPosition=new int[2],resumePosition=new int[2];waiting.getLocationInWindow(headerPosition);resume.getLocationInWindow(resumePosition);
            assertTrue("Stats must be above Resume",headerPosition[1]+waiting.getHeight()<=resumePosition[1]);
            ImageView logo=(ImageView)find(menu.getWindow().getDecorView(),"IV logo");assertNotNull(logo);assertTrue(logo.getDrawable() instanceof BitmapDrawable);
            Bitmap bitmap=((BitmapDrawable)logo.getDrawable()).getBitmap();assertEquals(0,Color.alpha(bitmap.getPixel(0,0)));assertEquals(0,Color.alpha(bitmap.getPixel(bitmap.getWidth()-1,bitmap.getHeight()-1)));
            assertTrue(find(menu.getWindow().getDecorView(),"Achievements").getHeight()>0);
            click(menu.getWindow().getDecorView(),"Diagnostics");assertNull(find(menu.getWindow().getDecorView(),"Recorded results"));assertNotNull(find(menu.getWindow().getDecorView(),"Session details"));assertNotNull(find(menu.getWindow().getDecorView(),"Copy details"));
            menu.dismiss();
        }
    }
    @Test public void choicesKeepScrollAndSavedPendingSettings()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();File data=data(a);MobileConfig.update(MobileConfig.file(data),Map.of("gta4_frame_limit","30"));PendingSettings.save(data,Map.of("resolution","540p"));
            AlertDialog settings=MobileSettingsUi.graphics(a,data,null);View decor=settings.getWindow().getDecorView();click(decor,"Performance");capture(a,decor,"settings-test8");
            android.widget.LinearLayout row=decor.findViewWithTag("gta4_native_converted_texture_disk_cache");assertNotNull(row);
            android.view.ViewParent parent=row.getParent();while(!(parent instanceof android.widget.ScrollView))parent=parent.getParent();android.widget.ScrollView scroll=(android.widget.ScrollView)parent;
            scroll.scrollTo(0,scroll.getChildAt(0).getHeight());Shadows.shadowOf(Looper.getMainLooper()).idle();int before=scroll.getScrollY();assertTrue("Fixture must scroll",before>0);
            click(row,"Off");
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(before,scroll.getScrollY());
            settings.onBackPressed();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals("Unsaved settings",Shadows.shadowOf(latest()).getTitle().toString());press(latest(),AlertDialog.BUTTON_NEUTRAL);assertTrue(settings.isShowing());
            click(decor,"Discard");android.widget.LinearLayout cap=decor.findViewWithTag("gta4_frame_limit");((Button)cap.getChildAt(1)).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();click(latest().getWindow().getDecorView(),"40 FPS");click(decor,"Save");
            assertEquals("40",MobileConfig.read(MobileConfig.file(data)).get("gta4_frame_limit"));assertEquals("540p",PendingSettings.read(data).get("resolution"));settings.dismiss();
        }
    }
    @Test public void settingsSaveAndDiscardNavigateWithoutCrashing(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            AlertDialog settings=MobileSettingsUi.graphics(c.get(),data(c.get()),null);assertNotNull(settings);
            click(settings.getWindow().getDecorView(),"Overlay");click(settings.getWindow().getDecorView(),"Performance");click(settings.getWindow().getDecorView(),"Discard");click(settings.getWindow().getDecorView(),"Save");settings.dismiss();
        }
    }
    @Test public void overlayEditorKeepsChangesInDraftUntilSettingsSave()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();File data=data(a);AlertDialog settings=MobileSettingsUi.graphics(a,data,null);View decor=settings.getWindow().getDecorView();click(decor,"Overlay");click(decor,"Edit overlay");AlertDialog editor=latest();assertEquals(-1,editor.getWindow().getAttributes().width);assertEquals(-1,editor.getWindow().getAttributes().height);
            click(editor.getWindow().getDecorView(),"Text size increase");click(editor.getWindow().getDecorView(),"Position");click(latest().getWindow().getDecorView(),"Bottom left");capture(a,editor.getWindow().getDecorView(),"overlay-editor-test8");click(editor.getWindow().getDecorView(),"Save");
            assertFalse(MobileConfig.read(MobileConfig.file(data)).containsKey("gta4_overlay_position"));click(decor,"Save");assertEquals("bottom_left",MobileConfig.read(MobileConfig.file(data)).get("gta4_overlay_position"));assertEquals("13",MobileConfig.read(MobileConfig.file(data)).get("gta4_overlay_text_size"));settings.dismiss();
        }
    }
    @Test public void controllerBackHonorsUnsavedGuard(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();boolean[] allow={false};AlertDialog dialog=ModernUi.screen(a,"Controller",ModernUi.text(a,"Body",14,ModernUi.TEXT),null,null,()->allow[0]);dialog.show();
            android.view.KeyEvent back=new android.view.KeyEvent(0,1,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BUTTON_B,0,0,0,0,0,android.view.InputDevice.SOURCE_GAMEPAD);
            dialog.dispatchKeyEvent(back);assertTrue(dialog.isShowing());allow[0]=true;dialog.dispatchKeyEvent(back);assertFalse(dialog.isShowing());
        }
    }
    @Test public void inlineTogglesAndControllerSettingsPersist()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();File data=data(a);AlertDialog settings=MobileSettingsUi.graphics(a,data,null);View decor=settings.getWindow().getDecorView();click(decor,"Controller");android.widget.LinearLayout invert=decor.findViewWithTag("gamepad_invert_right_y");assertNotNull(invert);click(invert,"On");assertSame(settings,latest());click(decor,"Save");assertEquals("true",MobileConfig.read(MobileConfig.file(data)).get("gamepad_invert_right_y"));capture(a,decor,"controller-settings-test8");settings.dismiss();
        }
    }
    @Test public void controllerCanCloseConfirmationAndToggleFilmGrainCorrectly()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup()){
            Activity a=c.get();AlertDialog confirm=ModernUi.builder(a).setTitle("Confirm").setPositiveButton("Keep",null).setNegativeButton("Cancel",null).create();confirm.show();confirm.dispatchKeyEvent(new android.view.KeyEvent(0,1,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BUTTON_B,0,0,0,0,0,android.view.InputDevice.SOURCE_GAMEPAD));assertFalse(confirm.isShowing());
            File data=data(a);AlertDialog settings=MobileSettingsUi.graphics(a,data,null);View decor=settings.getWindow().getDecorView();click(decor,"Graphics");android.widget.LinearLayout grain=decor.findViewWithTag("gta4_disable_tlad_film_grain");if(grain==null){click(decor,"Video");grain=decor.findViewWithTag("gta4_disable_tlad_film_grain");}assertNotNull(grain);click(grain,"Off");click(decor,"Save");assertEquals("true",MobileConfig.read(MobileConfig.file(data)).get("gta4_disable_tlad_film_grain"));settings.dismiss();
        }
    }
}

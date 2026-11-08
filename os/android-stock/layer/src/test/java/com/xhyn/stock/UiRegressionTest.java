package com.xhyn.stock;

import static org.junit.Assert.*;
import a.i;
import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w800dp-h360dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRegressionTest {
    @Before public void setup()throws Exception{i.values.clear();i.writes=0;SaveData.deleteTree(StockApi.data(RuntimeEnvironment.getApplication()));StockApi.data(RuntimeEnvironment.getApplication()).mkdirs();}
    static View find(View root,String value){
        if(root instanceof TextView && value.equals(((TextView)root).getText().toString()))return root;
        if(value.equals(String.valueOf(root.getContentDescription())))return root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;
    }
    static void click(View root,String value){View v=find(root,value);assertNotNull("Missing: "+value,v);assertTrue(v.performClick());idle();}
    static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));}
    static void layout(View root){root.measure(View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));root.layout(0,0,1600,720);}
    static void jobs(PanelActivity a)throws Exception{Field field=PanelActivity.class.getDeclaredField("worker");field.setAccessible(true);((ExecutorService)field.get(a)).submit(()->{}).get(15,TimeUnit.SECONDS);idle();}
    static KeyEvent key(int action,int code){return new KeyEvent(0,0,action,code,0,0,1,0,0,InputDevice.SOURCE_GAMEPAD);}
    static ActivityController<PanelActivity> panel(String page){return Robolectric.buildActivity(PanelActivity.class,new Intent().putExtra("page",page)).setup().visible();}
    static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    static void screenshot(View root,String name)throws Exception {
        String folder=System.getProperty("xhyn.screenshots");if(folder==null)return;layout(root);Bitmap image=Bitmap.createBitmap(1600,720,Bitmap.Config.ARGB_8888);root.draw(new Canvas(image));File output=new File(folder,name+".png");output.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    @Test public void controllerAActivatesOnceAndCanceledBDoesNothing(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            Activity a=c.get();LinearLayout root=Ui.column(a);Button b=Ui.button(a,"Action",false);root.addView(b);a.setContentView(root);layout(root);b.requestFocus();int[] clicks={0};b.setOnClickListener(v->clicks[0]++);
            Navigation.install(a,a.getWindow(),false,null);Window.Callback callback=a.getWindow().getCallback();callback.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A));assertEquals(0,clicks[0]);callback.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A));assertEquals(1,clicks[0]);
            KeyEvent canceled=KeyEvent.changeFlags(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B),KeyEvent.FLAG_CANCELED);callback.dispatchKeyEvent(canceled);assertFalse(a.isFinishing());
        }
    }
    @Test public void shoulderButtonsCycleVisibleTabsAndKeepFocus(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            Activity a=c.get();LinearLayout row=new LinearLayout(a);Button first=Ui.button(a,"First",false),second=Ui.button(a,"Second",false);first.setTag("tab");second.setTag("tab");first.setSelected(true);row.addView(first);row.addView(second);a.setContentView(row);layout(row);int[] clicks={0};second.setOnClickListener(v->clicks[0]++);
            Navigation.install(a,a.getWindow(),false,null);a.getWindow().getCallback().dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R1));assertEquals(1,clicks[0]);assertTrue(second.hasFocus());
        }
    }
    public static class InputActivity extends Activity {int keys,motion;@Override public boolean dispatchKeyEvent(KeyEvent e){keys++;return false;}@Override public boolean dispatchGenericMotionEvent(MotionEvent e){motion++;return false;}}
    @Test public void gameInputPassesThroughExceptDocumentedMenuShortcut(){
        try(ActivityController<InputActivity> c=Robolectric.buildActivity(InputActivity.class).setup().visible()){
            InputActivity a=c.get();int[] menu={0};Navigation.install(a,a.getWindow(),true,()->menu[0]++);Window.Callback callback=a.getWindow().getCallback();
            callback.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A));callback.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));assertEquals(2,a.keys);
            MotionEvent e=MotionEvent.obtain(0,0,MotionEvent.ACTION_MOVE,0,0,0);e.setSource(InputDevice.SOURCE_JOYSTICK);callback.dispatchGenericMotionEvent(e);e.recycle();assertEquals(1,a.motion);
            callback.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_START));assertEquals(0,menu[0]);callback.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_START));assertEquals(0,menu[0]);assertEquals(4,a.keys);
            callback.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_START));Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650));assertEquals(1,menu[0]);assertEquals(6,a.keys);callback.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_START));assertEquals(6,a.keys);
        }
    }
    @Test public void longCompactDialogKeepsItsActionsReachable()throws Exception{
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            Activity a=c.get();layout(a.getWindow().getDecorView());AlertDialog d=Ui.dialog(a,"Long details","Line of details\n".repeat(100),"Confirm",()->{});idle();
            View decor=d.getWindow().getDecorView();decor.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
            View confirm=find(decor,"Confirm");Rect r=new Rect();assertTrue(confirm.getGlobalVisibleRect(r));assertTrue(r.bottom<=720);assertTrue(decor.getMeasuredHeight()<=720);d.dismiss();
        }
    }
    @Test public void dialogBClosesDialogAndLeavesActivityOpen(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            Activity a=c.get();AlertDialog d=Ui.dialog(a,"Details","Small explanation","Close",null);idle();d.getWindow().getCallback().dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_B));idle();assertFalse(d.isShowing());assertFalse(a.isFinishing());
        }
    }
    @Test public void largeFontDialogKeepsActionsWithinTheWindow(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity a=c.get();android.content.res.Configuration config=new android.content.res.Configuration(a.getResources().getConfiguration());config.fontScale=2;
            a.getResources().updateConfiguration(config,a.getResources().getDisplayMetrics());
            AlertDialog d=Ui.dialog(a,"Achievement details","Long achievement explanation\n".repeat(60),"Close",null);idle();View decor=d.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.getMeasuredWidth(),decor.getMeasuredHeight());
            View close=find(decor,"Close");Rect visible=new Rect();assertTrue(close.getGlobalVisibleRect(visible));assertTrue(visible.bottom<=720);d.dismiss();
        }
    }
    @Test public void settingsDoNotWriteUntilSaveAndDiscardRetainsCurrentValues()throws Exception{
        try(ActivityController<PanelActivity> c=panel("settings")){
            PanelActivity a=c.get();View root=a.getWindow().getDecorView();layout(root);click(root,"720p");AlertDialog choices=ShadowAlertDialog.getLatestAlertDialog();click(choices.getWindow().getDecorView(),"1080p");assertTrue(a.dirty);assertEquals(0, i.writes);
            click(root,"Discard");assertFalse(a.dirty);assertEquals(0,i.writes);assertNotNull(find(root,"720p"));
            click(root,"720p");click(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"1080p");click(root,"Save");jobs(a);assertEquals("1080p",NextSettings.pending(a).get("resolution"));assertEquals(0,i.writes);assertFalse(a.dirty);NextSettings.activate(a);assertEquals("1080p",i.values.get("resolution"));screenshot(root,"settings");
        }
    }
    @Test public void settingsFooterFitsAboveTheBottomInset(){
        try(ActivityController<PanelActivity> c=panel("settings")){
            PanelActivity a=c.get();WindowInsets in=new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,0,0,48)).build();a.root.dispatchApplyWindowInsets(in);layout(a.getWindow().getDecorView());
            View save=find(a.root,"Save");int[] position=new int[2];save.getLocationInWindow(position);assertTrue(position[1]+save.getHeight()<=720-48);assertTrue(save.getHeight()>0);
        }
    }
    @Test public void fullscreenOverlayCanvasReachesBothEdges()throws Exception{
        try(ActivityController<PanelActivity> c=panel("overlay-editor")){
            PanelActivity a=c.get();AlertDialog intro=ShadowAlertDialog.getLatestAlertDialog();if(intro!=null)intro.dismiss();ViewGroup content=a.findViewById(android.R.id.content);FrameLayout frame=(FrameLayout)content.getChildAt(0);layout(frame);
            View canvas=frame.getChildAt(0);assertEquals(frame.getWidth(),canvas.getWidth());assertEquals(frame.getHeight(),canvas.getHeight());
            assertTrue(((Navigation.DirectionalEditor)canvas).direction(KeyEvent.KEYCODE_DPAD_DOWN));screenshot(frame,"overlay-editor");
            RectF tl=HudPlacement.bounds(1600,720,240,120,0,0),br=HudPlacement.bounds(1600,720,240,120,1,1);assertEquals(0,tl.top,.01);assertEquals(720,br.bottom,.01);assertEquals(1600,br.right,.01);
        }
    }
    @Test public void changingMetricsDoesNotOverwritePlacementSavedByEditor()throws Exception{
        LayerPreferences.set(RuntimeEnvironment.getApplication(),"x",0);
        try(ActivityController<PanelActivity> c=panel("overlay")){
            PanelActivity a=c.get();LayerPreferences.set(a,"x",1);click(a.getWindow().getDecorView(),"Save");assertEquals(1,LayerPreferences.read(a).getInt("x"));
        }
    }
    @Test public void fullscreenTouchEditorPreservesViewportAndUndo()throws Exception{
        try(ActivityController<PanelActivity> c=panel("touch")){
            PanelActivity a=c.get();layout(a.getWindow().getDecorView());click(a.getWindow().getDecorView(),"Open fullscreen layout editor");AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertEquals(-1,d.getWindow().getAttributes().width);assertEquals(-1,d.getWindow().getAttributes().height);
            View decor=d.getWindow().getDecorView();layout(decor);idle();click(decor,"Compact this mode");assertTrue(a.dirty);click(decor,"Undo");click(decor,"Done · review & save");assertFalse(d.isShowing());assertTrue(a.dirty);screenshot(a.getWindow().getDecorView(),"touch-settings");
        }
    }
    @Test public void cancellingPickerClearsPendingActionWithoutChangingFiles()throws Exception{
        try(ActivityController<PanelActivity> c=panel("drivers")){
            PanelActivity a=c.get();a.pendingAction="driver-import";a.onActivityResult(71,Activity.RESULT_CANCELED,null);assertEquals("",a.pendingAction);assertFalse(new File(StockApi.data(a),"driver.txt").exists());
        }
    }
    @Test public void homeRetainsStockInstallerAndUsesStockLaunchChecks()throws Exception{
        try(ActivityController<com.libertyrecomp.LauncherActivity> c=Robolectric.buildActivity(com.libertyrecomp.LauncherActivity.class).setup().visible()){
            com.libertyrecomp.LauncherActivity a=c.get();HomeScreen home=new HomeScreen(a,null);home.show();layout(a.getWindow().getDecorView());click(a.getWindow().getDecorView(),"Play");assertEquals(1,a.launches);
            click(a.getWindow().getDecorView(),"Game files & stock installer");assertNotNull(find(a.getWindow().getDecorView(),"Stock installer"));click(a.getWindow().getDecorView(),"‹ Back");assertNotNull(find(a.getWindow().getDecorView(),"Play"));screenshot(a.getWindow().getDecorView(),"home");
        }
    }
    @Test public void secretAchievementDetailsRemainHidden(){
        JSONObject e=new JSONObject();try{e.put("hidden",true).put("name","Spoiler title").put("description","Spoiler details");}catch(JSONException ex){throw new AssertionError(ex);}
        assertEquals("Secret achievement",AchievementsPage.title(e,false));assertFalse(AchievementsPage.description(e,false).contains("Spoiler"));assertEquals("Spoiler title",AchievementsPage.title(e,true));
    }
    @Test public void keyboardIsNotListedAsAController(){assertFalse(ControllerPage.controllerSources(InputDevice.SOURCE_KEYBOARD));assertTrue(ControllerPage.controllerSources(InputDevice.SOURCE_GAMEPAD));assertTrue(ControllerPage.controllerSources(InputDevice.SOURCE_JOYSTICK));}
    @Test public void controllerTesterReportsAInsteadOfActivatingItsOldFocusedButton(){
        try(ActivityController<PanelActivity> c=panel("controllers")){
            PanelActivity a=c.get();View root=a.getWindow().getDecorView();layout(root);click(root,"Test sticks & buttons");Window.Callback callback=a.getWindow().getCallback();
            callback.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_A));callback.dispatchKeyEvent(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_A));View state=root.findViewWithTag("controller-test");assertNotNull("Tester was deactivated by A",state);assertEquals("A released",((TextView)state).getText().toString());
        }
    }
    @Test public void tappingOverlayCanvasOnlyTogglesToolsWithoutStagingAMove(){
        try(ActivityController<PanelActivity> c=panel("overlay-editor")) {
            PanelActivity a=c.get();AlertDialog intro=ShadowAlertDialog.getLatestAlertDialog();if(intro!=null)intro.dismiss();FrameLayout frame=(FrameLayout)((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);layout(frame);View canvas=frame.getChildAt(0),tools=frame.getChildAt(1);
            MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,1200,600,0),up=MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,1200,600,0);canvas.dispatchTouchEvent(down);canvas.dispatchTouchEvent(up);down.recycle();up.recycle();assertFalse(a.dirty);assertEquals(View.GONE,tools.getVisibility());
        }
    }
    @Test public void keyboardInsetKeepsSettingsActionsAboveTheKeyboard(){
        try(ActivityController<PanelActivity> c=panel("settings")) {
            PanelActivity a=c.get();WindowInsets in=new WindowInsets.Builder().setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,280)).build();a.root.dispatchApplyWindowInsets(in);layout(a.getWindow().getDecorView());View save=find(a.root,"Save");int[] position=new int[2];save.getLocationInWindow(position);assertTrue(position[1]+save.getHeight()<=720-280);
        }
    }
}

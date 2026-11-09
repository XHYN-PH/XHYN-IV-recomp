package com.libertyrecomp;

import static org.junit.Assert.*;
import a.i;
import a.n;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Exercises the assembled base classes, without replacement launcher stubs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w800dp-h360dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BaseUiTest {
    private ActivityController<LauncherActivity> controller;
    private LauncherActivity activity;

    @Before public void open() {
        Installer.c = false;
        controller = Robolectric.buildActivity(LauncherActivity.class).create().start().visible();
        activity = controller.get();
        layout(activity.m);
    }
    @After public void close() { Installer.c = false; controller.pause().stop().destroy(); }

    static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        view.layout(0,0,1600,720);
    }
    static void english(View view) {
        if (view instanceof TextView) assertFalse(((TextView)view).getText().toString(), ((TextView)view).getText().toString().matches("(?s).*[А-Яа-яЁё].*"));
        if (view.getContentDescription()!=null) assertFalse(view.getContentDescription().toString().matches("(?s).*[А-Яа-яЁё].*"));
        if (view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) english(((ViewGroup)view).getChildAt(i));
    }
    static View find(View view, String text) {
        if(view instanceof TextView && text.equals(((TextView)view).getText().toString())) return view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=find(((ViewGroup)view).getChildAt(i),text); if(found!=null)return found; }
        return null;
    }
    void data(boolean installed) {
        activity.q=true; activity.r=installed; activity.s=false;
        activity.D(installed ? "installed" : "not installed");
        activity.m.r="cache"; activity.m.n(); layout(activity.m);
    }
    void screenshot(String name) throws Exception {
        String folder=System.getProperty("xhyn.screenshots"); if(folder==null)return;
        Bitmap image=Bitmap.createBitmap(1600,720,Bitmap.Config.ARGB_8888);
        activity.m.draw(new Canvas(image)); File file=new File(folder,name+".png"); file.getParentFile().mkdirs();
        try(OutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);} image.recycle();
    }
    @Test public void originalPagesAndSettingsTabsAreEnglish() throws Exception {
        assertEquals("a.P",activity.m.getClass().getName());
        screenshot("base-home");
        for(String page:new String[]{"home","cache","settings","diagnostics"}) {
            activity.m.r=page;
            for(String tab:new String[]{"Graphics","Controls","Audio","Game"}) {
                activity.m.t=tab; activity.m.n(); layout(activity.m); english(activity.m);
            }
        }
    }
    @Test public void isoAndTitleUpdateRemainVisibleWhenDataIsInstalled() throws Exception {
        data(true);
        assertEquals(View.VISIBLE,activity.A.getVisibility()); assertEquals(View.VISIBLE,activity.B.getVisibility());
        assertNotNull(find(activity.m,"Game disc image (.iso)")); assertNotNull(find(activity.m,"Title Update 8 (TU8)"));
        assertFalse(activity.D.isEnabled()); english(activity.m);
        for(String label:new String[]{"Game disc image (.iso)","Title Update 8 (TU8)","Install"}) {
            View view=find(activity.m,label); assertNotNull(view);
            view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);
            Rect visible=new Rect(); assertTrue(label,view.getGlobalVisibleRect(visible));
            assertEquals(label,view.getHeight(),visible.height());
            if(label.startsWith("Game disc")) screenshot("base-game-data-iso");
            if(label.startsWith("Title Update")) screenshot("base-game-data-tu");
        }
        activity.D("installed"); assertEquals(View.VISIBLE,activity.A.getVisibility());
    }
    @Test public void installerStillRequiresValidIsoAndTitleUpdate() {
        data(false); assertFalse(activity.D.isEnabled());
        activity.t[0]="/storage/game.iso"; activity.v=new n("Supported game",true);
        activity.D("not installed"); assertFalse(activity.D.isEnabled());
        activity.t[1]="/storage/TU8"; activity.D("not installed"); assertTrue(activity.D.isEnabled());
        activity.v=new n("Unsupported disc",false); activity.D("not installed"); assertFalse(activity.D.isEnabled());
        activity.v=new n("Supported game",true); activity.w=true; activity.D("not installed"); assertFalse(activity.D.isEnabled());
    }
    @Test public void busyInstallKeepsCancellationAndHidesPickers() {
        data(true); Installer.c=true; activity.D("installing");
        assertEquals(View.GONE,activity.B.getVisibility()); assertEquals(View.VISIBLE,activity.E.getVisibility());
        assertEquals(View.VISIBLE,activity.F.getVisibility()); assertFalse(activity.D.isEnabled());
        Installer.c=false; activity.D("installed"); assertEquals(View.VISIBLE,activity.B.getVisibility());
    }
    @Test @Config(sdk=29) public void isoAndTuButtonsUseOriginalDocumentPicker() {
        data(true);
        Shadows.shadowOf((android.app.Application)activity.getApplication()).grantPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        List<Button> pickers=new ArrayList<>();
        for(int j=0;j<activity.B.getChildCount();j++) {
            View child=activity.B.getChildAt(j);
            if(child instanceof LinearLayout) for(int k=0;k<((LinearLayout)child).getChildCount();k++) {
                View button=((LinearLayout)child).getChildAt(k);
                if(button instanceof Button && "Choose file...".equals(((Button)button).getText().toString())) pickers.add((Button)button);
            }
        }
        assertEquals(4,pickers.size());
        for(int i=0;i<2;i++) {
            assertTrue(pickers.get(i).performClick());
            org.robolectric.shadows.ShadowActivity.IntentForResult result=Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(result); assertEquals(Intent.ACTION_OPEN_DOCUMENT,result.intent.getAction()); assertEquals(10+i,result.requestCode);
        }
    }
    @Test public void englishMigrationPreservesOtherSettingsAndLaterLanguageChoice() throws Exception {
        File root=Files.createTempDirectory("base-language").toFile();
        i.J(root,Map.of("user_language","4","draw_distance","65","custom_setting","keep"));
        Method migrate=LauncherActivity.class.getDeclaredMethod("xhynEnsureEnglish",File.class); migrate.setAccessible(true); migrate.invoke(null,root);
        assertEquals("0",i.z(root).get("user_language")); assertEquals("65",i.z(root).get("draw_distance")); assertEquals("keep",i.z(root).get("custom_setting"));
        i.J(root,Collections.singletonMap("user_language","2")); migrate.invoke(null,root);
        assertEquals("2",i.z(root).get("user_language"));
    }
}

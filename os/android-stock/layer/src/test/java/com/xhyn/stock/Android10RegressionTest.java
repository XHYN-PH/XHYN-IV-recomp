package com.xhyn.stock;

import static org.junit.Assert.*;
import android.content.Intent;
import android.graphics.RectF;
import android.view.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=29,qualifiers="w800dp-h360dp-land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class Android10RegressionTest {
    @Test public void minimumApiCanOpenOverlayAndReachExtremePlacement(){
        try(var c=Robolectric.buildActivity(PanelActivity.class,new Intent().putExtra("page","overlay")).setup().visible()){
            PanelActivity a=c.get();UiRegressionTest.layout(a.getWindow().getDecorView());assertNotNull(UiRegressionTest.find(a.root,"Save & edit placement"));
            RectF box=HudPlacement.bounds(1600,720,240,120,1,1);assertEquals(1600,box.right,.01);assertEquals(720,box.bottom,.01);
        }
    }
}

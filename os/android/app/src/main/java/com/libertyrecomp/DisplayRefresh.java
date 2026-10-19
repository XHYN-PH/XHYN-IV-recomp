package com.libertyrecomp;
import android.app.Activity;
import android.os.Build;
import android.view.*;
import android.util.Log;
import java.util.*;

final class DisplayRefresh {
    @SuppressWarnings("deprecation")
    static void apply(Activity a,Surface surface,float cap) {
        Display display=a.getWindowManager().getDefaultDisplay();if(display==null)return;
        Display.Mode current=display.getMode();ArrayList<Display.Mode> compatible=new ArrayList<>();
        for(Display.Mode mode:display.getSupportedModes())if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())compatible.add(mode);
        float[] rates=new float[compatible.size()];for(int i=0;i<rates.length;i++)rates[i]=compatible.get(i).getRefreshRate();
        float target=RefreshPolicy.choose(rates,cap);if(target<=0)return;
        WindowManager.LayoutParams params=a.getWindow().getAttributes();
        for(Display.Mode mode:compatible)if(Math.abs(mode.getRefreshRate()-target)<0.1f){params.preferredDisplayModeId=mode.getModeId();break;}
        params.preferredRefreshRate=target;a.getWindow().setAttributes(params);
        if(surface!=null&&surface.isValid()&&Build.VERSION.SDK_INT>=30)try {
            float frameRate=cap>0?cap:target;
            if(Build.VERSION.SDK_INT>=31)surface.setFrameRate(frameRate,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,Surface.CHANGE_FRAME_RATE_ALWAYS);
            else surface.setFrameRate(frameRate,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }catch(IllegalArgumentException|IllegalStateException error){Log.w("LibertyRecomp","Frame-rate request rejected",error);}
        Log.i("LibertyRecomp","Display: cap="+cap+" requested="+target+" active="+display.getRefreshRate()+" Hz");
    }
}

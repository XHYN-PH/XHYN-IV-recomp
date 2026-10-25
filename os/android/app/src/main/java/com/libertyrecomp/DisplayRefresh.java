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
        float target=RefreshPolicy.choose(rates,cap,UiPreferences.get(a,"battery_refresh"));if(target<=0)return;
        WindowManager.LayoutParams params=a.getWindow().getAttributes();int previousMode=params.preferredDisplayModeId;
        for(Display.Mode mode:compatible)if(Math.abs(mode.getRefreshRate()-target)<0.1f){params.preferredDisplayModeId=mode.getModeId();break;}
        boolean changed=previousMode!=params.preferredDisplayModeId||Math.abs(params.preferredRefreshRate-target)>.1f;params.preferredRefreshRate=target;if(changed)a.getWindow().setAttributes(params);
        if(surface!=null&&surface.isValid()&&Build.VERSION.SDK_INT>=30)try {
            float frameRate=cap>0?cap:target;
            if(Build.VERSION.SDK_INT>=31)surface.setFrameRate(frameRate,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
            else surface.setFrameRate(frameRate,Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }catch(IllegalArgumentException|IllegalStateException error){Log.w("LibertyRecomp","Frame-rate request rejected",error);}
        String report="Display: cap="+cap+" requested="+target+" active="+display.getRefreshRate()+" Hz · seamless changes only";Log.i("LibertyRecomp",report);DebugLog.note(report);
    }
}

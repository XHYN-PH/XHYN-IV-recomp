package com.libertyrecomp;
import android.app.Activity;
import android.graphics.Insets;
import android.view.*;
import java.io.*;
import java.util.*;
/** Display and saved-renderer identity used only to validate editor calibration. */
final class ViewportIdentity {
    static final String[] KEYS={"resolution","gta4_native_upscaler","gta4_fsr1_quality"};
    static String settings(Map<String,String> values){
        String[] defaults={"720p","native","quality"};StringBuilder out=new StringBuilder();
        for(int i=0;i<KEYS.length;i++)out.append(KEYS[i]).append('=').append(values.getOrDefault(KEYS[i],defaults[i])).append(';');return out.toString();
    }
    static String savedSettings(File data){try{return settings(MobileConfig.read(MobileConfig.file(data)));}catch(IOException e){return "";}}
    static String display(Activity a){
        View root=a.getWindow().getDecorView();WindowInsets insets=root.getRootWindowInsets();DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
        int width=root.getWidth()>0?root.getWidth():a.getResources().getDisplayMetrics().widthPixels;
        int height=root.getHeight()>0?root.getHeight():a.getResources().getDisplayMetrics().heightPixels;
        Insets gestures=insets==null?Insets.NONE:insets.getMandatorySystemGestureInsets();
        return width+"x"+height+":"+a.getWindowManager().getDefaultDisplay().getRotation()+":"+a.getResources().getDisplayMetrics().densityDpi+":"+
            (cutout==null?"0:0:0:0":cutout.getSafeInsetLeft()+":"+cutout.getSafeInsetTop()+":"+cutout.getSafeInsetRight()+":"+cutout.getSafeInsetBottom())+":"+gestures.left+":"+gestures.top+":"+gestures.right+":"+gestures.bottom;
    }
    private ViewportIdentity(){}
}


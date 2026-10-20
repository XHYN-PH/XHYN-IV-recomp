package com.libertyrecomp;

import android.content.Context;
import android.os.*;

/** Sustained game rumble, coalesced on the main thread and stopped on pause. */
final class PhoneRumble {
    private final Vibrator vibrator;
    private final boolean supported,amplitudeControl;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable update=this::apply;
    private boolean active,closed;
    private int low,high;
    private float strength=.5f;
    PhoneRumble(Context context){
        Vibrator found=null;
        try{
            if(Build.VERSION.SDK_INT>=31){VibratorManager manager=context.getSystemService(VibratorManager.class);if(manager!=null)found=manager.getDefaultVibrator();}
            else found=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        }catch(RuntimeException ignored){}
        vibrator=found;
        boolean motor=false,amplitude=false;
        try{motor=found!=null&&found.hasVibrator();amplitude=motor&&found.hasAmplitudeControl();}catch(RuntimeException ignored){}
        supported=motor;amplitudeControl=amplitude;
    }
    boolean available(){return supported;}
    synchronized boolean motors(int left,int right,float gain){
        if(closed||!available())return false;
        if(!active){left=0;right=0;}
        float amount=Float.isFinite(gain)?Math.max(0,Math.min(1,gain)):0;
        if(low!=left||high!=right||strength!=amount){low=left;high=right;strength=amount;schedule();}
        return true;
    }
    synchronized void strength(float value){strength=Float.isFinite(value)?Math.max(0,Math.min(1,value)):.5f;schedule();}
    synchronized void active(boolean value){
        if(closed)return;
        active=value;
        if(!value){low=high=0;}
        schedule();
    }
    synchronized void close(){active=false;low=high=0;closed=true;schedule();}
    private void schedule(){main.removeCallbacks(update);main.post(update);}
    private synchronized void apply(){
        if(!available())return;
        int amplitude=active&&!closed?PhoneRumblePolicy.amplitude(low,high,strength):0;
        main.removeCallbacks(update);
        try{
            if(amplitude==0)vibrator.cancel();
            else{
                vibrator.vibrate(VibrationEffect.createOneShot(300,amplitudeControl?amplitude:VibrationEffect.DEFAULT_AMPLITUDE));
                // XInput holds its last motor state. Refresh a finite effect;
                // lifecycle cancellation also has a short hardware timeout.
                main.postDelayed(update,180);
            }
        }catch(RuntimeException ignored){low=high=0;}
    }
}

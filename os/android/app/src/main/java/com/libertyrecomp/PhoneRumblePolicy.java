package com.libertyrecomp;

/** Maps the two Xbox motors to one phone actuator. No Android dependencies. */
final class PhoneRumblePolicy {
    static int amplitude(int low,int high,float strength) {
        if(!Float.isFinite(strength)||strength<=0)return 0;
        int motor=Math.max(Math.max(0,Math.min(65535,low)),Math.max(0,Math.min(65535,high)));
        if(motor==0)return 0;
        return Math.max(1,Math.min(255,Math.round(motor/65535f*Math.min(1,strength)*255)));
    }
    private PhoneRumblePolicy(){}
}

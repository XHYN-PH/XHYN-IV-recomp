package com.libertyrecomp;

import org.json.JSONObject;

/** Samples describe protection state and requests, not achieved GPU clocks. */
final class ThermalStatus {
    static String summary(String json,long now,boolean compact){
        try{
            JSONObject s=new JSONObject(json);
            long stamp=s.optLong("sample_ns",0);
            if(!s.optBoolean("sampled")||stamp<=0||now<stamp||now-stamp>20_000_000_000L)return "Thermal status pending";
            String heat=PerformanceHud.thermal(s.optInt("severity",-1));
            String guard=!s.optBoolean("guard_enabled")?"Guard Off":s.optBoolean("guard_active")?"Guard active":"Guard monitoring";
            if(compact)return heat+" · "+guard;
            String turbo=s.optBoolean("turbo_backoff")?"GPU Turbo backed off":s.optBoolean("turbo_requested")?"GPU Turbo requested":"GPU Turbo Off";
            return heat+" · "+guard+"\n"+turbo+" · Draw distance "+s.optDouble("draw_scale",1)+"×";
        }catch(Exception unavailable){return "Thermal status unavailable";}
    }
    private ThermalStatus(){}
}

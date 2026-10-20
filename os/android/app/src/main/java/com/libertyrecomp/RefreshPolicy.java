package com.libertyrecomp;
final class RefreshPolicy {
    static float choose(float[] rates,float cap) {
        float maximum=0,best=0;
        for(float hz:rates) {
            if(!Float.isFinite(hz)||hz<24||hz>360)continue;
            maximum=Math.max(maximum,hz);
            if(cap>0&&hz+0.5f>=cap) {
                float multiple=hz/cap;
                if(Math.abs(multiple-Math.round(multiple))<0.025f)best=Math.max(best,hz);
            }
        }
        return best>0?best:maximum;
    }
}

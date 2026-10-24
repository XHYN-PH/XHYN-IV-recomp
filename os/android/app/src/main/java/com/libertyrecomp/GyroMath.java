package com.libertyrecomp;

/** Device coordinates are fixed to natural orientation, not the current screen. */
final class GyroMath {
    static float[] screenRates(float x,float y,int rotation){
        switch(rotation){case 1:return new float[]{-x,y};case 2:return new float[]{-y,-x};case 3:return new float[]{x,-y};default:return new float[]{y,x};}
    }
    static double seconds(long previous,long current){return previous>0&&current>previous&&current-previous<=100000000L?(current-previous)/1e9:0;}
    private GyroMath(){}
}

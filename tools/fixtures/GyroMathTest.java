package com.libertyrecomp;
public class GyroMathTest {
 public static void main(String[] args){
  float[][] expected={{2,1},{-1,2},{-2,-1},{1,-2}};
  for(int rotation=0;rotation<4;rotation++){float[] rates=GyroMath.screenRates(1,2,rotation);assert rates[0]==expected[rotation][0]&&rates[1]==expected[rotation][1];}
  assert GyroMath.seconds(0,10000000)==0;assert GyroMath.seconds(20000000,10000000)==0;
  assert GyroMath.seconds(1,200000002)==0;assert Math.abs(GyroMath.seconds(10000000,30000000)-.02)<1e-9;
  System.out.println("Phone gyro orientation and timing tests passed");
 }
}

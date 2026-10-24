package com.libertyrecomp;
/** Pure placement math shared by the live overlay and its settings preview. */
final class OverlayPlacement {
 static float[] place(String preset,float x,float y,int width,int height,int boxWidth,int boxHeight,int left,int top,int right,int bottom,int gap){
  if(!"custom".equals(preset)){x=preset.endsWith("center")?.5f:preset.endsWith("right")?1:0;y=preset.startsWith("bottom")?1:0;}
  x=Float.isFinite(x)?Math.max(0,Math.min(1,x)):.5f;y=Float.isFinite(y)?Math.max(0,Math.min(1,y)):.1f;
  float minX=Math.min(Math.max(0,left+gap),Math.max(0,width-boxWidth)),minY=Math.min(Math.max(0,top+gap),Math.max(0,height-boxHeight));
  float maxX=Math.max(minX,width-right-gap-boxWidth),maxY=Math.max(minY,height-bottom-gap-boxHeight);
  return new float[]{minX+(maxX-minX)*x,minY+(maxY-minY)*y};
 }
 private OverlayPlacement(){}
}

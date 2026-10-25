package com.libertyrecomp;
import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
/** Raw Android hardware preview, independent of gameplay and touch controls. */
final class ControllerTestUi {
 static void show(Activity a){
  LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
  TextView device=ModernUi.text(a,"Move either stick or press a controller button.",13,ModernUi.MUTED);body.addView(device);
  TextView values=ModernUi.text(a,"Raw hardware input · tuning applies in gameplay",12,ModernUi.ACCENT);
  View sticks=new View(a){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final float[] axes=new float[6];
   @Override protected void onDraw(Canvas c){float radius=Math.min(getHeight()*.32f,getWidth()*.16f);for(int i=0;i<2;i++){float x=getWidth()*(i==0?.27f:.73f),y=getHeight()*.46f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(ModernUi.MUTED);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);p.setColor(ModernUi.ACCENT);c.drawCircle(x+axes[i*2]*radius,y+axes[i*2+1]*radius,ModernUi.dp(a,7),p);}}
   @Override public boolean onGenericMotionEvent(MotionEvent e){axes[0]=e.getAxisValue(MotionEvent.AXIS_X);axes[1]=e.getAxisValue(MotionEvent.AXIS_Y);axes[2]=e.getAxisValue(MotionEvent.AXIS_Z);axes[3]=e.getAxisValue(MotionEvent.AXIS_RZ);axes[4]=Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_BRAKE));axes[5]=Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER),e.getAxisValue(MotionEvent.AXIS_GAS));if(e.getDevice()!=null)device.setText(e.getDevice().getName());values.setText(String.format(java.util.Locale.ROOT,"Left %.2f / %.2f   Right %.2f / %.2f   Triggers %.2f / %.2f",axes[0],axes[1],axes[2],axes[3],axes[4],axes[5]));invalidate();return true;}
  };
  body.addView(sticks,new LinearLayout.LayoutParams(-1,ModernUi.dp(a,150)));body.addView(values);
  AlertDialog dialog=new AlertDialog(a){@Override public boolean dispatchGenericMotionEvent(MotionEvent e){if(e.isFromSource(InputDevice.SOURCE_JOYSTICK))return sticks.onGenericMotionEvent(e);return super.dispatchGenericMotionEvent(e);}@Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getKeyCode()==KeyEvent.KEYCODE_BUTTON_B){if(e.getAction()==KeyEvent.ACTION_UP)dismiss();return true;}if(e.isFromSource(InputDevice.SOURCE_GAMEPAD)){values.setText(KeyEvent.keyCodeToString(e.getKeyCode())+(e.getAction()==KeyEvent.ACTION_DOWN?" · pressed":" · released"));return true;}return super.dispatchKeyEvent(e);}};
  LinearLayout panel=ModernUi.panel(a);panel.addView(body);Button back=ModernUi.button(a,"Back",false);panel.addView(back);back.setOnClickListener(v->dialog.dismiss());dialog.setView(panel);dialog.setOnShowListener(v->{Window w=dialog.getWindow();if(w!=null){ModernUi.immersive(w);w.setLayout(Math.min(ModernUi.dp(a,620),(int)(a.getResources().getDisplayMetrics().widthPixels*.92f)),-2);}});if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(ModernUi.motion(a)?R.style.SessionPanelAnimation:0);dialog.show();
 }
}

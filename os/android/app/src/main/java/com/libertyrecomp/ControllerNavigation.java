package com.libertyrecomp;
import android.view.*;
import android.widget.*;
/** Navigation is scoped to the visible Android window; game input is untouched. */
final class ControllerNavigation {
    private long next;private int last;
    boolean key(Window w,KeyEvent e,Runnable back){
        if(!e.isFromSource(InputDevice.SOURCE_GAMEPAD)&&!e.isFromSource(InputDevice.SOURCE_JOYSTICK))return false;
        int k=e.getKeyCode();
        if(k==KeyEvent.KEYCODE_BUTTON_B){if(e.getAction()==KeyEvent.ACTION_UP)back.run();return true;}
        if(k==KeyEvent.KEYCODE_BUTTON_A){View v=w==null?null:w.getCurrentFocus();if(v==null)return false;if(e.getAction()==KeyEvent.ACTION_UP)v.performClick();return true;}
        return false;
    }
    boolean motion(Window w,MotionEvent e){
        if(w==null||!e.isFromSource(InputDevice.SOURCE_JOYSTICK)||e.getAction()!=MotionEvent.ACTION_MOVE)return false;
        float x=e.getAxisValue(MotionEvent.AXIS_HAT_X),y=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if(Math.abs(x)<.5f&&Math.abs(y)<.5f){x=e.getAxisValue(MotionEvent.AXIS_X);y=e.getAxisValue(MotionEvent.AXIS_Y);}
        if(Math.max(Math.abs(x),Math.abs(y))<.55f){last=0;next=0;return true;}
        int direction=Math.abs(x)>Math.abs(y)?(x>0?View.FOCUS_RIGHT:View.FOCUS_LEFT):(y>0?View.FOCUS_DOWN:View.FOCUS_UP);
        long now=e.getEventTime();if(last==direction&&now<next)return true;next=now+(last==direction?110:300);last=direction;
        View focus=w.getCurrentFocus();if(focus==null){ModernUi.focusFirst(w.getDecorView());return true;}
        if((focus instanceof SeekBar||focus instanceof OverlayPreview)&&(focus instanceof OverlayPreview||direction==View.FOCUS_LEFT||direction==View.FOCUS_RIGHT)){
            int key=direction==View.FOCUS_LEFT?KeyEvent.KEYCODE_DPAD_LEFT:direction==View.FOCUS_RIGHT?KeyEvent.KEYCODE_DPAD_RIGHT:direction==View.FOCUS_UP?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN;
            focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key));return true;
        }
        View target=focus.focusSearch(direction);if(target!=null)target.requestFocus(direction);return true;
    }
}

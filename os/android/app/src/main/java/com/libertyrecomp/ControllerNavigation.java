package com.libertyrecomp;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
/** Window-scoped controller navigation; never changes gameplay input. */
final class ControllerNavigation {
    private long next;private int last;
    private static boolean controller(KeyEvent e){return e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)||e.isFromSource(InputDevice.SOURCE_DPAD);}
    boolean key(Window w,KeyEvent e,Runnable back){
        if(!controller(e)&&!(e.getKeyCode()>=KeyEvent.KEYCODE_BUTTON_A&&e.getKeyCode()<=KeyEvent.KEYCODE_BUTTON_MODE))return false;
        int k=e.getKeyCode();
        if(e.isCanceled())return true;
        if(k==KeyEvent.KEYCODE_BUTTON_B){if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled())back.run();return true;}
        if(w==null)return false;
        View focus=w.getCurrentFocus();
        if(k==KeyEvent.KEYCODE_BUTTON_L1||k==KeyEvent.KEYCODE_BUTTON_R1){java.util.ArrayList<View> tabs=new java.util.ArrayList<>();tabs(w.getDecorView(),tabs);if(tabs.isEmpty())return false;if(e.getAction()==KeyEvent.ACTION_UP){int selected=0;for(int i=0;i<tabs.size();i++)if(tabs.get(i).isSelected())selected=i;View target=tabs.get(Math.floorMod(selected+(k==KeyEvent.KEYCODE_BUTTON_R1?1:-1),tabs.size()));target.requestFocus();target.performClick();target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),false);}return true;}
        if(k==KeyEvent.KEYCODE_BUTTON_A||k==KeyEvent.KEYCODE_DPAD_CENTER){
            if(focus==null){ModernUi.focusFirst(w.getDecorView());return true;}
            if(focus instanceof EditText)return false;
            if(e.getAction()==KeyEvent.ACTION_UP&&!e.isCanceled()&&focus.isEnabled())focus.performClick();return true;
        }
        int direction=k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:k==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:k==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:0;
        if(direction!=0){if(focus instanceof EditText||focus instanceof Spinner)return false;if(e.getAction()==KeyEvent.ACTION_DOWN)move(w,direction);return true;}
        return false;
    }
    private void tabs(View view,java.util.List<View> out){if(!view.isShown())return;if("ui-tab".equals(view.getTag())&&view.isEnabled())out.add(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)tabs(((ViewGroup)view).getChildAt(i),out);}
    private void move(Window w,int direction){
        View focus=w.getCurrentFocus();if(focus==null){ModernUi.focusFirst(w.getDecorView());return;}
        if(focus instanceof OverlayPreview||(focus instanceof SeekBar&&(direction==View.FOCUS_LEFT||direction==View.FOCUS_RIGHT))){
            int code=direction==View.FOCUS_LEFT?KeyEvent.KEYCODE_DPAD_LEFT:direction==View.FOCUS_RIGHT?KeyEvent.KEYCODE_DPAD_RIGHT:direction==View.FOCUS_UP?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN;
            focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));focus.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));return;
        }
        View target=focus.focusSearch(direction);
        if(target!=null&&target.isShown()&&target.isEnabled()){target.requestFocus(direction);target.post(()->target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),false));}
        else for(android.view.ViewParent p=focus.getParent();p instanceof View;p=p.getParent())if(p instanceof ScrollView){((ScrollView)p).arrowScroll(direction);break;}
    }
    boolean motion(Window w,MotionEvent e){
        if(w==null||!e.isFromSource(InputDevice.SOURCE_JOYSTICK)||e.getAction()!=MotionEvent.ACTION_MOVE)return false;
        float x=e.getAxisValue(MotionEvent.AXIS_HAT_X),y=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if(Math.abs(x)<.5f&&Math.abs(y)<.5f){x=e.getAxisValue(MotionEvent.AXIS_X);y=e.getAxisValue(MotionEvent.AXIS_Y);}
        if(Math.max(Math.abs(x),Math.abs(y))<.55f){last=0;next=0;return true;}
        int direction=Math.abs(x)>Math.abs(y)?(x>0?View.FOCUS_RIGHT:View.FOCUS_LEFT):(y>0?View.FOCUS_DOWN:View.FOCUS_UP);
        long now=e.getEventTime();if(last==direction&&now<next)return true;next=now+(last==direction?110:300);last=direction;move(w,direction);return true;
    }
}

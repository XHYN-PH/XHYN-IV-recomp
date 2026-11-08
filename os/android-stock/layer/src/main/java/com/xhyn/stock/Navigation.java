package com.xhyn.stock;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import android.os.Handler;
import android.os.Looper;

/** One delegate per window. Unhandled input, including all game sticks, goes to the original callback. */
final class Navigation implements InvocationHandler {
    interface DirectionalEditor { boolean direction(int key); }
    private final Activity activity; private final Window window; private final Window.Callback original;
    private final boolean game; private final Runnable menu;
    private long next; private int last;
    private final Handler handler=new Handler(Looper.getMainLooper());private KeyEvent startDown;private boolean consumeStartUp;
    private final Runnable heldStart=this::openHeldStart;
    private void openHeldStart(){
        if(startDown==null)return;KeyEvent release=KeyEvent.changeAction(startDown,KeyEvent.ACTION_UP);startDown=null;consumeStartUp=true;
        original.dispatchKeyEvent(release);if(menu!=null)menu.run();
    }
    private Navigation(Activity a,Window w,boolean game,Runnable menu) {
        activity=a; window=w; original=w.getCallback(); this.game=game; this.menu=menu;
    }
    static void install(Activity a,Window w,boolean game,Runnable menu) {
        if(w==null || w.getCallback()==null)return;
        if(Proxy.isProxyClass(w.getCallback().getClass()) && Proxy.getInvocationHandler(w.getCallback()) instanceof Navigation)return;
        Navigation handler=new Navigation(a,w,game,menu);
        w.setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class[]{Window.Callback.class},handler));
    }
    @Override public Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
        if(method.getName().equals("onDetachedFromWindow")||(method.getName().equals("onWindowFocusChanged")&&args!=null&&Boolean.FALSE.equals(args[0]))) {
            handler.removeCallbacks(heldStart);if(startDown!=null&&game){original.dispatchKeyEvent(KeyEvent.changeAction(startDown,KeyEvent.ACTION_UP));startDown=null;}
        }
        if(method.getName().equals("dispatchKeyEvent") && args!=null && key((KeyEvent)args[0]))return true;
        if(!game && method.getName().equals("dispatchGenericMotionEvent") && args!=null && motion((MotionEvent)args[0]))return true;
        try { return method.invoke(original,args); } catch(InvocationTargetException e) { throw e.getCause(); }
    }
    boolean key(KeyEvent e) {
        int k=e.getKeyCode();
        if(game) {
            if(k==KeyEvent.KEYCODE_BUTTON_START) {
                if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){consumeStartUp=false;startDown=new KeyEvent(e);handler.removeCallbacks(heldStart);handler.postDelayed(heldStart,600);}
                if(e.getAction()==KeyEvent.ACTION_UP){handler.removeCallbacks(heldStart);startDown=null;if(consumeStartUp){consumeStartUp=false;return true;}}
                return false; // Tap Start retains the game's native pause/map/menu behavior.
            }
            if(k!=KeyEvent.KEYCODE_MENU)return false;
            if(e.getAction()==KeyEvent.ACTION_UP && !e.isCanceled() && menu!=null)menu.run();
            return true;
        }
        boolean controller=e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_DPAD)||
                e.isFromSource(InputDevice.SOURCE_JOYSTICK)||(k>=KeyEvent.KEYCODE_BUTTON_A && k<=KeyEvent.KEYCODE_BUTTON_MODE);
        if(!controller)return false;
        if(e.isCanceled())return true;
        if(k==KeyEvent.KEYCODE_BUTTON_B) {
            if(e.getAction()==KeyEvent.ACTION_UP) {
                if(window.getCallback()!=null && window!=activity.getWindow()) window.getDecorView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
                if(window!=activity.getWindow()) window.getDecorView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
                else activity.onBackPressed();
            } return true;
        }
        if(window.getDecorView().findViewWithTag("controller-test")!=null)return false;
        View focus=window.getCurrentFocus();
        if(k==KeyEvent.KEYCODE_BUTTON_L1 || k==KeyEvent.KEYCODE_BUTTON_R1) {
            List<View> tabs=new ArrayList<>(); tabs(window.getDecorView(),tabs); if(tabs.isEmpty())return false;
            if(e.getAction()==KeyEvent.ACTION_UP) { int selected=0; for(int i=0;i<tabs.size();i++)if(tabs.get(i).isSelected())selected=i;
                View t=tabs.get(Math.floorMod(selected+(k==KeyEvent.KEYCODE_BUTTON_R1?1:-1),tabs.size())); t.requestFocus(); t.performClick(); }
            return true;
        }
        if(k==KeyEvent.KEYCODE_BUTTON_A || k==KeyEvent.KEYCODE_DPAD_CENTER) {
            if(focus==null) { Ui.focusFirst(window.getDecorView()); return true; }
            if(focus instanceof EditText || focus instanceof SeekBar || focus instanceof Spinner)return false;
            if(e.getAction()==KeyEvent.ACTION_UP && focus.isEnabled())focus.performClick(); return true;
        }
        int d=k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:k==KeyEvent.KEYCODE_DPAD_RIGHT?View.FOCUS_RIGHT:
            k==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:k==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:0;
        if(d==0)return false;
        if(focus instanceof EditText || focus instanceof Spinner)return false;
        if(e.getAction()==KeyEvent.ACTION_DOWN)move(d); return true;
    }
    private static void tabs(View v,List<View> out) {
        if(!v.isShown())return; if("tab".equals(v.getTag())&&v.isEnabled())out.add(v);
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)tabs(((ViewGroup)v).getChildAt(i),out);
    }
    private void move(int direction) {
        View f=window.getCurrentFocus(); if(f==null){Ui.focusFirst(window.getDecorView());return;}
        int key=direction==View.FOCUS_LEFT?KeyEvent.KEYCODE_DPAD_LEFT:direction==View.FOCUS_RIGHT?KeyEvent.KEYCODE_DPAD_RIGHT:
            direction==View.FOCUS_UP?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN;
        if(f instanceof DirectionalEditor && ((DirectionalEditor)f).direction(key))return;
        if(f instanceof SeekBar && (direction==View.FOCUS_LEFT||direction==View.FOCUS_RIGHT)) {
            f.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key)); f.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,key)); return;
        }
        View target=f.focusSearch(direction);
        if(target!=null && target.isShown() && target.isEnabled()) {
            target.setFocusableInTouchMode(true);target.requestFocus(direction); target.post(()->target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),false));
        } else for(ViewParent p=f.getParent();p instanceof View;p=p.getParent())if(p instanceof ScrollView){((ScrollView)p).arrowScroll(direction);break;}
    }
    boolean motion(MotionEvent e) {
        if(!e.isFromSource(InputDevice.SOURCE_JOYSTICK)||e.getAction()!=MotionEvent.ACTION_MOVE)return false;
        if(window.getDecorView().findViewWithTag("controller-test")!=null)return false;
        View focus=window.getCurrentFocus(); if(focus instanceof EditText || focus instanceof Spinner)return false;
        float x=e.getAxisValue(MotionEvent.AXIS_HAT_X),y=e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if(Math.abs(x)<.5f && Math.abs(y)<.5f){x=e.getAxisValue(MotionEvent.AXIS_X);y=e.getAxisValue(MotionEvent.AXIS_Y);}
        if(Math.max(Math.abs(x),Math.abs(y))<.55f){last=0;next=0;return true;}
        int d=Math.abs(x)>Math.abs(y)?(x>0?View.FOCUS_RIGHT:View.FOCUS_LEFT):(y>0?View.FOCUS_DOWN:View.FOCUS_UP);
        long now=e.getEventTime(); if(last==d && now<next)return true; next=now+(last==d?120:320);last=d;move(d);return true;
    }
}

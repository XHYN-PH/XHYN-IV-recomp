package com.xhyn.stock;

import android.view.*;
import android.widget.*;
import java.util.Locale;

final class ControllerPage {
    private final PanelActivity a;private TextView state;private boolean testing;
    ControllerPage(PanelActivity a){this.a=a;}
    void show() {
        a.shell("Controllers");
        Ui.add(a.body,Ui.text(a,"Menus: D-pad or left stick to move · A to select · B to go back · LB/RB to change tabs.\nIn game: tap Start for the game’s own pause menu. Hold Start for 0.6 seconds, or press Menu, for session tools. Return to game leaves input with SDL.",14,Ui.TEXT));
        LinearLayout list=Ui.card(a);int found=0;
        for(int id:InputDevice.getDeviceIds()) {InputDevice d=InputDevice.getDevice(id);if(d==null)continue;
            if(!controllerSources(d.getSources()))continue;found++;
            Ui.add(list,Ui.text(a,d.getName()+" · "+(d.isVirtual()?"virtual":"connected"),14,Ui.TEXT));
        }
        if(found==0)Ui.add(list,Ui.text(a,"No controller detected. Pair Bluetooth or connect USB, then reopen this page.",14,Ui.MUTED));
        Ui.add(a.body,list);
        Ui.action(a,a.body,"Test sticks & buttons",()->{testing=!testing;state.setTag(testing?"controller-test":null);state.setText(testing?"Move a stick or press a button. B exits this page.":"Test stopped.");if(testing){state.setFocusable(true);state.setFocusableInTouchMode(true);state.requestFocus();}});
        state=Ui.text(a,"",14,Ui.ACCENT);Ui.add(a.body,state);
        Ui.action(a,a.body,"Stock input settings",()->PanelActivity.open(a,"settings"));
        Ui.add(a.body,Ui.text(a,"Gameplay mappings, dead zones and rumble follow v442/SDL. This page does not write unsupported engine settings.",12,Ui.MUTED));
    }
    boolean motion(MotionEvent e) {
        if(!testing||!e.isFromSource(InputDevice.SOURCE_JOYSTICK))return false;
        state.setText(String.format(Locale.ROOT,"Left %.2f, %.2f · Right %.2f, %.2f\nLT %.2f · RT %.2f · Hat %.0f, %.0f",
            e.getAxisValue(MotionEvent.AXIS_X),e.getAxisValue(MotionEvent.AXIS_Y),e.getAxisValue(MotionEvent.AXIS_Z),e.getAxisValue(MotionEvent.AXIS_RZ),
            e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_RTRIGGER),e.getAxisValue(MotionEvent.AXIS_HAT_X),e.getAxisValue(MotionEvent.AXIS_HAT_Y)));return true;
    }
    boolean key(KeyEvent e) { if(!testing)return false;state.setText(keyName(e.getKeyCode())+(e.getAction()==KeyEvent.ACTION_DOWN?" pressed":" released"));return e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)||e.isFromSource(InputDevice.SOURCE_DPAD); }
    private static String keyName(int key){switch(key){
        case KeyEvent.KEYCODE_BUTTON_A:return "A";case KeyEvent.KEYCODE_BUTTON_B:return "B";case KeyEvent.KEYCODE_BUTTON_X:return "X";case KeyEvent.KEYCODE_BUTTON_Y:return "Y";
        case KeyEvent.KEYCODE_BUTTON_L1:return "LB";case KeyEvent.KEYCODE_BUTTON_R1:return "RB";case KeyEvent.KEYCODE_BUTTON_L2:return "LT";case KeyEvent.KEYCODE_BUTTON_R2:return "RT";
        case KeyEvent.KEYCODE_BUTTON_THUMBL:return "Left stick";case KeyEvent.KEYCODE_BUTTON_THUMBR:return "Right stick";case KeyEvent.KEYCODE_BUTTON_START:return "Start";case KeyEvent.KEYCODE_BUTTON_SELECT:return "Select";
        case KeyEvent.KEYCODE_DPAD_UP:return "D-pad up";case KeyEvent.KEYCODE_DPAD_DOWN:return "D-pad down";case KeyEvent.KEYCODE_DPAD_LEFT:return "D-pad left";case KeyEvent.KEYCODE_DPAD_RIGHT:return "D-pad right";
        default:return KeyEvent.keyCodeToString(key).replace("KEYCODE_","");}}
    static boolean controllerSources(int sources){return(sources&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD||(sources&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;}
}

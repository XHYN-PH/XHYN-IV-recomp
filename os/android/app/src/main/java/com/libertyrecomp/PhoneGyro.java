package com.libertyrecomp;

import android.app.Activity;
import android.hardware.*;
import android.os.*;

/** Optional phone gyro camera input; no sensor listeners survive backgrounding. */
final class PhoneGyro implements SensorEventListener {
    interface Output {void sample(float x,float y);void reset();}
    private final Activity activity;private final SensorManager manager;private final Sensor sensor;private final Output output;
    private HandlerThread thread;private volatile boolean active;private volatile int rotation;private volatile float gain=1;private volatile boolean invertX,invertY;private long previous;
    private float smoothX,smoothY;private volatile float smoothing=.03f;
    PhoneGyro(Activity a,Output output){activity=a;this.output=output;manager=(SensorManager)a.getSystemService(Activity.SENSOR_SERVICE);sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);}
    boolean available(){return sensor!=null;}
    synchronized void configure(float gain,float smoothing,boolean invertX,boolean invertY){this.gain=Math.max(.1f,Math.min(5,gain));this.smoothing=Math.max(0,Math.min(.1f,smoothing));this.invertX=invertX;this.invertY=invertY;int next=activity.getWindowManager().getDefaultDisplay().getRotation();if(next!=rotation){previous=0;smoothX=smoothY=0;output.reset();}rotation=next;}
    synchronized void active(boolean value){
        value=value&&available();if(active==value)return;active=value;previous=0;smoothX=smoothY=0;output.reset();
        if(value){thread=new HandlerThread("PhoneGyroscope",android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE);thread.start();if(!manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME,new Handler(thread.getLooper()))){active=false;thread.quitSafely();thread=null;DebugLog.note("phone-gyro: sensor registration unavailable");}}
        else{if(manager!=null)manager.unregisterListener(this);if(thread!=null){thread.quitSafely();thread=null;}}
    }
    public synchronized void onSensorChanged(SensorEvent event){
        if(!active||event.values.length<3)return;double dt=GyroMath.seconds(previous,event.timestamp);previous=event.timestamp;if(dt==0){smoothX=smoothY=0;output.reset();return;}
        float[] rates=GyroMath.screenRates(event.values[0],event.values[1],rotation);float x=rates[0],y=rates[1];if(!Float.isFinite(x)||!Float.isFinite(y))return;
        float blend=(float)(dt/(smoothing+dt));smoothX+=(x-smoothX)*blend;smoothY+=(y-smoothY)*blend;
        // Integrated radians become relative camera movement, independent of event rate.
        output.sample((float)(-smoothX*dt*320*gain*(invertX?-1:1)),(float)(-smoothY*dt*320*gain*(invertY?-1:1)));
    }
    public void onAccuracyChanged(Sensor s,int accuracy){}
    void close(){active(false);}
}

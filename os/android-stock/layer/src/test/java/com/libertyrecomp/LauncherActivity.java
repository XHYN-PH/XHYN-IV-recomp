package com.libertyrecomp;
public class LauncherActivity extends android.app.Activity {
    public int launches;
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView t=new android.widget.TextView(this);t.setText("Stock installer");setContentView(t);}
    public void o(){launches++;}
}

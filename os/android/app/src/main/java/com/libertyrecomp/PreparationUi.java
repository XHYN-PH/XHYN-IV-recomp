package com.libertyrecomp;
import android.app.*;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.util.Locale;
/** Foreground-only view of actual renderer preparation, with no minimum display time. */
final class PreparationUi {
 private final Activity a;private final MobileSettingsUi.Runtime runtime;private final Handler ui=new Handler(Looper.getMainLooper());
 private AlertDialog dialog;private TextView text;private boolean closed;private int probes;
 PreparationUi(Activity a,MobileSettingsUi.Runtime runtime){this.a=a;this.runtime=runtime;}
 void start(){ui.post(poll);}
 private void completed(JSONObject status){
  String outcome=status.optString("outcome",status.optBoolean("skipped")?"skipped":status.optBoolean("cancelled")?"cancelled":"ready");
  String label=outcome.equals("budget_expired")?"Time budget reached":outcome.equals("cancelled")?"Startup wait skipped":outcome.equals("partial")?"Work remains":outcome.equals("skipped")?status.optString("reason","skipped"):"Ready";
  int remaining=status.optInt("pending")+status.optInt("queued")+status.optInt("compiling")+status.optInt("unpublished");
  String summary=String.format(Locale.ROOT,"Graphics preparation: %s · %.2f s · %d pipelines ready · %d jobs and %d shaders remaining%s",label,status.optLong("elapsed_ms")/1000.0,status.optInt("ready"),remaining,status.optInt("remaining_shaders"),status.optInt("failed")>0?" · "+status.optInt("failed")+" failed attempts":"");
  DebugLog.note(summary);close();if(!a.isFinishing()&&!a.isDestroyed())Toast.makeText(a,summary,Toast.LENGTH_LONG).show();
 }
 private final Runnable poll=new Runnable(){public void run(){
  if(closed||a.isFinishing()||a.isDestroyed()||++probes>180){close();return;}
  try{String value=runtime.get("gta4_native_preparation_status");if(value!=null&&!value.isEmpty()){
   JSONObject status=new JSONObject(value);if(!status.optBoolean("active")){completed(status);return;}
   if(!a.hasWindowFocus()&&dialog==null){cancel();return;}
   if(dialog==null){LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);text=ModernUi.text(a,"",14,ModernUi.TEXT);body.addView(text);body.addView(ModernUi.text(a,"Preparing learned graphics for this driver and settings. New scenes can still compile while playing.",12,ModernUi.MUTED));dialog=ModernUi.dialog(a,"Preparing graphics",body,"Skip",null,null);dialog.setOnDismissListener(d->{if(!closed)runtime.apply(new String[]{"gta4_native_preparation_cancel"},new String[]{"true"});});dialog.show();}
   text.setText(status.optInt("shaders")+" / "+status.optInt("known_shaders")+" known shaders visited · "+status.optInt("ready")+" pipelines ready\n"+status.optInt("queued")+" queued · "+status.optInt("compiling")+" compiling · "+status.optInt("unpublished")+" awaiting publication · "+status.optInt("pending")+" learned recipes pending");
  }}catch(Exception ignored){}if(!closed)ui.postDelayed(this,200);
 }};
 void cancel(){if(!closed)runtime.apply(new String[]{"gta4_native_preparation_cancel"},new String[]{"true"});close();}
 void close(){closed=true;ui.removeCallbacksAndMessages(null);if(dialog!=null){dialog.setOnDismissListener(null);dialog.dismiss();dialog=null;}}
}

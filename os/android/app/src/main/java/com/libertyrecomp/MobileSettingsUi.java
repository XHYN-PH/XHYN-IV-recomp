package com.libertyrecomp;

import android.app.*;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;

/** Shared launcher/game settings with the same saved names and lifecycle rules. */
final class MobileSettingsUi {
    interface Runtime {String get(String key);boolean apply(String[] keys,String[] values);}
    private static final class Choice {
        final String key,label,initial;final String[] values;final boolean restart;final double min,max,step;
        Choice(String k,String l,String initial,boolean restart,String... values){this.key=k;this.label=l;this.initial=initial;this.restart=restart;this.values=values;min=max=step=0;}
        Choice(String k,String l,String initial,boolean restart,double min,double max,double step){this.key=k;this.label=l;this.initial=initial;this.restart=restart;this.min=min;this.max=max;this.step=step;values=null;}
    }
    private static final Choice[] GRAPHICS={
        new Choice("resolution","Output resolution","720p",true,"854x480","540p","720p","900p","1080p"),
        new Choice("gta4_native_upscaler","Upscaler","native",true,"native","fsr1"),
        new Choice("gta4_fsr1_quality","FSR quality","quality",true,"ultra_quality","quality","balanced","performance"),
        new Choice("gta4_fsr1_sharpness_reduction","FSR softness","0.2",true,0,2,.05),
        new Choice("gta4_native_anti_aliasing","Anti-aliasing","fxaa",false,"off","fxaa","smaa"),
        new Choice("gta4_native_smaa_quality","SMAA quality","high",false,"low","medium","high","ultra"),
        new Choice("gta4_present_mode","VSync","vsync",false,"vsync","immediate"),
        new Choice("gta4_frame_limit","Frame limit","30",false,"0","20","24","25","30","40","45","60","90","120"),
        new Choice("gta4_dynamic_draw_distance","Adaptive draw distance","true",false,"true","false"),
        new Choice("gta4_draw_distance_scale","Draw distance","1.0",false,.5,1.5,.05),
        new Choice("gta4_shadow_map_base_size","Shadow detail","256",true,"256","512","768","1024"),
        new Choice("gta4_shadow_distance_scale","Shadow distance","1.0",true,1,4,.25),
        new Choice("gta4_native_shadow_interval","Sun shadows","1",false,"0","1"),
        new Choice("gta4_reflection_resolution","Reflection resolution","original",true,"original","1080p","full"),
        new Choice("gta4_water_reflection_resolution","Water reflection resolution","inherit",true,"inherit","original","1080p","full"),
        new Choice("gta4_mirror_reflection_resolution","Mirror reflection resolution","inherit",true,"inherit","original","1080p","full"),
        new Choice("gta4_reflection_aa","Reflection anti-aliasing","original",true,"original","off","2x","4x"),
        new Choice("gta4_reflection_capture_distance","Reflection distance","near",false,"near","original","extended","far"),
        new Choice("gta4_native_water_reflection_interval","Water updates","1",false,"0","1","2","3"),
        new Choice("gta4_native_environment_reflection_interval","Other reflection updates","1",false,"0","1","2","3"),
        new Choice("gta4_motion_blur","Motion blur","false",false,"false","true"),
        new Choice("gta4_native_max_queued_frames","Queued frames","3",true,"1","2","3"),
        new Choice("gta4_native_pipeline_prewarm","Pipeline preparation","true",true,"true","false"),
        new Choice("gta4_native_converted_texture_disk_cache","Converted texture disk cache","false",true,"false","true")
    };
    private static final Choice[] TOUCH={
        new Choice("gta4_phone_rumble_strength","Phone vibration","0.5",false,0,1,.05),
        new Choice("gta4_touch_camera","Camera movement","swipe",false,"swipe","stick"),
        new Choice("gta4_touch_move_mode","Movement joystick","fixed",false,"fixed","floating","invisible"),
        new Choice("gta4_touch_gamepad_scale","Button size","1.0",false,.5,2,.01),
        new Choice("gta4_touch_gamepad_opacity","Button opacity","1.0",false,0,1.6,.01),
        new Choice("gta4_touch_camera_scale","Camera control size","1.0",false,.25,3,.01),
        new Choice("gta4_touch_camera_opacity","Camera control opacity","1.0",false,0,1,.01),
        new Choice("gta4_touch_swipe_sensitivity","Camera sensitivity","1.0",false,.1,8,.05),
        new Choice("gta4_touch_button_hints","Controller button labels","true",false,"true","false"),
        new Choice("gta4_touch_labels","Action names","false",false,"false","true"),
        new Choice("gta4_touch_move_smoothing","Movement smoothing","0.035",false,0,.1,.005),
        new Choice("gta4_touch_radar_top","Minimap position","true",false,"true","false"),
        new Choice("gta4_touch_radar_margin","Minimap vertical offset","0",false,-.2,.15,.005)
    };
    static final Set<String> KEYS=new LinkedHashSet<>();
    static {for(Choice c:GRAPHICS)KEYS.add(c.key);for(Choice c:TOUCH)KEYS.add(c.key);}
    static AlertDialog graphics(Activity a,File data,Runtime runtime){return show(a,data,runtime,false,null);}
    static AlertDialog touch(Activity a,File data,Runtime runtime){return touch(a,data,runtime,xbox->a.startActivity(new Intent(a,TouchEditorActivity.class).putExtra("xbox",xbox)));}
    static AlertDialog touch(Activity a,File data,Runtime runtime,java.util.function.Consumer<Boolean> edit){return show(a,data,runtime,true,edit);}
    private static AlertDialog show(Activity a,File data,Runtime runtime,boolean touch,java.util.function.Consumer<Boolean> edit){
        final Map<String,String> saved;
        try{if(runtime==null)MobileConfig.migrateRemovedUpscaler(MobileConfig.file(data));saved=MobileConfig.read(MobileConfig.file(data));}
        catch(IOException e){Toast.makeText(a,"Cannot read settings: "+e.getMessage(),Toast.LENGTH_LONG).show();return null;}
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
        String help=touch?"Drag sliders for size, opacity and sensitivity. Swipe looks with the right finger; movement uses the left. Camera size and opacity affect the visible stick in Stick mode.":
            "Restart options apply next time you play. FSR selects a compatible scale when a preset would be below the game's rendering minimum. FSR works best with anti-aliasing. Less frequent reflection updates can cause visible stepping.";
        panel.addView(ModernUi.text(a,help,13,ModernUi.MUTED));
        if(!touch&&runtime!=null){String effective=runtime.get("gta4_effective_upscaling");if(effective!=null&&!effective.isEmpty())panel.addView(ModernUi.text(a,effective,14,ModernUi.ACCENT));}
        if(!touch){
            String preparation=runtime==null?"Graphics caches are reused automatically for the selected driver. New scenes can still prepare effects while playing.":runtime.get("gta4_native_pipeline_status");
            if(preparation!=null&&!preparation.isEmpty())panel.addView(ModernUi.text(a,preparation,13,ModernUi.MUTED));
        }
        Map<String,String> before=new LinkedHashMap<>();Map<Choice,Supplier<String>> controls=new LinkedHashMap<>();
        final AlertDialog[] current={null};
        java.util.function.BooleanSupplier commit=()->{
            Map<String,String> changes=new LinkedHashMap<>();for(Map.Entry<Choice,Supplier<String>> entry:controls.entrySet()){
                String value=entry.getValue().get();if(!value.equals(before.get(entry.getKey().key)))changes.put(entry.getKey().key,value);
            }
            try{if(!changes.isEmpty()){
                if(runtime==null)MobileConfig.update(MobileConfig.file(data),changes);
                else if(!runtime.apply(changes.keySet().toArray(new String[0]),changes.values().toArray(new String[0])))throw new IOException("Some settings could not be applied.");
                before.putAll(changes);
            }return true;}catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();return false;}
        };
        if(touch&&edit!=null){
            ModernUi.action(a,panel,"Edit mobile controls",()->{if(commit.getAsBoolean()){edit.accept(false);current[0].dismiss();}});
            ModernUi.action(a,panel,"Edit Xbox controls",()->{if(commit.getAsBoolean()){edit.accept(true);current[0].dismiss();}});
        }
        // Pair options where both columns remain readable, including large text.
        float widthDp=a.getResources().getDisplayMetrics().widthPixels/a.getResources().getDisplayMetrics().density;
        float contentDp=Math.min(760,widthDp*.94f)-44;
        int columns=contentDp/a.getResources().getConfiguration().fontScale>=480?2:1;
        LinearLayout row=null;int column=0;
        for(Choice c:touch?TOUCH:GRAPHICS){
            if(column==0){row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setBaselineAligned(false);panel.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            LinearLayout field=new LinearLayout(a);field.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams fieldParams=new LinearLayout.LayoutParams(0,-2,1);if(column>0)fieldParams.leftMargin=ModernUi.dp(a,22);row.addView(field,fieldParams);
            String value=runtime==null?saved.getOrDefault(c.key,c.initial):runtime.get(c.key);
            if(value==null||value.isEmpty())value=saved.getOrDefault(c.key,c.initial);
            before.put(c.key,value);boolean restart=c.restart||(c.key.equals("gta4_native_anti_aliasing")&&!Arrays.asList("off","fxaa","smaa").contains(value));
            TextView label=ModernUi.text(a,c.label+(restart?" · next launch":""),14,ModernUi.TEXT);label.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));label.setPadding(0,ModernUi.dp(a,15),0,0);field.addView(label);
            field.addView(ModernUi.text(a,guide(c.key),12,ModernUi.MUTED));
            if(c.values==null){
                double parsed;try{parsed=Double.parseDouble(value);}catch(Exception e){parsed=Double.parseDouble(c.initial);}
                final String original=value;final boolean[] changed={false};
                SeekBar slider=new SeekBar(a);slider.setMax((int)Math.round((c.max-c.min)/c.step));ModernUi.tint(slider);
                slider.setProgress((int)Math.round((Math.max(c.min,Math.min(c.max,parsed))-c.min)/c.step));slider.setEnabled(runtime==null||!restart);
                TextView amount=ModernUi.text(a,numericLabel(c,parsed),12,ModernUi.ACCENT);field.addView(amount);field.addView(slider);
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar bar,int progress,boolean user){changed[0]|=user;amount.setText(numericLabel(c,c.min+progress*c.step));}
                    public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
                });
                controls.put(c,()->changed[0]?number(c.min+slider.getProgress()*c.step):original);
            }else{
                ArrayList<String> values=new ArrayList<>(Arrays.asList(c.values));if(!values.contains(value))values.add(value);
                String[] labels=values.stream().map(v->pretty(c.key,v)).toArray(String[]::new);
                final int[] selected={values.indexOf(value)};Button picker=ModernUi.button(a,labels[selected[0]]+" ▾",false);
                picker.setEnabled(runtime==null||!restart);field.addView(picker,new LinearLayout.LayoutParams(-1,-2));
                picker.setOnClickListener(v->ModernUi.choose(a,c.label,labels,selected[0],index->{selected[0]=index;picker.setText(labels[index]+" ▾");}));
                controls.put(c,()->values.get(selected[0]));
            }
            column=(column+1)%columns;
        }
        if(columns==2&&column==1){LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,1,1);space.leftMargin=ModernUi.dp(a,22);row.addView(new View(a),space);}
        Runnable save=()->{if(commit.getAsBoolean())current[0].dismiss();};
        AlertDialog dialog=ModernUi.dialog(a,touch?"Controls":"Graphics",panel,"Save changes",save);current[0]=dialog;
        dialog.show();return dialog;
    }
    static String guide(String key){
        switch(key){
            case "gta4_phone_rumble_strength":return "Uses the phone motor for the game's controller rumble. Zero turns it off. Strength control depends on the phone; a connected controller keeps its own vibration.";
            case "resolution":return "Sets output sharpness. Lower values reduce GPU work and memory use; higher values look clearer.";
            case "gta4_native_upscaler":return "Native renders at the output size. FSR 1 renders fewer pixels and upscales them; it mainly helps when the GPU is the limit.";
            case "gta4_fsr1_quality":return "Used only with FSR. Quality keeps more detail; Performance uses fewer pixels. Very small render sizes are adjusted to the game's minimum.";
            case "gta4_fsr1_sharpness_reduction":return "Used only with FSR. Lower values sharpen edges more; higher values give a softer image.";
            case "gta4_native_anti_aliasing":return "Smooths jagged edges. Off costs least; FXAA is light and can soften detail; SMAA is sharper but costs more.";
            case "gta4_native_smaa_quality":return "Used only with SMAA. Higher quality searches more edges and takes more GPU time.";
            case "gta4_present_mode":return "VSync avoids tearing. Turning it off can reduce waiting but may produce tearing or uneven delivery on some devices.";
            case "gta4_frame_limit":return "Maximum game frame rate. A lower sustainable cap can feel steadier and use less power. Higher caps need more CPU and GPU headroom.";
            case "gta4_dynamic_draw_distance":return "Temporarily reduces distant scenery detail when frame delivery falls behind the selected cap, then restores it gradually.";
            case "gta4_draw_distance_scale":return "Controls how far scenery is drawn. Lower values reduce CPU and GPU work but make distant objects appear later.";
            case "gta4_shadow_map_base_size":return "Resolution of each shadow tile. The full atlas contains several tiles. Higher values sharpen shadows and increase GPU memory and work.";
            case "gta4_shadow_distance_scale":return "Controls how far shadows extend. Longer distances add work and spread the available shadow detail across a larger area.";
            case "gta4_native_shadow_interval":return "Turns sun shadows on or off. Off reduces work but removes directional shadows.";
            case "gta4_reflection_resolution":return "Base resolution of reflected scenes. Higher settings improve reflection detail and increase rendering cost.";
            case "gta4_water_reflection_resolution":return "Resolution of water reflections. Inherit uses the base reflection setting.";
            case "gta4_mirror_reflection_resolution":return "Resolution of mirror reflections. Inherit uses the base reflection setting.";
            case "gta4_reflection_aa":return "Smooths reflection edges. Off saves work; multisampling uses additional GPU memory and bandwidth.";
            case "gta4_reflection_capture_distance":return "How far reflected scenes reach. Near reduces scene preparation and drawing; longer distances include more objects.";
            case "gta4_native_water_reflection_interval":return "How often water reflections refresh. Every two or three frames saves work but moving reflections can step. Off removes the capture.";
            case "gta4_native_environment_reflection_interval":return "How often environment reflections refresh. Less frequent updates save work but can show delayed movement.";
            case "gta4_motion_blur":return "Adds blur during movement. Turning it off gives a clearer image and may reduce post-processing work.";
            case "gta4_native_max_queued_frames":return "Maximum prepared frames waiting for rendering. More can smooth workload bursts; fewer can reduce input delay. Restart to change.";
            case "gta4_native_pipeline_prewarm":return "Prepares graphics pipelines ahead of their draws. Learned pipelines are prepared again on later launches; a new effect may still need first-use compilation.";
            case "gta4_native_converted_texture_disk_cache":return "Keeps converted textures on storage to reuse later. Uses storage space and can reduce repeated conversion work. Benefits depend on the device.";
            case "gta4_touch_camera":return "Swipe follows your right finger. Stick keeps turning while you hold the camera joystick away from its center.";
            case "gta4_touch_move_mode":return "Fixed stays in place. Floating starts under your finger. Invisible works like Floating with its graphic hidden.";
            case "gta4_touch_gamepad_scale":return "Changes the size of all touch buttons. Individual buttons can also be resized in Edit mobile controls.";
            case "gta4_touch_gamepad_opacity":return "Changes button visibility without changing their touch areas. Zero makes them invisible; Android Back still opens the game menu.";
            case "gta4_touch_camera_scale":return "Changes the visible camera joystick in Stick mode. Swipe mode uses the right-side gesture area.";
            case "gta4_touch_camera_opacity":return "Visibility of the camera joystick in Stick mode. Does not change camera sensitivity.";
            case "gta4_touch_swipe_sensitivity":return "Higher values turn the camera farther for the same finger movement. Large values make small aiming adjustments harder.";
            case "gta4_touch_button_hints":return "Shows small A, B, X, Y, LB, RB, LT and RT labels beside mobile controls, matching the game's prompts.";
            case "gta4_touch_labels":return "Also shows action names such as Run and Cover. Controller button labels remain available separately.";
            case "gta4_touch_move_smoothing":return "Smooths movement-stick changes. Higher values feel softer but add response delay; zero responds immediately.";
            case "gta4_touch_radar_top":return "Moves the complete minimap, route and blips together to the top left, including when touch controls are hidden.";
            case "gta4_touch_radar_margin":return "Negative moves the minimap up; positive moves it down. Nearby help text follows it and stays readable on screen. Too far up can crop the map.";
            default:return "Changes this option for the next launch when marked above.";
        }
    }
    private static String number(double n){return java.math.BigDecimal.valueOf(n).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static String numericLabel(Choice c,double n){if(c.key.contains("opacity")||c.key.equals("gta4_phone_rumble_strength"))return Math.round(n*100)+"%";if(c.key.contains("smoothing"))return Math.round(n*1000)+" ms";if(c.key.contains("margin"))return number(n*100)+"% of safe height";return number(n)+"×";}
    private static String pretty(String key,String value){
        if(key.equals("gta4_touch_radar_top"))return value.equals("true")?"Top left":"Original position";
        if(value.equals("true"))return "On";if(value.equals("false"))return "Off";if(value.equals("fsr1"))return "FSR 1";
        if(value.equals("fxaa")||value.equals("smaa"))return value.toUpperCase(Locale.ROOT);
        if(key.equals("gta4_frame_limit"))return value.equals("0")?"Unlimited":value+" FPS";
        if(key.equals("gta4_native_shadow_interval"))return value.equals("0")?"Off":value.equals("1")?"On":"Every "+value+" frames";
        if(key.endsWith("_interval"))return value.equals("0")?"Off":value.equals("1")?"Every frame":"Every "+value+" frames";
        if(key.equals("gta4_present_mode"))return value.equals("vsync")?"On":"Off";
        if(value.equals("854x480"))return "480p";
        String text=value.replace('_',' ');return Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    private MobileSettingsUi(){}
}

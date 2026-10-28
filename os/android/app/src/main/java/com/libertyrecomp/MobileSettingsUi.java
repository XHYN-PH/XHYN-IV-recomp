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
        new Choice("gta4_native_smaa_quality","SMAA quality","low",false,"low","medium","high","ultra"),
        new Choice("gta4_present_mode","VSync","immediate",false,"vsync","immediate"),
        new Choice("gta4_frame_limit","Frame limit","30",false,"0","20","24","25","30","40","45","60","90","120"),
        new Choice("gta4_dynamic_draw_distance","Adaptive draw distance","true",false,"true","false"),
        new Choice("gta4_draw_distance_scale","Draw distance","0.85",false,.5,1.5,.05),
        new Choice("gta4_shadow_map_base_size","Shadow detail","256",true,"256","512","768","1024"),
        new Choice("gta4_shadow_distance_scale","Shadow distance","1.0",true,1,4,.25),
        new Choice("gta4_native_shadow_interval","Sun shadows","1",false,"0","1"),
        new Choice("gta4_reflection_resolution","Reflection resolution","original",true,"quarter","half","original","1080p","full"),
        new Choice("gta4_water_reflection_resolution","Water reflection resolution","inherit",true,"inherit","quarter","half","original","1080p","full"),
        new Choice("gta4_mirror_reflection_resolution","Mirror reflection resolution","inherit",true,"inherit","quarter","half","original","1080p","full"),
        new Choice("gta4_reflection_aa","Reflection anti-aliasing","off",true,"original","off","2x","4x"),
        new Choice("gta4_reflection_capture_distance","Reflection distance","near",false,"near","original","extended","far"),
        new Choice("gta4_native_water_reflection_interval","Water updates","0",false,"0","1","2","3"),
        new Choice("gta4_native_environment_reflection_interval","Other reflection updates","0",false,"0","1","2","3"),
        new Choice("gta4_disable_tlad_film_grain","Film grain (TLAD)","false",false,"false","true"),
        new Choice("gta4_native_output_dither","Output dithering","true",false,"true","false"),
        new Choice("gta4_motion_blur","Motion blur","false",false,"false","true"),
        new Choice("gta4_native_max_queued_frames","Queued frames","3",true,"1","2","3"),
        new Choice("gta4_native_pipeline_workers","Graphics shader workers","1",true,"1","2","3","4","5"),
        new Choice("gta4_native_startup_prepare_seconds","Preparation time limit","3",true,"0","3","5","10","20"),
        new Choice("gta4_native_texture_reuse_extended","Longer texture reuse","false",false,"true","false"),
        new Choice("gta4_native_depth_clip_fallback","Allow ordinary depth clipping (experimental)","false",false,"true","false"),
        new Choice("gta4_native_pipeline_prewarm","Pipeline preparation","true",true,"true","false"),
        new Choice("gta4_native_pipeline_lookahead","Pipeline lookahead","true",false,"true","false"),
        new Choice("gta4_native_converted_texture_disk_cache","Converted texture disk cache","true",true,"false","true")
    };
    private static final class State {String category="Video";final Map<String,Integer> scroll=new HashMap<>();}
    private static final Map<Activity,State> states=new WeakHashMap<>();
    private static final Choice[] TOUCH={
        new Choice("gamepad_left_deadzone","Left stick deadzone","0",false,0,.4,.01),
        new Choice("gamepad_right_deadzone","Right stick deadzone","0",false,0,.4,.01),
        new Choice("gamepad_left_sensitivity","Left stick sensitivity","1",false,.25,2,.05),
        new Choice("gamepad_right_sensitivity","Right stick sensitivity","1",false,.25,2,.05),
        new Choice("gamepad_response_curve","Stick response curve","1",false,.5,2,.05),
        new Choice("gamepad_trigger_threshold","Trigger threshold","0",false,0,.4,.01),
        new Choice("gamepad_invert_left_x","Invert left stick horizontally","false",false,"false","true"),
        new Choice("gamepad_invert_left_y","Invert left stick vertically","false",false,"false","true"),
        new Choice("gamepad_invert_right_x","Invert right stick horizontally","false",false,"false","true"),
        new Choice("gamepad_invert_right_y","Invert right stick vertically","false",false,"false","true"),
        new Choice("gamepad_rumble_strength","Controller vibration","1",false,0,1,.05),
        new Choice("gta4_touch_context_delay_ms","Mobile layout switch delay","0",false,0,1000,10),
        new Choice("mnk_mode","Keyboard and mouse","true",false,"true","false"),
        new Choice("mnk_sensitivity","Mouse sensitivity","1.0",false,.01,10,.05),
        new Choice("mnk_invert_y","Invert mouse vertically","false",false,"false","true"),
        new Choice("gta4_phone_gyro_mode","Gyroscope camera","off",false,"off","aim","always"),
        new Choice("gta4_phone_gyro_sensitivity","Gyro sensitivity","1.0",false,.1,5,.1),
        new Choice("gta4_phone_gyro_smoothing","Gyro smoothing","0.03",false,0,.1,.005),
        new Choice("gta4_phone_gyro_invert_x","Invert gyro horizontally","false",false,"false","true"),
        new Choice("gta4_phone_gyro_invert_y","Invert gyro vertically","false",false,"false","true"),
        new Choice("gta4_touch_aim_hold_lock","Hold-to-lock aim","true",false,"true","false"),
        new Choice("gta4_touch_aim_hold_seconds","Aim lock hold duration","0.4",false,.2,1.5,.05),
        new Choice("gta4_touch_icon_outline","Button circle outline","false",false,"false","true"),
        new Choice("gta4_touch_animations","Button animations","true",false,"true","false"),
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
        new Choice("gta4_touch_radar_margin","Minimap vertical offset","-0.06",false,-.2,.15,.005)
    };
    private static final Choice[] PERFORMANCE={
        new Choice("gta4_performance_overlay","Performance overlay","off",false,"off","fps","detailed"),
        new Choice("gta4_overlay_position","Overlay position","top_right",false,"top_left","top_center","top_right","bottom_left","bottom_center","bottom_right","custom"),
        new Choice("gta4_overlay_edge_gap","Overlay edge gap","2",false,0,40,1),
        new Choice("gta4_overlay_text_size","Overlay text size","12",false,9,24,1),
        new Choice("gta4_overlay_opacity","Overlay opacity","1.0",false,.2,1,.05),
        new Choice("gta4_overlay_x","Custom horizontal position","0.5",false,0,1,.01),
        new Choice("gta4_overlay_y","Custom vertical position","0.1",false,0,1,.01),
        new Choice("gta4_overlay_ram","Overlay memory metric","true",false,"true","false"),
        new Choice("gta4_overlay_cpu","Overlay CPU usage","false",false,"true","false"),
        new Choice("gta4_overlay_gpu","Overlay GPU usage","false",false,"true","false"),
        new Choice("gta4_overlay_resolution","Overlay actual resolution","false",false,"true","false"),
        new Choice("gta4_overlay_thermal","Overlay thermal metric","true",false,"true","false"),
        new Choice("gta4_overlay_compact","Compact overlay","false",false,"true","false"),
        new Choice("ui_battery_refresh","Lower display refresh","false",false,"false","true"),
        new Choice("ui_reduce_motion","Reduce menu motion","false",false,"false","true"),
        new Choice("ui_sustained","Sustained performance","false",true,"false","true"),
        new Choice("gta4_safe_mode","Compatibility mode","false",true,"false","true"),
        new Choice("android_perf_hint","Android performance hints","true",true,"true","false"),
        new Choice("android_thermal_guard","Thermal Guard","false",false,"true","false"),
        new Choice("android_gpu_turbo","GPU Turbo","false",true,"true","false"),
        new Choice("audio_adaptive_buffer","Audio dropout protection","true",false,"true","false"),
        new Choice("audio_mute","Mute audio","false",false,"false","true"),
        new Choice("audio_low_latency","Low-latency audio","false",true,"false","true")
    };
    static void select(Activity a,String category){states.computeIfAbsent(a,key->new State()).category=category;}
    static final Set<String> KEYS=new LinkedHashSet<>();
    static {for(Choice c:GRAPHICS)KEYS.add(c.key);for(Choice c:TOUCH)KEYS.add(c.key);for(Choice c:PERFORMANCE)KEYS.add(c.key);}
    static AlertDialog graphics(Activity a,File data,Runtime runtime){return show(a,data,runtime,false,null);}
    static AlertDialog graphics(Activity a,File data,Runtime runtime,Runnable logs){return show(a,data,runtime,false,null,logs);}
    static AlertDialog touch(Activity a,File data,Runtime runtime){return touch(a,data,runtime,xbox->a.startActivity(new Intent(a,TouchEditorActivity.class).putExtra("xbox",xbox)));}
    static AlertDialog touch(Activity a,File data,Runtime runtime,java.util.function.Consumer<Boolean> edit){return show(a,data,runtime,true,edit);}
    private static boolean sustainedSupported(Activity a){android.os.PowerManager power=(android.os.PowerManager)a.getSystemService(Activity.POWER_SERVICE);return power!=null&&power.isSustainedPerformanceModeSupported();}
    private static String uiKey(String key){return key.equals("ui_reduce_motion")?"reduce_motion":key.equals("ui_battery_refresh")?"battery_refresh":"sustained";}
    private static boolean restart(Choice c,Map<String,String> before){return c.restart||(c.key.equals("gta4_native_anti_aliasing")&&!Arrays.asList("off","fxaa","smaa").contains(before.get(c.key)));}
    private static String category(Choice c){
        if(c.key.startsWith("gamepad_"))return "Controller";
        if(c.key.startsWith("audio_")||c.key.equals("gta4_phone_rumble_strength"))return "Audio";
        if(c.key.startsWith("mnk_")||c.key.startsWith("gta4_touch_")||c.key.startsWith("gta4_phone_gyro"))return "Touch controls";
        if(c.key.equals("gta4_performance_overlay")||c.key.startsWith("gta4_overlay_"))return "Overlay";
        if(c.key.startsWith("ui_")||c.key.equals("gta4_safe_mode")||c.key.startsWith("android_")||(c.key.contains("pipeline_")||c.key.equals("gta4_native_pipeline_workers"))||c.key.contains("texture_disk")||c.key.contains("texture_reuse")||c.key.contains("startup_prepare")||c.key.contains("queued_frames")||c.key.contains("draw_distance")||c.key.equals("gta4_frame_limit"))return "Performance";
        if(c.key.contains("depth_clip")||c.key.contains("shadow")||c.key.contains("reflection"))return "Graphics";
        return "Video";
    }
    private static AlertDialog show(Activity a,File data,Runtime runtime,boolean touch,java.util.function.Consumer<Boolean> edit){
        return show(a,data,runtime,touch,edit,null);
    }
    private static AlertDialog show(Activity a,File data,Runtime runtime,boolean touch,java.util.function.Consumer<Boolean> edit,Runnable logs){
        final Map<String,String> saved;
        try{if(runtime==null)MobileConfig.migrateRemovedUpscaler(MobileConfig.file(data));saved=MobileConfig.read(MobileConfig.file(data));saved.putAll(PendingSettings.read(data));}
        catch(IOException e){Toast.makeText(a,"Cannot read settings: "+e.getMessage(),Toast.LENGTH_LONG).show();return null;}
        ArrayList<Choice> choices=new ArrayList<>();Collections.addAll(choices,GRAPHICS);Collections.addAll(choices,TOUCH);Collections.addAll(choices,PERFORMANCE);
        Map<String,String> before=new LinkedHashMap<>(),draft=new LinkedHashMap<>();
        for(Choice c:choices){String value=c.key.startsWith("ui_")?Boolean.toString(UiPreferences.get(a,uiKey(c.key))):runtime==null?saved.get(c.key):runtime.get(c.key);if(runtime==null&&value==null&&(c.key.equals("android_gpu_turbo")||c.key.equals("android_thermal_guard")))value=Boolean.toString(a.getSharedPreferences("performance_test",0).getBoolean(c.key.equals("android_gpu_turbo")?"gpu_turbo":"thermal_guard",false));if(restart(c,saved)&&saved.containsKey(c.key))value=saved.get(c.key);if(value==null||value.isEmpty())value=saved.getOrDefault(c.key,c.initial);before.put(c.key,value);draft.put(c.key,value);}
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        State state=states.computeIfAbsent(a,key->new State());
        String[] categories={"Video","Graphics","Performance","Touch controls","Controller","Overlay","Audio"};String[] selected={touch?"Touch controls":state.category};
        HorizontalScrollView tabScroll=new HorizontalScrollView(a);tabScroll.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(a);tabScroll.addView(tabs);LinearLayout navigation=new LinearLayout(a);navigation.addView(tabScroll,new LinearLayout.LayoutParams(0,-2,1));body.addView(navigation);
        EditText search=new EditText(a);search.setTextColor(ModernUi.TEXT);search.setHintTextColor(ModernUi.MUTED);search.setHint("Search all settings");search.setTextSize(14);search.setSingleLine(true);navigation.setBaselineAligned(false);navigation.addView(search,new LinearLayout.LayoutParams(ModernUi.dp(a,145),ModernUi.dp(a,36)));
        float width=a.getResources().getDisplayMetrics().widthPixels/a.getResources().getDisplayMetrics().density/a.getResources().getConfiguration().fontScale;
        boolean wide=width>=650;
        LinearLayout content=new LinearLayout(a);content.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);body.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(a);LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);content.addView(scroll,wide?new LinearLayout.LayoutParams(0,-1,2.1f):new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout help=ModernUi.panel(a);TextView helpTitle=ModernUi.text(a,"SETTING DETAILS",16,ModernUi.ACCENT),helpText=ModernUi.text(a,"Select a label or focus an option to read its purpose, performance effects, and when it applies.",13,ModernUi.TEXT);help.addView(helpTitle);help.addView(helpText);ScrollView helpScroll=new ScrollView(a);helpScroll.addView(help);LinearLayout.LayoutParams hp=wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,ModernUi.dp(a,110));hp.leftMargin=wide?ModernUi.dp(a,8):0;content.addView(helpScroll,hp);
        LinearLayout footer=new LinearLayout(a);footer.setGravity(Gravity.CENTER_VERTICAL);TextView pending=ModernUi.text(a,"No unsaved changes",12,ModernUi.MUTED);footer.addView(pending,new LinearLayout.LayoutParams(0,-2,1));Button reset=ModernUi.button(a,"Reset",false),discard=ModernUi.button(a,"Discard",false);footer.setBaselineAligned(false);footer.addView(reset,new LinearLayout.LayoutParams(-2,ModernUi.dp(a,34)));LinearLayout.LayoutParams discardSize=new LinearLayout.LayoutParams(-2,ModernUi.dp(a,34));discardSize.leftMargin=ModernUi.dp(a,6);footer.addView(discard,discardSize);body.addView(footer);
        final Runnable[] render={null};final AlertDialog[] current={null};OverlayPreview preview=new OverlayPreview(a,draft);
        Runnable updatePending=()->{preview.invalidate();int changes=0,restarts=0;for(Choice c:choices)if(!Objects.equals(before.get(c.key),draft.get(c.key))){changes++;if(restart(c,before))restarts++;}pending.setText(changes==0?(PendingSettings.pending(data)?"Restart pending":"No unsaved changes"):changes+" unsaved · "+restarts+" require next launch");};
        java.util.function.BooleanSupplier commit=()->{
            Map<String,String> changed=new LinkedHashMap<>();for(Choice c:choices)if(!Objects.equals(before.get(c.key),draft.get(c.key)))changed.put(c.key,draft.get(c.key));
            Map<String,String> allChanged=new LinkedHashMap<>(changed),next=new LinkedHashMap<>();
            for(Choice c:choices)if(changed.containsKey(c.key)&&restart(c,before))next.put(c.key,changed.remove(c.key));
            changed.keySet().removeIf(key->key.startsWith("ui_"));next.keySet().removeIf(key->key.startsWith("ui_"));
            try{if(!changed.isEmpty()){if(runtime==null)MobileConfig.update(MobileConfig.file(data),changed);else if(!runtime.apply(changed.keySet().toArray(new String[0]),changed.values().toArray(new String[0])))throw new IOException("Some settings could not be applied.");}
                PendingSettings.save(data,next);
                for(String key:new String[]{"ui_reduce_motion","ui_sustained","ui_battery_refresh"})if(allChanged.containsKey(key))UiPreferences.set(a,uiKey(key),Boolean.parseBoolean(allChanged.get(key)));
                before.putAll(allChanged);updatePending.run();if(!next.isEmpty()||allChanged.containsKey("ui_sustained"))PendingSettings.prompt(a,data,runtime!=null);return true;}
            catch(IOException e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();return false;}
        };
        java.util.function.BooleanSupplier close=()->{
            state.scroll.put(selected[0],scroll.getScrollY());if(before.equals(draft))return true;
            ModernUi.builder(a).setTitle("Unsaved settings").setMessage("Save your changes before leaving?").setPositiveButton("Save",(d,w)->{if(commit.getAsBoolean())current[0].dismiss();}).setNegativeButton("Discard",(d,w)->current[0].dismiss()).setNeutralButton("Keep editing",null).show();return false;
        };
        if(logs!=null){
            Button reports=ModernUi.icon(a,"Logs","diagnostics",false);reports.setContentDescription("Logs & troubleshooting");
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,ModernUi.dp(a,36));rp.rightMargin=ModernUi.dp(a,8);footer.addView(reports,1,rp);
            reports.setOnClickListener(v->{Runnable leave=()->{current[0].dismiss();logs.run();};
                if(before.equals(draft)){leave.run();return;}
                ModernUi.builder(a).setTitle("Unsaved settings").setMessage("Save your changes before opening Logs?")
                    .setPositiveButton("Save",(d,w)->{if(commit.getAsBoolean())leave.run();})
                    .setNegativeButton("Discard",(d,w)->leave.run()).setNeutralButton("Keep editing",null).show();
            });
        }
        java.util.function.Consumer<Choice> explain=c->{helpTitle.setText(c.label.toUpperCase(Locale.ROOT));helpText.setText(guide(c.key)+"\n\nDefault: "+(c.values==null?numericLabel(c,Double.parseDouble(c.initial)):pretty(c.key,c.initial))+"\n\n"+(c.restart?"Saved for the next launch. You can quit after saving to apply it.":"Applies when you save.")+(c.key.equals("gta4_present_mode")&&runtime!=null?"\n\nActive presentation: "+runtime.get("gta4_session_present_mode"):"")+(c.key.equals("gta4_native_anti_aliasing")&&runtime!=null?"\n\n"+activeAa(runtime):""));};
        final java.util.function.Consumer<Boolean> editor=edit!=null?edit:runtime==null?xbox->a.startActivity(new Intent(a,TouchEditorActivity.class).putExtra("xbox",xbox)):null;
        ArrayList<Button> tabButtons=new ArrayList<>();
        for(String category:categories){Button b=ModernUi.button(a,category,false);b.setTag("ui-tab");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,ModernUi.dp(a,34));lp.rightMargin=ModernUi.dp(a,4);tabs.addView(b,lp);tabButtons.add(b);b.setOnClickListener(v->{state.scroll.put(selected[0],scroll.getScrollY());selected[0]=category;state.category=category;search.setText("");render[0].run();});}
        render[0]=()->{
            state.category=selected[0];rows.removeAllViews();for(int i=0;i<categories.length;i++)ModernUi.selected(tabButtons.get(i),selected[0].equals(categories[i]));
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
            if(selected[0].equals("Video")&&query.isEmpty()&&runtime!=null)rows.addView(ModernUi.text(a,"Active rendering: "+runtime.get("gta4_effective_upscaling"),12,ModernUi.MUTED));
            if(selected[0].equals("Overlay")&&query.isEmpty()){ModernUi.gridAction(a,rows,"Edit overlay",()->OverlayEditorUi.show(a,draft,()->{updatePending.run();render[0].run();rows.post(()->{View launch=rows.findViewWithTag("overlay-editor-launch");if(launch!=null)launch.requestFocus();});})).setTag("overlay-editor-launch");}
            if(selected[0].equals("Performance")&&query.isEmpty()&&runtime!=null)rows.addView(ModernUi.text(a,ThermalStatus.summary(runtime.get("gta4_thermal_status"),System.nanoTime(),false),12,ModernUi.ACCENT));
            if(selected[0].equals("Performance")&&query.isEmpty())ModernUi.gridAction(a,rows,"Balanced settings",()->{draft.put("android_gpu_turbo","false");draft.put("android_thermal_guard","true");draft.put("ui_battery_refresh","true");if(sustainedSupported(a))draft.put("ui_sustained","true");draft.put("audio_low_latency","false");updatePending.run();render[0].run();});
            if(selected[0].equals("Controller")&&query.isEmpty()){ModernUi.gridAction(a,rows,"Test controller",()->ControllerTestUi.show(a));ModernUi.gridAction(a,rows,"Controller presets",()->ModernUi.choose(a,"Controller preset",new String[]{"Linear response","Reduce stick drift","Restore controller defaults"},-1,n->{if(n==0)draft.put("gamepad_response_curve","1");else if(n==1){draft.put("gamepad_left_deadzone","0.12");draft.put("gamepad_right_deadzone","0.12");}else for(Choice c:TOUCH)if(c.key.startsWith("gamepad_"))draft.put(c.key,c.initial);updatePending.run();render[0].run();}));}
            if(selected[0].equals("Touch controls")&&query.isEmpty()&&editor!=null){ModernUi.gridAction(a,rows,"Edit mobile controls",()->{if(commit.getAsBoolean()){state.scroll.put(selected[0],scroll.getScrollY());if(runtime!=null){current[0].setOnDismissListener(null);current[0].dismiss();}editor.accept(false);}});}
            for(Choice c:choices){if(c.key.startsWith("gta4_overlay_"))continue;if(query.isEmpty()?!category(c).equals(selected[0]):!(c.label+" "+guide(c.key)).toLowerCase(Locale.ROOT).contains(query))continue;count++;
                boolean restart=restart(c,before);boolean enabled=(!c.key.equals("ui_sustained")||sustainedSupported(a))&&(!c.key.startsWith("gta4_phone_gyro")||phoneGyroAvailable(a))&&(!c.key.equals("gta4_native_smaa_quality")||"smaa".equals(draft.get("gta4_native_anti_aliasing")))&&(!c.key.startsWith("gta4_fsr1_")||"fsr1".equals(draft.get("gta4_native_upscaler")));
                LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setMinimumHeight(ModernUi.dp(a,42));row.setPadding(ModernUi.dp(a,9),ModernUi.dp(a,3),ModernUi.dp(a,9),ModernUi.dp(a,3));row.setBackground(ModernUi.surface(a,0xff202123,10));
                TextView label=ModernUi.text(a,c.label+(c.key.equals("ui_sustained")&&!sustainedSupported(a)?" · unsupported":"")+(c.key.equals("gta4_present_mode")&&runtime!=null?" · Active: "+runtime.get("gta4_session_present_mode"):"")+(restart?" · restart":""),14,enabled?ModernUi.TEXT:ModernUi.MUTED);label.setFocusable(true);ModernUi.focusableCard(a,label,0xff202123);row.addView(label,new LinearLayout.LayoutParams(0,-2,1.1f));label.setOnClickListener(v->explain.accept(c));label.setOnFocusChangeListener((v,on)->{if(on)explain.accept(c);});
                if(c.values!=null&&c.values.length==2&&((Arrays.asList(c.values).contains("true")&&Arrays.asList(c.values).contains("false"))||c.key.equals("gta4_present_mode")||c.key.equals("gta4_native_shadow_interval"))){
                    String onValue=c.key.equals("gta4_disable_tlad_film_grain")?"false":c.key.equals("gta4_present_mode")?"vsync":c.key.equals("gta4_native_shadow_interval")?"1":"true",offValue=c.key.equals("gta4_disable_tlad_film_grain")?"true":c.key.equals("gta4_present_mode")?"immediate":c.key.equals("gta4_native_shadow_interval")?"0":"false";
                    LinearLayout toggle=new LinearLayout(a);row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
                    for(boolean on:new boolean[]{true,false}){Button b=ModernUi.button(a,pretty(c.key,on?onValue:offValue),false);b.setEnabled(enabled);ModernUi.selected(b,(on?onValue:offValue).equals(draft.get(c.key)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ModernUi.dp(a,38),1);lp.leftMargin=ModernUi.dp(a,4);toggle.addView(b,lp);b.setOnFocusChangeListener((v,f)->{if(f)explain.accept(c);});b.setOnClickListener(v->{draft.put(c.key,on?onValue:offValue);explain.accept(c);updatePending.run();for(int i=0;i<toggle.getChildCount();i++)ModernUi.selected((Button)toggle.getChildAt(i),(i==0)==on);});}
                }else if(c.values!=null){
                    Button value=ModernUi.button(a,pretty(c.key,draft.get(c.key)),false);value.setEnabled(enabled);
                    row.addView(value,new LinearLayout.LayoutParams(0,ModernUi.dp(a,36),1));
                    value.setOnFocusChangeListener((v,on)->{if(on)explain.accept(c);});
                    value.setOnClickListener(v->{
                        explain.accept(c);String[] labels=new String[c.values.length];
                        for(int i=0;i<labels.length;i++)labels[i]=pretty(c.key,c.values[i]);
                        ModernUi.choose(a,c.label,labels,Arrays.asList(c.values).indexOf(draft.get(c.key)),i->{
                            state.scroll.put(selected[0],scroll.getScrollY());draft.put(c.key,c.values[i]);updatePending.run();render[0].run();
                            rows.post(()->{for(int j=0;j<rows.getChildCount();j++)if(c.key.equals(rows.getChildAt(j).getTag())){
                                View target=((LinearLayout)rows.getChildAt(j)).getChildAt(1);target.requestFocus();break;
                            }});
                        });
                    });
                }
                else{
                    double parsed;try{parsed=Double.parseDouble(draft.get(c.key));if(!Double.isFinite(parsed))throw new NumberFormatException();}catch(Exception e){parsed=Double.parseDouble(c.initial);}
                    LinearLayout field=new LinearLayout(a);field.setOrientation(LinearLayout.HORIZONTAL);field.setGravity(Gravity.CENTER_VERTICAL);field.setBaselineAligned(false);row.addView(field,new LinearLayout.LayoutParams(0,-2,1.5f));TextView amount=ModernUi.text(a,numericLabel(c,parsed),13,ModernUi.ACCENT);amount.setPadding(0,0,0,ModernUi.dp(a,3));amount.setFocusable(true);amount.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);field.addView(amount,new LinearLayout.LayoutParams(ModernUi.dp(a,65),ModernUi.dp(a,36)));SeekBar slider=new SeekBar(a);slider.setMax((int)Math.round((c.max-c.min)/c.step));slider.setProgress((int)Math.round((Math.max(c.min,Math.min(c.max,parsed))-c.min)/c.step));slider.setEnabled(enabled);ModernUi.tint(slider);field.addView(slider,new LinearLayout.LayoutParams(0,ModernUi.dp(a,36),1));
                    slider.setOnFocusChangeListener((v,on)->{if(on)explain.accept(c);});slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user){double n=c.min+value*c.step;draft.put(c.key,number(n));amount.setText(numericLabel(c,n));explain.accept(c);updatePending.run();}}public void onStartTrackingTouch(SeekBar b){explain.accept(c);}public void onStopTrackingTouch(SeekBar b){}});
                    amount.setOnClickListener(v->{if(!enabled)return;explain.accept(c);EditText input=new EditText(a);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(draft.get(c.key));ModernUi.builder(a).setTitle(c.label+" ("+c.min+"–"+c.max+")").setView(input).setPositiveButton("Apply",(d,w)->{try{double n=Double.parseDouble(input.getText().toString());if(!Double.isFinite(n))throw new NumberFormatException();n=Math.max(c.min,Math.min(c.max,n));slider.setProgress((int)Math.round((n-c.min)/c.step));draft.put(c.key,number(c.min+slider.getProgress()*c.step));amount.setText(numericLabel(c,Double.parseDouble(draft.get(c.key))));updatePending.run();}catch(Exception e){Toast.makeText(a,"Enter a valid number.",Toast.LENGTH_SHORT).show();}}).setNegativeButton("Cancel",null).show();});
                }
                row.setTag(c.key);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ModernUi.dp(a,8);rows.addView(row,lp);
            }
            if(count==0)rows.addView(ModernUi.text(a,"No settings match this search.",14,ModernUi.MUTED));updatePending.run();scroll.post(()->scroll.scrollTo(0,state.scroll.getOrDefault(selected[0],0)));
        };
        reset.setOnClickListener(v->ModernUi.builder(a).setTitle("Reset "+selected[0]+"?").setMessage("Only editable options in this category will change. Press Save to apply.").setPositiveButton("Reset",(d,w)->{for(Choice c:choices)if(category(c).equals(selected[0]))draft.put(c.key,c.initial);render[0].run();}).setNegativeButton("Cancel",null).show());
        discard.setOnClickListener(v->{draft.clear();draft.putAll(before);render[0].run();});
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}public void afterTextChanged(android.text.Editable e){}});
        AlertDialog dialog=ModernUi.screen(a,"Settings",body,"Save",()->{if(commit.getAsBoolean())Toast.makeText(a,"Settings saved.",Toast.LENGTH_SHORT).show();},close);current[0]=dialog;render[0].run();dialog.show();return dialog;
    }
    private static boolean phoneGyroAvailable(Activity a){android.hardware.SensorManager manager=(android.hardware.SensorManager)a.getSystemService(Activity.SENSOR_SERVICE);return manager!=null&&manager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE)!=null;}
    private static String activeAa(Runtime runtime){try{org.json.JSONObject info=new org.json.JSONObject(runtime.get("gta4_session_stats"));return "Active AA: "+info.optString("aa","waiting for sample")+" · "+info.optInt("scene_samples",1)+" samples";}catch(Exception error){return "Active AA: waiting for sample";}}
    static String guide(String key){
        switch(key){
            case "gta4_disable_tlad_film_grain":return "Controls the existing Lost and Damned grain shader. Other episodes may not contain this effect.";
            case "gta4_native_output_dither":return "Adds subtle noise to reduce color banding at presentation. Off can reveal gradients; it is not a texture-quality setting.";
            case "ui_battery_refresh":return "Requests the lowest display refresh rate that matches the frame cap evenly, where Android supports it. Can reduce display power use. Off prefers the highest matching rate. The phone may decline a request.";
            case "ui_reduce_motion":return "Disables menu entrance, press and artwork movement. Android system animation settings are respected too.";
            case "ui_sustained":return "Asks Android for steadier sustained performance where supported. Disabled on phones that report no support. Applies when a new game process starts.";
            case "gta4_safe_mode":return "Turns off registered optional speed shortcuts for troubleshooting after restart. This can lower performance.";
            case "gta4_phone_gyro_mode":return "Use the phone gyroscope for camera movement. Aim only activates while aiming; Gameplay stays active during play. Stops in menus, loading screens and the background. Requires a phone gyroscope.";
            case "gta4_phone_gyro_sensitivity":return "Higher values turn the camera farther for the same phone rotation. Start low for small aiming adjustments.";
            case "gta4_phone_gyro_smoothing":return "Filters small sensor vibrations. Higher values smooth movement but add response delay. Zero responds directly.";
            case "gta4_phone_gyro_invert_x":return "Reverses horizontal camera movement from the phone gyroscope.";
            case "gta4_phone_gyro_invert_y":return "Reverses vertical camera movement from the phone gyroscope.";
            case "gta4_touch_icon_outline":return "Adds an outline around each circular touch button. Icons stay clean; positions and hit areas are unchanged.";
            case "gta4_overlay_edge_gap":return "Gap from the screen edge. Display cutouts are retained; zero reaches the usable top and bottom.";
            case "gta4_performance_overlay":return "Shows publisher FPS and average frame interval while playing. Detailed can also show app CPU, available GPU usage, RAM, heat severity and actual internal/output resolution. These are game publications, not a physical display FPS measurement.";
            case "gta4_overlay_position":return "Places the performance overlay in a screen corner, inside the safe area. The editor and live overlay share the same bounds; zero edge gap reaches the usable edges.";
            case "gta4_touch_aim_hold_lock":return "Quick taps aim only while pressed. Hold Aim to keep aiming after release, then tap again to unlock. Leaving gameplay clears the lock.";
            case "gta4_touch_aim_hold_seconds":return "How long to hold the mobile Aim button before it locks. Increase this if ordinary presses lock aim accidentally.";
            case "android_perf_hint":return "Lets Android schedule work against the selected frame target. Device support varies. This is a hint, not a guarantee of faster clocks.";
            case "android_thermal_guard":return "When sustained thermal pressure is detected, temporarily reduces draw distance and backs off GPU Turbo. Restores after the phone stays cool. Can trade detail for steadier sustained play.";
            case "android_gpu_turbo":return "Requests more GPU performance where the device permits it. Can increase heat and power use; Thermal Guard can back it off under pressure.";
            case "audio_adaptive_buffer":return "After audio runs out, rebuilds about 11 ms of audio before resuming. Can reduce repeated crackling during brief stalls with a small delay. It cannot recover audio the game never produced.";
            case "audio_mute":return "Mutes the game's audio output.";
            case "audio_low_latency":return "Uses shorter audio buffering after restart. May increase crackling when the game misses audio deadlines. Leave off when troubleshooting dropouts.";
            case "gta4_touch_animations":return "Adds a short visual response when a button is pressed. Turning this off removes the animation; input still responds immediately.";
            case "gta4_phone_rumble_strength":return "Uses the phone motor for the game's controller rumble. Zero turns it off. Strength control depends on the phone; a connected controller keeps its own vibration.";
            case "gta4_native_pipeline_workers":return "Number of background workers preparing game graphics. One uses less CPU and heat. More can prepare several effects together but may compete with gameplay. Try two before higher values. A driver-specific worker choice takes priority at launch.";
            case "mnk_mode":return "Enables physical keyboard and mouse gameplay input. Mobile controls remain available separately. Mouse capture releases while an Android menu is open.";
            case "mnk_sensitivity":return "Scales mouse camera movement. Lower values allow finer aiming; higher values turn faster. Start near one.";
            case "mnk_invert_y":return "Reverses vertical camera movement from a physical mouse.";
            case "gta4_overlay_compact":return "Places overlay information on one line. Turn off for separate, easier-to-read lines. Available in Edit overlay.";
            case "gta4_overlay_opacity":return "Visibility of the performance overlay. Lower values show more of the game underneath. Available in Edit overlay.";
            case "gta4_overlay_ram":return "Includes app memory use in the detailed performance overlay. This is not all memory used by the phone.";
            case "gta4_overlay_thermal":return "Includes Android's heat severity in the detailed overlay. Severity labels are not a temperature in degrees.";
            case "gta4_overlay_text_size":return "Size of the performance overlay text. Edit it while viewing the fullscreen preview.";
            case "gta4_overlay_x":case "gta4_overlay_y":return "Custom overlay position within the usable screen. Drag the sample in Edit overlay or move it with direction buttons.";
            case "gta4_native_startup_prepare_seconds":return "Maximum startup time spent preparing graphics learned on previous runs. A longer limit can reduce later stutters but delays starting. Zero skips this step. New effects can still need preparation during play.";
            case "gta4_native_max_queued_frames":return "Maximum frames allowed ahead of display. Lower values can improve input response; higher values can absorb brief uneven work. This is not an FPS multiplier.";
            case "gta4_native_texture_reuse_extended":return "Keeps reusable textures longer while memory has room. Can reduce repeated loading; memory pressure still releases them. Uses more RAM while active.";
            case "gta4_touch_context_delay_ms":return "Delay before any gameplay or phone layout change. Zero uses the earliest observed context; negative time cannot predict a future game signal. Menus, cutscenes and hidden controls always switch immediately. Larger values keep the old controls longer. Menus and loading respond immediately.";
            case "gamepad_left_deadzone":case "gamepad_right_deadzone":return "Ignores small stick movement near the center to reduce drift. Zero preserves the controller input. Start around 0.05 if your stick drifts. Applies only to physical controllers.";
            case "gamepad_left_sensitivity":case "gamepad_right_sensitivity":return "Scales physical stick movement. One preserves normal response. Higher values reach maximum movement sooner; lower values allow finer control.";
            case "gamepad_response_curve":return "One is linear. Above one gives finer control near the center; below one responds more strongly to small movements. Applies to physical sticks.";
            case "gamepad_trigger_threshold":return "Ignores light physical trigger presses and scales the remaining travel. Increase only if a trigger activates by itself.";
            case "gamepad_invert_right_x":return "Reverses horizontal movement of the physical controller's right stick.";
            case "gamepad_invert_right_y":return "Reverses vertical movement of the physical controller's right stick.";
            case "gamepad_rumble_strength":return "Physical controller vibration strength. Zero turns it off. Phone vibration has a separate setting. Hardware support varies.";
            case "gta4_native_depth_clip_fallback":return "For devices without depthClamp only. Ordinary clipping can remove near/far-plane geometry and shadows. This is a visual compromise, not equivalent depth clamping. Off rejects affected pipelines.";
            case "gta4_overlay_cpu":return "App CPU use as a share of the available CPU cores, sampled once per second. This is not whole-phone CPU use.";
            case "gta4_overlay_gpu":return "GPU utilization when the driver exposes a readable counter. Unavailable means Android blocks it; no estimated value is substituted.";
            case "gta4_overlay_resolution":return "Shows the actual internal rendering dimensions and output dimensions, including scaling. Preset names alone do not describe both.";
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
            case "gta4_reflection_resolution":return "Base size of reflected scenes. Half and Quarter reduce each dimension of the original reflection, saving GPU work and memory with less detail. Higher settings cost more.";
            case "gta4_water_reflection_resolution":return "Resolution of water reflections. Inherit uses the base reflection setting.";
            case "gta4_mirror_reflection_resolution":return "Resolution of mirror reflections. Inherit uses the base reflection setting.";
            case "gta4_reflection_aa":return "Smooths reflection edges. Off saves work; multisampling uses additional GPU memory and bandwidth.";
            case "gta4_reflection_capture_distance":return "How far reflected scenes reach. Near reduces scene preparation and drawing; longer distances include more objects.";
            case "gta4_native_water_reflection_interval":return "How often water reflections refresh. Every two or three frames saves work but moving reflections can step. Off removes the capture.";
            case "gta4_native_environment_reflection_interval":return "How often environment reflections refresh. Less frequent updates save work but can show delayed movement.";
            case "gta4_motion_blur":return "Adds blur during movement. Turning it off gives a clearer image and may reduce post-processing work.";
            case "gta4_native_pipeline_lookahead":return "Prepares upcoming graphics pipelines on the recording thread using a small bounded lookahead. Keeps the exact rendering fallback. Disable when comparing a suspected regression.";
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
    private static String numericLabel(Choice c,double n){if(c.key.equals("gta4_touch_context_delay_ms"))return Math.round(n)+" ms";if(c.key.contains("deadzone")||c.key.equals("gamepad_trigger_threshold")||c.key.equals("gamepad_rumble_strength"))return Math.round(n*100)+"%";if(c.key.equals("gta4_overlay_text_size"))return Math.round(n)+" sp";if(c.key.equals("gta4_overlay_edge_gap"))return Math.round(n)+" dp";if(c.key.equals("gta4_overlay_x")||c.key.equals("gta4_overlay_y"))return Math.round(n*100)+"%";if(c.key.equals("gta4_touch_aim_hold_seconds"))return Math.round(n*1000)+" ms";if(c.key.contains("opacity")||c.key.equals("gta4_phone_rumble_strength"))return Math.round(n*100)+"%";if(c.key.contains("smoothing"))return Math.round(n*1000)+" ms";if(c.key.contains("margin"))return number(n*100)+"%";return number(n)+"×";}
    private static String pretty(String key,String value){
        if(key.equals("gta4_disable_tlad_film_grain"))return value.equals("true")?"Off":"On";
        if(key.equals("gta4_touch_radar_top"))return value.equals("true")?"Top left":"Original position";
        if(key.equals("gta4_phone_gyro_mode"))return value.equals("aim")?"Aim only":value.equals("always")?"Gameplay":"Off";
        if(key.equals("gta4_performance_overlay"))return value.equals("fps")?"FPS":value.equals("detailed")?"Detailed":"Off";
        if(key.equals("gta4_native_startup_prepare_seconds"))return value.equals("0")?"Skip":value+" seconds";
        if(key.equals("gta4_native_pipeline_workers"))return value+(value.equals("1")?" worker":" workers");
        if(value.equals("half"))return "Half size";if(value.equals("quarter"))return "Quarter size";
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

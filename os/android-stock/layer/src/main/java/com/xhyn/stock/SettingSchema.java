package com.xhyn.stock;

import java.util.*;

/** Only settings exposed by v442's own launcher. Values are defaults from the effective stock profile. */
final class SettingSchema {
    static final class Entry {
        final String key, label, section, hint; final List<String> choices;
        Entry(String key, String label, String section, String hint, String... choices) {
            this.key=key; this.label=label; this.section=section; this.hint=hint; this.choices=Arrays.asList(choices);
        }
        boolean accepts(String value) {
            if(value==null)return false;if(choices.contains(value))return true;
            // The stock profile uses continuous values such as CAS 0.95 and density 0.4.
            if(!CONTINUOUS.contains(key)||!value.matches("[0-9]+(?:\\.[0-9]+)?"))return false;
            try{double n=Double.parseDouble(value);return Double.isFinite(n)&&n>=0&&n<=3;}catch(NumberFormatException e){return false;}
        }
        String display(String value) {
            if(key.equals("ui_renderer")&&value.equals("native"))return "Native (experimental)";
            if(key.equals("user_language")){String[] languages={"English","French","German","Italian","Russian","Spanish"};try{int n=Integer.parseInt(value);if(n>=0&&n<languages.length)return languages[n];}catch(NumberFormatException ignored){}}
            if(key.equals("gta4_frame_limit")&&value.equals("0"))return "Unlimited";
            if(key.equals("gta4_touch_layout"))return value.equals("context")?"Contextual":value.equals("gamepad")?"Xbox gamepad":value;
            return SettingSchema.display(value);
        }
    }
    private static final Set<String> CONTINUOUS=new HashSet<>(Arrays.asList("gta4_cas_sharpness","gta4_3d_resolution_scale","gta4_draw_distance_scale","gta4_model_lod_distance_scale","gta4_traffic_density_scale","gta4_parked_car_density_scale","gta4_ped_density_scale","gta4_scenario_ped_density_scale"));
    static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
        e("resolution","Output resolution","Display","Preset; the aspect ratio and output cap also affect actual dimensions.","","720p","1080p","1440p","4k"),
        e("gta4_output_height_cap","Output height cap","Display","0 uses the selected output height. Applied on the next launch.","0","360","480","540","720","900","1080"),
        e("gta4_3d_resolution_scale","3D resolution scale","Display","1.0 renders 3D at full output size; a smaller value lowers GPU work.","1.0","0.85","0.75","0.50"),
        e("gta4_aspect_ratio","Aspect ratio","Display","Auto follows the display; it can change the output width.","auto","16:9","16:10","4:3","21:9"),
        e("gta4_native_upscaler","Upscaling","Display","Stock native, CAS sharpening or FSR1 spatial upscaling.","native","cas","fsr1"),
        e("gta4_cas_sharpness","CAS sharpness","Display","Higher values can emphasize edges and noise.","0.0","0.5","0.90","1.10","1.5"),
        e("gta4_fsr1_quality","FSR1 quality","Display","Lower quality trades detail for performance.","ultra_quality","quality","balanced","performance","ultra_performance"),
        e("gta4_native_anti_aliasing","Anti-aliasing","Graphics","MSAA increases GPU and memory cost. Stock normalization is retained.","off","fxaa","smaa","msaa2x","msaa4x"),
        e("gta4_native_openflow","OpenFlow interpolation","Display","Experimental 2× interpolation; adds latency. Stock forces VSync and disables incompatible modes.","false","true"),
        e("gta4_native_openflow_min_side","OpenFlow motion grid","Display","A smaller grid reduces interpolation work.","64","96","128","180","252"),
        e("gta4_present_mode","Presentation","Display","VSync follows the display. Immediate may tear.","vsync","immediate"),
        e("gta4_frame_limit","Frame limit","Display","0 leaves the frame rate uncapped; this is a limit, not measured FPS.","0","30","40","60","120"),
        e("gta4_draw_distance_scale","Draw distance","World","Lower values reduce visible distance. Existing custom values are retained until changed.","3.0","1.0","0.85","0.75","0.5","0.35"),
        e("gta4_dynamic_draw_distance","Dynamic draw distance","World","Stock automatic draw-distance adjustment.","false","true"),
        e("gta4_model_lod_distance_scale","Model detail distance","World","Lower values use simpler models sooner.","1.0","0.85","0.75","0.5"),
        e("gta4_shadow_map_base_size","Shadow resolution","Graphics","Controls shadow texture size; no native shadow patch is applied.","64","128","256","512","1024"),
        e("gta4_native_shadow_interval","Sun shadows","Graphics","0 disables sun updates; 1 updates each frame.","0","1"),
        e("gta4_reflection_resolution","Reflection resolution","Graphics","Quarter: 80×45/cube 64. Half: 160×90/cube 128. Higher modes cost more.","quarter","half","original","1080p","full"),
        e("gta4_native_water_reflection_interval","Water update interval","Graphics","0 disables updates; otherwise update every N frames.","0","1","2","3"),
        e("gta4_native_environment_reflection_interval","Vehicle reflection interval","Graphics","0 disables updates; otherwise update every N frames.","0","1","2","3"),
        e("gta4_mirror_reflection_resolution","Mirror resolution","Graphics","Inherit uses the main reflection setting.","inherit","quarter","half","original","1080p","full"),
        e("gta4_water_reflection_resolution","Water reflection resolution","Graphics","Inherit uses the main reflection setting.","inherit","quarter","half","original","1080p","full"),
        e("gta4_environment_reflection_resolution","World reflection resolution","Graphics","Inherit uses the main reflection setting.","inherit","quarter","half","original","1080p","full"),
        e("gta4_motion_blur","Motion blur","Graphics","Stock motion blur effect.","false","true"),
        e("gta4_texture_filtering","Texture filtering","Graphics","Trilinear smooths transitions between texture levels.","bilinear","trilinear"),
        e("gta4_anisotropic_filtering","Anisotropic filtering","Graphics","Improves angled textures; higher settings can increase GPU work.","1x","2x","4x","8x","16x"),
        e("gta4_traffic_density_scale","Traffic density","World","Lower density may reduce CPU work.","1.25","1.0","0.75","0.5","0.35"),
        e("gta4_parked_car_density_scale","Parked cars","World","Stock parked vehicle density.","1.25","1.0","0.75","0.5","0.35"),
        e("gta4_ped_density_scale","Pedestrian density","World","Stock pedestrian density.","1.25","1.0","0.75","0.5","0.35"),
        e("gta4_scenario_ped_density_scale","Ambient pedestrians","World","Stock scenario pedestrian density.","1.25","1.0","0.75","0.5","0.35"),
        e("android_device_haptics","Phone vibration","Controls","Stock game haptics. Controller rumble is handled by SDL.","false","true"),
        e("touch_controls","Touch buttons","Controls","Use the stock touch controls or turn them off.","off","on"),
        e("gta4_touch_layout","Touch layout","Controls","Contextual controls or the stock Xbox gamepad layout.","context","gamepad"),
        e("gta4_skip_intro","Skip intro","Game","Skip the opening logos.","false","true"),
        e("user_language","Game language","Game","0 English · 1 French · 2 German · 3 Italian · 4 Russian · 5 Spanish. Installed content may differ.","0","1","2","3","4","5"),
        e("audio_mute","Mute game audio","Game","Stock mute setting.","false","true"),
        e("android_thermal_guard","Thermal guard","Game","Stock thermal adaptation. Monitor Android thermal status in the HUD.","false","true")
    ));
    static Entry e(String key,String label,String section,String hint,String... choices) { return new Entry(key,label,section,hint,choices); }
    static Entry find(String key) { for (Entry e:ALL) if(e.key.equals(key)) return e; return null; }
    static String display(String value) {
        switch(value){case "":case "auto":return "Auto";case "true":case "on":return "On";case "false":case "off":return "Off";
            case "native":return "Native";case "previous":return "Previous";case "cas":return "CAS";case "fsr1":return "FSR1";case "fxaa":return "FXAA";case "smaa":return "SMAA";case "vsync":return "VSync";default:return value;}
    }
    private SettingSchema() {}
}

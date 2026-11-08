package com.libertyrecomp;
import java.util.*;
import org.json.*;
/** In-memory model for Android UI tests; it does not claim to execute the native editor. */
abstract class TouchSettings {
    private static final Map<String,String> settings=new HashMap<>();private static final Map<Integer,float[]> positions=new HashMap<>();private static int mode;
    private static void nativeOpen(String path){settings.clear();settings.putAll(Map.of("scale","1","opacity","1","camera","1","aim","1","vehicle","1","flight","1","camera_mode","stick","left","false","floating","false","invert","false"));positions.clear();}
    private static String nativeGet(String key){return settings.getOrDefault(key,"");}
    private static boolean nativeSet(String key,String value){settings.put(key,value);return true;}
    private static String nativeLayout(int context,float w,float h,float density)throws JSONException {mode=context;float[] p=positions.computeIfAbsent(mode,k->new float[]{w/4,h/2,30*density});return new JSONArray().put(new JSONObject().put("action",1).put("x",p[0]).put("y",p[1]).put("r",p[2]).put("label","Interact").put("icon","")).toString();}
    private static boolean nativeMove(int action,float x,float y,float radius){if(radius<=0||!Float.isFinite(x)||!Float.isFinite(y))return false;positions.put(mode,new float[]{x,y,radius});return true;}
    private static void nativeReset(boolean all){positions.clear();}
    private static boolean nativeSave(){return true;}
}

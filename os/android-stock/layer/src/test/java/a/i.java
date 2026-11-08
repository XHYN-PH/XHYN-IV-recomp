package a;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Test double only. The release uses the pinned original DEX, verified by preflight.py. */
public abstract class i {
    public static final l[] b={new l("resolution","","720p"),new l("gta4_native_anti_aliasing","","smaa"),new l("saved_game_root","","")};
    public static final LinkedHashMap<String,String> values=new LinkedHashMap<>();public static int writes;public static boolean failWrites;
    public static LinkedHashMap<String,String> E(Context c,File data){LinkedHashMap<String,String> out=new LinkedHashMap<>();for(l entry:b)out.put(entry.a,entry.c);out.putAll(values);return out;}
    public static void J(File data,Map<String,String> changes){if(failWrites)throw new IllegalStateException("simulated settings failure");values.putAll(changes);writes++;}
    public static File q(Context c,File data){return new File(data,"LibertyRecomp/game");}
    public static File m(File game,ArrayList<String> args)throws IOException{for(String line:args)if(line.startsWith("--saved_game_root="))return new File(line.substring(18));return new File(game.getCanonicalFile().getParentFile(),"save");}
    public static d i(){return new d();}
}

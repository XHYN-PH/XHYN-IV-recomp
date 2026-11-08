package com.xhyn.stock;

import android.content.Context;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

final class StockDefaults {
    static Map<String,String> read(Context c) throws IOException {
        Map<String,String> out=new LinkedHashMap<>();
        // Definitions carry v442's actual defaults; profile assets then override them.
        try {
            Object[] definitions=(Object[])Class.forName("a.i").getField("b").get(null);
            for(Object definition:definitions) {
                Class<?> type=definition.getClass();
                out.put((String)type.getField("a").get(definition),(String)type.getField("c").get(definition));
            }
        } catch(ReflectiveOperationException e) { throw new IOException("Stock defaults are unavailable.",e); }
        String asset=profileAsset(c);
        try(InputStream in=c.getAssets().open(asset)) {
            String text=new String(FilesEx.read(in,262144),java.nio.charset.StandardCharsets.UTF_8);
            for(String line:text.split("\n")) {line=line.trim();int eq=line.indexOf('=');
                if(line.startsWith("--")&&eq>2)out.put(line.substring(2,eq),line.substring(eq+1));
            }
        }
        return out;
    }
    static String profileAsset(Context c)throws IOException {
        String asset="default_args.txt";
        try {
            Object gpu=StockApi.call("a.i","i",new Class[0]);
            int family=gpu.getClass().getField("a").getInt(gpu);
            boolean mali=Boolean.TRUE.equals(gpu.getClass().getMethod("a").invoke(gpu));
            if(family==1)asset="default_args_g99.txt";else if(mali)asset="default_args_mali.txt";
        } catch(ReflectiveOperationException e){throw new IOException("Stock GPU profile is unavailable.",e);}
        return asset;
    }
}

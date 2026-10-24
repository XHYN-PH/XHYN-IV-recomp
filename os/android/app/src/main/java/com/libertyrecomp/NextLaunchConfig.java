package com.libertyrecomp;
import java.io.*;
import java.util.*;
/** Durable restart edits, independent of the runtime's current configuration save. */
final class NextLaunchConfig {
 static File file(File data){return new File(data,"LibertyRecomp/next-launch.toml");}
 static Map<String,String> read(File data)throws IOException{return MobileConfig.read(file(data));}
 static void save(File data,Map<String,String> values)throws IOException{if(!values.isEmpty())MobileConfig.update(file(data),values);}
 static void activate(File data)throws IOException{Map<String,String> values=read(data);if(!values.isEmpty()){MobileConfig.update(MobileConfig.file(data),values);if(!file(data).delete())throw new IOException("Cannot acknowledge next-launch settings.");}}
 private NextLaunchConfig(){}
}

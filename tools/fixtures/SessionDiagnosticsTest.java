package com.libertyrecomp;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class SessionDiagnosticsTest {
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("session-diagnostics").toFile();
  String result="gta4-benchmark: complete measured_s=60.03 published_fps=29.77 interval_p50_ms=33.37 interval_p95_ms=34.39 interval_max_ms=102.83 failed=0";
  Properties measured=SessionHistory.benchmark("gta4-benchmark: start\n"+result+"\n");
  assert measured.getProperty("benchmark.interval_p95_ms").equals("34.39");
  assert SessionHistory.benchmark(result+"\ngta4-benchmark: start").isEmpty():"A prior completed run must not substitute for an unfinished run";
  assert SessionHistory.benchmark(result.replace("60.03","12.0")).isEmpty();
  assert SessionHistory.benchmark("window p95_ms=16.6\nwindow p95_ms=32.0").isEmpty():"Window percentiles are not a full benchmark";
  Properties other=new Properties();other.putAll(measured);measured.setProperty("configured.gta4_fsr1_quality","quality");other.setProperty("configured.gta4_fsr1_quality","performance");
  assert SessionHistory.differences(measured,other).contains("quality → performance");
  SessionHistory.save(root,"last-session",measured);
  assert SessionHistory.load(new File(SessionHistory.directory(root),"last-session.properties")).equals(measured);
  for(int i=0;i<30;i++)SessionHistory.save(root,"session-"+i,measured);
  assert SessionHistory.records(root).size()==24;
  assert new File(SessionHistory.directory(root),"last-session.properties").isFile();
  Properties session=new Properties();session.setProperty("kind","session");session.setProperty("started","1234");session.setProperty("log.session","debug-1234-0.log");SessionHistory.save(root,"last-session",session);
  File logs=new File(root,"debug_logs");assert logs.mkdir();File runtime=new File(logs,"debug-1234-0.log.runtime.log"),status=new File(runtime+".benchmark.status.txt");
  Files.writeString(runtime.toPath(),"gta4-benchmark: start\n"+result+"\n");Files.writeString(status.toPath(),"complete: 60-second measurement finished\n");
  SessionHistory.recoverBenchmark(root,root);
  File recovered=new File(SessionHistory.directory(root),"benchmark-1234-"+status.lastModified()+".properties");assert recovered.isFile();
  assert SessionHistory.load(recovered).getProperty("benchmark.interval_p95_ms").equals("34.39");
  DebugLogArchive.clear(root,root);assert !runtime.exists()&&recovered.isFile():"Log cleanup must preserve bounded benchmark results";
  File invalid=new File(root,"invalid.properties");Files.writeString(invalid.toPath(),"bad=\\uZZZZ");
  boolean rejected=false;try{SessionHistory.load(invalid);}catch(IOException e){rejected=true;}assert rejected;
  File link=new File(root,"link.properties");Files.createSymbolicLink(link.toPath(),invalid.toPath());
  rejected=false;try{SessionHistory.load(link);}catch(IOException e){rejected=true;}assert rejected;
  rejected=false;try{SessionHistory.save(root,"../escape",measured);}catch(IOException e){rejected=true;}assert rejected;
  float[] bottom=OverlayPlacement.place("bottom_right",0,0,1920,1080,200,60,40,52,0,30,2);
  assert bottom[0]==1718&&bottom[1]==988;
  float[] custom=OverlayPlacement.place("custom",1,1,1920,1080,200,60,40,52,0,30,2);
  assert Arrays.equals(bottom,custom);
  float[] top=OverlayPlacement.place("custom",-1,Float.NaN,200,100,40,20,10,20,15,10,2);
  assert top[0]>=12&&top[1]>=22&&top[1]<=68;
  float[] viewport={.025f,0,1,1,1920,1080};
  float[] projected=TouchViewport.project(viewport,1280,720);assert projected!=null&&Math.abs(projected[0]-32)<.01;
  assert TouchViewport.project(viewport,720,1280)==null:"Rotated geometry needs fresh calibration";
  System.out.println("Session diagnostics: completed benchmarks, condition differences, bounded history, corrupt records and safe overlay placement passed");
 }
}

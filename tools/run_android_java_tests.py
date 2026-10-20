#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];src=root/'os/android/app/src/main/java/com/libertyrecomp';out=root/'out/host-tests/java';out.mkdir(parents=True,exist_ok=True)
files=[src/(name+'.java') for name in ['DebugLog','DebugLogSink','DebugLogArchive','TouchLayoutSettings','TouchLayoutDocument','DebugLaunchOptions','PerformanceLaunchOptions','RefreshPolicy','MobileConfig','SaveData','DriverPolicy','PhoneRumblePolicy']]
files += [root/'tools/fixtures'/name for name in ['AndroidLogFilesTest.java','MobileOptionsTest.java','DriverPolicyTest.java','android-host/android/os/Process.java','android-host/android/util/Log.java']]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',str(out),*map(str,files)],check=True)
for name in ['MobileOptionsTest','AndroidLogFilesTest','DriverPolicyTest']:subprocess.run(['java','-ea','-cp',str(out),'com.libertyrecomp.'+name],check=True)

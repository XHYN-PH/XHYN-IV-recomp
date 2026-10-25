#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1];src=root/'os/android/app/src/main/java/com/libertyrecomp';out=root/'out/host-tests/java';out.mkdir(parents=True,exist_ok=True)
files=[src/(name+'.java') for name in ['DebugLog','LauncherCrashReport','DebugLogSink','DebugLogArchive','TouchLayoutSettings','TouchLayoutDocument','DebugLaunchOptions','PerformanceLaunchOptions','RefreshPolicy','MobileConfig','NextLaunchConfig','SaveData','SaveMetadata','DriverPolicy','PhoneRumblePolicy']]
files += [src/(name+'.java') for name in ['TouchIconAssets','GyroMath','SessionHistory','OverlayPlacement','TouchViewport','ModOverlayPolicy','DriverPackages','DriverState']]
files += [root/'tools/fixtures'/name for name in ['AndroidLogFilesTest.java','MobileOptionsTest.java','SaveSlotsTest.java','SaveMetadataTest.java','DriverPolicyTest.java','DriverPackagesTest.java','TouchIconAssetsTest.java','GyroMathTest.java','SessionDiagnosticsTest.java','TouchLayoutMergeTest.java','ModOverlayPolicyTest.java','android-host/android/os/Process.java','android-host/android/util/Log.java']]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',str(out),*map(str,files)],check=True)
for name in ['MobileOptionsTest','SaveSlotsTest','SaveMetadataTest','AndroidLogFilesTest','DriverPolicyTest','DriverPackagesTest','TouchIconAssetsTest','GyroMathTest','SessionDiagnosticsTest','TouchLayoutMergeTest','ModOverlayPolicyTest']:subprocess.run(['java','-ea','-cp',str(out),'com.libertyrecomp.'+name],check=True)

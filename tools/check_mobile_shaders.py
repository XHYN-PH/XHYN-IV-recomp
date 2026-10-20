#!/usr/bin/env python3
"""Validate optimized/unoptimized production motion-blur and presentation shaders."""
from pathlib import Path
import os
import subprocess
root=Path(__file__).resolve().parents[1]
sdk=root/'glue/rexglue-sdk-main'
out=root/'out/host-tests/shader-contract';out.mkdir(parents=True,exist_ok=True)
glslang=Path(os.environ.get('GLSLANG_VALIDATOR',str(root/'out/host-tools/glslang-16.6.0/bin/glslang')))
validator=Path(os.environ['ANDROID_NDK_HOME'])/'shader-tools/linux-x86_64/spirv-val'
def run(*args):subprocess.run([str(a) for a in args],check=True)
run('g++','-std=c++23','-I'+str(sdk/'src'),'-I'+str(sdk/'thirdparty/spirv-headers/include'),
    root/'tools/test_android_color_contract.cpp','-o',out/'rewrite')
source=root/'LibertyRecompLib/shader_overrides/motion_blur/gta_composite_mb_e2.glsl'
for optimize in (False,True):
    for late in (False,True):
        name=('optimized' if optimize else 'original')+('-late' if late else '-early')
        binary=out/(name+'.spv');rewritten=out/(name+'-rewritten.spv')
        flags=['-Os'] if optimize else []
        if late:flags+=['-DXENOS_RECOMP_LATE_FRAGMENT_TESTS=1']
        run(glslang,'-V','--target-env','vulkan1.2','-S','frag',*flags,'-o',binary,source)
        run(validator,'--target-env','vulkan1.2',binary)
        run(out/'rewrite',binary,rewritten)
        run(validator,'--target-env','vulkan1.2',rewritten)
for binary in (root/'out/host-tests/production-shaders').glob('*.spv'):run(validator,'--target-env','vulkan1.2',binary)
print('Production motion-blur early/late contracts and presentation shader validation passed')

#!/usr/bin/env python3
"""Validate the production address rewrite with real SPIR-V tools; no GPU required."""
from pathlib import Path
import argparse
import os
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools', type=Path, required=True)
    args = parser.parse_args()
    out = ROOT / 'out/host-tests/address-rewrite'
    out.mkdir(parents=True, exist_ok=True)
    binary = out / 'rewrite'
    subprocess.run(['g++', '-std=c++23', '-O1', '-g1', '-fsanitize=address,undefined',
                    '-fno-omit-frame-pointer', '-I' + str(ROOT / 'glue/rexglue-sdk-main/include'),
                    str(ROOT / 'tools/test_address_int64_rewrite.cpp'), '-o', str(binary)], check=True)
    common = '''OpCapability Shader
OpCapability Int64
OpMemoryModel Logical GLSL450
OpEntryPoint GLCompute %main "main"
OpExecutionMode %main LocalSize 1 1 1
%void = OpTypeVoid
%bool = OpTypeBool
%uint = OpTypeInt 32 0
%ulong = OpTypeInt 64 0
%fn = OpTypeFunction %void
%true = OpConstantTrue %bool
%a = OpConstant %ulong 4294967295
%b = OpConstant %ulong 4294967296
%shift = OpConstant %uint 32
'''
    cases = {
        'add': ('%r = OpIAdd %ulong %a %b', True),
        'subtract': ('%r = OpISub %ulong %a %b', True),
        'multiply': ('%r = OpIMul %ulong %a %b', True),
        'shift-left': ('%r = OpShiftLeftLogical %ulong %a %shift', True),
        'shift-right': ('%r = OpShiftRightLogical %ulong %b %shift', True),
        'compare': ('%r = OpULessThanEqual %bool %a %b', True),
        'select-spv13': ('%r = OpSelect %ulong %true %a %b', True),
        'division': ('%r = OpUDiv %ulong %a %b', False),
        'switch': ('''%selector = OpIAdd %ulong %a %b
OpSelectionMerge %merge None
OpSwitch %selector %default 1 %case
%default = OpLabel
OpBranch %merge
%case = OpLabel
OpBranch %merge
%merge = OpLabel''', False),
    }
    env = dict(os.environ, ASAN_OPTIONS='detect_leaks=0')
    for name, (body, accepted) in cases.items():
        source = out / (name + '.spvasm')
        original, rewritten = source.with_suffix('.spv'), out / (name + '.rewritten.spv')
        source.write_text(common + '%main = OpFunction %void None %fn\n%entry = OpLabel\n' + body + '\nOpReturn\nOpFunctionEnd\n')
        subprocess.run([str(args.tools / 'spirv-as'), '--target-env', 'spv1.3', str(source), '-o', str(original)], check=True)
        subprocess.run([str(args.tools / 'spirv-val'), '--target-env', 'vulkan1.2', str(original)], check=True)
        result = subprocess.run([str(binary), str(original), str(rewritten)], env=env, capture_output=True, text=True)
        if not accepted:
            if result.returncode != 1:
                raise SystemExit(name + ': unsupported operation was not rejected: ' + result.stderr)
        else:
            if result.returncode:
                raise SystemExit(name + ': ' + result.stderr)
            subprocess.run([str(args.tools / 'spirv-val'), '--target-env', 'vulkan1.2', str(rewritten)], check=True)
            words = struct.unpack('<' + 'I' * (rewritten.stat().st_size // 4), rewritten.read_bytes())
            p = 5
            while p < len(words):
                count, op = words[p] >> 16, words[p] & 65535
                assert not (op == 17 and words[p + 1] == 11), 'Int64 capability survived'
                assert not (op == 21 and words[p + 2] == 64), 'Int64 type survived'
                p += count
        print('PASS address rewrite:', name, flush=True)


if __name__ == '__main__':
    main()

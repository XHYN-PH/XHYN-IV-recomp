#!/usr/bin/env python3
"""Check address lowering against early/late title shaders with the pinned SMOL-V decoder."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import collections
import json
import os
import re
import subprocess
import zstandard

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tools', type=Path, required=True)
    args = parser.parse_args()
    out = ROOT / 'out/host-tests/address-rewrite/cache'
    out.mkdir(parents=True, exist_ok=True)
    binary = out.parent / 'cache-rewrite'
    smolv = ROOT / 'tools/XenosRecomp/thirdparty/smol-v/source'
    subprocess.run(['g++', '-std=c++23', '-O1', '-g1', '-fsanitize=address,undefined',
                    '-fno-omit-frame-pointer', '-DXHYN_TEST_SMOLV',
                    '-I' + str(ROOT / 'glue/rexglue-sdk-main/include'), '-I' + str(smolv),
                    str(ROOT / 'tools/test_address_int64_rewrite.cpp'), str(smolv / 'smolv.cpp'),
                    '-o', str(binary)], check=True)
    source = (ROOT / 'LibertyRecompLib/shader/shader_cache.cpp').read_text()
    packed = re.search(r'const uint8_t g_compressedSpirvCache\[\] = \{([^}]*)\}', source)[1]
    data = zstandard.ZstdDecompressor().decompress(bytes(map(int, packed.split(','))))
    cases = []
    for line in source.splitlines():
        if not re.match(r'\s*\{\s*0x', line):
            continue
        fields = line.strip().lstrip('{').split(',', 10)
        if len(fields) < 11:
            continue
        key = fields[0].strip()
        for label, oi, si in [('early', 3, 4), ('late', 5, 6)]:
            offset, size = int(fields[oi]), int(fields[si])
            if size:
                assert offset >= 0 and offset + size <= len(data)
                path = out / (key + '-' + label + '.smolv')
                path.write_bytes(data[offset:offset + size])
                cases.append(path)
    env = dict(os.environ, ASAN_OPTIONS='detect_leaks=0')

    def check(path):
        original, lowered = path.with_suffix('.spv'), path.with_suffix('.lowered.spv')
        def run(command):
            return subprocess.run(list(map(str, command)), env=env, capture_output=True, text=True)
        decoded = run([binary, path, original, 'decode'])
        if decoded.returncode:
            return ('decode-failed', path.name, decoded.stderr)
        valid = run([args.tools / 'spirv-val', '--target-env', 'vulkan1.2', original])
        if valid.returncode:
            return ('original-invalid', path.name, valid.stderr)
        rewritten = run([binary, path, lowered, 'smolv'])
        if rewritten.returncode == 1:
            return ('rejected', path.name, rewritten.stderr.strip())
        if rewritten.returncode:
            return ('rewrite-failed', path.name, rewritten.stderr)
        valid = run([args.tools / 'spirv-val', '--target-env', 'vulkan1.2', lowered])
        return ('lowered-invalid' if valid.returncode else 'passed', path.name, valid.stderr)

    results = []
    with ThreadPoolExecutor(max_workers=4) as executor:
        for result in executor.map(check, cases):
            results.append(result)
            if len(results) % 200 == 0:
                print('Checked title shader variants:', len(results), flush=True)
    counts = dict(collections.Counter(result[0] for result in results))
    report = {'counts': counts, 'variants': len(cases),
              'failures': [list(result) for result in results if result[0] != 'passed']}
    (out.parent / 'cache-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Title shader variants:', len(cases), counts)
    for result in report['failures'][:8]:
        print(result)
    if any(counts.get(key) for key in ['decode-failed', 'rewrite-failed', 'lowered-invalid']):
        raise SystemExit(1)


if __name__ == '__main__':
    main()

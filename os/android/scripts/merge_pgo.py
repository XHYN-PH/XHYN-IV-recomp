#!/usr/bin/env python3
"""Merge native profiles without replacing a working output on failure."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--llvm-profdata", default="llvm-profdata")
    parser.add_argument("-o", "--output", type=Path, required=True)
    parser.add_argument("inputs", nargs="+", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    inputs = [p.resolve(strict=True) for p in args.inputs]
    if output in inputs:
        parser.error("output must differ from input profiles")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=output.name + ".", suffix=".tmp", dir=output.parent)
    os.close(descriptor)
    temporary = Path(name)
    try:
        subprocess.run([args.llvm_profdata, "merge", "--failure-mode=any", "-o", str(temporary),
                        *map(str, inputs)], check=True)
        subprocess.run([args.llvm_profdata, "show", str(temporary)], check=True,
                       stdout=subprocess.DEVNULL)
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()

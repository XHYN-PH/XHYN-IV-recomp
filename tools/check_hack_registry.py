#!/usr/bin/env python3
"""Require explicit classification of performance and renderer CVars."""
from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / "glue/rexglue-sdk-main"
registry = (SDK / "gta4-recomp/src/gta4_hack_registry.h").read_text()
entries = re.findall(r'\{\s*"(\w+)"\s*,\s*Kind::', registry)
definitions = {}
pattern = re.compile(r'REXCVAR_DEFINE_\w+\s*\(\s*(\w+)\s*,\s*.*?,\s*"([^"]+)"', re.S)
for directory in (SDK / "src", SDK / "gta4-recomp/src"):
    for path in directory.rglob("*.cpp"):
        for name, category in pattern.findall(path.read_text(errors="replace")):
            definitions[name] = category
required = {name for name, category in definitions.items() if category in {
    "GTA IV/Graphics/Native Renderer", "GTA IV/Performance", "GTA IV/Stability"}}
errors = (["duplicate registry entry: " + name for name, n in Counter(entries).items() if n > 1]
          + ["unclassified cvar: " + name for name in sorted(required - set(entries))]
          + ["unknown registry cvar: " + name for name in sorted(set(entries) - definitions.keys())])
for error in errors:
    print(error, file=sys.stderr)
print(f"Registry: {len(entries)} entries, {len(required)} required CVars")
sys.exit(bool(errors))

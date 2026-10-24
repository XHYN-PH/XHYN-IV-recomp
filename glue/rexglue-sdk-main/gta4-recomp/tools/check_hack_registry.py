#!/usr/bin/env python3
"""Validate registry structure. Device equivalence and complete semantic coverage need review."""
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
source=(root/'src/gta4_hack_registry.h').read_text()
rows=re.findall(r'\{"([a-zA-Z0-9_]+)",\s*Kind::(\w+),\s*"([^"\n]*)",\s*"([^"\n]+)"\}',source)
errors=[];seen=set()
for name,kind,safe,description in rows:
 if name in seen:errors.append('Duplicate registered setting: '+name)
 seen.add(name)
 if safe and not re.fullmatch(r'[a-zA-Z0-9_.-]+',safe):errors.append('Invalid safe-mode value: '+name)
if not rows:errors.append('No registry entries found')
for error in errors:print(error,file=sys.stderr)
if errors:sys.exit(1)
print(f'{len(rows)} unique hack-registry records validated (structure, not device proof)')

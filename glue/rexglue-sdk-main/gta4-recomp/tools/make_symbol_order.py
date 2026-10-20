"""Writes the linker's symbol order (hottest functions first) from a PGO profile.

lld places the listed functions at the start of .text in this order, so the
code that runs most shares cache lines, pages and TLB entries; the ~30,000
functions that never ran stay out of the way. nfsmw-android does the same
("function ordering"). Heat is the sum of a function's block counters, so the
profile only has to name the functions: the order stays useful after the
generated code changes, because sub_X names are guest addresses.

usage: make_symbol_order.py <llvm-profdata> <profile.profdata> <out.txt>
"""
import re
import subprocess
import sys

tool, profile, out = sys.argv[1:4]
text = subprocess.run([tool, "show", "--all-functions", "--counts", profile],
                      capture_output=True, text=True, errors="replace", check=True).stdout
heat = {}
name = None
for line in text.splitlines():
    if line.startswith("  ") and not line.startswith("    ") and line.endswith(":"):
        name = line.strip()[:-1]
    elif name and "Block counts:" in line:
        heat[name] = sum(int(x) for x in re.findall(r"\d+", line.split(":", 1)[1]))
        name = None
total = sum(heat.values())
order, covered = [], 0
for count, symbol in sorted(((c, n) for n, c in heat.items() if c > 0), reverse=True):
    if ";" in symbol:  # file-local (static) functions carry their file path
        continue
    order.append(symbol)
    covered += count
    if covered > 0.995 * total:
        break
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(order) + "\n")
print(f"{len(order)} symbols of {len(heat)} profiled functions")

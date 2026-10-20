"""Writes a codegen config with guest registers as C++ locals, and the marks
for the functions that need ctx to hold their caller's registers.

With cr/ctr/xer/non_volatile_as_local the generated code keeps those
registers in C++ locals instead of ctx. Two kinds of functions still read a
caller's registers through ctx; their callers copy the locals into ctx around
the call and take them back:
  - sync_registers: every function with a native hook
    (tools/direct_calls_hooked.txt), because hooks read and write the caller's
    r14-r31 through ctx. The function body is generated normally.
  - share_registers: pieces the analyzer split off their owner (SEH handlers,
    tails), listed in tools/share_registers_fragments.txt (found with
    read_before_write.py). Their body keeps r14-r31 in ctx and saves nothing.

usage: mark_share_registers.py <input config.toml> <output config.toml>
"""
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))


def addresses(path):
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.split("#")[0].strip()
        if line:
            out.append(int(line.replace("sub_", ""), 16))
    return out


src, dst = sys.argv[1], sys.argv[2]
cfg = open(src, encoding="utf-8").read()
for key in ("ctr_as_local", "xer_as_local", "cr_as_local", "non_volatile_as_local"):
    cfg, n = re.subn(r"^%s = (true|false)" % key, "%s = true" % key, cfg, flags=re.M)
    assert n == 1, key

# Hooked functions sync (their body still localizes and preserves r14-r31);
# split-off pieces share (they run on their owner's registers and save
# nothing): a sharing body leaves r14-r31 changed in ctx, so it must never be
# used for an ordinary function.
fragments = set(addresses(os.path.join(TOOLS, "share_registers_fragments.txt")))
kinds = {a: "sync_registers" for a in addresses(os.path.join(TOOLS, "direct_calls_hooked.txt"))}
kinds.update({a: "share_registers" for a in fragments})
marks = sorted(kinds)
merged, added = 0, []
for address in marks:
    flag = kinds[address]
    pattern = re.compile(r'^"0x%08X"\s*=\s*\{([^}\n]*)\}' % address, re.M | re.I)
    match = pattern.search(cfg)
    if match:
        inner = match.group(1).strip()
        if flag not in inner:
            inner = (inner + ", " if inner else "") + flag + " = true"
        cfg = cfg[:match.start()] + '"0x%08X" = { %s }' % (address, inner) + cfg[match.end():]
        merged += 1
    else:
        added.append('"0x%08X" = { %s = true }' % (address, flag))

header = "\n[functions]\n"
at = cfg.index(header) + len(header)
block = ("# sync_registers / share_registers: see tools/mark_share_registers.py.\n" + "\n".join(added) + "\n\n")
cfg = cfg[:at] + block + cfg[at:]
open(dst, "w", encoding="utf-8", newline="\n").write(cfg)
print(f"{len(marks)} marked ({merged} merged into existing entries, {len(added)} added)")

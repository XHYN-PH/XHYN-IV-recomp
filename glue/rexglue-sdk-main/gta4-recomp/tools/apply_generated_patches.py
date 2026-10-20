"""Applies the hand edits the Android build needs to freshly generated code.
Run after every code generation (idempotent).

  sub_8297CE78: a freed child object in its child table crashed the game
  (2026-10-09). The child pointer loaded by `lwzx r24,r10,r11` is checked by
  GTA4_StaleChildGuard (src/android/android_stale_object_guard.cpp); a stale
  one takes the title's empty-table path (r24 = r11).

  Memory barriers: code generated before the codegen lowered sync, lwsync,
  eieio and isync (src/codegen/builders/system.cpp) has only their comments.
  Each gets its REX_PPC_* fence (gta4_init.h) on the next line.

usage: apply_generated_patches.py <generated dir>
"""
import glob
import os
import re
import sys

gen = sys.argv[1]
DECL = ('#if defined(__ANDROID__)\n'
        'extern "C" bool GTA4_StaleChildGuard(uint32_t child);\n'
        '#endif\n')
MARK = "GTA4_StaleChildGuard(r24"
BARRIERS = {"sync": "REX_PPC_SYNC", "lwsync": "REX_PPC_LWSYNC", "eieio": "REX_PPC_EIEIO",
            "isync": "REX_PPC_ISYNC"}
BARRIER_COMMENT = re.compile(r"^\t// (sync|lwsync|eieio|isync)\b[^\n]*\n(?!\tREX_PPC_)", re.M)

fenced = 0
for path in glob.glob(os.path.join(gen, "gta4_recomp.*.cpp")):
    text = open(path, encoding="utf-8").read()
    text, n = BARRIER_COMMENT.subn(lambda m: m.group(0) + "\t%s();\n" % BARRIERS[m.group(1)], text)
    if n:
        open(path, "w", encoding="utf-8", newline="").write(text)
        fenced += n
print(f"memory barriers: {fenced} fences added")

applied = 0
for path in glob.glob(os.path.join(gen, "gta4_recomp.*.cpp")):
    text = open(path, encoding="utf-8").read()
    start = text.find("DEFINE_REX_FUNC(sub_8297CE78)")
    if start < 0:
        continue
    end = text.find("\nDEFINE_REX_FUNC(", start + 1)
    end = len(text) if end < 0 else end
    body = text[start:end]
    if "GTA4_StaleChildGuard(" in body:
        print(f"{os.path.basename(path)}: already patched")
        applied += 1
        continue
    # The load follows `addi r10,r22,162` / `rlwinm`; each register may be in
    # ctx or a local (volatile_as_local makes r10/r11 locals too).
    pattern = re.compile(
        r"(\t// addi r10,r22,162\n[^\n]*\n\t// rlwinm r10,r10,2,0,29\n[^\n]*\n"
        r"\t// lwzx r24,r10,r11\n\t((?:ctx\.)?r24)\.u64 = "
        r"REX_LOAD_U32\((?:ctx\.)?r10\.u32 \+ ((?:ctx\.)?r11)\.u32\);\n)")
    matches = list(pattern.finditer(body))
    if len(matches) != 1:
        sys.exit(f"sub_8297CE78: expected one child-table load, found {len(matches)}")
    match = matches[0]
    r24 = match.group(2)
    r11 = match.group(3)
    guard = ("#if defined(__ANDROID__)\n"
             "\t// Hand edit (tools/apply_generated_patches.py): a freed child takes the\n"
             "\t// title's empty-table path below (r24 = r11) instead of faulting at +100.\n"
             f"\tif (!GTA4_StaleChildGuard({r24}.u32)) {r24}.u64 = {r11}.u64;\n"
             "#endif\n")
    body = body[:match.end()] + guard + body[match.end():]
    text = text[:start] + body + text[end:]
    first = text.index("\n") + 1  # after #include "gta4_init.h"
    if "GTA4_StaleChildGuard(uint32_t" not in text:
        text = text[:first] + DECL + text[first:]
    open(path, "w", encoding="utf-8", newline="").write(text)
    print(f"{os.path.basename(path)}: sub_8297CE78 guard applied ({r24})")
    applied += 1
if not applied:
    sys.exit("sub_8297CE78 not found")

#!/usr/bin/env python3
"""Direct calls between recompiled functions (after nfsmw-nx's llamadas_directas.py).

DEFINE_REX_FUNC makes every sub_X a weak alias of __imp__sub_X so that a hook
(REX_HOOK_RAW) can replace it at link time. The generated code always calls
sub_X, and no compiler inlines through a weak alias, not even with LTO. This
rewrites `sub_X(ctx, base)` to `__imp__sub_X(ctx, base)` for every function
without a hook. The function table (indirect calls) keeps sub_X, so indirect
calls still reach hooks.

A function is hooked when its sub_X in the linked libmain.so is a strong
symbol, i.e. its address differs from __imp__sub_X. That list is kept in
direct_calls_hooked.txt next to this script. As in nfsmw-android and NFS
Carbon, a function whose address appears anywhere in the hand-written sources
(gta4-recomp/src, the SDK's src and include) or in gta4_config.toml is kept
indirect too: hooks are sometimes built from pieces of names or patched in by
address, which the nm list misses.

  direct_calls.py hooks <nm-output>   refresh the hooked list from `llvm-nm --defined-only libmain.so`
  direct_calls.py apply               rewrite the generated sources (idempotent)
  direct_calls.py undo                restore sub_X calls
  direct_calls.py check <nm-output>   fail if a hooked function is ever called directly

Run `apply` again after every code generation: it silently loses the rewrite.
"""
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
GENERATED = HERE.parent / "generated"
HOOKED = HERE / "direct_calls_hooked.txt"
SDK = HERE.parent.parent
MENTION_ROOTS = [HERE.parent / "src", SDK / "src", SDK / "include"]
MENTION_SUFFIXES = {".cpp", ".h", ".hpp", ".inc", ".c", ".in"}
ADDRESS = re.compile(r"(?<![0-9A-Fa-f])(82[0-9A-Fa-f]{6})(?![0-9A-Fa-f])")
CALL = re.compile(r"\b(__imp__)?(sub_[0-9A-F]{8})\(ctx, base\)")


def read_nm(path):
    imp, sub = {}, {}
    for line in open(path, encoding="utf-8", errors="replace"):
        parts = line.split()
        if len(parts) != 3:
            continue
        address, _, name = parts
        if re.fullmatch(r"__imp__sub_[0-9A-F]{8}", name):
            imp[name[7:]] = address
        elif re.fullmatch(r"sub_[0-9A-F]{8}", name):
            sub[name] = address
    return {name for name, address in sub.items() if imp.get(name) != address}


def sources():
    return sorted(GENERATED.glob("*.cpp"))


def mentioned():
    found = set()
    files = [HERE.parent / "gta4_config.toml"]
    for root in MENTION_ROOTS:
        files += [f for f in root.rglob("*") if f.suffix in MENTION_SUFFIXES and
                  "generated" not in f.parts and "thirdparty" not in f.parts]
    for f in files:
        found.update("sub_" + a.upper() for a in ADDRESS.findall(f.read_text(encoding="utf-8", errors="ignore")))
    return found


def rewrite(direct):
    hooked = set(HOOKED.read_text().split()) | (mentioned() if direct else set())
    total = changed = 0
    for path in sources():
        text = path.read_text(encoding="utf-8")

        def replace(match):
            nonlocal total
            name = match.group(2)
            total += 1
            prefix = "__imp__" if direct and name not in hooked else ""
            return f"{prefix}{name}(ctx, base)"

        new = CALL.sub(replace, text)
        if new != text:
            path.write_text(new, encoding="utf-8", newline="")
            changed += 1
    return total, changed


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "hooks":
        hooked = read_nm(sys.argv[2])
        HOOKED.write_text("\n".join(sorted(hooked)) + "\n")
        print(f"{len(hooked)} hooked functions")
    elif command in ("apply", "undo"):
        total, changed = rewrite(command == "apply")
        print(f"{total} calls, {changed} files rewritten")
    elif command == "check":
        hooked = read_nm(sys.argv[2])
        bad = set()
        for path in sources():
            for match in CALL.finditer(path.read_text(encoding="utf-8")):
                if match.group(1) and match.group(2) in hooked:
                    bad.add(match.group(2))
        if bad:
            print("hooked functions called directly (rerun `hooks` and `apply`):", *sorted(bad))
            sys.exit(1)
        print(f"ok: {len(hooked)} hooked functions, none called directly")
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main()

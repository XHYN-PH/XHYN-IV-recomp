#!/usr/bin/env python3
"""Audit the active Graine generated-code wiring, without claiming game completion.

Checks only the checked-in generator format. Native export wrappers and generic
stubs are inventoried separately; their presence does not prove functionality or
that a particular gameplay path reaches them. No game assets are required.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SDK_RELATIVE = Path("glue/rexglue-sdk-main")
REGISTER = re.compile(r"registrar->SetFunction\((0x[\dA-Fa-f]+),\s*(\w+)\)")
MAPPING = re.compile(r"\{\s*(0x[\dA-Fa-f]+),\s*(\w+)\s*\}")
DEFINITION = re.compile(r"^DEFINE_REX_FUNC\((\w+)\)", re.MULTILINE)
DECLARATION = re.compile(r"^DECLARE_REX_FUNC\((\w+)\)", re.MULTILINE)
CALL = re.compile(r"\b((?:sub_[\dA-F]+|__imp__\w+|rexcrt_\w+))\(ctx, base\)")
EXPORT = re.compile(r"^\s*(REX_EXPORT_STUB_RETURN|REX_EXPORT_STUB|REX_EXPORT|REX_HOOK_RAW)"
                    r"\(\s*(__imp__\w+)", re.MULTILINE)


def duplicate_values(values):
    return sorted(value for value, count in Counter(values).items() if count > 1)


def audit(root: Path) -> dict:
    sdk = root / SDK_RELATIVE
    generated = sdk / "gta4-recomp/generated"
    errors = []
    source_digest = hashlib.sha256()

    def read(path):
        data = path.read_bytes()
        source_digest.update(path.relative_to(root).as_posix().encode() + b"\0" + data)
        return data.decode("utf-8")

    register_text = read(generated / "gta4_register.cpp")
    mapping_text = read(generated / "gta4_init.cpp")
    header = read(generated / "gta4_init.h")
    sources = read(generated / "sources.cmake")
    registrations = [(int(address, 16), name) for address, name in REGISTER.findall(register_text)]
    mappings = [(int(address, 16), name) for address, name in MAPPING.findall(mapping_text)]
    names = {name for _, name in registrations}
    declarations = DECLARATION.findall(header)
    declared = set(declarations)
    units = sorted(generated.glob("gta4_recomp.*.cpp"))
    expected_units = set(re.findall(r"gta4_recomp\.\d+\.cpp", sources))
    actual_units = {path.name for path in units}
    if not registrations or not units:
        errors.append("Generated registrations and translation units must be nonempty")
    if expected_units != actual_units:
        errors.append("Generated source list differs from translation units: " +
                      str(sorted(expected_units ^ actual_units)))
    if Counter(registrations) != Counter(mappings):
        errors.append("Registrar and PPCFuncMappings disagree")
    for label, values in (("guest addresses", [a for a, _ in registrations]),
                          ("registered symbols", [n for _, n in registrations]),
                          ("declarations", declarations)):
        duplicates = duplicate_values(values)
        if duplicates:
            errors.append(f"Duplicate {label}: {duplicates}")
    if declared != names:
        errors.append("Declarations differ from registered symbols: " + str(sorted(declared ^ names)))

    bounds = {name: int(value, 16) for name, value in
              re.findall(r"#define REX_(CODE_BASE|CODE_SIZE)\s+(0x[\dA-Fa-f]+)", header)}
    if set(bounds) != {"CODE_BASE", "CODE_SIZE"}:
        errors.append("Missing generated code-range bounds")
    else:
        for address, name in registrations:
            if address % 4 or not bounds["CODE_BASE"] <= address < bounds["CODE_BASE"] + bounds["CODE_SIZE"]:
                errors.append(f"Unaligned or out-of-range binding: {address:08X} {name}")

    definitions = []
    calls = Counter()
    traps = Counter()
    for path in units:
        text = read(path)
        definitions.extend(DEFINITION.findall(text))
        calls.update(CALL.findall(text))
        for line in text.splitlines():
            if "__builtin_trap()" in line:
                reason = line.partition("//")[2].strip() or "unclassified"
                traps[reason] += 1
    defined = set(definitions)
    if not defined:
        errors.append("Generated function bodies must be nonempty")
    if duplicate_values(definitions):
        errors.append("Duplicate generated function bodies: " + str(duplicate_values(definitions)))
    if defined - names:
        errors.append("Unregistered generated bodies: " + str(sorted(defined - names)))
    guest_names = {name for name in names if name.startswith("sub_")}
    if guest_names - defined:
        errors.append("Missing generated bodies: " + str(sorted(guest_names - defined)))
    unresolved = set(calls) - names - {"__imp__" + name for name in defined}
    if unresolved:
        errors.append("Unregistered generated direct-call targets: " + str(sorted(unresolved)))

    imports = {name for name in names if name.startswith("__imp__")}
    unclassified = names - defined - imports - {name for name in names if name.startswith("rexcrt_")}
    if unclassified:
        errors.append("Unclassified registered symbols: " + str(sorted(unclassified)))
    exports = {}
    wide_imports = []
    for path in sorted((sdk / "src/kernel").rglob("*.cpp")):
        text = read(path)
        for match in EXPORT.finditer(text):
            macro, name = match.groups()
            if name not in imports:
                continue
            exports.setdefault(name, []).append({
                "kind": macro,
                "source": path.relative_to(root).as_posix(),
                "line": text.count("\n", 0, match.start()) + 1,
            })
        for name, target in re.findall(r"REX_EXPORT\(\s*(__imp__\w+),\s*([\w:]+)\s*\)", text):
            if name not in imports:
                continue
            target = target.rsplit("::", 1)[-1]
            signature = re.search(r"\b" + re.escape(target) + r"\s*\(([^{};]*)\)\s*\{", text)
            if signature and re.search(r"\b(?:u64|i64|uint64_t|int64_t)\s+\w+", signature[1]):
                wide_imports.append({"symbol": name, "arguments": " ".join(signature[1].split())})
    if imports - exports.keys():
        errors.append("Unclassified kernel import bindings: " + str(sorted(imports - exports.keys())))
    stubs = []
    for name, entries in sorted(exports.items()):
        for entry in entries:
            if entry["kind"].startswith("REX_EXPORT_STUB"):
                stubs.append({"symbol": name, "generated_direct_calls": calls[name], **entry})
    return {
        "scope": "active Graine generated-source consistency and kernel export inventory",
        "source_sha256": source_digest.hexdigest(),
        "consistency_passed": not errors,
        "counts": {"translation_units": len(units), "generated_function_bodies": len(definitions),
                   "registered_addresses": len(registrations), "kernel_imports": len(imports),
                   "native_crt_bindings": sum(n.startswith("rexcrt_") for n in names),
                   "generic_stub_bindings": len(stubs)},
        "errors": errors,
        "generic_stubs": stubs,
        "imports_with_64_bit_integer_parameters": sorted(wide_imports, key=lambda x: x["symbol"]),
        "generated_traps_by_reason": dict(sorted(traps.items())),
        "limitations": [
            "Consistency does not establish binary-analysis coverage or full-game correctness.",
            "Native export wrappers may contain partial implementations; stub count is not remaining work.",
            "Stub reachability, missions, rendering, audio and device compatibility require separate validation.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--json", type=Path, help="Write the complete inventory to this file")
    args = parser.parse_args()
    try:
        report = audit(args.root.resolve())
    except (OSError, UnicodeError) as error:
        parser.exit(1, f"Cannot audit generated source: {error}\n")
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    counts = report["counts"]
    print(f"Generated wiring: {'PASS' if report['consistency_passed'] else 'FAIL'}; "
          f"{counts['generated_function_bodies']:,} bodies, "
          f"{counts['registered_addresses']:,} bindings, {counts['kernel_imports']} imports")
    print(f"Generic stub bindings requiring triage: {counts['generic_stub_bindings']}")
    print("This source audit does not establish recompilation or gameplay completion.")
    for error in report["errors"]:
        print("ERROR:", error)
    return 0 if report["consistency_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())

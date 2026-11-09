#!/usr/bin/env python3
"""Translate the pinned v442 UI in place and expose its original ISO/TU installer."""
from __future__ import annotations

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from preflight import APP_ID, BASE_SHA256, dex_contract, digest, file_digest, signature_entry, validate_base

ROOT = Path(__file__).resolve().parent
VERSION_CODE = 444
VERSION_NAME = "0.9.24-xhyn-base-english-test2"
PROVENANCE = "assets/xhyn-base-ui/build.json"
ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])
LITERAL = re.compile(r'(const-string(?:/jumbo)?\s+[vp]\d+,\s*)("(?:[^"\\]|\\.)*")')
CYRILLIC = re.compile(r'[А-Яа-яЁё]')
DEFAULTS = {"assets/default_args.txt", "assets/default_args_mali.txt", "assets/default_args_g99.txt"}
MAIN = "lib/arm64-v8a/libmain.so"
# Both strings are NUL-terminated ImGui text, with null end pointers at
# ContextTouchOverlay::OnDraw (0xc48188/0xc481dc, 0xc4859c/0xc485ec).
# Only these .rodata bytes change: no ARM64 instructions, relocations or sizes.
HINTS = ((0x3f7130, "Карта: перетаскивание / масштаб двумя пальцами", "MAP: DRAG / PINCH TO ZOOM"),
         (0x4066b8, "Пропустить", "SKIP"))
CHANGED = {"AndroidManifest.xml", "classes.dex", MAIN, *DEFAULTS}


def replace_once(text: str, old: str, new: str) -> str:
    if text.count(old) != 1:
        raise ValueError(f"Patch anchor must occur exactly once: {old[:120]!r}")
    return text.replace(old, new, 1)


def java_hash(text: str) -> int:
    result = 0
    raw = text.encode("utf-16-be")
    for i in range(0, len(raw), 2):
        result = (31 * result + int.from_bytes(raw[i:i+2], "big")) & 0xffffffff
    return result - (1 << 32) if result >= (1 << 31) else result


def decode_literal(literal: str) -> str:
    # Smali accepts escaped apostrophes in double-quoted strings; JSON does not.
    return json.loads(re.sub(r"(?<!\\)((?:\\\\)*)\\'", r"\1'", literal))


def translate_tree(decoded: Path) -> dict:
    catalog = json.loads((ROOT / "base-ui/english.json").read_text())
    if not catalog or any(not CYRILLIC.search(k) or not v or CYRILLIC.search(v) for k, v in catalog.items()):
        raise ValueError("Invalid English catalog")
    seen = Counter()
    for path in sorted((decoded / "smali").rglob("*.smali")):
        source = path.read_text()
        # The stock string switches dispatch on ASCII CVar/enum values. Fail if
        # future changes introduce translated switch keys requiring new hashes.
        for method in source.split(".end method"):
            if "Ljava/lang/String;->hashCode()I" not in method:
                continue
            hashes = {int(m, 16) if not m.startswith("-") else -int(m[1:], 16)
                      for m in re.findall(r'^\s*(-?0x[0-9a-f]+)\s*->', method, re.M)}
            for match in LITERAL.finditer(method):
                value = decode_literal(match[2])
                if value in catalog and java_hash(value) in hashes:
                    raise ValueError("Translated hash-switch key needs explicit handling")

        def translate(match):
            value = decode_literal(match[2])
            if CYRILLIC.search(value):
                if value not in catalog:
                    raise ValueError(f"Untranslated string in {path}: {value!r}")
                seen[value] += 1
                return match[1] + json.dumps(catalog[value], ensure_ascii=False)
            return match[0]

        path.write_text(LITERAL.sub(translate, source))
    if set(seen) != set(catalog):
        raise ValueError(f"Unused catalog entries: {sorted(set(catalog) - set(seen))}")
    for path in (decoded / "res").rglob("*.xml"):
        if CYRILLIC.search(path.read_text()):
            raise ValueError(f"Untranslated resource: {path}")
    return {"unique_strings": len(seen), "literal_occurrences": sum(seen.values())}


def patch_ui(decoded: Path):
    activity = decoded / "smali/com/libertyrecomp/LauncherActivity.smali"
    text = activity.read_text()
    anchor = ".method public final D(Ljava/lang/String;)V\n    .locals 8\n"
    # Expand the original form on every status refresh, including installed data.
    # Stock loading, busy-state, native validation and picker callbacks remain.
    text = replace_once(text, anchor, anchor + "\n    const/4 v0, 0x1\n    iput-boolean v0, p0, Lcom/libertyrecomp/LauncherActivity;->s:Z\n")
    anchor = "    iput-object v1, v9, Lcom/libertyrecomp/LauncherActivity;->a:Ljava/io/File;"
    text = replace_once(text, anchor, anchor + "\n\n    invoke-static {v1}, Lcom/libertyrecomp/LauncherActivity;->xhynEnsureEnglish(Ljava/io/File;)V")
    # v442 constructs the installer but omits it from the game-data view.
    # Insert it into that existing scrollable card after the folder picker.
    text = replace_once(text, "    new-instance v12, La/P;", """    iget-object v0, v9, Lcom/libertyrecomp/LauncherActivity;->A:Landroid/widget/LinearLayout;
    invoke-static {v13}, Lcom/libertyrecomp/LauncherActivity;->m(I)Landroid/widget/LinearLayout$LayoutParams;
    move-result-object v2
    invoke-virtual {v11, v0, v2}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    new-instance v12, La/P;""")
    text = replace_once(text, '"Base game (disc image .iso)"', '"Game disc image (.iso)"')
    text = replace_once(text, '"Title Update 8"', '"Title Update 8 (TU8)"')
    text = replace_once(text, '"Alternative: Install from ISO/TU or add episodes"', '"Install from ISO / Title Update"')
    text = replace_once(text, '"Offline Cache Mode: When the pre-baked game cache is placed in Android/data/"', '"Existing game data can be loaded from an extracted folder at Android/data/"')
    text = replace_once(text, '"/files/LibertyRecomp/game/, it is loaded directly. Neither ISO nor Title Update files are needed on-device."', '"/files/LibertyRecomp/game/. To install instead, select the game ISO and Title Update 8 above. Episodes are optional."')
    text += "\n" + (ROOT / "base-ui/ensure_english.smali").read_text()
    activity.write_text(text)
    helper = decoded / "smali/a/i.smali"
    text = helper.read_text()
    # Keep all six language choices. Change only the selected default.
    pattern = r'(const-string v5, "user_language"\s+const-string v6, "Game language"\s+const-string v7, )"4"'
    text, count = re.subn(pattern, r'\g<1>"0"', text)
    if count != 1:
        raise ValueError("Language setting default anchor changed")
    start = text.index(".method public static n(Ljava/io/File;)V")
    end = text.index(".end method", start)
    text = text[:start] + replace_once(text[start:end], 'const-string v2, "4"', 'const-string v2, "0"') + text[end:]
    helper.write_text(text)


def english_args(data: bytes) -> bytes:
    result, count = re.subn(rb'(?m)^--user_language=4(?=\r?$)', b'--user_language=0', data)
    if count != 1:
        raise ValueError("Unexpected default-args language")
    return result


def english_hints(data: bytes) -> bytes:
    result = bytearray(data)
    for offset, old, new in HINTS:
        source, target = old.encode(), new.encode()
        if data[offset:offset+len(source)+1] != source + b'\0' or len(target) > len(source):
            raise ValueError("Native hint anchor changed")
        result[offset:offset+len(source)] = target.ljust(len(source), b'\0')
    return bytes(result)


def elf_sections(data: bytes) -> dict[str, tuple[int, bytes]]:
    if data[:6] != b'\x7fELF\x02\x01':
        raise ValueError("Expected little-endian ELF64")
    start, = struct.unpack_from('<Q', data, 40)
    size, count, strings = struct.unpack_from('<HHH', data, 58)
    names_at, names_len = struct.unpack_from('<QQ', data, start + strings * size + 24)
    names = data[names_at:names_at+names_len]
    sections = {}
    for i in range(count):
        name, kind, flags, address, offset, length = struct.unpack_from('<IIQQQQ', data, start + i * size)
        name = names[name:names.index(b'\0', name)].decode()
        sections[name] = (flags, data[offset:offset+length] if kind != 8 else b'')
    return sections


def verify_candidate(base: Path, candidate: Path) -> dict:
    report = validate_base(base)
    with zipfile.ZipFile(base) as stock, zipfile.ZipFile(candidate) as built:
        if len(built.namelist()) != len(set(built.namelist())) or built.testzip():
            raise ValueError("Candidate ZIP integrity failure")
        expected = {n for n in stock.namelist() if not signature_entry(n)} | {PROVENANCE}
        if {n for n in built.namelist() if not signature_entry(n)} != expected:
            raise ValueError("Unexpected candidate file set (replacement UI or missing payload)")
        preserved = 0
        for name in stock.namelist():
            if signature_entry(name) or name in CHANGED:
                continue
            if stock.read(name) != built.read(name):
                raise ValueError(f"Unexpected payload change: {name}")
            preserved += 1
        for name in DEFAULTS:
            if built.read(name) != english_args(stock.read(name)):
                raise ValueError(f"Unexpected argument changes: {name}")
        if built.read(MAIN) != english_hints(stock.read(MAIN)):
            raise ValueError("Native changes exceed the two UI hints")
        before, after = elf_sections(stock.read(MAIN)), elf_sections(built.read(MAIN))
        for name, (flags, data) in before.items():
            if name != '.rodata' and after[name] != (flags, data):
                raise ValueError(f"ELF section changed: {name}")
        base_methods, base_fields = dex_contract(stock.read("classes.dex"))
        methods, fields = dex_contract(built.read("classes.dex"))
        if fields != base_fields or set(methods) != set(base_methods):
            raise ValueError("Stock UI classes/fields changed")
        owner = "Lcom/libertyrecomp/LauncherActivity;"
        extra = "xhynEnsureEnglish(Ljava/io/File;)V"
        migration_access = methods[owner].pop(extra, None)
        if migration_access != 0xa or methods != base_methods:
            raise ValueError("Unexpected stock UI method/interface changes")
        report.update(candidate_sha256=file_digest(candidate), candidate_bytes=candidate.stat().st_size,
                      preserved_entries=preserved, native_libraries_unchanged=11,
                      native_executable_sections="all byte-for-byte unchanged",
                      native_hint_strings_translated=2, replacement_ui="absent",
                      translated_dex_sha256=digest(built.read("classes.dex")),
                      candidate_native_libraries={n: digest(built.read(n)) for n in report['native_libraries']})
    return report


def run(args, log: Path):
    with log.open("ab") as output:
        subprocess.run([str(a) for a in args], stdout=output, stderr=subprocess.STDOUT, check=True)


def build(base: Path, apktool: Path, sdk: Path, out: Path):
    validate_base(base)
    out.mkdir(parents=True, exist_ok=True)
    work, log = out / "work", out / "build.log"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir()
    log.write_text("")
    java = Path(os.environ.get("JAVA_HOME", "/usr")) / "bin/java"
    decoded, compiled = work / "stock", work / "compiled.apk"
    run([java, "-jar", apktool, "d", "-f", "-o", decoded, base], log)
    translation = translate_tree(decoded)
    patch_ui(decoded)
    manifest_path = decoded / "AndroidManifest.xml"
    tree = ET.parse(manifest_path)
    root = tree.getroot()
    root.set("package", APP_ID)
    app = root.find("application")
    if app is None or app.get(ANDROID + "name"):
        raise ValueError("Unexpected stock Application")
    app.set(ANDROID + "label", "XHYN IV")
    app.set(ANDROID + "debuggable", "false")
    tree.write(manifest_path, encoding="utf-8", xml_declaration=True)
    config = decoded / "apktool.yml"
    source = replace_once(config.read_text(), "versionCode: 442", f"versionCode: {VERSION_CODE}")
    config.write_text(replace_once(source, "versionName: 0.9.24-renderer-v442", f"versionName: {VERSION_NAME}"))
    for name in ("lib", "assets", "original", "unknown"):
        if (decoded / name).is_dir():
            shutil.rmtree(decoded / name)
    run([java, "-jar", apktool, "b", "-f", "-o", compiled, decoded], log)
    provenance = {"base_sha256": BASE_SHA256, "version_code": VERSION_CODE, "version_name": VERSION_NAME,
                  "application_id": APP_ID, "apktool_sha256": file_digest(apktool),
                  "translation_catalog_sha256": file_digest(ROOT / 'base-ui/english.json'),
                  "builder_sha256": file_digest(Path(__file__)), "translation": translation,
                  "scope": "original UI in English; visible original ISO/TU installer; initial English game language",
                  "gameplay_validation": "requires Android device"}
    unaligned = work / "unaligned.apk"
    with zipfile.ZipFile(base) as stock, zipfile.ZipFile(compiled) as assembled, zipfile.ZipFile(unaligned, "w") as output:
        for entry in stock.infolist():
            name = entry.filename
            if signature_entry(name):
                continue
            data = stock.read(entry)
            if name in {"AndroidManifest.xml", "classes.dex"}:
                data = assembled.read(name)
            elif name in DEFAULTS:
                data = english_args(data)
            elif name == MAIN:
                data = english_hints(data)
            output.writestr(entry, data, compresslevel=9 if entry.compress_type == zipfile.ZIP_DEFLATED else None)
        output.writestr(PROVENANCE, json.dumps(provenance, indent=2) + "\n", compress_type=zipfile.ZIP_DEFLATED)
    apk = out / f"XHYN-IV-{VERSION_NAME}-unsigned.apk"
    zipalign = sdk / "build-tools/35.0.0/zipalign"
    run([zipalign, "-P", "16", "-f", "4", unaligned, apk], log)
    run([zipalign, "-c", "-P", "16", "4", apk], log)
    report = verify_candidate(base, apk)
    report["provenance"] = provenance
    (out / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"apk": str(apk), "bytes": apk.stat().st_size, "sha256": file_digest(apk), "translation": translation}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("base", "apktool", "sdk", "out"):
        parser.add_argument(f"--{name}", type=Path, required=True)
    args = parser.parse_args()
    build(*(getattr(args, name).resolve() for name in ("base", "apktool", "sdk", "out")))


if __name__ == "__main__":
    main()

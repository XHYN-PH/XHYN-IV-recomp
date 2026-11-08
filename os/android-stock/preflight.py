#!/usr/bin/env python3
"""Validate the exact stock base, reflective ABI, and byte-preservation boundary."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

BASE_SHA256 = "ae71035bbbed76c7bab9e55faca629e7524cd9fe22457c8fce15c04284a0b049"
VERSION_CODE = 443
VERSION_NAME = "0.9.24-xhyn-layer-test1"
APP_ID = "com.xhyn.ivrecomp"
APPLICATION = "com.xhyn.stock.LayerApplication"

CONTRACT = {
    "La/i;": {
        "E(Landroid/content/Context;Ljava/io/File;)Ljava/util/LinkedHashMap;",
        "J(Ljava/io/File;Ljava/util/Map;)V",
        "q(Landroid/content/Context;Ljava/io/File;)Ljava/io/File;",
        "m(Ljava/io/File;Ljava/util/ArrayList;)Ljava/io/File;",
        "i()La/d;",
    },
    "La/d;": {"a()Z"},
    "Lcom/libertyrecomp/LauncherActivity;": {"o()V"},
    "Lcom/libertyrecomp/LibertyActivity;": {"d(Ljava/io/File;)[Ljava/lang/String;"},
    "Lcom/libertyrecomp/Installer;": {"c(Landroid/content/Context;)Z"},
    "Lcom/libertyrecomp/TouchSettings;": {
        "nativeOpen(Ljava/lang/String;)V", "nativeGet(Ljava/lang/String;)Ljava/lang/String;",
        "nativeSet(Ljava/lang/String;Ljava/lang/String;)Z", "nativeLayout(IFFF)Ljava/lang/String;",
        "nativeMove(IFFF)Z", "nativeReset(Z)V", "nativeSave()Z",
    },
}
FIELDS = {"La/i;": {"b:[La/l;"}, "La/l;": {"a:Ljava/lang/String;", "c:Ljava/lang/String;"}, "La/d;": {"a:I"}}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def uleb(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    for shift in range(0, 35, 7):
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
    raise ValueError("Malformed DEX LEB128")


def dex_contract(data: bytes) -> tuple[dict, dict]:
    """Read actual defined members, not only method-name string references."""
    if not data.startswith(b"dex\n") or len(data) < 112:
        raise ValueError("Invalid stock DEX")
    string_count, strings_at, type_count, types_at, proto_count, protos_at, field_count, fields_at, method_count, methods_at, class_count, classes_at = struct.unpack_from("<12I", data, 56)
    strings = []
    for index in range(string_count):
        offset, = struct.unpack_from("<I", data, strings_at + 4 * index)
        _, offset = uleb(data, offset)
        strings.append(data[offset:data.index(b"\0", offset)].decode("utf-8", "replace"))
    types = [strings[struct.unpack_from("<I", data, types_at + 4 * index)[0]] for index in range(type_count)]
    protos = []
    for index in range(proto_count):
        _, ret, params = struct.unpack_from("<3I", data, protos_at + 12 * index)
        count = struct.unpack_from("<I", data, params)[0] if params else 0
        args = struct.unpack_from(f"<{count}H", data, params + 4) if count else []
        protos.append("(" + "".join(types[arg] for arg in args) + ")" + types[ret])
    method_ids = [struct.unpack_from("<HHI", data, methods_at + 8 * index) for index in range(method_count)]
    field_ids = [struct.unpack_from("<HHI", data, fields_at + 8 * index) for index in range(field_count)]
    methods, fields = {}, {}
    for index in range(class_count):
        owner, = struct.unpack_from("<I", data, classes_at + 32 * index)
        offset, = struct.unpack_from("<I", data, classes_at + 32 * index + 24)
        if not offset:
            continue
        counts = []
        for _ in range(4):
            count, offset = uleb(data, offset)
            counts.append(count)
        for count in counts[:2]:
            member_index = 0
            for _ in range(count):
                delta, offset = uleb(data, offset)
                access, offset = uleb(data, offset)
                member_index += delta
                _, typ, name = field_ids[member_index]
                fields.setdefault(types[owner], {})[strings[name] + ":" + types[typ]] = access
        for count in counts[2:]:
            member_index = 0
            for _ in range(count):
                delta, offset = uleb(data, offset)
                access, offset = uleb(data, offset)
                _, offset = uleb(data, offset)
                member_index += delta
                _, proto, name = method_ids[member_index]
                methods.setdefault(types[owner], {})[strings[name] + protos[proto]] = access
    return methods, fields


def validate_base(path: Path) -> dict:
    if file_digest(path) != BASE_SHA256:
        raise ValueError("Base APK SHA-256 does not match the inspected v442 build")
    with zipfile.ZipFile(path) as archive:
        if len(set(archive.namelist())) != len(archive.namelist()):
            raise ValueError("Duplicate base ZIP entries")
        if archive.testzip() is not None:
            raise ValueError("Base ZIP CRC failure")
        methods, fields = dex_contract(archive.read("classes.dex"))
        for expected, actual in ((CONTRACT, methods), (FIELDS, fields)):
            for owner, members in expected.items():
                missing = members - actual.get(owner, {}).keys()
                if missing:
                    raise ValueError(f"Stock interface missing: {owner} {sorted(missing)}")
        native = {name: digest(archive.read(name)) for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")}
        if len(native) != 12:
            raise ValueError("Expected 12 stock native libraries")
        return {"base_sha256": BASE_SHA256, "base_entries": len(archive.namelist()), "native_libraries": native, "stock_dex": digest(archive.read("classes.dex")), "adapter_contract": "passed"}


def signature_entry(name: str) -> bool:
    return name.upper().startswith("META-INF/") and (name.upper().endswith((".RSA", ".DSA", ".EC", ".SF")) or name.upper() == "META-INF/MANIFEST.MF")


def verify_candidate(base: Path, candidate: Path) -> dict:
    report = validate_base(base)
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(candidate) as built:
        if len(set(built.namelist())) != len(built.namelist()):
            raise ValueError("Duplicate candidate ZIP entries")
        if built.testzip() is not None:
            raise ValueError("Candidate ZIP CRC failure")
        preserved = 0
        for entry in original.infolist():
            if entry.filename == "AndroidManifest.xml" or signature_entry(entry.filename):
                continue
            if entry.filename not in built.namelist() or digest(original.read(entry)) != digest(built.read(entry.filename)):
                raise ValueError(f"Stock payload changed: {entry.filename}")
            preserved += 1
        extra = set(built.namelist()) - set(original.namelist())
        allowed = {"classes2.dex", "assets/xhyn-layer/build.json"}
        if any(name not in allowed and not signature_entry(name) for name in extra):
            raise ValueError(f"Unexpected package entries: {sorted(extra - allowed)}")
        if "classes2.dex" not in built.namelist():
            raise ValueError("Android layer DEX is missing")
        layer, _ = dex_contract(built.read("classes2.dex"))
        if "Lcom/xhyn/stock/LayerApplication;" not in layer:
            raise ValueError("Layer application is missing")
        if any(name in layer for name in ("Lcom/libertyrecomp/LibertyActivity;", "Lcom/libertyrecomp/LauncherActivity;")):
            raise ValueError("Layer duplicates stock Activity classes")
        report.update(candidate_sha256=file_digest(candidate), candidate_bytes=candidate.stat().st_size, preserved_entries=preserved, native_and_stock_payload="byte-for-byte unchanged")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--candidate", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = verify_candidate(args.base, args.candidate) if args.candidate else validate_base(args.base)
    text = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.write_text(text)
    print(text, end="")


if __name__ == "__main__":
    main()

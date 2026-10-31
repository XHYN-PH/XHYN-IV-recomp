"""Compare decrypted TU8 executable metadata and direct calls with generated code.

An uncompressed XEX contains an image laid out at section VirtualAddress values;
PE PointerToRawData values describe the original file layout and are not used.
This checks known entry points and call wiring, not full runtime correctness.
"""
from collections import Counter
import hashlib
from pathlib import Path
import struct


def audit_binary(path: Path, registered: set[int], recorded_calls: dict[int, int]) -> dict:
    with path.open("rb") as stream:
        data = stream.read(64 * 1024 * 1024 + 1)
    if len(data) > 64 * 1024 * 1024:
        raise ValueError("Executable exceeds the 64 MiB inspection limit")

    def require(condition, message):
        if not condition:
            raise ValueError(message)

    def inside(offset, length, total):
        return 0 <= offset <= total and 0 <= length <= total - offset

    def be32(offset):
        return struct.unpack_from(">I", data, offset)[0]

    require(len(data) >= 24 and data[:4] == b"XEX2", "Expected an XEX2 executable")
    header_size, count = be32(8), be32(20)
    require(24 <= header_size <= len(data) and count <= 4096 and
            count * 8 <= header_size - 24, "Invalid XEX optional-header table")
    options = {}
    for index in range(count):
        key, value = struct.unpack_from(">II", data, 24 + index * 8)
        require(key not in options, "Duplicate XEX optional header")
        options[key] = value
    require(0x3FF in options and inside(options[0x3FF], 8, header_size),
            "Missing XEX file-format information")
    encryption, compression = struct.unpack_from(">HH", data, options[0x3FF] + 4)
    require(encryption == 0 and compression == 0,
            "Prepare a decrypted, uncompressed TU8 XEX before auditing")
    require(0x40006 in options and inside(options[0x40006], 24, header_size),
            "Missing XEX execution information")
    execution = options[0x40006]
    version, title = be32(execution + 4), be32(execution + 12)
    require(title == 0x545407F2 and version == 0x805,
            "Executable does not match GTA IV TU8 (0.0.8.5)")

    image = memoryview(data)[header_size:]
    require(len(image) >= 64 and image[:2] == b"MZ", "Missing decoded PE image")
    nt = struct.unpack_from("<I", image, 0x3C)[0]
    require(inside(nt, 24, len(image)) and image[nt:nt + 4] == b"PE\0\0",
            "Invalid PE signature")
    machine, section_count = struct.unpack_from("<HH", image, nt + 4)
    optional_size = struct.unpack_from("<H", image, nt + 20)[0]
    optional = nt + 24
    require(machine == 0x1F2 and 0 < section_count <= 96 and optional_size >= 128 and
            inside(optional, optional_size + section_count * 40, len(image)),
            "Invalid Xbox PE header or section table")
    require(struct.unpack_from("<H", image, optional)[0] == 0x10B, "Expected PE32 image")
    base = struct.unpack_from("<I", image, optional + 28)[0]
    image_size = struct.unpack_from("<I", image, optional + 56)[0]
    require(base + image_size <= 1 << 32, "PE image exceeds the guest address range")
    sections = []
    rdata = None
    for index in range(section_count):
        offset = optional + optional_size + index * 40
        name = bytes(image[offset:offset + 8]).split(b"\0")[0].decode("ascii", errors="replace")
        size, rva = struct.unpack_from("<II", image, offset + 8)
        flags = struct.unpack_from("<I", image, offset + 36)[0]
        require(inside(rva, size, image_size), "Section exceeds the PE image size")
        if name == ".rdata" and inside(rva, size, len(image)):
            rdata = (base + rva, base + rva + size)
        if flags & 0x20000000:
            require(inside(rva, size, len(image)), "Executable section exceeds decoded data")
            sections.append({"name": name, "start": base + rva, "end": base + rva + size,
                             "rva": rva, "size": size})
    require(bool(sections), "No executable sections")
    sections.sort(key=lambda section: section["start"])
    require(all(a["end"] <= b["start"] for a, b in zip(sections, sections[1:])),
            "Overlapping executable sections")

    def executable(address, length=4):
        return any(s["start"] <= address and address + length <= s["end"] for s in sections)

    pdata_rva, pdata_size = struct.unpack_from("<II", image, optional + 96 + 3 * 8)
    require(pdata_size > 0 and pdata_size % 8 == 0 and
            inside(pdata_rva, pdata_size, len(image)), "Invalid exception DataDirectory")
    pdata = []
    function_ranges = []
    for begin, packed in struct.iter_unpack(">II", image[pdata_rva:pdata_rva + pdata_size]):
        length = ((packed >> 8) & 0x3FFFFF) * 4 or 4
        require(begin % 4 == 0 and executable(begin, length),
                "Exception entry lies outside executable sections")
        pdata.append(begin)
        function_ranges.append((begin, begin + length, bool(packed & 0x80000000)))

    def exception_context(pc):
        # Inventory the original scope metadata; this does not implement unwind.
        owner = next((f for f in function_ranges if f[0] <= pc < f[1]), None)
        if not owner or not owner[2] or not rdata or not inside(owner[0] - base - 8, 8, len(image)):
            return None
        thunk, table = struct.unpack_from(">II", image, owner[0] - base - 8)
        if not rdata[0] <= table <= rdata[1] - 4:
            return None
        count = struct.unpack_from(">I", image, table - base)[0]
        if not 0 < count <= 64 or table + 4 + count * 16 > rdata[1]:
            return None
        scopes = []
        for index in range(count):
            values = struct.unpack_from(">IIII", image, table - base + 4 + index * 16)
            scopes.append(dict(zip(("try_start", "try_end", "filter", "handler"),
                                   (f"0x{value:08X}" for value in values))))
        return {"function": f"0x{owner[0]:08X}", "handler_thunk": f"0x{thunk:08X}",
                "scope_table": f"0x{table:08X}", "scopes": scopes}

    def direct_bl(word, pc):
        if word >> 26 != 18 or not word & 1:
            return None
        displacement = word & 0x03FFFFFC
        if displacement & 0x02000000:
            displacement -= 0x04000000
        return (displacement if word & 2 else pc + displacement) & 0xFFFFFFFF

    targets = Counter()
    direct_sites = {}
    outside = 0
    pc_captures = 0
    for section in sections:
        rva, size = section["rva"], section["size"]
        for index, (word,) in enumerate(struct.iter_unpack(">I", image[rva:rva + size // 4 * 4])):
            pc = section["start"] + index * 4
            target = direct_bl(word, pc)
            if target is None:
                continue
            if target == pc + 4:
                pc_captures += 1
            elif executable(target):
                targets[target] += 1
                direct_sites[pc] = target
            else:
                outside += 1

    mismatched = []
    for pc, target in recorded_calls.items():
        if not executable(pc):
            mismatched.append({"pc": f"0x{pc:08X}", "reason": "outside executable sections"})
            continue
        word = struct.unpack_from(">I", image, pc - base)[0]
        actual = direct_bl(word, pc)
        if actual != target:
            mismatched.append({"pc": f"0x{pc:08X}", "generated_target": f"0x{target:08X}",
                               "binary_target": None if actual is None else f"0x{actual:08X}"})

    missing_pdata = sorted(set(pdata) - registered)
    missing_calls = sorted(targets.keys() - registered)
    errors = []
    if missing_pdata:
        errors.append(f"{len(missing_pdata)} exception entry points lack generated bindings")
    if missing_calls:
        errors.append(f"{len(missing_calls)} in-code BL targets lack generated bindings")
    if mismatched:
        errors.append(f"{len(mismatched)} generated BL call sites disagree with binary instructions")
    return {
        "consistency_passed": not errors,
        "errors": errors,
        "prepared_xex_sha256": hashlib.sha256(data).hexdigest(),
        "title_id": f"0x{title:08X}", "version": "0.0.8.5",
        "layout": "decrypted XEX image; PE VirtualAddress values",
        "pdata_entries": len(pdata), "pdata_unique_starts": len(set(pdata)),
        "missing_pdata_bindings": [f"0x{address:08X}" for address in missing_pdata[:100]],
        "unique_in_code_bl_targets": len(targets), "in_code_bl_instructions": sum(targets.values()),
        "missing_bl_bindings": [f"0x{address:08X}" for address in missing_calls[:100]],
        "pc_capture_bl_instructions": pc_captures,
        "outside_code_bl_candidates": outside,
        "generated_bl_sites_checked": len(recorded_calls),
        "unannotated_bl_candidates": [
            {"pc": f"0x{pc:08X}", "target": f"0x{target:08X}",
             "exception_scope_context": exception_context(pc)}
            for pc, target in direct_sites.items() if pc not in recorded_calls][:100],
        "unannotated_bl_candidate_count": len(direct_sites.keys() - recorded_calls.keys()),
        "generated_bl_site_mismatches": mismatched[:100],
        "code_section_sha256": {s["name"]: hashlib.sha256(
            image[s["rva"]:s["rva"] + s["size"]]).hexdigest() for s in sections},
        "limitations": [
            "Metadata and direct BL wiring do not establish instruction semantics or mission coverage.",
            "Indirect calls, conditional linked branches and data-like BL candidates need separate analysis.",
        ],
    }

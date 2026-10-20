"""Replaces the SPIR-V of shaders in the checked-in shader cache with a new
XenosRecomp translation, matched by shader hash.

The checked-in LibertyRecompLib/shader/shader_cache.cpp has entries that the
local shader sources cannot reproduce (runtime_captured, from recovered
sources), so a new translation cannot replace the file. This keeps every
entry, its filename, masks and the Metal (AIR) cache, and takes the SPIR-V
(both variants) from the new translation for every hash it contains. A hash
whose spec-constant or texture mask differs is refused: the runtime binds by
those masks.

usage: merge_shader_cache.py <checked-in cache.cpp> <new translation cache.cpp> <out cache.cpp>
Needs the zstandard module (pip install zstandard).
"""
import re
import sys

import zstandard

ENTRY = re.compile(r'^\t\{ (0x[0-9A-F]+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+), '
                   r'"([^"]*)", nullptr, (\d+) \},\s*$')


def load(path):
    text = open(path, encoding="utf-8").read()
    entries = []
    for line in text.split("\n"):
        m = ENTRY.match(line)
        if m:
            g = m.groups()
            entries.append({"hash": int(g[0], 16), "dxil": (int(g[1]), int(g[2])),
                            "spirv": (int(g[3]), int(g[4])), "late": (int(g[5]), int(g[6])),
                            "air": (int(g[7]), int(g[8])), "spec": int(g[9]), "filename": g[10],
                            "tex": int(g[11])})
    m = re.search(r"const uint8_t g_compressedSpirvCache\[\] = \{([^}]*)\};", text)
    size = int(re.search(r"g_spirvCacheDecompressedSize = (\d+);", text).group(1))
    raw = bytes(int(x) for x in m.group(1).split(",") if x.strip())
    spirv = zstandard.ZstdDecompressor().decompress(raw, max_output_size=size)
    assert len(spirv) == size, path
    return text, entries, spirv


def take(blob, span):
    return blob[span[0]:span[0] + span[1]]


base_text, base, base_spirv = load(sys.argv[1])
_, new, new_spirv = load(sys.argv[2])
new_by_hash = {e["hash"]: e for e in new}

spirv = bytearray()
lines = []
replaced = 0
for e in base:
    source, blob = e, base_spirv
    other = new_by_hash.get(e["hash"])
    if other:
        if other["spec"] != e["spec"] or other["tex"] != e["tex"]:
            sys.exit("0x%X: masks differ (spec %d/%d, tex %d/%d)" %
                     (e["hash"], e["spec"], other["spec"], e["tex"], other["tex"]))
        source, blob = other, new_spirv
        replaced += 1
    main = take(blob, source["spirv"])
    late = take(blob, source["late"])
    main_offset = len(spirv) if main else 0
    spirv += main
    late_offset = len(spirv) if late else 0
    spirv += late
    lines.append('\t{ 0x%X, %d, %d, %d, %d, %d, %d, %d, %d, %d, "%s", nullptr, %d },' %
                 (e["hash"], e["dxil"][0], e["dxil"][1], main_offset, len(main), late_offset, len(late),
                  e["air"][0], e["air"][1], e["spec"], e["filename"], e["tex"]))

compressed = zstandard.ZstdCompressor(level=22).compress(bytes(spirv))

text = base_text
start = text.index("ShaderCacheEntry g_shaderCacheEntries[] = {\n") + len("ShaderCacheEntry g_shaderCacheEntries[] = {\n")
end = text.index("\n};", start)
text = text[:start] + "\n".join(lines) + text[end:]
text = re.sub(r"const uint8_t g_compressedSpirvCache\[\] = \{[^}]*\};",
              lambda _: "const uint8_t g_compressedSpirvCache[] = {%s};" % ",".join(map(str, compressed)), text)
text = re.sub(r"g_spirvCacheCompressedSize = \d+;", "g_spirvCacheCompressedSize = %d;" % len(compressed), text)
text = re.sub(r"g_spirvCacheDecompressedSize = \d+;", "g_spirvCacheDecompressedSize = %d;" % len(spirv), text)
crlf = b"\r\n" in open(sys.argv[1], "rb").read(4096)
open(sys.argv[3], "w", encoding="utf-8", newline="\r\n" if crlf else "\n").write(text)
print(f"{replaced} of {len(base)} entries took the new SPIR-V; {len(base_spirv)} -> {len(spirv)} bytes "
      f"({len(compressed)} compressed)")

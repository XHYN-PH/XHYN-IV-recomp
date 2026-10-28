#!/usr/bin/env python3
"""Decode diagnostic PNGs independently, including stored-deflate block boundaries."""
from pathlib import Path
import binascii
import struct
import subprocess
import tempfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
HEADER = ROOT / "glue/rexglue-sdk-main/src"


def main():
    with tempfile.TemporaryDirectory(prefix="xhyn-png-") as temp:
        directory = Path(temp)
        source = directory / "encode.cpp"
        source.write_text(r'''
#include "graphics/gta4_native/native_capture_png.h"
#include <cassert>
#include <fstream>
using namespace rex::graphics::gta4_native;
int main(int argc,char** argv) {
  assert(argc==4);
  const auto width=uint32_t(std::stoul(argv[2])),height=uint32_t(std::stoul(argv[3]));
  std::vector<uint8_t> pixels(size_t(width)*height*4);
  for(size_t i=0;i<pixels.size();++i)pixels[i]=uint8_t(i*37+11);
  assert(EncodeNativeCapturePng(0,height,pixels).empty());
  assert(EncodeNativeCapturePng(16777217,1,{}).empty());
  assert(EncodeNativeCapturePng(width,height,{}).empty());
  const auto png=EncodeNativeCapturePng(width,height,pixels);
  std::ofstream file(argv[1],std::ios::binary);
  file.write(reinterpret_cast<const char*>(png.data()),png.size());
  return file ? 0 : 1;
}
''')
        executable = directory / "encode"
        subprocess.run(["g++", "-std=c++23", "-O2", "-I" + str(HEADER), str(source), "-o", str(executable)], check=True)
        for width, height in [(1, 1), (3, 2), (257, 65), (256, 256)]:
            path = directory / "image.png"
            subprocess.run([str(executable), str(path), str(width), str(height)], check=True)
            data = path.read_bytes()
            assert data[:8] == b"\x89PNG\r\n\x1a\n"
            offset, chunks = 8, []
            while offset < len(data):
                length = struct.unpack_from(">I", data, offset)[0]
                tag, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]
                crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
                assert binascii.crc32(tag + payload) == crc
                chunks.append((tag, payload))
                offset += length + 12
            assert offset == len(data) and [t for t, _ in chunks] == [b"IHDR", b"IDAT", b"IEND"]
            assert struct.unpack(">IIBBBBB", chunks[0][1]) == (width, height, 8, 6, 0, 0, 0)
            raw = zlib.decompress(chunks[1][1])
            expected = bytes((i * 37 + 11) & 255 for i in range(width * height * 4))
            assert len(raw) == height * (width * 4 + 1)
            for row in range(height):
                start = row * (width * 4 + 1)
                assert raw[start] == 0
                assert raw[start + 1:start + 1 + width * 4] == expected[row * width * 4:(row + 1) * width * 4]
        print("Diagnostic PNG: four images decoded losslessly; framing, CRC, block boundaries and input bounds passed")


if __name__ == "__main__":
    main()

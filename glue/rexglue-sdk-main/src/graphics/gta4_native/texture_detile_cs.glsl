#version 450
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference_uvec2 : require

// GPU form of the texture capture's block copy (CaptureTextureResourceUncached:
// GetTiledOffset2D/3D or linear addressing, then CopySwapBlock): one
// invocation per destination block of one subresource. The source is the
// subresource's raw guest bytes; the destination is laid out exactly like the
// CPU path's payload, so the image copies that follow are unchanged. Blocks of
// 4, 8 or 16 bytes only (the CPU path keeps the others).

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer SourceWords {
  uint words[];
};
layout(buffer_reference, std430, buffer_reference_align = 4) writeonly buffer DestinationWords {
  uint words[];
};

layout(push_constant) uniform DetileJob {
  uvec2 source;             // raw subresource bytes (device address)
  uvec2 destination;        // converted subresource bytes (device address)
  uint block_width;         // blocks per row to write
  uint block_height;        // rows to write
  uint depth;               // slices (3D), else 1
  uint pitch_h;             // guest pitch in blocks
  uint pitch_v;             // guest vertical pitch in blocks
  uint flags;               // bit 0 tiled, bit 1 3D, bits 4-5 endian, bits 8-12 log2(block bytes)
  uint packed_x;            // packed-mip block offset in the guest tile
  uint packed_y;
  uint destination_pitch;   // host row pitch in blocks
  uint destination_slice;   // host slice size in blocks
} job;

// texture_util::GetTiledOffset2D.
int TiledOffset2D(int x, int y, uint pitch, int log2_bytes) {
  int p = int((pitch + 31u) & ~31u);
  int macro_bits = ((x >> 5) + (y >> 5) * (p >> 5)) << (log2_bytes + 7);
  int micro = ((x & 7) + ((y & 0xE) << 2)) << log2_bytes;
  int offset = macro_bits + ((micro & ~0xF) << 1) + (micro & 0xF) + ((y & 1) << 4);
  return ((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2) +
         (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F);
}

// texture_util::GetTiledOffset3D.
int TiledOffset3D(int x, int y, int z, uint pitch, uint height, int log2_bytes) {
  int p = int((pitch + 31u) & ~31u);
  int h = int((height + 31u) & ~31u);
  int macro_outer = ((y >> 4) + (z >> 2) * (h >> 4)) * (p >> 5);
  int macro_bits = ((((x >> 5) + macro_outer) << (log2_bytes + 6)) & 0xFFFFFFF) << 1;
  int micro = (((x & 7) + ((y & 6) << 2)) << (log2_bytes + 6)) >> 6;
  int offset_outer = ((y >> 3) + (z >> 2)) & 1;
  int offset1 = offset_outer + ((((x >> 3) + (offset_outer << 1)) & 3) << 1);
  int offset2 = ((macro_bits + (micro & ~15)) << 1) + (micro & 15) +
                ((z & 3) << (log2_bytes + 6)) + ((y & 1) << 4);
  int address = (offset1 & 1) << 3;
  address += (offset2 >> 6) & 7;
  address <<= 3;
  address += offset1 & ~1;
  address <<= 2;
  address += offset2 & ~511;
  address <<= 3;
  address += offset2 & 63;
  return address;
}

// CopySwapBlock on one little-endian 32-bit word (xenos::Endian).
uint Swap(uint word, uint endian) {
  if (endian == 1u) return ((word & 0x00FF00FFu) << 8) | ((word >> 8) & 0x00FF00FFu);  // 8in16
  if (endian == 2u) {                                                                   // 8in32
    return (word << 24) | ((word & 0xFF00u) << 8) | ((word >> 8) & 0xFF00u) | (word >> 24);
  }
  if (endian == 3u) return (word << 16) | (word >> 16);                                 // 16in32
  return word;
}

void main() {
  uvec3 id = gl_GlobalInvocationID;
  if (id.x >= job.block_width || id.y >= job.block_height || id.z >= job.depth) return;
  int log2_bytes = int((job.flags >> 8) & 31u);
  int sx = int(job.packed_x + id.x);
  int sy = int(job.packed_y + id.y);
  int sz = int(id.z);
  int source_offset;
  if ((job.flags & 1u) != 0u) {
    source_offset = (job.flags & 2u) != 0u
                        ? TiledOffset3D(sx, sy, sz, job.pitch_h, job.pitch_v, log2_bytes)
                        : TiledOffset2D(sx, sy, job.pitch_h, log2_bytes);
  } else {
    source_offset = int(((id.z * job.pitch_v + uint(sy)) * job.pitch_h + uint(sx)) << log2_bytes);
  }
  uint destination_offset =
      (id.z * job.destination_slice + id.y * job.destination_pitch + id.x) << uint(log2_bytes);
  uint endian = (job.flags >> 4) & 3u;
  uint word_count = (1u << uint(log2_bytes)) >> 2;
  SourceWords source = SourceWords(job.source);
  DestinationWords destination = DestinationWords(job.destination);
  uint s = uint(source_offset) >> 2;
  uint d = destination_offset >> 2;
  for (uint i = 0u; i < word_count; ++i) {
    destination.words[d + i] = Swap(source.words[s + i], endian);
  }
}

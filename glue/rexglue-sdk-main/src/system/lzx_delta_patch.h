// Byte-oriented XEXP delta records. Compressed payload lengths do not preserve
// the alignment of the next record, so these are never overlaid with structs.
#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <span>

namespace rex::lzx::detail {

inline uint32_t DeltaBE32(const uint8_t* p) {
  return (uint32_t(p[0]) << 24) | (uint32_t(p[1]) << 16) | (uint32_t(p[2]) << 8) | p[3];
}

inline uint16_t DeltaBE16(const uint8_t* p) {
  return (uint16_t(p[0]) << 8) | p[1];
}

// Decode receives (payload, output, output_length, reference, reference_length).
// An error may leave an already applied prefix; the loader discards that image.
template <typename Decode>
int ApplyDeltaPatch(std::span<const uint8_t> patch, uint8_t* destination,
                    size_t destination_size, Decode&& decode) {
  constexpr size_t kHeaderSize = 12;
  size_t position = 0;
  while (position < patch.size()) {
    const auto remaining = patch.subspan(position);
    if (remaining.size() < kHeaderSize) {
      // A block may end in zero alignment padding, but never read past it.
      return std::all_of(remaining.begin(), remaining.end(), [](uint8_t b) { return b == 0; })
                 ? 0 : 1;
    }
    const uint32_t source = DeltaBE32(remaining.data());
    const uint32_t target = DeltaBE32(remaining.data() + 4);
    const uint16_t length = DeltaBE16(remaining.data() + 8);
    const uint16_t compressed = DeltaBE16(remaining.data() + 10);
    if (source == 0 && target == 0 && length == 0 && compressed == 0) {
      return 0;
    }

    if (target > destination_size || length > destination_size - target ||
        (compressed != 0 &&
         (source > destination_size || length > destination_size - source)) ||
        (!destination && (length != 0 || compressed > 1))) {
      return 1;
    }
    const size_t payload_size = compressed > 1 ? compressed : 0;
    if (payload_size > remaining.size() - kHeaderSize) {
      return 1;
    }
    if (compressed == 0) {
      if (length) std::memset(destination + target, 0, length);
    } else if (compressed == 1) {
      if (length) std::memmove(destination + target, destination + source, length);
    } else {
      const int result = decode(remaining.subspan(kHeaderSize, payload_size),
                                destination + target, length, destination + source, length);
      if (result) return result;
    }
    position += kHeaderSize + payload_size;
  }
  return 0;
}

}  // namespace rex::lzx::detail

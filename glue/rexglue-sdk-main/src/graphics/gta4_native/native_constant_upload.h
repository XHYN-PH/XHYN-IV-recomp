#pragma once

#include <algorithm>
#include <array>
#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>
#include <optional>
#include <span>

#if defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace rex::graphics::gta4_native {

// All constant allocations are contiguous, whole float4 registers. One tracker
// belongs to one host-visible frame-slot buffer. BeginFrame is called only after
// the slot fence (or unsubmitted rollback); ResetStorage is required whenever
// that buffer is replaced. No shader ABI or per-draw allocation is changed.
class NativeConstantUploadTracker {
 public:
  static constexpr size_t kRegisterBytes = 16;

  void BeginFrame() { written_bytes_ = 0; }
  void ResetStorage() {
    initialized_bytes_ = 0;
    written_bytes_ = 0;
  }

  std::optional<size_t> Write(std::span<uint8_t> storage, size_t offset,
                              std::span<const uint8_t> source, bool guest_word_order,
                              bool compare_existing = true) {
    if (source.empty() || offset % kRegisterBytes || source.size() % kRegisterBytes ||
        offset > storage.size() || source.size() > storage.size() - offset ||
        initialized_bytes_ > storage.size() ||
        source.size() > std::numeric_limits<size_t>::max() - written_bytes_) {
      return std::nullopt;
    }

    size_t written = 0;
    // Only the initialized, cached prefix may be read. Uninitialized tails and
    // uncached heaps take a single sequential copy/conversion path instead of
    // repeating comparison and initialization branches for every register.
    // Allocations may start past the initialized prefix (alignment gaps);
    // those bytes are written whole, without a comparison.
    const size_t existing = compare_existing && offset < initialized_bytes_
        ? std::min(source.size(), initialized_bytes_ - offset) : 0;
    const auto convert = [&](uint8_t* dst, const uint8_t* src, size_t bytes) {
      if (!guest_word_order) { std::memcpy(dst, src, bytes); return; }
      size_t i = 0;
#if defined(__aarch64__)
      // The destination is usually write-combined upload memory: the scalar
      // loop (a 4-byte store per word, ~1.7M per frame at the bridge) was the
      // recorder's hottest code. Swap 16 bytes per register, store 64 at once.
      for (; i + 64 <= bytes; i += 64) {
        uint8x16x4_t block = vld1q_u8_x4(src + i);
        block.val[0] = vrev32q_u8(block.val[0]);
        block.val[1] = vrev32q_u8(block.val[1]);
        block.val[2] = vrev32q_u8(block.val[2]);
        block.val[3] = vrev32q_u8(block.val[3]);
        vst1q_u8_x4(dst + i, block);
      }
      for (; i + 16 <= bytes; i += 16) vst1q_u8(dst + i, vrev32q_u8(vld1q_u8(src + i)));
#endif
      for (; i < bytes; i += sizeof(uint32_t)) {
        uint32_t word;
        std::memcpy(&word, src + i, sizeof(word));
        word = std::byteswap(word);
        std::memcpy(dst + i, &word, sizeof(word));
      }
    };
    size_t relative = 0;
    // Reject equal cache-line-sized groups in one comparison. Changed groups
    // still store/count only different registers, preserving exact bit patterns.
    for (; existing - relative >= 64; relative += 64) {
      alignas(16) std::array<uint8_t, 64> converted;
      convert(converted.data(), source.data() + relative, converted.size());
      uint8_t* destination = storage.data() + offset + relative;
      if (std::memcmp(destination, converted.data(), converted.size()) == 0) continue;
      for (size_t lane = 0; lane < converted.size(); lane += kRegisterBytes) {
        if (std::memcmp(destination + lane, converted.data() + lane, kRegisterBytes) != 0) {
          std::memcpy(destination + lane, converted.data() + lane, kRegisterBytes);
          written += kRegisterBytes;
        }
      }
    }
    for (; relative < existing; relative += kRegisterBytes) {
      std::array<uint8_t, kRegisterBytes> converted;
      convert(converted.data(), source.data() + relative, converted.size());
      uint8_t* destination = storage.data() + offset + relative;
      if (std::memcmp(destination, converted.data(), converted.size()) != 0) {
        std::memcpy(destination, converted.data(), converted.size());
        written += kRegisterBytes;
      }
    }
    if (relative < source.size()) {
      convert(storage.data() + offset + relative, source.data() + relative, source.size() - relative);
      written += source.size() - relative;
    }
    initialized_bytes_ = std::max(initialized_bytes_, offset + source.size());
    written_bytes_ += written;
    return written;
  }

  size_t initialized_bytes() const { return initialized_bytes_; }
  size_t written_bytes() const { return written_bytes_; }

 private:
  size_t initialized_bytes_ = 0;
  size_t written_bytes_ = 0;
};

}  // namespace rex::graphics::gta4_native

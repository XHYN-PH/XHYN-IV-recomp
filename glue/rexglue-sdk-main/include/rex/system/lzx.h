/**
 * @file        runtime/lzx.h
 * @brief       LZX decompression interface for XEX loading
 *
 * @copyright   Copyright 2022 Ben Vanik. All rights reserved. (Xenia Project)
 *
 * @license     BSD 3-Clause License
 *              See LICENSE file in the project root for full license text.
 *
 * @modified    Tom Clay, 2026 - Adapted for ReXGlue runtime
 */

#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <span>

namespace rex {
struct xex2_delta_patch;
}  // namespace rex

int lzx_decompress(const void* lzx_data, size_t lzx_len, void* dest, size_t dest_len,
                   uint32_t window_size, void* window_data, size_t window_data_len);

int lzxdelta_apply_patch(rex::xex2_delta_patch* patch, size_t patch_len, uint32_t window_size,
                         void* dest);

// Loader entry point with the real writable allocation extent. The four-argument
// entry point above is retained for ABI compatibility and has no allocation bound.
int lzxdelta_apply_patch(const void* patch, size_t patch_len, uint32_t window_size,
                         void* dest, size_t dest_len);

namespace rex::lzx {

// libmspack's LZX state is deliberately hidden from SDK consumers. One
// instance represents one retail streaming decoder and retains its window,
// repeated offsets, and Huffman tables across DecodeFrame calls.
// This persistent API follows Xbox XMem framing, including unpadded odd raw
// blocks. The separate one-shot lzx_decompress API retains standard LZX padding.
enum class DecodeStatus : uint8_t {
  kSuccess,
  kInvalidArgument,
  kOutOfMemory,
  kDataError,
  kPoisoned,
};

struct DecodeFrameResult {
  DecodeStatus status = DecodeStatus::kInvalidArgument;
  size_t bytes_written = 0;
  size_t bytes_consumed = 0;

  explicit operator bool() const { return status == DecodeStatus::kSuccess; }
};

class PersistentDecoder {
 public:
  static std::unique_ptr<PersistentDecoder> Create(uint32_t window_size);

  ~PersistentDecoder();
  PersistentDecoder(PersistentDecoder&&) noexcept;
  PersistentDecoder& operator=(PersistentDecoder&&) noexcept;

  PersistentDecoder(const PersistentDecoder&) = delete;
  PersistentDecoder& operator=(const PersistentDecoder&) = delete;

  // input is the complete readable region for this frame. Callers wrapping
  // APIs with documented look-ahead bytes must include those actual bytes in
  // the span; the decoder never invents padding between frames.
  DecodeFrameResult DecodeFrame(std::span<const uint8_t> input,
                                std::span<uint8_t> output);
  bool Reset();
  bool poisoned() const;
  uint64_t total_output_bytes() const;
  uint32_t window_size() const;

 private:
  struct Impl;

  explicit PersistentDecoder(std::unique_ptr<Impl> impl);
  std::unique_ptr<Impl> impl_;
};

}  // namespace rex::lzx

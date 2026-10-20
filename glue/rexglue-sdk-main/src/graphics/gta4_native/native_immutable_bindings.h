#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <span>
#include <type_traits>
#include <vector>

#include <xxhash.h>

#include "frame_constant_arena.h"
#include "native_working_set.h"
#include "stateful_constant_state.h"

namespace rex::graphics::gta4_native {

// One cache belongs to one submission-owned arena. Retaining each distinct
// version once makes raw identities safe even for temporary diagnostic draws.
// Reset is invoked only through the arena's fence/rollback checks; it releases
// all owners, never carrying immutable versions across an unbounded history.
template <typename Allocation>
class NativeImmutableBindings {
 public:
  static_assert(std::is_trivially_copyable_v<Allocation>);
  enum class Result { kFailure, kVersionHit, kContentHit, kUploaded };
  struct Entry {
    Allocation allocation{};
    const std::vector<uint8_t>* bytes = nullptr;
  };
  // write_bytes: the prefix of the bank the consuming shader can read. Only
  // that prefix is written; the identity includes it, so a shader reading
  // further never reuses a shorter upload of the same version.
  template <typename Materialize, typename Upload>
  Result Bind(FrameConstantKind kind, const std::shared_ptr<const ConstantStateVersion>& version,
              Allocation& allocation, const std::vector<uint8_t>*& bytes,
              Materialize&& materialize, Upload&& upload, uint32_t write_bytes = UINT32_MAX,
              uint64_t prefix_hash = 0, uint32_t prefix_hash_bytes = 0) {
    bytes = nullptr;
    if (!version || !version->byte_size || kind == FrameConstantKind::kShared) return Result::kFailure;
    const uint32_t written = uint32_t(std::min<size_t>(write_bytes, version->byte_size));
    // User-space pointers fit in 48 bits; the prefix (16-byte units) tags the top.
    const uint64_t tag = written == version->byte_size ? 0 : uint64_t(written / 16 + 1) << 48;
    const FrameConstantIdentity identity{kind, uint64_t(reinterpret_cast<uintptr_t>(version.get())) | tag};
    if (const auto* hit = versions_.Find(identity)) {
      allocation = hit->allocation; bytes = hit->bytes; return Result::kVersionHit;
    }
    bytes = materialize(version);
    if (!bytes || bytes->size() != version->byte_size || bytes->size() > UINT32_MAX) return Result::kFailure;
    // Content identity covers the written prefix. A version hashed at
    // creation (the whole bank, on the render worker) is reused even for a
    // prefix: equal banks have equal prefixes, and a candidate is compared
    // byte for byte below, so only prefix-only sharing is given up. Hashing
    // here instead costs the recorder, the slowest pipeline stage.
    // prefix_hash: the same prefix hashed ahead of time by the caller.
    const uint64_t content_hash = version->content_hash ? version->content_hash
                                  : prefix_hash_bytes == written && prefix_hash_bytes
                                      ? prefix_hash
                                      : XXH3_64bits(bytes->data(), written);
    const NativeConstantContentKey key{content_hash, uint32_t(kind), written};
    const Entry* candidate = contents_.Find(key);
    Result result = Result::kUploaded;
    if (candidate && std::memcmp(candidate->bytes->data(), bytes->data(), written) == 0) {
      allocation = candidate->allocation;
      result = Result::kContentHit;
    } else {
      bool uploaded;
      if constexpr (std::is_invocable_v<Upload&, FrameConstantKind, uint64_t, std::span<const uint8_t>,
                                        size_t, Allocation&>) {
        uploaded = upload(kind, uint64_t(reinterpret_cast<uintptr_t>(bytes)) | tag,
                          std::span<const uint8_t>(bytes->data(), written), bytes->size(), allocation);
      } else {
        uploaded = upload(kind, uint64_t(reinterpret_cast<uintptr_t>(bytes)) | tag, *bytes, allocation);
      }
      if (!uploaded) return Result::kFailure;
    }
    // Own before publishing any pointer-bearing entry. A content-hash match is
    // never used without equality of the written guest-endian bytes.
    owners_.push_back(version);
    const Entry entry{allocation, bytes};
    if (!candidate && !contents_.Insert(key, entry)) return Result::kFailure;
    if (!versions_.Insert(identity, entry)) return Result::kFailure;
    return result;
  }
  bool CanReset() const { return versions_.CanResetGeneration() && contents_.CanResetGeneration(); }
  // released, when given, receives the owners instead of dropping them here.
  bool Reset(std::vector<std::shared_ptr<const ConstantStateVersion>>* released = nullptr) {
    if (!CanReset()) return false;
    versions_.ResetGeneration(); contents_.ResetGeneration();
    if (released) {
      released->swap(owners_);
      owners_.reserve(released->size());
    } else {
      owners_.clear();
    }
    return true;
  }
  size_t owner_count() const { return owners_.size(); }
  size_t entry_count() const { return versions_.size(); }
  size_t content_count() const { return contents_.size(); }
  size_t retained_capacity_bytes() const {
    return owners_.capacity() * sizeof(owners_[0]);
  }
 private:
  FrameGenerationMap<FrameConstantIdentity, Entry, FrameConstantIdentityHash> versions_;
  FrameGenerationMap<NativeConstantContentKey, Entry, NativeConstantContentHash> contents_;
  std::vector<std::shared_ptr<const ConstantStateVersion>> owners_;
};

// Padding-free encoding for a single hash invocation. Every semantic input is
// represented separately, including the two descriptor epochs and raw floats.
// Equality remains the authoritative test after a hash match.
template <size_t Stages>
constexpr auto NativeSharedKeyWords(const SharedConstantSemanticKey<Stages>& key) {
  // Three stage arrays plus 44 words (verified by the field-coverage test).
  std::array<uint64_t, Stages * 3 + 44> words{};
  size_t cursor = 0;
  const auto append = [&](uint64_t value) { words[cursor++] = value; };
  for (auto v : key.texture_descriptor_indices) append(v);
  for (auto v : key.sampler_descriptor_indices) append(v);
  for (auto v : key.sampler_lod_bias_bits) append(v);
  append(key.boolean_version.epoch); append(key.boolean_version.revision);
  append(key.image_descriptor_epoch); append(key.sampler_descriptor_epoch);
  append(key.cached_descriptor_epoch); append(key.environmental_data_hash); append(key.environmental_sequence);
  append(key.tone_lut_address); append(key.cloud_mask_address);
  append(key.cloud_mask_width); append(key.cloud_mask_height);
  append(key.modern_effects_enabled); append(key.water_reflection);
  for (auto v : key.viewport_bits) append(v);
  append(key.device); append(key.descriptor_copy); append(key.descriptor_page);
  append(key.width); append(key.height); append(key.logical_width); append(key.logical_height);
  append(key.sample_count); append(key.alpha_reference_bits); append(key.alpha_to_mask);
  for (auto v : key.color_output_info) append(v);
  append(key.color_output_mask);
  for (auto v : key.clip_plane_bits) append(v);
  append(key.clip_plane_enable_mask); append(key.vertex_booleans); append(key.pixel_booleans);
  append(key.descriptor_backend); append(key.environment_present);
  append(key.split_postfx_applied);
  return words;
}
}  // namespace rex::graphics::gta4_native

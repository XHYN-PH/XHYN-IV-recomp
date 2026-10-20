#pragma once

#include <array>
#include <cstdint>

namespace rex::graphics::gta4_native {

constexpr uint32_t kNativeFixedRenderTargetCount = 4;

struct NativeBlendControlState {
  uint32_t source_color = 0;
  uint32_t color_operation = 0;
  uint32_t destination_color = 0;
  uint32_t source_alpha = 0;
  uint32_t alpha_operation = 0;
  uint32_t destination_alpha = 0;

  constexpr bool operator==(const NativeBlendControlState&) const = default;
};

constexpr NativeBlendControlState DecodeNativeBlendControl(uint32_t packed) {
  return {
      .source_color = packed & 0x1Fu,
      .color_operation = (packed >> 5) & 0x7u,
      .destination_color = (packed >> 8) & 0x1Fu,
      .source_alpha = (packed >> 16) & 0x1Fu,
      .alpha_operation = (packed >> 21) & 0x7u,
      .destination_alpha = (packed >> 24) & 0x1Fu,
  };
}

constexpr NativeBlendControlState kNativeBlendControlIdentity = {
    .source_color = 1u,
    .color_operation = 0u,
    .destination_color = 0u,
    .source_alpha = 1u,
    .alpha_operation = 0u,
    .destination_alpha = 0u,
};

constexpr bool IsNativeBlendControlIdentity(uint32_t packed) {
  return DecodeNativeBlendControl(packed) == kNativeBlendControlIdentity;
}

constexpr bool IsNativeBlendControlEnabled(uint32_t packed) {
  return !IsNativeBlendControlIdentity(packed);
}

// Blending that cannot change what a target stores: none of its channels is
// written, or every written channel group computes source * 1 + destination *
// 0 (add or subtract). Turning it off keeps Turnip's LRZ writes on for the
// rest of the render pass ("Depth write + blending" disables them).
// write_mask: R=1, G=2, B=4, A=8 (NativeColorWriteMaskForTarget).
constexpr bool IsNativeBlendEffectivelyDisabled(uint32_t packed, uint32_t write_mask) {
  if (!(write_mask & 0xFu)) return true;
  const NativeBlendControlState blend = DecodeNativeBlendControl(packed);
  const auto passthrough = [](uint32_t source, uint32_t operation, uint32_t destination) {
    return source == 1u && destination == 0u && (operation == 0u || operation == 1u);
  };
  const bool color_written = (write_mask & 0x7u) != 0;
  const bool alpha_written = (write_mask & 0x8u) != 0;
  return (!color_written ||
          passthrough(blend.source_color, blend.color_operation, blend.destination_color)) &&
         (!alpha_written ||
          passthrough(blend.source_alpha, blend.alpha_operation, blend.destination_alpha));
}

constexpr uint32_t NativeBlendEnableMask(
    const std::array<uint32_t, kNativeFixedRenderTargetCount>& blend_controls,
    uint32_t active_target_mask) {
  uint32_t result = 0;
  for (uint32_t target = 0; target < kNativeFixedRenderTargetCount; ++target) {
    const uint32_t target_bit = 1u << target;
    if ((active_target_mask & target_bit) != 0 &&
        IsNativeBlendControlEnabled(blend_controls[target])) {
      result |= target_bit;
    }
  }
  return result;
}

constexpr uint32_t NativeColorWriteMaskForTarget(uint32_t packed_mask, uint32_t target) {
  return target < kNativeFixedRenderTargetCount ? (packed_mask >> (target * 4u)) & 0xFu : 0u;
}

constexpr bool AnyNativeColorWriteEnabled(uint32_t packed_mask) {
  return (packed_mask & 0xFFFFu) != 0;
}

constexpr bool AnyNativeColorWriteEnabledForTargets(uint32_t packed_mask,
                                                    uint32_t active_target_mask) {
  for (uint32_t target = 0; target < kNativeFixedRenderTargetCount; ++target) {
    if ((active_target_mask & (1u << target)) != 0 &&
        NativeColorWriteMaskForTarget(packed_mask, target) != 0) {
      return true;
    }
  }
  return false;
}

constexpr uint32_t NativeColorTargetMaskFromWriteMask(uint32_t packed_mask) {
  uint32_t result = 0;
  for (uint32_t target = 0; target < kNativeFixedRenderTargetCount; ++target) {
    if (NativeColorWriteMaskForTarget(packed_mask, target) != 0) {
      result |= 1u << target;
    }
  }
  return result;
}

constexpr uint32_t NormalizeNativeColorWriteMask(uint32_t packed_mask,
                                                 uint32_t shader_output_mask,
                                                 uint32_t packed_format_component_masks) {
  uint32_t result = 0;
  for (uint32_t target = 0; target < kNativeFixedRenderTargetCount; ++target) {
    const uint32_t target_bit = 1u << target;
    if ((shader_output_mask & target_bit) == 0) {
      continue;
    }
    const uint32_t format_component_mask =
        NativeColorWriteMaskForTarget(packed_format_component_masks, target);
    uint32_t write_mask = NativeColorWriteMaskForTarget(packed_mask, target) &
                          format_component_mask;
    if (!write_mask) {
      continue;
    }
    // Match the generic Xenos renderer: components absent from the attachment
    // format are marked writable after checking that at least one real
    // component is selected. This avoids host read/merge slow paths without
    // turning a write to a nonexistent component into a real attachment write.
    write_mask |= 0xFu & ~format_component_mask;
    result |= write_mask << (target * 4u);
  }
  return result;
}

constexpr int32_t SignExtendNative15(uint32_t value) {
  value &= 0x7FFFu;
  return (value & 0x4000u) ? int32_t(value | 0xFFFF8000u) : int32_t(value);
}

struct NativePackedScissorState {
  std::array<int32_t, 4> rectangle{};
  std::array<int32_t, 2> window_offset{};
  bool window_offset_disable = false;

  constexpr bool operator==(const NativePackedScissorState&) const = default;
};

constexpr NativePackedScissorState DecodeNativePackedScissor(uint32_t packed_top_left,
                                                             uint32_t packed_bottom_right,
                                                             uint32_t packed_window_offset) {
  // PA_SC_WINDOW_SCISSOR coordinates are unsigned 14-bit fields. GTA IV's XDK
  // setter writes through a 15-bit mask, but bit 14 is hardware padding and is
  // deliberately ignored here, matching the generic Xenos register decode.
  constexpr uint32_t kScissorCoordinateMask = 0x3FFFu;
  NativePackedScissorState result{};
  result.rectangle = {
      int32_t(packed_top_left & kScissorCoordinateMask),
      int32_t((packed_top_left >> 16) & kScissorCoordinateMask),
      int32_t(packed_bottom_right & kScissorCoordinateMask),
      int32_t((packed_bottom_right >> 16) & kScissorCoordinateMask),
  };
  result.window_offset = {
      SignExtendNative15(packed_window_offset),
      SignExtendNative15(packed_window_offset >> 16),
  };
  result.window_offset_disable = (packed_top_left & 0x80000000u) != 0;
  if (!result.window_offset_disable) {
    result.rectangle[0] += result.window_offset[0];
    result.rectangle[1] += result.window_offset[1];
    result.rectangle[2] += result.window_offset[0];
    result.rectangle[3] += result.window_offset[1];
  }
  return result;
}

enum class NativeCullFace : uint8_t {
  kNone,
  kFront,
  kBack,
  kInvalid,
};

struct NativeCullRasterState {
  NativeCullFace cull_face = NativeCullFace::kInvalid;
  bool front_face_clockwise = false;

  constexpr bool valid() const { return cull_face != NativeCullFace::kInvalid; }
  constexpr bool operator==(const NativeCullRasterState&) const = default;
};

constexpr NativeCullRasterState DecodeNativeCullRasterState(uint32_t cull_mode) {
  switch (cull_mode) {
    case 0:
      return {NativeCullFace::kNone, false};
    case 1:
      return {NativeCullFace::kFront, false};
    case 2:
      return {NativeCullFace::kBack, false};
    case 4:
      return {NativeCullFace::kNone, true};
    case 5:
      return {NativeCullFace::kFront, true};
    case 6:
      return {NativeCullFace::kBack, true};
    default:
      return {NativeCullFace::kInvalid, false};
  }
}

enum class NativePolygonMode : uint8_t {
  kFill,
  kLine,
  kInvalid,
};

constexpr NativePolygonMode SelectNativePolygonMode(uint32_t setup_mode, uint32_t cull_mode) {
  const NativeCullRasterState cull = DecodeNativeCullRasterState(cull_mode);
  if (!cull.valid()) {
    return NativePolygonMode::kInvalid;
  }
  const uint32_t polygon_mode_enable = (setup_mode >> 3) & 0x3u;
  // Only Xenos dual polygon mode consumes the per-face polygon types. Other
  // values rasterize triangles, including the reserved value used by titles.
  if (polygon_mode_enable != 1u) {
    return NativePolygonMode::kFill;
  }
  const uint32_t front_type = (setup_mode >> 5) & 0x7u;
  const uint32_t back_type = (setup_mode >> 8) & 0x7u;
  uint32_t selected_type = 2u;
  if (cull.cull_face != NativeCullFace::kFront) {
    selected_type = front_type < selected_type ? front_type : selected_type;
  }
  if (cull.cull_face != NativeCullFace::kBack) {
    selected_type = back_type < selected_type ? back_type : selected_type;
  }
  if (selected_type <= 1u) {
    return NativePolygonMode::kLine;
  }
  return selected_type == 2u ? NativePolygonMode::kFill : NativePolygonMode::kInvalid;
}

constexpr uint32_t NativeActiveSampleBits(uint32_t sample_count) {
  switch (sample_count) {
    case 1:
      return 0x1u;
    case 2:
      return 0x3u;
    case 4:
      return 0xFu;
    default:
      return 0u;
  }
}

struct NativeSampleMaskSelection {
  bool representable = false;
  uint32_t host_mask = 0;

  constexpr bool operator==(const NativeSampleMaskSelection&) const = default;
};

constexpr uint32_t RemapNativeSampleMaskNibble(uint32_t guest_mask, uint32_t sample_count) {
  guest_mask &= NativeActiveSampleBits(sample_count);
  switch (sample_count) {
    case 1:
      return guest_mask;
    case 2:
      // Xenos T, B correspond to Vulkan standard 2x samples 1, 0.
      return ((guest_mask & 0x1u) << 1) | ((guest_mask & 0x2u) >> 1);
    case 4:
      // Xenos TL, BL, TR, BR correspond to Vulkan 0, 2, 1, 3.
      return (guest_mask & 0x9u) | ((guest_mask & 0x2u) << 1) | ((guest_mask & 0x4u) >> 1);
    default:
      return 0;
  }
}

constexpr NativeSampleMaskSelection SelectNativeSampleMask(uint32_t guest_mask,
                                                           uint32_t sample_count) {
  const uint32_t active_bits = NativeActiveSampleBits(sample_count);
  if (!active_bits) {
    return {};
  }
  const uint32_t first_pixel = guest_mask & active_bits;
  for (uint32_t pixel = 1; pixel < 4; ++pixel) {
    if (((guest_mask >> (pixel * 4u)) & active_bits) != first_pixel) {
      // Vulkan pipeline sample masks are uniform across pixels. Xenos can carry
      // a 2x2 per-pixel mask pattern, which needs shader emulation.
      return {};
    }
  }
  return {true, RemapNativeSampleMaskNibble(first_pixel, sample_count)};
}

constexpr NativeSampleMaskSelection SelectNativePipelineSampleMask(
    uint32_t guest_mask, uint32_t guest_sample_count, uint32_t host_sample_count) {
  const NativeSampleMaskSelection guest = SelectNativeSampleMask(guest_mask, guest_sample_count);
  const uint32_t host_bits = NativeActiveSampleBits(host_sample_count);
  if (!guest.representable || !host_bits) {
    return {};
  }
  if (guest_sample_count == host_sample_count) {
    return guest;
  }
  if (!guest.host_mask) {
    return {true, 0};
  }
  if (guest.host_mask == NativeActiveSampleBits(guest_sample_count)) {
    return {true, host_bits};
  }
  // A partial mask does not have an exact mapping across different raster
  // sample positions. Nonuniform pixel patterns are rejected above as well:
  // fragment output masks cannot reproduce early depth/stencil z-fail writes.
  return {};
}

struct NativeDepthBiasFaceState {
  bool enabled = false;
  uint32_t slope_bits = 0;
  uint32_t constant_bits = 0;

  constexpr bool operator==(const NativeDepthBiasFaceState&) const = default;
};

struct NativeDepthBiasSelection {
  bool representable = false;
  NativeDepthBiasFaceState selected{};

  constexpr bool operator==(const NativeDepthBiasSelection&) const = default;
};

constexpr NativeDepthBiasFaceState NormalizeNativeDepthBiasFaceState(
    const NativeDepthBiasFaceState& state) {
  return state.enabled ? state : NativeDepthBiasFaceState{};
}

constexpr NativeDepthBiasSelection SelectNativeDepthBias(
    uint32_t cull_mode, const NativeDepthBiasFaceState& front_input,
    const NativeDepthBiasFaceState& back_input) {
  const NativeDepthBiasFaceState front = NormalizeNativeDepthBiasFaceState(front_input);
  const NativeDepthBiasFaceState back = NormalizeNativeDepthBiasFaceState(back_input);
  const NativeCullRasterState cull = DecodeNativeCullRasterState(cull_mode);
  if (!cull.valid()) {
    return {};
  }
  if (cull.cull_face == NativeCullFace::kFront) {
    return {true, back};
  }
  if (cull.cull_face == NativeCullFace::kBack) {
    return {true, front};
  }
  if (front == back) {
    return {true, front};
  }
  // Core Vulkan has one depth-bias state for both rasterized faces.
  return {};
}

struct NativeStencilMaskRefState {
  uint32_t reference = 0;
  uint32_t compare_mask = 0;
  uint32_t write_mask = 0;

  constexpr bool operator==(const NativeStencilMaskRefState&) const = default;
};

struct NativeStencilMaskRefSelection {
  bool representable = false;
  bool use_separate_faces = false;
  NativeStencilMaskRefState front{};
  NativeStencilMaskRefState back{};

  constexpr bool operator==(const NativeStencilMaskRefSelection&) const = default;
};

constexpr NativeStencilMaskRefSelection SelectNativeStencilMaskRef(
    bool two_sided, bool separate_stencil_mask_ref_supported, uint32_t cull_mode,
    const NativeStencilMaskRefState& front, const NativeStencilMaskRefState& back) {
  if (!two_sided) {
    return {true, false, front, front};
  }
  if (separate_stencil_mask_ref_supported) {
    return {true, true, front, back};
  }
  if (front == back) {
    return {true, false, front, front};
  }
  const NativeCullRasterState cull = DecodeNativeCullRasterState(cull_mode);
  if (!cull.valid()) {
    return {};
  }
  if (cull.cull_face == NativeCullFace::kFront) {
    return {true, false, back, back};
  }
  if (cull.cull_face == NativeCullFace::kBack) {
    return {true, false, front, front};
  }
  return {};
}

}  // namespace rex::graphics::gta4_native

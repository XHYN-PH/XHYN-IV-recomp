#pragma once
#include <cstdint>
#include <optional>
#include <initializer_list>
#include <string_view>

namespace rex::graphics::gta4_native {
// FSR1 spatial presets from AMD's integration guide. Fractions make resolution
// rounding deterministic on all hosts. Quality has no effect in Native mode.
struct Fsr1Scale { uint32_t numerator, denominator; };
constexpr std::optional<Fsr1Scale> Fsr1QualityScale(std::string_view quality) {
  if (quality == "ultra_quality") return Fsr1Scale{13, 10};
  if (quality == "quality") return Fsr1Scale{3, 2};
  if (quality == "balanced") return Fsr1Scale{17, 10};
  if (quality == "performance") return Fsr1Scale{2, 1};
  return std::nullopt;
}
enum class Fsr1Selection : uint8_t {
  kNative, kEnabled, kSupersampling, kHdrInputUnsupported, kBelowRenderFloor, kInvalidQuality,
  kAdjustedQuality
};
struct Fsr1RenderExtent {
  uint32_t width, height;
  Fsr1Selection selection = Fsr1Selection::kNative;
  constexpr bool active() const { return selection == Fsr1Selection::kEnabled || selection == Fsr1Selection::kAdjustedQuality; }
};
constexpr Fsr1RenderExtent SelectFsr1RenderExtent(uint32_t display_width, uint32_t display_height,
    bool requested, std::string_view quality, bool supersampling, bool hdr,
    bool perceptual_presentation_before_hdr) {
  Fsr1RenderExtent result{display_width, display_height};
  if (!requested) return result;
  if (supersampling) { result.selection = Fsr1Selection::kSupersampling; return result; }
  if (hdr && !perceptual_presentation_before_hdr) {
    result.selection = Fsr1Selection::kHdrInputUnsupported; return result;
  }
  const auto scale = Fsr1QualityScale(quality);
  if (!scale) { result.selection = Fsr1Selection::kInvalidQuality; return result; }
  const auto divide = [&](uint32_t value) {
    return uint32_t((uint64_t(value) * scale->denominator + scale->numerator / 2) / scale->numerator);
  };
  const uint32_t width = divide(display_width), height = divide(display_height);
  // Preserve the title's validated minimum scene size, not just the API limit.
  if (width < 640 || height < 360) {
    // Try the next compatible spatial preset on wide displays while keeping
    // the title's validated scene minimum and the selected output aspect.
    for (auto compatible : {std::string_view("balanced"),std::string_view("quality"),std::string_view("ultra_quality")}) {
      const auto safer=Fsr1QualityScale(compatible);
      if (uint64_t(safer->numerator)*scale->denominator >= uint64_t(scale->numerator)*safer->denominator) continue;
      const auto candidate=[&](uint32_t value){return uint32_t((uint64_t(value)*safer->denominator+safer->numerator/2)/safer->numerator);};
      const uint32_t w=candidate(display_width),h=candidate(display_height);
      if(w>=640&&h>=360){result.width=w;result.height=h;result.selection=Fsr1Selection::kAdjustedQuality;return result;}
    }
    result.selection = Fsr1Selection::kBelowRenderFloor; return result;
  }
  result.width = width; result.height = height; result.selection = Fsr1Selection::kEnabled;
  return result;
}
// Application quality presets, not fixed MetalFX API modes. MetalFX accepts
// continuous input/output ratios. Keep the same four menu quality ratios and
// the title's validated render floor for predictable switching between scalers.
constexpr Fsr1RenderExtent SelectMetalFxRenderExtent(uint32_t width,uint32_t height,
    bool requested,std::string_view quality,bool supersampling){
  return SelectFsr1RenderExtent(width,height,requested,quality,supersampling,false,true);
}
}  // namespace rex::graphics::gta4_native

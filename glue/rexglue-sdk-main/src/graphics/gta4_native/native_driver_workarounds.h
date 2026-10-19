#pragma once
#include <string_view>
namespace rex::graphics::gta4_native {
inline bool NativeLegacyLightGuard(std::string_view, bool turnip) {
  // Upstream's light-volume shader workaround is for Turnip, not one GPU
  // model. Re-enabling it on 7xx devices can stall the GPU after the intro.
  return turnip;
}
}

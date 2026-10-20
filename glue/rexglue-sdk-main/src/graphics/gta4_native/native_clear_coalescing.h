#pragma once
#include <algorithm>
#include <iterator>
namespace rex::graphics::gta4_native {
// Only adjacent clears of identical captured attachments may use this test.
template <typename Clear> bool NativeClearOverwritten(const Clear& a, const Clear& b) {
  return a.device == b.device && a.flags == b.flags && a.depth_bits == b.depth_bits &&
         a.stencil == b.stencil && std::equal(std::begin(a.color_bits), std::end(a.color_bits), std::begin(b.color_bits)) &&
         a.left < a.right && a.top < a.bottom && b.left <= a.left && b.top <= a.top &&
         b.right >= a.right && b.bottom >= a.bottom;
}
}

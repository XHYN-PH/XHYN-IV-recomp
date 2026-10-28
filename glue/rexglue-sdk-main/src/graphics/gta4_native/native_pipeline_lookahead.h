#pragma once
#include <cstddef>
#include <cstdint>
#include <array>
namespace rex::graphics::gta4_native {
// Reach late-frame materials even when the wall-time budget expires early.
// The first visit remains the imminent draw. No renderer state is mutated.
constexpr size_t NativePipelineLookaheadIndex(size_t visit, size_t commands) {
  unsigned reversed = 0;
  for (unsigned bit = 0; bit < 8; ++bit) reversed = (reversed << 1) | ((visit >> bit) & 1);
  return commands ? (commands / 256) * reversed + ((commands % 256) * reversed) / 256 : 0;
}
struct NativeShaderPair {
  uint64_t vertex=0,pixel=0;
  bool operator==(const NativeShaderPair&)const=default;
};
class NativePreparedShaderPairs {
  std::array<NativeShaderPair,1024> pairs_{};
  static size_t Slot(NativeShaderPair p){return size_t((p.vertex^(p.pixel*1099511628211ull))%1024);}
 public:
  bool Contains(uint64_t v,uint64_t p)const{return v&&pairs_[Slot({v,p})]==NativeShaderPair{v,p};}
  void Publish(uint64_t v,uint64_t p){if(v)pairs_[Slot({v,p})]={v,p};}
};
}

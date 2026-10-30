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
// A scheduling hint only: a fingerprint never supplies a Vulkan pipeline.
// Normal draws still resolve and validate their complete exact pipeline key.
struct NativePipelinePreparationTarget {
  uint32_t flags=0,format=0,width=0,height=0,samples=0,packed_dimensions=0;
};
template<size_t Colors,size_t Streams>
struct NativePipelinePreparationInputs {
  uint64_t vertex_shader=0,pixel_shader=0;
  uint64_t vertex_identity=0,pixel_identity=0;
  uint64_t declaration=0,fixed_state=0,lifetime=0,policy=0;
  std::array<uint32_t,Streams> strides{};
  std::array<NativePipelinePreparationTarget,Colors+1> targets{};
  uint32_t primitive=0,user_stride=0;
  bool primitive_restart=false;
  uint64_t Signature()const {
    uint64_t hash=0xcbf29ce484222325ull;
    const auto mix=[&](uint64_t value){hash=(hash^value)*0x9e3779b97f4a7c15ull;hash^=hash>>29;};
    for(auto value:{vertex_shader,pixel_shader,vertex_identity,pixel_identity,declaration,fixed_state,lifetime,policy})mix(value);
    for(auto stride:strides)mix(stride);
    for(const auto& t:targets){mix(t.flags);mix(t.format);mix(t.width);mix(t.height);mix(t.samples);mix(t.packed_dimensions);}
    mix(primitive);mix(user_stride);mix(primitive_restart);
    return hash?hash:1;
  }
};
// Successful preparation of a shader pair does not cover its other blend,
// depth, declaration, stride, topology or attachment variants. Remember the
// full input fingerprint only after an exact ready pipeline was returned.
// Hash collisions can defer speculative work; they cannot reuse a wrong PSO.
template<size_t Capacity=2048>
class NativePreparedPipelineInputs {
  static_assert(Capacity>0);
  std::array<uint64_t,Capacity> ready_{};
 public:
  bool Contains(uint64_t signature)const{return signature&&ready_[signature%Capacity]==signature;}
  void RememberReady(uint64_t signature){if(signature)ready_[signature%Capacity]=signature;}
};
}

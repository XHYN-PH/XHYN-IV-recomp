#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>

namespace rex::graphics::gta4_native {

// Tracks commands actually emitted into one command buffer. This owns no GPU
// resources. Reset at recording boundaries and after any external draw path.
// Floating-point state is passed as bits, preserving signed zero and NaN values.
template <size_t DescriptorSetCount>
class NativeDrawStateCache {
 public:
  void Reset() { *this = {}; }

  bool UpdatePipeline(uint64_t pipeline, bool full_dynamic_state = false) {
    if (!pipeline_.Update(pipeline)) {
      return false;
    }
    const bool retain = full_dynamic_state && full_dynamic_state_;
    full_dynamic_state_ = full_dynamic_state;
    if (retain) return true;
    // Unknown or mixed static/dynamic pipelines invalidate these values.
    viewport_ = {};
    scissor_ = {};
    depth_bias_ = {};
    stencil_ = {};
    blend_constants_ = {};
    push_constants_ = {};
    return true;
  }

  struct DescriptorSpan { uint32_t first=0,count=0; };
  DescriptorSpan ChangedDescriptors(uint64_t layout,const std::array<uint64_t,DescriptorSetCount>& sets) {
    uint32_t first=0,last=DescriptorSetCount;
    if(descriptors_.last&&descriptors_.last->layout==layout){
      while(first<last&&descriptors_.last->values[first]==sets[first])++first;
      if(first==last)return {0,0};
      while(last>first&&descriptors_.last->values[last-1]==sets[last-1])--last;
    }
    descriptors_.last=LayoutValues<DescriptorSetCount>{layout,sets};return {first,last-first};
  }
  bool UpdateDescriptors(uint64_t layout,const std::array<uint64_t,DescriptorSetCount>& sets){return ChangedDescriptors(layout,sets).count!=0;}
  bool UpdateViewport(const std::array<uint32_t, 6>& bits) { return viewport_.Update(bits); }
  bool UpdateScissor(const std::array<int64_t, 4>& rectangle) {
    return scissor_.Update(rectangle);
  }
  bool UpdateDepthBias(const std::array<uint32_t, 3>& bits) { return depth_bias_.Update(bits); }
  bool UpdateStencil(const std::array<uint32_t, 6>& faces) { return stencil_.Update(faces); }
  bool UpdateBlendConstants(const std::array<uint32_t, 4>& bits) {
    return blend_constants_.Update(bits);
  }
  bool UpdatePushConstants(uint64_t layout, const std::array<uint64_t, 3>& addresses) {
    return push_constants_.Update({layout, addresses});
  }
  bool UpdateIndexBuffer(uint64_t command_buffer,uint64_t buffer,uint64_t offset,uint32_t type) {
    return index_buffer_.Update({command_buffer,buffer,offset,type});
  }
  // Vertex buffer bindings are command-buffer state that survives pipeline
  // and render-pass changes; the command buffer is part of the value.
  bool UpdateVertexBuffer(uint64_t command_buffer, uint32_t binding, uint64_t buffer,
                          uint64_t offset) {
    if (binding >= vertex_buffers_.size()) return true;
    return vertex_buffers_[binding].Update({command_buffer, buffer, offset});
  }

 private:
  template <typename Value>
  struct Tracked {
    std::optional<Value> last;
    bool Update(const Value& value) {
      if (last && *last == value) {
        return false;
      }
      last = value;
      return true;
    }
  };
  template <size_t Count>
  struct LayoutValues {
    uint64_t layout;
    std::array<uint64_t, Count> values;
    bool operator==(const LayoutValues&) const = default;
  };

  Tracked<std::array<uint64_t,4>> index_buffer_;
  Tracked<uint64_t> pipeline_;
  bool full_dynamic_state_ = false;
  Tracked<LayoutValues<DescriptorSetCount>> descriptors_;
  Tracked<std::array<uint32_t, 6>> viewport_;
  Tracked<std::array<int64_t, 4>> scissor_;
  Tracked<std::array<uint32_t, 3>> depth_bias_;
  Tracked<std::array<uint32_t, 6>> stencil_;
  Tracked<std::array<uint32_t, 4>> blend_constants_;
  Tracked<LayoutValues<3>> push_constants_;
  std::array<Tracked<std::array<uint64_t, 3>>, 32> vertex_buffers_;
};

}  // namespace rex::graphics::gta4_native

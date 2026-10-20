#pragma once
#include <cstring>
#include <memory>
#include <type_traits>

namespace rex::graphics::gta4_native {
// Shared snapshots stay immutable while older queued frames retain them.
// A hash alone is insufficient: compare bytes even for equal hashes.
template<typename T> class ImmutableSnapshot {
 public:
  static_assert(std::is_trivially_copyable_v<T>);
  std::shared_ptr<const T> Get(const T& source) {
    if(!value_||std::memcmp(value_.get(),&source,sizeof(T))!=0)value_=std::make_shared<const T>(source);
    return value_;
  }
 private:
  std::shared_ptr<const T> value_;
};
}

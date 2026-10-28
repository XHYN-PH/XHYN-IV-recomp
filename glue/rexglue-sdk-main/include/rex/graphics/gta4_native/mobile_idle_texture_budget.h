#pragma once
#include <array>
#include <cstddef>
#include <cstdint>
namespace rex::graphics::gta4_native {
// Recorder-owned extra idle retention, independent of the active working set.
// Reservations belong to images and are returned on use or retirement.
template<size_t Heaps> class MobileIdleTextureBudget {
 public:
  bool Reserve(size_t heap,uint64_t bytes,uint64_t heap_limit,uint64_t total_limit,
               uint64_t& reservation) {
    if(heap>=Heaps)return false;
    if(reservation && heaps_[heap]<=heap_limit && total_<=total_limit)return true;
    Release(heap,reservation);
    if(!bytes || heaps_[heap]>heap_limit || bytes>heap_limit-heaps_[heap] ||
       total_>total_limit || bytes>total_limit-total_)return false;
    heaps_[heap]+=bytes;total_+=bytes;reservation=bytes;return true;
  }
  void Release(size_t heap,uint64_t& reservation) {
    if(!reservation)return;
    if(heap<Heaps){heaps_[heap]-=reservation;total_-=reservation;}
    reservation=0;
  }
  uint64_t bytes()const{return total_;}
 private:
  std::array<uint64_t,Heaps> heaps_{};
  uint64_t total_=0;
};
}

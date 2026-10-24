#pragma once
#include <cstdint>

namespace gta4::input {
// A quick tap is momentary. A deliberate hold latches until the next press.
class AimHoldPolicy {
 public:
  bool Update(bool down, uint64_t now_ms, uint64_t threshold_ms, bool allowed) noexcept {
    if (!allowed) { Reset(); return false; }
    if (down && !down_) {
      began_ = now_ms;
      suppress_ = locked_;
      if (locked_) locked_ = false;
    }
    if (down && !suppress_ && now_ms >= began_ && now_ms - began_ >= threshold_ms)
      locked_ = true;
    down_ = down;
    if (!down) suppress_ = false;
    return locked_ || (down && !suppress_);
  }
  void Reset() noexcept { locked_ = down_ = suppress_ = false; began_ = 0; }
  bool locked() const noexcept { return locked_; }
 private:
  bool locked_ = false, down_ = false, suppress_ = false;
  uint64_t began_ = 0;
};
}

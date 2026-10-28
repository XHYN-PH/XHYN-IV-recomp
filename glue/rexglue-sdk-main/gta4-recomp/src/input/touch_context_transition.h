#pragma once

#include <cstdint>

namespace gta4::input {

// Hold a newly observed gameplay layout for a configurable duration. A zero
// duration always uses the first observation; suspended UI bypasses the hold.
template <typename Mode>
class TouchContextTransition {
 public:
  Mode Update(Mode requested, Mode current, uint64_t now_ms, int32_t delay_ms,
              bool allow_hold) {
    if (!initialized_ || requested != pending_ || now_ms < since_ms_) {
      initialized_ = true;
      pending_ = requested;
      since_ms_ = now_ms;
    }
    if (allow_hold && requested != current && delay_ms > 0 &&
        now_ms - since_ms_ < uint64_t(delay_ms)) {
      return current;
    }
    return requested;
  }

 private:
  bool initialized_ = false;
  Mode pending_{};
  uint64_t since_ms_ = 0;
};

}  // namespace gta4::input

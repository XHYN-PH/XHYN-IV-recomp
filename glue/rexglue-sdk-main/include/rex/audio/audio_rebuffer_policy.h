#pragma once

#include <algorithm>
#include <cstddef>

namespace rex::audio {
enum class AudioQueueDecision { kPlay, kEmpty, kRefill };

// At 48 kHz / 256 samples, 2..6 blocks is about 11..32 ms. Repeated
// underruns grow recovery headroom; two stable seconds restore low latency.
class AudioRebufferPolicy {
 public:
  AudioQueueDecision Next(size_t queued, bool adaptive) {
    if (!queued) {
      if (!recovering_) {
        if (adaptive && played_ && stable_ < 375) target_ = std::min(6u, target_ + 1);
        stable_ = 0;
      }
      recovering_ = true;
      return AudioQueueDecision::kEmpty;
    }
    if (adaptive && recovering_ && queued < target_) return AudioQueueDecision::kRefill;
    recovering_ = false;
    played_ = true;
    stable_ = std::min(375u, stable_ + 1);
    if (stable_ == 375) target_ = 2;
    return AudioQueueDecision::kPlay;
  }
  unsigned target_blocks() const { return target_; }
  void Reset() { *this = {}; }
 private:
  bool recovering_ = true, played_ = false;
  unsigned target_ = 2, stable_ = 0;
};
}  // namespace rex::audio

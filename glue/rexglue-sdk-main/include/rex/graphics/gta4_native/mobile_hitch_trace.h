#pragma once

#include <array>
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace rex::graphics::gta4_native {

struct MobilePresentGap {
  bool boundary = false;
  uint64_t interval_ns = 0, outside_publish_ns = 0;
};

// Pause revisions survive a complete open/close between consecutive presents.
// Merely reading the current paused flag cannot identify that transition.
class MobilePresentClock {
 public:
  MobilePresentGap Observe(uint64_t begin, uint64_t end, bool published,
                           bool paused, uint32_t revision) {
    MobilePresentGap result;
    result.boundary = paused || revision != revision_ || end < begin ||
                      (last_ && end < last_);
    revision_ = revision;
    if (!result.boundary && last_ && published) {
      result.interval_ns = end - last_;
      result.outside_publish_ns = begin > last_ ? begin - last_ : 0;
    }
    if (result.boundary) last_ = 0;
    if (published && !paused && end >= begin) last_ = end;
    return result;
  }
 private:
  uint64_t last_ = 0;
  uint32_t revision_ = 0;
};

struct MobileHitchRow {
  uint64_t end_ns = 0, interval_ns = 0, publish_ns = 0, outside_publish_ns = 0;
  uint64_t pipeline_wait_ticks = 0, compile_ticks = 0, evictions = 0, reuploads = 0;
  uint32_t frame = 0;
};

class MobileHitchHistory {
 public:
  static constexpr size_t kRecentCaptureSlots = 12;
  static constexpr size_t kWorstCaptureSlots = 4;
  // A pause resets recent timing history, not the session's severe evidence.
  void Reset() { size_ = cursor_ = 0; worst_capture_.reset(); }
  bool Record(const MobileHitchRow& row, uint64_t threshold_ns = 100'000'000) {
    worst_capture_.reset();
    rows_[cursor_] = row;
    cursor_ = (cursor_ + 1) % rows_.size();
    if (size_ < rows_.size()) ++size_;
    if (row.interval_ns < threshold_ns ||
        (last_capture_ && row.end_ns >= last_capture_ &&
         row.end_ns - last_capture_ < 2'000'000'000)) return false;
    last_capture_ = row.end_ns;
    ++captures_;
    const auto smallest = std::min_element(worst_intervals_.begin(), worst_intervals_.end());
    if (row.interval_ns > *smallest) {
      *smallest = row.interval_ns;
      worst_capture_ = size_t(smallest - worst_intervals_.begin());
    }
    return true;
  }
  uint64_t sequence() const { return captures_; }
  std::optional<size_t> worst_capture_slot() const { return worst_capture_; }
  std::vector<MobileHitchRow> Snapshot() const {
    std::vector<MobileHitchRow> result;
    result.reserve(size_);
    for (size_t i = 0; i < size_; ++i)
      result.push_back(rows_[(cursor_ + rows_.size() - size_ + i) % rows_.size()]);
    return result;
  }
 private:
  std::array<MobileHitchRow, 64> rows_{};
  size_t size_ = 0, cursor_ = 0;
  uint64_t last_capture_ = 0;
  uint64_t captures_ = 0;
  std::array<uint64_t, kWorstCaptureSlots> worst_intervals_{};
  std::optional<size_t> worst_capture_;
};
}  // namespace rex::graphics::gta4_native

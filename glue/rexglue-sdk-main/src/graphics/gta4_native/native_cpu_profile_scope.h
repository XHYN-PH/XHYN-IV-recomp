#pragma once
#include <rex/chrono/clock.h>
#include <type_traits>
#include <utility>
#include "native_cpu_profile.h"

namespace rex::graphics::gta4_native::profile {
inline thread_local CpuRecorder* current_cpu_recorder = nullptr;
inline uint64_t CpuTick() {
  return rex::chrono::Clock::QueryHostTickCount();
}
class CpuScope {
 public:
  explicit CpuScope(CpuOp op) : owner_(current_cpu_recorder) {
    if (owner_ && owner_->active())
      token_ = owner_->Enter(op, CpuTick());
  }
  ~CpuScope() { End(); }
  void End() {
    if (token_) {
      owner_->Leave(token_, CpuTick());
      token_ = {};
    }
  }
  CpuScope(const CpuScope&) = delete;
  CpuScope& operator=(const CpuScope&) = delete;

 private:
  CpuRecorder* owner_ = nullptr;
  CpuRecorder::Token token_{};
};
class CpuPhaseScope {
 public:
  explicit CpuPhaseScope(CpuPhase phase) : owner_(current_cpu_recorder && current_cpu_recorder->active() ? current_cpu_recorder : nullptr) {
    if (owner_)
      prior_ = owner_->SetPhase(phase);
  }
  ~CpuPhaseScope() {
    if (owner_)
      owner_->SetPhase(prior_);
  }
  void Set(CpuPhase phase) {
    if (owner_)
      owner_->SetPhase(phase);
  }

 private:
  CpuRecorder* owner_ = nullptr;
  CpuPhase prior_ = CpuPhase::kPublish;
};
class CpuContextScope {
 public:
  explicit CpuContextScope(CpuContext context) : owner_(current_cpu_recorder && current_cpu_recorder->active() ? current_cpu_recorder : nullptr) {
    if (owner_)
      prior_ = owner_->SetContext(context);
  }
  // Builds the context only while a capture is running: per-command contexts
  // otherwise chase pipeline and shader pointers for nothing.
  template <typename MakeContext,
            typename = std::enable_if_t<std::is_invocable_r_v<CpuContext, MakeContext&>>>
  explicit CpuContextScope(MakeContext&& make)
      : owner_(current_cpu_recorder && current_cpu_recorder->active() ? current_cpu_recorder
                                                                       : nullptr) {
    if (owner_)
      prior_ = owner_->SetContext(make());
  }
  ~CpuContextScope() {
    if (owner_)
      owner_->SetContext(prior_);
  }

 private:
  CpuRecorder* owner_ = nullptr;
  CpuContext prior_{};
};
class CpuRecorderBinding {
 public:
  explicit CpuRecorderBinding(CpuRecorder* recorder) : prior_(current_cpu_recorder) {
    current_cpu_recorder = recorder;
  }
  ~CpuRecorderBinding() { current_cpu_recorder = prior_; }

 private:
  CpuRecorder* prior_;
};
template <typename Fn>
decltype(auto) CpuCall(CpuOp op, Fn&& fn) {
  CpuScope scope(op);
  return std::forward<Fn>(fn)();
}
inline uint64_t CalibrateCpuClock() {
  uint64_t best = UINT64_MAX;
  for (unsigned i = 0; i < 256; ++i) {
    auto a = CpuTick();
    auto b = CpuTick();
    if (b >= a)
      best = std::min(best, b - a);
  }
  return best == UINT64_MAX ? 0 : best;
}
}  // namespace rex::graphics::gta4_native::profile

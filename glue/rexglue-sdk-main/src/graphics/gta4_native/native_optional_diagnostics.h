#pragma once
#include <atomic>
namespace rex::graphics::gta4_native {
inline std::atomic<bool> native_optional_diagnostics_enabled{false};
// Investigation-only counters never participate in rendering decisions.
template <typename T> class NativeOptionalDiagnosticCounter {
 public:
  constexpr NativeOptionalDiagnosticCounter(T value = 0) : value_(value) {}
  T operator++() { return Add(1); }
  T operator+=(T amount) { return Add(amount); }
  T fetch_add(T amount, std::memory_order = std::memory_order_relaxed) {
    return native_optional_diagnostics_enabled.load(std::memory_order_relaxed)
        ? value_.fetch_add(amount, std::memory_order_relaxed) : T{};
  }
  T exchange(T value) { return value_.exchange(value, std::memory_order_relaxed); }
  T load() const { return value_.load(std::memory_order_relaxed); }
 private:
  T Add(T amount) {
    if (!native_optional_diagnostics_enabled.load(std::memory_order_relaxed)) return T{};
    return value_.fetch_add(amount, std::memory_order_relaxed) + amount;
  }
  std::atomic<T> value_{0};
};
}

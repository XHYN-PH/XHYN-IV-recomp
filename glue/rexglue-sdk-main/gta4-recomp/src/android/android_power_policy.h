#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <optional>
namespace gta4::android_power {
struct ThermalPressure { bool hot=false,cool=false; };
inline ThermalPressure ClassifyThermals(double headroom,int status,double gpu_c,
                                        double hot_c,double cool_c) noexcept {
  const bool prediction=std::isfinite(headroom)&&headroom>=0;
  const bool severity=status>=0;
  if(prediction||severity){
    // A severe current reading must not be overruled by a cooler prediction.
    return {(prediction&&headroom>=.95)||(severity&&status>=3),
            (!prediction||headroom<=.75)&&(!severity||status<=1)};
  }
  return std::isfinite(gpu_c)?ThermalPressure{gpu_c>=hot_c,gpu_c<=cool_c}:ThermalPressure{};
}
inline std::atomic<bool> thermal_pressure{false};
inline int64_t FrameBudgetNanoseconds(uint32_t cap) { return 1'000'000'000ll / std::clamp(cap ? cap : 60u, 10u, 240u); }
inline int64_t HintTargetNanoseconds(uint32_t cap, double ms) {
  return std::isfinite(ms) && ms > 0 ? int64_t(std::clamp(ms,1.0,100.0)*1e6) : FrameBudgetNanoseconds(cap)*9/10;
}
class ThermalRecoveryHold {
  std::optional<uint64_t> cool_since_;
 public:
  bool Update(uint64_t now,bool hot,bool cool){if(hot||!cool){cool_since_.reset();return false;}if(!cool_since_)cool_since_=now;return now>=*cool_since_&&now-*cool_since_>=10000;}
};
class ThermalDrawDistance {
 public:
  bool lowered() const { return saved_.has_value(); }
  std::optional<double> Update(double current, bool enabled, bool hot, bool cool) {
    if (!std::isfinite(current) || current <= 0) return {};
    if (saved_ && std::abs(current-applied_) > 0.00001) { saved_.reset(); manual_override_ = true; }
    if (!enabled || cool) { manual_override_ = false; auto restore = saved_; saved_.reset(); return restore; }
    if (!saved_ && !manual_override_ && hot) {
      const double value = std::min(current, std::max(0.5,current*0.7));
      if (value < current) { saved_ = current; applied_ = value; return value; }
    }
    return {};
  }
 private:
  std::optional<double> saved_;
  double applied_ = 0;
  bool manual_override_ = false;
};
}

#pragma once
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace rex::graphics::gta4_native {
// Render-worker-only, fixed storage. No GPU queries or allocations per frame.
template<size_t Capacity> class MobileFrameStatsStorage {
 public:
  struct Summary {
    double seconds=0,published_fps=0,interval_p50_ms=0,interval_p95_ms=0,interval_max_ms=0;
    double publish_p50_ms=0,publish_p95_ms=0,publish_max_ms=0;
    uint64_t published=0,failed=0,over_50ms=0,interval_samples=0;
  };
  bool Active()const{return start_!=0;}
  void Start(uint64_t now) {if(!start_)start_=now;}
  void Record(uint64_t now,uint64_t wall_ns,bool published) {
    Start(now);
    const double wall=double(wall_ns)/1000000.;
    walls_[wall_count_%walls_.size()]=wall;++wall_count_;wall_max_=std::max(wall_max_,wall);
    if(!published){++failed_;return;}
    ++published_;
    if(last_success_&&now>=last_success_) {
      const double ms=double(now-last_success_)/1000000.;
      intervals_[interval_count_%intervals_.size()]=ms;++interval_count_;
      interval_max_=std::max(interval_max_,ms);over_50ms_+=ms>50.;
    }
    last_success_=now;
  }
  bool Ready(uint64_t now,uint64_t interval_ns=2000000000ull)const {
    return start_&&now>=start_&&now-start_>=interval_ns;
  }
  Summary Take(uint64_t now) {
    Summary s;s.seconds=start_&&now>start_?double(now-start_)/1e9:0;
    s.published=published_;s.failed=failed_;s.over_50ms=over_50ms_;s.interval_samples=interval_count_;
    s.published_fps=s.seconds>0?published_/s.seconds:0;
    Quantiles(intervals_,interval_count_,s.interval_p50_ms,s.interval_p95_ms);
    Quantiles(walls_,wall_count_,s.publish_p50_ms,s.publish_p95_ms);
    s.interval_max_ms=interval_max_;s.publish_max_ms=wall_max_;
    start_=now;published_=failed_=over_50ms_=wall_count_=interval_count_=0;interval_max_=wall_max_=0;
    return s;
  }
  void Reset(){start_=last_success_=published_=failed_=over_50ms_=wall_count_=interval_count_=0;interval_max_=wall_max_=0;}
 private:
  static void Quantiles(const std::array<double,Capacity>& stored,uint64_t count,double& p50,double& p95) {
    std::vector<double> values(stored.begin(),stored.begin()+std::min<uint64_t>(count,stored.size()));
    const size_t n=std::min<uint64_t>(count,values.size());if(!n){p50=p95=0;return;}
    std::sort(values.begin(),values.begin()+n);
    p50=values[(n-1)*50/100];p95=values[(n*95+99)/100-1];
  }
  uint64_t start_=0,last_success_=0,published_=0,failed_=0,over_50ms_=0,wall_count_=0,interval_count_=0;
  double interval_max_=0,wall_max_=0;
  std::array<double,Capacity> intervals_{},walls_{};
};
using MobileFrameStats=MobileFrameStatsStorage<512>;
using BenchmarkFrameStats=MobileFrameStatsStorage<32768>;
}  // namespace rex::graphics::gta4_native

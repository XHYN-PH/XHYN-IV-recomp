#pragma once
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <mutex>
#include <string>

namespace rex::graphics::gta4_native {
struct MobileMemory {
  uint64_t total=0,available=0;
  uint64_t TextureRetentionLimit()const {
    if(!total)return 0;
    const uint64_t reserve=std::clamp<uint64_t>(total/20,192ull<<20,768ull<<20);
    if(available<=reserve)return 0;
    return std::min(std::clamp<uint64_t>(total/24,96ull<<20,512ull<<20),(available-reserve)/2);
  }
  bool pressure()const {
    const uint64_t reserve=std::clamp<uint64_t>(total/20,192ull<<20,768ull<<20);
    return total&&available<reserve;
  }
  uint64_t TextureFallbackLimit(uint64_t resident)const {
    if(!total)return 0;
    const uint64_t reserve=std::clamp<uint64_t>(total/20,192ull<<20,768ull<<20);
    const uint64_t headroom=available>reserve?(available-reserve)/2:0;
    const uint64_t ceiling=resident>UINT64_MAX-headroom?UINT64_MAX:resident+headroom;
    return std::min(TextureFallbackLimit(),std::max<uint64_t>(128ull<<20,ceiling));
  }
  uint64_t TextureFallbackLimit()const {
    return total?std::clamp<uint64_t>(total/12,128ull<<20,1024ull<<20):0;
  }
};
// At most one small procfs read per two seconds. Unknown data never forces
// pressure or changes a working Vulkan budget.
inline MobileMemory ReadMobileMemory() {
#if defined(__ANDROID__)
  static std::mutex mutex;static auto last=std::chrono::steady_clock::time_point{};static MobileMemory cached;
  std::lock_guard lock(mutex);const auto now=std::chrono::steady_clock::now();
  if(last.time_since_epoch().count()&&now-last<std::chrono::seconds(2))return cached;
  last=now;std::ifstream file("/proc/meminfo");std::string line;MobileMemory next;bool have_available=false;
  while(std::getline(file,line)) {
    if(line.starts_with("MemTotal:")||line.starts_with("MemAvailable:")) {
      const auto colon=line.find(':');char* end=nullptr;const auto kb=std::strtoull(line.c_str()+colon+1,&end,10);
      if(kb>UINT64_MAX/1024)continue;
      if(line.starts_with("MemTotal:"))next.total=kb*1024;else {next.available=kb*1024;have_available=true;}
    }
    if(next.total&&have_available)break;
  }
  cached=next.total&&have_available?next:MobileMemory{};return cached;
#else
  return {};
#endif
}
inline bool MobilePrefetchMemoryPressure() {
  // Entity admission is a hot path. Sample infrequently on each producer,
  // rather than locking/reading a clock for every entity considered.
  thread_local unsigned admission=511;thread_local bool pressure=false;
  if(++admission>=512){admission=0;pressure=ReadMobileMemory().pressure();}
  return pressure;
}

}

#pragma once
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
namespace rex::graphics::gta4_native {
inline uint32_t MobileTextureRetentionFrames(uint32_t cap,uint64_t resident,uint64_t soft_limit,bool pressure){
 if(pressure)return 0;
 if(!soft_limit||resident>=soft_limit)return 600;
 return std::max(600u,std::clamp(cap?cap:60u,20u,240u)*60u);
}
inline double MobileAdaptiveTarget(uint32_t cap){return double(std::clamp(cap?cap:60u,10u,240u));}
inline bool MobileAdaptiveSlow(double fps,double target){return fps<target*.9;}
inline bool MobileAdaptiveRecovered(double fps,double target){return fps>=target*.95;}
inline bool MobileDecodeParallel(unsigned cpus,uint32_t rows,size_t bytes){return cpus>2&&rows>=16&&bytes>=(cpus<=4?512u*1024:128u*1024);}
inline uint64_t MobileTextureContentIdentity(uint64_t hash,uint32_t format,uint32_t width,uint32_t height){
 if(!hash)return 0;for(uint32_t v:{format,width,height})hash=(hash^v)*1099511628211ull;return hash?hash:1;
}
// Bounded, direct-mapped diagnostic sample. Collisions can miss reuse.
template<size_t Capacity> class MobileTextureReuseSample {
 static_assert(Capacity>0);struct Entry{uint64_t identity=0,frame=0;};std::array<Entry,Capacity> entries_{};
 static size_t Slot(uint64_t id){return size_t((id^(id>>32))%Capacity);}
 public:
 void Evicted(uint64_t id,uint64_t frame){if(id)entries_[Slot(id)]={id,frame};}
 std::optional<uint64_t> Reuploaded(uint64_t id,uint64_t frame){if(!id)return {};auto& e=entries_[Slot(id)];if(e.identity!=id||frame<e.frame)return {};auto age=frame-e.frame;e={};return age;}
 void Reset(){entries_.fill({});}
};
}

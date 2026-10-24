#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <mutex>

namespace gta4::input {
struct PhoneGyroDelta { double x=0,y=0;bool active=false; };
// A relative sample is consumed once. No old movement survives a pause or stall.
class PhoneGyroBuffer {
 public:
  void Publish(double x,double y,uint64_t now){
    std::lock_guard lock(mutex_);
    if(!std::isfinite(x)||!std::isfinite(y))return;
    if(now<last_||now-last_>150)delta_={};
    delta_.x=std::clamp(delta_.x+x,-128.0,128.0);
    delta_.y=std::clamp(delta_.y+y,-128.0,128.0);delta_.active=true;last_=now;
  }
  PhoneGyroDelta Consume(uint64_t now,bool allowed){
    std::lock_guard lock(mutex_);auto result=delta_;delta_={};
    if(!allowed||now<last_||now-last_>150)return {};
    return result;
  }
  void Reset(){std::lock_guard lock(mutex_);delta_={};last_=0;}
 private:std::mutex mutex_;PhoneGyroDelta delta_;uint64_t last_=0;
};
inline PhoneGyroBuffer phone_gyro;
}

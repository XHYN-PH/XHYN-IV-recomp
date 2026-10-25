#pragma once

#include <cstdint>
#include <limits>
#include <algorithm>
#include <cmath>

namespace rex::input::sdl {

// SDL thumb Y grows downward; XInput grows upward. Both define zero as
// centered. Bitwise complement shifts centered input to -1, so invert the
// signed magnitude and saturate the asymmetric negative endpoint instead.
constexpr int16_t ToXInputThumbY(int16_t axis) {
  return axis == std::numeric_limits<int16_t>::min()
             ? std::numeric_limits<int16_t>::max()
             : static_cast<int16_t>(-axis);
}

inline void TuneThumbs(int16_t& x,int16_t& y,double deadzone,double sensitivity,double exponent,bool invert_x,bool invert_y){
  if(deadzone==0&&sensitivity==1&&exponent==1&&!invert_x&&!invert_y)return;
  deadzone=std::isfinite(deadzone)?std::clamp(deadzone,0.0,.4):0;
  sensitivity=std::isfinite(sensitivity)?std::clamp(sensitivity,.25,2.0):1;
  exponent=std::isfinite(exponent)?std::clamp(exponent,.5,2.0):1;
  double nx=double(x)/32768.0,ny=double(y)/32768.0,length=std::hypot(nx,ny);
  if(length<=deadzone){x=y=0;return;}
  double magnitude=std::min(1.0,std::pow(std::min(1.0,(length-deadzone)/(1-deadzone)),exponent)*sensitivity);
  auto convert=[](double v){return int16_t(std::clamp(std::lround(v*32768),-32768l,32767l));};
  x=convert(nx/length*magnitude*(invert_x?-1:1));y=convert(ny/length*magnitude*(invert_y?-1:1));
}
inline uint8_t TuneTrigger(uint8_t value,double threshold){if(!std::isfinite(threshold)||threshold<=0)return value;threshold=std::clamp(threshold,0.0,.4);return uint8_t(std::clamp(std::lround((double(value)/255-threshold)/(1-threshold)*255),0l,255l));}
}  // namespace rex::input::sdl

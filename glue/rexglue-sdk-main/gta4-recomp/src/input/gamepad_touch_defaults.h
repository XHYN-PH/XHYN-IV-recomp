#pragma once
#include <array>

namespace gta4::input {
// x = width*xf + height*(xh + xs*button_scale); y is analogous.
// Shared with the offline editor through generate_touch_defaults.py.
struct XboxControlDefault {
  const char* id;
  const char* label;
  float xf,xh,xs,yf,ys,r;
  bool buttons,camera;
};
inline constexpr std::array<XboxControlDefault,18> kXboxControlDefaults={{
  {"left_stick","L",.12f,0,0,.72f,0,.105f,true,false},
  {"right_stick","R",.75f,0,0,.60f,0,.095f,false,true},
  {"dpad_up","UP",.12f,.26f,0,.86f,-.078f,.042f,true,false},
  {"dpad_down","DOWN",.12f,.26f,0,.86f,.078f,.042f,true,false},
  {"dpad_left","LEFT",.12f,.26f,-.078f,.86f,0,.042f,true,false},
  {"dpad_right","RIGHT",.12f,.26f,.078f,.86f,0,.042f,true,false},
  {"left_trigger","LT",.12f,0,0,.355f,0,.048f,true,false},
  {"left_shoulder","LB",.12f,0,0,.475f,0,.048f,true,false},
  {"left_thumb","L3",.12f,0,.115f,.475f,0,.036f,true,false},
  {"a","A",1,-.52f,0,.80f,.097f,.052f,true,false},
  {"b","B",1,-.52f,.097f,.80f,0,.052f,true,false},
  {"x","X",1,-.52f,-.097f,.80f,0,.052f,true,false},
  {"y","Y",1,-.52f,0,.80f,-.097f,.052f,true,false},
  {"right_trigger","RT",1,-.17f,0,.47f,0,.048f,true,false},
  {"right_shoulder","RB",1,-.17f,0,.59f,0,.048f,true,false},
  {"right_thumb","R3",1,-.17f,-.115f,.59f,0,.036f,true,false},
  {"back","BACK",.44f,0,0,.075f,0,.036f,true,false},
  {"start","START",.56f,0,0,.075f,0,.036f,true,false}
}};
}

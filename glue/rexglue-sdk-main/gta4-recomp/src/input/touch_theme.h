#pragma once

#include <imgui.h>

namespace gta4::input::theme {

inline void DrawImageOutline(ImDrawList* draw,ImTextureRef texture,ImVec2 center,float half,ImVec2 uv0,ImVec2 uv1,ImU32 color){
  const float stroke=half*.055f>1.f?half*.055f:1.f;
  for(int y=-1;y<=1;++y)for(int x=-1;x<=1;++x)if(x||y)draw->AddImage(texture,ImVec2(center.x-half+x*stroke,center.y-half+y*stroke),ImVec2(center.x+half+x*stroke,center.y+half+y*stroke),uv0,uv1,color);
}

// A gray cap and four separated grip marks, matching the approved button set.
// The base and touch radius stay with each layout's existing geometry.
inline void DrawStickGrip(ImDrawList* draw, ImVec2 center, float radius,
                          ImU32 color) {
  draw->AddCircleFilled(center, radius * .30f, color, 32);
  for (int i = 0; i < 4; ++i) {
    const float angle = float(i) * 1.57079632679f;
    draw->PathArcTo(center, radius * .41f, angle - .38f, angle + .38f, 6);
    draw->PathStroke(color, 0, radius * .06f);
  }
}

}  // namespace gta4::input::theme

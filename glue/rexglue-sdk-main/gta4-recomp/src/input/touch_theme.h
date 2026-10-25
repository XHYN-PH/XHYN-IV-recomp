#pragma once

#include <imgui.h>
#include <array>

namespace gta4::input::theme {

// Host controls use the whole window, independently of game output scaling.
inline std::array<float,4> FullscreenArea(float width,float height){return {0,0,width,height};}
inline void DrawButtonOutline(ImDrawList* draw,ImVec2 center,float radius,ImU32 color){
  draw->AddCircle(center,radius,color,48,radius*.025f>1.f?radius*.025f:1.f);
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

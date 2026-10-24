#include "input/context_touch_draw.h"
#include "input/touch_theme.h"

#include <algorithm>
#include <cfloat>
#include <cmath>

namespace gta4::input {
namespace {

void DrawContextTouchLayer(ImDrawList* draw, ImFont* font, float font_size,
                             const ContextTouchLayout& layout, const ContextTouchOverlaySnapshot* input, float fade_alpha,
                             float logical_width, float logical_height,
                             ContextTouchIconResolver resolver, void* icon_context) {
  if (!draw || !font || !std::isfinite(font_size) || font_size <= 0.0f ||
      layout.mode == ContextTouchMode::kFrontend ||
      layout.mode == ContextTouchMode::kMap) return;
  const auto transform = BuildContextTouchOverlayTransform(layout.viewport,
                                                          logical_width, logical_height);
  if (!transform.valid) return;
  const float radius_scale = std::min(transform.scale_x, transform.scale_y);
  const float opacity = std::clamp(layout.opacity, 0.2f, 1.0f) *
                        std::clamp(fade_alpha, 0.0f, 1.0f);
  if (!std::isfinite(opacity) || opacity <= 0.0f) return;
  const auto color = [opacity](int r, int g, int b, float alpha) {
    return IM_COL32(r, g, b, static_cast<int>(std::clamp(alpha * opacity, 0.0f, 255.0f)));
  };
  const auto point = [&](float x, float y) {
    return ImVec2(transform.offset_x + x * transform.scale_x,
                  transform.offset_y + y * transform.scale_y);
  };
  for (size_t i = 0; i < layout.control_count; ++i) {
    const auto& control = layout.controls[i];
    if (!control.visible || control.native_hud || control.kind == ContextTouchControlKind::kLookSurface) continue;
    const ImVec2 center = control.kind == ContextTouchControlKind::kMovementStick && (input && input->movement_owned)
        ? point((input ? input->movement_origin_x : 0.0f), (input ? input->movement_origin_y : 0.0f))
        : point(control.center_x, control.center_y);
    const float radius = control.radius * radius_scale;
    if (!std::isfinite(radius) || radius <= 0.0f) continue;
    const bool active = (input ? input->active[i] : 0) != 0;
    if (control.kind == ContextTouchControlKind::kActivitySurface) {
      const auto lo = point(control.minimum_x, control.minimum_y);
      const auto hi = point(control.maximum_x, control.maximum_y);
      const float rounding = radius * 0.28f;
      draw->AddRectFilled(lo, hi, color(10, 10, 10, active ? 140.0f : 85.0f), rounding);
      const float label_size = std::min(font_size, radius * 0.22f);
      const auto size = font->CalcTextSizeA(label_size, FLT_MAX, 0.0f, control.label.data());
      draw->AddText(font, label_size, ImVec2(center.x - size.x * 0.5f, lo.y + rounding),
                    color(194, 194, 194, 225.0f), control.label.data());
      using G = TouchActivityGesture;
      const auto gesture = control.activity_gesture;
      const bool circular = gesture == G::kCircleRight;
      const bool horizontal = gesture == G::kAlternatingHorizontal || gesture == G::kHorizontalLeft;
      const bool vertical = gesture == G::kStrokeRight || gesture == G::kAlternatingVertical;
      const float length = radius * 0.55f, tip = radius * 0.15f;
      if (circular) {
        draw->AddCircle(center, length, color(255,255,255,160.0f), 32, 2.0f);
        draw->AddTriangleFilled(ImVec2(center.x + length, center.y + tip),
            ImVec2(center.x + length - tip, center.y - tip),
            ImVec2(center.x + length + tip, center.y - tip), color(255,255,255,200.0f));
      } else if (horizontal || vertical) {
        const ImVec2 a(center.x - (horizontal ? length : 0.0f), center.y - (vertical ? length : 0.0f));
        const ImVec2 b(center.x + (horizontal ? length : 0.0f), center.y + (vertical ? length : 0.0f));
        draw->AddLine(a, b, color(255,255,255,150.0f), 2.0f);
        if (horizontal) {
          draw->AddTriangleFilled(a, ImVec2(a.x+tip,a.y-tip), ImVec2(a.x+tip,a.y+tip), color(255,255,255,190.0f));
          draw->AddTriangleFilled(b, ImVec2(b.x-tip,b.y-tip), ImVec2(b.x-tip,b.y+tip), color(255,255,255,190.0f));
        } else {
          draw->AddTriangleFilled(a, ImVec2(a.x-tip,a.y+tip), ImVec2(a.x+tip,a.y+tip), color(255,255,255,190.0f));
          draw->AddTriangleFilled(b, ImVec2(b.x-tip,b.y-tip), ImVec2(b.x+tip,b.y-tip), color(255,255,255,190.0f));
        }
      } else {
        draw->AddCircle(center, length, color(255,255,255,120.0f), 24, 1.0f);
        draw->AddCircleFilled(center, tip, color(255,255,255,180.0f), 16);
      }
      continue;
    }
    draw->AddCircleFilled(center, radius, active ? color(50, 50, 50, 150.0f)
                                               : color(18, 18, 18, 96.0f), 48);
    if (control.kind == ContextTouchControlKind::kMovementStick || control.kind == ContextTouchControlKind::kRightStick) {
      const bool right = control.kind == ContextTouchControlKind::kRightStick;
      const float x = std::clamp(right ? (input ? input->right_x : 0.0f) : (input ? input->movement_x : 0.0f), -255.0f, 255.0f) / 255.0f;
      const float y = std::clamp(right ? (input ? input->right_y : 0.0f) : (input ? input->movement_y : 0.0f), -255.0f, 255.0f) / 255.0f;
      theme::DrawStickGrip(draw, ImVec2(center.x + x * radius * 0.55f,
                                      center.y + y * radius * 0.55f),
                           radius, color(194, 194, 194, 190.0f));
      continue;
    }
    ContextTouchIcon icon;
    bool has_icon = false;
    if (resolver && control.icon_id[0]) {
      has_icon = resolver(icon_context, control.icon_id.data(), &icon) && icon.texture &&
                 std::isfinite(icon.aspect) && icon.aspect > 0.0f;
    }
    if (has_icon) {
      const float bound = radius * 0.62f;
      const float half_width = icon.aspect >= 1.0f ? bound : bound * icon.aspect;
      const float half_height = icon.aspect >= 1.0f ? bound / icon.aspect : bound;
      draw->AddImage(icon.texture, ImVec2(center.x - half_width, center.y - half_height),
                     ImVec2(center.x + half_width, center.y + half_height), icon.uv_min,
                     icon.uv_max, color(194, 194, 194, 225.0f));
    } else if (control.label[0]) {
      const ImVec2 natural = font->CalcTextSizeA(font_size, FLT_MAX, 0.0f, control.label.data());
      const float fitted_size = natural.x > 0.0f
          ? std::min(font_size, font_size * radius * 1.55f / natural.x) : font_size;
      const ImVec2 size = font->CalcTextSizeA(fitted_size, FLT_MAX, 0.0f, control.label.data());
      draw->AddText(font, fitted_size, ImVec2(center.x - size.x * 0.5f, center.y - size.y * 0.5f),
                     color(194, 194, 194, 225.0f), control.label.data());
    }
  }
}

}  // namespace

void DrawContextTouchOverlay(ImDrawList* draw, ImFont* font, float font_size,
                             const ContextTouchOverlaySnapshot& snapshot,
                             float logical_width, float logical_height,
                             ContextTouchIconResolver resolver, void* icon_context) {
  if (!snapshot.visible) return;
  if (snapshot.outgoing_layout && snapshot.outgoing_alpha > 0.0f)
    DrawContextTouchLayer(draw, font, font_size, *snapshot.outgoing_layout, nullptr,
        snapshot.fade_alpha * snapshot.outgoing_alpha, logical_width, logical_height, resolver, icon_context);
  DrawContextTouchLayer(draw, font, font_size, snapshot.layout, &snapshot,
      snapshot.fade_alpha * snapshot.layout_alpha, logical_width, logical_height, resolver, icon_context);
}
}  // namespace gta4::input

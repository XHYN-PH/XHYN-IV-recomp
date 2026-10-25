#include "input/gamepad_touch_overlay.h"
#include "input/liberty_touch_overlay.h"
#include "input/context_touch_context.h"
#include "input/context_touch_draw.h"
#include "input/gamepad_touch_defaults.h"
#include "input/touch_theme.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cfloat>
#include <cmath>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <fstream>
#include <sstream>
#include <rex/filesystem.h>

#include <SDL3/SDL_stdinc.h>
#include <SDL3/SDL_touch.h>
#if defined(__ANDROID__)
#include <SDL3/SDL_system.h>
#endif

#include <rex/cvar.h>
#include <rex/input/absolute_pointer.h>
#include <rex/input/input.h>

REXCVAR_DEFINE_STRING(gta4_touch_layout, "liberty", "GTA IV/Input/Touch",
                      "On-screen touch controls: liberty (icon buttons that follow on foot, "
                      "driving, phone and menus; movable), gamepad (an editable Xbox controller) "
                      "or context (upstream's context-sensitive layout)")
    .allowed({"liberty", "gamepad", "context"});
REXCVAR_DEFINE_STRING(gta4_touch_camera, "swipe", "GTA IV/Input/Touch",
                      "How the right half of the screen moves the camera: swipe (the camera "
                      "follows your finger, like a mouse) or stick (a virtual joystick)")
    .allowed({"swipe", "stick"});
REXCVAR_DEFINE_DOUBLE(gta4_touch_swipe_sensitivity, 1.0, "GTA IV/Input/Touch",
                      "Swipe camera speed; 0.5 is half as fast, 2.0 twice as fast");
REXCVAR_DEFINE_DOUBLE(gta4_touch_camera_scale,1.0,"GTA IV/Input/Touch","Visible camera stick size").range(0.25,3.0);
REXCVAR_DEFINE_DOUBLE(gta4_touch_camera_opacity,1.0,"GTA IV/Input/Touch","Visible camera stick opacity").range(0.0,1.0);
REXCVAR_DEFINE_DOUBLE(gta4_touch_gamepad_scale, 1.0, "GTA IV/Input/Touch",
                      "Size of the on-screen gamepad's buttons (0.5 to 2.0)");
REXCVAR_DEFINE_DOUBLE(gta4_touch_gamepad_opacity, 1.0, "GTA IV/Input/Touch",
                      "Opacity of the on-screen gamepad (0.3 faint to 1.6 strong)");
REXCVAR_DECLARE(std::string,gta4_touch_move_mode);
REXCVAR_DECLARE(double,gta4_touch_move_smoothing);
REXCVAR_DEFINE_BOOL(gta4_touch_icon_outline,false,"GTA IV/Input/Touch","Outline circular touch buttons without outlining glyphs");
REXCVAR_DEFINE_BOOL(gta4_touch_animations,true,"GTA IV/Input/Touch","Animate touch-button press feedback");

namespace gta4::input {
namespace {

enum class Kind : uint8_t { kStick, kButton, kTrigger, kArrow };

struct Control {
  Kind kind = Kind::kButton;
  float x = 0.0f, y = 0.0f, r = 0.0f;  // display pixels; a stick's resting position
  uint16_t button = 0;                  // buttons, arrows
  uint8_t index = 0;                    // stick 0/1, trigger 0/1, arrow 0..3 (up, down, left, right)
  const char* label = "";
  ImU32 label_color = 0;                // 0: the common label colour
};

constexpr size_t kControlCount = kXboxControlDefaults.size();
constexpr std::array<const char*, kControlCount> kThemeIcons = {
    nullptr, nullptr, "up", "down", "left", "right", "lt", "lb", "l3",
    "a", "b", "x", "y", "rt", "rb", "r3", "view", "xbox_menu"};
constexpr size_t kLeftStick = 0;
constexpr size_t kRightStick = 1;
constexpr float kDeadZone = 0.12f;

// Grey and see-through, so the overlay does not hide the game.
constexpr ImU32 kFill = IM_COL32(18, 18, 18, 96);
constexpr ImU32 kFillHeld = IM_COL32(50, 50, 50, 150);
constexpr ImU32 kLabel = IM_COL32(194, 194, 194, 225);
constexpr ImU32 kKnob = IM_COL32(194, 194, 194, 190);

// The colours above with their alpha scaled by gta4_touch_gamepad_opacity.
ImU32 Faded(ImU32 color,float multiplier=1.f) {
  const double value = REXCVAR_GET(gta4_touch_gamepad_opacity);
  const float opacity = std::isfinite(value) ? float(std::clamp(value, 0.0, 1.6)) : 1.0f;
  const uint32_t alpha = (color >> IM_COL32_A_SHIFT) & 0xFF;
  const uint32_t scaled = uint32_t(std::clamp(alpha * opacity * multiplier, 0.0f, 255.0f));
  return (color & ~IM_COL32_A_MASK) | (scaled << IM_COL32_A_SHIFT);
}

struct Finger {
  int control = -1;
};

struct LayoutEdit {float dx=0,dy=0,scale=1,opacity=1;bool hidden=false;};

struct State {
  std::mutex mutex;
  bool visible = false;
  bool layout_loaded=false;
  std::array<LayoutEdit,kControlCount> layout{};
  std::array<Control, kControlCount> controls{};
  std::array<bool, kControlCount> held{};
  std::array<float,kControlCount> press_feedback{};
  // Sticks float: the centre is where the finger came down.
  ImVec2 stick_origin[2] = {{0, 0}, {0, 0}};
  float stick_x[2] = {0, 0};  // -1..1, screen space (y down)
  float stick_y[2] = {0, 0};
  std::unordered_map<uint64_t, Finger> fingers;
  // Swipe camera: the finger's last position and the time of the last update,
  // and whether the right stick is currently driven by a swipe.
  ImVec2 swipe_last = {0, 0};
  bool swipe_tracking = false;
  bool swipe_active = false;
  std::chrono::steady_clock::time_point last_update{};
};

bool SwipeCamera() noexcept {
  return REXCVAR_GET(gta4_touch_camera) != "stick";
}

State& state() {
  static State s;
  return s;
}

// Sized from the screen height so the controls keep their physical size on
// 16:9 and 20:9 panels. The left half of the screen outside the buttons is the
// left stick (it centres wherever the finger lands); the right half is the
// right stick, i.e. the camera. The radar moves to the top-left corner while
// the overlay is up (ContextTouchHudLayoutActive).
void LoadLayout(State& s) {
  s.layout_loaded=true;s.layout.fill({});
  auto root=rex::filesystem::GetUserFolder();
#if defined(__ANDROID__)
  if(const auto* external=SDL_GetAndroidExternalStoragePath();external&&*external)root=external;
#endif
  std::ifstream file(root/"LibertyRecomp"/"liberty_touch_layout.txt");
  std::string line;unsigned lines=0;
  while(lines++<4096&&std::getline(file,line)){
    if(line.size()>512)continue;
    std::istringstream input(line);std::string key;LayoutEdit edit;int hidden=0;
    if(!(input>>key>>edit.dx>>edit.dy>>edit.scale>>hidden)||!std::isfinite(edit.dx)||!std::isfinite(edit.dy)||!std::isfinite(edit.scale))continue;
    for(size_t i=0;i<kControlCount;++i)if(key==std::string("xbox/")+kXboxControlDefaults[i].id){
      edit.dx=std::clamp(edit.dx,-2000.f,2000.f);edit.dy=std::clamp(edit.dy,-800.f,800.f);
      edit.scale=std::clamp(edit.scale,.35f,4.f);edit.hidden=hidden!=0;float opacity=1;if(input>>opacity&&std::isfinite(opacity))edit.opacity=std::clamp(opacity,0.f,1.f);s.layout[i]=edit;break;
    }
  }
}
std::array<Control, kControlCount> BuildLayout(float w, float h) {
  std::array<Control,kControlCount> c{};
  const double configured=REXCVAR_GET(gta4_touch_gamepad_scale),camera=REXCVAR_GET(gta4_touch_camera_scale);
  const float k=std::isfinite(configured)?float(std::clamp(configured,.5,2.0)):1.f;
  const float camera_scale=std::isfinite(camera)?float(std::clamp(camera,.25,3.0)):1.f;
  size_t n=0;
  const auto add=[&](Kind kind,uint16_t button,uint8_t index,ImU32 color=0){
    const auto& d=kXboxControlDefaults[n];const auto& edit=state().layout[n];
    const float r=std::min(std::min(w,h)*.45f,std::max(4.f,d.r*h*(d.buttons?k:1.f)*(d.camera?camera_scale:1.f)*edit.scale));
    const float x=std::clamp(w*d.xf+h*(d.xh+d.xs*k)+edit.dx*h/737.f,r,w-r);
    const float y=std::clamp(h*(d.yf+d.ys*k)+edit.dy*h/737.f,r,h-r);
    c[n++]=Control{kind,x,y,edit.hidden?0.f:r,button,index,d.label,color};
  };
  add(Kind::kStick,0,0);add(Kind::kStick,0,1);
  add(Kind::kArrow,rex::input::X_INPUT_GAMEPAD_DPAD_UP,0);
  add(Kind::kArrow,rex::input::X_INPUT_GAMEPAD_DPAD_DOWN,1);
  add(Kind::kArrow,rex::input::X_INPUT_GAMEPAD_DPAD_LEFT,2);
  add(Kind::kArrow,rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT,3);
  add(Kind::kTrigger,0,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_LEFT_THUMB,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_A,0,IM_COL32(65,190,80,170));
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_B,0,IM_COL32(220,60,60,170));
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_X,0,IM_COL32(75,145,240,170));
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_Y,0,IM_COL32(245,205,50,170));
  add(Kind::kTrigger,0,1);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_RIGHT_SHOULDER,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_RIGHT_THUMB,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_BACK,0);
  add(Kind::kButton,rex::input::X_INPUT_GAMEPAD_START,0);
  return c;
}

// The button under a finger, or the stick of that half of the screen.
int HitTest(const std::array<Control, kControlCount>& controls, float px, float py, float w) {
  int best = -1;
  float best_distance = FLT_MAX;
  for (size_t i = 0; i < controls.size(); ++i) {
    const auto& control = controls[i];
    if (control.r <= 0.0f || control.kind == Kind::kStick) continue;
    // A finger that lands just outside a button still presses it; the nearest
    // button (relative to its size) wins where two capture areas overlap.
    const float capture = control.r * 1.4f;
    const float distance = std::hypot(px - control.x, py - control.y);
    if (distance <= capture && distance / capture < best_distance) {
      best_distance = distance / capture;
      best = int(i);
    }
  }
  if (best >= 0) return best;
  const int stick=int(px < w * .5f ? kLeftStick : kRightStick);
  return controls[stick].r>0?stick:-1;
}

// The origin that draws a label's ink centred on `center`: horizontally by its
// advance, vertically by the glyph box of its first letter (labels are capitals),
// not by the line height, which leaves letters sitting high.
ImVec2 LabelOrigin(ImFont* font, float size, const char* label, ImVec2 center) {
  const ImVec2 text = font->CalcTextSizeA(size, FLT_MAX, 0.0f, label);
  float y = center.y - text.y * 0.5f;
  float x = center.x - text.x * 0.5f;
  if (ImFontBaked* baked = font->GetFontBaked(size)) {
    if (const ImFontGlyph* glyph = baked->FindGlyphNoFallback(ImWchar(label[0]))) {
      const float scale = baked->Size > 0.0f ? size / baked->Size : 1.0f;
      y = center.y - (glyph->Y0 + glyph->Y1) * 0.5f * scale;
      if (label[1] == 0) x = center.x - (glyph->X0 + glyph->X1) * 0.5f * scale;
    }
  }
  return ImVec2(x, y);
}

void Draw(ImDrawList* draw, ImFont* font, float font_size, State& s) {
  for (size_t i = 0; i < s.controls.size(); ++i) {
    const auto& control = s.controls[i];
    const auto faded=[opacity=s.layout[i].opacity](ImU32 color,float factor=1.f){return Faded(color,factor*opacity);};
    if (control.r <= 0.0f) continue;
    const bool active = s.held[i];
    auto& feedback=s.press_feedback[i];
    feedback+=(float(active)-feedback)*(REXCVAR_GET(gta4_touch_animations)?std::min(1.f,ImGui::GetIO().DeltaTime*18.f):1.f);
    const float radius = control.r*(1.f-.035f*feedback);
    if (control.kind == Kind::kStick) {
      // The camera stick shows only while it is being used.
      if (control.index == 1 && (!active || SwipeCamera())) continue;
      if(control.index==0&&REXCVAR_GET(gta4_touch_move_mode)=="invisible")continue;
      const ImVec2 center = active ? s.stick_origin[control.index] : ImVec2(control.x, control.y);
      const float camera_alpha=control.index==1?float(REXCVAR_GET(gta4_touch_camera_opacity)):1.f;
      draw->AddCircleFilled(center, radius, faded(kFill,camera_alpha), 48);
      const ImVec2 knob(center.x + s.stick_x[control.index] * radius * 0.55f,
                        center.y + s.stick_y[control.index] * radius * 0.55f);
      theme::DrawStickGrip(draw, knob, radius, faded(kKnob,camera_alpha));
      continue;
    }
    const ImVec2 center(control.x, control.y);
    draw->AddCircleFilled(center,radius,faded(active?kFillHeld:kFill),48);
    if(REXCVAR_GET(gta4_touch_icon_outline))theme::DrawButtonOutline(draw,center,radius,faded(kLabel));
    ContextTouchIcon image;
    if (kThemeIcons[i] && ResolveLibertyIcon(kThemeIcons[i], &image)) {
      draw->AddImage(image.texture, ImVec2(center.x-radius, center.y-radius),
                     ImVec2(center.x+radius, center.y+radius), image.uv_min,
                     image.uv_max, faded(control.label_color ? control.label_color : kLabel));
      continue;
    }
    if(control.button==rex::input::X_INPUT_GAMEPAD_START){
      for(int row=-1;row<=1;++row)draw->AddRectFilled(ImVec2(center.x-radius*.44f,center.y+row*radius*.27f-radius*.055f),ImVec2(center.x+radius*.44f,center.y+row*radius*.27f+radius*.055f),faded(kLabel),radius*.05f);
      continue;
    }
    if(control.button==rex::input::X_INPUT_GAMEPAD_BACK){
      const float q=radius*.36f;
      draw->AddRectFilled(ImVec2(center.x-q*1.3f,center.y-q*1.3f),ImVec2(center.x+q*.6f,center.y+q*.6f),faded(kLabel),radius*.07f);
      draw->AddRectFilled(ImVec2(center.x-q*.6f,center.y-q*.6f),ImVec2(center.x+q*1.3f,center.y+q*1.3f),faded(kFill),radius*.07f);
      draw->AddRectFilled(ImVec2(center.x-q*.4f,center.y-q*.4f),ImVec2(center.x+q*1.3f,center.y+q*1.3f),faded(kLabel),radius*.07f);
      continue;
    }
    if (control.kind == Kind::kArrow) {
      const float t = radius * 0.42f;
      ImVec2 a, b, c;
      switch (control.index) {
        case 0: a = {center.x, center.y - t}; b = {center.x - t, center.y + t * 0.6f}; c = {center.x + t, center.y + t * 0.6f}; break;
        case 1: a = {center.x, center.y + t}; b = {center.x + t, center.y - t * 0.6f}; c = {center.x - t, center.y - t * 0.6f}; break;
        case 2: a = {center.x - t, center.y}; b = {center.x + t * 0.6f, center.y + t}; c = {center.x + t * 0.6f, center.y - t}; break;
        default: a = {center.x + t, center.y}; b = {center.x - t * 0.6f, center.y - t}; c = {center.x - t * 0.6f, center.y + t}; break;
      }
      draw->AddTriangleFilled(a, b, c, faded(kLabel));
      continue;
    }
    // Letters fill most of the circle; longer labels are fitted to its width.
    const ImVec2 natural = font->CalcTextSizeA(font_size, FLT_MAX, 0.0f, control.label);
    const float size = control.label[1] == 0 ? radius * 0.95f
        : natural.x > 0.0f ? std::min(radius * 0.62f, font_size * radius * 1.35f / natural.x)
                           : font_size;
    draw->AddText(font, size, LabelOrigin(font, size, control.label, center),
                  faded(control.label_color ? control.label_color : kLabel), control.label);
  }
}

}  // namespace

namespace {
enum class SelectedLayout { kLiberty, kGamepad, kContext };
std::atomic<SelectedLayout> selected_layout{SelectedLayout::kLiberty};
std::atomic<int> requested_layout{-1};
std::atomic<bool> host_menu{false};
std::atomic<int64_t> host_start_until{0};
bool host_menu_paused=false;
bool wait_for_layout_release=false;
bool LibertySelected() noexcept { return selected_layout.load(std::memory_order_relaxed) == SelectedLayout::kLiberty; }
}
bool TouchHostMenuOpen() noexcept {return host_menu.load(std::memory_order_acquire);}
void SetTouchHostMenu(bool open,bool resume) noexcept {
  const auto context=GetTouchContextSnapshot();
  const bool previous=host_menu.exchange(open,std::memory_order_acq_rel);
  if(previous==open)return;
  bool pulse=false;
  if(open) {
    host_menu_paused=context.valid&&context.playing&&context.gameplay_allowed&&!context.cutscene&&!context.loading&&!context.frontend;
    pulse=host_menu_paused;
  }else {
    pulse=resume&&host_menu_paused&&!context.gameplay_allowed&&!context.cutscene&&!context.loading;
    host_menu_paused=false;
  }
  if(pulse)host_start_until.store(std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now().time_since_epoch()).count()+160,std::memory_order_release);
  ResetLibertyTouchOverlay();
}
void RequestGamepadTouchLayout(bool xbox) noexcept {
  requested_layout.store(xbox ? 1 : 0, std::memory_order_release);
}
void RefreshGamepadTouchLayout() noexcept {
  const int requested = requested_layout.exchange(-1, std::memory_order_acq_rel);
  if (requested >= 0) {
    wait_for_layout_release=true;
    ResetLibertyTouchOverlay();
    { auto& s=state();std::lock_guard lock(s.mutex);s.visible=false;s.fingers.clear();
      s.held.fill(false);s.stick_x[0]=s.stick_x[1]=s.stick_y[0]=s.stick_y[1]=0;
      s.swipe_tracking=s.swipe_active=false; }
    rex::cvar::SetFlagByName("gta4_touch_layout", requested ? "gamepad" : "liberty");
    // An explicit on-screen controller selection also re-enables controls
    // that were hidden by a saved Off/Auto policy.
    rex::cvar::SetFlagByName("touch_controls", "on");
  }
  const auto& layout = REXCVAR_GET(gta4_touch_layout);
  selected_layout.store(layout == "gamepad" ? SelectedLayout::kGamepad :
      layout == "context" ? SelectedLayout::kContext : SelectedLayout::kLiberty, std::memory_order_relaxed);
}
bool GamepadTouchLayoutSelected() noexcept { return selected_layout.load(std::memory_order_relaxed) != SelectedLayout::kContext; }

void ReloadGamepadTouchLayout() noexcept {
  auto& s=state();std::lock_guard lock(s.mutex);s.layout_loaded=false;s.fingers.clear();s.held.fill(false);
  s.stick_x[0]=s.stick_x[1]=s.stick_y[0]=s.stick_y[1]=0;
  s.swipe_tracking=s.swipe_active=false;PublishTouchLook(0,0,false);
}

bool GamepadTouchOverlayVisible() noexcept {
  if (LibertySelected()) return LibertyTouchOverlayVisible();
  if (!GamepadTouchLayoutSelected()) return false;
  std::lock_guard lock(state().mutex);
  return state().visible;
}

bool UpdateAndDrawGamepadTouchOverlay(ImDrawList* draw, ImFont* font, float font_size,
                                      float width, float height) noexcept {
  RefreshGamepadTouchLayout();
  if (wait_for_layout_release) {
    bool down=false; int count=0;
    if (auto* devices=SDL_GetTouchDevices(&count)) {
      for(int i=0;i<count;++i) { int fingers=0;auto** points=SDL_GetTouchFingers(devices[i],&fingers);
        down |= fingers>0;SDL_free(points); }
      SDL_free(devices);
    }
    if(down) return true;
    wait_for_layout_release=false;
  }
  if (LibertySelected()) return UpdateAndDrawLibertyTouchOverlay(draw, font, font_size, width, height);
  auto& s = state();
  const bool visible = GamepadTouchLayoutSelected() && rex::input::TouchControlsVisible() &&
                       std::isfinite(width) && std::isfinite(height) &&
                       width > 0.0f && height > 0.0f;
  std::lock_guard lock(s.mutex);
  if (!visible) {
    s.visible = false;
    s.fingers.clear();
    s.held.fill(false);
    s.stick_x[0] = s.stick_x[1] = s.stick_y[0] = s.stick_y[1] = 0.0f;
    s.swipe_tracking = s.swipe_active = false;
    return false;
  }
  s.visible = true;
  if(!s.layout_loaded)LoadLayout(s);
  s.controls = BuildLayout(width, height);
  const auto now = std::chrono::steady_clock::now();
  const float frame_seconds =
      s.last_update.time_since_epoch().count() == 0
          ? 1.0f / 60.0f
          : std::chrono::duration<float>(now - s.last_update).count();
  s.last_update = now;

  // Every finger currently on the screen, from every touch device.
  std::unordered_map<uint64_t, ImVec2> touches;
  int device_count = 0;
  if (SDL_TouchID* devices = SDL_GetTouchDevices(&device_count)) {
    for (int d = 0; d < device_count; ++d) {
      int finger_count = 0;
      if (SDL_Finger** fingers = SDL_GetTouchFingers(devices[d], &finger_count)) {
        for (int f = 0; f < finger_count; ++f) {
          const uint64_t key = (uint64_t(devices[d]) << 32) ^ uint64_t(fingers[f]->id);
          touches[key] = ImVec2(fingers[f]->x * width, fingers[f]->y * height);
        }
        SDL_free(fingers);
      }
    }
    SDL_free(devices);
  }

  for (auto it = s.fingers.begin(); it != s.fingers.end();) {
    it = touches.count(it->first) ? std::next(it) : s.fingers.erase(it);
  }
  bool stick_taken[2] = {false, false};
  for (const auto& [key, finger] : s.fingers) {
    if (finger.control >= 0 && s.controls[finger.control].kind == Kind::kStick) {
      stick_taken[s.controls[finger.control].index] = true;
    }
  }

  s.held.fill(false);
  bool stick_owned[2] = {false, false};
  for (const auto& [key, position] : touches) {
    auto found = s.fingers.find(key);
    if (found == s.fingers.end()) {
      // A finger belongs to what it first landed on until it lifts. A stick
      // centres under the finger, kept fully on screen; one finger per stick.
      int control = HitTest(s.controls, position.x, position.y, width);
      if (control >= 0 && s.controls[control].kind == Kind::kStick) {
        const auto& stick = s.controls[control];
        if (stick_taken[stick.index]) {
          control = -1;
        } else {
          stick_taken[stick.index] = true;
          s.stick_origin[stick.index] = stick.index==0&&REXCVAR_GET(gta4_touch_move_mode)=="fixed"?
              ImVec2(stick.x,stick.y):ImVec2(std::clamp(position.x, stick.r, width - stick.r),
                     std::clamp(position.y, stick.r, height - stick.r));
        }
      }
      found = s.fingers.emplace(key, Finger{control}).first;
    }
    const int control_index = found->second.control;
    if (control_index < 0) continue;
    const Control& control = s.controls[control_index];
    s.held[control_index] = true;
    if (control.kind != Kind::kStick) continue;
    if (control.index == kRightStick && SwipeCamera()) {
      // Swipe camera: the stick follows the finger's speed, so the view turns
      // while the finger moves and stops when it stops. Full speed when the
      // finger crosses about 1.5 screen heights per second (sensitivity 1).
      if (!s.swipe_tracking) {
        s.swipe_last = position;
        s.swipe_tracking = true;
      }
      const double value = REXCVAR_GET(gta4_touch_swipe_sensitivity);
      const float sensitivity = std::isfinite(value) ? float(std::clamp(value, 0.1, 8.0)) : 1.0f;
      const float gain=720.f/std::max(height,1.f)*sensitivity*0.8f;
      PublishTouchLook((position.x-s.swipe_last.x)*gain,(position.y-s.swipe_last.y)*gain,true);
      s.stick_x[kRightStick]=s.stick_y[kRightStick]=0;
      s.swipe_last = position;
      s.swipe_active = true;
      stick_owned[kRightStick] = true;
      continue;
    }
    const ImVec2 origin = s.stick_origin[control.index];
    float x = (position.x - origin.x) / (control.r * 0.85f);
    float y = (position.y - origin.y) / (control.r * 0.85f);
    const float length = std::hypot(x, y);
    if (length > 1.0f) {
      x /= length;
      y /= length;
    }
    float blend=1;
    const double tau=REXCVAR_GET(gta4_touch_move_smoothing);
    if(control.index==0&&std::isfinite(tau)&&tau>0)
      blend=1.f-std::exp(-std::clamp(ImGui::GetIO().DeltaTime,1.f/240.f,0.1f)/float(tau));
    s.stick_x[control.index] += (x-s.stick_x[control.index])*blend;
    s.stick_y[control.index] += (y-s.stick_y[control.index])*blend;
    stick_owned[control.index] = true;
  }
  for (int i = 0; i < 2; ++i) {
    if (!stick_owned[i]) s.stick_x[i] = s.stick_y[i] = 0.0f;
  }
  if (!stick_owned[kRightStick]) {s.swipe_tracking = s.swipe_active = false;PublishTouchLook(0,0,false);}

  if (draw && font && font_size > 0.0f) Draw(draw, font, font_size, s);
  return true;
}

bool ReadGamepadTouchOverlay(uint32_t user, rex::input::X_INPUT_GAMEPAD* output) noexcept {
  if (!output || user != 0 || !GamepadTouchLayoutSelected()) return false;
  *output = {};
  const auto now_ms=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
  if(TouchHostMenuOpen()||now_ms<host_start_until.load(std::memory_order_acquire)) {
    if(now_ms<host_start_until.load(std::memory_order_relaxed))output->buttons=rex::input::X_INPUT_GAMEPAD_START;
    return true;
  }
  // Connectivity must precede the first gameplay frame. The title queries
  // controller capabilities while mobile controls are hidden during startup.
  // Return a connected neutral pad through loading and layout transitions.
  if (LibertySelected()) {
    ReadLibertyTouchOverlay(user, output);
    return true;
  }
  auto& s = state();
  std::lock_guard lock(s.mutex);
  if (!s.visible) return true;
  uint16_t buttons = 0;
  uint8_t triggers[2] = {0, 0};
  for (size_t i = 0; i < s.controls.size(); ++i) {
    const auto& control = s.controls[i];
    if (!s.held[i]) continue;
    if (control.kind == Kind::kTrigger) {
      triggers[control.index] = 255;
    } else if (control.kind != Kind::kStick) {
      buttons |= control.button;
    }
  }
  // Radial dead zone per stick; screen Y points down, the pad's up.
  const auto stick = [&](int i, int16_t* x_out, int16_t* y_out) {
    const float x = s.stick_x[i], y = s.stick_y[i];
    const float length = std::hypot(x, y);
    if (length < kDeadZone) {
      *x_out = *y_out = 0;
      return;
    }
    const float scale = std::min((length - kDeadZone) / (1.0f - kDeadZone), 1.0f) / length;
    *x_out = int16_t(std::clamp(x * scale, -1.0f, 1.0f) * 32767.0f);
    *y_out = int16_t(std::clamp(-y * scale, -1.0f, 1.0f) * 32767.0f);
  };
  int16_t lx = 0, ly = 0, rx = 0, ry = 0;
  stick(0, &lx, &ly);
  if (s.swipe_active) {
    // Swipe camera: no dead zone of our own, and small movements start just
    // past the game's own right-stick dead zone (about 27%, as on XInput) so
    // slow, fine aiming swipes still turn the camera.
    constexpr float kGameDeadZone = 0.27f;
    const float x = s.stick_x[1], y = s.stick_y[1];
    const float length = std::hypot(x, y);
    // Leftovers of the smoothing after the finger stops are dropped, or the
    // dead-zone offset would keep the camera creeping for a few frames.
    if (length > 0.04f) {
      const float scaled = std::min(kGameDeadZone + (1.0f - kGameDeadZone) * length, 1.0f) / length;
      rx = int16_t(std::clamp(x * scaled, -1.0f, 1.0f) * 32767.0f);
      ry = int16_t(std::clamp(-y * scaled, -1.0f, 1.0f) * 32767.0f);
    }
  } else {
    stick(1, &rx, &ry);
  }
  *output = {};
  output->buttons = buttons;
  output->left_trigger = triggers[0];
  output->right_trigger = triggers[1];
  output->thumb_lx = lx;
  output->thumb_ly = ly;
  output->thumb_rx = rx;
  output->thumb_ry = ry;
  return true;
}

}  // namespace gta4::input

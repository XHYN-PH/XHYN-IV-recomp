// Liberty touch layout (Mali Edition): icon buttons that follow what the
// player is doing (on foot, driving, phone, menus), drawn in a GTA-style HUD
// look, sent to the title as an Xbox 360 pad. Every control can be moved:
// hold Pause for 1.5 s to edit, drag, then use Save layout.
#include "input/liberty_touch_overlay.h"
#include "input/touch_context_transition.h"
#include "input/gamepad_touch_overlay.h"
#include "input/touch_theme.h"
#include "input/aim_hold_policy.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cfloat>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <map>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include <SDL3/SDL_stdinc.h>
#include <SDL3/SDL_touch.h>
#if defined(__ANDROID__)
#include <SDL3/SDL_system.h>
#endif

#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/input/absolute_pointer.h>
#include <rex/logging.h>
#include <rex/input/input.h>

#include "input/context_touch_context.h"
#include "input/context_touch_draw.h"
#include <rex/diagnostics/policy.h>
REXCVAR_DECLARE(bool,gta4_touch_animations);
REXCVAR_DECLARE(bool,gta4_touch_icon_outline);
REXCVAR_DEFINE_BOOL(gta4_touch_aim_hold_lock,true,"GTA IV/Input/Touch",
                    "Long-hold the mobile Aim button to keep aiming after release; tap again to unlock");
REXCVAR_DEFINE_DOUBLE(gta4_touch_aim_hold_seconds,0.4,"GTA IV/Input/Touch",
                      "Hold duration in seconds before mobile aim locks").range(0.2,1.5);

REXCVAR_DECLARE(double, gta4_touch_gamepad_scale);
REXCVAR_DECLARE(double, gta4_touch_gamepad_opacity);
REXCVAR_DECLARE(std::string, gta4_touch_camera);
REXCVAR_DECLARE(double, gta4_touch_swipe_sensitivity);
REXCVAR_DECLARE(double,gta4_touch_camera_scale);
REXCVAR_DECLARE(double,gta4_touch_camera_opacity);
REXCVAR_DEFINE_BOOL(gta4_touch_labels, false, "GTA IV/Input/Touch",
                    "Show text labels under the Liberty layout's buttons");
REXCVAR_DEFINE_BOOL(gta4_touch_button_hints, true, "GTA IV/Input/Touch",
                    "Show the Xbox button names beside mobile controls");
REXCVAR_DEFINE_STRING(gta4_touch_move_mode, "fixed", "GTA IV/Input/Touch",
                      "Movement joystick: fixed, floating, or invisible")
    .allowed({"fixed", "floating", "invisible"});
REXCVAR_DEFINE_INT32(gta4_touch_context_delay_ms,0,"GTA IV/Input/Touch","Mobile gameplay/phone layout transition hold; zero uses earliest observed context").range(0,1000);
REXCVAR_DEFINE_DOUBLE(gta4_touch_move_smoothing, 0.035, "GTA IV/Input/Touch",
                      "Movement smoothing time constant in seconds; zero disables smoothing")
    .range(0.0, 0.1);
REXCVAR_DEFINE_BOOL(gta4_touch_radar_top, true, "GTA IV/Input/Touch",
                    "Place the gameplay radar at the top left while touch controls are enabled");

REXCVAR_DEFINE_DOUBLE(gta4_touch_radar_margin,-0.06,"GTA IV/Input/Touch","Minimap vertical offset as a fraction of safe height; negative moves up").range(-0.20,0.15);
#if REX_PLATFORM_ANDROID
extern "C" void GTA4_AndroidRequestTouchEditor();
extern "C" void GTA4_AndroidTouchEditorClosed();
#endif

namespace gta4::input {
namespace {

using rex::input::X_INPUT_GAMEPAD_A;
using rex::input::X_INPUT_GAMEPAD_B;
using rex::input::X_INPUT_GAMEPAD_BACK;
using rex::input::X_INPUT_GAMEPAD_DPAD_DOWN;
using rex::input::X_INPUT_GAMEPAD_DPAD_LEFT;
using rex::input::X_INPUT_GAMEPAD_DPAD_RIGHT;
using rex::input::X_INPUT_GAMEPAD_DPAD_UP;
using rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER;
using rex::input::X_INPUT_GAMEPAD_LEFT_THUMB;
using rex::input::X_INPUT_GAMEPAD_RIGHT_SHOULDER;
using rex::input::X_INPUT_GAMEPAD_RIGHT_THUMB;
using rex::input::X_INPUT_GAMEPAD_START;
using rex::input::X_INPUT_GAMEPAD_X;
using rex::input::X_INPUT_GAMEPAD_Y;

enum class Screen : uint8_t { kHidden, kOnFoot, kDriving, kBike, kHelicopter, kPassenger, kPhone, kMenu, kCutscene };
enum class Kind : uint8_t { kButton, kStick, kTrigger, kSteer, kReset };
enum class Anchor : uint8_t { kLeft, kRight, kCenter };
enum class Icon : uint8_t {
  kNone, kMenu, kPhone, kView, kCrouch, kCover, kPistol, kDoor, kReload, kFist, kAim, kBullet,
  kJump, kRun, kSteerLeft, kSteerRight, kRadio, kHorn, kEye, kHandbrake, kGas, kBrake,
  kUp, kDown, kLeft, kRight, kCheck, kBack, kHangUp, kLB, kRB, kReset, kSkip, kHouse, kGamepad, kInteract
};

// Icon image files (assets/touch-icons/<name>.png, copied to the user folder by
// the launcher). Drawn tinted; the vector icon is the fallback when a file is
// missing.
const char* IconFile(Icon icon) {
  switch (icon) {
    case Icon::kMenu: return "house";
    case Icon::kInteract: return "interact";
    case Icon::kHouse: return "house";
    case Icon::kPhone: return "phone";
    case Icon::kView: return "view";
    case Icon::kGamepad: return "gamepad";
    case Icon::kCrouch: return "crouch";
    case Icon::kCover: return "cover";
    case Icon::kPistol: return "pistol";
    case Icon::kDoor: return "door";
    case Icon::kReload: return "reload";
    case Icon::kFist: return "fist";
    case Icon::kAim: return "aim";
    case Icon::kBullet: return "bullet";
    case Icon::kJump: return "jump";
    case Icon::kRun: return "run";
    case Icon::kSteerLeft: return "steer_left";
    case Icon::kSteerRight: return "steer_right";
    case Icon::kRadio: return "radio";
    case Icon::kHorn: return "horn";
    case Icon::kEye: return "eye";
    case Icon::kHandbrake: return "handbrake";
    case Icon::kGas: return "gas";
    case Icon::kBrake: return "brake";
    case Icon::kUp: return "up";
    case Icon::kDown: return "down";
    case Icon::kLeft: return "left";
    case Icon::kRight: return "right";
    case Icon::kCheck: return "check";
    case Icon::kBack: return "back";
    case Icon::kHangUp: return "hang_up";
    case Icon::kLB: return "lb";
    case Icon::kRB: return "rb";
    case Icon::kReset: return "reset";
    case Icon::kSkip: return "skip";
    default: return nullptr;
  }
}

std::atomic<uint32_t> g_pause_flag{0};
std::atomic<uint32_t> g_menu_screen{0};

LibertyIconResolver g_icon_resolver = nullptr;
void* g_icon_context = nullptr;

// Accent colours (ring and some icons).
constexpr ImU32 kWhite = IM_COL32(255, 255, 255, 255);
constexpr ImU32 kGreen = IM_COL32(125, 228, 140, 255);
constexpr ImU32 kRed = IM_COL32(246, 104, 98, 255);
constexpr ImU32 kYellow = IM_COL32(246, 207, 92, 255);
constexpr ImU32 kOrange = IM_COL32(240, 132, 86, 255);

// A control as designed on a 1600x737 reference screen. Positions are in
// "design units" (1/737 of the screen height) from the anchor edge, so the
// layout keeps its shape on any aspect ratio.
struct Spec {
  const char* id;
  Kind kind;
  Anchor anchor;
  float x, y, r;
  Icon icon;
  const char* label;
  uint16_t button;  // pad button; trigger: 0 = left, 1 = right; stick: 0/1; steer: -1/+1 via sign
  ImU32 accent;
  ImU32 icon_color;
};

constexpr uint16_t kLT = 0, kRT = 1;

// ---------------- layouts (from the approved v3 mockups) ----------------
// Mapping from the Xbox 360 GTA IV defaults: A sprint/select, B reload/melee/
// back, X jump, Y enter/exit vehicle, RB cover/handbrake, LB drive-by, L3
// crouch/horn, R3 look back, LT aim/brake, RT fire/gas, D-pad weapons/phone.
const std::vector<Spec>& Layout(Screen screen) {
  static const std::vector<Spec> on_foot = {
      {"focus", Kind::kButton, Anchor::kCenter, 120, 58, 30, Icon::kEye, "FOCUS / LOOK", X_INPUT_GAMEPAD_RIGHT_THUMB, 0, kWhite},
      {"radar", Kind::kButton, Anchor::kCenter, 200, 58, 30, Icon::kDown, "RADAR", X_INPUT_GAMEPAD_DPAD_DOWN, 0, kWhite},
      {"pause", Kind::kButton, Anchor::kCenter, 0, 58, 28, Icon::kMenu, "PAUSE", X_INPUT_GAMEPAD_START, 0, kWhite},
      {"phone", Kind::kButton, Anchor::kCenter, 280, 58, 30, Icon::kPhone, "PHONE", X_INPUT_GAMEPAD_DPAD_UP, 0, kWhite},
      {"view", Kind::kButton, Anchor::kCenter, 360, 58, 30, Icon::kView, "CAMERA", X_INPUT_GAMEPAD_BACK, 0, kWhite},
      {"switch", Kind::kButton, Anchor::kCenter, 440, 58, 30, Icon::kGamepad, "XBOX", 0, 0, kWhite},
      {"move", Kind::kStick, Anchor::kLeft, 400, 540, 112, Icon::kNone, "MOVE", 0, 0, kWhite},
      {"crouch", Kind::kButton, Anchor::kLeft, 92, 260, 34, Icon::kCrouch, "CROUCH", X_INPUT_GAMEPAD_LEFT_THUMB, 0, kWhite},
      {"cover", Kind::kButton, Anchor::kLeft, 92, 375, 34, Icon::kCover, "COVER", X_INPUT_GAMEPAD_RIGHT_SHOULDER, 0, kWhite},
      {"left_shoulder", Kind::kButton, Anchor::kLeft, 92, 490, 30, Icon::kInteract, "INTERACT", X_INPUT_GAMEPAD_LEFT_SHOULDER, 0, kWhite},
      {"weapon_prev", Kind::kButton, Anchor::kRight, 251, 140, 22, Icon::kLeft, nullptr, X_INPUT_GAMEPAD_DPAD_LEFT, 0, kWhite},
      {"weapon", Kind::kButton, Anchor::kRight, 175, 140, 44, Icon::kPistol, "WEAPON", X_INPUT_GAMEPAD_DPAD_RIGHT, 0, kWhite},
      {"weapon_next", Kind::kButton, Anchor::kRight, 99, 140, 22, Icon::kRight, nullptr, X_INPUT_GAMEPAD_DPAD_RIGHT, 0, kWhite},
      {"enter", Kind::kButton, Anchor::kRight, 80, 262, 38, Icon::kDoor, "ENTER CAR", X_INPUT_GAMEPAD_Y, 0, kWhite},
      {"melee", Kind::kButton, Anchor::kRight, 255, 262, 34, Icon::kFist, "MELEE", X_INPUT_GAMEPAD_B, 0, kWhite},
      {"reload", Kind::kButton, Anchor::kRight, 395, 375, 32, Icon::kReload, "RELOAD", X_INPUT_GAMEPAD_B, 0, kWhite},
      {"aim", Kind::kTrigger, Anchor::kRight, 265, 395, 48, Icon::kAim, "AIM", kLT, 0, kWhite},
      {"shoot", Kind::kTrigger, Anchor::kRight, 95, 385, 56, Icon::kBullet, "SHOOT", kRT, kOrange, kWhite},
      {"jump", Kind::kButton, Anchor::kRight, 365, 505, 42, Icon::kJump, "JUMP", X_INPUT_GAMEPAD_X, 0, kWhite},
      {"sprint", Kind::kButton, Anchor::kRight, 365, 650, 40, Icon::kRun, "SPRINT", X_INPUT_GAMEPAD_A, 0, kWhite},
      {"camera", Kind::kStick, Anchor::kRight, 170, 590, 88, Icon::kView, "CAMERA", 1, 0, kWhite},
  };
  static const std::vector<Spec> driving = {
      {"radar", Kind::kButton, Anchor::kCenter, 200, 58, 30, Icon::kDown, "RADAR", X_INPUT_GAMEPAD_DPAD_DOWN, 0, kWhite},
      {"cinematic", Kind::kButton, Anchor::kLeft, 92, 490, 30, Icon::kView, "CINEMATIC", X_INPUT_GAMEPAD_B, 0, kWhite},
      {"radio_previous", Kind::kButton, Anchor::kLeft, 175, 260, 30, Icon::kLeft, "PREV RADIO", X_INPUT_GAMEPAD_DPAD_LEFT, 0, kWhite},
      {"pause", Kind::kButton, Anchor::kCenter, 0, 58, 28, Icon::kMenu, "PAUSE", X_INPUT_GAMEPAD_START, 0, kWhite},
      {"phone", Kind::kButton, Anchor::kCenter, 280, 58, 30, Icon::kPhone, "PHONE", X_INPUT_GAMEPAD_DPAD_UP, 0, kWhite},
      {"view", Kind::kButton, Anchor::kCenter, 360, 58, 30, Icon::kView, "CAMERA", X_INPUT_GAMEPAD_BACK, 0, kWhite},
      {"switch", Kind::kButton, Anchor::kCenter, 440, 58, 30, Icon::kGamepad, "XBOX", 0, 0, kWhite},
      {"steer_left", Kind::kSteer, Anchor::kLeft, 330, 585, 64, Icon::kSteerLeft, "STEER LEFT", 0, 0, kWhite},
      {"steer_right", Kind::kSteer, Anchor::kLeft, 505, 585, 64, Icon::kSteerRight, "STEER RIGHT", 1, 0, kWhite},
      {"radio", Kind::kButton, Anchor::kLeft, 92, 260, 34, Icon::kRadio, "RADIO", X_INPUT_GAMEPAD_DPAD_RIGHT, 0, kWhite},
      {"horn", Kind::kButton, Anchor::kLeft, 92, 375, 34, Icon::kHorn, "HORN", X_INPUT_GAMEPAD_LEFT_THUMB, 0, kWhite},
      {"weapon", Kind::kButton, Anchor::kRight, 175, 140, 44, Icon::kPistol, "WEAPON", X_INPUT_GAMEPAD_X, 0, kWhite},
      {"exit", Kind::kButton, Anchor::kRight, 80, 262, 38, Icon::kDoor, "EXIT CAR", X_INPUT_GAMEPAD_Y, 0, kWhite},
      {"look_back", Kind::kButton, Anchor::kRight, 410, 460, 36, Icon::kEye, "LOOK BACK", X_INPUT_GAMEPAD_RIGHT_THUMB, 0, kWhite},
      {"drive_by", Kind::kButton, Anchor::kRight, 100, 405, 38, Icon::kBullet, "DRIVE-BY", X_INPUT_GAMEPAD_LEFT_SHOULDER, kOrange, kWhite},
      {"handbrake", Kind::kButton, Anchor::kRight, 260, 455, 44, Icon::kHandbrake, "HANDBRAKE", X_INPUT_GAMEPAD_RIGHT_SHOULDER, kYellow, kYellow},
      {"brake", Kind::kTrigger, Anchor::kRight, 270, 620, 56, Icon::kBrake, "BRAKE", kLT, kRed, kRed},
      {"gas", Kind::kTrigger, Anchor::kRight, 100, 575, 70, Icon::kGas, "GAS", kRT, kGreen, kGreen},
      {"camera", Kind::kStick, Anchor::kRight, 440, 615, 72, Icon::kView, "CAMERA", 1, 0, kWhite},
  };
  static const std::vector<Spec> bike = [&] {
    auto controls = driving;
    std::erase_if(controls, [](const Spec& c) { return c.kind == Kind::kSteer; });
    controls.push_back({"steer_lean",Kind::kStick,Anchor::kLeft,400,540,112,Icon::kNone,"STEER / LEAN",0,0,kWhite});
    return controls;
  }();
  static const std::vector<Spec> helicopter = [&] {
    std::vector<Spec> controls;
    for (auto c : driving) {
      const std::string_view id(c.id);
      if (id == "steer_left" || id == "steer_right" || id == "handbrake" || id == "look_back") continue;
      if (id == "gas") { c.id="ascend"; c.label="ASCEND"; c.icon=Icon::kUp; }
      if (id == "brake") { c.id="descend"; c.label="DESCEND"; c.icon=Icon::kDown; }
      if (id == "drive_by") { c.id="fire"; c.button=X_INPUT_GAMEPAD_A; c.label="FIRE"; }
      if (id == "exit") c.label="EXIT";
      controls.push_back(c);
    }
    controls.push_back({"pitch_roll",Kind::kStick,Anchor::kLeft,400,540,112,Icon::kNone,"PITCH / ROLL",0,0,kWhite});
    controls.push_back({"yaw_left",Kind::kButton,Anchor::kRight,395,375,34,Icon::kLeft,"YAW LEFT",X_INPUT_GAMEPAD_LEFT_SHOULDER,0,kWhite});
    controls.push_back({"yaw_right",Kind::kButton,Anchor::kRight,265,375,34,Icon::kRight,"YAW RIGHT",X_INPUT_GAMEPAD_RIGHT_SHOULDER,0,kWhite});
    return controls;
  }();
  static const std::vector<Spec> passenger = [&] {
    auto controls = on_foot;
    std::erase_if(controls, [](const Spec& c) {
      const std::string_view id(c.id);
      return id=="move" || id=="jump" || id=="sprint" || id=="crouch" || id=="cover" || id=="melee";
    });
    for (auto& c : controls) {
      if (std::string_view(c.id)=="enter") c.label="EXIT";
      if (std::string_view(c.id)=="shoot") { c.kind=Kind::kButton; c.button=X_INPUT_GAMEPAD_LEFT_SHOULDER; }
    }
    return controls;
  }();
  static const std::vector<Spec> phone = {
      {"pause", Kind::kButton, Anchor::kCenter, 0, 58, 28, Icon::kMenu, "PAUSE", X_INPUT_GAMEPAD_START, 0, kWhite},
      {"up", Kind::kButton, Anchor::kLeft, 260, 400, 44, Icon::kUp, "UP", X_INPUT_GAMEPAD_DPAD_UP, 0, kWhite},
      {"down", Kind::kButton, Anchor::kLeft, 260, 600, 44, Icon::kDown, "DOWN", X_INPUT_GAMEPAD_DPAD_DOWN, 0, kWhite},
      {"left", Kind::kButton, Anchor::kLeft, 160, 500, 44, Icon::kLeft, "LEFT", X_INPUT_GAMEPAD_DPAD_LEFT, 0, kWhite},
      {"right", Kind::kButton, Anchor::kLeft, 360, 500, 44, Icon::kRight, "RIGHT", X_INPUT_GAMEPAD_DPAD_RIGHT, 0, kWhite},
      {"select", Kind::kButton, Anchor::kRight, 130, 560, 62, Icon::kCheck, "SELECT", X_INPUT_GAMEPAD_A, kGreen, kGreen},
      {"back", Kind::kButton, Anchor::kRight, 295, 620, 48, Icon::kBack, "BACK", X_INPUT_GAMEPAD_B, kRed, kRed},
      {"hang_up", Kind::kButton, Anchor::kRight, 270, 455, 40, Icon::kHangUp, "HANG UP", X_INPUT_GAMEPAD_B, kRed, kRed},
  };
  static const std::vector<Spec> menu = {
      {"zoom_in", Kind::kTrigger, Anchor::kRight, 95, 180, 30, Icon::kUp, "ZOOM IN", kRT, 0, kWhite},
      {"zoom_out", Kind::kTrigger, Anchor::kRight, 175, 180, 30, Icon::kDown, "ZOOM OUT", kLT, 0, kWhite},
      {"map_move", Kind::kStick, Anchor::kLeft, 450, 520, 75, Icon::kNone, "MOVE MAP", 0, 0, kWhite},
      {"map_x", Kind::kButton, Anchor::kRight, 95, 350, 30, Icon::kCheck, "X", X_INPUT_GAMEPAD_X, 0, kWhite},
      {"map_y", Kind::kButton, Anchor::kRight, 175, 350, 30, Icon::kCheck, "Y", X_INPUT_GAMEPAD_Y, 0, kWhite},
      {"prev_tab", Kind::kButton, Anchor::kLeft, 215, 290, 38, Icon::kLeft, "PREV TAB", X_INPUT_GAMEPAD_LEFT_SHOULDER, 0, kWhite},
      {"next_tab", Kind::kButton, Anchor::kRight, 215, 290, 38, Icon::kRight, "NEXT TAB", X_INPUT_GAMEPAD_RIGHT_SHOULDER, 0, kWhite},
      {"up", Kind::kButton, Anchor::kLeft, 200, 425, 42, Icon::kUp, "UP", X_INPUT_GAMEPAD_DPAD_UP, 0, kWhite},
      {"down", Kind::kButton, Anchor::kLeft, 200, 615, 42, Icon::kDown, "DOWN", X_INPUT_GAMEPAD_DPAD_DOWN, 0, kWhite},
      {"left", Kind::kButton, Anchor::kLeft, 105, 520, 42, Icon::kLeft, "LEFT", X_INPUT_GAMEPAD_DPAD_LEFT, 0, kWhite},
      {"right", Kind::kButton, Anchor::kLeft, 295, 520, 42, Icon::kRight, "RIGHT", X_INPUT_GAMEPAD_DPAD_RIGHT, 0, kWhite},
      {"select", Kind::kButton, Anchor::kRight, 140, 560, 62, Icon::kCheck, "SELECT", X_INPUT_GAMEPAD_A, kGreen, kGreen},
      {"back", Kind::kButton, Anchor::kRight, 300, 620, 48, Icon::kBack, "BACK", X_INPUT_GAMEPAD_B, kRed, kRed},
      {"resume", Kind::kButton, Anchor::kRight, 290, 460, 38, Icon::kMenu, "RESUME", X_INPUT_GAMEPAD_START, 0, kWhite},
  };
  // Skip sends A, B and X together in one short tap (see ReadLibertyTouchOverlay):
  // whichever of them the title uses to skip, the others do nothing in a cutscene.
  static const std::vector<Spec> cutscene = {
      {"pause", Kind::kButton, Anchor::kCenter, 0, 58, 28, Icon::kMenu, "PAUSE", X_INPUT_GAMEPAD_START, 0, kWhite},
      {"skip", Kind::kButton, Anchor::kRight, 140, 600, 60, Icon::kSkip, "SKIP", 0, 0, kWhite},
  };
  static const std::vector<Spec> none;
  switch (screen) {
    case Screen::kOnFoot: return on_foot;
    case Screen::kDriving: return driving;
    case Screen::kBike: return bike;
    case Screen::kHelicopter: return helicopter;
    case Screen::kPassenger: return passenger;
    case Screen::kPhone: return phone;
    case Screen::kMenu: return menu;
    case Screen::kCutscene: return cutscene;
    default: return none;
  }
}

const char* ScreenName(Screen screen) {
  switch (screen) {
    case Screen::kOnFoot: return "on_foot";
    case Screen::kDriving: return "driving";
    case Screen::kBike: return "bike";
    case Screen::kHelicopter: return "helicopter";
    case Screen::kPassenger: return "passenger";
    case Screen::kPhone: return "phone";
    case Screen::kMenu: return "menu";
    case Screen::kCutscene: return "cutscene";
    default: return "hidden";
  }
}

// Which screen the game is on right now.
Screen CurrentScreen(bool forced_menu, bool pause_flag_verified, uint32_t* logged) {
  const TouchContextSnapshot context = GetTouchContextSnapshot();
  const uint32_t pause_flag = g_pause_flag.load(std::memory_order_relaxed);
  const bool pause_menu = false;  // flag logged only: it does not track the pause menu reliably
  const uint32_t facts = uint32_t(pause_menu) << 9 | uint32_t(context.valid) | uint32_t(context.frontend) << 1 |
                         uint32_t(context.map) << 2 | uint32_t(context.loading) << 3 |
                         uint32_t(context.cutscene) << 4 | uint32_t(context.phone_visible) << 5 |
                         uint32_t(context.playing) << 6 | uint32_t(context.gameplay_allowed) << 7 |
                         uint32_t(forced_menu) << 8 | uint32_t(context.base_mode) << 12;
  if (logged && *logged != facts && rex::diagnostics::IsEnabled(rex::diagnostics::Category::kGuestHooks)) {
    *logged = facts;
    REXLOG_WARN("liberty-touch: pause-flag={:08X} menu-screen={} valid={} frontend={} map={} "
                "loading={} cutscene={} phone={} playing={} gameplay={} forced-menu={} mode={} verified={}",
                pause_flag, g_menu_screen.load(std::memory_order_relaxed), context.valid,
                context.frontend, context.map, context.loading, context.cutscene,
                context.phone_visible, context.playing, context.gameplay_allowed, forced_menu,
                int(context.base_mode), pause_flag_verified);
  }
  if (forced_menu || pause_menu) return Screen::kMenu;
  // Menus first: while a menu is open the player facts are often not
  // readable, so "not valid" must not hide (or replace) the menu controls.
  if (context.frontend || context.map) return Screen::kMenu;
  // Boot, legal and loading screens: no controls until the game is running.
  if (!context.valid || context.loading) return Screen::kHidden;
  if (context.cutscene) return Screen::kCutscene;
  if (context.phone_visible) return Screen::kPhone;
  switch (context.base_mode) {
    case ContextTouchMode::kVehicleAutomobile:
    case ContextTouchMode::kVehicleBoat:
    case ContextTouchMode::kVehicleDriverUnknown:
      return Screen::kDriving;
    case ContextTouchMode::kVehicleBike: return Screen::kBike;
    case ContextTouchMode::kVehicleHelicopter: return Screen::kHelicopter;
    case ContextTouchMode::kVehiclePassenger: return Screen::kPassenger;
    case ContextTouchMode::kPhone:
      return Screen::kPhone;
    case ContextTouchMode::kFrontend:
    case ContextTouchMode::kMap:
      return Screen::kMenu;
    default:
      return Screen::kOnFoot;
  }
}

// A placed control for this frame (screen pixels).
struct Placed {
  const Spec* spec;
  ImVec2 center;
  float r;
  float opacity=1;
};

struct Finger {
  int control = -1;  // index into placed, -1 = nothing
  ImVec2 grab_offset{0,0};
  ImVec2 last{0,0};
  bool camera=false;
  ImVec2 origin{0,0};
};

struct State {
  std::mutex mutex;
  AimHoldPolicy aim_hold;
  std::array<float,64> press_feedback{};
  bool visible = false;
  bool editing = false;
  bool layout_loaded = false;
  bool geometry_dirty=true, placed_editing=false, placed_swipe=false, swipe_active=false;
  float placed_width=0, placed_height=0, placed_scale=0, placed_camera_scale=0;
  std::array<float,4> placed_safe{};
  std::unordered_set<uint64_t> blocked_fingers;
  Screen screen = Screen::kHidden;
  std::vector<Placed> placed;
  std::unordered_map<uint64_t, Finger> fingers;
  std::vector<bool> held;
  std::array<ImVec2, 2> stick{ImVec2(0, 0), ImVec2(0, 0)};  // -1..1, screen axes
  float steer = 0.0f;
  // Custom offsets: "screen/id" -> offset in design units.
  std::map<std::string, ImVec2> offsets;
  std::map<std::string,float> sizes;
  std::map<std::string,float> opacities;
  std::unordered_set<std::string> hidden;
  int editor_screen=0, selected=-1;
  int last_editor_context=0;
  std::string save_error;
  ImVec2 smooth_move{0,0};
  std::string placed_move_mode;
  std::chrono::steady_clock::time_point pause_down{};
  bool pause_long_handled = false;
  bool pause_was_held = false;
  // A short tap on Pause sends Start for a moment after the finger lifts
  // (holding it would open the pause menu before edit mode could start).
  std::chrono::steady_clock::time_point start_pulse_until{};
  // Skip: A + B + X for a moment after each new press.
  std::chrono::steady_clock::time_point skip_pulse_until{};
  bool skip_was_held = false;
  bool switch_was_held = false;
  // Pause-menu fallback: the title's frontend flag does not cover every menu,
  // so tapping PAUSE also switches to the menu controls until RESUME.
  bool forced_menu = false;
  bool forced_saw_pause = false;  // gameplay stopped after the Pause tap (menu opened)
  std::chrono::steady_clock::time_point gameplay_since{};
  // The pause flag is only trusted once it has been seen to rise (0 -> non-zero)
  // right after a Pause tap; until then a wrong flag cannot pin the menu.
  bool pause_flag_verified = false;
  uint32_t pause_flag_before_tap = 0;
  std::chrono::steady_clock::time_point pause_check_until{};
  bool resume_was_held = false;
  // Fallback when the pause flag cannot be verified: Select goes one level
  // into the menu, Back one level out; Back at the top level closes it.
  int menu_depth = 0;
  bool select_was_held = false;
  bool back_was_held = false;
  // Diagnostics: last logged context facts.
  uint32_t logged_context = 0xFFFFFFFFu;
};

State& state() {
  static State s;
  return s;
}
std::atomic<int> editor_request{-1};
std::atomic<bool> editor_close_request{false};
std::mutex look_mutex;
TouchLookDelta look_delta;

struct PublishedInput {
  std::mutex mutex;
  rex::input::X_INPUT_GAMEPAD pad{};
  bool visible=false;
  std::chrono::steady_clock::time_point start_pulse_until{},skip_pulse_until{};
};
PublishedInput& published_input() { static PublishedInput input; return input; }
void PublishInput(const State& s) {
  rex::input::X_INPUT_GAMEPAD pad{}; auto* output=&pad;
  if (s.visible && !s.editing) {
  uint16_t buttons = 0;
  uint8_t triggers[2] = {0, 0};
  for (size_t i = 0; i < s.placed.size() && i < s.held.size(); ++i) {
    if (!s.held[i]) continue;
    const Spec& spec = *s.placed[i].spec;
    if (std::string_view(spec.id) == "pause") continue;  // sent on release, below
    if (spec.kind == Kind::kButton) buttons |= spec.button;
    if (spec.kind == Kind::kTrigger) triggers[spec.button & 1] = 255;
  }
  constexpr float kDeadZone = 0.12f;
  const auto axis = [&](ImVec2 v, int16_t* x_out, int16_t* y_out) {
    const float length = std::hypot(v.x, v.y);
    if (length < kDeadZone) return;
    const float k = std::min((length - kDeadZone) / (1.0f - kDeadZone), 1.0f) / length;
    *x_out = int16_t(std::clamp(v.x * k, -1.0f, 1.0f) * 32767.0f);
    *y_out = int16_t(std::clamp(-v.y * k, -1.0f, 1.0f) * 32767.0f);
  };
  // The pad fields are big-endian (rex::be); fill locals, then assign.
  int16_t lx = 0, ly = 0, rx = 0, ry = 0;
  axis(s.stick[0], &lx, &ly);
  if (!s.swipe_active) axis(s.stick[1],&rx,&ry);
  if (s.steer != 0.0f) lx = int16_t(std::clamp(s.steer, -1.0f, 1.0f) * 32767.0f);
  output->thumb_lx = lx;
  output->thumb_ly = ly;
  output->thumb_rx = rx;
  output->thumb_ry = ry;
  output->buttons = buttons;
  output->left_trigger = triggers[0];
  output->right_trigger = triggers[1];
  }
  auto& input=published_input(); std::lock_guard lock(input.mutex);
  input.pad=pad; input.visible=s.visible;
  input.start_pulse_until=s.editing ? std::chrono::steady_clock::time_point{} : s.start_pulse_until;
  input.skip_pulse_until=s.editing ? std::chrono::steady_clock::time_point{} : s.skip_pulse_until;
}

std::filesystem::path LayoutPath() {
#if REX_PLATFORM_ANDROID
  std::filesystem::path root;
  if(const auto* external=SDL_GetAndroidExternalStoragePath();external&&*external)root=external;
  if(root.empty())root=rex::filesystem::GetUserFolder();
  return root/"LibertyRecomp"/"liberty_touch_layout.txt";
#else
  return rex::filesystem::GetUserFolder() / "liberty_touch_layout.txt";
#endif
}

void LoadOffsets(State& s) {
  s.layout_loaded = true;
  std::ifstream file(LayoutPath());
#if REX_PLATFORM_ANDROID
  if(!file)file.open(rex::filesystem::GetUserFolder()/"liberty_touch_layout.txt");
#endif
  std::string line;
  while (std::getline(file, line)) {
    std::istringstream in(line);
    std::string key;
    float dx = 0, dy = 0;
    if (in >> key >> dx >> dy && key.size()<96 && std::isfinite(dx) && std::isfinite(dy)) {
      s.offsets[key] = ImVec2(std::clamp(dx, -2000.0f, 2000.0f), std::clamp(dy, -800.0f, 800.0f));
      float scale=1;int hidden=0;
      if(in>>scale>>hidden && std::isfinite(scale)) {
        s.sizes[key]=std::clamp(scale,0.35f,4.0f);
        if(hidden)s.hidden.insert(key);float opacity=1;if(in>>opacity&&std::isfinite(opacity))s.opacities[key]=std::clamp(opacity,0.f,1.f);
      }
    }
  }
}

bool SaveOffsets(const State& s) {
  std::error_code directory_error;std::filesystem::create_directories(LayoutPath().parent_path(),directory_error);
  if(directory_error)return false;
  auto temporary=LayoutPath();temporary += ".tmp";
  std::ofstream file(temporary, std::ios::trunc);
  file << "# Liberty touch layout: screen/control dx dy scale hidden\n";
  for (const auto& [key, offset] : s.offsets) {
    auto size=s.sizes.find(key);
    file << key << ' ' << offset.x << ' ' << offset.y << ' '
         << (size==s.sizes.end()?1.0f:size->second) << ' ' << s.hidden.contains(key) << ' ' << (s.opacities.contains(key)?s.opacities.at(key):1.f) << '\n';
  }
  file.flush();if(!file)return false;file.close();if(file.fail())return false;
  std::error_code error;std::filesystem::rename(temporary,LayoutPath(),error);
  if(error)REXLOG_WARN("Could not save mobile touch layout: {}",error.message());
  return !error;
}

bool FinishEditing(State& s) {
  if(!SaveOffsets(s)) {
    s.save_error="Could not save the layout. Check available storage and try again.";
    return false;
  }
  s.save_error.clear();s.editing=false;s.geometry_dirty=true;s.selected=-1;
  for(const auto& [key,f]:s.fingers)s.blocked_fingers.insert(key);
  s.fingers.clear();s.held.assign(s.held.size(),false);
  s.stick={ImVec2(0,0),ImVec2(0,0)};s.start_pulse_until=s.skip_pulse_until={};
  s.pause_was_held=false;PublishTouchLook(0,0,false);PublishInput(s);
#if REX_PLATFORM_ANDROID
  // The Android callback only queues work. Never query overlay state while
  // the render thread holds this mutex.
  GTA4_AndroidTouchEditorClosed();
#endif
  return true;
}

float Scale() {
  const double value=REXCVAR_GET(gta4_touch_gamepad_scale);
  return std::isfinite(value) ? float(std::clamp(value,0.5,2.0)) : 1.0f;
}
float Opacity() {
  const double value=REXCVAR_GET(gta4_touch_gamepad_opacity);
  return std::isfinite(value) ? float(std::clamp(value,0.0,1.6)) : 1.0f;
}

ImU32 Alpha(ImU32 color, float alpha) {
  const float a = float((color >> IM_COL32_A_SHIFT) & 0xFF) * std::clamp(alpha, 0.0f, 1.0f);
  return (color & ~IM_COL32_A_MASK) | (uint32_t(a) << IM_COL32_A_SHIFT);
}

bool SwipeCamera() { return REXCVAR_GET(gta4_touch_camera)=="swipe"; }
bool CameraScreen(Screen screen) {
  return screen==Screen::kOnFoot || screen==Screen::kDriving || screen==Screen::kBike ||
      screen==Screen::kHelicopter || screen==Screen::kPassenger;
}
std::array<float,4> SafeArea(float w,float h) {
#if defined(__ANDROID__)
  return theme::FullscreenArea(w,h);
#else
  rex::input::TouchPresentationState p{};
  std::array<float,4> safe{0,0,w,h};
  if (rex::input::GetTouchPresentationState(&p) && p.valid && p.logical_width>0 && p.logical_height>0 &&
      p.logical_safe_width>0 && p.logical_safe_height>0) {
    const float sx=w/p.logical_width, sy=h/p.logical_height;
    const float x=std::clamp(p.logical_safe_x*sx,0.0f,w), y=std::clamp(p.logical_safe_y*sy,0.0f,h);
    const float right=std::clamp((p.logical_safe_x+p.logical_safe_width)*sx,x,w);
    const float bottom=std::clamp((p.logical_safe_y+p.logical_safe_height)*sy,y,h);
    if (std::isfinite(x+y+right+bottom) && right>x && bottom>y) safe={x,y,right-x,bottom-y};
  }
  return safe;
#endif
}
void Place(State& s,float w,float h) {
  const auto safe=SafeArea(w,h);
  const bool swipe=SwipeCamera();
  const auto move_mode=REXCVAR_GET(gta4_touch_move_mode);
  const float unit=safe[3]/737.0f, k=Scale(),camera_scale=float(REXCVAR_GET(gta4_touch_camera_scale));
  if (!s.geometry_dirty && s.placed_width==w && s.placed_height==h && s.placed_scale==k && s.placed_camera_scale==camera_scale &&
      s.placed_editing==s.editing && s.placed_swipe==swipe && s.placed_safe==safe && s.placed_move_mode==move_mode) {
    std::fill(s.held.begin(),s.held.end(),false); return;
  }
  s.geometry_dirty=false; s.placed_width=w; s.placed_height=h; s.placed_scale=k; s.placed_camera_scale=camera_scale;
  s.placed_editing=s.editing; s.placed_swipe=swipe; s.placed_safe=safe;
  s.placed_move_mode=move_mode;
  s.fingers.clear(); s.placed.clear();
  for (const Spec& spec : Layout(s.screen)) {
    if (std::string_view(spec.id)=="switch") continue;
    if (swipe && !s.editing && std::string_view(spec.id)=="camera") continue;
    const std::string key=std::string(ScreenName(s.screen))+"/"+spec.id;
    if(!s.editing&&s.hidden.contains(key))continue;
    ImVec2 offset(0, 0);
    if (auto it = s.offsets.find(key); it != s.offsets.end()) {
      offset = it->second;
    }
    float x = spec.x + offset.x;
    float px=spec.anchor==Anchor::kLeft ? safe[0]+x*unit : spec.anchor==Anchor::kRight ? safe[0]+safe[2]-x*unit : safe[0]+safe[2]*0.5f+x*unit;
    const auto size=s.sizes.find(key);
    const float r=std::min(spec.r*unit*k*(std::string_view(spec.id)=="camera"?camera_scale:1.f)*(size==s.sizes.end()?1.f:size->second),std::min(safe[2],safe[3])*0.45f);
    px=std::clamp(px,safe[0]+r,safe[0]+safe[2]-r);
    const float py=std::clamp(safe[1]+(spec.y+offset.y)*unit,safe[1]+r,safe[1]+safe[3]-r);
    s.placed.push_back({&spec, ImVec2(px, py), r,s.opacities.contains(key)?s.opacities.at(key):1.f});
  }
  s.held.assign(s.placed.size(), false);
}

int HitTest(const State& s, ImVec2 p) {
  int best = -1;
  float best_score = FLT_MAX;
  for (size_t i = 0; i < s.placed.size(); ++i) {
    const auto& c = s.placed[i];
    if(!s.editing && c.spec->kind==Kind::kStick && c.spec->button==0 &&
        p.x>=s.placed_safe[0]+s.placed_safe[2]*0.5f)continue;
    const float d = std::hypot(p.x - c.center.x, p.y - c.center.y);
    const float capture = c.r * (c.spec->kind == Kind::kStick ? 1.5f : 1.35f);
    if (d <= capture && d / c.r < best_score) {
      best_score = d / c.r;
      best = int(i);
    }
  }
  return best;
}

// ---------------- drawing ----------------
// Geometric fallback glyphs use one clean pass when a texture is unavailable.
struct Pen {
  ImDrawList* d;
  ImU32 col;
  float grow;  // extra half-thickness for the outline pass
  void Line(ImVec2 a, ImVec2 b, float w) const { d->AddLine(a, b, col, w + 2 * grow); }
  void Disc(ImVec2 c, float r) const { d->AddCircleFilled(c, r + grow, col, 24); }
  void Ring(ImVec2 c, float r, float w) const { d->AddCircle(c, r, col, 32, w + 2 * grow); }
  void Rect(ImVec2 a, ImVec2 b, float rounding = 0) const {
    d->AddRectFilled(ImVec2(a.x - grow, a.y - grow), ImVec2(b.x + grow, b.y + grow), col, rounding);
  }
  void Tri(ImVec2 a, ImVec2 b, ImVec2 c) const {
    d->AddTriangleFilled(a, b, c, col);
    if (grow > 0) {
      ImVec2 pts[3] = {a, b, c};
      d->AddPolyline(pts, 3, col, ImDrawFlags_Closed, 2 * grow);
    }
  }
  void Poly(const ImVec2* pts, int n) const {
    d->AddConvexPolyFilled(pts, n, col);
    if (grow > 0) d->AddPolyline(pts, n, col, ImDrawFlags_Closed, 2 * grow);
  }
  void Arc(ImVec2 c, float r, float a0, float a1, float w) const {
    d->PathArcTo(c, r, a0, a1, 20);
    d->PathStroke(col, 0, w + 2 * grow);
  }
};

ImVec2 P(ImVec2 c, float r, float x, float y) { return ImVec2(c.x + x * r, c.y + y * r); }

void Limb(const Pen& p, std::initializer_list<ImVec2> pts, float w) {
  const ImVec2* prev = nullptr;
  for (const ImVec2& pt : pts) {
    if (prev) p.Line(*prev, pt, w);
    p.Disc(pt, w * 0.5f);
    prev = &pt;
  }
}

void DrawIcon(const Pen& p, Icon icon, ImVec2 c, float r, ImU32 hole) {
  const float w = r * 0.2f;
  switch (icon) {
    case Icon::kMenu:
      for (int i = -1; i <= 1; ++i) p.Rect(P(c, r, -0.6f, i * 0.38f - 0.1f), P(c, r, 0.6f, i * 0.38f + 0.1f), r * 0.08f);
      break;
    case Icon::kPhone:
      p.Rect(P(c, r, -0.42f, -0.82f), P(c, r, 0.42f, 0.82f), r * 0.14f);
      if (p.grow == 0) p.d->AddRectFilled(P(c, r, -0.28f, -0.6f), P(c, r, 0.28f, 0.25f), hole);
      break;
    case Icon::kView:
      p.Rect(P(c, r, -0.75f, -0.3f), P(c, r, 0.22f, 0.48f), r * 0.1f);
      p.Tri(P(c, r, 0.28f, 0.09f), P(c, r, 0.78f, -0.25f), P(c, r, 0.78f, 0.43f));
      p.Disc(P(c, r, -0.5f, -0.58f), r * 0.2f);
      p.Disc(P(c, r, -0.08f, -0.58f), r * 0.2f);
      break;
    case Icon::kCrouch:
      p.Disc(P(c, r, 0.12f, -0.53f), r * 0.17f);
      Limb(p, {P(c, r, 0.05f, -0.25f), P(c, r, -0.3f, 0.18f)}, w * 1.25f);
      Limb(p, {P(c, r, -0.3f, 0.18f), P(c, r, 0.22f, 0.32f), P(c, r, 0.15f, 0.82f)}, w);
      Limb(p, {P(c, r, -0.3f, 0.18f), P(c, r, -0.55f, 0.82f)}, w);
      Limb(p, {P(c, r, -0.05f, -0.12f), P(c, r, 0.45f, 0.02f)}, w * 0.85f);
      break;
    case Icon::kCover:
      p.Rect(P(c, r, 0.3f, -0.75f), P(c, r, 0.75f, 0.85f));
      p.Disc(P(c, r, -0.16f, -0.39f), r * 0.16f);
      Limb(p, {P(c, r, -0.12f, -0.15f), P(c, r, -0.2f, 0.3f)}, w * 1.2f);
      Limb(p, {P(c, r, -0.2f, 0.3f), P(c, r, -0.55f, 0.45f), P(c, r, -0.45f, 0.82f)}, w);
      Limb(p, {P(c, r, -0.2f, 0.3f), P(c, r, 0.1f, 0.82f)}, w);
      Limb(p, {P(c, r, -0.12f, -0.05f), P(c, r, 0.22f, -0.05f)}, w * 0.85f);
      break;
    case Icon::kPistol: {
      p.Rect(P(c, r, -0.88f, -0.46f), P(c, r, 0.88f, -0.08f), r * 0.04f);
      const ImVec2 grip[4] = {P(c, r, -0.62f, -0.08f), P(c, r, -0.12f, -0.08f), P(c, r, -0.32f, 0.82f), P(c, r, -0.78f, 0.82f)};
      p.Poly(grip, 4);
      p.Arc(P(c, r, 0.06f, 0.06f), r * 0.24f, 0.0f, 3.14159f, r * 0.1f);
      break;
    }
    case Icon::kDoor: {
      const ImVec2 body[5] = {P(c, r, -0.78f, -0.05f), P(c, r, -0.3f, -0.72f), P(c, r, 0.7f, -0.72f), P(c, r, 0.7f, 0.75f), P(c, r, -0.78f, 0.75f)};
      p.Poly(body, 5);
      if (p.grow == 0) {
        const ImVec2 win[4] = {P(c, r, -0.52f, -0.1f), P(c, r, -0.18f, -0.55f), P(c, r, 0.55f, -0.55f), P(c, r, 0.55f, -0.1f)};
        p.d->AddConvexPolyFilled(win, 4, hole);
        p.d->AddRectFilled(P(c, r, 0.18f, 0.12f), P(c, r, 0.52f, 0.25f), hole, r * 0.05f);
      }
      break;
    }
    case Icon::kReload:
      p.Arc(c, r * 0.62f, -0.87f, 4.1f, w);
      p.Tri(P(c, r, 0.33f, -0.93f), P(c, r, 0.78f, -0.38f), P(c, r, 0.1f, -0.3f));
      break;
    case Icon::kInteract:
    case Icon::kFist:
      p.Rect(P(c, r, -0.62f, -0.45f), P(c, r, 0.5f, 0.55f), r * 0.22f);
      if (p.grow == 0)
        for (int i = 0; i < 3; ++i) p.d->AddLine(P(c, r, -0.3f + i * 0.28f, -0.45f), P(c, r, -0.3f + i * 0.28f, -0.02f), hole, r * 0.08f);
      break;
    case Icon::kAim:
      p.Ring(c, r * 0.58f, w * 0.8f);
      for (int i = 0; i < 4; ++i) {
        const float a = i * 1.5708f, ca = std::cos(a), sa = std::sin(a);
        p.Line(ImVec2(c.x + ca * r * 0.3f, c.y + sa * r * 0.3f), ImVec2(c.x + ca * r * 0.98f, c.y + sa * r * 0.98f), w * 0.9f);
      }
      p.Disc(c, w * 0.7f);
      break;
    case Icon::kBullet: {
      // Tilted cartridge: body line with a round nose.
      const float a = -0.9f, ca = std::cos(a), sa = std::sin(a);
      const ImVec2 base(c.x - ca * r * 0.62f, c.y - sa * r * 0.62f), nose(c.x + ca * r * 0.35f, c.y + sa * r * 0.35f);
      p.Line(base, nose, r * 0.52f);
      p.Disc(nose, r * 0.26f);
      p.Disc(ImVec2(c.x + ca * r * 0.5f, c.y + sa * r * 0.5f), r * 0.2f);
      break;
    }
    case Icon::kJump:
      p.Disc(P(c, r, 0.0f, -0.81f), r * 0.17f);
      Limb(p, {P(c, r, 0, -0.55f), P(c, r, 0, 0.05f)}, w * 1.25f);
      Limb(p, {P(c, r, 0, -0.42f), P(c, r, -0.42f, -0.78f)}, w * 0.85f);
      Limb(p, {P(c, r, 0, -0.42f), P(c, r, 0.42f, -0.78f)}, w * 0.85f);
      Limb(p, {P(c, r, 0, 0.05f), P(c, r, -0.35f, 0.3f), P(c, r, -0.25f, 0.62f)}, w);
      Limb(p, {P(c, r, 0, 0.05f), P(c, r, 0.35f, 0.3f), P(c, r, 0.25f, 0.62f)}, w);
      p.Rect(P(c, r, -0.8f, 0.8f), P(c, r, 0.8f, 0.93f), r * 0.06f);
      break;
    case Icon::kRun:
      p.Disc(P(c, r, 0.25f, -0.78f), r * 0.17f);
      Limb(p, {P(c, r, 0.18f, -0.5f), P(c, r, -0.05f, 0.08f)}, w * 1.25f);
      Limb(p, {P(c, r, -0.05f, 0.08f), P(c, r, 0.35f, 0.32f), P(c, r, 0.25f, 0.82f)}, w);
      Limb(p, {P(c, r, -0.05f, 0.08f), P(c, r, -0.35f, 0.45f), P(c, r, -0.72f, 0.4f)}, w);
      Limb(p, {P(c, r, 0.15f, -0.38f), P(c, r, 0.5f, -0.12f), P(c, r, 0.72f, -0.4f)}, w * 0.85f);
      Limb(p, {P(c, r, 0.15f, -0.38f), P(c, r, -0.22f, -0.28f), P(c, r, -0.45f, -0.02f)}, w * 0.85f);
      break;
    case Icon::kSteerLeft:
    case Icon::kSteerRight: {
      const float s = icon == Icon::kSteerLeft ? -1.0f : 1.0f;
      p.Tri(P(c, r, -0.1f * s, -0.75f), P(c, r, 0.62f * s, 0.0f), P(c, r, -0.1f * s, 0.75f));
      p.Rect(P(c, r, std::min(-0.7f * s, -0.1f * s), -0.32f), P(c, r, std::max(-0.7f * s, -0.1f * s), 0.32f));
      break;
    }
    case Icon::kRadio:
      p.Rect(P(c, r, -0.78f, -0.35f), P(c, r, 0.78f, 0.65f), r * 0.14f);
      p.Line(P(c, r, -0.45f, -0.38f), P(c, r, 0.4f, -0.85f), r * 0.11f);
      if (p.grow == 0) {
        p.d->AddCircleFilled(P(c, r, 0.32f, 0.12f), r * 0.24f, hole, 20);
        for (int i = 0; i < 3; ++i) p.d->AddRectFilled(P(c, r, -0.6f, -0.12f + i * 0.17f), P(c, r, -0.1f, -0.05f + i * 0.17f), hole);
      }
      break;
    case Icon::kHorn: {
      const ImVec2 cone[4] = {P(c, r, -0.35f, -0.25f), P(c, r, 0.08f, -0.65f), P(c, r, 0.08f, 0.65f), P(c, r, -0.35f, 0.25f)};
      p.Rect(P(c, r, -0.7f, -0.25f), P(c, r, -0.32f, 0.25f));
      p.Poly(cone, 4);
      p.Arc(P(c, r, 0.08f, 0), r * 0.4f, -0.78f, 0.78f, r * 0.15f);
      p.Arc(P(c, r, 0.08f, 0), r * 0.68f, -0.78f, 0.78f, r * 0.15f);
      break;
    }
    case Icon::kEye: {
      // Almond: two arcs; iris and pupil.
      p.Arc(P(c, r, 0, 0.55f), r * 0.95f, -2.6f, -0.54f, r * 0.14f);
      p.Arc(P(c, r, 0, -0.55f), r * 0.95f, 0.54f, 2.6f, r * 0.14f);
      p.Disc(c, r * 0.3f);
      if (p.grow == 0) p.d->AddCircleFilled(c, r * 0.13f, hole, 16);
      break;
    }
    case Icon::kHandbrake:
      p.Ring(c, r * 0.5f, r * 0.16f);
      p.Arc(c, r * 0.82f, 2.2f, 4.1f, r * 0.14f);
      p.Arc(c, r * 0.82f, -0.95f, 0.95f, r * 0.14f);
      break;
    case Icon::kGas:
    case Icon::kBrake: {
      const bool wide = icon == Icon::kBrake;
      const float hw = wide ? 0.58f : 0.42f, hh = wide ? 0.62f : 0.85f;
      p.Rect(P(c, r, -hw, -hh), P(c, r, hw, hh), r * 0.18f);
      if (p.grow == 0) {
        const int n = wide ? 4 : 6;
        for (int i = 0; i < n; ++i) {
          const float y = -hh + 0.25f + i * (2 * hh - 0.5f) / (n - 1);
          p.d->AddLine(P(c, r, -hw * 0.6f, y + 0.08f), P(c, r, hw * 0.6f, y - 0.08f), hole, r * 0.09f);
        }
      }
      break;
    }
    case Icon::kUp: p.Tri(P(c, r, 0, -0.6f), P(c, r, 0.6f, 0.42f), P(c, r, -0.6f, 0.42f)); break;
    case Icon::kDown: p.Tri(P(c, r, 0, 0.6f), P(c, r, -0.6f, -0.42f), P(c, r, 0.6f, -0.42f)); break;
    case Icon::kLeft: p.Tri(P(c, r, -0.6f, 0), P(c, r, 0.42f, -0.6f), P(c, r, 0.42f, 0.6f)); break;
    case Icon::kRight: p.Tri(P(c, r, 0.6f, 0), P(c, r, -0.42f, 0.6f), P(c, r, -0.42f, -0.6f)); break;
    case Icon::kCheck: Limb(p, {P(c, r, -0.58f, 0.02f), P(c, r, -0.15f, 0.45f), P(c, r, 0.62f, -0.48f)}, r * 0.26f); break;
    case Icon::kBack:
      p.Arc(P(c, r, 0.1f, 0.13f), r * 0.55f, -1.57f, 2.2f, w * 1.2f);
      p.Tri(P(c, r, -0.78f, -0.42f), P(c, r, -0.08f, -0.85f), P(c, r, -0.08f, 0.0f));
      break;
    case Icon::kHangUp:
      p.Arc(P(c, r, 0, 0.32f), r * 0.75f, 3.5f, 5.93f, r * 0.3f);
      p.Rect(P(c, r, -0.85f, -0.02f), P(c, r, -0.42f, 0.35f), r * 0.1f);
      p.Rect(P(c, r, 0.42f, -0.02f), P(c, r, 0.85f, 0.35f), r * 0.1f);
      break;
    case Icon::kHouse: {
      const ImVec2 roof[3] = {P(c, r, 0.0f, -0.82f), P(c, r, 0.85f, -0.05f), P(c, r, -0.85f, -0.05f)};
      p.Poly(roof, 3);
      p.Rect(P(c, r, -0.58f, -0.1f), P(c, r, 0.58f, 0.75f));
      if (p.grow == 0) p.d->AddRectFilled(P(c, r, -0.16f, 0.25f), P(c, r, 0.16f, 0.75f), hole);
      break;
    }
    case Icon::kGamepad:
      p.Rect(P(c, r, -0.85f, -0.42f), P(c, r, 0.85f, 0.42f), r * 0.4f);
      p.Disc(P(c, r, -0.55f, 0.35f), r * 0.32f);
      p.Disc(P(c, r, 0.55f, 0.35f), r * 0.32f);
      if (p.grow == 0) {
        p.d->AddRectFilled(P(c, r, -0.62f, -0.07f), P(c, r, -0.28f, 0.07f), hole);
        p.d->AddRectFilled(P(c, r, -0.52f, -0.17f), P(c, r, -0.38f, 0.17f), hole);
        p.d->AddCircleFilled(P(c, r, 0.38f, -0.05f), r * 0.09f, hole, 12);
        p.d->AddCircleFilled(P(c, r, 0.58f, -0.18f), r * 0.09f, hole, 12);
        p.d->AddCircleFilled(P(c, r, 0.58f, 0.08f), r * 0.09f, hole, 12);
      }
      break;
    case Icon::kSkip:
      p.Tri(P(c, r, -0.78f, -0.6f), P(c, r, 0.02f, 0.0f), P(c, r, -0.78f, 0.6f));
      p.Tri(P(c, r, -0.08f, -0.6f), P(c, r, 0.72f, 0.0f), P(c, r, -0.08f, 0.6f));
      p.Rect(P(c, r, 0.72f, -0.6f), P(c, r, 0.88f, 0.6f));
      break;
    case Icon::kReset:
      p.Arc(c, r * 0.58f, -0.5f, 4.4f, w);
      p.Tri(P(c, r, 0.62f, -0.6f), P(c, r, 0.75f, 0.0f), P(c, r, 0.2f, -0.25f));
      break;
    default:
      break;
  }
}

void DrawText(ImDrawList* d, ImFont* font, float size, ImVec2 top_center, const char* text, float alpha) {
  const ImVec2 extent = font->CalcTextSizeA(size, FLT_MAX, 0.0f, text);
  const ImVec2 pos(top_center.x - extent.x * 0.5f, top_center.y);
  d->AddText(font, size, pos, Alpha(kWhite, alpha * 0.95f), text);
}

// Borderless discs and neutral glyphs shared with the Xbox theme.
constexpr ImU32 kIconGrey = IM_COL32(194, 194, 194, 225);

void DrawRing(ImDrawList* d, ImVec2 c, float r, float alpha, bool held) {
  d->AddCircleFilled(c, r, Alpha(IM_COL32(18, 18, 18, held ? 150 : 96), alpha), 48);
}

const char* ButtonHint(const Spec& spec) {
  if(spec.kind==Kind::kTrigger)return spec.button==kLT?"LT":"RT";
  if(spec.kind==Kind::kStick)return spec.button==0?"LS":"RS";
  if(spec.kind!=Kind::kButton)return "";
  switch(spec.button){
    case X_INPUT_GAMEPAD_A:return "A";case X_INPUT_GAMEPAD_B:return "B";
    case X_INPUT_GAMEPAD_X:return "X";case X_INPUT_GAMEPAD_Y:return "Y";
    case X_INPUT_GAMEPAD_LEFT_SHOULDER:return "LB";case X_INPUT_GAMEPAD_RIGHT_SHOULDER:return "RB";
    case X_INPUT_GAMEPAD_LEFT_THUMB:return "LS";case X_INPUT_GAMEPAD_RIGHT_THUMB:return "RS";
    case X_INPUT_GAMEPAD_START:return "START";case X_INPUT_GAMEPAD_BACK:return "BACK";
    case X_INPUT_GAMEPAD_DPAD_UP:return "UP";case X_INPUT_GAMEPAD_DPAD_DOWN:return "DOWN";
    case X_INPUT_GAMEPAD_DPAD_LEFT:return "LEFT";case X_INPUT_GAMEPAD_DPAD_RIGHT:return "RIGHT";
    default:return "";
  }
}

void DrawControl(ImDrawList* d, ImFont* font, State& s, size_t i, float unit) {
  const Placed& c = s.placed[i];
  const Spec& spec = *c.spec;
  const float configured_alpha = c.opacity*Opacity()*(std::string_view(spec.id)=="camera"?float(REXCVAR_GET(gta4_touch_camera_opacity)):1.f);
  const float alpha=s.editing?std::max(.65f,configured_alpha):configured_alpha;
  const bool held = i < s.held.size() && s.held[i];
  if(spec.icon==Icon::kAim&&s.aim_hold.locked()){
    const ImVec2 p(c.center.x+c.r*.52f,c.center.y-c.r*.62f);
    const ImU32 amber=Alpha(IM_COL32(223,194,138,255),alpha);
    d->AddRect(ImVec2(p.x-4*unit,p.y-7*unit),ImVec2(p.x+4*unit,p.y+2*unit),amber,3*unit,0,2*unit);
    d->AddRectFilled(ImVec2(p.x-6*unit,p.y-1*unit),ImVec2(p.x+6*unit,p.y+7*unit),amber,2*unit);
  }
  float& feedback=s.press_feedback[i%s.press_feedback.size()];
  feedback+=(float(held)-feedback)*(REXCVAR_GET(gta4_touch_animations)?std::min(1.f,ImGui::GetIO().DeltaTime*18.f):1.f);
  const float visual_scale=1.f-.035f*feedback;
  const ImU32 hole = IM_COL32(0, 0, 0, 0);
  if (spec.kind == Kind::kStick) {
    if(!s.editing&&spec.button==0&&s.placed_move_mode=="invisible")return;
    ImVec2 center=c.center;
    if(!s.editing&&spec.button==0&&s.placed_move_mode!="fixed")
      for(const auto& [key,f]:s.fingers)if(f.control==int(i)){center=f.origin;break;}
    DrawRing(d, center, c.r, alpha * 0.9f, held);
    const ImVec2 v = s.stick[spec.button];
    const ImVec2 knob(center.x + v.x * c.r * 0.55f, center.y + v.y * c.r * 0.55f);
    theme::DrawStickGrip(d, knob, c.r, Alpha(IM_COL32(194, 194, 194, held ? 225 : 170), alpha));
  } else {
    DrawRing(d, c.center, c.r*visual_scale, alpha, held);
    if(REXCVAR_GET(gta4_touch_icon_outline))theme::DrawButtonOutline(d,c.center,c.r*visual_scale,Alpha(kIconGrey,alpha));
    const float ir = c.r * 0.66f*visual_scale;
    const ImU32 tint = Alpha(held ? IM_COL32(255, 255, 255, 240) : kIconGrey, alpha);
    ContextTouchIcon image;
    const char* file = IconFile(spec.icon);
    const bool drew_image =
        file && g_icon_resolver && g_icon_resolver(g_icon_context, file, &image) && image.texture;
    if (drew_image) {
      // Icon PNG: square, about 82% of the button's diameter.
      const float half = c.r * 0.82f*visual_scale;
      d->AddImage(image.texture, ImVec2(c.center.x - half, c.center.y - half),
                  ImVec2(c.center.x + half, c.center.y + half), image.uv_min, image.uv_max, Alpha(IM_COL32(255,255,255,held?255:240),alpha));
    } else {
      const Pen fill{d, tint, 0.0f};
      // Cut-outs (windows, grooves) show the dark disc behind the icon.
      DrawIcon(fill, spec.icon, c.center, ir, Alpha(IM_COL32(20, 20, 22, 120), alpha));
    }
    if (!drew_image &&
        (spec.icon == Icon::kHandbrake || spec.icon == Icon::kLB || spec.icon == Icon::kRB)) {
      const char* text = spec.icon == Icon::kHandbrake ? "P" : spec.icon == Icon::kLB ? "LB" : "RB";
      const float size = ir * (spec.icon == Icon::kHandbrake ? 1.0f : 0.9f);
      const ImVec2 extent = font->CalcTextSizeA(size, FLT_MAX, 0.0f, text);
      d->AddText(font, size, ImVec2(c.center.x - extent.x * 0.5f, c.center.y - extent.y * 0.5f),
                 Alpha(kIconGrey, alpha), text);
    }
  }
  (void)hole;
  float text_y=c.center.y+c.r+4.f;
  if(REXCVAR_GET(gta4_touch_button_hints)&&spec.kind!=Kind::kReset){
    const char* hint=ButtonHint(spec);
    if(*hint){const float size=std::max(8.f,std::min(c.r*.36f,19.f*unit));
      text_y=std::min(text_y,s.placed_height-size*1.2f);
      DrawText(d,font,size,ImVec2(c.center.x,text_y),hint,alpha);text_y+=size*1.2f;}
  }
  if (spec.label && REXCVAR_GET(gta4_touch_labels)) {
    const float size = std::max(11.0f, std::min(c.r * 0.3f, 17.0f * unit * 1.2f));
    DrawText(d, font, size, ImVec2(c.center.x, text_y), spec.label, alpha);
  }
  if (s.editing && spec.kind != Kind::kReset) {
    const auto key=std::string(ScreenName(s.screen))+"/"+spec.id;
    d->AddCircle(c.center, c.r * 1.12f,s.hidden.contains(key)?IM_COL32(255,90,90,220):IM_COL32(246,207,92,220),40,2.5f);
  }
}

void DrawAmmo(ImDrawList* d, ImFont* font, const State& s, float unit) {
  const TouchContextSnapshot context = GetTouchContextSnapshot();
  if (!context.clip_known || !context.armed) return;
  for (const auto& c : s.placed) {
    if (std::string_view(c.spec->id) == "weapon") {
      char text[24];
      std::snprintf(text, sizeof(text), "%u", unsigned(context.clip_ammo));
      DrawText(d, font, 17.0f * unit * 1.2f, ImVec2(c.center.x, c.center.y - c.r - 26.0f * unit), text, 1.0f);
    }
  }
}

}  // namespace

void PublishTouchLook(double x,double y,bool active) noexcept {
  std::lock_guard lock(look_mutex);
  if(!active){look_delta={};return;}
  if(std::isfinite(x)&&std::isfinite(y)) {
    look_delta.x=std::clamp(look_delta.x+x,-2048.0,2048.0);
    look_delta.y=std::clamp(look_delta.y+y,-2048.0,2048.0);
  }
  look_delta.active=true;
}
TouchLookDelta ConsumeTouchLook() noexcept {
  std::lock_guard lock(look_mutex);auto result=look_delta;
  look_delta.x=look_delta.y=0;return result;
}
void RequestLibertyLayoutEditor(int context) noexcept {
  editor_close_request.store(false,std::memory_order_release);
  editor_request.store(context<0?8:std::clamp(context,0,7),std::memory_order_release);
}
void RequestLibertyLayoutEditorClose() noexcept {editor_close_request.store(true,std::memory_order_release);}
void ReloadLibertyTouchLayout() noexcept {
  auto& s=state();std::lock_guard lock(s.mutex);
  s.layout_loaded=false;s.geometry_dirty=true;s.editing=false;s.selected=-1;
  s.offsets.clear();s.sizes.clear();s.opacities.clear();s.hidden.clear();s.fingers.clear();
  s.stick={ImVec2(0,0),ImVec2(0,0)};s.steer=0;s.smooth_move={0,0};
  s.held.assign(s.held.size(),false);PublishTouchLook(0,0,false);PublishInput(s);
}
int LibertyTouchEditorContext() noexcept {
  auto& s=state();std::lock_guard lock(s.mutex);return s.last_editor_context;
}

bool LibertyTouchLayoutActive(std::string_view layout) noexcept { return layout == "liberty"; }

void PublishLibertyPauseMenu(uint32_t pause_flag, uint32_t screen) noexcept {
  g_pause_flag.store(pause_flag, std::memory_order_relaxed);
  g_menu_screen.store(screen, std::memory_order_relaxed);
}

void SetLibertyIconResolver(LibertyIconResolver resolver, void* context) noexcept {
  g_icon_resolver = resolver;
  g_icon_context = context;
}

bool ResolveLibertyIcon(std::string_view id, ContextTouchIcon* icon) {
  return icon && g_icon_resolver && g_icon_resolver(g_icon_context, id, icon) && icon->texture;
}

bool UpdateAndDrawLibertyTouchOverlay(ImDrawList* draw, ImFont* font, float font_size, float width,
                                      float height) noexcept {
  (void)font_size;
  auto& s = state();
  std::lock_guard lock(s.mutex);
  if (!s.layout_loaded) LoadOffsets(s);
  const int edit=editor_request.exchange(-1,std::memory_order_acq_rel);

  constexpr Screen editor_screens[]={Screen::kOnFoot,Screen::kDriving,Screen::kBike,
      Screen::kHelicopter,Screen::kPassenger,Screen::kPhone,Screen::kMenu,Screen::kCutscene};
  if(edit>=0){
    s.editing=true;s.editor_screen=edit;
    if(edit==8){const Screen current=CurrentScreen(false,s.pause_flag_verified,&s.logged_context);s.editor_screen=0;for(int i=0;i<8;++i)if(editor_screens[i]==current)s.editor_screen=i;}
    s.geometry_dirty=true;s.selected=-1;s.forced_menu=!GetTouchContextSnapshot().gameplay_allowed;
    s.save_error.clear();s.start_pulse_until=s.skip_pulse_until={};
  }
  // Keep a close request pending until the open request reaches this thread.
  if(s.editing&&editor_close_request.exchange(false,std::memory_order_acq_rel))FinishEditing(s);
  ImVec2 toolbar_min{-1,-1},toolbar_max{-1,-1};
  if(s.editing&&draw&&font&&std::isfinite(width)&&std::isfinite(height)&&width>0&&height>0) {
    ImGui::SetNextWindowPos(ImVec2(width*0.35f,12),ImGuiCond_Appearing);
    ImGui::SetNextWindowSize(ImVec2(std::min(width*0.62f,640.f),0),ImGuiCond_Appearing);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize,0.f);ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize,0.f);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding,4.f);ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding,3.f);ImGui::PushStyleVar(ImGuiStyleVar_PopupBorderSize,0.f);
    ImGui::PushStyleColor(ImGuiCol_WindowBg,ImVec4(.08f,.08f,.08f,.98f));ImGui::PushStyleColor(ImGuiCol_TitleBgActive,ImVec4(.15f,.15f,.15f,1));
    ImGui::PushStyleColor(ImGuiCol_FrameBg,ImVec4(.19f,.19f,.19f,1));ImGui::PushStyleColor(ImGuiCol_Button,ImVec4(.22f,.22f,.22f,1));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered,ImVec4(.55f,.47f,.32f,1));ImGui::PushStyleColor(ImGuiCol_ButtonActive,ImVec4(.68f,.58f,.40f,1));
    ImGui::PushStyleColor(ImGuiCol_FrameBgHovered,ImVec4(.30f,.29f,.27f,1));ImGui::PushStyleColor(ImGuiCol_FrameBgActive,ImVec4(.36f,.33f,.27f,1));
    ImGui::PushStyleColor(ImGuiCol_PopupBg,ImVec4(.08f,.08f,.08f,.98f));ImGui::PushStyleColor(ImGuiCol_Header,ImVec4(.25f,.23f,.19f,1));
    ImGui::PushStyleColor(ImGuiCol_HeaderHovered,ImVec4(.42f,.37f,.28f,1));ImGui::PushStyleColor(ImGuiCol_HeaderActive,ImVec4(.55f,.47f,.32f,1));
    ImGui::PushStyleColor(ImGuiCol_SliderGrab,ImVec4(.78f,.66f,.45f,1));ImGui::PushStyleColor(ImGuiCol_SliderGrabActive,ImVec4(.87f,.76f,.54f,1));
    ImGui::PushStyleColor(ImGuiCol_CheckMark,ImVec4(.87f,.76f,.54f,1));ImGui::PushStyleColor(ImGuiCol_Text,ImVec4(.957f,.945f,.91f,1));ImGui::PushStyleColor(ImGuiCol_TextDisabled,ImVec4(.67f,.66f,.65f,1));
    ImGui::Begin("Mobile layout editor",nullptr,ImGuiWindowFlags_AlwaysAutoResize);
    if(ImGui::Combo("Context",&s.editor_screen,"On foot\0Car / boat\0Bike\0Helicopter\0Passenger\0Phone\0Menus / map\0Cutscene\0")) {
      s.geometry_dirty=true;s.selected=-1;
    }
    ImGui::TextWrapped("Drag a control to move it. Select one to change its size or visibility. Red outlines are hidden in play.");
    if(s.selected>=0&&s.selected<int(s.placed.size())) {
      const auto& c=s.placed[s.selected];
      if(c.spec->kind!=Kind::kReset) {
        const auto key=std::string(ScreenName(s.screen))+"/"+c.spec->id;
        ImGui::Text("Selected: %s",c.spec->label?c.spec->label:c.spec->id);
        s.offsets.try_emplace(key,ImVec2(0,0));
        auto [size,inserted]=s.sizes.try_emplace(key,1.f);
        if(ImGui::SliderFloat("Size",&size->second,0.35f,4.0f,"%.2fx"))s.geometry_dirty=true;
        bool hidden=s.hidden.contains(key);
        if(ImGui::Checkbox("Hidden in play",&hidden)) {
          if(hidden)s.hidden.insert(key);else s.hidden.erase(key);
        }
      }
    }
    if(!s.save_error.empty())ImGui::TextWrapped("%s",s.save_error.c_str());
    if(ImGui::Button("Save layout",ImVec2(130,36)))FinishEditing(s);
    ImGui::SameLine();
    if(ImGui::Button("Reset context",ImVec2(130,36))) {
      const auto prefix=std::string(ScreenName(editor_screens[s.editor_screen]))+"/";
      std::erase_if(s.offsets,[&](const auto& e){return e.first.starts_with(prefix);});
      std::erase_if(s.sizes,[&](const auto& e){return e.first.starts_with(prefix);});
      std::erase_if(s.hidden,[&](const auto& e){return e.starts_with(prefix);});
      s.geometry_dirty=true;
    }
    toolbar_min=ImGui::GetWindowPos();const auto extent=ImGui::GetWindowSize();
    toolbar_max={toolbar_min.x+extent.x,toolbar_min.y+extent.y};ImGui::End();ImGui::PopStyleColor(17);ImGui::PopStyleVar(5);
  }
  // Leave the forced menu when the game is clearly back in play: a cutscene,
  // loading, or the title reporting the menu closed after it had reported it open.
  {
    const TouchContextSnapshot context = GetTouchContextSnapshot();
    if (s.forced_menu && (context.loading || context.cutscene)) s.forced_menu = false;
    // The pause menu stops gameplay (gameplay_allowed false). Once that has been
    // seen after the Pause tap, the menu has opened; when gameplay stays allowed
    // again for 0.4 s the player is back in the game, however the menu was
    // closed (Back, Resume, ...).
    const auto now_tick = std::chrono::steady_clock::now();
    if (s.forced_menu) {
      if (!context.gameplay_allowed) {
        s.forced_saw_pause = true;
        s.gameplay_since = {};
      } else if (s.forced_saw_pause) {
        if (s.gameplay_since.time_since_epoch().count() == 0) s.gameplay_since = now_tick;
        if (now_tick - s.gameplay_since > std::chrono::milliseconds(400)) s.forced_menu = false;
      }
    }
    if (!s.forced_menu) {
      s.forced_saw_pause = false;
      s.gameplay_since = {};
    }
  }
  Screen screen = rex::input::TouchControlsVisible() && std::isfinite(width) && std::isfinite(height) && width > 0 && height > 0
                            ? (s.editing?editor_screens[s.editor_screen]:CurrentScreen(s.forced_menu, s.pause_flag_verified, &s.logged_context))
                            : Screen::kHidden;
  // Delay only gameplay-to-gameplay transitions. Menus/loading always take effect immediately.
  static TouchContextTransition<Screen> transition;
  const auto transition_now=std::chrono::steady_clock::now();
  const auto gameplay_screen=[](Screen value){return value==Screen::kOnFoot||value==Screen::kDriving||value==Screen::kBike||value==Screen::kHelicopter||value==Screen::kPassenger||value==Screen::kPhone;};
  screen=transition.Update(screen,s.screen,
      std::chrono::duration_cast<std::chrono::milliseconds>(transition_now.time_since_epoch()).count(),
      REXCVAR_GET(gta4_touch_context_delay_ms),!s.editing&&gameplay_screen(screen)&&gameplay_screen(s.screen));
  // The host menu may reveal the game's pause screen behind it. Keep the
  // gameplay layout that was active when the player opened our menu.
  if(screen!=Screen::kHidden&&(s.editing||!TouchHostMenuOpen()))s.last_editor_context=int(screen)-1;
  if (screen != s.screen) {
    s.aim_hold.Reset();
    s.screen = screen;
    s.geometry_dirty=true;
    for (const auto& [key,finger] : s.fingers) s.blocked_fingers.insert(key);
    s.fingers.clear();
    if (screen!=Screen::kCutscene) s.skip_pulse_until={};
    s.skip_was_held=false;
    s.stick = {ImVec2(0, 0), ImVec2(0, 0)};
    s.steer = 0.0f;
  }
  if (screen == Screen::kHidden) {
    s.visible = false;
    s.placed.clear();
    s.held.clear(); s.stick={ImVec2(0,0),ImVec2(0,0)};
    s.start_pulse_until=s.skip_pulse_until={};
    s.pause_was_held=s.resume_was_held=s.switch_was_held=false;
    PublishInput(s);
    PublishTouchLook(0,0,false);
    return false;
  }
  s.visible = true;
  Place(s, width, height);
  const float unit = height / 737.0f;

  // Every finger on the screen.
  std::unordered_map<uint64_t, ImVec2> touches;
  int device_count = 0;
  if (SDL_TouchID* devices = SDL_GetTouchDevices(&device_count)) {
    for (int dev = 0; dev < device_count; ++dev) {
      int finger_count = 0;
      if (SDL_Finger** fingers = SDL_GetTouchFingers(devices[dev], &finger_count)) {
        for (int f = 0; f < finger_count; ++f) {
          const uint64_t key = (uint64_t(devices[dev]) << 32) ^ uint64_t(fingers[f]->id);
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

  for (auto it=s.blocked_fingers.begin();it!=s.blocked_fingers.end();)
    it=touches.contains(*it) ? std::next(it) : s.blocked_fingers.erase(it);
  bool stick_owned[2]={false,false};
  bool stick_taken[2]={false,false};
  for(const auto& [key,f]:s.fingers)if(f.control>=0&&f.control<int(s.placed.size())&&s.placed[f.control].spec->kind==Kind::kStick)
    stick_taken[s.placed[f.control].spec->button]=true;
  s.swipe_active=false;
  uint64_t camera_owner=0; bool have_camera_owner=false;
  for (const auto& [key,finger] : s.fingers) if (finger.camera) { camera_owner=key; have_camera_owner=true; break; }
  bool layout_changed = false;
  s.steer = 0.0f;
  for (const auto& [key, position] : touches) {
    if (s.blocked_fingers.contains(key)) continue;
    auto found = s.fingers.find(key);
    if (found == s.fingers.end()) {
      if(s.editing&&position.x>=toolbar_min.x&&position.x<=toolbar_max.x&&position.y>=toolbar_min.y&&position.y<=toolbar_max.y)continue;
      Finger finger;
      finger.control=HitTest(s,position); finger.last=finger.origin=position;
      if(finger.control<0&&!s.editing&&s.placed_move_mode!="fixed"&&CameraScreen(s.screen)&&
          position.x>=s.placed_safe[0]&&position.x<s.placed_safe[0]+s.placed_safe[2]*0.5f&&
          position.y>=s.placed_safe[1]+s.placed_safe[3]*0.2f) {
        for(size_t i=0;i<s.placed.size();++i)if(s.placed[i].spec->kind==Kind::kStick&&s.placed[i].spec->button==0){finger.control=int(i);break;}
      }
      if(!s.editing&&finger.control>=0&&s.placed[finger.control].spec->kind==Kind::kStick) {
        const auto axis=s.placed[finger.control].spec->button;
        if(stick_taken[axis])finger.control=-2;else stick_taken[axis]=true;
      }
      if (finger.control<0 && !s.editing && SwipeCamera() && CameraScreen(s.screen) && !have_camera_owner &&
          position.x>=s.placed_safe[0]+s.placed_safe[2]*0.5f && position.x<=s.placed_safe[0]+s.placed_safe[2] &&
          position.y>=s.placed_safe[1] && position.y<=s.placed_safe[1]+s.placed_safe[3]) {
        finger.camera=true; camera_owner=key; have_camera_owner=true;
      }
      if (finger.control >= 0) {
        if(s.editing)s.selected=finger.control;
        const auto& c = s.placed[finger.control];
        finger.grab_offset = ImVec2(position.x - c.center.x, position.y - c.center.y);
        if (s.editing && c.spec->kind == Kind::kReset) {
          for (auto it = s.offsets.begin(); it != s.offsets.end();) {
            it = it->first.rfind(std::string(ScreenName(s.screen)) + "/", 0) == 0 ? s.offsets.erase(it)
                                                                                 : std::next(it);
          }
          SaveOffsets(s);
          s.geometry_dirty=true;
          layout_changed = true;
        }
      }
      found = s.fingers.emplace(key, finger).first;
    }
    if (found->second.camera && have_camera_owner && key==camera_owner) {
      const double setting=REXCVAR_GET(gta4_touch_swipe_sensitivity);
      const double gain=720.0/std::max(s.placed_safe[3],1.f)*(std::isfinite(setting)?std::clamp(setting,0.1,8.0):1.0)*0.8;
      PublishTouchLook((position.x-found->second.last.x)*gain,(position.y-found->second.last.y)*gain,true);
      s.stick[1]={0,0};
      found->second.last=position; s.swipe_active=true; stick_owned[1]=true; continue;
    }
    const int index=found->second.control;
    if (index < 0 || index >= int(s.placed.size())) continue;
    const Placed& c = s.placed[index];
    const Spec& spec = *c.spec;

    if (s.editing && spec.kind != Kind::kReset) {
      // Drag to move: store the new centre as an offset in design units.
      const ImVec2 target(position.x - found->second.grab_offset.x, position.y - found->second.grab_offset.y);
      const float dx = (target.x - c.center.x) / unit, dy = (target.y - c.center.y) / unit;
      if (std::fabs(dx) > 0.01f || std::fabs(dy) > 0.01f) {
        ImVec2& offset = s.offsets[std::string(ScreenName(s.screen)) + "/" + spec.id];
        offset.x += spec.anchor == Anchor::kRight ? -dx : dx;
        offset.y += dy;
        s.geometry_dirty=true;
        layout_changed = true;
      }
      continue;
    }
    s.held[index] = true;
    if (spec.kind == Kind::kStick && !stick_owned[spec.button]) {
      stick_owned[spec.button] = true;
      const float range = c.r * 0.85f;
      const auto center=spec.button==0&&s.placed_move_mode!="fixed"?found->second.origin:c.center;
      ImVec2 v((position.x - center.x) / range, (position.y - center.y) / range);
      const float length = std::hypot(v.x, v.y);
      if (length > 1.0f) v = ImVec2(v.x / length, v.y / length);
      s.stick[spec.button] = v;
    } else if (spec.kind == Kind::kSteer) {
      s.steer += spec.button ? 1.0f : -1.0f;
    }
  }
  for (int i = 0; i < 2; ++i)
    if (!stick_owned[i]) s.stick[i] = ImVec2(0, 0);
  if(!s.swipe_active)PublishTouchLook(0,0,false);
  {
    const auto context=GetTouchContextSnapshot();
    const auto now=uint64_t(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count());
    bool down=false,has_aim=false;
    for(size_t i=0;i<s.placed.size();++i)if(s.placed[i].spec->icon==Icon::kAim){has_aim=true;down|=s.held[i];}
    const double configured=REXCVAR_GET(gta4_touch_aim_hold_seconds);
    const auto threshold=uint64_t((std::isfinite(configured)?std::clamp(configured,.2,1.5):.4)*1000);
    const bool allowed=has_aim&&!s.editing&&!TouchHostMenuOpen()&&context.valid&&context.playing&&context.gameplay_allowed&&!context.loading&&!context.cutscene;
    if(REXCVAR_GET(gta4_touch_aim_hold_lock)){
      const bool active=s.aim_hold.Update(down,now,threshold,allowed);
      for(size_t i=0;i<s.placed.size();++i)if(s.placed[i].spec->icon==Icon::kAim)s.held[i]=active;
    }else s.aim_hold.Reset();
  }
  if(stick_owned[0]&&!s.editing) {
    const double tau=REXCVAR_GET(gta4_touch_move_smoothing);
    const float dt=std::clamp(ImGui::GetIO().DeltaTime,1.f/240.f,0.1f);
    const float blend=std::isfinite(tau)&&tau>0?1.f-std::exp(-dt/float(tau)):1.f;
    s.smooth_move.x+=(s.stick[0].x-s.smooth_move.x)*blend;
    s.smooth_move.y+=(s.stick[0].y-s.smooth_move.y)*blend;s.stick[0]=s.smooth_move;
  }else s.smooth_move={0,0};
  if (layout_changed) Place(s, width, height);

  // Hold Pause 1.5 s: toggle edit mode. In edit mode a tap on Pause saves.
  bool pause_held = false;
  for (size_t i = 0; i < s.placed.size(); ++i)
    if (std::string_view(s.placed[i].spec->id) == "pause" && s.held[i]) pause_held = true;
  if(s.editing)pause_held=false;
  const auto now = std::chrono::steady_clock::now();
  if (pause_held && !s.pause_was_held) {
    s.pause_down = now;
    s.pause_long_handled = false;
  }
  if (pause_held && !s.pause_long_handled && !s.editing && now - s.pause_down > std::chrono::milliseconds(1500)) {
#if REX_PLATFORM_ANDROID
    GTA4_AndroidRequestTouchEditor();
#else
    s.editing = true;
#endif
    s.pause_long_handled = true;
    s.fingers.clear();
  } else if (!pause_held && s.pause_was_held && !s.pause_long_handled) {
    if (s.editing) {
      FinishEditing(s);
    } else {
      s.start_pulse_until = now + std::chrono::milliseconds(120);  // short tap: pause the game
      s.forced_menu = true;

    }
  }
  s.pause_was_held = pause_held;
  bool skip_held = false;
  for (size_t i = 0; i < s.placed.size(); ++i)
    if (std::string_view(s.placed[i].spec->id) == "skip" && s.held[i]) skip_held = true;
  if (skip_held && !s.skip_was_held && !s.editing) s.skip_pulse_until = now + std::chrono::milliseconds(150);
  s.skip_was_held = skip_held;
  // RESUME (menu screen): Start closes the pause menu; leave the forced menu.
  bool resume_held = false;
  for (size_t i = 0; i < s.placed.size(); ++i)
    if (std::string_view(s.placed[i].spec->id) == "resume" && s.held[i]) resume_held = true;
  if (!resume_held && s.resume_was_held) s.forced_menu = false;
  s.resume_was_held = resume_held;

  if (s.editing) {  // Pause never reaches the game while editing
    for (size_t i = 0; i < s.placed.size(); ++i)
      if (std::string_view(s.placed[i].spec->id) == "pause") s.held[i] = false;
  }

  PublishInput(s);
  if (draw && font) {
    for (size_t i = 0; i < s.placed.size(); ++i) DrawControl(draw, font, s, i, unit);
    if (!s.editing) DrawAmmo(draw, font, s, unit);
  }
  return true;
}

bool LibertyTouchOverlayVisible() noexcept {
  auto& input=published_input();
  std::lock_guard lock(input.mutex);
  return input.visible;
}

bool ReadLibertyTouchOverlay(uint32_t user,rex::input::X_INPUT_GAMEPAD* output) noexcept {
  if (!output || user!=0) return false;
  auto& input=published_input(); std::lock_guard lock(input.mutex);
  if (!input.visible) return false;
  *output=input.pad;
  const auto now=std::chrono::steady_clock::now();
  uint16_t buttons=output->buttons;
  if (now<input.start_pulse_until) buttons|=X_INPUT_GAMEPAD_START;
  if (now<input.skip_pulse_until) buttons|=X_INPUT_GAMEPAD_A|X_INPUT_GAMEPAD_B|X_INPUT_GAMEPAD_X;
  output->buttons=buttons;
  return true;
}

void ResetLibertyTouchOverlay() noexcept {
  auto& s=state(); std::lock_guard lock(s.mutex);
  s.aim_hold.Reset();
  PublishTouchLook(0,0,false);s.smooth_move={0,0};
  for (const auto& [key,finger]:s.fingers) s.blocked_fingers.insert(key);
  s.fingers.clear();s.held.assign(s.held.size(),false);
  s.stick={ImVec2(0,0),ImVec2(0,0)};s.steer=0;s.swipe_active=false;
  s.start_pulse_until=s.skip_pulse_until={};s.visible=false;
  s.pause_was_held=s.resume_was_held=s.switch_was_held=false;
  if(s.editing) SaveOffsets(s);
  s.editing=false;PublishInput(s);
}

}  // namespace gta4::input

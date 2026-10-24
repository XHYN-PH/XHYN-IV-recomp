#pragma once
// Liberty touch layout (Mali Edition): icon buttons that switch between on
// foot, driving, phone and menu screens, sent to the title as an Xbox 360 pad.
// Selected with gta4_touch_layout=liberty. Hold Pause for 1.5 s to move the
// buttons; positions are saved in liberty_touch_layout.txt.
#include <cstdint>
#include <string_view>

#include <imgui.h>

namespace rex::input {
struct X_INPUT_GAMEPAD;
}

namespace gta4::input {

struct ContextTouchIcon;
// Loads touch-icons/<name>.png (the context overlay's icon cache).
using LibertyIconResolver = bool (*)(void* context, std::string_view id, ContextTouchIcon* icon);
void SetLibertyIconResolver(LibertyIconResolver resolver, void* context) noexcept;
// Shares the existing bounded image cache with the Xbox button theme.
bool ResolveLibertyIcon(std::string_view id, ContextTouchIcon* icon);

// Game thread, every input poll: the pause-menu flag and current menu screen.
void PublishLibertyPauseMenu(uint32_t pause_flag, uint32_t screen) noexcept;

bool LibertyTouchLayoutActive(std::string_view layout) noexcept;
bool UpdateAndDrawLibertyTouchOverlay(ImDrawList* draw, ImFont* font, float font_size, float width,
                                      float height) noexcept;
bool LibertyTouchOverlayVisible() noexcept;
void ResetLibertyTouchOverlay() noexcept;
// Relative displacement is consumed once by the guest input poll. It never
// shares an owner or axis with movement and has no joystick dead-zone floor.
struct TouchLookDelta { double x=0, y=0; bool active=false; };
void PublishTouchLook(double x,double y,bool active) noexcept;
TouchLookDelta ConsumeTouchLook() noexcept;
void RequestLibertyLayoutEditor(int context) noexcept;
void RequestLibertyLayoutEditorClose() noexcept;
void ReloadLibertyTouchLayout() noexcept;
int LibertyTouchEditorContext() noexcept;
bool ReadLibertyTouchOverlay(uint32_t user, rex::input::X_INPUT_GAMEPAD* output) noexcept;

}  // namespace gta4::input

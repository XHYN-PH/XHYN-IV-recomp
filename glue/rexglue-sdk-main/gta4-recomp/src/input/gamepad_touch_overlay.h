#pragma once

#include <cstdint>

#include <imgui.h>

namespace rex::input {
struct X_INPUT_GAMEPAD;
}

namespace gta4::input {

// A fixed on-screen Xbox 360 controller: two sticks, D-pad, A/B/X/Y,
// bumpers, triggers, Back and Start, drawn in the style of the context touch
// controls. It replaces them while gta4_touch_layout is "gamepad": every
// finger is read straight from SDL (multitouch), and the resulting pad state
// reaches the title through the same touch gamepad provider, exactly like a
// physical controller would.

// gta4_touch_layout is "gamepad".
void RefreshGamepadTouchLayout() noexcept;
// Android's native UI requests a change; the rendering thread applies the CVar.
void RequestGamepadTouchLayout(bool xbox) noexcept;
void ReloadGamepadTouchLayout() noexcept;
void SetTouchHostMenu(bool open, bool resume=true) noexcept;
bool TouchHostMenuOpen() noexcept;
bool GamepadTouchLayoutSelected() noexcept;
// Selected and the touch controls are currently shown (touch_controls).
bool GamepadTouchOverlayVisible() noexcept;
// Reads the fingers, updates the pad state and draws the overlay. UI thread,
// once per ImGui frame. Returns whether the overlay was drawn.
bool UpdateAndDrawGamepadTouchOverlay(ImDrawList* draw, ImFont* font, float font_size,
                                      float width, float height) noexcept;
// The current pad state for the touch gamepad provider (user 0 only).
bool ReadGamepadTouchOverlay(uint32_t user, rex::input::X_INPUT_GAMEPAD* output) noexcept;

}  // namespace gta4::input

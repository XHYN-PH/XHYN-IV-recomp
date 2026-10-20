#pragma once
/**
 * @file        rex/ui/surface_android.h
 * @brief       Android ANativeWindow surface for Vulkan presentation.
 */

#include <rex/ui/surface.h>

#include <android/native_window.h>
#include <dlfcn.h>
#include <cstdint>

namespace rex {
namespace ui {

class AndroidNativeWindowSurface final : public Surface {
 public:
  // Holds its own reference: SDL releases the ANativeWindow as soon as the
  // Java surface is destroyed (screen off, home), while the presenter may
  // still be using this surface. A dangling pointer here faulted inside
  // RefBase::incStrong, which the guest-memory SIGSEGV handler then retried
  // forever (black screen after unlocking).
  explicit AndroidNativeWindowSurface(ANativeWindow* window) : window_(window) {
    if (window_) ANativeWindow_acquire(window_);
  }
  ~AndroidNativeWindowSurface() override {
    if (window_) ANativeWindow_release(window_);
  }
  AndroidNativeWindowSurface(const AndroidNativeWindowSurface&) = delete;
  AndroidNativeWindowSurface& operator=(const AndroidNativeWindowSurface&) = delete;
  TypeIndex GetType() const override { return kTypeIndex_AndroidNativeWindow; }
  ANativeWindow* window() const { return window_; }
  // Optional compositor vote. The runtime lookup preserves Android 10 support.
  void SetFrameRateHint(uint32_t fps) const {
    if (!window_ || fps == frame_rate_hint_) return;
    using SetRate = int32_t (*)(ANativeWindow*, float, int8_t);
    static const auto set_rate = [] {
      void* library = dlopen("libnativewindow.so", RTLD_NOW | RTLD_LOCAL);
      return library ? reinterpret_cast<SetRate>(dlsym(library,"ANativeWindow_setFrameRate")) : nullptr;
    }();
    frame_rate_hint_ = fps;
    if (set_rate) set_rate(window_,float(fps),1 /* FIXED_SOURCE */);
  }

 protected:
  bool GetSizeImpl(uint32_t& width_out, uint32_t& height_out) const override {
    if (!window_) return false;
    width_out = static_cast<uint32_t>(ANativeWindow_getWidth(window_));
    height_out = static_cast<uint32_t>(ANativeWindow_getHeight(window_));
    return true;
  }

 private:
  ANativeWindow* window_;
  mutable uint32_t frame_rate_hint_ = UINT32_MAX;
};

}  // namespace ui
}  // namespace rex

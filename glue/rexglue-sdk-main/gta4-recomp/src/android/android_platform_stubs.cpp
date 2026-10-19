/**
 * Android replacements for the services the macOS host implements in
 * Objective-C++ (Game Center, AVFoundation user music, microphone TCC) and
 * for the curl/OpenSSL community multiplayer backend.
 *
 * Every entry point reports "unavailable" so the title takes its existing
 * offline/no-music paths.
 */

#include <cstdint>
#include <filesystem>
#include <functional>
#include <memory>
#include <set>
#include <string>
#include <vector>
#include <mutex>
#include <cstdlib>
#include <jni.h>
#include <SDL3/SDL_system.h>

#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/graphics/gta4_native/anti_aliasing_policy.h>
#include <rex/logging.h>
#include <rex/input/absolute_pointer.h>

#include "achievement_bridge_gc.h"
#include "input/user_music_player.h"
#include "input/gamepad_touch_overlay.h"
#include "input/liberty_touch_overlay.h"
#include "gta4_touch_coordinator.h"
#include "network/community_multiplayer.h"
#include "network/gta4_microphone_permission.h"

REXCVAR_DEFINE_DOUBLE(gta4_phone_rumble_strength,0.5,"GTA IV/Input/Touch",
                      "Phone vibration strength; zero disables game rumble").range(0.0,1.0);

extern "C" JNIEXPORT void JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeSetTouchLayout(JNIEnv*, jclass, jboolean xbox) {
  gta4::input::RequestGamepadTouchLayout(xbox == JNI_TRUE);
}
namespace {
const std::set<std::string_view> mobile_settings={
  "gta4_native_anti_aliasing","gta4_present_mode","gta4_frame_limit","gta4_draw_distance_scale",
  "gta4_native_water_reflection_interval","gta4_native_environment_reflection_interval","gta4_motion_blur",
  "gta4_native_benchmark_trigger","gta4_profile_native_capture_trigger","gta4_shadow_capture_trigger","gta4_trace_shadow_targets",
  "gta4_touch_camera","gta4_touch_move_mode","gta4_touch_gamepad_scale","gta4_touch_gamepad_opacity",
  "gta4_touch_swipe_sensitivity","gta4_touch_move_smoothing","gta4_touch_radar_top",
  "gta4_touch_camera_scale","gta4_touch_camera_opacity","gta4_touch_radar_margin",
  "gta4_touch_button_hints","gta4_touch_labels",
  "gta4_phone_rumble_strength",
  "gta4_native_smaa_quality","gta4_native_shadow_interval","gta4_reflection_capture_distance","gta4_dynamic_draw_distance"};
std::string JavaString(JNIEnv* env,jstring s) {
  if(!s)return {};
  const char* chars=env->GetStringUTFChars(s,nullptr);if(!chars)return {};
  std::string result(chars);env->ReleaseStringUTFChars(s,chars);return result;
}
bool SetMobileSetting(std::string_view key,std::string_view value) {
  if(key=="gta4_native_anti_aliasing")return
      rex::graphics::gta4_native::SetConfiguredAntiAliasingMode(value)!=
      rex::graphics::gta4_native::AntiAliasingApplyResult::kRejected;
  return rex::cvar::SetFlagByName(key,value);
}
void NotifyAndroidFrameLimit(std::string_view value) {
      auto* env=static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
      auto activity=static_cast<jobject>(SDL_GetAndroidActivity());
      if(!env||!activity)return;
      auto type=env->GetObjectClass(activity);
      auto method=type?env->GetMethodID(type,"onNativeFrameLimitChanged","(I)V"):nullptr;
      if(method)env->CallVoidMethod(activity,method,jint(std::strtoul(std::string(value).c_str(),nullptr,10)));
      if(env->ExceptionCheck()){env->ExceptionClear();REXLOG_WARN("Android display refresh notification unavailable");}
      if(type)env->DeleteLocalRef(type);env->DeleteLocalRef(activity);
}

jmethodID phone_rumble_method=nullptr;
bool AndroidPhoneRumble(uint32_t user,uint16_t low,uint16_t high) noexcept {
  if(user!=0||!phone_rumble_method)return false;
  auto* env=static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
  auto activity=static_cast<jobject>(SDL_GetAndroidActivity());
  if(!env||!activity)return false;
  const auto result=env->CallBooleanMethod(activity,phone_rumble_method,jint(low),jint(high),jfloat(REXCVAR_GET(gta4_phone_rumble_strength)));
  const bool failed=env->ExceptionCheck();if(failed)env->ExceptionClear();
  env->DeleteLocalRef(activity);return !failed&&result==JNI_TRUE;
}
void InitializePhoneRumble() {
  auto* env=static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
  auto activity=static_cast<jobject>(SDL_GetAndroidActivity());
  if(!env||!activity)return;
  auto type=env->GetObjectClass(activity);
  auto supported=type?env->GetMethodID(type,"isPhoneRumbleAvailable","()Z"):nullptr;
  if(supported&&env->CallBooleanMethod(activity,supported)==JNI_TRUE)
    phone_rumble_method=env->GetMethodID(type,"onNativePhoneRumble","(IIF)Z");
  if(env->ExceptionCheck()){env->ExceptionClear();phone_rumble_method=nullptr;}
  if(phone_rumble_method)rex::input::SetTouchGamepadRumbleProvider(&AndroidPhoneRumble);
  if(type)env->DeleteLocalRef(type);env->DeleteLocalRef(activity);
}

}
void AndroidRegisterMobileSettingsCallbacks() {
  static std::once_flag once;
  std::call_once(once,[]{
    InitializePhoneRumble();
    rex::cvar::RegisterChangeCallback("gta4_frame_limit",[](std::string_view,std::string_view value){
      NotifyAndroidFrameLimit(value);
    });
  });
  NotifyAndroidFrameLimit(rex::cvar::GetFlagByName("gta4_frame_limit"));
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeGetSetting(JNIEnv* env,jclass,jstring name) {
  const auto key=JavaString(env,name);
  if(key.size()>96)return env->NewStringUTF("");
  return env->NewStringUTF(rex::cvar::GetFlagByName(key).c_str());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeApplySettings(JNIEnv* env,jclass,jobjectArray keys,jobjectArray values) {
  if(!keys||!values)return JNI_FALSE;
  const auto count=env->GetArrayLength(keys);
  if(count>64||count!=env->GetArrayLength(values))return JNI_FALSE;
  std::vector<std::pair<std::string,std::string>> changes;
  for(jsize i=0;i<count;++i) {
    auto k=static_cast<jstring>(env->GetObjectArrayElement(keys,i));auto v=static_cast<jstring>(env->GetObjectArrayElement(values,i));
    auto key=JavaString(env,k),value=JavaString(env,v);env->DeleteLocalRef(k);env->DeleteLocalRef(v);
    if(!mobile_settings.contains(key)||value.size()>64||!rex::cvar::GetFlagInfo(key))return JNI_FALSE;
    changes.emplace_back(std::move(key),std::move(value));
  }
  // Setters retain normal validation and lifecycle enforcement. Roll back
  // earlier edits if a later value is rejected; never save a partial batch.
  std::vector<std::pair<std::string,std::string>> previous;
  for(const auto& [key,value]:changes) {
    previous.emplace_back(key,rex::cvar::GetFlagByName(key));
    if(!SetMobileSetting(key,value)) {
      for(const auto& [name,old]:previous)SetMobileSetting(name,old);
      return JNI_FALSE;
    }
  }
  if(changes.size()==1&&(changes[0].first=="gta4_native_benchmark_trigger"||changes[0].first=="gta4_profile_native_capture_trigger"||changes[0].first=="gta4_shadow_capture_trigger"||changes[0].first=="gta4_trace_shadow_targets"))return JNI_TRUE;
  std::filesystem::path data_root;
  if(const auto* external=SDL_GetAndroidExternalStoragePath();external&&*external)data_root=external;
  if(data_root.empty())data_root=rex::filesystem::GetUserFolder();
  const auto path=data_root/"LibertyRecomp"/"native.toml";
  auto temporary=path;temporary += ".mobile.tmp";
  std::error_code error;
  if(rex::cvar::SaveConfig(temporary)) {
    std::filesystem::rename(temporary,path,error);
    if(!error)return JNI_TRUE;
  }
  std::filesystem::remove(temporary,error);
  for(const auto& [name,old]:previous)SetMobileSetting(name,old);
  return JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeHostMenu(JNIEnv*,jclass,jboolean open) {
  gta4::input::SetTouchHostMenu(open==JNI_TRUE);
  GTA4_SetTouchTitleInputOwned(open==JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeEditLayout(JNIEnv*,jclass,jint context) {
  // Keep the host's existing pause ownership until the user presses Resume.
  // The overlay still draws and receives raw touches; guest pad reads stay neutral.
  gta4::input::SetTouchHostMenu(true);
  GTA4_SetTouchTitleInputOwned(true);
  gta4::input::RequestGamepadTouchLayout(false);
  gta4::input::RequestLibertyLayoutEditor(context);
}
extern "C" JNIEXPORT void JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeFinishTouchEditor(JNIEnv*,jclass) {
  gta4::input::RequestLibertyLayoutEditorClose();
}
extern "C" JNIEXPORT void JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeReloadTouchLayout(JNIEnv*,jclass) {
  gta4::input::ReloadLibertyTouchLayout();
  gta4::input::ReloadGamepadTouchLayout();
}
extern "C" JNIEXPORT jint JNICALL
Java_com_libertyrecomp_LibertyActivity_nativeTouchEditorContext(JNIEnv*,jclass) {
  return gta4::input::LibertyTouchEditorContext();
}
extern "C" void GTA4_AndroidRequestTouchEditor() {
  auto* env=static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());auto activity=static_cast<jobject>(SDL_GetAndroidActivity());
  if(!env||!activity)return;auto type=env->GetObjectClass(activity);
  auto method=type?env->GetMethodID(type,"onNativeTouchEditorRequested","()V"):nullptr;
  if(method)env->CallVoidMethod(activity,method);
  if(env->ExceptionCheck())env->ExceptionClear();
  if(type)env->DeleteLocalRef(type);env->DeleteLocalRef(activity);
}
extern "C" void GTA4_AndroidTouchEditorClosed() {
  auto* env=static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());auto activity=static_cast<jobject>(SDL_GetAndroidActivity());
  if(!env||!activity)return;auto type=env->GetObjectClass(activity);
  auto method=type?env->GetMethodID(type,"onNativeTouchEditorClosed","()V"):nullptr;
  if(method)env->CallVoidMethod(activity,method);
  if(env->ExceptionCheck())env->ExceptionClear();
  if(type)env->DeleteLocalRef(type);env->DeleteLocalRef(activity);
}

namespace gta4::game_center {

void Initialize() {}

void SubmitAchievement(uint32_t) {}

}  // namespace gta4::game_center

namespace gta4::input {

struct UserMusicPlayer::Impl {};

UserMusicPlayer::UserMusicPlayer(std::filesystem::path) : impl_(std::make_unique<Impl>()) {}

UserMusicPlayer::~UserMusicPlayer() = default;

bool UserMusicPlayer::Next() {
  return false;
}

bool UserMusicPlayer::Previous() {
  return false;
}

bool UserMusicPlayer::Stop() {
  return false;
}

bool UserMusicPlayer::HasTracks() const {
  return false;
}

void PublishUserMusicPlayer(UserMusicPlayer*) {}

bool IsUserMusicAvailable() {
  return false;
}

bool RequestUserMusicNext() {
  return false;
}

bool RequestUserMusicPrevious() {
  return false;
}

bool RequestUserMusicStop() {
  return false;
}

}  // namespace gta4::input

namespace gta4::voice {

MicrophonePermissionStatus GetMicrophonePermissionStatus() noexcept {
  return MicrophonePermissionStatus::kDenied;
}

void RequestMicrophonePermission(std::function<void()> completion) {
  if (completion) {
    completion();
  }
}

}  // namespace gta4::voice

namespace LibertyRecomp::Network {

rex::system::xam::LiveBackendServices CreateCommunityMultiplayerBackend(
    const rex::system::xam::LiveConfig&, const rex::system::xam::LiveIdentity&) {
  REXLOG_WARN("Community multiplayer is not available on Android");
  return {};
}

}  // namespace LibertyRecomp::Network

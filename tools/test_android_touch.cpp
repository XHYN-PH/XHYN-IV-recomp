// Runs the production overlays with real ImGui/CVar code and synthetic SDL fingers.
#include <cassert>
#include <cmath>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <thread>
#include <chrono>
#include <vector>
#include <SDL3/SDL_touch.h>
#include <imgui.h>
#include <rex/cvar.h>
#include <rex/input/input.h>
#include <rex/input/input_system.h>
#include <rex/input/absolute_pointer.h>
#include <rex/logging.h>
#include "input/context_touch_context.h"
#include "input/gamepad_touch_overlay.h"
#include "input/liberty_touch_overlay.h"

namespace rex::input::mnk {bool ReadVirtualControllerCompatibilityGamepad(uint32_t,X_INPUT_GAMEPAD&){return false;}}
namespace rex::input {
class FeedbackDriver final:public InputDriver {
 public:
  unsigned writes=0;
  FeedbackDriver():InputDriver(nullptr,0){}
  X_STATUS Setup()override{return X_STATUS_SUCCESS;}
  X_RESULT GetCapabilities(uint32_t,uint32_t,X_INPUT_CAPABILITIES*)override{return X_ERROR_DEVICE_NOT_CONNECTED;}
  X_RESULT GetState(uint32_t,X_INPUT_STATE*)override{return X_ERROR_DEVICE_NOT_CONNECTED;}
  X_RESULT SetState(uint32_t user,X_INPUT_VIBRATION*)override{if(user)return X_ERROR_DEVICE_NOT_CONNECTED;++writes;return X_ERROR_SUCCESS;}
  X_RESULT GetKeystroke(uint32_t,uint32_t,X_INPUT_KEYSTROKE*)override{return X_ERROR_DEVICE_NOT_CONNECTED;}
};
}
static std::vector<SDL_Finger> fingers;
static gta4::input::TouchContextSnapshot context;
static std::filesystem::path user_folder;
static uint16_t rumble_low=0,rumble_high=0;
static bool Rumble(uint32_t user,uint16_t low,uint16_t high)noexcept{if(user)return false;rumble_low=low;rumble_high=high;return true;}

extern "C" SDL_TouchID* SDL_GetTouchDevices(int* count) {
  *count=1;auto* p=static_cast<SDL_TouchID*>(std::malloc(sizeof(SDL_TouchID)));*p=1;return p;
}
extern "C" SDL_Finger** SDL_GetTouchFingers(SDL_TouchID,int* count) {
  *count=int(fingers.size());
  auto** p=static_cast<SDL_Finger**>(std::malloc((fingers.size()+1)*sizeof(SDL_Finger*)));
  for(size_t i=0;i<fingers.size();++i)p[i]=&fingers[i];p[fingers.size()]=nullptr;return p;
}
extern "C" void SDL_free(void* p){std::free(p);}
namespace rex::filesystem {std::filesystem::path GetUserFolder(){return user_folder;}}
namespace rex::diagnostics {bool IsEnabled(Category) noexcept{return false;}}
namespace rex {spdlog::logger* GetLoggerRaw(LogCategoryId){return nullptr;}}
namespace rex {LogCategoryId RegisterLogCategory(const char*){return LogCategoryId(0);}}
namespace gta4::input {TouchContextSnapshot GetTouchContextSnapshot() noexcept{return context;}}

static rex::input::X_INPUT_GAMEPAD Frame(bool draw=true) {
  ImGui::NewFrame();
  gta4::input::UpdateAndDrawGamepadTouchOverlay(draw?ImGui::GetForegroundDrawList():nullptr,
       draw?ImGui::GetFont():nullptr,18,1600,737);
  rex::input::X_INPUT_GAMEPAD pad{};
  rex::input::ReadTouchGamepad(0,&pad);
  ImGui::Render();return pad;
}
static void Touch(float x,float y,int id=1){fingers={{SDL_FingerID(id),x/1600,y/737,1}};}
static void Release(){fingers.clear();Frame();}
static void Mode(gta4::input::ContextTouchMode mode){Release();context.base_mode=mode;Frame();}

int main(int argc,char** argv){
  assert(argc==2);user_folder=argv[1];std::filesystem::create_directories(user_folder);
  ImGui::CreateContext();auto& io=ImGui::GetIO();io.DisplaySize={1600,737};io.DeltaTime=1.0f/60;
  io.IniFilename=nullptr;io.Fonts->AddFontDefault();unsigned char* pixels;int w,h;
  io.Fonts->GetTexDataAsRGBA32(&pixels,&w,&h);
  auto& service=rex::input::GetAbsolutePointerService();
  rex::ui::GuestOutputTransform transform;
  transform.revision=1;transform.surface_width=transform.host_render_target_width=transform.output_width=1600;
  transform.surface_height=transform.host_render_target_height=transform.output_height=737;
  transform.guest_width=1280;transform.guest_height=720;
  service.UpdatePresentation(transform,0,0,1600,737,1);service.SetLogicalSize(1600,737,2);service.SetFocused(true,3);
  assert(rex::cvar::SetFlagByName("touch_controls","on"));
  rex::input::SetTouchGamepadProvider(&gta4::input::ReadGamepadTouchOverlay);
  rex::input::X_INPUT_GAMEPAD boot{};
  assert(rex::input::ReadTouchGamepad(0,&boot));
  assert(uint16_t(boot.buttons)==0 && boot.right_trigger==0);
  assert(!rex::input::ReadTouchGamepad(1,&boot));
  context.valid=context.playing=context.gameplay_allowed=true;
  context.base_mode=gta4::input::ContextTouchMode::kOnFoot;
  assert(rex::cvar::SetFlagByName("gta4_touch_layout","liberty"));
  Frame();
  Touch(1505,385);auto pad=Frame();assert(pad.right_trigger==255);
  Release();pad=Frame();assert(pad.right_trigger==0);
  Touch(900,330);Frame();Touch(901.8425f,330);pad=Frame();
  assert(int16_t(pad.thumb_rx)==0);
  auto look=gta4::input::ConsumeTouchLook();assert(look.active&&look.x>1.4&&look.x<1.5&&look.y==0);
  assert(gta4::input::ConsumeTouchLook().x==0); // no duplicate consumption
  pad=Frame();assert(int16_t(pad.thumb_rx)==0);Release();
  // Walking/running with a separate camera finger never turns from movement.
  fingers={{10,400.f/1600,540.f/737,1},{20,900.f/1600,330.f/737,1}};Frame();gta4::input::ConsumeTouchLook();
  fingers[0].x=470.f/1600;pad=Frame();look=gta4::input::ConsumeTouchLook();
  assert(int16_t(pad.thumb_lx)>0&&look.x==0&&look.y==0);
  fingers[1].x=920.f/1600;pad=Frame();look=gta4::input::ConsumeTouchLook();
  assert(int16_t(pad.thumb_lx)>0&&look.x>15&&look.y==0&&int16_t(pad.thumb_rx)==0);
  // A third finger cannot steal the right-side camera owner.
  fingers.push_back({30,1000.f/1600,330.f/737,1});Frame();gta4::input::ConsumeTouchLook();
  fingers[2].x=1200.f/1600;Frame();look=gta4::input::ConsumeTouchLook();assert(look.x==0);
  Release();assert(!gta4::input::ConsumeTouchLook().active);
  assert(rex::cvar::SetFlagByName("gta4_touch_move_mode","invisible"));
  Touch(220,500);pad=Frame();assert(int16_t(pad.thumb_lx)==0);
  Touch(280,500);pad=Frame();assert(int16_t(pad.thumb_lx)>0);
  Release();pad=Frame();assert(int16_t(pad.thumb_lx)==0);
  assert(rex::cvar::SetFlagByName("gta4_touch_move_mode","floating"));
  Touch(230,500);Frame();Touch(280,500);assert(int16_t(Frame().thumb_lx)>0);Release();
  assert(rex::cvar::SetFlagByName("gta4_touch_move_mode","fixed"));
  // The fire finger held over a context transition must not activate a menu item.
  Touch(1505,385);assert(Frame().right_trigger==255);
  context.frontend=true;pad=Frame();assert(pad.right_trigger==0 && uint16_t(pad.buttons)==0);
  context.frontend=false;pad=Frame();assert(pad.right_trigger==0);Release();
  Mode(gta4::input::ContextTouchMode::kVehicleBike);
  Touch(400,500);pad=Frame();assert(int16_t(pad.thumb_ly)>0);Release();
  Mode(gta4::input::ContextTouchMode::kVehicleHelicopter);
  Touch(1500,575);pad=Frame();assert(pad.right_trigger==255);Release();
  Touch(1205,375);pad=Frame();assert(uint16_t(pad.buttons)&rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER);Release();
  Mode(gta4::input::ContextTouchMode::kVehiclePassenger);
  Touch(1505,385);pad=Frame();assert(pad.right_trigger==0);
  assert(uint16_t(pad.buttons)&rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER);Release();
  context.cutscene=true;Frame();Touch(1460,600);pad=Frame();
  assert(uint16_t(pad.buttons)&rex::input::X_INPUT_GAMEPAD_A);
  context.cutscene=false;pad=Frame();assert(uint16_t(pad.buttons)==0);Release();
  context.loading=true;assert(!gta4::input::UpdateAndDrawGamepadTouchOverlay(nullptr,nullptr,18,1600,737));
  assert(rex::input::ReadTouchGamepad(0,&pad));assert(uint16_t(pad.buttons)==0 && pad.right_trigger==0);
  // Changing layouts while another finger is held must not press the new layout.
  assert(rex::cvar::SetFlagByName("touch_controls","off"));
  gta4::input::RequestGamepadTouchLayout(true);Touch(1505,385);Frame();
  assert(rex::cvar::GetFlagByName("touch_controls")=="on");
  assert(rex::input::ReadTouchGamepad(0,&pad) && pad.right_trigger==0);
  gta4::input::RequestGamepadTouchLayout(false);Frame();Release();
  context.loading=false;Frame();
  // Preview/edit a vehicle layout without having to enter that vehicle.
  gta4::input::RequestLibertyLayoutEditor(1);Frame();
  Touch(1500,575);pad=Frame();assert(pad.right_trigger==0);
  Touch(1430,520);Frame();Release();
  // Android Back asks the live editor to save, rather than silently discarding.
  // If storage fails, remain in editing with guest buttons suppressed.
  std::filesystem::create_directory(user_folder/"liberty_touch_layout.txt.tmp");
  gta4::input::RequestLibertyLayoutEditorClose();Frame();
  Touch(1505,385);pad=Frame();assert(uint16_t(pad.buttons)==0&&pad.right_trigger==0);Release();
  std::filesystem::remove(user_folder/"liberty_touch_layout.txt.tmp");
  gta4::input::RequestLibertyLayoutEditorClose();Frame();
  {std::ifstream layout(user_folder/"liberty_touch_layout.txt");std::string text((std::istreambuf_iterator<char>(layout)),{});assert(text.find("driving/gas")!=std::string::npos);}
  Touch(1505,385);pad=Frame();assert(uint16_t(pad.buttons)&rex::input::X_INPUT_GAMEPAD_LEFT_SHOULDER);Release();
  // Editing over the host pause menu keeps its pause ownership. Returning to
  // that menu does not send an extra Start; Resume sends the closing pulse.
  gta4::input::SetTouchHostMenu(true);context.gameplay_allowed=false;Frame();
  gta4::input::RequestLibertyLayoutEditor(0);Frame();Touch(1505,385);pad=Frame();assert(pad.right_trigger==0);Release();
  gta4::input::RequestLibertyLayoutEditorClose();Frame();gta4::input::SetTouchHostMenu(true);
  gta4::input::SetTouchHostMenu(false);pad=Frame();assert(uint16_t(pad.buttons)&rex::input::X_INPUT_GAMEPAD_START);
  std::this_thread::sleep_for(std::chrono::milliseconds(180));context.gameplay_allowed=true;Release();
  // Reject malformed stored tuning and retain a usable, finite drawing.
  assert(rex::cvar::SetFlagByName("gta4_touch_gamepad_scale","nan"));
  assert(rex::cvar::SetFlagByName("gta4_touch_gamepad_opacity","nan"));
  transform.revision=2;service.UpdatePresentation(transform,60,24,1480,680,4);
  Frame();
  assert(rex::cvar::SetFlagByName("gta4_touch_layout","gamepad"));Frame();
  assert(rex::cvar::SetFlagByName("gta4_touch_gamepad_scale","1"));
  std::filesystem::create_directories(user_folder/"LibertyRecomp");
  {std::ofstream layout(user_folder/"LibertyRecomp/liberty_touch_layout.txt");layout<<"xbox/a -350 -120 1 0\n";}
  gta4::input::ReloadGamepadTouchLayout();Frame();
  Touch(866.76f,541.089f);assert(uint16_t(Frame().buttons)&rex::input::X_INPUT_GAMEPAD_A);Release();
  Touch(1216.76f,661.089f);assert(!(uint16_t(Frame().buttons)&rex::input::X_INPUT_GAMEPAD_A));Release();
  {std::ofstream layout(user_folder/"LibertyRecomp/liberty_touch_layout.txt");layout<<"xbox/a -350 -120 1 1\n";}
  gta4::input::ReloadGamepadTouchLayout();Frame();
  Touch(866.76f,541.089f);assert(!(uint16_t(Frame().buttons)&rex::input::X_INPUT_GAMEPAD_A));Release();
  rex::input::SetTouchGamepadRumbleProvider(&Rumble);
  assert(rex::input::TouchGamepadRumbleAvailable(0)&&!rex::input::TouchGamepadRumbleAvailable(1));
  assert(rex::input::WriteTouchGamepadRumble(0,32000,65535)&&rumble_low==32000&&rumble_high==65535);
  rex::input::SetTouchInputActiveCallback([]{return false;});
  assert(!rex::input::WriteTouchGamepadRumble(0,65535,65535)&&rumble_low==0&&rumble_high==0);
  rex::input::SetTouchInputActiveCallback({});
  assert(rex::input::WriteTouchGamepadRumble(0,65535,0));
  rex::input::SetTouchGamepadRumbleProvider(nullptr);assert(!rumble_low&&!rumble_high);
  {
    rex::input::SetTouchGamepadRumbleProvider(&Rumble);
    using rex::X_RESULT;
    rex::input::InputSystem router(nullptr);
    rex::input::X_INPUT_CAPABILITIES caps{};
    assert(router.GetCapabilities(0,0,&caps)==X_ERROR_SUCCESS);
    assert(uint16_t(caps.flags)&rex::input::X_INPUT_CAPS_FFB_SUPPORTED);
    assert(uint16_t(caps.vibration.left_motor_speed)==65535&&uint16_t(caps.vibration.right_motor_speed)==65535);
    rex::input::X_INPUT_VIBRATION motors{};motors.left_motor_speed=12345;motors.right_motor_speed=23456;
    assert(router.SetState(0,&motors)==X_ERROR_SUCCESS&&rumble_low==12345&&rumble_high==23456);
    assert(router.SetState(0,nullptr)==X_ERROR_BAD_ARGUMENTS);
    auto physical=std::make_unique<rex::input::FeedbackDriver>();auto* output=physical.get();router.AddDriver(std::move(physical));
    assert(router.SetState(0,&motors)==X_ERROR_SUCCESS&&output->writes==1&&rumble_low==0&&rumble_high==0);
    router.Shutdown();assert(!rex::input::TouchGamepadRumbleAvailable(0));
  }
  assert(rex::cvar::SetFlagByName("gta4_touch_layout","context"));Frame();
  assert(!gta4::input::ReadGamepadTouchOverlay(0,&pad));
  service.SetFocused(false,5);Frame();assert(!rex::input::ReadTouchGamepad(0,&pad));
  service.SetFocused(true,6);assert(rex::cvar::SetFlagByName("gta4_touch_layout","liberty"));Frame();
  rex::input::SetTouchInputActiveCallback([]{return false;});Frame();
  pad.buttons=0xFFFF;assert(rex::input::ReadTouchGamepad(0,&pad));assert(uint16_t(pad.buttons)==0);
  rex::input::SetTouchInputActiveCallback({});rex::input::SetTouchGamepadProvider(nullptr);
  ImGui::DestroyContext();std::cout<<"Production touch overlay regression checks passed\n";
}

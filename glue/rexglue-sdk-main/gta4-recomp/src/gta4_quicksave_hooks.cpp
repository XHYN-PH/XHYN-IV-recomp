#include "gta4_quicksave_hooks.h"
#include "input/context_touch_activity.h"
#include "input/context_touch_context.h"
#include "gta4_quicksave_policy.h"
#include "gta4_cheat_policy.h"
#include "gta4_phone_quicksave_profiles.h"

#include <array>
#include <atomic>
#include <chrono>
#include <cstring>
#include <bit>
#include <cmath>
#include <fstream>
#include <limits>
#include <mutex>
#include <optional>
#include <string>
#include <utility>

#include <rex/crypto/sha256.h>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/runtime.h>
#include <rex/system/function_dispatcher.h>
#include <rex/system/kernel_state.h>
#include <rex/system/xam/user_profile.h>
#include "gta4_init.h"

REXCVAR_DEFINE_BOOL(gta4_quicksave, true, "GTA IV/Gameplay",
                    "Add Quicksave to the single-player phone in GTA IV and both episodes")
    .lifecycle(rex::cvar::Lifecycle::kRequiresRestart);
REXCVAR_DEFINE_BOOL(gta4_trace_quicksave, false, "GTA IV/Diagnostics",
                    "Log bounded Quicksave menu and save transaction observations");
REXCVAR_DEFINE_BOOL(gta4_cheat_achievements,false,"GTA IV/Gameplay",
                    "Allow local achievement award events while using cheats; no Xbox Live sync");
REXCVAR_DEFINE_DOUBLE(gta4_completion_percent,-1.0,"GTA IV/Status","Last observed live completion percentage; -1 means unavailable").range(-1.0,100.0);
REXCVAR_DEFINE_INT32(gta4_completion_episode,-1,"GTA IV/Status","Episode of the observed progress; not a save-slot identifier").range(-1,2);
REXCVAR_DEFINE_STRING(gta4_cheat_status,"Ready","GTA IV/Status","Last requested cheat result");

namespace gta4::quicksave {
namespace {
// All addresses below come from generated PPC and the existing script-aware input hooks.
constexpr uint32_t kEpisode = 0x82B39504, kExecutingThread = 0x8319277C, kGlobals = 0x831927B4;
constexpr uint32_t kPlayerIndex = 0x82A98778, kPlayerTable = 0x82C01C70;
constexpr uint32_t kPhoneCreated = 0x831D4DD4, kFrontendRequest = 0x82BFA13C,
                   kFrontendVisible = 0x82BFA144, kSaveSucceeded = 0x82BF984D;
constexpr uint32_t kPedOffset = 1400, kVehicleFlags = 572, kInVehicle = 0x20000000;
constexpr uint32_t kThreadProgram = 8, kThreadLocals = 80, kThreadState = 12, kThreadName = 92;
constexpr uint32_t kMaxSCO = 1024 * 1024;
std::mutex state_mutex, native_mutex, label_mutex;
std::atomic<uint64_t> world_epoch{1};
Transaction transaction;
uint64_t next_poll = 0;
std::array<bool, 3> installed{};
std::array<uint32_t, 3> program_addresses{};
std::atomic<uint32_t> menu_trace_count{0};
thread_local bool inside_poll = false;
uint32_t request_thunk = 0, observe_thunk = 0;

bool Span(uint8_t* base, uint32_t address, size_t size, bool write = false) {
  if (!base || !address || !size || uint64_t(address) + size > uint64_t(UINT32_MAX) + 1)
    return false;
  auto* kernel = REX_KERNEL_STATE();
  auto* memory = kernel ? kernel->memory() : nullptr;
  auto* heap = memory ? memory->LookupHeap(address) : nullptr;
  const uint32_t last = uint32_t(uint64_t(address) + size - 1);
  if (!heap || heap != memory->LookupHeap(last))
    return false;
  const auto access = heap->QueryRangeAccess(address, last);
  using rex::memory::PageAccess;
  return access == PageAccess::kReadWrite || access == PageAccess::kExecuteReadWrite ||
         (!write && (access == PageAccess::kReadOnly || access == PageAccess::kExecuteReadOnly));
}
uint32_t Read(uint8_t* base, uint32_t address) {
  uint32_t v;
  std::memcpy(&v, rex::memory::GuestPtr(base, address), sizeof(v));
  return __builtin_bswap32(v);
}
void Write(uint8_t* base, uint32_t address, uint32_t v) {
  v = __builtin_bswap32(v);
  std::memcpy(rex::memory::GuestPtr(base, address), &v, sizeof(v));
}
uint8_t Byte(uint8_t* base, uint32_t address) {
  return *rex::memory::GuestPtr(base, address);
}
std::string String(uint8_t* base, uint32_t address, size_t cap = 96) {
  std::string out;
  for (size_t i = 0; i < cap; ++i) {
    if (uint64_t(address) + i > UINT32_MAX || !Span(base, address + uint32_t(i), 1))
      return {};
    const char c = char(Byte(base, address + uint32_t(i)));
    if (!c)
      return out;
    out.push_back(c);
  }
  return {};
}
std::string ProgramName(uint8_t* base, uint32_t address) {
  auto name = String(base, address, 256);
  const auto slash = name.find_last_of("/\\");
  if (slash != std::string::npos)
    name.erase(0, slash + 1);
  for (char& c : name)
    if (c >= 'A' && c <= 'Z')
      c = char(c - 'A' + 'a');
  if (name.ends_with(".sco"))
    name.resize(name.size() - 4);
  return name;
}
uint64_t Now() {
  return uint64_t(std::chrono::duration_cast<std::chrono::milliseconds>(
                      std::chrono::steady_clock::now().time_since_epoch())
                      .count());
}
struct NativeEntry {
  std::string_view name;
  uint32_t handler = 0;
};
std::array natives = {NativeEntry{"GET_PLAYER_ID"},
                      NativeEntry{"IS_PLAYER_PLAYING"},
                      NativeEntry{"IS_PLAYER_CONTROL_ON"},
                      NativeEntry{"GET_PLAYER_CHAR"},
                      NativeEntry{"IS_CHAR_DEAD"},
                      NativeEntry{"IS_PED_RAGDOLL"},
                      NativeEntry{"IS_CHAR_IN_AIR"},
                      NativeEntry{"GET_MISSION_FLAG"},
                      NativeEntry{"IS_MINIGAME_IN_PROGRESS"},
                      NativeEntry{"IS_MEMORY_CARD_IN_USE"},
                      NativeEntry{"IS_AUTO_SAVE_IN_PROGRESS"},
                      NativeEntry{"IS_SYSTEM_UI_SHOWING"},
                      NativeEntry{"IS_MOBILE_PHONE_CALL_ONGOING"},
                      NativeEntry{"IS_SCREEN_FADED_IN"},
                      NativeEntry{"IS_NETWORK_GAME_RUNNING"},
                      NativeEntry{"NETWORK_IS_SESSION_STARTED"},
                      NativeEntry{"IS_WANTED_LEVEL_GREATER"},
                      NativeEntry{"PRINT_HELP"},NativeEntry{"ACTIVATE_CHEAT"},
                      NativeEntry{"GET_PROGRESS_PERCENTAGE"},NativeEntry{"GET_CURRENT_EPISODE"},NativeEntry{"SET_CHAR_HEALTH"},NativeEntry{"GET_CHAR_HEALTH"},NativeEntry{"GET_CHAR_ARMOUR"},NativeEntry{"ADD_ARMOUR_TO_CHAR"},NativeEntry{"GET_CAR_CHAR_IS_USING"},NativeEntry{"FIX_CAR"},NativeEntry{"GET_CAR_HEALTH"},NativeEntry{"ADD_SCORE"},NativeEntry{"SET_PLAYER_INVINCIBLE"},NativeEntry{"FORWARD_TO_TIME_OF_DAY"}};
uint32_t original_award=0,award_thunk=0;
std::mutex cheat_mutex;
std::optional<gta4::cheats::Request> pending_cheat;
uint64_t cheat_serial=0;
std::atomic<bool> has_pending_cheat{false};
thread_local bool inside_cheat=false;
uint32_t NativeAddress(std::string_view name) {
  std::lock_guard lock(native_mutex);
  for (const auto& n : natives)
    if (n.name == name)
      return n.handler;
  return 0;
}
// Use a separate PPC stack frame and the game's real native context layout.
// Existing VM argument arrays and return storage are never reused or overwritten.
std::optional<uint32_t> Invoke(PPCContext& parent, uint8_t* base, std::string_view name,
                               std::initializer_list<uint32_t> values = {},
                               int output_argument = -1) {
  const uint32_t address = NativeAddress(name);
  auto* runtime = rex::Runtime::instance();
  auto* dispatcher = runtime ? runtime->function_dispatcher() : nullptr;
  auto* function = dispatcher && address ? dispatcher->GetFunction(address) : nullptr;
  if (!function || values.size() > 8 || parent.r1.u32 < 0x200)
    return std::nullopt;
  const uint32_t frame = (parent.r1.u32 - 0x200u) & ~15u;
  if (!Span(base, frame, 0x200, true))
    return std::nullopt;
  const uint32_t context = frame + 0x80, result = frame + 0xC0, arguments = frame + 0xD0;
  Write(base, frame, parent.r1.u32);
  Write(base, context, result);
  Write(base, context + 4, uint32_t(values.size()));
  Write(base, context + 8, arguments);
  Write(base, context + 12, 0);
  Write(base, result, 0);
  size_t i = 0;
  for (const auto value : values) {
    Write(base, arguments + uint32_t(i) * 4, int(i) == output_argument ? result : value);
    ++i;
  }
  PPCContext call = parent;
  call.r1.u32 = frame;
  call.r3.u32 = context;
  call.lr = 0x82844BA0;
  function(call, base);
  return Read(base, result);
}
void AwardLocalAchievement(PPCContext& ctx,uint8_t* base){
  // Intercept the award event, not the game's cheat statistics or save flags.
  // When disabled, run the exact registered original handler.
  if(REXCVAR_GET(gta4_cheat_achievements)){
    auto* runtime=rex::Runtime::instance();auto* kernel=runtime?runtime->kernel_state():nullptr;
    const auto network=Invoke(ctx,base,"IS_NETWORK_GAME_RUNNING");
    const auto session=Invoke(ctx,base,"NETWORK_IS_SESSION_STARTED");
    if(kernel&&network&&session&&!*network&&!*session&&Span(base,ctx.r3.u32,12)&&Read(base,ctx.r3.u32+4)>=1){
      const auto args=Read(base,ctx.r3.u32+8),result=Read(base,ctx.r3.u32);
      if(Span(base,args,4)&&Span(base,result,4,true)){
        const auto id=Read(base,args);
        if(kernel->achievements().FindAchievement(id)){
          kernel->UnlockAchievement(id);Write(base,result,1);return;
        }
      }
    }
  }
  auto* runtime=rex::Runtime::instance();auto* dispatcher=runtime?runtime->function_dispatcher():nullptr;
  if(auto* original=dispatcher&&original_award?dispatcher->GetFunction(original_award):nullptr)original(ctx,base);
}
struct Text {
  std::string_view key, value;
  uint32_t address = 0, key_address = 0;
};
std::array texts = {Text{"LR_QSAVE", "Quicksave"},
                    Text{"LR_QOFF", "Quicksave is disabled."},
                    Text{"LR_QNET", "Quicksave is only available in single-player."},
                    Text{"LR_QMISS", "You cannot save during a mission or mission replay."},
                    Text{"LR_QACT", "Finish the current activity before saving."},
                    Text{"LR_QCAR", "Exit the vehicle to save your game."},
                    Text{"LR_QBUSY", "Another save or menu is active. Try again in a moment."},
                    Text{"LR_QCALL", "Finish the phone call before saving."},
                    Text{"LR_QWANT", "Lose your wanted level before saving."},
                    Text{"LR_QWAIT", "Quicksave was cancelled because the game state changed."},
                    Text{"LR_QERR", "Quicksave is unavailable right now. Try again in free roam."}};
rex::memory::Memory* text_memory = nullptr;
uint32_t text_pool = 0;
bool EnsureText(uint8_t* base) {
  auto* kernel = REX_KERNEL_STATE();
  auto* memory = kernel ? kernel->memory() : nullptr;
  if (!memory)
    return false;
  std::lock_guard lock(label_mutex);
  if (text_pool && text_memory == memory && Span(base, text_pool, 1))
    return true;
  size_t bytes = 0;
  for (const auto& text : texts)
    bytes += text.key.size() + text.value.size() + 2;
  const uint32_t allocation = memory->SystemHeapAlloc(uint32_t(bytes));
  if (!allocation)
    return false;
  uint32_t offset = allocation;
  for (auto& text : texts) {
    text.key_address = offset;
    std::memcpy(rex::memory::GuestPtr(base, offset), text.key.data(), text.key.size());
    offset += uint32_t(text.key.size());
    *rex::memory::GuestPtr(base, offset++) = 0;
    text.address = offset;
    std::memcpy(rex::memory::GuestPtr(base, offset), text.value.data(), text.value.size());
    offset += uint32_t(text.value.size());
    *rex::memory::GuestPtr(base, offset++) = 0;
  }
  text_memory = memory;
  text_pool = allocation;
  return true;
}
void Notify(PPCContext& parent, uint8_t* base, Denial denial) {
  REXLOG_INFO("gta4-quicksave: unavailable reason={}", ReasonKey(denial));
  if (!EnsureText(base))
    return;
  uint32_t key = 0;
  {
    std::lock_guard lock(label_mutex);
    for (const auto& t : texts)
      if (t.key == ReasonKey(denial)) {
        key = t.key_address;
        break;
      }
  }
  if (key)
    Invoke(parent, base, "PRINT_HELP", {key});
}
Session CurrentSession(uint8_t* base) {
  Session result{.epoch = world_epoch.load(std::memory_order_relaxed),
                 .episode = UINT32_MAX,
                 .player = UINT32_MAX};
  auto* kernel = REX_KERNEL_STATE();
  auto* user = kernel ? kernel->user_profile() : nullptr;
  if (user && user->signin_state() != 0)
    result.xuid = user->xuid();
  if (Span(base, kEpisode, 4))
    result.episode = Read(base, kEpisode);
  if (!Span(base, kPlayerIndex, 4))
    return result;
  result.player = Read(base, kPlayerIndex);
  if (result.player >= 4)
    return result;
  const uint32_t slot = kPlayerTable + result.player * 4;
  if (!Span(base, slot, 4))
    return result;
  const uint32_t info = Read(base, slot);
  if (uint64_t(info) + kPedOffset + 4 > UINT32_MAX || !Span(base, info, kPedOffset + 4))
    return result;
  result.ped = Read(base, info + kPedOffset);
  return result;
}
Denial Eligibility(PPCContext& parent, uint8_t* base, const Session& session,
                   bool check_busy = true) {
  Conditions conditions;
  conditions.enabled = REXCVAR_GET(gta4_quicksave);
  {
    std::lock_guard lock(state_mutex);
    conditions.known_program = session.episode < installed.size() && installed[session.episode];
  }
  conditions.signed_in = session.xuid != 0;
  conditions.player_ready =
      session.player < 4 && session.ped && Span(base, session.ped, kVehicleFlags + 4);
  if (!conditions.enabled || !conditions.known_program || !conditions.player_ready ||
      !conditions.signed_in)
    return CanSave(conditions);
  conditions.in_vehicle = (Read(base, session.ped + kVehicleFlags) & kInVehicle) != 0;
  auto playing = Invoke(parent, base, "IS_PLAYER_PLAYING", {session.player});
  auto control = Invoke(parent, base, "IS_PLAYER_CONTROL_ON", {session.player});
  auto mission = Invoke(parent, base, "GET_MISSION_FLAG");
  auto minigame = Invoke(parent, base, "IS_MINIGAME_IN_PROGRESS");
  auto network = Invoke(parent, base, "IS_NETWORK_GAME_RUNNING");
  auto online = Invoke(parent, base, "NETWORK_IS_SESSION_STARTED");
  auto system_ui = Invoke(parent, base, "IS_SYSTEM_UI_SHOWING");
  auto phone_call = Invoke(parent, base, "IS_MOBILE_PHONE_CALL_ONGOING");
  auto visible = Invoke(parent, base, "IS_SCREEN_FADED_IN");
  auto wanted = Invoke(parent, base, "IS_WANTED_LEVEL_GREATER", {session.player, 0});
  auto storage = Invoke(parent, base, "IS_MEMORY_CARD_IN_USE");
  auto autosave = Invoke(parent, base, "IS_AUTO_SAVE_IN_PROGRESS");
  if (!playing || !control || !mission || !minigame || !network || !online || !system_ui ||
      !phone_call || !visible || !wanted || !storage || !autosave)
    return Denial::kUnavailable;
  conditions.player_ready = *playing != 0;
  conditions.multiplayer = *network || *online;
  conditions.mission = *mission != 0;
  conditions.activity = *minigame != 0;
  conditions.phone_call = *phone_call != 0;
  conditions.wanted = *wanted != 0;
  conditions.transitioning = !*control || !*visible;
  conditions.busy = *storage || *autosave || *system_ui;
  if (check_busy)
    conditions.busy |= (Read(base, kFrontendRequest) != 0 || Byte(base, kFrontendVisible) != 0);
  const Denial preliminary = CanSave(conditions);
  if (preliminary != Denial::kNone)
    return preliminary;
  const auto ped_handle = Invoke(parent, base, "GET_PLAYER_CHAR", {session.player, 0}, 1);
  if (!ped_handle || !*ped_handle)
    return Denial::kUnavailable;
  for (const auto name : {"IS_CHAR_DEAD", "IS_PED_RAGDOLL", "IS_CHAR_IN_AIR"}) {
    const auto value = Invoke(parent, base, name, {*ped_handle});
    if (!value)
      return Denial::kUnavailable;
    if (*value)
      return Denial::kActivity;
  }
  return Denial::kNone;
}
const PhoneProfile* CallerProfile(PPCContext& ctx, uint8_t* base) {
  if (!Span(base, ctx.r3.u32, 16) || !Span(base, kExecutingThread, 4))
    return nullptr;
  const uint32_t arguments = Read(base, ctx.r3.u32 + 8);
  if (Read(base, ctx.r3.u32 + 4) != 1 || !Span(base, arguments, 4))
    return nullptr;
  const uint32_t episode = Read(base, arguments);
  if (episode >= kPhoneProfiles.size())
    return nullptr;
  const uint32_t thread = Read(base, kExecutingThread);
  if (!Span(base, thread, 116) || Read(base, thread + kThreadProgram) != kPhoneProgramKey ||
      ProgramName(base, thread + kThreadName) != "spcellphonemain")
    return nullptr;
  if (!Span(base, kEpisode, 4) || Read(base, kEpisode) != episode)
    return nullptr;
  std::lock_guard lock(state_mutex);
  return installed[episode] ? &kPhoneProfiles[episode] : nullptr;
}
void StoreResult(PPCContext& ctx, uint8_t* base, uint32_t value) {
  if (!Span(base, ctx.r3.u32, 4))
    return;
  const uint32_t out = Read(base, ctx.r3.u32);
  if (Span(base, out, 4, true))
    Write(base, out, value);
}
void NativeRequest(PPCContext& ctx, uint8_t* base) {
  const auto* profile = CallerProfile(ctx, base);
  StoreResult(ctx, base, 0);
  if (!profile)
    return;
  const uint32_t globals = Read(base, kGlobals), thread = Read(base, kExecutingThread),
                 locals = Read(base, thread + kThreadLocals);
  const uint32_t global_offset = profile->phone_global * 4,
                 selected_offset = profile->menu_local * 4 + 60;
  if (!Span(base, globals, global_offset + 4) || !Span(base, locals, selected_offset + 4) ||
      Read(base, globals + global_offset) != kRootPhoneState ||
      Read(base, locals + selected_offset) != uint32_t(kQuicksaveAction))
    return;
  bool busy;
  {
    std::lock_guard lock(state_mutex);
    busy = transaction.phase() != Phase::kIdle;
  }
  if (busy) {
    Notify(ctx, base, Denial::kBusy);
    return;
  }
  const Session session = CurrentSession(base);
  const auto denial = Eligibility(ctx, base, session);
  if (denial != Denial::kNone) {
    Notify(ctx, base, denial);
    return;
  }
  bool accepted;
  {
    std::lock_guard lock(state_mutex);
    accepted = transaction.Begin(session, Now());
    next_poll = 0;
  }
  StoreResult(ctx, base, accepted ? 1 : 0);
  if (accepted)
    REXLOG_INFO(
        "gta4-quicksave: request accepted episode={} phone-thread={:08X}; awaiting stock phone "
        "cleanup",
        profile->name, thread);
}
void NativeObserve(PPCContext& ctx, uint8_t* base) {
  const auto* profile = CallerProfile(ctx, base);
  if (!profile)
    return;
  const uint32_t thread = Read(base, kExecutingThread), locals = Read(base, thread + kThreadLocals);
  const uint32_t controller = profile->menu_local * 4;
  if (!Span(base, locals, (profile->options_local + 1 + kOptionCapacity * kOptionWords) * 4))
    return;
  const uint32_t count = Read(base, locals + controller + 4);
  if (count > kOptionCapacity) {
    REXLOG_ERROR("gta4-quicksave: phone option count invalid: {}", count);
    return;
  }
  if (REXCVAR_GET(gta4_trace_quicksave) && menu_trace_count.fetch_add(1) < 32)
    REXLOG_INFO("gta4-quicksave: phone menu built episode={} entries={} label=Quicksave action={}",
                profile->name, count, kQuicksaveAction);
}
bool EnsureThunks() {
  std::lock_guard lock(state_mutex);
  auto* runtime = rex::Runtime::instance();
  auto* dispatcher = runtime ? runtime->function_dispatcher() : nullptr;
  if (!dispatcher)
    return false;
  if (!request_thunk)
    request_thunk = dispatcher->AllocateThunk(NativeRequest, 0x82846780);
  if (!observe_thunk)
    observe_thunk = dispatcher->AllocateThunk(NativeObserve, 0x82846780);
  return request_thunk && observe_thunk;
}
void ResetWorld() {
  rex::cvar::SetFlagByName("gta4_completion_percent","-1");rex::cvar::SetFlagByName("gta4_completion_episode","-1");
  gta4::input::ResetTouchActivityPrograms();
  std::lock_guard lock(state_mutex);
  world_epoch.fetch_add(1);
  transaction.Reset();
  installed = {};
  program_addresses = {};
  next_poll = 0;
}
void Poll(PPCContext& parent, uint8_t* base) {
  if (inside_poll)
    return;
  const uint64_t now = Now();
  Phase phase;
  {
    std::lock_guard lock(state_mutex);
    phase = transaction.phase();
    if (phase == Phase::kIdle || now < next_poll)
      return;
    next_poll = now + 16;
  }
  struct Scope {
    Scope() { inside_poll = true; }
    ~Scope() { inside_poll = false; }
  } scope;
  Observation observation;
  observation.session = CurrentSession(base);
  observation.milliseconds = now;
  if (observation.session.episode >= kPhoneProfiles.size()) {
    std::lock_guard lock(state_mutex);
    transaction.Reset();
    return;
  }
  if (!Span(base, kGlobals, 4) || !Span(base, kPhoneCreated, 1) ||
      !Span(base, kFrontendRequest, 4) || !Span(base, kFrontendVisible, 1) ||
      !Span(base, kSaveSucceeded, 1))
    return;
  const auto& profile = kPhoneProfiles[observation.session.episode];
  const uint32_t globals = Read(base, kGlobals);
  observation.phone_closed = !Byte(base, kPhoneCreated) &&
                             Span(base, globals, profile.phone_global * 4 + 4) &&
                             Read(base, globals + profile.phone_global * 4) == kClosedPhoneState;
  observation.frontend_requested = Read(base, kFrontendRequest) != 0;
  observation.frontend_visible = Byte(base, kFrontendVisible) != 0;
  const auto storage = Invoke(parent, base, "IS_MEMORY_CARD_IN_USE");
  observation.storage_busy = !storage || *storage;
  observation.save_succeeded = Byte(base, kSaveSucceeded) != 0;
  if (phase == Phase::kClosingPhone)
    observation.eligibility = Eligibility(parent, base, observation.session);
  Decision action;
  {
    std::lock_guard lock(state_mutex);
    action = transaction.Poll(observation);
  }
  if (action == Decision::kOpenSave) {
    // Keep the original native's on-foot preparation and its result reset. No
    // apartment time advance, ped teleport, vehicle-bit spoofing or direct I/O.
    PPCContext call = parent;
    sub_825D28A8(call, base);
    if (Read(base, kFrontendRequest) != 11) {
      {
        std::lock_guard lock(state_mutex);
        transaction.Reset();
      }
      Notify(parent, base, Denial::kUnavailable);
      return;
    }
    REXLOG_INFO("gta4-quicksave: stock save screen requested episode={} request=11", profile.name);
  } else if (action == Decision::kSaved || action == Decision::kCancelled) {
    REXLOG_INFO("gta4-quicksave: transaction finished result={} episode={}",
                action == Decision::kSaved ? "saved" : "cancelled", profile.name);
  } else if (action == Decision::kAbandoned) {
    REXLOG_INFO("gta4-quicksave: pending request cancelled (state change or timeout)");
    if (phase == Phase::kClosingPhone)
      Notify(
          parent, base,
          observation.eligibility == Denial::kNone ? Denial::kTransition : observation.eligibility);
  }
}
struct LoadFrame {
  bool phone = false;
  const PhoneProfile* profile = nullptr;
  uint32_t linked_source = 0;
  std::string activity_name;
  std::optional<size_t> activity_profile;
  uint32_t activity_source = 0;
};
thread_local LoadFrame* loading = nullptr;
}  // namespace

void ObserveNativeRegistration(PPCContext& context, uint8_t* base) {
  const auto name = String(base, context.r3.u32);
  if (name.empty())
    return;
  if(name=="AWARD_ACHIEVEMENT"){
    if(context.r4.u32!=award_thunk)original_award=context.r4.u32;
    auto* runtime=rex::Runtime::instance();auto* dispatcher=runtime?runtime->function_dispatcher():nullptr;
    if(dispatcher&&!award_thunk)award_thunk=dispatcher->AllocateThunk(&AwardLocalAchievement,uint32_t(context.lr));
    if(award_thunk)context.r4.u32=award_thunk;
  }
  std::lock_guard lock(native_mutex);
  for (auto& entry : natives)
    if (entry.name == name) {
      entry.handler = context.r4.u32;
      return;
    }
}
bool QueueCheat(uint32_t index){
  // Base-game native cheat indices, 5 is the nonfunctional money placeholder.
  const auto reject=[&](const char* reason){rex::cvar::SetFlagByName("gta4_cheat_status",reason);REXLOG_WARN("gta4-cheats: index={} rejected={}",index,reason);return false;};
  if(!gta4::cheats::SupportedIndex(index))return reject("Unavailable: unsupported cheat");
  if(!NativeAddress(gta4::cheats::Handler(index)))return reject("Unavailable: this game action is not registered yet");
  const auto context=gta4::input::GetTouchContextSnapshot();
  if(!gta4::cheats::CanQueue(context))return reject("Unavailable: return to active single-player gameplay");
  std::lock_guard lock(cheat_mutex);if(pending_cheat)return reject("Pending: another cheat is waiting for Resume");
  pending_cheat=gta4::cheats::Request{index,world_epoch.load(),Now()+10000,context.player_identity,context.player_generation,++cheat_serial};
  has_pending_cheat.store(true,std::memory_order_release);
  rex::cvar::SetFlagByName("gta4_cheat_status","Queued: waiting for gameplay to resume");
  REXLOG_INFO("gta4-cheats: request={} action=\"{}\" index={} queued world={} frame={} player={:08X}",pending_cheat->serial,gta4::cheats::Name(index),index,pending_cheat->world,context.epoch,context.player_identity);return true;
}
std::string CheatStatus(){return rex::cvar::GetFlagByName("gta4_cheat_status");}
void PollCompletion(PPCContext& parent,uint8_t* base){
  static uint64_t next=0,last_write=0;const auto now=Now();if(now<next||inside_cheat)return;next=now+2000;
  const auto context=gta4::input::GetTouchContextSnapshot();if(context.loading||context.cutscene||!context.player_identity)return;
  const auto raw=Invoke(parent,base,"GET_PROGRESS_PERCENTAGE"),episode=Invoke(parent,base,"GET_CURRENT_EPISODE");if(!raw||!episode||*episode>2)return;
  const float percent=std::bit_cast<float>(*raw);if(!std::isfinite(percent)||percent<0||percent>100)return;
  rex::cvar::SetFlagByName("gta4_completion_percent",std::to_string(percent));rex::cvar::SetFlagByName("gta4_completion_episode",std::to_string(*episode));
  if(now-last_write<30000)return;last_write=now;auto* runtime=rex::Runtime::instance();if(!runtime||runtime->user_data_root().empty())return;
  const auto path=runtime->user_data_root()/"last-session-completion.json";auto temporary=path;temporary += ".tmp";
  std::ofstream file(temporary,std::ios::trunc);file<<"{\"percent\":"<<percent<<",\"episode\":"<<*episode<<",\"scope\":\"last observed session; slot not identified\"}\n";file.flush();if(file){file.close();std::error_code error;std::filesystem::rename(temporary,path,error);}
}
void PollCheats(PPCContext& parent,uint8_t* base){
  PollCompletion(parent,base);
  if(inside_cheat||!has_pending_cheat.load(std::memory_order_acquire))return;
  std::optional<gta4::cheats::Request> request;
  {
    std::lock_guard lock(cheat_mutex);if(!pending_cheat)return;
    const auto context=gta4::input::GetTouchContextSnapshot();
    const auto decision=gta4::cheats::Decide(*pending_cheat,context,Now(),world_epoch.load());
    if(decision==gta4::cheats::Decision::kCancel){
      REXLOG_WARN("gta4-cheats: request={} index={} cancelled expired={} world_changed={} loading={} cutscene={}",pending_cheat->serial,pending_cheat->index,Now()>=pending_cheat->expires,pending_cheat->world!=world_epoch.load(),context.loading,context.cutscene);
      pending_cheat.reset();has_pending_cheat.store(false,std::memory_order_release);rex::cvar::SetFlagByName("gta4_cheat_status","Cancelled: timed out or gameplay state changed");return;
    }
    if(decision!=gta4::cheats::Decision::kExecute)return;
    request=pending_cheat;pending_cheat.reset();has_pending_cheat.store(false,std::memory_order_release);
  }
  struct Guard{Guard(){inside_cheat=true;}~Guard(){inside_cheat=false;}}guard;
  const auto network=Invoke(parent,base,"IS_NETWORK_GAME_RUNNING"),session=Invoke(parent,base,"NETWORK_IS_SESSION_STARTED");
  const auto episode=Invoke(parent,base,"GET_CURRENT_EPISODE");
  if(!gta4::cheats::BaseSinglePlayer(network,session,episode)){
    rex::cvar::SetFlagByName("gta4_cheat_status","Unavailable: base GTA IV single-player is required");
    REXLOG_WARN("gta4-cheats: index={} rejected single-player check network={} session={} episode={}",request->index,network.value_or(UINT32_MAX),session.value_or(UINT32_MAX),episode.value_or(UINT32_MAX));return;
  }
  const auto index=request->index;bool applied=false,verified=false;
  if(index<16)applied=Invoke(parent,base,"ACTIVATE_CHEAT",{index}).has_value();
  else {
    const auto mission=Invoke(parent,base,"GET_MISSION_FLAG");
    const auto player=Invoke(parent,base,"GET_PLAYER_ID");
    const auto ped=player?Invoke(parent,base,"GET_PLAYER_CHAR",{*player,0},1):std::nullopt;
    if(!mission||(*mission&&index!=105)||!player||!ped||!*ped){rex::cvar::SetFlagByName("gta4_cheat_status","Unavailable: extra actions require active free roam");REXLOG_WARN("gta4-cheats: request={} action=\"{}\" rejected=free-roam-required",request->serial,gta4::cheats::Name(index));return;}
    switch(index){
      case 100:applied=Invoke(parent,base,"SET_CHAR_HEALTH",{*ped,200}).has_value();if(auto value=Invoke(parent,base,"GET_CHAR_HEALTH",{*ped,0},1))verified=applied&&*value>=200;break;
      case 101:applied=Invoke(parent,base,"ADD_ARMOUR_TO_CHAR",{*ped,100}).has_value();if(auto value=Invoke(parent,base,"GET_CHAR_ARMOUR",{*ped,0},1))verified=applied&&*value>=100;break;
      case 102:{const auto vehicle=Invoke(parent,base,"GET_CAR_CHAR_IS_USING",{*ped,0},1);if(!vehicle||!*vehicle){rex::cvar::SetFlagByName("gta4_cheat_status","Unavailable: enter a vehicle before repairing it");REXLOG_WARN("gta4-cheats: request={} action=\"{}\" rejected=no-current-vehicle",request->serial,gta4::cheats::Name(index));return;}applied=Invoke(parent,base,"FIX_CAR",{*vehicle}).has_value();break;}
      case 103:applied=Invoke(parent,base,"ADD_SCORE",{*player,10000}).has_value();break;
      case 104:case 105:applied=Invoke(parent,base,"SET_PLAYER_INVINCIBLE",{*player,index==104?1u:0u}).has_value();break;
      default:{constexpr uint32_t hours[]={6,12,18,0};applied=Invoke(parent,base,"FORWARD_TO_TIME_OF_DAY",{hours[index-106],0}).has_value();break;}
    }
  }
  rex::cvar::SetFlagByName("gta4_cheat_status",std::string(verified?"Value confirmed: ":applied?"Submitted to game: ":"Failed: ")+std::string(gta4::cheats::Name(index))+(applied&&!verified?" · check the effect":""));
  REXLOG_INFO("gta4-cheats: request={} action=\"{}\" index={} native_called={} handler={:08X} effect_verified={}",request->serial,gta4::cheats::Name(index),index,applied,NativeAddress(gta4::cheats::Handler(index)),verified);
}
bool ResolveText(PPCContext& context, uint8_t* base) {
  const auto key = String(base, context.r4.u32, 16);
  if (!key.starts_with("LR_Q"))
    return false;
  bool known = false;
  for (const auto& text : texts)
    if (text.key == key) {
      known = true;
      break;
    }
  if (!known || !EnsureText(base))
    return false;
  std::lock_guard lock(label_mutex);
  for (const auto& text : texts)
    if (text.key == key) {
      context.r3.u32 = text.address;
      return true;
    }
  return false;
}
}  // namespace gta4::quicksave

extern "C" void sub_82846AE8(PPCContext& ctx, uint8_t* base) {
  using namespace gta4::quicksave;
  const auto name = ProgramName(base, ctx.r4.u32);
  LoadFrame frame{.phone = name == "spcellphonemain"};
  for (const auto& profile : gta4::input::kTouchActivityProfiles)
    if (profile.name == name) { frame.activity_name = name; break; }
  struct Restore {
    LoadFrame* previous;
    ~Restore() { loading = previous; }
  } restore{loading};
  loading = &frame;
  __imp__sub_82846AE8(ctx, base);
}
extern "C" void sub_828453F8(PPCContext& ctx, uint8_t* base) {
  using namespace gta4::quicksave;
  if (loading && !loading->activity_name.empty() && ctx.r4.u32 <= kMaxSCO &&
      Span(base, ctx.r3.u32, ctx.r4.u32)) {
    const std::string_view data(
        reinterpret_cast<const char*>(rex::memory::GuestPtr(base, ctx.r3.u32)), ctx.r4.u32);
    const auto digest = rex::crypto::sha256(data);
    loading->activity_profile = gta4::input::MatchTouchActivityProfile(
        loading->activity_name, ctx.r4.u32, digest);
    loading->activity_source = ctx.r3.u32;
    if (!loading->activity_profile)
      REXLOG_WARN("gta4-touch-activity: unknown script={} size={} sha256={}; generic controls retained",
                  loading->activity_name, ctx.r4.u32, digest);
  }
  if (loading && loading->phone && REXCVAR_GET(gta4_quicksave) && ctx.r4.u32 <= kMaxSCO &&
      Span(base, ctx.r3.u32, ctx.r4.u32)) {
    const std::string_view data(
        reinterpret_cast<const char*>(rex::memory::GuestPtr(base, ctx.r3.u32)), ctx.r4.u32);
    const auto digest = rex::crypto::sha256(data);
    loading->profile = MatchPhoneProfile(ctx.r4.u32, digest);
    loading->linked_source = ctx.r3.u32;
    if (!loading->profile)
      REXLOG_WARN("gta4-quicksave: unsupported phone script; unchanged size={} sha256={}",
                  ctx.r4.u32, digest);
  }
  __imp__sub_828453F8(ctx, base);
  if (loading && ctx.r3.u32 == 0) {
    loading->profile = nullptr;
    loading->activity_profile.reset();
  }
}
extern "C" void sub_82846780(PPCContext& ctx, uint8_t* base) {
  using namespace gta4::quicksave;
  const auto name = ProgramName(base, ctx.r4.u32);
  if (name == "main" || name == "initial")
    ResetWorld();
  if (loading && !loading->activity_name.empty() && loading->activity_name == name) {
    using namespace gta4::input;
    InvalidateTouchActivityProgram(name);
    const auto index = loading->activity_profile;
    const auto* activity = index ? &kTouchActivityProfiles[*index] : nullptr;
    const bool valid = activity && ctx.r5.u32 == loading->activity_source &&
        ctx.r6.u32 == activity->code_size && ctx.r8.u32 == activity->local_count && ctx.r10.u32 == 0 &&
        Span(base, ctx.r5.u32, ctx.r6.u32);
    __imp__sub_82846780(ctx, base);
    if (valid && Span(base, ctx.r3.u32, 28) && Read(base, ctx.r3.u32 + 4) == activity->program_key &&
        Read(base, ctx.r3.u32 + 16) == activity->code_size) {
      PublishTouchActivityProgram(*index, ctx.r3.u32);
      REXLOG_INFO("gta4-touch-activity: admitted script={} episode={} program={:08X} sha256={}",
                  activity->name, activity->episode, ctx.r3.u32, activity->sha256);
    }
    return;
  }
  const auto* profile = loading ? loading->profile : nullptr;
  if (!profile || name != "spcellphonemain" || ctx.r5.u32 != loading->linked_source ||
      ctx.r6.u32 != profile->code_size || ctx.r8.u32 != profile->local_count || ctx.r10.u32 != 0 ||
      !Span(base, ctx.r5.u32, ctx.r6.u32) || !EnsureThunks()) {
    __imp__sub_82846780(ctx, base);
    return;
  }
  auto* kernel = REX_KERNEL_STATE();
  auto* memory = kernel ? kernel->memory() : nullptr;
  if (!memory) {
    __imp__sub_82846780(ctx, base);
    return;
  }
  uint32_t temporary = 0;
  PhonePatch patch;
  try {
    patch = BuildPhonePatch({rex::memory::GuestPtr(base, ctx.r5.u32), ctx.r6.u32}, *profile,
                            request_thunk, observe_thunk);
    temporary = memory->SystemHeapAlloc(uint32_t(patch.code.size()));
  } catch (const std::exception& error) {
    REXLOG_ERROR("gta4-quicksave: phone extension rejected; original retained: {}", error.what());
  }
  if (!temporary) {
    __imp__sub_82846780(ctx, base);
    return;
  }
  std::memcpy(rex::memory::GuestPtr(base, temporary), patch.code.data(), patch.code.size());
  ctx.r5.u32 = temporary;
  ctx.r6.u32 = uint32_t(patch.code.size());
  // Original constructor owns its own code copy; the temporary is never retained.
  __imp__sub_82846780(ctx, base);
  memory->SystemHeapFree(temporary);
  if (Span(base, ctx.r3.u32, 28) && Read(base, ctx.r3.u32 + 4) == kPhoneProgramKey &&
      Read(base, ctx.r3.u32 + 16) == patch.code.size()) {
    {
      std::lock_guard lock(state_mutex);
      installed[profile->episode] = true;
      program_addresses[profile->episode] = ctx.r3.u32;
    }
    REXLOG_INFO(
        "gta4-quicksave: installed episode={} program={:08X} code={}->{} original-sha256={}",
        profile->name, ctx.r3.u32, profile->code_size, patch.code.size(), profile->sha256);
  } else
    REXLOG_ERROR("gta4-quicksave: program publication validation failed");
}
extern "C" void sub_825E87C8(PPCContext& ctx, uint8_t* base) {
  __imp__sub_825E87C8(ctx, base);
  gta4::quicksave::PollCheats(ctx,base);
  gta4::quicksave::Poll(ctx, base);
}
extern "C" void sub_82243260(PPCContext& ctx, uint8_t* base) {
  __imp__sub_82243260(ctx, base);
  gta4::quicksave::PollCheats(ctx,base);
  gta4::quicksave::Poll(ctx, base);
}

#pragma once

#include <cstdint>
#include <optional>
#include <string_view>

#include "input/context_touch_context.h"

namespace gta4::cheats {

struct Request {
  uint32_t index;
  uint64_t world, expires;
  uint32_t player_identity = 0, player_generation = 0;
  uint64_t serial=0;
};

enum class Decision { kWait, kCancel, kExecute };

constexpr bool SupportedIndex(uint32_t index) noexcept {
  // Retail base-game indices. Index 5 is a nonfunctional money placeholder.
  return (index <= 15 && index != 5) || (index>=100&&index<=109);
}

constexpr std::string_view Name(uint32_t index) noexcept {
 constexpr std::string_view retail[]={"Weapons set one","Weapons set two","Health, armour and ammunition","Increase wanted level","Clear wanted level","Unsupported money placeholder","Change weather","Spawn Annihilator","Spawn NRG 900","Spawn FIB Buffalo","Spawn Jetmax","Spawn Comet","Spawn Turismo","Spawn Cognoscenti","Spawn Super GT","Spawn Sanchez"};
 constexpr std::string_view extra[]={"Restore health","Add armour","Repair current vehicle","Add $10,000","Invincibility on","Invincibility off","Advance to morning","Advance to noon","Advance to evening","Advance to midnight"};
 return index<16?retail[index]:index>=100&&index<=109?extra[index-100]:"Unsupported action";
}
constexpr std::string_view Handler(uint32_t index) noexcept {
 if(index<16)return "ACTIVATE_CHEAT";
 switch(index){case 100:return "SET_CHAR_HEALTH";case 101:return "ADD_ARMOUR_TO_CHAR";case 102:return "FIX_CAR";case 103:return "ADD_SCORE";case 104:case 105:return "SET_PLAYER_INVINCIBLE";case 106:case 107:case 108:case 109:return "FORWARD_TO_TIME_OF_DAY";default:return {};}
}

inline bool CanQueue(const input::TouchContextSnapshot& context) noexcept {
  // The host menu pauses gameplay controls; execution waits until it closes.
  return context.valid && context.playing && !context.loading &&
         !context.frontend && !context.cutscene;
}

inline Decision Decide(const Request& request,
                       const input::TouchContextSnapshot& context,
                       uint64_t now, uint64_t world) noexcept {
  if (!SupportedIndex(request.index) || now >= request.expires ||
      world != request.world || context.loading || context.cutscene || context.frontend ||
      (context.valid && request.player_identity &&
       (context.player_identity != request.player_identity ||
        context.player_generation != request.player_generation)))
    return Decision::kCancel;
  // context.epoch advances every input capture. It is a frame counter, not a
  // world identity, and must never invalidate a request waiting for Resume.
  return CanQueue(context) && context.gameplay_allowed ? Decision::kExecute
                                                     : Decision::kWait;
}

inline bool BaseSinglePlayer(std::optional<uint32_t> network,
                             std::optional<uint32_t> session,
                             std::optional<uint32_t> episode) noexcept {
  return network && session && episode && *network == 0 && *session == 0 &&
         *episode == 0;
}

}  // namespace gta4::cheats

#pragma once
#include <cstdint>
#include <string>
struct PPCContext;
namespace gta4::quicksave {
// Called by the single existing native registrar / text resolver owners.
void ObserveNativeRegistration(PPCContext& context, uint8_t* base);
bool ResolveText(PPCContext& context, uint8_t* base);
bool QueueCheat(uint32_t index);
std::string CheatStatus();
void PollCheats(PPCContext& context,uint8_t* base);
}  // namespace gta4::quicksave

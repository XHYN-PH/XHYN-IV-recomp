#pragma once
// Rewrites XenosRecomp's guest constant bank reads from buffer-device-address
// loads into uniform-buffer loads.
//
// The recompiled shaders read float4 registers as
//   RawBufferLoad<float4>(push.VertexShaderConstants + offset)
// (bank 1 for pixel shaders). On Turnip those loads become dozens of global
// loads in each draw's preamble, which runs before the draw's waves. Reading
// the same registers from a dynamic uniform buffer lets the driver's command
// processor stream them into the constant file ahead of the draw instead.
//
// Only loads of whole float4 registers through `bank_base + offset` are
// rewritten; anything else keeps its original BDA load, which stays valid
// because the push constants still carry the bank addresses.
//
// A register index computed at run time (relative addressing, `c[a0 + n]`) is
// clamped to the block: a stray a0 (a NaN shadow-cascade constant, say) read
// outside the uniform buffer and hung the GPU (kgsl "GPU hang detected" in
// the directional shadow pass), where the console reads a wrong register.
#include <cstdint>
#include <optional>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace rex::graphics::gta4_native {

struct SpirvUboConstantsResult {
  std::vector<uint32_t> words;
  uint32_t rewritten_loads = 0;
  uint32_t clamped_loads = 0;  // run-time register indices clamped to the block
};

// bank: 0 vertex constants, 1 pixel constants. register_count sizes the
// uniform block (256 vertex, 224 pixel registers).
inline std::optional<SpirvUboConstantsResult> RewriteSpirvConstantBankToUbo(
    const std::vector<uint32_t>& in, uint32_t bank, uint32_t register_count, uint32_t set,
    uint32_t binding) {
  constexpr uint16_t kOpName = 5, kOpMemberName = 6, kOpEntryPoint = 15, kOpTypeInt = 21,
                     kOpTypeFloat = 22, kOpTypeVector = 23, kOpTypePointer = 32, kOpConstant = 43,
                     kOpFunction = 54, kOpVariable = 59, kOpLoad = 61, kOpAccessChain = 65,
                     kOpInBoundsAccessChain = 66, kOpDecorate = 71, kOpMemberDecorate = 72,
                     kOpConvertUToPtr = 120, kOpUConvert = 113, kOpBitcast = 124, kOpIAdd = 128,
                     kOpTypeArray = 28, kOpTypeStruct = 30, kOpShiftRightLogical = 194,
                     kOpTypeBool = 20, kOpULessThan = 176, kOpSelect = 169;
  constexpr uint32_t kStoragePushConstant = 9, kStorageUniform = 2,
                     kStoragePhysicalStorageBuffer = 5349;
  constexpr uint32_t kDecorationBlock = 2, kDecorationArrayStride = 6, kDecorationOffset = 35,
                     kDecorationBinding = 33, kDecorationDescriptorSet = 34;
  if (in.size() < 5 || in[0] != 0x07230203u) return std::nullopt;

  // Pass 1: identify types, the bank base value and bank addresses.
  uint32_t bound = in[3];
  uint32_t push_variable = 0, uint_type = 0, ulong_type = 0, float_type = 0, vec4_type = 0,
           bool_type = 0;
  std::unordered_map<uint32_t, uint64_t> constants;      // id -> value
  std::unordered_map<uint32_t, uint32_t> constant_types; // id -> type
  std::unordered_set<uint32_t> bank_pointers;            // access chain -> member `bank`
  std::unordered_set<uint32_t> bank_bases;               // loaded bank address
  struct Address {
    uint32_t offset_id;  // 64-bit offset value
    uint32_t offset32;   // 32-bit source when the offset is a UConvert, else 0
  };
  std::unordered_map<uint32_t, Address> addresses;
  std::unordered_set<uint32_t> psb_vec4_pointer_types;
  std::unordered_map<uint32_t, uint32_t> uconvert_sources;  // ulong id -> uint id
  std::unordered_map<uint32_t, Address> bank_pointer_values;  // pointer id -> address
  for (size_t i = 5; i < in.size();) {
    const uint32_t count = in[i] >> 16;
    const uint16_t op = uint16_t(in[i] & 0xFFFF);
    if (!count || i + count > in.size()) return std::nullopt;
    const uint32_t* w = in.data() + i;
    switch (op) {
      case kOpTypeInt:
        if (w[2] == 32 && w[3] == 0) uint_type = w[1];
        if (w[2] == 64 && w[3] == 0) ulong_type = w[1];
        break;
      case kOpTypeFloat:
        if (w[2] == 32) float_type = w[1];
        break;
      case kOpTypeBool:
        bool_type = w[1];
        break;
      case kOpTypeVector:
        if (w[2] == float_type && w[3] == 4 && float_type) vec4_type = w[1];
        break;
      case kOpTypePointer:
        if (w[2] == kStoragePhysicalStorageBuffer && w[3] == vec4_type && vec4_type)
          psb_vec4_pointer_types.insert(w[1]);
        break;
      case kOpConstant:
        constant_types[w[2]] = w[1];
        constants[w[2]] = count >= 5 ? (uint64_t(w[4]) << 32 | w[3]) : w[3];
        break;
      case kOpVariable:
        if (count >= 4 && w[3] == kStoragePushConstant) push_variable = w[2];
        break;
      case kOpAccessChain:
      case kOpInBoundsAccessChain:
        if (count == 5 && w[3] == push_variable && push_variable) {
          const auto member = constants.find(w[4]);
          if (member != constants.end() && member->second == bank) bank_pointers.insert(w[2]);
        }
        break;
      case kOpLoad:
        if (count >= 4 && bank_pointers.count(w[3])) bank_bases.insert(w[2]);
        break;
      case kOpUConvert:
        if (w[1] == ulong_type && ulong_type) uconvert_sources[w[2]] = w[3];
        break;
      case kOpIAdd:
        if (w[1] == ulong_type && ulong_type) {
          if (bank_bases.count(w[3]) && !bank_bases.count(w[4])) addresses[w[2]] = {w[4], 0};
          else if (bank_bases.count(w[4]) && !bank_bases.count(w[3])) addresses[w[2]] = {w[3], 0};
        }
        break;
      case kOpBitcast:
      case kOpConvertUToPtr:
        if (psb_vec4_pointer_types.count(w[1])) {
          const auto address = addresses.find(w[3]);
          if (address != addresses.end()) bank_pointer_values[w[2]] = address->second;
        }
        break;
      default:
        break;
    }
    i += count;
  }
  if (!uint_type || !vec4_type || bank_pointer_values.empty()) return std::nullopt;

  // New ids.
  const uint32_t uint_zero = bound++, uint_four = bound++, uint_length = bound++,
                 uint_last = bound++;
  const uint32_t array_type = bound++, struct_type = bound++, struct_pointer = bound++,
                 element_pointer = bound++, variable = bound++;

  SpirvUboConstantsResult result;
  std::vector<uint32_t>& out = result.words;
  out.reserve(in.size() + in.size() / 8 + 64);
  out.insert(out.end(), in.begin(), in.begin() + 5);
  auto emit = [&](uint16_t op, std::initializer_list<uint32_t> operands) {
    out.push_back(uint32_t(operands.size() + 1) << 16 | op);
    out.insert(out.end(), operands);
  };
  bool decorations_emitted = false, globals_emitted = false;
  for (size_t i = 5; i < in.size();) {
    const uint32_t count = in[i] >> 16;
    const uint16_t op = uint16_t(in[i] & 0xFFFF);
    const uint32_t* w = in.data() + i;
    const bool type_or_global = (op >= kOpTypeBool && op <= 39) || (op >= 41 && op <= 52) ||
                                (op == kOpVariable && !globals_emitted && w[3] != 7);
    if (!decorations_emitted && (op == kOpDecorate || op == kOpMemberDecorate || type_or_global)) {
      emit(kOpDecorate, {array_type, kDecorationArrayStride, 16});
      emit(kOpMemberDecorate, {struct_type, 0, kDecorationOffset, 0});
      emit(kOpDecorate, {struct_type, kDecorationBlock});
      emit(kOpDecorate, {variable, kDecorationDescriptorSet, set});
      emit(kOpDecorate, {variable, kDecorationBinding, binding});
      decorations_emitted = true;
    }
    if (!globals_emitted && op == kOpFunction) {
      emit(kOpConstant, {uint_type, uint_zero, 0});
      emit(kOpConstant, {uint_type, uint_four, 4});
      emit(kOpConstant, {uint_type, uint_length, register_count});
      emit(kOpConstant, {uint_type, uint_last, register_count - 1});
      emit(kOpTypeArray, {array_type, vec4_type, uint_length});
      emit(kOpTypeStruct, {struct_type, array_type});
      emit(kOpTypePointer, {struct_pointer, kStorageUniform, struct_type});
      emit(kOpTypePointer, {element_pointer, kStorageUniform, vec4_type});
      emit(kOpVariable, {struct_pointer, variable, kStorageUniform});
      globals_emitted = true;
    }
    if (op == kOpEntryPoint && in[1] >= 0x00010400u) {
      // SPIR-V 1.4+: every global the entry point uses is in its interface
      // (before 1.4 only Input/Output variables may be listed).
      out.push_back(uint32_t(count + 1) << 16 | op);
      out.insert(out.end(), w + 1, w + count);
      out.push_back(variable);
    } else if (op == kOpLoad && count >= 4 && bank_pointer_values.count(w[3]) &&
               w[1] == vec4_type) {
      const Address& address = bank_pointer_values[w[3]];
      uint32_t offset32 = 0;
      if (const auto source = uconvert_sources.find(address.offset_id);
          source != uconvert_sources.end()) {
        offset32 = source->second;
      } else {
        offset32 = bound++;
        emit(kOpUConvert, {uint_type, offset32, address.offset_id});
      }
      uint32_t index = bound++;
      const uint32_t pointer = bound++;
      emit(kOpShiftRightLogical, {uint_type, index, offset32, uint_four});
      const bool constant_offset =
          constants.count(address.offset_id) || constants.count(offset32);
      if (!constant_offset && bool_type) {
        const uint32_t in_range = bound++, clamped = bound++;
        emit(kOpULessThan, {bool_type, in_range, index, uint_length});
        emit(kOpSelect, {uint_type, clamped, in_range, index, uint_last});
        index = clamped;
        ++result.clamped_loads;
      }
      emit(kOpAccessChain, {element_pointer, pointer, variable, uint_zero, index});
      // Same result id and type, now from the uniform block (drop any
      // physical-storage memory operands such as Aligned).
      emit(kOpLoad, {w[1], w[2], pointer});
      ++result.rewritten_loads;
    } else {
      out.insert(out.end(), w, w + count);
    }
    i += count;
  }
  if (!decorations_emitted || !globals_emitted) return std::nullopt;
  out[3] = bound;
  return result;
}

}  // namespace rex::graphics::gta4_native

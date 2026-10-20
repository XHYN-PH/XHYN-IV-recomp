#pragma once

// Rewrites XenosRecomp's shared-constant reads from buffer-device-address
// loads into uniform-buffer loads (the companion of native_spirv_ubo_constants.h).
//
// The recompiled shaders read texture/sampler descriptor indices, LOD biases,
// boolean constants, the alpha threshold, the half-pixel offset and similar
// values as
//   RawBufferLoad<T>(push.SharedConstants + <constant offset>)
// for every texture fetch and branch. On Turnip each becomes a global load in
// the draw's preamble; from a dynamic uniform buffer the command processor
// streams them into the constant file ahead of the draw instead.
//
// Rewritten: loads of 32-bit uint/float scalars and 2-4 component vectors at a
// compile-time offset whose bytes lie in one 16-byte register. Anything else
// keeps its original load, which stays valid because the push constants still
// carry the shared-constant address.

#include <cstdint>
#include <initializer_list>
#include <optional>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace rex::graphics::gta4_native {

struct SpirvUboSharedResult {
  std::vector<uint32_t> words;
  uint32_t rewritten_loads = 0;
};

// bank: the push-constant member holding the shared-constant address (2).
// register_count: 16-byte registers in the uniform block.
inline std::optional<SpirvUboSharedResult> RewriteSpirvSharedConstantsToUbo(
    const std::vector<uint32_t>& in, uint32_t bank, uint32_t register_count, uint32_t set,
    uint32_t binding) {
  constexpr uint16_t kOpEntryPoint = 15, kOpTypeBool = 20, kOpTypeInt = 21, kOpTypeFloat = 22,
                     kOpTypeVector = 23, kOpTypeArray = 28, kOpTypeStruct = 30,
                     kOpTypePointer = 32, kOpConstant = 43, kOpFunction = 54, kOpVariable = 59,
                     kOpLoad = 61, kOpAccessChain = 65, kOpInBoundsAccessChain = 66,
                     kOpDecorate = 71, kOpMemberDecorate = 72, kOpVectorShuffle = 79,
                     kOpCompositeExtract = 81, kOpConvertUToPtr = 120, kOpBitcast = 124,
                     kOpIAdd = 128;
  constexpr uint32_t kStoragePushConstant = 9, kStorageUniform = 2,
                     kStoragePhysicalStorageBuffer = 5349;
  constexpr uint32_t kDecorationBlock = 2, kDecorationArrayStride = 6, kDecorationOffset = 35,
                     kDecorationBinding = 33, kDecorationDescriptorSet = 34;
  if (in.size() < 5 || in[0] != 0x07230203u) return std::nullopt;

  // Pass 1: types, constants, the shared bank base, constant-offset pointers.
  uint32_t bound = in[3];
  uint32_t push_variable = 0, uint_type = 0, ulong_type = 0, float_type = 0;
  std::unordered_map<uint32_t, uint32_t> uint_vectors;   // component count -> type
  std::unordered_map<uint32_t, uint32_t> float_vectors;  // component count -> type
  struct VectorInfo {
    uint32_t component;  // element type id
    uint32_t count;
  };
  std::unordered_map<uint32_t, VectorInfo> vector_types;
  std::unordered_map<uint32_t, uint64_t> constants;
  std::unordered_set<uint32_t> bank_pointers, bank_bases;
  std::unordered_map<uint32_t, uint64_t> addresses;       // ulong id -> constant byte offset
  std::unordered_map<uint32_t, uint32_t> psb_pointee;     // PSB pointer type -> pointee type
  struct Target {
    uint32_t offset;
    uint32_t pointee;
  };
  std::unordered_map<uint32_t, Target> pointers;          // pointer id -> target
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
      case kOpTypeVector:
        vector_types[w[1]] = {w[2], w[3]};
        if (w[2] == uint_type && uint_type) uint_vectors[w[3]] = w[1];
        if (w[2] == float_type && float_type) float_vectors[w[3]] = w[1];
        break;
      case kOpTypePointer:
        if (w[2] == kStoragePhysicalStorageBuffer) psb_pointee[w[1]] = w[3];
        break;
      case kOpConstant:
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
      case kOpIAdd:
        if (w[1] == ulong_type && ulong_type) {
          const uint32_t base = bank_bases.count(w[3]) ? w[3] : bank_bases.count(w[4]) ? w[4] : 0;
          const uint32_t other = base == w[3] ? w[4] : w[3];
          const auto value = constants.find(other);
          if (base && value != constants.end() && value->second < uint64_t(register_count) * 16)
            addresses[w[2]] = value->second;
        }
        break;
      case kOpBitcast:
      case kOpConvertUToPtr: {
        const auto pointee = psb_pointee.find(w[1]);
        const auto address = addresses.find(w[3]);
        if (pointee != psb_pointee.end() && address != addresses.end())
          pointers[w[2]] = {uint32_t(address->second), pointee->second};
        break;
      }
      default:
        break;
    }
    i += count;
  }
  if (!uint_type || pointers.empty()) return std::nullopt;

  // Which loads can be rewritten, and what they need.
  struct Rewrite {
    uint32_t index;      // register
    uint32_t component;  // first 32-bit component
    uint32_t count;      // components
    bool is_float;
  };
  std::unordered_map<uint32_t, Rewrite> rewrites;  // load result id -> rewrite
  std::unordered_set<uint32_t> needed_indices, needed_uvectors;
  for (size_t i = 5; i < in.size();) {
    const uint32_t count = in[i] >> 16;
    const uint16_t op = uint16_t(in[i] & 0xFFFF);
    const uint32_t* w = in.data() + i;
    if (op == kOpLoad && count >= 4) {
      const auto target = pointers.find(w[3]);
      if (target != pointers.end() && target->second.pointee == w[1] && !(target->second.offset & 3)) {
        uint32_t components = 0;
        bool is_float = false;
        if (w[1] == uint_type) {
          components = 1;
        } else if (w[1] == float_type && float_type) {
          components = 1;
          is_float = true;
        } else if (const auto vector = vector_types.find(w[1]); vector != vector_types.end()) {
          if (vector->second.component == uint_type || (vector->second.component == float_type && float_type)) {
            components = vector->second.count;
            is_float = vector->second.component == float_type;
          }
        }
        const uint32_t first = (target->second.offset >> 2) & 3;
        if (components && first + components <= 4) {
          rewrites[w[2]] = {target->second.offset >> 4, first, components, is_float};
          needed_indices.insert(target->second.offset >> 4);
          if (components > 1) needed_uvectors.insert(components);
        }
      }
    }
    i += count;
  }
  if (rewrites.empty()) return std::nullopt;

  // New ids.
  const bool make_uvec4 = !uint_vectors.count(4);
  const uint32_t uvec4_type = make_uvec4 ? bound++ : uint_vectors[4];
  std::unordered_map<uint32_t, uint32_t> new_uvectors;  // count -> new type id
  for (uint32_t components : needed_uvectors) {
    if (components != 4 && !uint_vectors.count(components)) new_uvectors[components] = bound++;
  }
  const auto uvector = [&](uint32_t components) {
    if (components == 4) return uvec4_type;
    const auto existing = uint_vectors.find(components);
    return existing != uint_vectors.end() ? existing->second : new_uvectors[components];
  };
  const uint32_t uint_zero = bound++, uint_length = bound++;
  const uint32_t array_type = bound++, struct_type = bound++, struct_pointer = bound++,
                 element_pointer = bound++, variable = bound++;
  std::unordered_map<uint32_t, uint32_t> index_constants;
  for (uint32_t index : needed_indices) index_constants[index] = bound++;

  SpirvUboSharedResult result;
  std::vector<uint32_t>& out = result.words;
  out.reserve(in.size() + rewrites.size() * 12 + 64);
  out.insert(out.end(), in.begin(), in.begin() + 5);
  const auto emit = [&](uint16_t op, std::initializer_list<uint32_t> operands) {
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
      if (make_uvec4) emit(kOpTypeVector, {uvec4_type, uint_type, 4});
      for (const auto& [components, type] : new_uvectors) emit(kOpTypeVector, {type, uint_type, components});
      emit(kOpConstant, {uint_type, uint_zero, 0});
      emit(kOpConstant, {uint_type, uint_length, register_count});
      for (const auto& [index, id] : index_constants) emit(kOpConstant, {uint_type, id, index});
      emit(kOpTypeArray, {array_type, uvec4_type, uint_length});
      emit(kOpTypeStruct, {struct_type, array_type});
      emit(kOpTypePointer, {struct_pointer, kStorageUniform, struct_type});
      emit(kOpTypePointer, {element_pointer, kStorageUniform, uvec4_type});
      emit(kOpVariable, {struct_pointer, variable, kStorageUniform});
      globals_emitted = true;
    }
    const auto rewrite = op == kOpLoad && count >= 4 ? rewrites.find(w[2]) : rewrites.end();
    if (op == kOpEntryPoint && in[1] >= 0x00010400u) {
      // SPIR-V 1.4+: every global the entry point uses is in its interface.
      out.push_back(uint32_t(count + 1) << 16 | op);
      out.insert(out.end(), w + 1, w + count);
      out.push_back(variable);
    } else if (rewrite != rewrites.end()) {
      const Rewrite& r = rewrite->second;
      const uint32_t pointer = bound++, registers = bound++;
      emit(kOpAccessChain, {element_pointer, pointer, variable, uint_zero, index_constants[r.index]});
      emit(kOpLoad, {uvec4_type, registers, pointer});
      // Same result id and type as the original load.
      if (r.count == 1) {
        if (r.is_float) {
          const uint32_t bits = bound++;
          emit(kOpCompositeExtract, {uint_type, bits, registers, r.component});
          emit(kOpBitcast, {w[1], w[2], bits});
        } else {
          emit(kOpCompositeExtract, {uint_type, w[2], registers, r.component});
        }
      } else {
        std::vector<uint32_t> shuffle = {r.is_float ? uvector(r.count) : w[1],
                                         r.is_float ? bound++ : w[2], registers, registers};
        for (uint32_t c = 0; c < r.count; ++c) shuffle.push_back(r.component + c);
        out.push_back(uint32_t(shuffle.size() + 1) << 16 | kOpVectorShuffle);
        out.insert(out.end(), shuffle.begin(), shuffle.end());
        if (r.is_float) emit(kOpBitcast, {w[1], w[2], shuffle[1]});
      }
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

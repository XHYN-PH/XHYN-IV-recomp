#pragma once
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <unordered_map>
#include <vector>
namespace rex::ui::vulkan::util {
enum class ClipCullRewriteResult { kUnchanged, kRewritten, kUnsupported };
// This compatibility fallback omits clip planes; it does not emulate clipping.
inline ClipCullRewriteResult RewriteClipCullDistances(const uint32_t* code, size_t code_size_bytes,
    bool clip_supported, bool cull_supported, uint32_t max_output_locations, std::vector<uint32_t>& rewritten) {
  constexpr uint32_t kMagic = 0x07230203;
  constexpr uint32_t kOpCapability = 17, kOpEntryPoint = 15, kOpTypeVector = 23,
                     kOpTypeMatrix = 24, kOpTypeArray = 28, kOpTypePointer = 32,
                     kOpConstant = 43, kOpVariable = 59, kOpDecorate = 71, kOpMemberDecorate = 72;
  constexpr uint32_t kCapabilityClipDistance = 32, kCapabilityCullDistance = 33;
  constexpr uint32_t kDecorationBuiltIn = 11, kDecorationLocation = 30;
  constexpr uint32_t kBuiltInClipDistance = 3, kBuiltInCullDistance = 4;
  constexpr uint32_t kStorageClassOutput = 3, kExecutionModelVertex = 0;

  const size_t word_count = code_size_bytes / sizeof(uint32_t);
  if (!code || code_size_bytes % sizeof(uint32_t) || word_count < 5 || code[0] != kMagic)
    return ClipCullRewriteResult::kUnsupported;
  if (clip_supported && cull_supported) return ClipCullRewriteResult::kUnchanged;
  auto unsupported_builtin = [&](uint32_t builtin) {
    return (builtin == kBuiltInClipDistance && !clip_supported) ||
           (builtin == kBuiltInCullDistance && !cull_supported);
  };
  auto unsupported_capability = [&](uint32_t capability) {
    return (capability == kCapabilityClipDistance && !clip_supported) ||
           (capability == kCapabilityCullDistance && !cull_supported);
  };

  struct Variable {
    uint32_t type;
    uint32_t storage_class;
  };
  std::unordered_map<uint32_t, std::vector<uint32_t>> types;  // id -> opcode, operands
  std::unordered_map<uint32_t, uint32_t> constants;
  std::unordered_map<uint32_t, Variable> variables;
  std::unordered_map<uint32_t, uint32_t> locations;
  std::vector<size_t> removed_capabilities;
  std::vector<size_t> builtin_decorations;  // word index of each OpDecorate to rewrite
  std::vector<uint32_t> member_builtins;
  bool needs_clip_rewrite = false;
  bool needs_cull_rewrite = false;
  bool vertex_only = true;
  uint32_t highest_member_location = 0;
  bool has_member_location = false;
  for (size_t i = 5; i < word_count;) {
    const uint32_t opcode = code[i] & 0xFFFF;
    const uint32_t length = code[i] >> 16;
    if (!length || i + length > word_count) {
      return ClipCullRewriteResult::kUnsupported;
    }
    const uint32_t* operands = code + i + 1;
    const uint32_t operand_count = length - 1;
    if (opcode == kOpCapability && operand_count >= 1 && unsupported_capability(operands[0])) {
      removed_capabilities.push_back(i);
      needs_clip_rewrite |= operands[0] == kCapabilityClipDistance;
      needs_cull_rewrite |= operands[0] == kCapabilityCullDistance;
    } else if (opcode == kOpEntryPoint && operand_count >= 1) {
      vertex_only = vertex_only && operands[0] == kExecutionModelVertex;
    } else if (opcode == kOpDecorate && operand_count >= 3 && operands[1] == kDecorationBuiltIn &&
               unsupported_builtin(operands[2])) {
      builtin_decorations.push_back(i);
    } else if (opcode == kOpMemberDecorate && operand_count >= 4 &&
               operands[2] == kDecorationBuiltIn && unsupported_builtin(operands[3])) {
      member_builtins.push_back(operands[3]);
    } else if (opcode == kOpDecorate && operand_count >= 3 &&
               operands[1] == kDecorationLocation) {
      locations[operands[0]] = operands[2];
    } else if (opcode == kOpMemberDecorate && operand_count >= 4 &&
               operands[2] == kDecorationLocation) {
      highest_member_location = std::max(highest_member_location, operands[3]);
      has_member_location = true;
    } else if ((opcode == kOpTypeVector || opcode == kOpTypeMatrix || opcode == kOpTypeArray ||
                opcode == kOpTypePointer) &&
               operand_count >= 1) {
      types[operands[0]].assign(code + i, code + i + length);
    } else if (opcode == kOpConstant && operand_count >= 3) {
      constants[operands[1]] = operands[2];
    } else if (opcode == kOpVariable && operand_count >= 3) {
      variables[operands[1]] = {operands[0], operands[2]};
    }
    i += length;
  }
  // glslang includes unused ClipDistance/CullDistance members in gl_PerVertex
  // even in shaders that only write Position. Without the corresponding
  // capability these declarations are inert: rejecting them prevents the UI
  // and fullscreen resolve shaders from reaching Vulkan on native devices.
  if (removed_capabilities.empty()) return ClipCullRewriteResult::kUnchanged;
  const auto needs_rewrite = [&](uint32_t builtin) {
    return (builtin == kBuiltInClipDistance && needs_clip_rewrite) ||
           (builtin == kBuiltInCullDistance && needs_cull_rewrite);
  };
  // An actively used gl_PerVertex member cannot become a located output in
  // place. Keep rejecting that case rather than submit an invalid fallback.
  if (std::any_of(member_builtins.begin(), member_builtins.end(), needs_rewrite))
    return ClipCullRewriteResult::kUnsupported;
  std::erase_if(builtin_decorations, [&](size_t index) {
    return !needs_rewrite(code[index + 3]);
  });
  if (builtin_decorations.empty() || !vertex_only) {
    return ClipCullRewriteResult::kUnsupported;
  }

  // Locations a value of this type occupies (scalars and vectors up to 4
  // components take one; 64-bit vectors are not used by these outputs).
  std::function<uint32_t(uint32_t, uint32_t)> slots = [&](uint32_t type, uint32_t depth) {
    const auto it = types.find(type);
    if (it == types.end() || depth > 8) {
      return uint32_t(1);
    }
    const std::vector<uint32_t>& t = it->second;
    const uint32_t opcode = t[0] & 0xFFFF;
    if (opcode == kOpTypeArray && t.size() >= 4) {
      const auto length = constants.find(t[3]);
      const uint64_t count = uint64_t(length != constants.end() ? length->second : UINT32_MAX) * slots(t[2], depth + 1);
      return uint32_t(std::min(count,uint64_t(UINT32_MAX)));
    }
    if (opcode == kOpTypeMatrix && t.size() >= 4) {
      return t[3];
    }
    return uint32_t(1);
  };
  auto variable_slots = [&](const Variable& variable) {
    const auto pointer = types.find(variable.type);
    if (pointer == types.end() || pointer->second.size() < 4) {
      return uint32_t(1);
    }
    return slots(pointer->second[3], 0);
  };

  uint64_t next_location = has_member_location ? uint64_t(highest_member_location) + 1 : 0;
  for (const auto& [id, location] : locations) {
    const auto variable = variables.find(id);
    if (variable != variables.end() && variable->second.storage_class == kStorageClassOutput) {
      next_location = std::max(next_location,uint64_t(location) + variable_slots(variable->second));
    }
  }

  rewritten.assign(code, code + word_count);
  for (const size_t decoration : builtin_decorations) {
    const auto variable = variables.find(code[decoration + 1]);
    if (variable == variables.end() || variable->second.storage_class != kStorageClassOutput) {
      return ClipCullRewriteResult::kUnsupported;
    }
    const uint32_t location = uint32_t(next_location);
    next_location += variable_slots(variable->second);
    if (next_location > max_output_locations) {
      return ClipCullRewriteResult::kUnsupported;
    }
    // OpDecorate %id BuiltIn ClipDistance -> OpDecorate %id Location N, same length.
    rewritten[decoration + 2] = kDecorationLocation;
    rewritten[decoration + 3] = location;
  }
  // Remove the capability declarations, last first so earlier indices stay valid.
  for (auto it = removed_capabilities.rbegin(); it != removed_capabilities.rend(); ++it) {
    rewritten.erase(rewritten.begin() + *it, rewritten.begin() + *it + (code[*it] >> 16));
  }
  return ClipCullRewriteResult::kRewritten;
}

}

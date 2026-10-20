#pragma once

#include "native_gpu_scope_summary_labels.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <string_view>
#include <utility>
#include <type_traits>
#include <fmt/format.h>
#include <rex/logging.h>
#include <rex/ui/vulkan/device.h>
#include <rex/ui/vulkan/instance.h>
#include <rex/ui/vulkan/util.h>

namespace rex::graphics::gta4_native::gpu_labels {

// Opt-in metadata only: no queries, barriers, attachment changes or waits.
inline bool Enabled() {
  static const bool enabled = [] {
    const char* value = std::getenv("REX_GTA4_GPU_PASS_MARKERS");
    return value && std::strcmp(value, "1") == 0;
  }();
  return enabled;
}

struct Functions {
  VkDevice device = VK_NULL_HANDLE;
  PFN_vkCmdBeginDebugUtilsLabelEXT begin = nullptr;
  PFN_vkCmdEndDebugUtilsLabelEXT end = nullptr;
};

inline const Functions& GetFunctions(const ui::vulkan::VulkanDevice* device) {
  // Only function addresses are cached. There are no owned Vulkan objects.
  thread_local Functions functions;
  if (device && functions.device != device->device()) {
    functions = {};
    functions.device = device->device();
    if (device->vulkan_instance()->extensions().ext_EXT_debug_utils) {
      const auto get = device->vulkan_instance()->functions().vkGetDeviceProcAddr;
      functions.begin = reinterpret_cast<PFN_vkCmdBeginDebugUtilsLabelEXT>(
          get(device->device(), "vkCmdBeginDebugUtilsLabelEXT"));
      functions.end = reinterpret_cast<PFN_vkCmdEndDebugUtilsLabelEXT>(
          get(device->device(), "vkCmdEndDebugUtilsLabelEXT"));
    }
  }
  return functions;
}

inline bool Available(const ui::vulkan::VulkanDevice* device, VkCommandBuffer command) {
  if (!Enabled() || !device || !command) return false;
  const auto& api = GetFunctions(device);
  return api.begin && api.end;
}

inline void Begin(const ui::vulkan::VulkanDevice* device, VkCommandBuffer command,
                  const char* name) {
  if (!Enabled() || !device || !command) return;
  const auto& api = GetFunctions(device);
  if (!api.begin || !api.end) return;
  VkDebugUtilsLabelEXT label{};
  label.sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT;
  label.pLabelName = name;
  api.begin(command, &label);
}

inline void End(const ui::vulkan::VulkanDevice* device, VkCommandBuffer command) {
  if (!Enabled() || !device || !command) return;
  const auto& api = GetFunctions(device);
  if (api.begin && api.end) api.end(command);
}

template <typename Handle>
inline uint64_t HandleValue(Handle value) {
  if constexpr (std::is_pointer_v<Handle>) return uint64_t(reinterpret_cast<uintptr_t>(value));
  else return uint64_t(value);
}

inline void BeginRendering(const ui::vulkan::VulkanDevice* device,
                           VkCommandBuffer command, const VkRenderingInfo& info,
                           const char* operation, uint32_t frame = 0,
                           uint32_t command_index = 0) {
  if (!Enabled() || !device || !command) return;
  const auto& api = GetFunctions(device);
  if (!api.begin || !api.end) return;
  std::string name = fmt::format("GTA4/render/{} frame={} cmd={} extent={}x{} colors={}",
      operation, frame, command_index, info.renderArea.extent.width,
      info.renderArea.extent.height, info.colorAttachmentCount);
  for (uint32_t index = 0; info.pColorAttachments && index < info.colorAttachmentCount; ++index) {
    const auto& attachment = info.pColorAttachments[index];
    name += fmt::format(" c{}={:X}:load{}:store{}", index,
        HandleValue(attachment.imageView),
        uint32_t(attachment.loadOp), uint32_t(attachment.storeOp));
  }
  if (info.pDepthAttachment) {
    name += fmt::format(" depth={:X}:load{}:store{}",
        HandleValue(info.pDepthAttachment->imageView),
        uint32_t(info.pDepthAttachment->loadOp), uint32_t(info.pDepthAttachment->storeOp));
  }
  Begin(device, command, name.c_str());
}

inline void BeginSceneScope(const ui::vulkan::VulkanDevice* device,
                            VkCommandBuffer command, const VkRenderingInfo& info,
                            NativeGpuScopeSummary* summary, uint32_t frame,
                            uint32_t first_command) {
  if (!summary || !Available(device, command)) return;
  summary->Begin(frame, first_command);
  const auto operation = fmt::format("RecordNativeFrame scope={}", summary->serial());
  BeginRendering(device, command, info, operation.c_str(), frame, first_command);
}

// Metadata only. These groups are on the Metal command buffer, outside the
// ended render encoder AND after the outer group has closed. Instruments does
// not export nested encoder groups reliably. The explicit frame/scope identity
// joins these sibling records to their outer scope and its Metal encoder ID.
// An encoder with several entries is mixed; never divide its GPU time according
// to draw counts or call a CPU group duration a GPU shader duration.
inline void EndSceneScope(const ui::vulkan::VulkanDevice* device,
                          VkCommandBuffer command, NativeGpuScopeSummary& summary) {
  if (!summary.active()) return;
  if (Available(device, command)) {
    EmitNativeGpuScopeLabels(summary, [&](const std::string& label) {
      Begin(device, command, label.c_str());
      End(device, command);
    });
  }
  summary.End();
}

// The group begins BEFORE vkCmdBeginRendering and ends AFTER vkCmdEndRendering.
// MoltenVK therefore pushes/pops the command-buffer group, not two different
// render encoders. This leaves all existing render-pass boundaries intact.

inline void NameShader(const ui::vulkan::VulkanDevice* device, VkShaderModule shader,
                       std::string_view name) {
  if (!Enabled() || !device || !shader) return;
  const std::string owned(name);
  device->SetObjectName(VK_OBJECT_TYPE_SHADER_MODULE, shader, owned.c_str());
}

inline VkShaderModule CreateShader(const ui::vulkan::VulkanDevice* device,
                                  const uint32_t* code, size_t size,
                                  std::string_view name) {
  const auto shader = ui::vulkan::util::CreateShaderModule(device, code, size);
  if (!shader) REXLOG_ERROR("gta4-native: failed to create shader '{}'", name);
  NameShader(device, shader, name);
  return shader;
}

template <typename Function>
decltype(auto) Transfer(const ui::vulkan::VulkanDevice* device, VkCommandBuffer command,
                        const char* name, Function&& function) {
  Begin(device, command, name);
  struct Guard {
    const ui::vulkan::VulkanDevice* device;
    VkCommandBuffer command;
    ~Guard() { End(device, command); }
  } guard{device, command};
  return std::forward<Function>(function)();
}

}  // namespace rex::graphics::gta4_native::gpu_labels

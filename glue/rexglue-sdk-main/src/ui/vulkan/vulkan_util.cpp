/**
 ******************************************************************************
 * Xenia : Xbox 360 Emulator Research Project                                 *
 ******************************************************************************
 * Copyright 2020 Ben Vanik. All rights reserved.                             *
 * Released under the BSD license - see LICENSE in the root for more details. *
 ******************************************************************************
 *
 * @modified    Tom Clay, 2026 - Adapted for ReXGlue runtime
 */

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <functional>
#include <unordered_map>
#include <vector>

#include <rex/assert.h>
#include <rex/logging.h>
#include <rex/math.h>
#include <rex/ui/vulkan/device.h>
#include <rex/ui/vulkan/util.h>

namespace rex {
namespace ui {
namespace vulkan {
namespace util {

void FlushMappedMemoryRange(const VulkanDevice* const vulkan_device, const VkDeviceMemory memory,
                            const uint32_t memory_type, const VkDeviceSize offset,
                            const VkDeviceSize memory_size, const VkDeviceSize size) {
  assert_false(size != VK_WHOLE_SIZE && memory_size == VK_WHOLE_SIZE);
  assert_true(memory_size == VK_WHOLE_SIZE || offset <= memory_size);
  assert_true(memory_size == VK_WHOLE_SIZE || size <= memory_size - offset);
  if (!size || (vulkan_device->memory_types().host_coherent & (uint32_t(1) << memory_type))) {
    return;
  }
  VkMappedMemoryRange range;
  range.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
  range.pNext = nullptr;
  range.memory = memory;
  range.offset = offset;
  range.size = size;
  const VkDeviceSize non_coherent_atom_size = vulkan_device->properties().nonCoherentAtomSize;
  range.offset = offset / non_coherent_atom_size * non_coherent_atom_size;
  if (size != VK_WHOLE_SIZE) {
    range.size =
        std::min(rex::round_up(offset + size, non_coherent_atom_size), memory_size) - range.offset;
  }
  vulkan_device->functions().vkFlushMappedMemoryRanges(vulkan_device->device(), 1, &range);
}

bool CreateDedicatedAllocationBuffer(const VulkanDevice* const vulkan_device,
                                     const VkDeviceSize size, const VkBufferUsageFlags usage,
                                     const MemoryPurpose memory_purpose, VkBuffer& buffer_out,
                                     VkDeviceMemory& memory_out, uint32_t* const memory_type_out,
                                     VkDeviceSize* const memory_size_out) {
  const VulkanDevice::Functions& dfn = vulkan_device->functions();
  const VkDevice device = vulkan_device->device();

  VkBufferCreateInfo buffer_create_info;
  buffer_create_info.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
  buffer_create_info.pNext = nullptr;
  buffer_create_info.flags = 0;
  buffer_create_info.size = size;
  buffer_create_info.usage = usage;
  buffer_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  buffer_create_info.queueFamilyIndexCount = 0;
  buffer_create_info.pQueueFamilyIndices = nullptr;
  VkBuffer buffer;
  if (dfn.vkCreateBuffer(device, &buffer_create_info, nullptr, &buffer) != VK_SUCCESS) {
    return false;
  }

  VkMemoryRequirements memory_requirements;
  dfn.vkGetBufferMemoryRequirements(device, buffer, &memory_requirements);
  uint32_t memory_type = ChooseMemoryType(vulkan_device->memory_types(),
                                          memory_requirements.memoryTypeBits, memory_purpose);
  if (memory_type == UINT32_MAX) {
    dfn.vkDestroyBuffer(device, buffer, nullptr);
    return false;
  }

  VkMemoryAllocateInfo memory_allocate_info;
  memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  memory_allocate_info.pNext = nullptr;
  memory_allocate_info.allocationSize = memory_requirements.size;
  memory_allocate_info.memoryTypeIndex = memory_type;
  VkMemoryDedicatedAllocateInfo memory_dedicated_allocate_info;
  if (vulkan_device->extensions().ext_1_1_KHR_dedicated_allocation) {
    memory_dedicated_allocate_info.pNext = memory_allocate_info.pNext;
    memory_allocate_info.pNext = &memory_dedicated_allocate_info;
    memory_dedicated_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    memory_dedicated_allocate_info.pNext = nullptr;
    memory_dedicated_allocate_info.image = VK_NULL_HANDLE;
    memory_dedicated_allocate_info.buffer = buffer;
  }
  VkDeviceMemory memory;
  if (dfn.vkAllocateMemory(device, &memory_allocate_info, nullptr, &memory) != VK_SUCCESS) {
    dfn.vkDestroyBuffer(device, buffer, nullptr);
    return false;
  }

  if (dfn.vkBindBufferMemory(device, buffer, memory, 0) != VK_SUCCESS) {
    dfn.vkDestroyBuffer(device, buffer, nullptr);
    dfn.vkFreeMemory(device, memory, nullptr);
    return false;
  }

  buffer_out = buffer;
  memory_out = memory;
  if (memory_type_out) {
    *memory_type_out = memory_type;
  }
  if (memory_size_out) {
    *memory_size_out = memory_allocate_info.allocationSize;
  }
  return true;
}

bool CreateDedicatedAllocationImage(const VulkanDevice* const vulkan_device,
                                    const VkImageCreateInfo& create_info,
                                    const MemoryPurpose memory_purpose, VkImage& image_out,
                                    VkDeviceMemory& memory_out, uint32_t* const memory_type_out,
                                    VkDeviceSize* const memory_size_out) {
  // Legacy image-format queries describe ordinary images exactly. Extended
  // creation chains (external memory, format lists, etc.) require their own
  // matching properties2 query and continue through the existing creation path.
  // Why an image could not be made (rare; the caller only sees false).
  const auto log_failure = [&](const char* step, int result, uint64_t size) {
    static std::atomic<uint32_t> count{0};
    if (count.fetch_add(1, std::memory_order_relaxed) < 32) {
      REXLOG_WARN("vulkan: image {} failed: result={} format={} extent={}x{} samples={} "
                  "usage={:08X} size={}",
                  step, result, uint32_t(create_info.format), create_info.extent.width,
                  create_info.extent.height, uint32_t(create_info.samples),
                  uint32_t(create_info.usage), size);
    }
  };
  if (!create_info.pNext && !IsImageCreateInfoSupported(vulkan_device, create_info)) {
    log_failure("format-query", 0, 0);
    return false;
  }
  const VulkanDevice::Functions& dfn = vulkan_device->functions();
  const VkDevice device = vulkan_device->device();

  VkImage image;
  if (const VkResult result = dfn.vkCreateImage(device, &create_info, nullptr, &image);
      result != VK_SUCCESS) {
    log_failure("create", int(result), 0);
    return false;
  }

  VkMemoryRequirements memory_requirements;
  dfn.vkGetImageMemoryRequirements(device, image, &memory_requirements);
  uint32_t memory_type = ChooseMemoryType(vulkan_device->memory_types(),
                                          memory_requirements.memoryTypeBits, memory_purpose);
  if (memory_type == UINT32_MAX) {
    log_failure("memory-type", int(memory_requirements.memoryTypeBits), memory_requirements.size);
    dfn.vkDestroyImage(device, image, nullptr);
    return false;
  }

  VkMemoryAllocateInfo memory_allocate_info;
  memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  memory_allocate_info.pNext = nullptr;
  memory_allocate_info.allocationSize = memory_requirements.size;
  memory_allocate_info.memoryTypeIndex = memory_type;
  VkMemoryDedicatedAllocateInfo memory_dedicated_allocate_info;
  if (vulkan_device->extensions().ext_1_1_KHR_dedicated_allocation) {
    memory_dedicated_allocate_info.pNext = memory_allocate_info.pNext;
    memory_allocate_info.pNext = &memory_dedicated_allocate_info;
    memory_dedicated_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO;
    memory_dedicated_allocate_info.pNext = nullptr;
    memory_dedicated_allocate_info.image = image;
    memory_dedicated_allocate_info.buffer = VK_NULL_HANDLE;
  }
  VkDeviceMemory memory;
  if (const VkResult result = dfn.vkAllocateMemory(device, &memory_allocate_info, nullptr, &memory);
      result != VK_SUCCESS) {
    log_failure("allocate", int(result), memory_requirements.size);
    dfn.vkDestroyImage(device, image, nullptr);
    return false;
  }

  if (dfn.vkBindImageMemory(device, image, memory, 0) != VK_SUCCESS) {
    dfn.vkDestroyImage(device, image, nullptr);
    dfn.vkFreeMemory(device, memory, nullptr);
    return false;
  }

  image_out = image;
  memory_out = memory;
  if (memory_type_out) {
    *memory_type_out = memory_type;
  }
  if (memory_size_out) {
    *memory_size_out = memory_allocate_info.allocationSize;
  }
  return true;
}

bool IsImageCreateInfoSupported(const VulkanDevice* vulkan_device,
                                const VkImageCreateInfo& create_info) {
  if (!vulkan_device || create_info.pNext) {
    return false;
  }
  VkImageFormatProperties properties{};
  if (vulkan_device->vulkan_instance()->functions().vkGetPhysicalDeviceImageFormatProperties(
          vulkan_device->physical_device(), create_info.format, create_info.imageType,
          create_info.tiling, create_info.usage, create_info.flags, &properties) != VK_SUCCESS) {
    return false;
  }
  return create_info.extent.width && create_info.extent.height && create_info.extent.depth &&
         create_info.extent.width <= properties.maxExtent.width &&
         create_info.extent.height <= properties.maxExtent.height &&
         create_info.extent.depth <= properties.maxExtent.depth && create_info.mipLevels &&
         create_info.mipLevels <= properties.maxMipLevels && create_info.arrayLayers &&
         create_info.arrayLayers <= properties.maxArrayLayers &&
         (properties.sampleCounts & create_info.samples) != 0;
}

VkPipeline CreateComputePipeline(const VulkanDevice* const vulkan_device,
                                 const VkPipelineLayout layout, const VkShaderModule shader,
                                 const VkSpecializationInfo* const specialization_info,
                                 const char* const entry_point) {
  VkComputePipelineCreateInfo pipeline_create_info;
  pipeline_create_info.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
  pipeline_create_info.pNext = nullptr;
  pipeline_create_info.flags = 0;
  pipeline_create_info.stage.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
  pipeline_create_info.stage.pNext = nullptr;
  pipeline_create_info.stage.flags = 0;
  pipeline_create_info.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
  pipeline_create_info.stage.module = shader;
  pipeline_create_info.stage.pName = entry_point;
  pipeline_create_info.stage.pSpecializationInfo = specialization_info;
  pipeline_create_info.layout = layout;
  pipeline_create_info.basePipelineHandle = VK_NULL_HANDLE;
  pipeline_create_info.basePipelineIndex = -1;
  VkPipeline pipeline;
  if (vulkan_device->functions().vkCreateComputePipelines(vulkan_device->device(), VK_NULL_HANDLE,
                                                          1, &pipeline_create_info, nullptr,
                                                          &pipeline) != VK_SUCCESS) {
    return VK_NULL_HANDLE;
  }
  return pipeline;
}

VkPipeline CreateComputePipeline(const VulkanDevice* const vulkan_device, VkPipelineLayout layout,
                                 const uint32_t* shader_code, size_t shader_code_size_bytes,
                                 const VkSpecializationInfo* specialization_info,
                                 const char* entry_point) {
  const VkShaderModule shader =
      CreateShaderModule(vulkan_device, shader_code, shader_code_size_bytes);
  if (shader == VK_NULL_HANDLE) {
    return VK_NULL_HANDLE;
  }
  const VkPipeline pipeline =
      CreateComputePipeline(vulkan_device, layout, shader, specialization_info, entry_point);
  vulkan_device->functions().vkDestroyShaderModule(vulkan_device->device(), shader, nullptr);
  return pipeline;
}

VkShaderModule CreateShaderModule(const VulkanDevice* vulkan_device,
                                 const uint32_t* code, size_t code_size_bytes) {
  std::vector<uint32_t> rewritten;
  const auto rewrite = RewriteUnsupportedClipCullDistances(
      vulkan_device, code, code_size_bytes, rewritten);
  if (rewrite == ClipCullRewriteResult::kUnsupported) {
    REXLOG_ERROR("Vulkan shader compatibility rejected module: bytes={} clip={} cull={}",
                 code_size_bytes, vulkan_device->properties().shaderClipDistance,
                 vulkan_device->properties().shaderCullDistance);
    return VK_NULL_HANDLE;
  }
  if (rewrite == ClipCullRewriteResult::kRewritten) {
    code = rewritten.data();
    code_size_bytes = rewritten.size() * sizeof(uint32_t);
  }
  VkShaderModuleCreateInfo info{};
  info.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
  info.codeSize = code_size_bytes;
  info.pCode = code;
  VkShaderModule shader = VK_NULL_HANDLE;
  const auto result = vulkan_device->functions().vkCreateShaderModule(
      vulkan_device->device(), &info, nullptr, &shader);
  if (result != VK_SUCCESS) {
    REXLOG_ERROR("Vulkan shader module creation failed: result={} bytes={}",
                 int32_t(result), code_size_bytes);
    return VK_NULL_HANDLE;
  }
  return shader;
}

ClipCullRewriteResult RewriteUnsupportedClipCullDistances(const VulkanDevice* vulkan_device,
    const uint32_t* code, size_t code_size_bytes, std::vector<uint32_t>& rewritten) {
  const auto& properties = vulkan_device->properties();
  if (properties.shaderClipDistance && properties.shaderCullDistance)
    return RewriteClipCullDistances(code,code_size_bytes,true,true,0,rewritten);
  VkPhysicalDeviceProperties device_properties{};
  vulkan_device->vulkan_instance()->functions().vkGetPhysicalDeviceProperties(vulkan_device->physical_device(), &device_properties);
  return RewriteClipCullDistances(code, code_size_bytes, properties.shaderClipDistance,
      properties.shaderCullDistance, device_properties.limits.maxVertexOutputComponents/4, rewritten);
}

}  // namespace util
}  // namespace vulkan
}  // namespace ui
}  // namespace rex

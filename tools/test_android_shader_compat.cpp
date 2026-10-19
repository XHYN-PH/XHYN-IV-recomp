#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>
#include <rex/ui/vulkan/clip_cull_rewrite.h>

namespace shaders {
#include "ui/shaders/vulkan_spirv/immediate_vs.h"
#include "ui/shaders/vulkan_spirv/immediate_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_triangle_strip_rect_vs.h"
#include "ui/shaders/vulkan_spirv/guest_output_bilinear_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_bilinear_dither_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_cas_resample_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_cas_resample_dither_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_cas_sharpen_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_cas_sharpen_dither_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_fsr_easu_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_fsr_rcas_ps.h"
#include "ui/shaders/vulkan_spirv/guest_output_ffx_fsr_rcas_dither_ps.h"
#include "graphics/shaders/vulkan_spirv/fullscreen_cw_vs.h"
#include "graphics/gta4_native/depth_handoff_ps.h"
#include "graphics/gta4_native/scene_depth_handoff_ps.h"
#include "graphics/gta4_native/packed_depth_alias_ps.h"
}

int main(int argc, char** argv) {
  using namespace rex::ui::vulkan::util;
  struct Shader { const char* name; const uint32_t* code; size_t bytes; };
#define SHADER(name) Shader{#name, shaders::name, sizeof(shaders::name)}
  const Shader cases[] = {
      SHADER(immediate_vs), SHADER(immediate_ps),
      SHADER(guest_output_triangle_strip_rect_vs),
      SHADER(guest_output_bilinear_ps), SHADER(guest_output_bilinear_dither_ps),
      SHADER(guest_output_ffx_cas_resample_ps), SHADER(guest_output_ffx_cas_resample_dither_ps),
      SHADER(guest_output_ffx_cas_sharpen_ps), SHADER(guest_output_ffx_cas_sharpen_dither_ps),
      SHADER(guest_output_ffx_fsr_easu_ps), SHADER(guest_output_ffx_fsr_rcas_ps),
      SHADER(guest_output_ffx_fsr_rcas_dither_ps), SHADER(fullscreen_cw_vs),
      SHADER(gta4_native_depth_handoff_ps), SHADER(gta4_native_scene_depth_handoff_ps),
      SHADER(gta4_native_packed_depth_alias_ps)};
#undef SHADER
  unsigned failures = 0, checks = 0;
  for (const auto& shader : cases) {
    for (unsigned features = 0; features < 4; ++features) {
      std::vector<uint32_t> output;
      const auto result = RewriteClipCullDistances(shader.code, shader.bytes,
          bool(features & 1), bool(features & 2), 32, output);
      ++checks;
      // These production shaders declare unused gl_PerVertex members, but do
      // not use either capability. They must reach vkCreateShaderModule intact.
      if (result != ClipCullRewriteResult::kUnchanged) {
        std::cerr << shader.name << ": rejected/changed with feature mask " << features << '\n';
        ++failures;
      }
    }
    if (argc == 2) {
      std::filesystem::create_directories(argv[1]);
      std::ofstream file(std::filesystem::path(argv[1]) / (std::string(shader.name) + ".spv"),
                         std::ios::binary);
      file.write(reinterpret_cast<const char*>(shader.code), shader.bytes);
      if (!file) return 2;
    }
  }
  std::cout << "Production UI, FSR and native resolve shaders: " << checks
            << " feature checks, " << failures << " failures\n";
  return failures ? 1 : 0;
}

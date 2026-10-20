#include "gta4_hack_registry.h"

#include <string>

#include <rex/cvar.h>
#include <rex/logging.h>

#if defined(__ANDROID__)
#include <android/log.h>
#endif

REXCVAR_DEFINE_BOOL(gta4_safe_mode, false, "GTA IV/Stability",
                    "Launch with every registered shortcut, gameplay change and power tweak at "
                    "its plain value (gta4_hack_registry.h); for checking whether a bug comes "
                    "from the port's shortcuts");

namespace gta4::hacks {

// Called once after the command line is parsed, before the renderer plugin
// loads: values for its cvars wait until it registers them, and every value
// set here outranks the configuration file like a launch argument.
void ApplySafeModeIfRequested() {
  if (!REXCVAR_GET(gta4_safe_mode)) {
    REXLOG_INFO("hack registry: {} entries, safe mode off", kRegistry.size());
    return;
  }
  size_t applied = 0;
  std::string skipped;
  for (const Entry& entry : kRegistry) {
    if (entry.safe_value.empty() && entry.cvar != "gta4_native_skip_pixel_shaders") continue;
    if (rex::cvar::SetLaunchFlag(entry.cvar, entry.safe_value)) {
      ++applied;
    } else {
      skipped += std::string(skipped.empty() ? "" : ", ") + std::string(entry.cvar);
    }
  }
  REXLOG_WARN("hack registry: SAFE MODE, {} values applied{}{}", applied,
              skipped.empty() ? "" : "; rejected: ", skipped);
#if defined(__ANDROID__)
  // The log sink is not set up yet this early in startup.
  __android_log_print(ANDROID_LOG_WARN, "LibertyRecomp", "hack registry: SAFE MODE, %zu values applied%s%s",
                      applied, skipped.empty() ? "" : "; rejected: ", skipped.c_str());
#endif
}

}  // namespace gta4::hacks

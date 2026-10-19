#pragma once
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <thread>
#if defined(__ANDROID__)
#include <sched.h>
#endif
#include "native_pipeline_compiler.h"
#include <rex/graphics/gta4_native/mobile_render_policy.h>
namespace rex::graphics::gta4_native {
// One bounded helper. Source references stay alive until the owner joins it.
class NativeTextureDecodeWorker {
 public:
  template <typename Convert> bool Run(uint32_t rows, size_t bytes, bool parallel, const Convert& convert) {
    if (!parallel || rows < 16 || bytes < 128*1024) return convert(0, rows);
    const uint64_t id = next_id_.fetch_add(1, std::memory_order_relaxed);
    const uint32_t split = rows/2;
    if (!worker_.Enqueue(id, [&convert, split] { return convert(0, split); }, true)) return convert(0, rows);
    bool owner_ok;
    try { owner_ok = convert(split, rows); }
    catch (...) { worker_.Take(id, true); throw; }
    const auto helper = worker_.Take(id, true);
    return owner_ok && helper.value_or(false);
  }
 private:
  std::atomic<uint64_t> next_id_{1};
  NativePipelineCompiler<uint64_t, bool, std::hash<uint64_t>> worker_{1};
};
template <typename Convert>
bool RunNativeTextureDecodeRows(uint32_t rows, size_t bytes, bool enabled, const Convert& convert) {
  if (!enabled || !MobileDecodeParallel(std::thread::hardware_concurrency(),rows,bytes)) return convert(0, rows);
#if defined(__ANDROID__)
  // Respect Android cpusets and an explicit affinity choice. Launching a helper
  // inherited onto one busy CPU can be slower than keeping conversion serial.
  cpu_set_t allowed;
  if(sched_getaffinity(0,sizeof(allowed),&allowed)==0&&!MobileDecodeParallel(CPU_COUNT(&allowed),rows,bytes))return convert(0,rows);
#endif
  static NativeTextureDecodeWorker worker;
  return worker.Run(rows, bytes, true, convert);
}
}

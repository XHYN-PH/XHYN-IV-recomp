#pragma once
#include <rex/cvar.h>
#include <atomic>
#include <map>
#include <memory>
#include <mutex>
#include <ostream>
#include <set>
#include <string>
#include <string_view>
#include <vector>

namespace rex::graphics::gta4_native {
// Observe configuration writes without formatting or allocating strings each frame.
class NativeBenchmarkConditions {
  struct Observed {
    std::mutex mutex;
    std::once_flag installed;
    std::string last;
    std::atomic<uint64_t> revision{0};
  };
  struct Item {
    std::string name, initial;
    std::shared_ptr<Observed> observed;
    uint64_t revision = 0;
    bool changed = false;
  };
  static std::shared_ptr<Observed> Observe(const std::string& name) {
    static std::mutex mutex;
    static std::map<std::string, std::shared_ptr<Observed>> values;
    std::shared_ptr<Observed> result;
    { std::lock_guard lock(mutex); auto& entry = values[name]; if (!entry) entry = std::make_shared<Observed>(); result = entry; }
    std::call_once(result->installed, [&] {
      const auto value = rex::cvar::GetFlagByName(name);
      { std::lock_guard lock(result->mutex); result->last = value; }
      rex::cvar::RegisterChangeCallback(name, [result](std::string_view key, std::string_view) {
        const auto actual = rex::cvar::GetFlagByName(key);
        std::lock_guard lock(result->mutex);
        if (actual != result->last) {
          result->last = actual;
          result->revision.fetch_add(1, std::memory_order_release);
        }
      });
    });
    return result;
  }
  static std::string Escape(std::string value) {
    std::string out;
    for (char c : value) {
      if (c == '\\') out += "\\\\";
      else if (c == '\n') out += "\\n";
      else if (c == '\r') out += "\\r";
      else if (c == '\t') out += "\\t";
      else out += c;
    }
    return out;
  }
 public:
  void Start(const std::string& list) {
    items_.clear();
    std::set<std::string> names = {
      "gta4_frame_limit", "resolution", "gta4_native_upscaler", "gta4_effective_upscaling",
      "gta4_session_present_mode", "gta4_draw_distance_dynamic_factor", "gta4_session_paused",
      "android_thermal_guard", "android_gpu_turbo", "data_file_mods"
    };
    for (size_t start = 0; start < list.size();) {
      const auto end = list.find(',', start);
      auto name = list.substr(start, end == std::string::npos ? end : end - start);
      if (!name.empty() && name.size() <= 80) names.insert(std::move(name));
      if (end == std::string::npos) break;
      start = end + 1;
    }
    for (const auto& name : names) {
      if (!rex::cvar::GetFlagInfo(name)) continue;
      Item item;
      item.name = name; item.observed = Observe(name);
      item.revision = item.observed->revision.load(std::memory_order_acquire);
      item.initial = rex::cvar::GetFlagByName(name);
      item.changed = item.revision != item.observed->revision.load(std::memory_order_acquire);
      items_.push_back(std::move(item));
    }
  }
  void Sample() {
    for (auto& item : items_)
      item.changed |= item.observed->revision.load(std::memory_order_acquire) != item.revision;
  }
  bool Mixed() const { for (const auto& item : items_) if (item.changed) return true; return false; }
  void WriteStart(std::ostream& out) const {
    out << "schema=2\nconditions.monitor=registry callbacks sampled each published frame\n";
    for (const auto& item : items_) out << item.name << '=' << Escape(item.initial) << '\n';
  }
  void WriteEnd(std::ostream& out) const {
    out << "conditions.mixed=" << (Mixed() ? "true" : "false") << "\nconditions.changed=";
    bool first = true;
    for (const auto& item : items_) if (item.changed) { if (!first) out << ','; out << item.name; first = false; }
    out << '\n';
  }
 private:
  std::vector<Item> items_;
};
}  // namespace rex::graphics::gta4_native


#pragma once

#include <algorithm>
#include <charconv>
#include <cstdint>
#include <iterator>
#include <string_view>
#include <filesystem>
#include <fstream>
#include <set>
#include <string>
#include <system_error>
#include <vector>

namespace rex::filesystem::data_mods {
inline constexpr const char* kConfig = "xhyn-mod.cfg";
inline constexpr const char* kReport = "data-mod-last-run.txt";
inline constexpr uint64_t kMaxBytes = 256ull * 1024 * 1024;
inline constexpr size_t kMaxFiles = 8192;
inline constexpr size_t kMaxDepth = 25;

struct Mod {
  std::filesystem::path folder;
  bool enabled = true;
  int priority = 0;
  std::string error;
  std::vector<std::string> files;
};
inline std::string Utf8(const std::filesystem::path& path) {
  const auto value = path.generic_u8string();
  return {reinterpret_cast<const char*>(value.data()), value.size()};
}
inline std::string PathKey(std::string value) {
  for (char& c : value) if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
  return value;
}
inline std::string Trim(std::string value) {
  const auto first = value.find_first_not_of(" \t\r\n\f\v");
  if (first == std::string::npos) return {};
  return value.substr(first, value.find_last_not_of(" \t\r\n\f\v") - first + 1);
}
inline std::string Identity(const std::string& value) {
  constexpr char hex[] = "0123456789abcdef";
  std::string out;
  for (unsigned char c : value) { out += hex[c >> 4]; out += hex[c & 15]; }
  return out;
}
inline bool ReadSettings(Mod& mod) {
  const auto path = mod.folder / kConfig;
  const auto status = std::filesystem::symlink_status(path);
  if (status.type() == std::filesystem::file_type::not_found) return true;
  if (!std::filesystem::is_regular_file(status)) return false;
  std::ifstream input(path, std::ios::binary);
  if (!input) return false;
  char bytes[4097];
  input.read(bytes, sizeof(bytes));
  if (input.bad() || input.gcount() > 4096) return false;
  const std::string text(bytes, static_cast<size_t>(input.gcount()));
  for (unsigned char c : text)
    if (c < 32 && c != '\t' && c != '\n' && c != '\r' && c != '\f' && c != '\v') return false;
  std::set<std::string> seen;
  for (size_t start = 0; start <= text.size();) {
    const auto end = text.find('\n', start);
    const auto line = Trim(text.substr(start, end == std::string::npos ? end : end - start));
    if (!line.empty() && line[0] != '#') {
      const auto at = line.find('=');
      if (at == std::string::npos || at == 0) return false;
      const auto key = Trim(line.substr(0, at)), value = Trim(line.substr(at + 1));
      if (!seen.insert(key).second) return false;
      if (key == "enabled") {
        if (value != "true" && value != "false") return false;
        mod.enabled = value == "true";
      } else if (key == "priority") {
        const auto digits = value.starts_with("-") ? value.substr(1) : value;
        if (digits.empty() || digits.size() > 4 ||
            digits.find_first_not_of("0123456789") != std::string::npos) return false;
        const auto result = std::from_chars(value.data(), value.data() + value.size(), mod.priority);
        if (result.ec != std::errc{} || result.ptr != value.data() + value.size() ||
            mod.priority < -1000 || mod.priority > 1000) return false;
      } else return false;
    }
    if (end == std::string::npos) break;
    start = end + 1;
  }
  return true;
}
inline bool OwnMetadata(const std::string& path) {
  if (path == kConfig) return true;
  constexpr std::string_view prefix = "mod-settings-", suffix = ".tmp";
  if (!path.starts_with(prefix) || !path.ends_with(suffix) ||
      path.size() <= prefix.size() + suffix.size()) return false;
  const auto digits = path.substr(prefix.size(), path.size() - prefix.size() - suffix.size());
  return digits.find_first_not_of("0123456789") == std::string::npos;
}
inline std::string LayoutProblem(std::string_view name,std::string_view path) {
  const auto lower=PathKey(std::string(path)),mod=PathKey(std::string(name));
  const auto slash=lower.find('/');const auto top=lower.substr(0,slash);
  const auto dot=lower.rfind('.');const auto ext=dot==std::string::npos?std::string{}:lower.substr(dot);
  if(ext==".asi"||ext==".dll"||ext==".exe"||ext==".cs"||ext==".lua")
    return "PC plugin or script is unsupported: "+std::string(path)+". Moving it into game/ does not make it compatible.";
  if(mod.find("fusionfix")!=std::string::npos||top=="pc"||top=="plugins")
    return "This appears to be a PC package: "+std::string(path)+". This loader supports loose Xbox 360 data overrides, not PC FusionFix/ASI plugins.";
  if(mod=="tlad"||mod=="tbogt")
    return "Episode data needs the launcher’s Reinstall or add episodes installer. Only compatible loose-data overrides belong under this mod’s game/ or update/ folder.";
  return "Unexpected path: "+std::string(path)+". Expected mods/"+std::string(name)+"/game/common/data/handling.dat (or update/). Keep the original Xbox 360 game-relative paths.";
}
inline void Validate(Mod& mod) {
  const auto status = std::filesystem::symlink_status(mod.folder);
  if (!std::filesystem::is_directory(status)) { mod.error = "Mod must be a regular folder."; return; }
  if (!ReadSettings(mod)) { mod.error = "Invalid xhyn-mod.cfg."; return; }
  uint64_t bytes = 0;
  std::set<std::string> seen;
  for (const auto& item : std::filesystem::recursive_directory_iterator(mod.folder)) {
    const auto relative = item.path().lexically_relative(mod.folder);
    const auto path = Utf8(relative);
    const auto item_status = item.symlink_status();
    if (std::filesystem::is_symlink(item_status)) { mod.error = "Symbolic links are unsupported."; break; }
    const size_t depth = static_cast<size_t>(std::distance(relative.begin(), relative.end()));
    if (depth == 1 && OwnMetadata(path) && std::filesystem::is_regular_file(item_status)) continue;
    const auto top = Utf8(*relative.begin());
    if (depth > kMaxDepth || (top != "game" && top != "update")) {
      mod.error = depth>kMaxDepth?"Mod folder nesting is too deep: "+path:LayoutProblem(Utf8(mod.folder.filename()),path); break;
    }
    if (std::filesystem::is_directory(item_status)) continue;
    const auto ext = PathKey(Utf8(item.path().extension()));
    if(ext==".asi"||ext==".dll"||ext==".exe"||ext==".cs"||ext==".lua"){
      mod.error=LayoutProblem(Utf8(mod.folder.filename()),path);break;
    }
    if (!std::filesystem::is_regular_file(item_status) ||
        (ext != ".dat" && ext != ".xml" && ext != ".ide" && ext != ".ipl" &&
         ext != ".txt" && ext != ".meta" && ext != ".gxt")) {
      mod.error = "Unsupported overlay file: " + path+". This loader accepts loose Xbox 360 data; packed RPF/model/texture archives are unsupported."; break;
    }
    if (!seen.insert(PathKey(path)).second) { mod.error = "Duplicate path ignoring case: " + path; break; }
    const auto size = item.file_size();
    if (mod.files.size() >= kMaxFiles || size > kMaxBytes - bytes) {
      mod.error = "Mod exceeds 8192 files or 256 MiB."; break;
    }
    bytes += size;
    mod.files.push_back(path);
  }
  if (mod.error.empty() && mod.files.empty()) mod.error = "No supported loose files.";
  if (!mod.error.empty()) mod.files.clear();
}
inline std::vector<Mod> Scan(const std::filesystem::path& root) {
  const auto status = std::filesystem::symlink_status(root);
  if (status.type() == std::filesystem::file_type::not_found) return {};
  if (!std::filesystem::is_directory(status))
    throw std::filesystem::filesystem_error("Invalid mods folder", root, std::make_error_code(std::errc::invalid_argument));
  std::vector<Mod> mods;
  for (const auto& item : std::filesystem::directory_iterator(root)) {
    std::error_code status_error;
    const auto item_status = item.symlink_status(status_error);
    Mod mod;
    mod.folder = item.path();
    if (status_error) {
      mod.error = status_error.message();
      mods.push_back(std::move(mod));
      continue;
    }
    if (!std::filesystem::is_directory(item_status) && !std::filesystem::is_symlink(item_status)) continue;
    try { Validate(mod); }
    catch (const std::filesystem::filesystem_error& error) { mod.error = error.what(); mod.files.clear(); }
    mods.push_back(std::move(mod));
  }
  std::sort(mods.begin(), mods.end(), [](const Mod& a, const Mod& b) {
    return a.priority != b.priority ? a.priority < b.priority : a.folder.filename().generic_u8string() < b.folder.filename().generic_u8string();
  });
  return mods;
}
}  // namespace rex::filesystem::data_mods

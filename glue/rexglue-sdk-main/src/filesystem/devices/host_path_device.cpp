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

#include <rex/filesystem/devices/host_path_entry.h>

#include <algorithm>
#include <cctype>

#include <rex/filesystem.h>
#include <rex/filesystem/data_mod_policy.h>
#include <exception>
#include <rex/filesystem/devices/host_path_device.h>
#include <rex/logging.h>
#include <rex/math.h>
#include <rex/string/utf8.h>

namespace rex::filesystem {

HostPathDevice::HostPathDevice(const std::string_view mount_path,
                               const std::filesystem::path& host_path, bool read_only,
                               bool allow_share_delete, bool trace_io)
    : Device(mount_path),
      name_("STFS"),
      host_path_(host_path),
      read_only_(read_only),
      allow_share_delete_(allow_share_delete),
      trace_io_(trace_io) {}

HostPathDevice::~HostPathDevice() = default;

bool HostPathDevice::Initialize() {
  if (!std::filesystem::exists(host_path_)) {
    if (!read_only_) {
      // Create the path.
      std::filesystem::create_directories(host_path_);
    } else {
      REXFS_ERROR("Host path does not exist");
      return false;
    }
  }

  auto root_entry = new HostPathEntry(this, nullptr, "", host_path_);
  root_entry->attributes_ = kFileAttributeDirectory;
  root_entry_ = std::unique_ptr<Entry>(root_entry);
  PopulateEntry(root_entry);

  return true;
}

void HostPathDevice::Dump(string::StringBuffer* string_buffer) {
  auto global_lock = global_critical_region_.Acquire();
  root_entry_->Dump(string_buffer, 0);
}

Entry* HostPathDevice::ResolvePath(const std::string_view path) {
  // The filesystem will have stripped our prefix off already, so the path will
  // be in the form:
  // some\PATH.foo
  auto* resolved = root_entry_->ResolvePath(path);
  if (resolved) {
    return resolved;
  }

  // Fallback to a lazy case-insensitive host lookup when an entry is missing
  // from the in-memory tree (for example because casing differs on Linux).
  auto* current_entry = static_cast<HostPathEntry*>(root_entry_.get());
  for (const auto& part : rex::string::utf8_split_path(path)) {
    if (part.empty()) {
      continue;
    }

    auto* child = current_entry->GetChild(part);
    if (!child) {
      auto child_infos = rex::filesystem::ListFiles(current_entry->host_path());
      auto match = std::find_if(child_infos.begin(), child_infos.end(), [&](const auto& info) {
        return rex::string::utf8_equal_case(rex::path_to_utf8(info.name), part);
      });
      if (match == child_infos.end()) {
        return nullptr;
      }

      auto new_child = HostPathEntry::Create(this, current_entry,
                                             current_entry->host_path() / match->name, *match);
      if (!new_child) {
        return nullptr;
      }
      child = new_child;
      current_entry->children_.push_back(std::unique_ptr<Entry>(new_child));
    }

    current_entry = static_cast<HostPathEntry*>(child);
  }

  return current_entry;
}

bool HostPathDevice::AddReadOnlyOverlay(const std::filesystem::path& path) {
  return AddReadOnlyOverlays({{this, path}});
}

bool HostPathDevice::AddReadOnlyOverlays(const std::vector<Overlay>& overlays) {
  std::vector<OverlayEdit> edits;
  uint32_t files = 0;
  uint64_t bytes = 0;
  bool success = true;
  try {
    for (const auto& overlay : overlays) {
      if (!overlay.device || !overlay.device->read_only_ || !overlay.device->root_entry_ ||
          !overlay.device->MergeReadOnlyOverlay(
              static_cast<HostPathEntry*>(overlay.device->root_entry_.get()),
              overlay.path, 1, files, bytes, &edits)) {
        success = false;
        break;
      }
    }
  } catch (const std::exception&) {
    success = false;
  }
  if (!success) {
    for (auto edit = edits.rbegin(); edit != edits.rend(); ++edit) {
      if (edit->previous) edit->parent->children_[edit->index] = std::move(edit->previous);
      else edit->parent->children_.pop_back();
    }
  }
  return success;
}

bool HostPathDevice::MergeReadOnlyOverlay(
    HostPathEntry* parent, const std::filesystem::path& path, uint32_t depth,
    uint32_t& files, uint64_t& bytes, std::vector<OverlayEdit>* edits) {
  const auto root_status = std::filesystem::symlink_status(path);
  if (depth > data_mods::kMaxDepth || !std::filesystem::is_directory(root_status)) return false;
  for (const auto& item : std::filesystem::directory_iterator(path)) {
    const auto status = item.symlink_status();
    if (depth >= data_mods::kMaxDepth || std::filesystem::is_symlink(status)) return false;
    const bool directory = std::filesystem::is_directory(status);
    if (!directory) {
      const auto extension = data_mods::PathKey(data_mods::Utf8(item.path().extension()));
      if (!std::filesystem::is_regular_file(status) ||
          (extension != ".dat" && extension != ".xml" && extension != ".ide" &&
           extension != ".ipl" && extension != ".txt" && extension != ".meta" &&
           extension != ".gxt")) return false;
      const auto size = item.file_size();
      if (files >= data_mods::kMaxFiles || size > data_mods::kMaxBytes - bytes) return false;
      ++files;
      bytes += size;
    }
    const auto name = rex::path_to_utf8(item.path().filename());
    auto existing = std::find_if(parent->children_.begin(), parent->children_.end(),
        [&](const auto& entry) { return rex::string::utf8_equal_case(entry->name(), name); });
    if (existing != parent->children_.end()) {
      const bool was_directory = ((*existing)->attributes() & kFileAttributeDirectory) != 0;
      if (was_directory != directory) return false;
      if (directory) {
        if (!MergeReadOnlyOverlay(static_cast<HostPathEntry*>(existing->get()), item.path(),
                                  depth + 1, files, bytes, edits)) return false;
        continue;
      }
    }
    rex::filesystem::FileInfo info;
    if (!rex::filesystem::GetInfo(item.path(), &info)) return false;
    info.name = item.path().filename();
    auto child = std::unique_ptr<HostPathEntry>(HostPathEntry::Create(this, parent, item.path(), info));
    if (!child) return false;
    // Build unattached subtrees first; their children must never enter the shared journal.
    if (directory && !MergeReadOnlyOverlay(child.get(), item.path(), depth + 1, files, bytes, nullptr))
      return false;
    const size_t index = static_cast<size_t>(existing - parent->children_.begin());
    const bool replace = existing != parent->children_.end();
    // Reserve before journaling: a failed allocation must not journal an unapplied append.
    if (!replace && parent->children_.size() == parent->children_.capacity())
      parent->children_.reserve(std::max<size_t>(8, parent->children_.size() * 2));
    if (edits) {
      edits->push_back({parent, index, nullptr});
      if (replace) edits->back().previous = std::move(parent->children_[index]);
    }
    if (replace) parent->children_[index] = std::move(child);
    else parent->children_.push_back(std::move(child));
  }
  return true;
}

void HostPathDevice::PopulateEntry(HostPathEntry* parent_entry) {
  auto child_infos = rex::filesystem::ListFiles(parent_entry->host_path());
  for (auto& child_info : child_infos) {
    auto child = HostPathEntry::Create(this, parent_entry,
                                       parent_entry->host_path() / child_info.name, child_info);
    parent_entry->children_.push_back(std::unique_ptr<Entry>(child));

    if (child_info.type == rex::filesystem::FileInfo::Type::kDirectory) {
      PopulateEntry(child);
    }
  }
}

}  // namespace rex::filesystem

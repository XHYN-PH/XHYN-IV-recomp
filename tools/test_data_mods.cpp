#include <rex/filesystem/data_mod_policy.h>
#include <rex/filesystem/devices/host_path_device.h>
#include <rex/filesystem/devices/host_path_entry.h>
#include <cassert>
#include <filesystem>
#include <fstream>
#include <iostream>

namespace fs = std::filesystem;
namespace policy = rex::filesystem::data_mods;
using rex::filesystem::HostPathDevice;
using rex::filesystem::HostPathEntry;

static void Put(const fs::path& path, const std::string& text) {
  fs::create_directories(path.parent_path());
  std::ofstream stream(path); stream << text; stream.close(); assert(stream.good());
}
static fs::path Host(HostPathDevice& device, const std::string& name) {
  auto* entry = device.ResolvePath(name);
  return entry ? static_cast<HostPathEntry*>(entry)->host_path() : fs::path{};
}
int main(int argc, char** argv) {
  assert(policy::LayoutProblem("GTAIV.EFLC.FusionFix","IV").find("PC package")!=std::string::npos);
  assert(policy::LayoutProblem("TBoGT","common").find("Reinstall or add episodes")!=std::string::npos);
  assert(policy::LayoutProblem("TLAD","common").find("Reinstall or add episodes")!=std::string::npos);
  assert(policy::LayoutProblem("common","data").find("game/common/data/handling.dat")!=std::string::npos);
  assert(policy::LayoutProblem("update","plugins").find("PC package")!=std::string::npos);
  assert(policy::LayoutProblem("plugins","root.asi").find("PC plugin")!=std::string::npos);
  assert(argc > 1);
  const fs::path root = argv[1];
  fs::remove_all(root); fs::create_directories(root);
  const auto mods = root / "mods";
  Put(mods / "A/game/common/handling.dat", "A");
  Put(mods / "B/game/common/handling.dat", "B");
  Put(mods / "B/xhyn-mod.cfg", "enabled=false\npriority=10\n");
  Put(mods / "Bad/game/plugin.asi", "bad");
  Put(mods / "Config/game/test.dat", "valid"); Put(mods / "Config/xhyn-mod.cfg", "priority=oops\n");
  Put(mods / "Duplicate/game/same.dat", "one"); Put(mods / "Duplicate/game/SAME.dat", "two");
  fs::create_directory_symlink(mods / "A", mods / "Link");
  const auto found = policy::Scan(mods);
  size_t accepted = 0, rejected = 0;
  for (const auto& mod : found) {
    if (!mod.error.empty()) ++rejected;
    else if (mod.enabled) ++accepted;
  }
  assert(accepted == 1 && rejected == 4);
  assert(found.back().folder.filename() == "B" && !found.back().enabled);
  assert(policy::Identity("A") == "41");
  assert(policy::Scan(root / "missing").empty());

  const auto game = root / "game", update = root / "update";
  Put(game / "common/handling.dat", "original");
  Put(update / "common/timecyc.dat", "original");
  HostPathDevice game_device("\\Device\\Game", game, true);
  HostPathDevice update_device("\\Device\\Update", update, true);
  assert(game_device.Initialize() && update_device.Initialize());
  assert(game_device.AddReadOnlyOverlay(mods / "A/game"));
  const auto* previous = game_device.ResolvePath("common\\handling.dat");
  assert(Host(game_device, "common\\handling.dat") == mods / "A/game/common/handling.dat");

  const auto overlay = root / "transaction";
  Put(overlay / "game/common/handling.dat", "replacement");
  Put(overlay / "game/new/deep/new.dat", "new");
  Put(overlay / "update/common/timecyc.dat", "replacement");
  Put(overlay / "update/common/plugin.asi", "invalid");
  assert(!HostPathDevice::AddReadOnlyOverlays({{&game_device, overlay / "game"}, {&update_device, overlay / "update"}}));
  assert(game_device.ResolvePath("common\\handling.dat") == previous);
  assert(!game_device.ResolvePath("new\\deep\\new.dat"));
  assert(Host(update_device, "common\\timecyc.dat") == update / "common/timecyc.dat");
  fs::remove(overlay / "update/common/plugin.asi");
  assert(HostPathDevice::AddReadOnlyOverlays({{&game_device, overlay / "game"}, {&update_device, overlay / "update"}}));
  assert(Host(game_device, "common\\handling.dat") == overlay / "game/common/handling.dat");
  assert(Host(game_device, "new\\deep\\new.dat") == overlay / "game/new/deep/new.dat");
  assert(Host(update_device, "common\\timecyc.dat") == overlay / "update/common/timecyc.dat");

  Put(root / "collision/common/handling.dat/child.dat", "bad");
  assert(!game_device.AddReadOnlyOverlay(root / "collision"));
  assert(Host(game_device, "common\\handling.dat") == overlay / "game/common/handling.dat");
  Put(root / "file-collision/new.dat", "file");
  Put(root / "file-collision/new", "file-versus-directory");
  assert(!game_device.AddReadOnlyOverlay(root / "file-collision"));
  assert(!game_device.ResolvePath("new.dat"));
  assert(Host(game_device, "new\\deep\\new.dat") == overlay / "game/new/deep/new.dat");

  Put(mods / "Large/game/large.dat", "");
  fs::resize_file(mods / "Large/game/large.dat", policy::kMaxBytes + 1);
  Put(mods / "MetaDirectory/xhyn-mod.cfg/child.dat", "bad");
  Put(mods / "HugeConfig/game/test.dat", "valid");
  Put(mods / "HugeConfig/xhyn-mod.cfg", std::string(4097, 'x'));
  Put(mods / "Control/game/test.dat", "valid");
  Put(mods / "Control/xhyn-mod.cfg", std::string("enabled=") + char(1) + "true\n");
  fs::path deep = "game";
  for (int i = 0; i < 23; ++i) deep /= "d";
  Put(mods / "DeepValid" / deep / "valid.dat", "valid");
  Put(mods / "TooDeep" / deep / "d/bad.dat", "invalid");
  for (const auto& mod : policy::Scan(mods))
    if (mod.folder.filename() == "Large" || mod.folder.filename() == "MetaDirectory" ||
        mod.folder.filename() == "HugeConfig" || mod.folder.filename() == "Control" ||
        mod.folder.filename() == "TooDeep") assert(!mod.error.empty());
    else if (mod.folder.filename() == "DeepValid") assert(mod.error.empty());
  fs::remove_all(root);
  std::cout << "Production mod policy and cross-device VFS transaction regressions passed\n";
}

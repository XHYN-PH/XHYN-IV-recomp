#include <rex/system/achievement_manager.h>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <cassert>
#include <filesystem>
#include <fstream>
#include <iterator>
namespace rex{spdlog::logger* GetLoggerRaw(LogCategoryId){return nullptr;}LogCategoryId RegisterLogCategory(const char*){return LogCategoryId(0);}}
int main(int argc,char** argv){
  assert(argc>1);const auto root=std::filesystem::path(argv[1]);
  std::filesystem::remove_all(root);std::filesystem::create_directories(root);
  rex::system::AchievementManager manager;
  rex::system::AchievementInfo a;a.id=1;a.label="A \"quoted\" name\nnext";a.flags=0;a.gamerscore=10;
  rex::system::AchievementInfo visible;visible.id=2;visible.label="Public";visible.flags=8;
  manager.RegisterAchievement(a);manager.RegisterAchievement(visible);manager.SetUnlockSavePath(root/"545407F2.toml");manager.SetCatalogExportPath(root/"catalog.json");
  int notifications=0,unlocks=0;
  manager.RegisterUnlockCallback([&](const auto&){++unlocks;});
  manager.RegisterNotificationCallback([&](const auto&){++notifications;});
  assert(rex::cvar::SetFlagByName("achievement_notifications","false"));
  assert(manager.UnlockAchievement(1,rex::system::AchievementNotification::kShow)==rex::system::AchievementUnlockResult::kUnlocked);
  const auto time=manager.GetUnlockTime(1);assert(time>0&&unlocks==1&&notifications==0);
  assert(manager.UnlockAchievement(1)==rex::system::AchievementUnlockResult::kAlreadyUnlocked);
  assert(unlocks==1&&time==manager.GetUnlockTime(1));
  assert(manager.UnlockAchievement(777)==rex::system::AchievementUnlockResult::kUnknownAchievement);
  std::ifstream file(root/"catalog.json");std::string json((std::istreambuf_iterator<char>(file)),{});
  assert(json.find("\\\"quoted\\\"")!=json.npos&&json.find("\\u000a")!=json.npos);
  assert(json.find("\"hidden\":true")!=json.npos&&json.find("\"unlocked\":true")!=json.npos);
  const auto public_entry=json.find("\"id\":2,");assert(public_entry!=json.npos);
  assert(json.substr(public_entry,json.find('}',public_entry)-public_entry).find("\"hidden\":false")!=json.npos);
  rex::system::AchievementManager restored;restored.RegisterAchievement(a);restored.SetUnlockSavePath(root/"545407F2.toml");restored.LoadUnlockState();
  assert(restored.IsUnlocked(1)&&restored.GetUnlockTime(1)==time);
  assert(rex::cvar::SetFlagByName("achievement_notifications","true"));
  assert(manager.ShowAchievementNotification(1)&&notifications==1&&unlocks==1);
}

#include <rex/system/xam/user_profile.h>
#include <cassert>
#include <iostream>
#include <memory>
#include <type_traits>
#include <vector>

using Profile = rex::system::xam::UserProfile;
static_assert(std::has_virtual_destructor_v<Profile::Setting>);
struct ObservedBinary final : Profile::BinarySetting {
  explicit ObservedBinary(int& destroyed) : BinarySetting(1, std::vector<uint8_t>(1000, 7)), destroyed(destroyed) {}
  ~ObservedBinary() override { ++destroyed; }
  int& destroyed;
};
int main() {
  std::vector<std::unique_ptr<Profile::Setting>> settings;
  int destroyed = 0;
  for (int i = 0; i < 128; ++i) {
    settings.push_back(std::make_unique<ObservedBinary>(destroyed));
    settings.push_back(std::make_unique<Profile::UnicodeSetting>(2, std::u16string(256, u'a')));
    settings.push_back(std::make_unique<Profile::Int32Setting>(3, i));
  }
  for (auto& setting : settings) {
    auto previous = std::move(setting);
    setting = std::make_unique<Profile::BinarySetting>(4, std::vector<uint8_t>(512, 9));
    previous.reset();
    assert(setting->Serialize().size() == 512);
  }
  assert(destroyed == 128);
  settings.clear();
  std::cout << "Profile setting production lifetime/replacement regression passed\n";
}

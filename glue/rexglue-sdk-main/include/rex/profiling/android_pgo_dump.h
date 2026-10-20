#pragma once
#if defined(LIBERTY_PGO_GENERATE)
#include <android/log.h>
#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <string>
#include <thread>
extern "C" int __llvm_profile_write_file();
extern "C" void __llvm_profile_set_filename(const char*);
namespace rex::profiling {
class AndroidPgoDumper {
 public:
  explicit AndroidPgoDumper(const char* component) {
    const char* data = std::getenv("XDG_DATA_HOME");
    if (!data || !*data) return;
    const auto directory = std::filesystem::path(data)/"pgo";
    std::error_code error; std::filesystem::create_directories(directory,error);
    if (error) return;
    const auto stamp = std::chrono::system_clock::now().time_since_epoch().count();
    filename_ = (directory/(std::string(component)+"-%p-"+std::to_string(stamp)+".profraw")).string();
    __llvm_profile_set_filename(filename_.c_str());
    std::thread([] {
      for (;;) {
        std::this_thread::sleep_for(std::chrono::seconds(60));
        __android_log_print(ANDROID_LOG_INFO,"LibertyRecomp","pgo: profile exported (%d)",__llvm_profile_write_file());
      }
    }).detach();
  }
 private: std::string filename_;
};
}
#endif

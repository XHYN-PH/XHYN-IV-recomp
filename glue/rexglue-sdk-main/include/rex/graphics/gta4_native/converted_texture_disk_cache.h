#pragma once
#include <xxhash.h>
#include <algorithm>
#include <array>
#include <condition_variable>
#include <cstring>
#include <cstdio>
#include <deque>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <span>
#include <thread>
#include <vector>

namespace rex::graphics::gta4_native {
// Optional CPU-decoded immutable subresources only. GPU-produced surfaces and
// GPU-detile paths never enter this cache. Writes are bounded and asynchronous.
class ConvertedTextureDiskCache {
 public:
  struct Key {uint64_t input_lo=0,input_hi=0,metadata_lo=0,metadata_hi=0;};
  explicit ConvertedTextureDiskCache(std::filesystem::path root):root_(std::move(root)),worker_([this]{Run();}){}
  ~ConvertedTextureDiskCache(){ {std::lock_guard lock(mutex_);stop_=true;}condition_.notify_all();worker_.join();}
  static Key Identify(std::span<const uint8_t> input,std::span<const uint8_t> metadata) {
    auto a=XXH3_128bits(input.data(),input.size()),b=XXH3_128bits(metadata.data(),metadata.size());
    return {a.low64,a.high64,b.low64,b.high64};
  }
  bool Read(Key key,std::span<uint8_t> output) const {
    if(output.size()<65536||output.size()>8388608)return false;
    try {
      std::ifstream file(Path(key),std::ios::binary|std::ios::ate);
      if(!file||file.tellg()!=std::streamoff(sizeof(Header)+output.size()))return false;
      Header header{};file.seekg(0);file.read(reinterpret_cast<char*>(&header),sizeof(header));
      if(!file||header.magic!=kMagic||header.size!=output.size()||std::memcmp(&header.key,&key,sizeof(key)))return false;
      std::vector<uint8_t> data(output.size());file.read(reinterpret_cast<char*>(data.data()),data.size());
      if(!file||XXH3_64bits(data.data(),data.size())!=header.checksum)return false;
      std::memcpy(output.data(),data.data(),data.size());return true;
    }catch(const std::exception&){return false;}
  }
  bool Write(Key key,std::span<const uint8_t> bytes) {
    if(bytes.size()<65536||bytes.size()>8388608)return false;
    std::lock_guard lock(mutex_);
    if(stop_||jobs_.size()>=4||queued_bytes_+bytes.size()>16777216)return false;
    for(const auto& job:jobs_)if(!std::memcmp(&job.key,&key,sizeof(key)))return false;
    jobs_.push_back({key,{bytes.begin(),bytes.end()}});queued_bytes_+=bytes.size();condition_.notify_one();return true;
  }
 private:
  static constexpr uint64_t kMagic=0x315854344e594858ull; // schema/decoder version 1
  struct Header {uint64_t magic=kMagic,size=0,checksum=0;Key key{};};
  struct Job {Key key;std::vector<uint8_t> data;};
  std::filesystem::path Path(Key k)const {
    char name[100];std::snprintf(name,sizeof(name),"%016llx%016llx-%016llx%016llx.bin",
        (unsigned long long)k.input_hi,(unsigned long long)k.input_lo,
        (unsigned long long)k.metadata_hi,(unsigned long long)k.metadata_lo);return root_/name;
  }
  void Run() {
    for(;;) {
      Job job;
      {std::unique_lock lock(mutex_);condition_.wait(lock,[&]{return stop_||!jobs_.empty();});
       if(stop_)return;job=std::move(jobs_.front());jobs_.pop_front();queued_bytes_-=job.data.size();}
      try {
        std::error_code error;std::filesystem::create_directories(root_,error);if(error)continue;
        auto path=Path(job.key),temp=path;temp += ".tmp";
        Header header;header.key=job.key;header.size=job.data.size();header.checksum=XXH3_64bits(job.data.data(),job.data.size());
        {std::ofstream file(temp,std::ios::binary|std::ios::trunc);
         file.write(reinterpret_cast<const char*>(&header),sizeof(header));file.write(reinterpret_cast<const char*>(job.data.data()),job.data.size());file.flush();
         if(!file){std::filesystem::remove(temp,error);continue;}}
        std::filesystem::rename(temp,path,error);if(error){std::filesystem::remove(temp,error);continue;}
        uint64_t bytes=0;std::vector<std::pair<std::filesystem::file_time_type,std::filesystem::path>> files;
        for(const auto& entry:std::filesystem::directory_iterator(root_)) {
          if(entry.is_regular_file()&&entry.path().extension()==".bin") {bytes+=entry.file_size();files.emplace_back(entry.last_write_time(),entry.path());}
        }
        std::sort(files.begin(),files.end());size_t count=files.size();
        for(const auto& [time,file]:files) {
          if(bytes<=268435456ull&&count<=512)break;
          const auto size=std::filesystem::file_size(file,error);if(!error&&std::filesystem::remove(file,error)){bytes-=size;--count;}
        }
      }catch(const std::exception&){} // A cache failure never rejects a texture.
    }
  }
  std::filesystem::path root_;mutable std::mutex mutex_;std::condition_variable condition_;
  std::deque<Job> jobs_;size_t queued_bytes_=0;bool stop_=false;std::thread worker_;
};
}

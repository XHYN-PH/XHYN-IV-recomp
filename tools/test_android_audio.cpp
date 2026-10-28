// Exercise the production SDL callback with a deterministic device boundary.
#include <rex/audio/sdl/sdl_audio_driver.h>
#include <rex/cvar.h>
#include <rex/perf/counter.h>
#include <cassert>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <vector>

static bool fail_put=false;
static std::vector<float> output;
extern "C" {
bool SDL_PutAudioStreamData(SDL_AudioStream*,const void* data,int len){
  output.assign(static_cast<const float*>(data),static_cast<const float*>(data)+len/sizeof(float));
  return !fail_put;
}
const char* SDL_GetError(){return "injected device failure";}
bool SDL_LockAudioStream(SDL_AudioStream*){return true;}
bool SDL_UnlockAudioStream(SDL_AudioStream*){return true;}
bool SDL_ClearAudioStream(SDL_AudioStream*){return true;}
bool SDL_PauseAudioStreamDevice(SDL_AudioStream*){return true;}
bool SDL_ResumeAudioStreamDevice(SDL_AudioStream*){return true;}
void* SDL_malloc(size_t n){return std::malloc(n);}
void SDL_free(void* p){std::free(p);}
}
// Guest memory translation is outside this callback harness; fail if reached.
namespace rex::memory {const BaseHeap* Memory::LookupHeap(uint32_t)const{assert(false&&"unexpected guest translation");return nullptr;}}
namespace rex::thread {void Sleep(std::chrono::microseconds){assert(false&&"unexpected silent fallback");}}
namespace rex::perf {void SetCounter(CounterId,int64_t){}}
struct Credits final:rex::thread::Semaphore {
  int count=0;void* native_handle()const override{return nullptr;}
  bool Release(int n,int*)override{count+=n;return true;}
};
struct Driver final:rex::audio::sdl::SDLAudioDriver {
  explicit Driver(Credits* c):SDLAudioDriver(nullptr,c){sdl_device_channels_=2;sdl_stream_=reinterpret_cast<SDL_AudioStream*>(1);}
  ~Driver(){while(!frames_queued_.empty()){delete[] frames_queued_.front();frames_queued_.pop();}while(!frames_unused_.empty()){delete[] frames_unused_.top();frames_unused_.pop();}sdl_stream_=nullptr;}
  void Feed(){auto* data=new float[1536];for(unsigned i=0;i<1536;++i){uint32_t be=0x0000803f;std::memcpy(data+i,&be,4);}frames_queued_.push(data);}
  void Callback(){SDLCallback(this,sdl_stream_,256*2*sizeof(float),0);}
  size_t queued()const{return frames_queued_.size();}
  unsigned empty()const{return empty_fills_;}unsigned refill()const{return refill_fills_;}
  unsigned filled()const{return filled_blocks_;}unsigned target()const{return rebuffer_policy_.target_blocks();}
};
int main(){
  Credits c;Driver d(&c);
  rex::cvar::SetFlagByName("audio_adaptive_buffer","true");
  d.Callback();assert(d.empty()==1&&c.count==0);
  d.Feed();d.Callback();assert(d.refill()==1&&d.queued()==1&&c.count==0&&output[0]==0);
  d.Feed();d.Callback();assert(d.queued()==1&&c.count==1&&d.filled()==1&&output[0]>.99f);
  d.Callback();assert(d.queued()==0&&c.count==2);
  d.Callback();assert(d.target()==3&&d.empty()==2);
  d.Feed();d.Feed();d.Callback();assert(d.refill()==2&&d.queued()==2&&c.count==2);
  d.Feed();fail_put=true;d.Callback();assert(d.queued()==2&&c.count==3); // consumed frame returns credit even on SDL failure
  fail_put=false;d.Pause();assert(!d.queued()&&!d.empty()&&!d.refill()&&!d.filled()&&d.target()==2);
  d.Resume();d.Feed();d.Callback();assert(d.refill()==1&&c.count==3);
  rex::cvar::SetFlagByName("audio_adaptive_buffer","false");d.Callback();assert(!d.queued()&&c.count==4);
  rex::cvar::SetFlagByName("audio_mute","true");d.Feed();d.Callback();assert(c.count==5&&output[0]==0);
  std::cout<<"Production SDL callback: recovery, silence classification, pause reset, mute and failed-device credits passed\n";
}

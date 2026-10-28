#define XXH_INLINE_ALL
#include <cassert>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <thread>
#include <rex/graphics/gta4_native/mobile_frame_stats.h>
#include <rex/graphics/gta4_native/mobile_hitch_trace.h>
#include <rex/graphics/gta4_native/mobile_idle_texture_budget.h>
#include <rex/audio/audio_rebuffer_policy.h>
#include <rex/filesystem/expected_missing_device.h>
#include "graphics/gta4_native/native_pipeline_lookahead.h"
#include <rex/graphics/gta4_native/upscaling_policy.h>
#include <rex/graphics/gta4_native/mobile_memory.h>
#include <rex/graphics/gta4_native/mobile_render_policy.h>
#include "gta4_aspect_policy.h"
#include "gta4_cheat_policy.h"
#include "input/phone_gyro_policy.h"
#include "input/touch_context_transition.h"
#include "android/android_power_policy.h"
#include <rex/graphics/gta4_native/immutable_snapshot.h>
#include <rex/graphics/gta4_native/converted_texture_disk_cache.h>
using namespace rex::graphics::gta4_native;
int main(int argc,char** argv) {
  {
    MobilePresentClock clock;
    assert(!clock.Observe(10,20,true,false,0).interval_ns);
    auto gap=clock.Observe(40,60,true,false,0);assert(gap.interval_ns==40&&gap.outside_publish_ns==20);
    gap=clock.Observe(600,620,true,false,2);assert(gap.boundary&&!gap.interval_ns); // open + close between presents
    assert(clock.Observe(640,660,true,false,2).interval_ns==40);
    assert(clock.Observe(700,720,true,true,3).boundary);
    assert(clock.Observe(740,760,true,false,4).boundary);
    assert(!clock.Observe(1,2,true,false,4).interval_ns); // clock reset
    MobileHitchHistory h;
    for(unsigned i=1;i<=80;++i)h.Record({i*1'000'000'000ull,1});
    assert(h.Snapshot().size()==64&&h.Snapshot().front().end_ns==17'000'000'000ull);
    assert(h.Record({81'000'000'000ull,120'000'000}));
    assert(!h.Record({82'000'000'000ull,120'000'000}));
    assert(h.Record({83'000'000'000ull,120'000'000}));
    h.Reset();assert(h.Snapshot().empty());
    // Rolling captures keep working beyond the bounded 16 output slots.
    for(unsigned i=0;i<20;++i)assert(h.Record({(85ull+i*2)*1'000'000'000ull,120'000'000}));
    assert(h.sequence()==22);
  }
  {
    MobileIdleTextureBudget<2> b;uint64_t a=0,c=0,d=0;
    assert(b.Reserve(0,80,100,150,a));assert(!b.Reserve(0,30,100,150,c));
    assert(b.Reserve(1,70,100,150,c));assert(b.bytes()==150);
    assert(!b.Reserve(1,1,100,150,d));
    assert(b.Reserve(0,80,100,150,a)&&b.bytes()==150);
    b.Release(0,a);b.Release(0,a);assert(b.bytes()==70&&!a); // use and retirement are idempotent
    assert(!b.Reserve(1,70,50,150,c)&&!c&&b.bytes()==0); // shrinking/pressure allowance
    assert(!b.Reserve(0,UINT64_MAX,UINT64_MAX,1,d));
    assert(!b.Reserve(2,1,1,1,d));
  }
  {
    using namespace rex::audio;AudioRebufferPolicy p;
    assert(p.Next(0,true)==AudioQueueDecision::kEmpty);
    assert(p.Next(1,true)==AudioQueueDecision::kRefill);
    assert(p.Next(2,true)==AudioQueueDecision::kPlay);
    for(unsigned target=3;target<=6;++target){
      assert(p.Next(0,true)==AudioQueueDecision::kEmpty);assert(p.target_blocks()==target);
      for(int i=0;i<10;++i)assert(p.Next(0,true)==AudioQueueDecision::kEmpty);
      assert(p.target_blocks()==target);
      assert(p.Next(target-1,true)==AudioQueueDecision::kRefill);
      assert(p.Next(target,true)==AudioQueueDecision::kPlay);
    }
    assert(p.Next(0,true)==AudioQueueDecision::kEmpty&&p.target_blocks()==6);
    for(unsigned i=0;i<375;++i)assert(p.Next(6,true)==AudioQueueDecision::kPlay);
    assert(p.target_blocks()==2);p.Reset();assert(p.Next(1,false)==AudioQueueDecision::kPlay);
  }
  {
    using rex::filesystem::IsOptionalXboxCachePath;
    for(auto path:{"cache:\\a","CACHE1:\\","Cache0:file"})assert(IsOptionalXboxCachePath(path));
    for(auto path:{"game:cache/a","cache2:a","cache1x:a","cache/a","update:file"})assert(!IsOptionalXboxCachePath(path));
    NativePreparedShaderPairs pairs;assert(!pairs.Contains(1,2));pairs.Publish(1,2);assert(pairs.Contains(1,2)&&!pairs.Contains(1,3));
    pairs.Publish(1025,2);assert(!pairs.Contains(1,2)&&pairs.Contains(1025,2)); // collision cannot claim a different pair ready
  }
  {
    enum class Mode { foot, vehicle, phone, minigame, hidden };
    gta4::input::TouchContextTransition<Mode> transition;
    assert(transition.Update(Mode::vehicle,Mode::foot,10,20,true)==Mode::foot);
    assert(transition.Update(Mode::vehicle,Mode::foot,29,20,true)==Mode::foot);
    assert(transition.Update(Mode::vehicle,Mode::foot,30,20,true)==Mode::vehicle);
    assert(transition.Update(Mode::phone,Mode::vehicle,31,20,true)==Mode::vehicle);
    assert(transition.Update(Mode::phone,Mode::vehicle,51,20,true)==Mode::phone);
    assert(transition.Update(Mode::minigame,Mode::phone,52,20,true)==Mode::phone);
    assert(transition.Update(Mode::hidden,Mode::phone,53,1000,false)==Mode::hidden);
    assert(transition.Update(Mode::foot,Mode::vehicle,54,0,true)==Mode::foot);
    assert(transition.Update(Mode::vehicle,Mode::foot,55,-20,true)==Mode::vehicle);
    // A changed candidate restarts the hold; a clock reset cannot underflow.
    assert(transition.Update(Mode::phone,Mode::foot,60,20,true)==Mode::foot);
    assert(transition.Update(Mode::vehicle,Mode::foot,61,20,true)==Mode::foot);
    assert(transition.Update(Mode::vehicle,Mode::foot,1,20,true)==Mode::foot);
  }
  {
    gta4::input::PhoneGyroBuffer gyro;
    gyro.Publish(1,2,100);gyro.Publish(3,-1,110);auto d=gyro.Consume(120,true);assert(d.active&&d.x==4&&d.y==1);assert(!gyro.Consume(121,true).active);
    gyro.Publish(1,1,200);assert(!gyro.Consume(351,true).active);
    gyro.Publish(4,5,400);assert(!gyro.Consume(401,false).active&&!gyro.Consume(402,true).active);
    gyro.Publish(NAN,2,500);assert(!gyro.Consume(501,true).active);
    gyro.Publish(10000,-10000,600);d=gyro.Consume(601,true);assert(d.x==128&&d.y==-128);
    gyro.Publish(1,1,700);gyro.Reset();assert(!gyro.Consume(701,true).active);
    gyro.Publish(1,1,800);assert(!gyro.Consume(799,true).active);
  }

  {
    using gta4::android_power::ClassifyThermals;
    assert(ClassifyThermals(.6,3,40,65,50).hot&&!ClassifyThermals(.6,3,40,65,50).cool);
    assert(ClassifyThermals(1.0,0,40,65,50).hot);
    assert(ClassifyThermals(.7,1,70,65,50).cool);
    assert(!ClassifyThermals(.8,0,40,65,50).cool);
    assert(ClassifyThermals(NAN,3,NAN,65,50).hot);
    assert(ClassifyThermals(NAN,-1,70,65,50).hot);
    assert(ClassifyThermals(NAN,-1,45,65,50).cool);
    assert(!ClassifyThermals(NAN,-1,NAN,65,50).hot&&!ClassifyThermals(NAN,-1,NAN,65,50).cool);
  }
  {
    using namespace gta4::cheats;
    unsigned supported=0;
    for(uint32_t id=0;id<100;++id)supported+=SupportedIndex(id);
    assert(supported==15&&!SupportedIndex(UINT32_MAX));
    for(uint32_t id=100;id<=109;++id)assert(SupportedIndex(id)&&!Name(id).empty()&&!Handler(id).empty());assert(!SupportedIndex(110));
    gta4::input::TouchContextSnapshot context;
    Request request{2,7,10000};
    context.epoch=7;
    assert(!CanQueue(context)&&Decide(request,context,1,7)==Decision::kWait);
    context.valid=context.playing=true;
    assert(CanQueue(context)&&Decide(request,context,1,7)==Decision::kWait);
    context.gameplay_allowed=true;
    assert(Decide(request,context,1,7)==Decision::kExecute);
    context.loading=true;assert(!CanQueue(context)&&Decide(request,context,1,7)==Decision::kCancel);
    context.loading=false;context.frontend=true;assert(!CanQueue(context));
    context.frontend=false;context.cutscene=true;assert(!CanQueue(context));context.cutscene=false;
    assert(Decide(request,context,10000,7)==Decision::kCancel);
    context.epoch=8;assert(Decide(request,context,1,7)==Decision::kExecute);
    context.epoch=500;assert(Decide(request,context,1,7)==Decision::kExecute);
    assert(Decide(request,context,1,8)==Decision::kCancel);
    request.player_identity=42;context.player_identity=42;assert(Decide(request,context,1,7)==Decision::kExecute);
    context.player_identity=43;assert(Decide(request,context,1,7)==Decision::kCancel);
    request.player_identity=0;request.index=5;assert(Decide(request,context,1,7)==Decision::kCancel);
    assert(BaseSinglePlayer(0,0,0));
    assert(!BaseSinglePlayer(1,0,0)&&!BaseSinglePlayer(0,1,0)&&!BaseSinglePlayer(0,0,1));
    assert(!BaseSinglePlayer({},0,0)&&!BaseSinglePlayer(0,{},0)&&!BaseSinglePlayer(0,0,{}));
  }
  for(uint32_t cap:{20u,24u,25u,30u,40u,45u,60u,90u,120u}){double t=MobileAdaptiveTarget(cap);assert(t==cap&&!MobileAdaptiveSlow(cap,t)&&MobileAdaptiveRecovered(cap,t));assert(MobileAdaptiveSlow(t*.85,t)&&!MobileAdaptiveRecovered(t*.90,t));}
  assert(MobileAdaptiveTarget(0)==60);
  assert(!MobileDecodeParallel(2,128,1<<20)&&!MobileDecodeParallel(4,128,128<<10)&&MobileDecodeParallel(4,128,512<<10)&&MobileDecodeParallel(8,128,128<<10)&&!MobileDecodeParallel(8,15,1<<20));
  const auto id=MobileTextureContentIdentity(123,4,256,128);assert(id&&id!=MobileTextureContentIdentity(123,4,256,256));
  MobileTextureReuseSample<4096> reuse;reuse.Evicted(id,100);assert(reuse.Reuploaded(id,112)==12&&!reuse.Reuploaded(id,113));reuse.Evicted(id,120);reuse.Reset();assert(!reuse.Reuploaded(id,130)&&!reuse.Reuploaded(0,130));
  for(gta4::aspect::Extent output: {gta4::aspect::Extent{1280,720},gta4::aspect::Extent{2560,1144},gta4::aspect::Extent{1920,1200}}){
    const gta4::aspect::Rect authored{.02,.68,.2,.95};const auto layout=gta4::aspect::RadarLayout(output);
    auto a=gta4::aspect::TopLeftRadarViewport(authored,0,1,layout,0),b=gta4::aspect::TopLeftRadarViewport(authored,0,1,layout,.04),c=gta4::aspect::TopLeftRadarViewport(authored,.02,.98,layout,.01);
    assert(std::abs(a.Map(gta4::aspect::Point{authored.left,authored.top}).y)<1e-9&&std::abs(b.Map(gta4::aspect::Point{authored.left,authored.top}).y-.04)<1e-9&&std::abs(c.Map(gta4::aspect::Point{authored.left,authored.top}).y-.0296)<1e-9);
    assert(a.sx==layout.sx&&a.sy==layout.sy&&a.ox==layout.ox);
    for(double offset:{-.20,-.025,-.01,0.0,.015,.15}) {
      const auto moved=gta4::aspect::TopLeftRadarViewport(authored,.02,.98,layout,offset);
      assert(std::abs(moved.Map(gta4::aspect::Point{authored.left,authored.top}).y-(.02+offset*.96))<1e-9);
      assert(moved.sx==layout.sx&&moved.sy==layout.sy&&moved.ox==layout.ox);
    }
    auto automatic=gta4::aspect::TopLeftRadarViewport(authored,0,1,layout);
    assert(std::abs(automatic.Map(gta4::aspect::Point{authored.left,authored.top}).y-(1-layout.Map(authored).bottom))<1e-9);
  }
  const auto wide=SelectFsr1RenderExtent(1280,576,true,"performance",false,false,true);
  assert(wide.active()&&wide.selection==Fsr1Selection::kAdjustedQuality&&wide.width==853&&wide.height==384);
  assert(!SelectFsr1RenderExtent(640,360,true,"performance",false,false,true).active());
  for(uint32_t w:{854u,1280u,1920u,2560u})for(uint32_t h:{480u,576u,720u,1080u}) {
    auto f=SelectFsr1RenderExtent(w,h,true,"performance",false,false,true);
    assert(f.width<=w&&f.height<=h);assert(!f.active()||(f.width>=640&&f.height>=360));
  }
  for(auto output:{gta4::aspect::Extent{1280,720},gta4::aspect::Extent{2048,920},gta4::aspect::Extent{800,1000}}){
    const gta4::aspect::Rect viewport{.02,.65,.3,.98},local{.08,.3,.92,.95};
    const auto transform=gta4::aspect::TopLeftRadarViewport(viewport,.015,.985,gta4::aspect::RadarLayout(output),0,local);
    const auto visible=gta4::aspect::RadarVisibleBounds(transform.Map(viewport),local);
    assert(std::abs(visible.top-.015)<1e-9);
    const auto original=gta4::aspect::RadarLayout(output).Map(viewport);
    assert(std::abs((visible.right-visible.left)-(original.right-original.left)*.84)<1e-9);
    const auto raised=gta4::aspect::TopLeftRadarViewport(viewport,.015,.985,gta4::aspect::RadarLayout(output),-.01,local);
    const auto raised_visible=gta4::aspect::RadarVisibleBounds(raised.Map(viewport),local);
    assert(std::abs(raised_visible.top-(.015-.0097))<1e-9);
    assert(std::abs((raised_visible.right-raised_visible.left)-(visible.right-visible.left))<1e-9);
  }
  MobileFrameStats stats;uint64_t now=1000000000;
  // A tutorial authored near the bottom must follow a relocated minimap.
  // Moving the minimap then moves the text by the same delta until the safe
  // top edge is reached. Aspect and width still bound the readable column.
  for(auto output:{gta4::aspect::Extent{1280,720},gta4::aspect::Extent{2560,1144},gta4::aspect::Extent{1600,1200}}){
    const gta4::aspect::Rect radar{.02,.08,.20,.35},safe{.01,.015,.99,.985};
    const gta4::aspect::Point old_origin{.22,.78};
    const auto first=gta4::aspect::TouchHelpArea(radar,safe,old_origin,.4,output);
    const auto second=gta4::aspect::TouchHelpArea({.02,.13,.20,.40},safe,old_origin,.4,output);
    assert(first&&second&&first->left>radar.right&&first->top<radar.bottom);
    assert(std::abs(second->top-first->top-.05)<1e-9);
    const auto raised=gta4::aspect::TouchHelpArea({.02,-.10,.20,.17},safe,old_origin,.4,output);
    assert(raised&&raised->top>=safe.top&&raised->bottom<=safe.bottom);
    const gta4::aspect::Rect blocked{.21,0,.31,.20};
    const auto avoiding=gta4::aspect::TouchHelpArea(radar,safe,old_origin,.4,output,std::span(&blocked,1));
    assert(avoiding&&(avoiding->left>blocked.right||avoiding->top>blocked.bottom)&&avoiding->right<=safe.right);
  }
  stats.Start(now);
  for(unsigned i=0;i<60;++i){now+=33333333;stats.Record(now,4000000,true);}
  assert(!stats.Ready(now));assert(stats.Ready(now+20));
  auto s=stats.Take(now);assert(s.published==60&&s.failed==0&&s.published_fps>29.99&&s.published_fps<30.01);
  assert(s.interval_p95_ms>33.3&&s.interval_p95_ms<33.4&&s.publish_p95_ms==4);
  now+=100000000;stats.Record(now,5000000,false);
  now+=100000000;stats.Record(now,6000000,true);s=stats.Take(now);
  assert(s.published==1&&s.failed==1&&s.over_50ms==1&&s.interval_max_ms==200);
  for(int i=0;i<900;++i){now+=1000000;stats.Record(now,1000,true);}s=stats.Take(now);
  assert(s.interval_samples==900&&s.interval_p95_ms==1);
  stats.Reset();assert(!stats.Active());
  BenchmarkFrameStats benchmark;uint64_t clock=1000000000;benchmark.Start(clock);
  for(int i=0;i<2000;++i){clock+=(i<120?100000000:1000000);benchmark.Record(clock,1000,true);}
  auto full=benchmark.Take(clock);assert(full.interval_samples==1999&&full.interval_p95_ms==100);

  struct Data{uint64_t x,y;};ImmutableSnapshot<Data> cache;
  auto a=cache.Get({1,2}),b=cache.Get({1,2});assert(a==b);
  auto c=cache.Get({1,3});assert(a!=c&&a->y==2&&c->y==3);
  assert(MobileTextureRetentionFrames(30,64ull<<20,256ull<<20,false)==1800);
  assert(MobileTextureRetentionFrames(120,64ull<<20,256ull<<20,false)==7200);
  assert(MobileTextureRetentionFrames(0,64ull<<20,256ull<<20,false)==3600);
  assert(MobileTextureRetentionFrames(30,256ull<<20,256ull<<20,false)==600);
  assert(MobileTextureRetentionFrames(30,0,0,false)==600);
  assert(MobileTextureRetentionFrames(30,0,256ull<<20,true)==0);
  assert((MobileMemory{12ull<<30,4ull<<30}.TextureRetentionLimit())==(512ull<<20));
  assert((MobileMemory{2ull<<30,512ull<<20}.TextureRetentionLimit())==(96ull<<20));
  assert((MobileMemory{2ull<<30,128ull<<20}.TextureRetentionLimit())==0);
  MobileMemory unknown;assert(!unknown.pressure()&&!unknown.TextureFallbackLimit());
  MobileMemory small{2ull<<30,512ull<<20};assert(!small.pressure()&&small.TextureFallbackLimit()>=(128ull<<20));
  small.available=64ull<<20;assert(small.pressure());
  MobileMemory large{12ull<<30,4ull<<30};assert(!large.pressure()&&large.TextureFallbackLimit()==(1024ull<<20));
  assert(large.TextureFallbackLimit(128ull<<20)==large.TextureFallbackLimit());large.available=800ull<<20;assert(large.TextureFallbackLimit(128ull<<20)<large.TextureFallbackLimit());assert(large.TextureFallbackLimit(UINT64_MAX)==large.TextureFallbackLimit());
  assert(argc==2);std::filesystem::path dir=argv[1];std::filesystem::remove_all(dir);
  std::vector<uint8_t> input(65536,42),output(65536,9),read(65536,0),metadata{1,2,3};
  auto key=ConvertedTextureDiskCache::Identify(input,metadata);
  {ConvertedTextureDiskCache disk(dir);
   assert(!disk.Read(key,read));assert(disk.Write(key,output));
   const auto until=std::chrono::steady_clock::now()+std::chrono::seconds(3);
   while(!disk.Read(key,read)&&std::chrono::steady_clock::now()<until)std::this_thread::sleep_for(std::chrono::milliseconds(5));
   assert(read==output);metadata[0]=2;assert(!disk.Read(ConvertedTextureDiskCache::Identify(input,metadata),read));
   for(auto& file:std::filesystem::directory_iterator(dir))if(file.path().extension()==".bin") {
     std::ofstream corrupt(file.path(),std::ios::binary|std::ios::trunc);corrupt<<"corrupt";
   }
   std::fill(read.begin(),read.end(),77);assert(!disk.Read(key,read));assert(read[0]==77);
   assert(!disk.Write(key,std::span<const uint8_t>(output.data(),32)));
  }
  std::filesystem::remove_all(dir);
  std::cout<<"Frame statistics, immutable lifetime, memory bounds and corrupt disk-cache fallback passed\n";
}

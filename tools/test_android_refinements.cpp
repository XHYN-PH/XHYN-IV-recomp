// Deterministic checks of the production queue, state and conversion helpers.
#include <array>
#include <cassert>
#include <atomic>
#include <cmath>
#include <future>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>
#include "graphics/gta4_native/native_pipeline_compiler.h"
#include "graphics/gta4_native/native_draw_state_cache.h"
#include "graphics/gta4_native/native_texture_decode_worker.h"
#include "graphics/gta4_native/native_optional_diagnostics.h"
#include "graphics/gta4_native/native_clear_coalescing.h"
#include "graphics/gta4_native/native_driver_workarounds.h"
#include "android/android_power_policy.h"
#include <rex/graphics/pipeline/texture/conversion.h>

using namespace rex::graphics::gta4_native;
using Compiler = NativePipelineCompiler<int,int,std::hash<int>>;

void QueueTests() {
  Compiler queue(2);
  std::promise<void> entered, release;
  auto gate=release.get_future().share();
  assert(queue.Enqueue(1,[&]{entered.set_value();gate.wait();return 10;}));
  entered.get_future().wait();
  assert(queue.Enqueue(2,[]{return 20;},true));
  // A full queue must never evict work whose caller is waiting.
  assert(!queue.Enqueue(3,[]{return 30;},true));
  assert(queue.Contains(2));
  release.set_value();
  assert(queue.Take(2,true)==20);
  assert(queue.Take(1,true)==10);
  assert(queue.TakeCompleted().empty());
  assert(queue.Enqueue(1,[]{return 40;}));
  queue.WaitUntilIdle();
  auto done=queue.TakeCompleted();
  assert(done.size()==1 && done[0].first==1 && done[0].second==40);
  assert(queue.Enqueue(1,[]()->int{throw std::runtime_error("failure");}));
  assert(queue.Take(1,true)==0);
  queue.Stop();
  assert(!queue.Enqueue(4,[]{return 4;}));

  Compiler priority(3);
  std::promise<void> ready, go;
  auto wait=go.get_future().share();
  std::vector<int> order;
  assert(priority.Enqueue(0,[&]{ready.set_value();wait.wait();return 0;}));
  ready.get_future().wait();
  assert(priority.Enqueue(1,[&]{order.push_back(1);return 1;}));
  assert(priority.Enqueue(2,[&]{order.push_back(2);return 2;}));
  assert(priority.Enqueue(2,[]{return 99;},true));
  assert(priority.Enqueue(3,[&]{order.push_back(3);return 3;},true));
  assert(!priority.Contains(1));
  go.set_value();
  priority.WaitUntilIdle();
  assert((order==std::vector<int>{3,2}));
  assert(priority.Take(2,false)==2);
  assert(priority.TakeCompleted().size()==2);
}

void StateTests() {
  NativeDrawStateCache<6> state;
  std::array<uint64_t,6> descriptors{1,2,3,4,5,6};
  auto span=state.ChangedDescriptors(1,descriptors);assert(span.first==0&&span.count==6);
  assert(state.ChangedDescriptors(1,descriptors).count==0);
  descriptors[3]=9;span=state.ChangedDescriptors(1,descriptors);assert(span.first==3&&span.count==1);
  descriptors[1]=10;descriptors[5]=11;span=state.ChangedDescriptors(1,descriptors);assert(span.first==1&&span.count==5);
  assert(state.ChangedDescriptors(2,descriptors).count==6);state.Reset();assert(state.ChangedDescriptors(2,descriptors).count==6);
  std::array<uint32_t,6> viewport{0,0,640,480,0,1};
  assert(state.UpdateIndexBuffer(1,2,16,0));
  assert(!state.UpdateIndexBuffer(1,2,16,0));
  assert(state.UpdateIndexBuffer(1,2,16,1));
  assert(state.UpdateIndexBuffer(2,2,16,1));
  state.Reset();assert(state.UpdateIndexBuffer(2,2,16,1));
  assert(state.UpdatePipeline(1,true));
  assert(state.UpdateViewport(viewport));
  assert(state.UpdatePipeline(2,true));
  assert(!state.UpdateViewport(viewport));
  assert(state.UpdatePipeline(3,false));
  assert(state.UpdateViewport(viewport));
  assert(state.UpdatePipeline(4,true));
  assert(state.UpdateViewport(viewport));
  state.Reset();
  assert(state.UpdateViewport(viewport));
  NativeOptionalDiagnosticCounter<unsigned> count;
  native_optional_diagnostics_enabled=false;
  ++count;count+=12;count.fetch_add(5);assert(count.load()==0);
  native_optional_diagnostics_enabled=true;
  ++count;count+=12;assert(count.fetch_add(5)==13);assert(count.exchange(0)==18);
  native_optional_diagnostics_enabled=false;
  struct Clear { int device=1,flags=3; unsigned depth_bits=0,stencil=0;
    std::array<unsigned,4> color_bits{};int left=1,top=1,right=5,bottom=5; };
  Clear a,b;b.left=0;b.top=0;b.right=6;b.bottom=6;
  assert(NativeClearOverwritten(a,b));
  b.depth_bits=1;assert(!NativeClearOverwritten(a,b));b.depth_bits=0;
  b.right=4;assert(!NativeClearOverwritten(a,b));b.right=6;
  b.device=2;assert(!NativeClearOverwritten(a,b));
  assert(NativeLegacyLightGuard("Turnip Adreno (TM) 650",true));
  assert(NativeLegacyLightGuard("Turnip Adreno (TM) 750",true));
  assert(NativeLegacyLightGuard("Turnip Adreno (TM) 730",true));
  assert(NativeLegacyLightGuard("",true));
  assert(!NativeLegacyLightGuard("Adreno 650",false));
  assert(!NativeLegacyLightGuard("Mali-G715",false));
}

void PowerTests() {
  using namespace gta4::android_power;
  assert(FrameBudgetNanoseconds(120)==8'333'333);
  assert(FrameBudgetNanoseconds(0)==16'666'666);
  assert(HintTargetNanoseconds(120,0)==7'499'999);
  assert(HintTargetNanoseconds(60,5)==5'000'000);
  assert(HintTargetNanoseconds(60,std::numeric_limits<double>::quiet_NaN())==14'999'999);
  ThermalRecoveryHold hold;assert(!hold.Update(0,true,false));assert(!hold.Update(1000,false,true));assert(!hold.Update(10999,false,true));assert(hold.Update(11000,false,true));assert(!hold.Update(12000,true,false));assert(!hold.Update(13000,false,true));assert(hold.Update(23000,false,true));
  ThermalDrawDistance policy;
  auto value=policy.Update(1,true,true,false);assert(value && std::abs(*value-.7)<1e-8);
  assert(!policy.Update(.7,true,true,false));
  // A manual setting while hot must survive cooling and disabling the guard.
  assert(!policy.Update(.8,true,true,false));
  assert(!policy.Update(.8,true,false,true));
  assert(!policy.Update(.4,true,true,false));
  assert(policy.Update(1,true,true,false));
  assert(policy.Update(.7,false,false,false)==1);
}

void WorkerTests() {
  NativeTextureDecodeWorker worker;
  std::promise<void> entered,release;
  auto gate=release.get_future().share();
  auto running=std::async(std::launch::async,[&] {
    return worker.Run(32,256*1024,true,[&](unsigned first,unsigned last){
      if(first==0){entered.set_value();gate.wait();} return first<last;
    });
  });
  entered.get_future().wait();
  int serial_calls=0;
  assert(worker.Run(32,256*1024,true,[&](unsigned first,unsigned last){
    ++serial_calls;assert(first==0 && last==32);return true;}));
  assert(serial_calls==1);release.set_value();assert(running.get());
  assert(!worker.Run(32,256*1024,true,[](unsigned first,unsigned){return first!=0;}));
  bool joined=false,thrown=false;
  try { worker.Run(32,256*1024,true,[&](unsigned first,unsigned){
    if(first){throw std::runtime_error("owner failed");}joined=true;return true;}); }
  catch(const std::runtime_error&){thrown=true;}
  assert(thrown && joined);
}

void ConversionTests() {
  using namespace rex::graphics::texture_conversion;
  using rex::graphics::xenos::Endian;
  struct Format { void(*convert)(Endian,void*,const void*,size_t);unsigned source,pixel; };
  const Format formats[]={
    {ConvertTexelDXT1ToR8G8B8A8,8,4},{ConvertTexelDXT1ToA1R5G5B5,8,2},
    {ConvertTexelDXT2_3ToR8G8B8A8,16,4},{ConvertTexelDXT4_5ToR8G8B8A8,16,4},
    {ConvertTexelDXT3AToR8G8B8A8,8,4},{ConvertTexelCTX1ToR8G8,8,2},
    {ConvertTexelDXNToR8G8,16,2},{ConvertTexelDXT5AToR8,8,1}};
  NativeTextureDecodeWorker worker;
  size_t cases=0;
  for(auto format:formats) for(auto endian:{Endian::kNone,Endian::k8in16,Endian::k8in32,Endian::k16in32})
    for(unsigned rows:{1u,16u,33u,129u}) {
      const unsigned columns=73,pitch=columns*4*format.pixel+32;
      std::vector<unsigned char> source(rows*columns*format.source);
      for(size_t i=0;i<source.size();++i)source[i]=unsigned((i*131+i/7+19)&255);
      std::vector<unsigned char> serial(rows*4*pitch+64,0xAD),parallel=serial;
      auto convert=[&](auto& output,unsigned begin,unsigned end){
        for(unsigned y=begin;y<end;++y)for(unsigned x=0;x<columns;++x)
          format.convert(endian,output.data()+32+y*4*pitch+x*4*format.pixel,
                         source.data()+(y*columns+x)*format.source,pitch);
        return true;
      };
      assert(convert(serial,0,rows));
      assert(worker.Run(rows,parallel.size(),true,[&](unsigned a,unsigned b){return convert(parallel,a,b);}));
      assert(serial==parallel);++cases;
    }
  std::cout<<"Production texture serial/parallel comparisons: "<<cases<<"\n";
}

int main(){QueueTests();StateTests();PowerTests();WorkerTests();ConversionTests();
  std::cout<<"Android native refinement regression checks passed\n";}

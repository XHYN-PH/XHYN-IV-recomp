#include <cassert>
#include <atomic>
#include <chrono>
#include <future>
#include <thread>
#include <cmath>
#include "graphics/gta4_native/native_pipeline_compiler.h"
#include "input/sdl/sdl_axis_policy.h"
using rex::graphics::gta4_native::NativePipelineCompiler;
int main(){
 using namespace rex::input::sdl;
 for(int v:{-32768,-15000,0,15000,32767}){int16_t x=v,y=v;TuneThumbs(x,y,0,1,1,false,false);assert(x==v&&y==v);}
 {int16_t x=1000,y=-1000;TuneThumbs(x,y,.1,1,1,false,false);assert(x==0&&y==0);}
 {int16_t x=-32768,y=0;TuneThumbs(x,y,0,1,1,true,true);assert(x==32767&&y==0);}
 {int16_t x=32767,y=32767;TuneThumbs(x,y,.1,2,.5,false,false);assert(x>0&&y>0&&std::hypot(double(x),double(y))<32770);}
 assert(TuneTrigger(255,.2)==255&&TuneTrigger(10,.2)==0&&TuneTrigger(0,0)==0);
 using Compiler=NativePipelineCompiler<int,int,std::hash<int>>;
 {
  std::atomic<int> entered{0},active{0},peak{0};std::promise<void> go;auto gate=go.get_future().share();Compiler pool(16,{},5);
  for(int i=0;i<8;++i)assert(pool.Enqueue(i,[&,i]{int n=++active;++entered;int prior=peak;while(n>prior&&!peak.compare_exchange_weak(prior,n)){}gate.wait();--active;return i*7;}));
  auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(3);while(entered<5&&std::chrono::steady_clock::now()<deadline)std::this_thread::yield();
  assert(entered==5&&pool.GetProgress().active_count==5);go.set_value();for(int i=0;i<8;++i){auto v=pool.Take(i,true);assert(v&&*v==i*7);}pool.WaitUntilIdle();assert(peak<=5);pool.Stop();assert(!pool.Enqueue(100,[]{return 0;}));
 }
 {
  std::promise<void> entered,go;auto gate=go.get_future().share();std::atomic<int> calls{0};Compiler pool(2);
  assert(pool.Enqueue(1,[&]{entered.set_value();gate.wait();++calls;return 10;}));entered.get_future().wait();assert(pool.Enqueue(1,[&]{++calls;return 100;}));assert(pool.Enqueue(2,[]{return 20;}));assert(pool.Enqueue(3,[]{return 30;},true));assert(!pool.Contains(2));go.set_value();assert(pool.Take(3,true)==30);assert(pool.Take(1,true)==10);assert(calls==1);
 }
}

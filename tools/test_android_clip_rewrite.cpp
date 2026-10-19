#include <cassert>
#include <cstring>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>
#include <rex/ui/vulkan/clip_cull_rewrite.h>
using namespace rex::ui::vulkan::util;
std::vector<uint32_t> Read(const char* path){
  std::ifstream file(path,std::ios::binary);std::vector<char> bytes{std::istreambuf_iterator<char>(file),{}};
  assert(!bytes.empty() && bytes.size()%4==0);std::vector<uint32_t> words(bytes.size()/4);
  std::memcpy(words.data(),bytes.data(),bytes.size());return words;
}
int main(int argc,char** argv){
  assert(argc==4);auto words=Read(argv[1]);auto member=Read(argv[2]);std::vector<uint32_t> out;
  assert(RewriteClipCullDistances(words.data(),words.size()*4,true,true,8,out)==ClipCullRewriteResult::kUnchanged);
  assert(RewriteClipCullDistances(words.data(),words.size()*4,false,false,8,out)==ClipCullRewriteResult::kRewritten);
  std::ofstream file(argv[3],std::ios::binary);file.write(reinterpret_cast<const char*>(out.data()),out.size()*4);file.close();
  assert(RewriteClipCullDistances(words.data(),words.size()*4,false,false,6,out)==ClipCullRewriteResult::kUnsupported);
  for (int feature=0;feature<2;++feature) {
    assert(RewriteClipCullDistances(words.data(),words.size()*4,feature==0,feature==1,8,out)==ClipCullRewriteResult::kRewritten);
    std::ofstream mixed(std::string(argv[3])+".mixed"+std::to_string(feature),std::ios::binary);
    mixed.write(reinterpret_cast<const char*>(out.data()),out.size()*4);
  }
  assert(RewriteClipCullDistances(member.data(),member.size()*4,false,false,8,out)==ClipCullRewriteResult::kUnsupported);
  assert(RewriteClipCullDistances(member.data(),member.size()*4,true,false,8,out)==ClipCullRewriteResult::kUnchanged);
  assert(RewriteClipCullDistances(member.data(),member.size()*4,false,true,8,out)==ClipCullRewriteResult::kUnsupported);
  assert(RewriteClipCullDistances(nullptr,20,false,false,8,out)==ClipCullRewriteResult::kUnsupported);
  assert(RewriteClipCullDistances(words.data(),words.size()*4-1,false,false,8,out)==ClipCullRewriteResult::kUnsupported);
  words[5]=0;assert(RewriteClipCullDistances(words.data(),words.size()*4,false,false,8,out)==ClipCullRewriteResult::kUnsupported);
  std::cout<<"SPIR-V rewrite boundary checks passed\n";
}

#pragma once
#include <algorithm>
#include <cstdint>
#include <span>
#include <unordered_set>
#include <vector>
namespace rex::graphics::gta4_native {
// Only Input variables change locations; vertex outputs and shader semantics stay intact.
inline bool RemapNativeVertexLocations(std::vector<uint32_t>& words,std::span<const uint32_t> legacy) {
 if(words.empty())return true;
 if(words.size()<5||words[0]!=0x07230203||!words[3]||words[3]>1048576)return false;
 std::unordered_set<uint32_t> inputs;
 for(size_t i=5;i<words.size();){uint32_t n=words[i]>>16,op=words[i]&0xffff;if(!n||n>words.size()-i)return false;if(op==59&&n>=4&&words[i+3]==1)inputs.insert(words[i+2]);i+=n;}
 // Validate the complete mapping before writing anything.
 for(size_t i=5;i<words.size();){uint32_t n=words[i]>>16;if((words[i]&0xffff)==71&&n>=4&&words[i+2]==30&&inputs.contains(words[i+1])&&!std::binary_search(legacy.begin(),legacy.end(),words[i+3]))return false;i+=n;}
 for(size_t i=5;i<words.size();){uint32_t n=words[i]>>16;if((words[i]&0xffff)==71&&n>=4&&words[i+2]==30&&inputs.contains(words[i+1]))words[i+3]=uint32_t(std::lower_bound(legacy.begin(),legacy.end(),words[i+3])-legacy.begin());i+=n;}
 return true;
}
}

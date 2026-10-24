#include "../glue/rexglue-sdk-main/src/graphics/gta4_native/native_vertex_location_remap.h"
#include <cassert>
#include <cstring>
#include <fstream>
#include <iterator>
#include <iostream>
using rex::graphics::gta4_native::RemapNativeVertexLocations;
int main(int argc,char**argv){
 std::vector<uint32_t> module={0x07230203,0x00010000,0,40,0,
   (4u<<16)|71,10,30,17,(4u<<16)|71,11,30,32,(4u<<16)|71,12,30,17,
   (4u<<16)|59,1,10,1,(4u<<16)|59,1,11,1,(4u<<16)|59,1,12,3};
 const auto original=module;const std::vector<uint32_t> locations={17,32};assert(RemapNativeVertexLocations(module,locations));assert(module[8]==0&&module[12]==1&&module[16]==17);
 auto bad=original;bad[5]=0;const auto before=bad;assert(!RemapNativeVertexLocations(bad,locations));assert(bad==before);
 bad=original;const std::vector<uint32_t> incomplete={17};assert(!RemapNativeVertexLocations(bad,incomplete));assert(bad==original);
 if(argc==3){std::ifstream in(argv[1],std::ios::binary);std::vector<char> data((std::istreambuf_iterator<char>(in)),{});assert(!data.empty()&&data.size()%4==0);std::vector<uint32_t> words(data.size()/4);std::memcpy(words.data(),data.data(),data.size());assert(RemapNativeVertexLocations(words,locations));std::ofstream out(argv[2],std::ios::binary);out.write(reinterpret_cast<const char*>(words.data()),words.size()*4);}
 std::cout<<"Vertex locations: compact input mapping, preserved outputs, incomplete map and malformed module rejection passed\n";
}

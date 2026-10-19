#include <fstream>
#include <iostream>
#include <vector>
#include "graphics/gta4_native/native_color_output_spirv.h"
int main(int argc,char** argv) {
  if(argc!=3)return 2;
  std::ifstream input(argv[1],std::ios::binary|std::ios::ate);if(!input)return 2;
  auto bytes=input.tellg();if(bytes<=0||bytes%4)return 2;
  std::vector<uint32_t> code(size_t(bytes)/4);input.seekg(0);input.read(reinterpret_cast<char*>(code.data()),bytes);
  std::string error;auto rewritten=rex::graphics::gta4_native::AddNativeColorOutputEpilogue(code,&error);
  if(!rewritten){std::cerr<<error<<'\n';return 1;}
  std::ofstream output(argv[2],std::ios::binary);output.write(reinterpret_cast<const char*>(rewritten->data()),rewritten->size()*4);
  return output?0:2;
}

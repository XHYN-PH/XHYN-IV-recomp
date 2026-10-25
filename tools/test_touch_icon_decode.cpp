#include <rex/ui/image_decode.h>
#include <cassert>
#include <fstream>
#include <filesystem>
#include <iostream>
int main(int argc,char** argv){assert(argc==2);unsigned count=0;for(auto& file:std::filesystem::directory_iterator(argv[1])){if(file.path().extension()!=".png")continue;std::ifstream input(file.path(),std::ios::binary);std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(input)),{});int w=0,h=0;auto rgba=rex::ui::DecodeImageRGBA(bytes.data(),bytes.size(),w,h,1024,1024);assert(!rgba.empty()&&w>0&&h>0&&rgba.size()==size_t(w)*h*4);++count;}assert(count==44);std::cout<<count<<" touch PNGs decoded by the native image decoder\n";}

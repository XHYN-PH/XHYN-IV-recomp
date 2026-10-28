#pragma once
#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <span>
#include <vector>
namespace rex::graphics::gta4_native {
// A bounded, lossless PNG writer for explicit diagnostic requests. Stored
// deflate blocks avoid adding a codec dependency to the renderer plugin.
inline std::vector<uint8_t> EncodeNativeCapturePng(uint32_t width,uint32_t height,
                                                std::span<const uint8_t> rgba) {
  if(!width||!height||uint64_t(width)*height>16777216||rgba.size()!=uint64_t(width)*height*4)return {};
  std::vector<uint8_t> raw;raw.reserve(rgba.size()+height);
  for(uint32_t y=0;y<height;++y){raw.push_back(0);const auto row=rgba.subspan(size_t(y)*width*4,size_t(width)*4);raw.insert(raw.end(),row.begin(),row.end());}
  std::vector<uint8_t> z{0x78,0x01};
  for(size_t offset=0;offset<raw.size();){uint16_t n=uint16_t(std::min<size_t>(65535,raw.size()-offset));z.push_back(offset+n==raw.size()?1:0);z.push_back(uint8_t(n));z.push_back(uint8_t(n>>8));z.push_back(uint8_t(~n));z.push_back(uint8_t((~n)>>8));z.insert(z.end(),raw.begin()+offset,raw.begin()+offset+n);offset+=n;}
  auto big=[](std::vector<uint8_t>& out,uint32_t n){for(int shift=24;shift>=0;shift-=8)out.push_back(uint8_t(n>>shift));};
  uint32_t a=1,b=0;for(uint8_t c:raw){a=(a+c)%65521;b=(b+a)%65521;}big(z,(b<<16)|a);
  std::vector<uint8_t> out{137,80,78,71,13,10,26,10};
  static constexpr auto crc_table=[] {
    std::array<uint32_t,256> table{};
    for(uint32_t i=0;i<table.size();++i){uint32_t crc=i;for(int bit=0;bit<8;++bit)crc=(crc>>1)^(0xEDB88320u&uint32_t(-int32_t(crc&1)));table[i]=crc;}
    return table;
  }();
  auto chunk=[&](const char* tag,std::span<const uint8_t> data){big(out,uint32_t(data.size()));const size_t begin=out.size();out.insert(out.end(),tag,tag+4);out.insert(out.end(),data.begin(),data.end());uint32_t crc=~0u;for(size_t i=begin;i<out.size();++i)crc=crc_table[(crc^out[i])&255]^(crc>>8);big(out,~crc);};
  std::vector<uint8_t> ihdr;big(ihdr,width);big(ihdr,height);ihdr.insert(ihdr.end(),{8,6,0,0,0});chunk("IHDR",ihdr);chunk("IDAT",z);chunk("IEND",{});return out;
}
}

#pragma once
#include <string_view>

namespace rex::filesystem {
constexpr bool IsOptionalXboxCachePath(std::string_view path) {
  const auto starts=[](std::string_view value,std::string_view prefix){
    if(value.size()<prefix.size())return false;
    for(std::size_t i=0;i<prefix.size();++i){char c=value[i];if(c>='A'&&c<='Z')c+=32;if(c!=prefix[i])return false;}
    return true;
  };
  return starts(path,"cache:")||starts(path,"cache0:")||starts(path,"cache1:");
}
}  // namespace rex::filesystem

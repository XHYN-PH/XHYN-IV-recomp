#pragma once
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include "spirv_typed_results.h"

namespace rex::ui::vulkan::util {
// The title cache uses uint64 for physical-storage addresses. Represent that
// ABI as two uint32 lanes on devices with BDA but without shaderInt64. Carry,
// borrow and cross-lane shifts are explicit. Pointer conversions use OpBitcast,
// as required by SPV_KHR_physical_storage_buffer without Int64. This is not a
// general 64-bit ALU emulator: unreviewed operations fail closed.
inline std::optional<std::vector<uint32_t>> RewriteAddressInt64(
    std::span<const uint32_t> code, std::string* error=nullptr) {
  using Words=std::vector<uint32_t>;
  auto fail=[&](const char* reason)->std::optional<Words>{if(error)*error=reason;return std::nullopt;};
  if(code.size()<5||code[0]!=0x07230203||!code[3]||code[3]>1000000)return fail("invalid SPIR-V header");
  std::vector<Words> instructions;std::unordered_set<uint32_t> types,wide;
  std::unordered_map<uint32_t,uint32_t> widths,value_types;
  uint32_t uint_type=0,bool_type=0,pair_type=0,next=code[3];
  for(size_t p=5;p<code.size();){uint32_t n=code[p]>>16,op=code[p]&65535;if(!n||n>code.size()-p)return fail("invalid instruction length");
    Words w(code.begin()+p,code.begin()+p+n);
    if(op>=19&&op<=39&&n>=2)types.insert(w[1]);
    if(op==20&&n==2)bool_type=w[1];
    if(op==21&&n==4){widths[w[1]]=w[2];if(w[2]==32&&w[3]==0)uint_type=w[1];if(w[2]==64){if(w[3])return fail("signed Int64 is outside the address fallback");wide.insert(w[1]);}}
    instructions.push_back(std::move(w));p+=n;
  }
  if(wide.empty())return Words(code.begin(),code.end());
  for(const auto& w:instructions){uint32_t op=w[0]&65535;
    if(op==23&&w.size()==4){if(wide.contains(w[2]))return fail("Int64 vector is outside the address fallback");if(w[2]==uint_type&&w[3]==2)pair_type=w[1];}
    if(SpirvHasTypedResult(op)&&w.size()>2&&w[2]<code[3])value_types[w[2]]=w[1];
  }
  auto id=[&](){return next++;};
  if(!uint_type)uint_type=id();if(!bool_type)bool_type=id();if(!pair_type)pair_type=id();
  const uint32_t carry_type=id();
  auto emit=[](Words& out,uint32_t op,std::initializer_list<uint32_t> args){out.push_back((uint32_t(args.size()+1)<<16)|op);out.insert(out.end(),args);};
  Words declarations;emit(declarations,21,{uint_type,32,0});emit(declarations,20,{bool_type});emit(declarations,23,{pair_type,uint_type,2});emit(declarations,30,{carry_type,uint_type,uint_type});
  std::unordered_map<uint32_t,uint32_t> constants;
  auto constant=[&](Words& out,uint32_t value){auto found=constants.find(value);if(found!=constants.end())return found->second;uint32_t result=id();emit(out,43,{uint_type,result,value});constants[value]=result;return result;};
  const uint32_t zero=constant(declarations,0),one=constant(declarations,1),bits31=constant(declarations,31),bits32=constant(declarations,32);
  auto is_wide=[&](uint32_t value){auto i=value_types.find(value);return i!=value_types.end()&&wide.contains(i->second);};
  auto type=[&](uint32_t t){return wide.contains(t)?pair_type:t;};
  Words body;bool inserted=false,in_function=false;
  for(auto w:instructions){uint32_t op=w[0]&65535;
    if(op==54)in_function=true;
    if(op==17&&w.size()==2&&w[1]==11)continue; // Int64 capability.
    if((op>=19&&op<=39)&&w.size()>=2){
      if(!inserted){body.insert(body.end(),declarations.begin(),declarations.end());inserted=true;}
      if(wide.contains(w[1])||w[1]==uint_type||w[1]==bool_type||w[1]==pair_type)continue;
      if((op==28||op==29)&&w.size()>=3)w[2]=type(w[2]);
      if(op==32&&w.size()==4)w[3]=type(w[3]);
      if(op==30||op==33)for(size_t i=2;i<w.size();++i)w[i]=type(w[i]);
      if(op==28&&w.size()>3&&is_wide(w[3]))return fail("Int64 array length");
      body.insert(body.end(),w.begin(),w.end());continue;
    }
    if((op==5||op==71)&&w.size()>1&&wide.contains(w[1])){if(op==5)continue;return fail("decorated Int64 type");}
    const bool result_wide=SpirvHasTypedResult(op)&&w.size()>2&&wide.contains(w[1]);
    if(op==43&&result_wide){
      if(w.size()!=5)return fail("invalid Int64 constant");uint32_t low=constant(body,w[3]),high=constant(body,w[4]);emit(body,44,{pair_type,w[2],low,high});continue;
    }
    if((op==50||op==51||op==52)&&result_wide)return fail("Int64 specialization constant");
    auto extract=[&](uint32_t pair,uint32_t lane){uint32_t result=id();emit(body,81,{uint_type,result,pair,lane});return result;};
    auto binary=[&](uint32_t operation,uint32_t a,uint32_t b,uint32_t ty=0){uint32_t result=id();emit(body,operation,{ty?ty:uint_type,result,a,b});return result;};
    auto choose=[&](uint32_t condition,uint32_t a,uint32_t b){uint32_t result=id();emit(body,169,{uint_type,result,condition,a,b});return result;};
    auto compose=[&](uint32_t low,uint32_t high){emit(body,80,{pair_type,w[2],low,high});};
    if(op==169&&result_wide&&w.size()==6){
      if(!is_wide(w[4])||!is_wide(w[5]))return fail("mixed-width address selection");
      uint32_t low=choose(w[3],extract(w[4],0),extract(w[5],0));
      uint32_t high=choose(w[3],extract(w[4],1),extract(w[5],1));
      compose(low,high);continue;
    }
    if((op==117&&result_wide)||(op==120&&w.size()==4&&is_wide(w[3]))){w[0]=(uint32_t(w.size())<<16)|124;w[1]=type(w[1]);body.insert(body.end(),w.begin(),w.end());continue;}
    if((op==113||op==114)&&w.size()==4&&(result_wide||is_wide(w[3]))){
      uint32_t source=w[3];if(result_wide&&is_wide(source)){emit(body,83,{pair_type,w[2],source});continue;}
      if(result_wide){uint32_t low=source;auto st=value_types.find(source);if(st==value_types.end()||!widths.contains(st->second)||widths[st->second]>32)return fail("unsupported address conversion");
        uint32_t converted=id();emit(body,widths[st->second]==32?124:op,{uint_type,converted,source});low=converted;
        uint32_t high=op==114?binary(195,low,bits31):zero;compose(low,high);
      }else{uint32_t low=extract(source,0);emit(body,widths[w[1]]==32?124:op,{w[1],w[2],low});}continue;
    }
    if(result_wide&&w.size()==5&&(op==128||op==130||op==132)){
      if(!is_wide(w[3])||!is_wide(w[4]))return fail("mixed-width address arithmetic");
      uint32_t a=extract(w[3],0),ah=extract(w[3],1),b=extract(w[4],0),bh=extract(w[4],1),low=0,high=0;
      if(op==132){uint32_t product=id();emit(body,151,{carry_type,product,a,b});low=extract(product,0);high=extract(product,1);high=binary(128,high,binary(128,binary(132,a,bh),binary(132,ah,b)));}
      else{low=binary(op,a,b);uint32_t carry=op==128?binary(176,low,a,bool_type):binary(176,a,b,bool_type);high=binary(op,binary(op,ah,bh),choose(carry,one,zero));}
      compose(low,high);continue;
    }
    if(result_wide&&w.size()==5&&(op==194||op==195||op==196)){
      uint32_t low=extract(w[3],0),high=extract(w[3],1),shift=w[4];
      if(is_wide(shift))shift=extract(shift,0);else{uint32_t converted=id();uint32_t source_type=value_types[shift];emit(body,widths[source_type]==32?124:113,{uint_type,converted,shift});shift=converted;}
      uint32_t small=binary(199,shift,bits31),large=binary(174,shift,bits32,bool_type),nonzero=binary(171,small,zero,bool_type);
      uint32_t inverse=binary(199,binary(130,bits32,small),bits31),l=0,h=0;
      if(op==196){l=choose(large,zero,binary(196,low,small));h=choose(large,binary(196,low,small),binary(197,binary(196,high,small),choose(nonzero,binary(194,low,inverse),zero)));}
      else{uint32_t shifted_high=binary(op,high,small);l=choose(large,shifted_high,binary(197,binary(194,low,small),choose(nonzero,binary(196,high,inverse),zero)));h=choose(large,op==195?binary(195,high,bits31):zero,shifted_high);}
      compose(l,h);continue;
    }
    if(op>=170&&op<=179&&w.size()==5&&(is_wide(w[3])||is_wide(w[4]))){
      if(!is_wide(w[3])||!is_wide(w[4]))return fail("mixed-width address comparison");
      // Signed comparisons require signed64 input, rejected above.
      if(op==173||op==175||op==177||op==179)return fail("signed address comparison");
      uint32_t a=extract(w[3],0),ah=extract(w[3],1),b=extract(w[4],0),bh=extract(w[4],1);
      uint32_t same=binary(170,ah,bh,bool_type),lower=binary(op,a,b,bool_type),result=0;
      if(op==170)result=binary(167,same,lower,bool_type);
      else if(op==171)result=binary(166,binary(171,ah,bh,bool_type),lower,bool_type);
      else{uint32_t highop=(op==172||op==174)?172:176;result=binary(166,binary(highop,ah,bh,bool_type),binary(167,same,lower,bool_type),bool_type);}
      emit(body,83,{w[1],w[2],result});continue;
    }
    if((op==65||op==66||op==67||op==70)&&w.size()>4)for(size_t i=4;i<w.size();++i)if(is_wide(w[i]))return fail("Int64 access-chain index");
    if(op==251&&w.size()>1&&is_wide(w[1]))return fail("Int64 switch selector");
    bool touches=result_wide;
    if(in_function||op==52)for(size_t i=op==62||op==254?1:op==12?5:3;i<w.size();++i)touches|=is_wide(w[i]);
    if(touches){
      switch(op){case 1:case 44:case 46:case 54:case 55:case 57:case 59:case 61:case 62:case 80:case 81:case 82:case 83:case 124:case 169:case 197:case 198:case 199:case 200:case 245:case 254:break;
        default:return fail("unreviewed Int64 operation");}
    }
    if(SpirvHasTypedResult(op)&&w.size()>2)w[1]=type(w[1]);
    body.insert(body.end(),w.begin(),w.end());
    if(op==56)in_function=false;
  }
  if(!inserted)return fail("missing type declarations");
  Words result(code.begin(),code.begin()+5);result[3]=next;result.insert(result.end(),body.begin(),body.end());return result;
}
}

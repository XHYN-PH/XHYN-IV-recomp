// Execute production CVar serialization with an independent TOML parse.
#include <rex/cvar.h>
#include <toml++/toml.hpp>
#include <cassert>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>
REXCVAR_DEFINE_STRING(config_regression_text,"default","ConfigRegression","Escaped config fixture");
REXCVAR_DEFINE_INT32(config_regression_number,17,"ConfigRegression","Numeric config fixture");
int main(int argc,char** argv){
  assert(argc>1);const auto root=std::filesystem::path(argv[1]);std::filesystem::create_directories(root);
  std::string controls;for(unsigned char c=0;c<32;++c)controls+=char(c);controls+=char(127);
  const std::vector<std::string> values={R"({"active":false,"outcome":"ready","reason":""})",
      R"(C:\Users\Player\LibertyRecomp\native.toml)","first line\nnext\tline\r\n# text = \"quoted\"",controls,
      "UTF-8: café · 日本語 · 🎮", "\"\nconfig_regression_number = 99\n#"};
  try{
    for(const auto& value:values){
      rex::cvar::testing::ResetAllForTesting();
      assert(rex::cvar::SetFlagByName("config_regression_text",value));
      for(const auto& text:{rex::cvar::SerializeToTOML(),rex::cvar::SerializeToTOML("ConfigRegression")}){
        const auto parsed=toml::parse(text);
        assert(parsed["config_regression_text"].value<std::string>()==value);
        assert(!parsed.contains("config_regression_number"));
      }
      const auto path=root/"roundtrip.toml";assert(rex::cvar::SaveConfig(path));
      rex::cvar::testing::ResetAllForTesting();assert(rex::cvar::LoadConfigWithResult(path).status==rex::cvar::ConfigLoadStatus::kLoaded);
      assert(rex::cvar::GetFlagByName("config_regression_text")==value);
    }
  }catch(const toml::parse_error& e){std::cerr<<"Production config round-trip failed: "<<e.description()<<'\n';return 1;}
  rex::cvar::testing::ResetAllForTesting();
  const auto invalid=root/"invalid.toml";
  const std::string broken="config_regression_number = 99\nconfig_regression_text = \"{\"active\":false}\"\n";
  {std::ofstream f(invalid);f<<broken;}
  const auto failure=rex::cvar::LoadConfigWithResult(invalid);
  assert(failure.status==rex::cvar::ConfigLoadStatus::kError&&!failure.error.empty()&&failure.line==2&&failure.column>0);
  assert(rex::cvar::Query<int32_t>("config_regression_number")==17);
  assert(rex::cvar::Query<std::string>("config_regression_text")=="default");
  {std::ifstream f(invalid);assert(std::string(std::istreambuf_iterator<char>(f),{})==broken);}
  assert(rex::cvar::LoadConfigWithResult(root/"does-not-exist.toml").status==rex::cvar::ConfigLoadStatus::kNotFound);
  assert(rex::cvar::LoadConfigWithResult(root).status==rex::cvar::ConfigLoadStatus::kError);
  rex::cvar::testing::ResetAllForTesting();
  const auto valid=root/"precedence.toml";{std::ofstream f(valid);f<<"config_regression_number = 123\nconfig_regression_text = \"\\\"quoted\\\"\"\n";}
  char name[]="config-test",override[]="--config_regression_number=456";char* args[]={name,override};
  rex::cvar::Init(2,args);assert(rex::cvar::LoadConfigWithResult(valid).status==rex::cvar::ConfigLoadStatus::kLoaded);
  assert(rex::cvar::Query<int32_t>("config_regression_number")==456);
  assert(rex::cvar::Query<std::string>("config_regression_text")=="\"quoted\"");
  std::cout<<"Production CVar TOML round-trip: JSON, paths, Unicode, controls and injection boundaries passed\n";
}

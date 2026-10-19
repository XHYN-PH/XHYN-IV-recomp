#include <cassert>
#include <string>
#include <vector>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/diagnostics/policy.h>
namespace rex {spdlog::logger* GetLoggerRaw(LogCategoryId){return nullptr;} LogCategoryId RegisterLogCategory(const char*){return LogCategoryId(0);}}
REXCVAR_DEFINE_STRING(diagnostics_categories,"logging,native-profiler","Test","Saved categories");
REXCVAR_DEFINE_BOOL(diagnostics,true,"Test","Saved diagnostics");
REXCVAR_DEFINE_STRING(log_file,"","Test","Log destination");
REXCVAR_DEFINE_STRING(gta4_touch_camera,"stick","Test","Camera").allowed({"stick","swipe"});
static void parse(std::vector<std::string> args){std::vector<char*> raw;for(auto& arg:args)raw.push_back(arg.data());rex::cvar::Init(int(raw.size()),raw.data());}
int main(int,char**){
  parse({"launch","--log_file=/dev/null","--gta4_touch_camera=swipe"});
  assert(REXCVAR_GET(log_file)=="/dev/null"&&REXCVAR_GET(gta4_touch_camera)=="swipe");
  REXCVAR_SET(gta4_touch_camera,"stick");
  parse({"launch","--log_file=","--gta4_touch_camera=swipe"});
  assert(REXCVAR_GET(log_file).empty()&&REXCVAR_GET(gta4_touch_camera)=="swipe");
  parse({"launch","--log_file=","--log_file=/tmp/explicit.log","--gta4_touch_camera=stick"});
  assert(REXCVAR_GET(log_file)=="/tmp/explicit.log"&&REXCVAR_GET(gta4_touch_camera)=="stick");
  parse({"launch","--log_file=/tmp/old.log","--log_file=","--gta4_touch_camera=swipe"});
  assert(REXCVAR_GET(log_file).empty()&&REXCVAR_GET(gta4_touch_camera)=="swipe");
  parse({"launch","--diagnostics=false","--diagnostics_categories=","--log_file=/dev/null","--gta4_touch_camera=swipe"});
  assert(!REXCVAR_GET(diagnostics)&&REXCVAR_GET(diagnostics_categories).empty()&&REXCVAR_GET(log_file)=="/dev/null");
  rex::diagnostics::Policy policy;std::string error;assert(rex::diagnostics::ParsePolicy(REXCVAR_GET(diagnostics),REXCVAR_GET(diagnostics_categories),&policy,&error)&&!policy.enabled);
}

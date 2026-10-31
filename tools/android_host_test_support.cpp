// Host-only logging boundary; tests link the real production CVar registry.
#include <span>
#include <rex/logging.h>
namespace rex::diagnostics {bool IsEnabled(Category) noexcept{return false;}}
namespace rex {spdlog::logger* GetLoggerRaw(LogCategoryId){return nullptr;}}
namespace rex {LogCategoryId RegisterLogCategory(const char*){return LogCategoryId(0);}}
namespace rex {std::shared_ptr<spdlog::logger> GetLogger(){return nullptr;}}

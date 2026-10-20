#include <rex/profiling/android_pgo_dump.h>
#if defined(LIBERTY_PGO_GENERATE)
namespace { rex::profiling::AndroidPgoDumper pgo_dumper("rexgpu"); }
#endif

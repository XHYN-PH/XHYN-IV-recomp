// C11 threads (call_once, mtx_*, cnd_*, thrd_*, tss_*) for Android before 11.
//
// Bionic exports these from libc only since API 30. Turnip builds made with a
// newer NDK target reference them, so on Android 10 the driver fails to load
// ("cannot locate symbol call_once"). hook_impl preloads this library into the
// driver namespace with RTLD_GLOBAL on API < 30. The bodies are bionic's own:
// libc builds the same header with exported instead of inline functions.
//
// Compiled for API 30 (see CMakeLists.txt) so <threads.h> declares the
// functions instead of pulling in the static inline copies; the bodies only
// call pthread functions, which every supported API level has.
#define __BIONIC_THREADS_INLINE __attribute__((visibility("default")))
#include <bits/threads_inlines.h>

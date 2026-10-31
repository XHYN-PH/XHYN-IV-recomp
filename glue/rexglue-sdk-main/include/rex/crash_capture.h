#pragma once
#include <cstdint>

namespace rex::arch {
// Enable for an explicitly requested debug session, before guest threads start.
bool EnableCrashCapture(const char* path);
// A breadcrumb, not an unwinder or recovery handler. No guest, instruction or
// stack memory is read. Uses fixed buffers and async-signal-safe POSIX I/O.
void RecordFatalSignal(int signal, int code, uintptr_t address,
                       uintptr_t pc, uintptr_t sp, uintptr_t lr);
}  // namespace rex::arch

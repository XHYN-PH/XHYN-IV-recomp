#include <rex/crash_capture.h>
#include <atomic>
#include <cerrno>
#include <cstddef>
#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>

namespace rex::arch {
namespace {
static_assert(std::atomic<int>::is_always_lock_free);
std::atomic<int> capture_fd{-1};
std::atomic_flag recorded = ATOMIC_FLAG_INIT;
void Write(int fd, const char* data, size_t size) {
  while (size) {
    ssize_t count = ::write(fd, data, size);
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0) break;
    data += count;
    size -= size_t(count);
  }
}
}  // namespace

bool EnableCrashCapture(const char* path) {
  if (!path || !*path || capture_fd.load(std::memory_order_relaxed) >= 0) return false;
  int fd = ::open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
  if (fd < 0) return false;
  capture_fd.store(fd, std::memory_order_relaxed);
  return true;
}

void RecordFatalSignal(int signal, int code, uintptr_t address,
                       uintptr_t pc, uintptr_t sp, uintptr_t lr) {
  const int saved_errno = errno;
  const int fd = capture_fd.load(std::memory_order_relaxed);
  if (fd < 0 || recorded.test_and_set(std::memory_order_relaxed)) return;
  char buffer[768];
  size_t length = 0;
  auto text = [&](const char* value) { while (*value) buffer[length++] = *value++; };
  auto field = [&](const char* name, uint64_t value) {
    text(name); text("=0x");
    constexpr char digits[] = "0123456789abcdef";
    for (int shift = 60; shift >= 0; shift -= 4) buffer[length++] = digits[(value >> shift) & 15];
    text("\n");
  };
  text("XHYN native fatal signal capture v1\n");
  field("signal", uint32_t(signal)); field("code", uint32_t(code));
  field("pid", uint32_t(::getpid()));
#ifdef SYS_gettid
  field("tid", uint32_t(::syscall(SYS_gettid)));
#endif
  field("fault_address", address); field("pc", pc); field("sp", sp); field("lr", lr);
  text("No stack unwinding performed. Original signal handler follows.\n--- mappings ---\n");
  Write(fd, buffer, length);
  // Read the actual modules at the fault, including drivers loaded after
  // startup. No dladdr, logger, allocator or stack walking is used here.
  int maps = ::open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
  if (maps >= 0) {
    char block[4096];
    size_t remaining = 1024 * 1024;
    while (remaining) {
      ssize_t count = ::read(maps, block, remaining < sizeof(block) ? remaining : sizeof(block));
      if (count < 0 && errno == EINTR) continue;
      if (count <= 0) break;
      Write(fd, block, size_t(count)); remaining -= size_t(count);
    }
    ::close(maps);
  }
  errno = saved_errno;
}
}  // namespace rex::arch

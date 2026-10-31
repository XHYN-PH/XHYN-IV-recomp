#include <rex/crash_capture.h>
#include <cassert>
#include <cerrno>
#include <csignal>
#include <fstream>
#include <iostream>
#include <iterator>
#include <string>
#include <sys/resource.h>
#include <sys/wait.h>
#include <ucontext.h>
#include <unistd.h>

static void Fault(int signal, siginfo_t* info, void* context) {
  auto& machine = static_cast<ucontext_t*>(context)->uc_mcontext;
#if defined(__x86_64__)
  uintptr_t pc = machine.gregs[REG_RIP], sp = machine.gregs[REG_RSP], lr = 0;
#elif defined(__aarch64__)
  uintptr_t pc = machine.pc, sp = machine.sp, lr = machine.regs[30];
#endif
  errno = EDOM;
  rex::arch::RecordFatalSignal(signal, info->si_code,
      reinterpret_cast<uintptr_t>(info->si_addr), pc, sp, lr);
  if (errno != EDOM) _exit(101);
  rex::arch::RecordFatalSignal(signal, 999, 0xDEADBEEF, 0, 0, 0);
  ::signal(signal, SIG_DFL);
}

int main(int argc, char** argv) {
  assert(argc == 2);
  errno = EDOM;
  rex::arch::RecordFatalSignal(SIGSEGV, 0, 0, 0, 0, 0);
  assert(errno == EDOM);
  assert(!rex::arch::EnableCrashCapture(""));
  std::string path = argv[1];
  ::unlink(path.c_str());
  pid_t child = fork();
  assert(child >= 0);
  if (!child) {
    rlimit limit{0, 0}; setrlimit(RLIMIT_CORE, &limit);
    if (!rex::arch::EnableCrashCapture(path.c_str())) _exit(102);
    struct sigaction action{};
    action.sa_sigaction = Fault; action.sa_flags = SA_SIGINFO;
    sigemptyset(&action.sa_mask);
    if (sigaction(SIGSEGV, &action, nullptr)) _exit(103);
    volatile uintptr_t invalid = 0;
    *reinterpret_cast<volatile int*>(invalid) = 42;
    _exit(104);
  }
  int status = 0;
  assert(waitpid(child, &status, 0) == child);
  assert(WIFSIGNALED(status) && WTERMSIG(status) == SIGSEGV);
  std::ifstream input(path);
  std::string text{std::istreambuf_iterator<char>(input), {}};
  assert(text.find("signal=0x000000000000000b") != std::string::npos);
  assert(text.find("fault_address=0x0000000000000000") != std::string::npos);
  assert(text.find("pc=0x0000000000000000") == std::string::npos);
  assert(text.find("--- mappings ---") != std::string::npos);
  assert(text.find("native-fatal-capture") != std::string::npos);
  assert(text.find("deadbeef") == std::string::npos);
  assert(text.size() < 1024 * 1024 + 768);
  ::unlink(path.c_str());
  std::cout << "Native fatal capture: real SIGSEGV, kernel PC/address, module maps, errno, single record and fatal termination preserved\n";
}

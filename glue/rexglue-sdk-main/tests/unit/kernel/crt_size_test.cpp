// Execute the real exported CRT hooks with unspecified upper GPR bits. Guarded
// child processes keep a bad multi-gigabyte length from killing the test runner.
#if defined(__linux__)
#include <array>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>
#include <catch2/catch_test_macros.hpp>
#include <rex/ppc/context.h>

#define CRT_DECLARE(name) extern "C" void rexcrt_##name(PPCContext&, uint8_t*)
CRT_DECLARE(memcpy); CRT_DECLARE(memmove); CRT_DECLARE(memset); CRT_DECLARE(memchr);
CRT_DECLARE(XMemCpy); CRT_DECLARE(XMemSet); CRT_DECLARE(XMemSet128); CRT_DECLARE(memset_vmx);
CRT_DECLARE(memcpy_s); CRT_DECLARE(memmove_s);
CRT_DECLARE(strncmp); CRT_DECLARE(strncpy); CRT_DECLARE(strcpy_s); CRT_DECLARE(wcsncpy_s);
#undef CRT_DECLARE
namespace {
using Hook = void (*)(PPCContext&, uint8_t*);
constexpr uint64_t Poison(uint32_t word, uint32_t high = 0xABCDEF01) {
  return (uint64_t(high) << 32) | word;
}
template <typename Body> bool Child(Body body) {
  pid_t pid = fork();
  if (pid < 0) return false;
  if (!pid) {
    rlimit limit{0, 0}; setrlimit(RLIMIT_CORE, &limit);
    auto* base = static_cast<uint8_t*>(mmap(nullptr, 0x20000, PROT_NONE,
        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    if (base == MAP_FAILED || mprotect(base, 0x10000, PROT_READ | PROT_WRITE)) _exit(90);
    std::memset(base, 0xCC, 0x10000);
    PPCContext ctx{};
    ctx.r3.u64 = Poison(0x100);
    ctx.r4.u64 = Poison(0x200);
    ctx.r5.u64 = Poison(16);
    bool ok = body(ctx, base);
    munmap(base, 0x20000);
    _exit(ok ? 0 : 91);
  }
  int status;
  while (waitpid(pid, &status, 0) < 0) if (errno != EINTR) return false;
  return WIFEXITED(status) && WEXITSTATUS(status) == 0;
}
}
TEST_CASE("CRT memset variants use the guest low length word", "[kernel][crt][abi]") {
  for (Hook hook : {rexcrt_memset, rexcrt_XMemSet, rexcrt_XMemSet128, rexcrt_memset_vmx}) {
    for (uint32_t count : {0u, 16u}) {
      CHECK(Child([=](PPCContext& ctx, uint8_t* base) {
        ctx.r4.u64 = Poison(0x5A); ctx.r5.u64 = Poison(count);
        hook(ctx, base);
        for (unsigned i = 0; i < 32; ++i) {
          if (base[0x100 + i] != (i < count ? 0x5A : 0xCC)) return false;
        }
        return ctx.r3.u32 == 0x100 && base[0xFF] == 0xCC;
      }));
    }
  }
}
TEST_CASE("CRT copy variants bound the native transfer to the guest count", "[kernel][crt][abi]") {
  for (Hook hook : {rexcrt_memcpy, rexcrt_memmove, rexcrt_XMemCpy}) {
    CHECK(Child([=](PPCContext& ctx, uint8_t* base) {
      std::memcpy(base + 0x200, "ABCDEFGHIJKLMNOP", 16);
      hook(ctx, base);
      return ctx.r3.u32 == 0x100 && std::memcmp(base + 0x100, base + 0x200, 16) == 0
          && base[0xFF] == 0xCC && base[0x110] == 0xCC;
    }));
  }
}
TEST_CASE("CRT memchr does not search beyond the guest count", "[kernel][crt][abi]") {
  CHECK(Child([](PPCContext& ctx, uint8_t* base) {
    ctx.r4.u64 = Poison(0x5A); base[0x114] = 0x5A;
    rexcrt_memchr(ctx, base);
    return ctx.r3.u64 == 0;
  }));
}
TEST_CASE("CRT secure copy variants decode both guest size arguments", "[kernel][crt][abi]") {
  for (Hook hook : {rexcrt_memcpy_s, rexcrt_memmove_s}) {
    for (uint32_t count : {8u, 17u}) {
      CHECK(Child([=](PPCContext& ctx, uint8_t* base) {
        ctx.r4.u64 = Poison(16, 0x200); ctx.r5.u64 = Poison(0x200);
        ctx.r6.u64 = Poison(count, 0x100);
        std::memcpy(base + 0x200, "ABCDEFGHIJKLMNOP", 16);
        hook(ctx, base);
        if (count > 16) return ctx.r3.u32 == 22 && base[0x100] == 0xCC;
        return ctx.r3.u32 == 0 && std::memcmp(base + 0x100, base + 0x200, count) == 0
            && base[0x100 + count] == 0xCC;
      }));
    }
  }
}
TEST_CASE("CRT narrow string operations use guest bounds", "[kernel][crt][abi]") {
  CHECK(Child([](PPCContext& ctx, uint8_t* base) {
    std::memcpy(base + 0x100, "abcQ", 5); std::memcpy(base + 0x200, "abcZ", 5);
    ctx.r5.u64 = Poison(3); rexcrt_strncmp(ctx, base);
    return ctx.r3.u32 == 0;
  }));
  CHECK(Child([](PPCContext& ctx, uint8_t* base) {
    std::memcpy(base + 0x200, "abcdef", 7); ctx.r5.u64 = Poison(3);
    rexcrt_strncpy(ctx, base);
    return ctx.r3.u32 == 0x100 && std::memcmp(base + 0x100, "abc", 3) == 0
        && base[0x103] == 0xCC;
  }));
  CHECK(Child([](PPCContext& ctx, uint8_t* base) {
    std::memcpy(base + 0x200, "abcdef", 7);
    ctx.r4.u64 = Poison(3); ctx.r5.u64 = Poison(0x200);
    rexcrt_strcpy_s(ctx, base);
    return ctx.r3.u32 == 34 && base[0x100] == 0 && base[0x101] == 0xCC;
  }));
}
TEST_CASE("CRT wide string copy decodes guest capacity and count", "[kernel][crt][abi]") {
  CHECK(Child([](PPCContext& ctx, uint8_t* base) {
    auto* source = reinterpret_cast<int16_t*>(base + 0x200);
    const int16_t input[] = {0x41, 0x42, 0x43, 0x44, 0};
    std::memcpy(source, input, sizeof(input));
    ctx.r4.u64 = Poison(4); ctx.r5.u64 = Poison(0x200); ctx.r6.u64 = Poison(2);
    rexcrt_wcsncpy_s(ctx, base);
    auto* dst = reinterpret_cast<int16_t*>(base + 0x100);
    return ctx.r3.u32 == 0 && dst[0] == 0x41 && dst[1] == 0x42 && dst[2] == 0
        && uint16_t(dst[3]) == 0xCCCC;
  }));
}
#endif

/**
 * Tests for ArgTranslator stack argument support and ImportFunction isolation.
 */

#include <cstdint>
#include <cstring>

#include <catch2/catch_test_macros.hpp>

#include <rex/hook.h>
#include <rex/ppc/context.h>
#include <rex/ppc/function.h>

using namespace rex::ppc;

// Fake 64KB guest memory block for tests
alignas(64) static uint8_t g_test_mem[0x10000] = {};

TEST_CASE("SetIntegerArgumentValue writes stack args for index > 7", "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r1.u32 = 0x8000;

  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 8, 0xDEADBEEF);

  uint64_t readback = ArgTranslator::GetIntegerArgumentValue(ctx, g_test_mem, 8);
  CHECK(readback == 0xDEADBEEF);
}

TEST_CASE("SetIntegerArgumentValue writes multiple stack args at correct offsets",
          "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r1.u32 = 0x8000;
  std::memset(g_test_mem + 0x8000, 0, 0x100);

  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 8, 0x11111111);
  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 9, 0x22222222);
  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 10, 0x33333333);

  CHECK(ArgTranslator::GetIntegerArgumentValue(ctx, g_test_mem, 8) == 0x11111111);
  CHECK(ArgTranslator::GetIntegerArgumentValue(ctx, g_test_mem, 9) == 0x22222222);
  CHECK(ArgTranslator::GetIntegerArgumentValue(ctx, g_test_mem, 10) == 0x33333333);
}

TEST_CASE("SetIntegerArgumentValue still works for register args 0-7", "[ppc][arg_translator]") {
  PPCContext ctx{};
  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 0, 0xAAAA);
  ArgTranslator::SetIntegerArgumentValue(ctx, g_test_mem, 7, 0xBBBB);
  CHECK(ctx.r3.u64 == 0xAAAA);
  CHECK(ctx.r10.u64 == 0xBBBB);
}

namespace {

uint64_t EchoIdentifier(uint64_t identifier) { return identifier; }

void CheckWideGuestArguments(PPCContext& ctx, uint8_t* base) {
  // Inspect the PPC ABI directly, independently of ArgTranslator's reader.
  CHECK(ctx.r3.u32 == 1);
  CHECK(ctx.r10.u64 == 0x8877665544332211ull);
  const uint8_t expected[] = {0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF};
  CHECK(std::memcmp(base + ctx.r1.u32 + 0x50, expected, sizeof(expected)) == 0);
  const uint8_t next[] = {0xCA, 0xFE, 0xBA, 0xBE};
  CHECK(std::memcmp(base + ctx.r1.u32 + 0x5C, next, sizeof(next)) == 0);
  CHECK(base[ctx.r1.u32 + 0x4F] == 0xA5);
  CHECK(base[ctx.r1.u32 + 0x60] == 0xA5);
  ctx.r3.u64 = 0xFEDCBA9876543210ull;
}

}  // namespace

TEST_CASE("Native hooks receive the full 64-bit identifier", "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r3.u64 = 0xE0000123456789ABull;
  HostToGuestFunction<&EchoIdentifier>(ctx, g_test_mem);
  CHECK(ctx.r3.u64 == 0xE0000123456789ABull);
}

TEST_CASE("64-bit native arguments preserve every integer register", "[ppc][arg_translator]") {
  PPCContext ctx{};
  const uint64_t expected[] = {0x0123456789ABCDEFull, 0xFEDCBA9876543210ull,
                             0x8000000000000000ull, 0xFFFFFFFFFFFFFFFFull,
                             0x100000000ull, 0x8000000080000000ull,
                             0x1122334455667788ull, 0x8877665544332211ull};
  ctx.r3.u64 = expected[0];
  ctx.r4.u64 = expected[1];
  ctx.r5.u64 = expected[2];
  ctx.r6.u64 = expected[3];
  ctx.r7.u64 = expected[4];
  ctx.r8.u64 = expected[5];
  ctx.r9.u64 = expected[6];
  ctx.r10.u64 = expected[7];
  for (size_t i = 0; i < 8; ++i) {
    CHECK(ArgTranslator::GetValue<uint64_t>(ctx, g_test_mem, i) == expected[i]);
  }
  CHECK(ArgTranslator::GetValue<int64_t>(ctx, g_test_mem, 3) == -1);
}

TEST_CASE("64-bit native arguments read complete big-endian stack slots",
          "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r1.u32 = 0x8000;
  const uint8_t slots[] = {0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF,
                          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE};
  std::memcpy(g_test_mem + ctx.r1.u32 + 0x50, slots, sizeof(slots));
  CHECK(ArgTranslator::GetValue<uint64_t>(ctx, g_test_mem, 8) == 0x0123456789ABCDEFull);
  CHECK(ArgTranslator::GetValue<int64_t>(ctx, g_test_mem, 9) == -2);
  // A 32-bit argument occupies the low word of the same eight-byte ABI slot.
  CHECK(ArgTranslator::GetValue<uint32_t>(ctx, g_test_mem, 8) == 0x89ABCDEF);
  CHECK(ArgTranslator::GetValue<int32_t>(ctx, g_test_mem, 9) == -2);
}

TEST_CASE("Typed imports send full 64-bit register and stack arguments",
          "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r1.u32 = 0x8000;
  std::memset(g_test_mem + ctx.r1.u32, 0xA5, 0x100);
  ImportFunction<uint64_t(uint32_t, uint32_t, uint32_t, uint32_t, uint32_t,
                          uint32_t, uint32_t, uint64_t, uint64_t, uint32_t)>
      call{CheckWideGuestArguments};
  CHECK(call(ctx, g_test_mem, 1, 2, 3, 4, 5, 6, 7, 0x8877665544332211ull,
             0x0123456789ABCDEFull, 0xCAFEBABE) == 0xFEDCBA9876543210ull);
  CHECK(ctx.r1.u32 == 0x8000);
}

TEST_CASE("Narrow native arguments ignore unspecified upper register bits",
          "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r3.u64 = 0xDEADBEEF00000000ull;
  ctx.r4.u64 = 0xABCDEF0180000000ull;
  ctx.r5.u64 = 0xAABBCCDD00001234ull;
  CHECK(ArgTranslator::GetValue<uint32_t>(ctx, g_test_mem, 0) == 0);
  CHECK_FALSE(ArgTranslator::GetValue<bool>(ctx, g_test_mem, 0));
  CHECK(ArgTranslator::GetValue<void*>(ctx, g_test_mem, 0) == nullptr);
  CHECK_FALSE(ArgTranslator::GetValue<mapped_void>(ctx, g_test_mem, 0));
  CHECK(ArgTranslator::GetValue<int32_t>(ctx, g_test_mem, 1) == INT32_MIN);
  CHECK(ArgTranslator::GetValue<void*>(ctx, g_test_mem, 2) == g_test_mem + 0x1234);
  CHECK(ArgTranslator::GetValue<mapped_void>(ctx, g_test_mem, 2).guest_address() == 0x1234);
}

TEST_CASE("Big-endian scalar native arguments keep numeric value and storage order",
          "[ppc][arg_translator]") {
  PPCContext ctx{};
  ctx.r3.u64 = 0x0123456789ABCDEFull;
  const auto wide = ArgTranslator::GetValue<be_u64>(ctx, g_test_mem, 0);
  CHECK(static_cast<uint64_t>(wide) == ctx.r3.u64);
  const uint8_t expected[] = {0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF};
  CHECK(std::memcmp(&wide, expected, sizeof(expected)) == 0);
  ctx.r4.u32 = 0x12345678;
  CHECK(static_cast<uint32_t>(ArgTranslator::GetValue<be_u32>(ctx, g_test_mem, 1)) ==
        0x12345678);
  ctx.r1.u32 = 0x8000;
  ArgTranslator::SetValue(ctx, g_test_mem, 8, wide);
  CHECK(std::memcmp(g_test_mem + ctx.r1.u32 + 0x50, expected, sizeof(expected)) == 0);
  ArgTranslator::SetValue(ctx, g_test_mem, 9, int64_t{-2});
  const uint8_t negative[] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFE};
  CHECK(std::memcmp(g_test_mem + ctx.r1.u32 + 0x58, negative, sizeof(negative)) == 0);
}

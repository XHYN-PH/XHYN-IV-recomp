/**
 * @file        kernel/crt/memory.cpp
 *
 * @brief       Native memory operation hooks -- replaces recompiled PPC
 *              implementations of memcpy, memmove, memset, etc.
 *
 * @copyright   Copyright (c) 2026 Tom Clay <tomc@tctechstuff.com>
 * @license     BSD 3-Clause License
 */
#include <cstdint>
#include <cstring>

#include <rex/hook.h>

namespace rex::kernel::crt {

// ---------------------------------------------------------------------------
// Xbox size_t arguments are 32-bit even on a 64-bit host. Keep the guest
// width in every hooked signature; conversion to host size_t happens inside
// the C library call, after the PPC argument has been decoded.
// Standard memory operations
// ---------------------------------------------------------------------------

static void* native_memcpy(void* dst, const void* src, uint32_t n) {
  return std::memcpy(dst, src, n);
}

static void* native_memmove(void* dst, const void* src, uint32_t n) {
  return std::memmove(dst, src, n);
}

static void* native_memset(void* dst, int val, uint32_t n) {
  return std::memset(dst, val, n);
}

static void* native_memchr(const void* ptr, int val, uint32_t n) {
  return const_cast<void*>(std::memchr(ptr, val, n));
}

// ---------------------------------------------------------------------------
// Xbox/VMX-optimized variants (same semantics, native speed)
// ---------------------------------------------------------------------------

static void* native_XMemCpy(void* dst, const void* src, uint32_t n) {
  return std::memcpy(dst, src, n);
}

static void* native_XMemSet(void* dst, int val, uint32_t n) {
  return std::memset(dst, val, n);
}

static void* native_XMemSet128(void* dst, int val, uint32_t n) {
  return std::memset(dst, val, n);
}

static void* native_memset_vmx(void* dst, int val, uint32_t n) {
  return std::memset(dst, val, n);
}

// ---------------------------------------------------------------------------
// Secure variants (return errno_t)
// ---------------------------------------------------------------------------

static int native_memcpy_s(void* dst, uint32_t dstsz, const void* src, uint32_t count) {
  if (!dst || !src || count > dstsz)
    return 22;  // EINVAL
  std::memcpy(dst, src, count);
  return 0;
}

static int native_memmove_s(void* dst, uint32_t dstsz, const void* src, uint32_t count) {
  if (!dst || !src || count > dstsz)
    return 22;  // EINVAL
  std::memmove(dst, src, count);
  return 0;
}

}  // namespace rex::kernel::crt

REX_HOOK(rexcrt_memcpy, rex::kernel::crt::native_memcpy)
REX_HOOK(rexcrt_memmove, rex::kernel::crt::native_memmove)
REX_HOOK(rexcrt_memset, rex::kernel::crt::native_memset)
REX_HOOK(rexcrt_memchr, rex::kernel::crt::native_memchr)
REX_HOOK(rexcrt_XMemCpy, rex::kernel::crt::native_XMemCpy)
REX_HOOK(rexcrt_XMemSet, rex::kernel::crt::native_XMemSet)
REX_HOOK(rexcrt_XMemSet128, rex::kernel::crt::native_XMemSet128)
REX_HOOK(rexcrt_memset_vmx, rex::kernel::crt::native_memset_vmx)
REX_HOOK(rexcrt_memcpy_s, rex::kernel::crt::native_memcpy_s)
REX_HOOK(rexcrt_memmove_s, rex::kernel::crt::native_memmove_s)

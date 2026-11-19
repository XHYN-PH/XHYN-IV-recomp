# Test14 CRT length fix candidate

Version `0.5.9.3-xhyn-r2-test14`, Android version code 42. This candidate fixes
the reproduced native CRT argument-width regression and retains Test13 crash
capture. Phone confirmation is still required; the public release remains Test12.

## Fault and correction

The supplied Test13 fatal capture locates the instruction in Android libc and
the return address at `librexruntime.so + 0x7AA214`. The preceding call at
`+0x7AA210` resolves to the production native `memset` hook. The write reached a
protected guest mapping at guest address `0x70480000`. This is a runtime memory
operation; the capture does not locate the fault in a graphics driver.

The earlier bridge correction began honoring native parameter widths. CRT hooks
still declared guest lengths as host `size_t`, which is 64-bit on Android ARM64
but 32-bit for the Xbox guest. Unspecified upper GPR bits could become part of
the native operation length. A small guest count could consequently turn into a
multi-gigabyte operation and cross a protected page.

Use `uint32_t` at all 14 affected CRT memory/string hook boundaries, including
both sizes in secure variants. Conversion to host `size_t` happens inside the C
library call. Genuine 64-bit identifiers and sizes in the kernel bridge retain
their full width. No arbitrary length cap or guest-memory permission change is
used to conceal the fault.

## Validation

- Six new cases execute all 14 real exported hooks in guarded child processes
  with poisoned upper GPR bits. All 20 behavioral checks fail with the old
  signatures; all pass after the correction, including zero-length, canary,
  capacity, search-limit and wide-string cases.
- The complete production core suite passes 45 cases / 192 assertions with
  ASan and strict UBSan. Existing genuine 64-bit argument tests continue to pass.
- The source/binary audit regressions pass 22 cases. The source gate now rejects
  host-sized parameters in native CRT hooks and retains explicit 64-bit imports.
- Full Android ARM64/API 29 game/runtime/renderers/installer build passes with
  NDK r29. Android UI regression tests pass 24 cases.

The local reproduction and binary fault site support this correction. Confirm
it on the phone with the same gameplay route before marking the reported crash
resolved. Keep logging enabled for that comparison and share the session ZIP if
it fails again.

## Test15 follow-up

The November 5 Motorola Edge 50 Ultra session used Qualcomm's system Vulkan
driver. The November 6 Xiaomi 23076RN4BI session used Turnip v4.5. Both ran
Test12 (40) and crashed in `__memset_aarch64`. The two November 6 ZIPs have the
same SHA-256 and contain one session. Both sessions match the S24 FE report
with RADV 1.8 and 1.9 at the same application frames:

- `librexruntime.so + 0x7a99cc` in `rexcrt_memset`, Build ID
  `75ece6aa4933d9ad7bcc2f9be8c4f2111627c0f8`.
- `libmain.so + 0xe10334` in `sub_82A08550`, Build ID
  `dd0a229784ac8c50e30f11cbe32a30c049c2e6cf`.

Disassembly of the matching Test12 APK shows that the caller negates a
zero-extended negative string field width and keeps the upper bits when it
sets the `memset` length register. For example, negating `0x00000000fffffff0`
produces `0xffffffff00000010`, with an Xbox length of 16. Test12 loads the
whole register into the host length. The fixed runtime loads the low word.
The example comes from the caller's instructions. The November logs do not
contain the register values for those sessions.

Test15 packages the verified Test14 native runtime from the original
LibertyRecomp source branch and adds a regression for this padding operation
at a protected page. The test rejects the Test12 hook with an AddressSanitizer
length error and passes with the corrected hook. All 46 core cases (193
assertions), 22 audit cases, 11 Java fixture groups and 24 Android UI tests pass.
The native SIGSEGV capture check also passes.

Version code 445 allows an in-place update over XHYN installations through
code 444 when signed with the XHYN release key. The unsigned package passed
CRC, 16 KiB alignment and native/asset verification. Repeating the gameplay
route on hardware will confirm the reported crash on each device.

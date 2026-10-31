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

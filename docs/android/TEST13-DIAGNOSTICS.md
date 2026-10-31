# Test13 diagnostic candidate

Version `0.5.9.3-xhyn-r2-test13-diagnostics`, Android version code 41. The public
release remains Test12. This candidate collects evidence for the reported native
gameplay crash; the underlying fault is still unidentified.

## Evidence and changes

The supplied Test12 session ends abruptly after approximately 57 seconds on
Android 16 / Adreno 750 with ESMERINGHOY. It contains no native backtrace or fault
address. Its exit-summary context predates this session. The user also reports
a crash with the bundled Vauzi driver, so an ESMERINGHOY-specific cause is not
established. Last retained samples show no memory-pressure flag; they do not
identify the cause or exclude every allocation failure.

- When logging is explicitly enabled, pre-open a dedicated native fault file.
  On an unhandled SIGSEGV/SIGILL, record kernel PC, SP, LR, fault address, signal code,
  process/thread IDs and bounded current module mappings before forwarding to
  the existing signal handler. The recorder uses fixed buffers and POSIX I/O,
  performs no stack unwinding, and does not recover from a fault.
- Match crash-report excerpts to the capture's PID and recent write time in
  external or fallback `debug_logs`. Support rotated log ownership and read the
  actual end of a large log. Do not attach unrelated legacy-folder logs.
- Include installed app/device metadata and label absent Android memory samples.
  Decode and retain available system tombstones for signal exits too.
- Refresh Android exit summaries when returning to the launcher and before
  exporting, then include matching saved crash reports and raw tombstones in
  the selected session ZIP. Other sessions' crash reports remain excluded.

## Validation

- A forked host process incurs a real SIGSEGV. The production recorder retains
  its kernel PC, zero fault address and module maps; preserves errno and the
  first record; and leaves the process terminated by SIGSEGV.
- 24 Android UI/regression cases passed, including five new capture-selection,
  rotation, fallback-storage and large-log cases.
- 11 Java fixture groups passed, including matching-crash export and byte-exact
  preservation of a raw tombstone larger than the text-export limit.
- Full Android ARM64/API 29 game/runtime/renderer/installer build passed with
  NDK r29. The app resolves the recorder through the shared runtime.

The native recorder runs after the runtime's guest-fault handlers decline a
signal. A fault before capture initialization or inside another signal handler
may still require Android's system tombstone. No device crash fix or performance
gain is claimed from these checks.

## Capture the next crash

Install the signed candidate over the current app, keep the same settings, and
use **Logs → Play with logs**. After the crash, reopen the launcher and use
**Logs → Share report**. The selected session ZIP includes any generated
`.runtime.log.fatal.txt` alongside its normal logs and available system trace.
Do not uninstall or erase game/save data for this test.

# ObsiLauncher v1.13.1 — the "Exit code: -1" fix

## Fixed

- **The game JVM finally opens — "Exit code: -1" is gone.** Root cause found by
  comparing the launch chain line-by-line against ZalithLauncher: our code
  deleted `latestlog.txt` and then called the vendored `Logger.begin()`, whose
  native `open()` uses `O_TRUNC` **without** `O_CREAT` — so opening the log
  threw `IOException` on every single launch, *before the JVM ever booted*,
  and the catch path reported the generic `Exit code: -1`. Zalith calls
  `createNewFile()` first for exactly this reason; we now do the same.
- **Crash reports now show the real reason.** The crash-dialog excerpt used to
  be read from the log file while the pipe was still draining (and boot
  failures never printed anything), so it showed a generic line. Boot
  failures now carry the exact error (`IOException: …`, `IllegalStateException: …`)
  directly into the dialog, and when the JVM produces no output at all the
  dialog falls back to the in-flight console tail (runtime self-check + JLI
  diagnostics).
- **Pre-boot runtime self-check.** Before booting, the launcher verifies
  `libjli.so` / `lib/server/libjvm.so` exist in the selected runtime and logs
  both paths; a broken runtime now says "reinstall it from Settings →
  Components" instead of a mysterious exit code.
- **Crash parser coverage** extended to JVM bootstrap errors ("Could not
  create the Java Virtual Machine", "Unrecognized option", "Could not reserve
  enough space", "JLI lib = NULL", …) and to the newest file in the game dir's
  `crash-reports/` folder; the tail fallback grew from 12 to 15 lines.

## Improved

- Zalith parity for the JVM environment: the `server/` libjvm directory now
  leads `LD_LIBRARY_PATH` (forked children like `jspawnhelper` inherit it),
  `-Duser.timezone` / `-Duser.language` / `-Duser.country` come from the
  device (Android gives the JVM no TZ env), and `-Dpojav.path.private.account`
  is provided for authlib-injector accounts.
- User-supplied JVM args are purged of flags the launcher generates itself
  (`-Xms/-Xmx`, renderer/freetype libnames, `java.library.path`, …) so they
  can no longer fight the generated ones.
- The logger thread gets a short drain window before the exit report is
  written, so the tail of the JVM output is no longer cut off.

## Notes

- Same build/verify chain as 1.13.0: one-chunk `compile+assembleRelease`,
  aapt2 badging + apksigner cert checks, 25 native libs, bundled Internal-21
  runtime verified by re-extracting the shipped tarballs.

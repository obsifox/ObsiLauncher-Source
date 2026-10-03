# ObsiLauncher v1.13.0

**The JVM finally opens.** v1.12.0 shipped a half-finished migration to the
ZalithLauncher-style in-process JVM boot: the shell still called a
`GameManager.launch(...)` that no longer existed, the UI never had a game
surface to hand to the JVM, and the pre-flight verified a runtime store the
boot path never read. This release completes that migration end to end and
closes the whole v1.13.0 feedback list.

## Fixed

- **JVM opens — the game actually starts.** Completed the in-process
  ZalithLauncher/PojavLauncher boot chain:
  - `GameManager.launch()` glue restored (PLAY → session → game surface →
    `JLI_Launch`);
  - new **GameScreen** with the render `SurfaceView`, live log overlay and a
    stop button — the surface boots the JVM exactly once;
  - the pre-flight and the boot path now use the SAME runtime store
    (ObsiComponents / Internal-*), so a verified device is really ready;
  - Zalith argument parity: `-Djava.home`, `-Djdk.lang.Process.launchMechanism=FORK`,
    `glfwstub.*` geometry, `ext.net.resolvPath`, `os.name/user.home/tmpdir`,
    `--add-exports` for the main-class package on Java 9+;
  - `LD_LIBRARY_PATH` rebuilt in the Zalith order (jli first), the whole JRE
    lib tree is pre-dlopened with the correct arch dir, and
    `libfreetype.so.6` is aliased for JREs that ship it that way;
  - bundled components + Internal-21 unpack on app start (they previously
    had no call site at all — a fresh install had no runtime);
  - native logcat tags (jrelog/LIBGL/NativeInput) stream into the console.
- **Crash reports, the briefed style.** Every game death (and boot failure)
  now opens the reference dialog: red "Game Crashed" headline, the extracted
  error in a scroll box, Copy Exit Code and Close.
- **Mods show up.** The content pane lists the folder the game REALLY uses
  (`game/mods` when the instance keeps one), re-scans every 2 s while open,
  and gains an **Import from device** button for local .jar/.zip mods.
- **Language switch no longer restarts the app.** system / فارسی / English
  now flip in place — the shell re-resolves every string live, RTL included.
- **Trailer replays after 10 seconds** (was 45 s) — photo → video cycle is
  much livelier; replays still start at 0:00 with the slow end fade.

## Components — the full shelf

Settings gained the **Components** tab: every runtime and file the launcher
can use, visible, downloadable and deletable in one place —
Internal-8 / Internal-17 / Internal-21 (bundled) / Internal-25,
authlib-injector, caciocavallo, caciocavallo 17, JNA, Launcher Components and
the LWJGL 3 Android fork (3.3.6, covering the 3.3.3 / 3.4.x game
requirements). Downloads unpack immediately and are picked up the next time
the game starts.

## Icons

- The app icon is now the **vanilla grass block** — a hand-rendered
  isometric 16×16-per-face pixel-art block with the classic palette.

## Internal

- Version 1.13.0 (11300), UA updated, update-gate runtime check aligned with
  the boot path (bundled Internal-21 first, catalog runtimes as fallback).

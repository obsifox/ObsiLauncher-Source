# Changes in 1.3.0 — Android launcher rewritten from scratch

## Android: a brand-new launcher

The Android app is now **written from scratch** — every file is ObsiLauncher's own
GPL-3.0-or-later code. The ZalithLauncher2 overlay (pinned upstream + patches) is
gone from the repository. `studio.obsifox.obsilauncher` and the public signing key
are unchanged, so 1.3.0 installs as a normal update over 1.2.0.

### The launcher engine (`android/app`)

- **Version manager** — Mojang piston-meta manifest with a 6-hour cache; releases,
  snapshots, old-beta and old-alpha are all installable. Client jar, libraries
  (rule evaluation + Maven repos), native extraction and the full assets pipeline
  are implemented in the app, with sha1 verification and retry.
- **Launch pipeline** — classpath + JVM args + standard game arguments, spawned as a
  child process through `execve("/system/bin/linker64", …)` (JNI bridge
  `libobsibridge.so`), with stdout/stderr piped into the new Console page.
- **Runtime packs** — the JVM/LWJGL/renderer glue ships as downloadable
  `.tar.xz` packs (format: `docs/RUNTIME-PACKS.md`); the launcher installs,
  validates and selects them.
- **Accounts** — offline player profiles with the game's own offline-UUID recipe.
  Free: no Microsoft account, no keys, no gates.

### The Obsi design (all screens, written fresh)

- Glass-dark Compose UI: Home, Versions, Accounts, Console, Settings, About
- **Version wallpapers**: official *Wilderness Bound* pack for Minecraft ≥ 1.12.2,
  official *Minecraft PC bundle* for older versions, the trailer as a live video
  background for the latest release, and a user background override
- **Obsi Dynamic theming** — the accent colour is extracted from the active wallpaper
- Factory defaults: dark glass, blur on; full Persian (FA) translation
- About screen: the single **MobileGlues** credit, no other projects referenced

### Size

The APK drops from ~192 MB (bundled upstream runtime) to **~9 MB** — runtimes are
packs you install per architecture.

## Desktop

The desktop launcher (Compose MP + core engine) is unchanged in this release.

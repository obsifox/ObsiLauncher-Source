# ObsiLauncher for Android (standalone, from scratch)

Since **1.3.0** the Android launcher is **written from scratch** — it is no longer an
overlay of ZalithLauncher2. Every file in this folder is ObsiLauncher's own code
(GPL-3.0-or-later), with the single rendering credit **MobileGlues** documented in
the app's About page.

## What is inside

| Path | What it does |
|---|---|
| `app/src/main/java/.../core/` | Launcher engine: Mojang manifest, version installer (client + libraries + natives + assets), launch pipeline, process manager |
| `app/src/main/java/.../obsi/` | The Obsi design: version wallpapers (Wilderness Bound ≥ 1.12.2 / PC bundle < 1.12.2 / trailer for the latest release), dynamic accent extraction |
| `app/src/main/java/.../ui/` | Glass-dark Compose UI: Home, Versions, Accounts, Console, Settings, About (EN + FA) |
| `app/src/main/cpp/obsibridge.c` | JNI bridge: `fork + execve("/system/bin/linker64", …)` to start the game JVM, log-pipe capture, kill/waitpid |
| `app/obsilauncher.jks` | Public release keystore — see [`KEYSTORE.md`](KEYSTORE.md) |

## Build

JDK 21 + Android SDK (platform 37.2, build-tools 37, NDK 25.2.9519653):

```bash
cd android
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/ObsiLauncher-<version>-<abi>.apk`.

## Runtime packs

The launcher itself manages versions, downloads and the process — the game runs on a
**runtime pack** (JVM for Android + LWJGL/MobileGlues natives). Pack format and
ready-made sources: [`docs/RUNTIME-PACKS.md`](../docs/RUNTIME-PACKS.md).

## Design

- Free software, no access keys, no locked features
- Factory defaults: dark glass theme, blur on, dynamic colours from the version wallpaper
- Offline player profiles (Microsoft login is not required and not implemented)
- The only third-party credit kept is **MobileGlues**

# ObsiLauncher for Android (standalone, from scratch)

Since **1.3.0** the Android launcher is **written from scratch** — it is no longer an
overlay of ZalithLauncher2. Every file in this folder is ObsiLauncher's own code
(GPL-3.0-or-later), with the single rendering credit **MobileGlues** documented in
the app's About page.

**1.4.0** adds built-in **Modrinth** (mods, modpacks, resource packs, shaders) with a
dependency-resolution engine, **Fabric / Forge / NeoForge / Quilt / OptiFine** loader
installs on top of any vanilla version, **Microsoft device-code login** and local-only
custom skins & capes, named **instances** with per-instance settings, and file
**fix & repair**.

## What is inside

| Path | What it does |
|---|---|
| `app/src/main/java/.../core/game/` | Mojang manifest, version installer (client + libraries + natives + assets), fix & repair, launch pipeline, process manager, JVM spawn helper |
| `app/src/main/java/.../core/loaders/` | Fabric / Forge / NeoForge / Quilt / OptiFine version lists and installers (official installers run headless on the runtime pack JVM) |
| `app/src/main/java/.../core/modrinth/` | Modrinth API v2 client, dependency resolver (required locked / optional tickable), installed-content manager with prerequisite locks, `.mrpack` modpack installer |
| `app/src/main/java/.../core/instance/` | Named instances with custom memory / JVM overrides |
| `app/src/main/java/.../core/auth/` | Microsoft device-code login (XBL → XSTS → Minecraft) |
| `app/src/main/java/.../core/cosmetics/` | Local-only custom skin & cape via CustomSkinLoader's LocalSkin provider |
| `app/src/main/java/.../obsi/` | The Obsi design: version wallpapers (Wilderness Bound ≥ 1.12.2 / PC bundle < 1.12.2 / trailer for the latest release), dynamic accent extraction |
| `app/src/main/java/.../ui/` | Glass-dark Compose UI: Home, Versions, Mods (Modrinth), Accounts, Console, Settings, About (EN + FA) |
| `app/src/main/cpp/obsibridge.c` | JNI bridge: `fork + execve("/system/bin/linker64", …)` to start the game JVM, log-pipe capture, kill/waitpid |
| `app/obsilauncher.jks` | Public release keystore — see [`KEYSTORE.md`](KEYSTORE.md) |

## Build

JDK 21 + Android SDK (platform 37.2, build-tools 37, NDK r27c):

```bash
cd android
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/ObsiLauncher-<version>-<abi>.apk`.

## Runtime packs

The launcher itself manages versions, downloads and the process — the game runs on a
**runtime pack** (JVM for Android + LWJGL/MobileGlues natives). Pack format and
ready-made sources: [`docs/RUNTIME-PACKS.md`](../docs/RUNTIME-PACKS.md). The pack's
JVM also runs the Forge/NeoForge installers and the OptiFine patcher.

## Design

- Free software, no access keys, no locked features
- Factory defaults: dark glass theme, blur on, dynamic colours from the version wallpaper
- Offline player profiles by default; Microsoft login is a free, optional extra
- The only third-party credit kept is **MobileGlues**

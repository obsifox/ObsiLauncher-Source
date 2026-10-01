<p align="center"><img src="docs/assets/logo.png" width="128" alt="ObsiLauncher"></p>

<h1 align="center">ObsiLauncher</h1>
<p align="center">A multi-profile launcher for <b>Minecraft: Java Edition</b> with built-in Modrinth, for <b>Android</b>, <b>Windows</b> and <b>Linux</b>.</p>
<p align="center"><a href="README.fa.md">فارسی</a></p>

> Private repository · version `0.1.0` (MVP). *Not affiliated with Mojang AB or Microsoft.*

## What you get

| | Android | Windows / Linux |
|---|---|---|
| Based on | ZalithLauncher2 `2.6.1` (GPL-3.0), rebranded by an overlay | Written from scratch (Kotlin + Compose Multiplatform) |
| Profiles (instances), each with its own mods/saves/settings | ✔ | ✔ |
| Vanilla, Fabric, Quilt, Forge, NeoForge | ✔ | ✔ (auto-installed) |
| Microsoft account + offline account | ✔ | ✔ (device-code sign-in) |
| Modrinth: search, install with dependencies, update check, `.mrpack` modpacks | ✔ | ✔ |
| Java runtime | bundled | downloaded from Mojang automatically |
| UI languages | EN + notice card in FA | **EN + FA (RTL, Vazirmatn font)** |
| Extras | renderers, controls, … (upstream) | live game console, crash dialog, BMCLAPI mirror + proxy, per-profile memory/JVM args/resolution/auto-join |

## Download

CI builds are published in the **[nightly pre-release](../../releases/tag/nightly)** (private repo → you must be logged in):

* `ObsiLauncher-<ver>-zl2.6.1-arm64-v8a.apk` – Android 8.0+ (arm64)
* `ObsiLauncher-<ver>-windows-x64.msi` or `…-portable.zip`
* `ObsiLauncher-<ver>-linux-x64.deb` or `…-portable.tar.gz`

Run **Actions → Android / Desktop → Run workflow** to produce fresh ones. See [docs/SETUP.md](docs/SETUP.md) for the optional secrets (Microsoft sign-in needs your own Azure app ID).

## Repository layout

```
core/      platform-independent Kotlin/JVM engine: Mojang manifests, downloads, Java runtimes, loaders,
           Modrinth, accounts, launch command builder, headless CLI + tests
desktop/   Compose Multiplatform UI (Windows/Linux) + jpackage config
android/   overlay + scripts that turn pinned ZalithLauncher2 into ObsiLauncher at CI time
tools/     icon generator
docs/      SETUP.md, ARCHITECTURE.md
.github/   workflows (manual / tag-triggered only) + publish script
```

Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Licensing: [NOTICE.md](NOTICE.md).

## Build locally

```bash
./gradlew :core:test                    # offline unit tests
./gradlew :desktop:run                  # start the desktop launcher (JDK 21)
./gradlew :desktop:createDistributable  # portable app image in desktop/build/compose/binaries
```
The Android APK is built in CI (it needs the Android SDK/NDK and ~1 GB of upstream runtimes): `bash android/scripts/prepare.sh && cd android/.work/upstream && ./gradlew ZalithLauncher:assembleRelease -Darch=arm64`.

## Status & known limits

* Microsoft login is **disabled until you add `MS_CLIENT_ID`** and Mojang approves it ([docs/SETUP.md](docs/SETUP.md)). Offline accounts work now.
* Desktop game launches were verified headlessly (vanilla, Fabric, Quilt, NeoForge, Forge start up to window creation); the UI has not been exercised on a real Windows machine yet.
* Android builds are CI-only; the APK installs on a real device but was not run in this environment.
* Not yet: CurseForge on desktop, skin management, server list, instance export, macOS packaging.

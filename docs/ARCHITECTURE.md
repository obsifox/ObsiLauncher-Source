# Architecture

ObsiLauncher ships as two products from one repository:

```
                     ┌────────────────────────────── desktop (Windows / Linux) ─────────────────────────────┐
 you ── GUI ───────▶ │ Compose Multiplatform UI  ──▶  AppController (state, tasks)  ──▶  core (engine)       │
                     └───────────────────────────────────────────────────────────────────────────────────────┘
 you ── GUI ───────▶  Android: pinned ZalithLauncher2 2.6.1  +  android/overlay  (patched & built in CI)
```

Why two? Android needs a Minecraft *runtime* (bundled JREs, GL translation layers, JNI glue, touch controls) that took
others years to build. Re-implementing it would be neither fast nor verifiable here, so the Android app is a
GPL-compliant **rebrand overlay** of ZalithLauncher2. The desktop is a clean engine + UI, because a JVM launcher
only has to download files and run `java`.

## `core/` – the engine (pure Kotlin/JVM, no UI)

| Package | Responsibility |
|---|---|
| `LauncherPaths`, `LauncherCore` | Data layout; `LauncherCore` wires all services (UI talks only to this + StateFlows). |
| `net` | `Http` (retries, proxy, BMCLAPI mirror candidates), `Downloader` (parallel, SHA-1 verified, atomic `.part` moves, progress). |
| `mojang` | Version manifest, JSON models, **inheritance resolver** (`inheritsFrom`, child libs win, args concatenated), OS/feature `Rules`, `GameInstaller` (client jar, libraries, legacy natives, asset index + objects, log config). |
| `java` | `JavaRuntimeManager`: downloads Mojang's per-version runtimes (`java-runtime-delta` …) or falls back to system Java. |
| `loaders` | Fabric & Quilt (meta servers give a ready version JSON), Forge & NeoForge (official installer jar run headless with the right Java). |
| `auth` | Microsoft device-code → Xbox Live → XSTS → Minecraft services; offline UUIDs (v3 of `OfflinePlayer:<name>`, identical to vanilla servers). |
| `modrinth` | API v2 client, `ContentManager` (install + required dependencies, enable/disable, hash-based update check), `ModpackInstaller` (`.mrpack`). |
| `launch` | `LaunchBuilder` (JVM/game args, placeholders, features, quick-play), `GameSession` (process, live log, log4j-XML → text). |
| `instance` | `Instance`/`Settings` models, JSON stores. |
| `cli` | Headless developer CLI (`versions`, `create`, `launch`, `search`), used for smoke tests. |

### On-disk layout (`%APPDATA%\ObsiLauncher` or `~/.local/share/ObsiLauncher`, override: `OBSI_HOME`)

```
shared/{versions,libraries,assets}/   one copy of game files for every profile (Mojang layout, so loader installers work)
runtime/<component>/<platform>/       Mojang-provided Java runtimes
instances/<id>/instance.json          profile settings
instances/<id>/game/                  the game directory: mods/, saves/, resourcepacks/, options.txt …
instances/<id>/obsi-content.json      what came from Modrinth (ids, versions, hashes) – drives updates
accounts.json (0600)  settings.json  cache/  logs/
```

### Launch pipeline

`prepare()`: ensure loader installed → resolve version chain → download missing libs/assets (cheap no-op if present) →
pick Java (override → Mojang runtime → system) → refresh the Microsoft token if < 5 min left → build the command.
`start()`: spawn, stream output into a ring buffer (4000 lines) + `logs/<id>-latest.log`, record play time on exit.

## `desktop/`

`AppController` owns navigation, a task list (progress, cancel, error), running sessions and a small image cache.
Screens (`Home`, `Instances`, `InstanceDetail`, `Browse`, `Accounts`, `Settings`, `Console`) are stateless composables over `StateFlow`s.
`Strings` + `StringTables` hold EN/FA text; `Lang.rtl` flips `LocalLayoutDirection`, so the whole UI mirrors for Persian.

## `android/`

`prepare.sh` shallow-fetches the pinned upstream commit, `patch.py` applies anchored edits that **fail the build if upstream drifts**
(application id, update-check URLs, home card) and copies the overlay (notice card, strings, icons). `android.yml` builds with
upstream's own Gradle project (`-Plauncher_name=…`) and signs with our keystore. Bumping upstream = edit `android/upstream.version`.

## CI

Workflows are `workflow_dispatch` + tag `v*` only (private-repo minutes are limited). Artifacts expire after 3 days; durable outputs go
to the `nightly` pre-release (or the tag's release) through `.github/scripts/publish.sh`.

## Robustness notes (learned the hard way)

* Mojang lists some libraries several times per version (LWJGL 3.2.x: a mac-only jar, the real jar, an entry carrying natives). Libraries are filtered by OS rules *first*, and only a **child** version may override its parent's library - never entries of the same file.
* Forge/NeoForge installers write the version JSON before their long processors finish, so an installation counts as complete only when our `.obsi-installed` marker exists.
* Loader lists are sorted semantically (Quilt's API order is not by version).
* On Windows a profile path with non-ASCII letters (e.g. a Persian user name) breaks native loading in many JVM/LWJGL combinations, so the data folder then falls back to `C:\Users\Public\ObsiLauncher`.
* A game stopped from the launcher is not reported as a crash; any other non-zero exit shows the crash dialog with the log tail.

## Testing

* `core:test` – offline unit tests (rules, resolver, launch command, offline UUID, log parsing, NeoForge version math).
* `-Pnet=true` – integration tests against the live Mojang / Modrinth / Fabric / Forge servers, including real installs.
* `obsi-cli launch` – real game start-up; on a machine without a display the game reaches window creation and stops there, which proves the command line.

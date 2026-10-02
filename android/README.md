# ObsiLauncher for Android

This folder does **not** contain the Android app's source. It contains what is needed to produce it from a pinned upstream:

| File | Purpose |
|---|---|
| `upstream.version` | Upstream repo, release tag and commit that CI fetches. |
| `scripts/prepare.sh` | Shallow-fetches that commit into `android/.work/upstream` and runs `patch.py`. |
| `scripts/patch.py` | Anchored text replacements (fails loudly if upstream changed) + copies `overlay/`. |
| `overlay/` | Files that are added/overwritten: the *Unofficial Modified Version* card (EN/FA), icons, string resources, **Obsidian Fox theme** (`ui/theme/*.kt`, settings screen), notice card v2. |

What the overlay changes: application id `studio.obsifox.obsilauncher`, name "Obsi Launcher", fox icons, a permanent
*unofficial* card on the home screen (required by upstream's additional license terms), update-check URLs that no longer
point at upstream, and the **Obsidian Fox** colour palette (warm charcoal + fox orange) registered in the upstream
theme picker. Kotlin packages and the Android `namespace` are left untouched on purpose (the native JNI symbols depend on them).

> The overlay theme files (`ui/theme/Color.kt`, `ColorTheme.kt`, `Theme.kt`, `NativeThemeUtils.kt` and
> `ui/screens/content/settings/LauncherSettingsScreen.kt`) are full replacements generated from the pinned upstream
> commit — regenerate them with `scripts/gen_mobile_theme.py` after bumping `upstream.version`.

## The glass-dark design (v1.2.0)

The new design is applied to the app itself, not only to its palette:

| Piece | Where | What it does |
|---|---|---|
| Version wallpaper engine | `overlay/.../obsi/ObsiWallpaper.kt` | Downloads the official *Wilderness Bound* (>= 1.12.2) or *Minecraft PC bundle* (< 1.12.2) packs from minecraft.net, caches them, and applies the matching artwork as the launcher background whenever the selected version changes. For the latest release it uses the Wilderness trailer as a live video background when `files/obsi/trailer.mp4` is present (fetch it with `tools/fetch-trailer.py`). |
| Dynamic accent | `overlay/.../obsi/ObsiTheme.kt` | Extracts an accent colour from the active wallpaper (hue histogram, same algorithm as desktop `Palette.kt`) and publishes it to Compose state. |
| Obsi Dynamic theme | `ui/theme/ColorTheme.kt` + `Theme.kt` + `LauncherSettingsScreen.kt` | A dark glass scheme generated from the extracted accent (factory default; always dark). Obsidian Fox is the fallback before the first wallpaper lands. |
| Glass-dark defaults | `scripts/patch.py` -> `AllSettings.kt` | Dark mode on, background blur 14, opacity 55, sponsor dialog off. |
| Background refresh | `scripts/patch.py` -> `BackgroundViewModel.kt`, `MainActivity.kt` | `reload()` hook + `LaunchedEffect` in `setContent` so the wallpaper/appearance updates live on version switch. |
| About screen | `overlay/.../ui/screens/content/settings/AboutInfoScreen.kt` | Full replacement: logo, name, version, the single **MobileGlues** credit and the GPL-3.0-or-later note. No other projects, sponsors or links. |

Build (CI does exactly this): see `.github/workflows/android.yml`. Locally you need JDK 21,
Android SDK (platform 37.2 + build-tools 37 + NDK 25.2.9519653) and about 8 GB of disk:

```bash
bash android/scripts/prepare.sh
cd android/.work/upstream && ./gradlew ZalithLauncher:assembleRelease -Darch=arm64
```

`patch.py` rewrites the module `gradle.properties`, so no `-Plauncher_name=...` is needed:
the produced APK is `ObsiLauncher-<version>-<abi>.apk` (currently **1.2.0**, `studio.obsifox.obsilauncher`,
versionCode `10200`). Release builds are signed with the fork's own public keystore — see
[`KEYSTORE.md`](KEYSTORE.md). On machines with less than ~6 GB RAM, R8 (`isMinifyEnabled`)
and `lintVitalRelease` can be skipped locally without affecting the app itself.

First delivered build: **ObsiLauncher-1.2.0-arm64-v8a.apk** (arm64-v8a; other ABIs via
`-Darch=arm|x86|x86_64|all`).

Licensing: everything here is GPL-3.0-or-later (see `/NOTICE.md`). Updating upstream: change `upstream.version`, run the workflow, fix any anchor `patch.py` reports.

Known gaps: only the notice card is translated to Persian – a complete `values-fa` for the whole app is future work.

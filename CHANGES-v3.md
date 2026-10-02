# Changelog — v0.3.0 (feedback round)

This round applies the user feedback on v0.2.0:

> «تمام نسخه‌ها ساپورت بشن، و نیازی به کلید نداشته باشه — رایگانه — و چرا روی موبایل
> این دیزاین جدید اعمال نشده؟»

1. **Free — the access-key system is gone.** No activation gate, no keys,
   no keygen. Every feature is unlocked for everyone.
2. **Every Minecraft version is supported.** The New-Instance dialog on
   desktop now offers releases, snapshots **and** the historical `old_beta` /
   `old_alpha` versions from the full Mojang manifest (the Android app already
   did — its download screen has filters for all four types).
3. **The new glass-dark design is now fully applied on the mobile (Android)
   app** — not just a colour overlay. Details below.

---

## 1. Access-key system removed

* Deleted `core/.../license/License.kt`, `LicenseTest.kt`,
  `desktop/.../ActivationScreen.kt` and `tools/obsi-keygen.py`.
* `AppController` no longer holds a `LicenseManager`; `App.kt` renders the
  shell directly (no gate branch); the Settings "Access key" card and the
  About licence row were removed; all `license_*` / `gate_*` strings (EN+FA)
  were dropped from `StringTables.kt`.
* The About screen now has a **"Free & open"** card instead of the key status.

## 2. All versions supported (desktop)

* `InstancesScreen.NewInstanceDialog` gained a **"Show old versions"**
  checkbox next to "Show snapshots"; `old_beta` and `old_alpha` entries from
  `version_manifest_v2.json` are now selectable and launchable.
* New strings: `show_old_versions` (EN + FA).

## 3. Mobile: the new design is now the app

The Android overlay went from "branding + theme colour" to a full design
integration. New overlay package `com.movtery.zalithlauncher.obsi`:

* **`ObsiWallpaper.kt`** — the mobile version of the version-driven wallpaper
  engine, mirroring the desktop `WallpaperService` rules:
  * Minecraft >= 1.12.2 → official *Wilderness Bound* pack
  * Minecraft < 1.12.2 → official *Minecraft PC bundle* pack
  * latest release → the *Wilderness Bound trailer* as a live video
    background when `files/obsi/trailer.mp4` exists (fetch it once with the
    shipped `tools/fetch-trailer.py`, or drop any mp4 there)
  * both packs are downloaded once from minecraft.net, cached under
    `files/obsi/wallpapers/<pack>/`, and the best portrait variant for the
    device is picked automatically
  * a state marker prevents re-copying the same artwork over a background the
    user has customised manually
* **`ObsiTheme.kt`** — composable-observable accent state; a hue-histogram
  extractor (same algorithm as the desktop `Palette.kt`) derives the accent
  colour from the active wallpaper, with the fox-orange fallback.
* **`OBSI_DYNAMIC` theme** (`ui/theme/ColorTheme.kt`, `Theme.kt`,
  `LauncherSettingsScreen.kt`) — a dark Material scheme generated from the
  extracted accent (`materialkolor`, TonalSpot), with the Obsidian Fox palette
  as fallback before the first wallpaper lands. It is the **factory default**
  theme, and it always renders glass-dark.
* **Glass-dark factory defaults** (patched `AllSettings.kt`): dark mode on,
  background blur 14, background opacity 55 — so the wallpaper shows through
  the frosted panels out of the box. The sponsor dialog is disabled.
* **`MainActivity`** is patched to run `ObsiWallpaper.autoSync` whenever the
  selected game version changes; the background refresh hook
  (`BackgroundViewModel.reload()`) makes the new artwork appear live.
* **About screen fully replaced** (`AboutInfoScreen.kt`): logo, name, version,
  the single **MobileGlues** rendering credit and the GPL-3.0-or-later note.
  All third-party acknowledgements, sponsor links and library lists are gone
  from the UI (the GPL §7 "unofficial modified version" home-screen card stays,
  as upstream's licence requires).

## 4. UX prototype

* `ObsiLauncher-UX-v3.html` replaces v2: the key gate is removed, a
  **"Free · no key required"** badge is shown, and a new **mobile preview**
  (phone frame) demonstrates the Android design — version wallpaper,
  frosted cards and the Obsi Dynamic accent switching per version rule.

## Verification

* `./gradlew :core:compileKotlin :desktop:compileKotlin :core:test :desktop:test` — green.
* `android/scripts/patch.py` dry-run against the pinned upstream commit — all
  17 anchors applied, overlay copied (14 files).
* Mobile Compose code is compile-checked in CI (Android SDK/NDK required
  locally — see `android/README.md`).

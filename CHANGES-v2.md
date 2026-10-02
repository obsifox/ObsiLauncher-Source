> **Note (v0.3.0):** the access-key system described below was **removed** in v0.3.0 — see [CHANGES-v3.md](CHANGES-v3.md). The launcher is free, no key required.

# ObsiLauncher v2 — Design & Feature Changelog

Version `0.2.0`. This document summarises everything that changed in the
**v2 redesign** compared to the public `0.1.0` MVP.

## 1. Desktop (Windows / Linux) — full visual redesign

* **Full-bleed wallpaper shell.** The official Minecraft artwork is the primary
  surface of the launcher; every screen floats on frosted-glass panels
  (`ObsiCard`, glass sidebar, glass task bar) over the art.
* **Three themes** (Settings → Appearance): *Vanilla* (warm dark glass,
  default), *White* (ivory glass), *Black* (low-glare near-black).
* **Dynamic accent engine.** The active wallpaper is sampled (coarse 40×40
  grid, hue histogram) and the extracted colour drives the accent of buttons,
  highlights, switches and the sidebar — the launcher literally takes its
  colour from the version you are playing. Toggle: *Tint the interface from
  the wallpaper*.
* **New Home screen** following the base UX spec: account chip top-left,
  active pack pill top-right, glass hero card with the selected profile,
  session strip bottom-left, large Play action bottom-right.
* **Dedicated About screen** (sidebar → About): brand identity, version,
  access-key status and **exactly one credit — MobileGlues**. No other
  third-party names, links or references appear in the About section.
* New task bar, crash dialog and snackbar styling in glass.

## 2. Version-driven wallpaper system (new)

`desktop/.../WallpaperService.kt` implements the product rule set:

| Minecraft version | Background |
|---|---|
| **>= 1.12.2** (all releases/snapshots up to the latest) | Official **Wilderness Bound** pack (`wallpapers_wilderness_bound_drop.zip` from minecraft.net) |
| **< 1.12.2** | Official **Minecraft PC Bundle** pack (`wallpapers_minecraft_pc_bundle.zip`) |
| **Latest release only** | Optional **Wilderness Bound trailer** as a muted, looping video background |

* Packs are downloaded once from minecraft.net, unpacked under
  `<data>/shared/wallpapers/<pack>/` and cached.
* **Offline fallback:** an optimized wide + portrait JPEG pair per pack ships
  inside the app resources and is extracted when the download fails, so the
  launcher always has artwork.
* Best-variant picker follows the UX spec (aspect-ratio match, no upscaling).
* Background modes: *Auto (per version)*, *Still image*, *Video (latest
  release only)*. A custom image can override the official pack.
* **Video background** (`VideoBackground.kt`): JavaFX Media renders the
  trailer muted + looping (starts ~8 s in, per the spec) beneath the UI. The
  still wallpaper stays as poster/fallback; sound is opt-in.
  Use `tools/fetch-trailer.py` (pytube) to place the MP4 at
  `<data>/shared/wallpapers/video/trailer.mp4`.

## 3. Access-key (activation) system (new)

* `core/.../license/License.kt` — offline key format `OBSI-XXXX-XXXX-XXXX-XXXX`
  (12-char payload + 4-char checksum, look-alike-free alphabet). Keys are
  validated locally; no server is contacted.
* First run shows a full-screen **activation gate**; the key is remembered in
  `<data>/license.json` and can be removed from Settings.
* Key generator: `tools/obsi-keygen.py` (Python, same algorithm, covered by a
  cross-implementation unit test).
* Unit tests: generation, tampering rejection, persistence round-trip.

## 4. Mobile (Android) — Obsidian Fox redesign

* New **Obsidian Fox** colour palette (warm charcoal + fox orange) registered
  in the upstream theme picker alongside the existing palettes — full
  light/dark Material 3 schemes in `overlay/.../ui/theme/*.kt` (generated from
  the pinned upstream commit by `tools/gen_mobile_theme.py`).
* **Notice card v2** (`ObsiNoticeCard.kt`): glass card with fox mark and
  accent gradient. The user-visible text keeps only the licence-required
  *Unofficial Modified Version* statement and a single **MobileGlues** credit;
  other source names were removed from the UI strings (EN + FA).
* `patch.py` registers the new theme label; the overlay copy step replaces the
  theme files (they are regenerated per upstream pin).

## 5. UX prototype

* `ObsiLauncher-UX-v2.html` — updated standalone prototype: access-key gate
  (same checksum algorithm in JS), version→wallpaper rule demo, About modal
  (MobileGlues only), EN/FA RTL preserved.

## 6. Build & tooling

* JavaFX Media added to the desktop module (jar + jpackage runtime modules).
* `settings.gradle.kts`: foojay toolchain resolver so machines shipping only a
  JRE can still build (`./gradlew :desktop:run`).
* Bundled wallpaper resources: `desktop/src/main/resources/wallpapers/{wilderness,legacy}/{wide,portrait}.jpg`.
* `VERSION` bumped to `0.2.0`.

## 7. Verification

* `./gradlew :core:test :desktop:test` — all green (incl. new license tests and
  EN/FA string parity).
* Headless renders of every screen (EN + FA/RTL) verified with the bundled
  `Screenshots` tool over the live wallpaper pipeline.
* UX prototype exercised in a real browser (gate → demo key → unlock → version
  rule → About modal).

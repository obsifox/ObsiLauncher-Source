# ObsiLauncher 1.5.0 — Setup wizard, dual background engine, landscape-first

Version code 10500 · package `studio.obsifox.obsilauncher` · still 100% free (no key)

## New in 1.5.0

### First-launch setup wizard
- The launcher now starts as a four-step wizard, exactly as briefed:
  1. **Welcome** — free & open, no access keys.
  2. **Background choice** — pick *Live video* or *Official wallpapers*; the
     choice applies immediately behind the glass UI.
  3. **Player profile** — create the offline profile (Microsoft can be added
     later in Accounts).
  4. **Ready** — enter the launcher.
- The wizard only appears once (stored in `obsi_settings`); the wallpaper
  engine keeps running behind it so the first impression is already themed.

### Two background types (video OR wallpaper)
- **Wallpapers (per version)** — unchanged rule from the brief:
  - Minecraft ≥ 1.12.2 → official *Wilderness Bound* drop from minecraft.net
  - Minecraft < 1.12.2 → official *Minecraft PC bundle* from minecraft.net
  - The accent colour extracted from the active artwork still drives the
    Obsi Dynamic theme, so the whole launcher recolors per version.
- **Video (latest release only)** — the pinned trailer
  (https://youtu.be/1HCrV7mFWr8) is downloaded in-app with a from-scratch,
  pytube-style InnerTube resolver (`core/net/YouTube.kt`, no dependencies,
  no API key) and plays muted + looping behind the UI — **only** while the
  latest Minecraft release is selected. Any other version automatically
  falls back to wallpaper artwork. Status, progress, retry and a manual
  download button live in Settings → Background.

### Fully landscape launcher
- `MainActivity` is now locked to `sensorLandscape` — the whole launcher,
  wizard included, always runs horizontally as requested.
- Wallpaper picking prefers landscape artwork for the horizontal layout.

### Also
- Settings → Background section (mode switch, trailer download state,
  re-sync background) in English and Persian.
- Version wallpapers + theme recoloring re-sync whenever the selected
  instance changes.

## Existing features (from 1.4.0) — all still in
- Modrinth browser: mods, modpacks, resource packs, shader packs
- Dependency engine: required dependencies locked, optional ones tickable,
  installed content shows "required by …" with deletion locked
- Loaders: Vanilla, Fabric, Forge, NeoForge, Quilt, OptiFine + custom versions
- Accounts: offline + free Microsoft device-code login
- Local-only custom skin & cape, fix & repair, per-instance settings
- Runtime packs documented in `docs/RUNTIME-PACKS.md`; MobileGlues remains the
  only third-party rendering credit.
